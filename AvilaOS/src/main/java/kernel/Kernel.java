package kernel;

import hardware.Memoria;
import modelo.Proceso;
import modelo.enums.EstadoProceso;
import sincronizacion.ColaProcesos;
import hardware.Cpu;
import planificacion.PoliticaPlanificacion;
import modelo.enums.MotivoBloqueo;
import modelo.enums.TipoProceso;
import modelo.enums.ModoCpu;
import modelo.enums.TipoOperacion;
import java.util.concurrent.atomic.AtomicInteger;
import sincronizacion.Buffer;
import sincronizacion.Semaforo;

/**
 * Kernel de un computador: decide qué proceso está en cada estado. Mantiene las
 * colas de nuevos, listos, bloqueados y terminados, y admite procesos a
 * memoria. El hardware (Memoria, Cpu) solo obedece; las decisiones se toman
 * aquí.
 */
public class Kernel {

    private final Memoria memoria;
    private final Cpu cpu;
    private final ColaProcesos nuevos;
    private final ColaProcesos listos;
    private final ColaProcesos bloqueados;
    private final ColaProcesos listosSuspendidos;
    private final ColaProcesos bloqueadosSuspendidos;
    private final ColaProcesos terminados;

    // volatile: la interfaz puede cambiar la política mientras el kernel la usa
    private volatile PoliticaPlanificacion politica;
    private volatile ServicioSistema sistema;

    // Ciclos seguidos que lleva el proceso actual en la CPU (para el quantum)
    private int ciclosEnCpu;

    // Último ciclo en que el kernel tomó decisiones; se usa como fecha de los eventos del log
    private volatile long cicloLog;

    // --- Planificador de mediano plazo ---
    // RAM de los procesos que se estan suspendiendo (su escritura a swap sigue en la cola del disco):
    // se cuenta como "por liberar" para no suspender mas procesos de los necesarios
    private final AtomicInteger ramPorLiberar = new AtomicInteger();
    private final AtomicInteger suspensiones = new AtomicInteger();
    private final AtomicInteger reactivaciones = new AtomicInteger();
    // ultimo aviso del mediano plazo, para no repetir la misma linea en cada ciclo
    private String ultimoAviso = "";

    public Kernel(Memoria memoria, Cpu cpu, PoliticaPlanificacion politica) {
        if (memoria == null || cpu == null || politica == null) {
            throw new IllegalArgumentException("El kernel necesita memoria, CPU y política");
        }
        this.memoria = memoria;
        this.cpu = cpu;
        this.politica = politica;
        this.ciclosEnCpu = 0;
        this.nuevos = new ColaProcesos();
        this.listos = new ColaProcesos();
        this.bloqueados = new ColaProcesos();
        this.listosSuspendidos = new ColaProcesos();
        this.bloqueadosSuspendidos = new ColaProcesos();
        this.terminados = new ColaProcesos();
    }

    /**
     * Anota un evento de este computador en el log del sistema (si hay uno
     * conectado). La fecha es el ciclo en que el kernel está decidiendo.
     */
    private void registrar(String descripcion) {
        ServicioSistema servicio = sistema;
        if (servicio != null) {
            servicio.registrarEvento(cicloLog, "Computador " + cpu.getIdComputador() + ": " + descripcion);
        }
    }

    /**
     * Recibe un proceso recién creado: registra su ciclo de llegada y lo deja
     * en la cola de nuevos. Devuelve false si el proceso es null.
     */
    public boolean recibirProceso(Proceso proceso, long cicloActual) {
        if (proceso == null) {
            return false;
        }
        proceso.setCicloLlegada(cicloActual);
        proceso.setEstado(EstadoProceso.NUEVO);
        nuevos.encolar(proceso);
        return true;
    }

    /**
     * Intenta admitir a memoria los procesos nuevos, por orden de llegada. - Si
     * su RAM cabe, se reserva y pasa a listos. - Si no cabe ahora, se queda
     * esperando en nuevos. - Si su RAM es inválida (<= 0) o mayor que toda la
     * memoria del computador, nunca podrá entrar: se manda a terminados para no
     * atascar la simulación. Solo se llama desde el hilo del kernel; la
     * interfaz solo agrega a nuevos.
     */
    public void admitirNuevos() {
        Proceso[] pendientes = nuevos.obtenerListaProcesos();
        boolean suspensionPedida = false;
        for (int i = 0; i < pendientes.length; i++) {
            Proceso p = pendientes[i];
            int ram = p.getMemoriaRAM();
            if (ram <= 0 || ram > memoria.getCapacidadTotal()) {
                nuevos.quitar(p);
                p.setEstado(EstadoProceso.TERMINADO);
                terminados.encolar(p);
                registrar(p.getNombre() + " rechazado: pide " + ram + " de RAM y el computador tiene "
                        + memoria.getCapacidadTotal() + ".");
            } else if (memoria.reservar(ram)) {
                nuevos.quitar(p);
                p.setEstado(EstadoProceso.LISTO);
                listos.encolar(p);
                p.setEnMemoria(true);
                registrar(p.getNombre() + " admitido en memoria (RAM " + ram + ", libre "
                        + memoria.getLibre() + ").");
            } else if (!suspensionPedida) {
                // No cabe: el planificador de mediano plazo intenta hacerle lugar suspendiendo bloqueados.
                // Solo se atiende al primero que no cabe (por orden de llegada).
                suspensionPedida = true;
                liberarMemoriaPara(p);
            }
        }
    }

    /**
     * Fase de decisiones del kernel en un ciclo. Se llama ANTES de que la CPU
     * ejecute ese ciclo.
     */
    public void planificar(long ciclo) {
        // Se lee una sola vez: si la interfaz cambia la política a mitad de ciclo,
        // este ciclo termina con la política con la que empezó.
        PoliticaPlanificacion actual = politica;
        cicloLog = ciclo;

        admitirNuevos();
        reactivarSuspendidos(ciclo);
        descontarDeadlines(ciclo);
        atenderBloqueados();
        revisarProcesoEnCpu(ciclo);
        expropiarSiCorresponde(actual);
        asignarCpuSiLibre(actual, ciclo);
        acumularEspera();

        // Si hay un proceso en la CPU, este ciclo lo va a ejecutar
        Proceso enCpu = cpu.getProcesoActual();
        if (enCpu != null) {
            ciclosEnCpu++;
            enCpu.incrementarRafaga();
            // Los 5 pasos del protocolo cuentan como instrucciones en modo sistema
            if (enCpu.esProdCons()) {
                cpu.setModo(enCpu.estaEnProtocolo()
                        ? ModoCpu.SISTEMA_OPERATIVO : ModoCpu.USUARIO);
            }
            // La operacion de archivo cuenta como instruccion en modo sistema operativo
            if (enCpu.esArchivo()) {
                cpu.setModo(enCpu.isSolicitudEnviada()
                        ? ModoCpu.USUARIO : ModoCpu.SISTEMA_OPERATIVO);
            }
        }
    }

    /**
     * Los procesos que siguen en listos después de decidir la CPU esperaron
     * este ciclo (se usa para el tiempo de espera).
     */
    private void acumularEspera() {
        Proceso[] enListos = listos.obtenerListaProcesos();
        for (int i = 0; i < enListos.length; i++) {
            enListos[i].incrementarCiclosEspera();
        }
    }

    /**
     * Resta un ciclo al deadline de todos los procesos vivos. Los que están en
     * listos o bloqueados y llegan a 0 se terminan; el de la CPU se revisa
     * después, en revisarProcesoEnCpu.
     */
    private void descontarDeadlines(long ciclo) {
        Proceso[] enListos = listos.obtenerListaProcesos();
        for (int i = 0; i < enListos.length; i++) {
            enListos[i].decrementarDeadline();
            if (enListos[i].getDeadline() == 0) {
                listos.quitar(enListos[i]);
                finalizarProceso(enListos[i], ciclo);
            }
        }

        Proceso[] enBloqueados = bloqueados.obtenerListaProcesos();
        for (int i = 0; i < enBloqueados.length; i++) {
            enBloqueados[i].decrementarDeadline();
            if (enBloqueados[i].getDeadline() == 0 && bloqueados.quitar(enBloqueados[i])) {
                finalizarProceso(enBloqueados[i], ciclo);
            }
        }

        // Los suspendidos tambien consumen su deadline: si se les vence en swap, terminan
        Proceso[] enListosSusp = listosSuspendidos.obtenerListaProcesos();
        for (int i = 0; i < enListosSusp.length; i++) {
            enListosSusp[i].decrementarDeadline();
            if (enListosSusp[i].getDeadline() == 0 && listosSuspendidos.quitar(enListosSusp[i])) {
                finalizarProceso(enListosSusp[i], ciclo);
            }
        }
        Proceso[] enBloqSusp = bloqueadosSuspendidos.obtenerListaProcesos();
        for (int i = 0; i < enBloqSusp.length; i++) {
            enBloqSusp[i].decrementarDeadline();
            if (enBloqSusp[i].getDeadline() == 0 && bloqueadosSuspendidos.quitar(enBloqSusp[i])) {
                finalizarProceso(enBloqSusp[i], ciclo);
            }
        }

        Proceso enCpu = cpu.getProcesoActual();
        if (enCpu != null) {
            enCpu.decrementarDeadline();
        }
    }

    /**
     * Avanza un ciclo a los procesos bloqueados. Todos suman un ciclo de
     * bloqueo. Los que esperan E/S o latencia de red descuentan su espera y
     * vuelven a LISTO al llegar a 0; los que esperan un semáforo los despierta
     * un semSignal (despertarBloqueado).
     */
    private void atenderBloqueados() {
        Proceso[] bloqueadosAhora = bloqueados.obtenerListaProcesos();
        for (Proceso p : bloqueadosAhora) {
            p.incrementarCiclosBloqueado();
            MotivoBloqueo motivo = p.getMotivoBloqueo();
            if (motivo == MotivoBloqueo.SEM_BUFFER_LLENO
                    || motivo == MotivoBloqueo.SEM_BUFFER_VACIO
                    || motivo == MotivoBloqueo.SEM_MUTEX) {
                p.incrementarCiclosBloqueadoSemaforo();
            }
            if (motivo != MotivoBloqueo.ESPERA_ES && motivo != MotivoBloqueo.LATENCIA_RED) {
                continue;
            }
            p.decrementarEspera();
            if (p.getCiclosEsperaRestantes() == 0) {
                synchronized (p) {
                    if (p.isSuspendiendo()) {
                        // ya se esta pasando a swap: al terminar la suspension quedara listo/suspendido
                        p.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
                    } else if (bloqueados.quitar(p)) {
                        p.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
                        p.setEstado(EstadoProceso.LISTO);
                        listos.encolar(p);
                        registrar(p.getNombre() + " vuelve a listos ("
                                + (motivo == MotivoBloqueo.ESPERA_ES ? "terminó la E/S" : "llegó el dato remoto") + ").");
                    }
                }
            }
        }

        // Los bloqueados que estan en swap siguen esperando su E/S; al cumplirse pasan a listos/suspendidos
        Proceso[] suspendidosAhora = bloqueadosSuspendidos.obtenerListaProcesos();
        for (Proceso p : suspendidosAhora) {
            p.incrementarCiclosBloqueado();
            MotivoBloqueo motivo = p.getMotivoBloqueo();
            if (motivo != MotivoBloqueo.ESPERA_ES && motivo != MotivoBloqueo.LATENCIA_RED) {
                continue;
            }
            p.decrementarEspera();
            if (p.getCiclosEsperaRestantes() == 0) {
                synchronized (p) {
                    if (bloqueadosSuspendidos.quitar(p)) {
                        p.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
                        p.setEstado(EstadoProceso.LISTO_SUSPENDIDO);
                        listosSuspendidos.encolar(p);
                        registrar(p.getNombre() + " pasa a listo/suspendido (terminó su espera mientras estaba en swap).");
                    }
                }
            }
        }
    }

    /**
     * Si el proceso que está en la CPU ya ejecutó todas sus instrucciones o se
     * le venció el deadline, se saca de la CPU y se termina.
     */
    private void revisarProcesoEnCpu(long ciclo) {
        Proceso enCpu = cpu.getProcesoActual();
        if (enCpu == null) {
            return;
        }
        // Un productor/consumidor aplica el efecto de la instrucción que acaba
        // de ejecutar; si eso lo bloquea (o falla), sale de la CPU.
        if (enCpu.esProdCons() && enCpu.getDeadline() != 0) {
            if (!avanzarProdCons(enCpu, ciclo)) {
                cpu.retirar();
                ciclosEnCpu = 0;
                return;
            }
        }

        // Un proceso de E/S de archivo envia su solicitud al disco despues de
        // ejecutar su primera instruccion (la operacion, en modo sistema operativo)
        if (enCpu.esArchivo() && !enCpu.isSolicitudEnviada() && enCpu.getDeadline() != 0
                && enCpu.getTiempoRestante() < enCpu.getInstruccionesTotales()) {
            enviarSolicitudDisco(enCpu, ciclo);
            return;
        }

        if (enCpu.getTiempoRestante() <= 0 || enCpu.getDeadline() == 0) {
            cpu.retirar();
            ciclosEnCpu = 0;
            finalizarProceso(enCpu, ciclo);
        } else if (debeBloquearsePorEs(enCpu)) {
            cpu.retirar();
            ciclosEnCpu = 0;
            bloquearPorEs(enCpu);
        }
    }

    /**
     * Un proceso I/O bound pide E/S al completar su ráfaga de CPU.
     */
    private boolean debeBloquearsePorEs(Proceso p) {
        return p.getTipo() == TipoProceso.IO_BOUND
                && p.getCiclosCpu() > 0
                && p.getCiclosIo() > 0
                && p.getCiclosCpuEnRafaga() >= p.getCiclosCpu();
    }

    private void bloquearPorEs(Proceso p) {
        p.setEstado(EstadoProceso.BLOQUEADO);
        p.setMotivoBloqueo(MotivoBloqueo.ESPERA_ES);
        p.setCiclosEsperaRestantes(p.getCiclosIo());
        p.reiniciarRafaga();
        bloqueados.encolar(p);
        registrar(p.getNombre() + " se bloquea por E/S durante " + p.getCiclosIo() + " ciclos.");
    }

    /**
     * El proceso sale de la CPU y queda bloqueado esperando al disco. Se anota
     * en bloqueados ANTES de pedir la operacion, para que el disco lo encuentre
     * si lo despierta enseguida. Si la solicitud no se puede hacer, el proceso
     * termina.
     */
    private void enviarSolicitudDisco(Proceso p, long ciclo) {
        cpu.retirar();
        ciclosEnCpu = 0;
        p.setSolicitudEnviada(true);
        p.setEstado(EstadoProceso.BLOQUEADO);
        p.setMotivoBloqueo(MotivoBloqueo.ESPERA_DISCO);
        bloqueados.encolar(p);
        ServicioSistema servicio = sistema;
        if (servicio == null || !servicio.solicitarDisco(p, ciclo)) {
            bloqueados.quitar(p);
            p.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
            registrar(p.getNombre() + ": solicitud de " + p.getOperacion() + " sobre "
                    + p.getRutaArchivo() + " rechazada.");
            finalizarProceso(p, ciclo);
            return;
        }
        registrar(p.getNombre() + " se bloquea esperando al disco ("
                + p.getOperacion() + " " + p.getRutaArchivo() + ").");
    }

    /**
     * Aplica el efecto de la instrucción que el productor/consumidor acaba de
     * ejecutar y deja listo el siguiente paso. Fase 0 = trabajando; 1 a 5 =
     * pasos del protocolo (semWait, semWait, insertar/extraer, semSignal,
     * semSignal).
     *
     * @return false si el proceso debe salir de la CPU (se bloqueó o falló)
     */
    private boolean avanzarProdCons(Proceso p, long ciclo) {
        ServicioSistema servicio = sistema;
        Buffer buffer = (servicio == null) ? null : servicio.buscarBuffer(p.getIdBuffer());
        if (buffer == null) {
            // buffer inexistente: el proceso no puede trabajar, termina sin completarse
            finalizarProceso(p, ciclo);
            return false;
        }

        boolean productor = p.getTipo() == TipoProceso.PRODUCTOR;
        Semaforo primero = productor ? buffer.getVacios() : buffer.getLlenos();
        Semaforo segundo = productor ? buffer.getLlenos() : buffer.getVacios();
        MotivoBloqueo motivoPrimero = productor
                ? MotivoBloqueo.SEM_BUFFER_LLENO : MotivoBloqueo.SEM_BUFFER_VACIO;

        switch (p.getFaseProdCons()) {
            case 0:
                p.incrementarTrabajo();
                if (p.getTrabajoRealizado() >= p.getCiclosProdCons()) {
                    p.reiniciarTrabajo();
                    p.setFaseProdCons(1);
                    boolean remoto = buffer.getIdComputadorAnfitrion() != cpu.getIdComputador();
                    if (remoto && servicio.getLatenciaRed() > 0) {
                        registrar(p.getNombre() + " accede al buffer remoto " + buffer.getId() + " (computador "
                                + buffer.getIdComputadorAnfitrion() + "): espera "
                                + servicio.getLatenciaRed() + " ciclos de latencia de red.");
                        p.setEstado(EstadoProceso.BLOQUEADO);
                        p.setMotivoBloqueo(MotivoBloqueo.LATENCIA_RED);
                        p.setCiclosEsperaRestantes(servicio.getLatenciaRed());
                        bloqueados.encolar(p);
                        return false;
                    }
                }
                return true;
            case 1:
                p.setFaseProdCons(2);
                return esperarSemaforo(p, primero, motivoPrimero);
            case 2:
                p.setFaseProdCons(3);
                return esperarSemaforo(p, buffer.getMutex(), MotivoBloqueo.SEM_MUTEX);
            case 3:
                if (productor) {
                    buffer.insertar(p.getNombre());
                } else {
                    buffer.extraer();
                }
                registrar(p.getNombre() + (productor ? " inserta en el buffer " : " extrae del buffer ")
                        + buffer.getId() + " (elementos: " + buffer.getCantidadElementos() + "/"
                        + buffer.getCapacidad() + ").");
                p.setFaseProdCons(4);
                return true;
            case 4:
                senalar(buffer.getMutex(), p);
                p.setFaseProdCons(5);
                return true;
            default:
                senalar(segundo, p);
                p.incrementarElementos();
                p.setFaseProdCons(0);
                return true;
        }
    }

    /**
     * semWait del proceso. Se marca bloqueado y se anota en la cola de
     * bloqueados ANTES del semWait, para que un semSignal de otro computador
     * que lo despierte enseguida lo encuentre. Si el semWait lo deja pasar, se
     * deshace.
     *
     * @return true si el proceso puede seguir en la CPU
     */
    private boolean esperarSemaforo(Proceso p, Semaforo semaforo, MotivoBloqueo motivo) {
        p.setEstado(EstadoProceso.BLOQUEADO);
        p.setMotivoBloqueo(motivo);
        bloqueados.encolar(p);
        if (semaforo.semWait(p)) {
            bloqueados.quitar(p);
            p.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
            p.setEstado(EstadoProceso.EJECUCION);
            registrar(p.getNombre() + " semWait(" + semaforo.getNombre() + "): pasa (valor "
                    + semaforo.getValor() + ").");
            return true;
        }
        registrar(p.getNombre() + " semWait(" + semaforo.getNombre() + "): se bloquea (valor "
                + semaforo.getValor() + ").");
        return false;
    }

    /**
     * semSignal del proceso: lo anota en el log y, si había alguien esperando
     * en ese semáforo, lo despierta.
     */
    private void senalar(Semaforo semaforo, Proceso quien) {
        Proceso despertado = semaforo.semSignal();
        if (despertado != null) {
            registrar(quien.getNombre() + " semSignal(" + semaforo.getNombre() + "): despierta a "
                    + despertado.getNombre() + ".");
        } else {
            registrar(quien.getNombre() + " semSignal(" + semaforo.getNombre() + "): valor "
                    + semaforo.getValor() + ".");
        }
        despertarProceso(despertado);
    }

    private void despertarProceso(Proceso despertado) {
        ServicioSistema servicio = sistema;
        if (despertado != null && servicio != null) {
            servicio.despertar(despertado);
        }
    }

    /**
     * Devuelve lo que un productor/consumidor tenía tomado en los semáforos
     * cuando termina antes de tiempo (deadline). Para un proceso que termina
     * bien (fase 0) no hace nada.
     */
    private void liberarRecursosProdCons(Proceso p) {
        ServicioSistema servicio = sistema;
        Buffer buffer = (servicio == null) ? null : servicio.buscarBuffer(p.getIdBuffer());
        if (buffer == null) {
            return;
        }
        boolean productor = p.getTipo() == TipoProceso.PRODUCTOR;
        Semaforo primero = productor ? buffer.getVacios() : buffer.getLlenos();
        Semaforo segundo = productor ? buffer.getLlenos() : buffer.getVacios();
        MotivoBloqueo motivo = p.getMotivoBloqueo();

        if (motivo == MotivoBloqueo.SEM_BUFFER_LLENO || motivo == MotivoBloqueo.SEM_BUFFER_VACIO) {
            // todavía no había pasado el primer semWait: solo se saca de su cola
            primero.cancelarEspera(p);
            return;
        }
        if (motivo == MotivoBloqueo.SEM_MUTEX) {
            // esperaba el mutex, pero ya tenía el permiso del primer semáforo
            buffer.getMutex().cancelarEspera(p);
            senalar(primero, p);
            return;
        }
        switch (p.getFaseProdCons()) {
            case 2:
                senalar(primero, p);
                break;
            case 3:
                senalar(buffer.getMutex(), p);
                senalar(primero, p);
                break;
            case 4:
                senalar(buffer.getMutex(), p);
                senalar(segundo, p);
                break;
            case 5:
                senalar(segundo, p);
                break;
            default:
                break;
        }
    }

    /**
     * Pasa un proceso a terminados: libera su RAM, registra el ciclo en que el
     * kernel lo detectó terminado y lo guarda. Si termina con tiempoRestante >
     * 0, significa que perdió su deadline (útil para las métricas).
     */
    private void finalizarProceso(Proceso proceso, long ciclo) {
        if (proceso.esProdCons()) {
            liberarRecursosProdCons(proceso);
        }
        synchronized (proceso) {
            // un proceso suspendido ya devolvio su RAM; uno que se esta suspendiendo todavia la tiene
            if (proceso.isEnMemoria()) {
                memoria.liberar(proceso.getMemoriaRAM());
            }
            if (proceso.isSuspendiendo()) {
                ramPorLiberar.addAndGet(-proceso.getMemoriaRAM());
            }
            proceso.terminarTransitoSwap();
            proceso.setEstado(EstadoProceso.TERMINADO);
        }
        ServicioSistema servicioSwap = sistema;
        if (servicioSwap != null) {
            servicioSwap.liberarSwap(proceso);
        }
        proceso.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
        proceso.setCicloFinalizacion(ciclo);
        terminados.encolar(proceso);
        if (proceso.getTiempoRestante() <= 0) {
            registrar(proceso.getNombre() + " termina (libera " + proceso.getMemoriaRAM() + " de RAM).");
        } else if (proceso.getDeadline() == 0) {
            registrar(proceso.getNombre() + " pierde su deadline (le faltaban "
                    + proceso.getTiempoRestante() + " instrucciones).");
        } else {
            registrar(proceso.getNombre() + " termina sin completarse (le faltaban "
                    + proceso.getTiempoRestante() + " instrucciones).");
        }
    }

    /**
     * Si la política lo pide (quantum agotado, llegó uno más prioritario o más
     * urgente), el proceso sale de la CPU y vuelve al final de listos.
     */
    private void expropiarSiCorresponde(PoliticaPlanificacion actual) {
        Proceso enCpu = cpu.getProcesoActual();
        if (enCpu != null && actual.debeExpropiar(enCpu, listos, ciclosEnCpu)) {
            cpu.retirar();
            enCpu.setEstado(EstadoProceso.LISTO);
            listos.encolar(enCpu);
            ciclosEnCpu = 0;
            registrar(enCpu.getNombre() + " expropiado por la política " + actual.getTipo()
                    + " y vuelve a listos.");
        }
    }

    /**
     * Si la CPU está libre, la política elige el siguiente proceso de listos y
     * el kernel se lo asigna.
     */
    private void asignarCpuSiLibre(PoliticaPlanificacion actual, long ciclo) {
        if (cpu.getProcesoActual() != null) {
            return;
        }
        Proceso siguiente = actual.seleccionarSiguiente(listos);
        if (siguiente == null) {
            return;
        }
        if (cpu.asignar(siguiente)) {
            siguiente.setEstado(EstadoProceso.EJECUCION);
            ciclosEnCpu = 0;
            registrar(actual.getTipo() + " elige a " + siguiente.getNombre() + " para la CPU.");
            if (siguiente.getCicloPrimeraEjecucion() < 0) {
                siguiente.setCicloPrimeraEjecucion(ciclo);
            }
        } else {
            listos.encolar(siguiente);
        }
    }

    // ------------------------------------------------------------------
    // Planificador de mediano plazo (suspension y reactivacion con swap)
    // ------------------------------------------------------------------
    /**
     * Un proceso nuevo no cabe en la RAM: se suspenden procesos bloqueados
     * hasta que su RAM, sumada a la libre y a la que ya se esta liberando,
     * alcance. Cada suspension es una solicitud de escritura a swap en la cola
     * del disco; la RAM se libera cuando el disco termina (completarSwap).
     */
    private void liberarMemoriaPara(Proceso nuevo) {
        int falta = nuevo.getMemoriaRAM() - memoria.getLibre() - ramPorLiberar.get();
        while (falta > 0) {
            Proceso victima = elegirVictima();
            if (victima == null) {
                avisarUnaVez("Mediano plazo: " + nuevo.getNombre() + " no cabe en RAM y no hay procesos bloqueados que suspender.");
                return;
            }
            if (!iniciarSuspension(victima, nuevo)) {
                return;
            }
            falta -= victima.getMemoriaRAM();
        }
    }

    /**
     * Criterio para elegir a quien suspender: solo procesos BLOQUEADOS (no
     * van a usar la CPU pronto), nunca el que esta en la CPU ni los listos.
     * Entre ellos, el que mas RAM ocupa: libera mas memoria con menos
     * escrituras a swap (cada una cuesta un ciclo de disco por bloque). Si
     * empatan, el que lleva menos tiempo bloqueado, porque es al que le falta
     * mas para despertar (en una cola FIFO es el ultimo en ser atendido).
     */
    private Proceso elegirVictima() {
        Proceso[] candidatos = bloqueados.obtenerListaProcesos();
        Proceso mejor = null;
        for (int i = 0; i < candidatos.length; i++) {
            Proceso c = candidatos[i];
            if (c.getEstado() != EstadoProceso.BLOQUEADO || c.isEnTransitoSwap() || !c.isEnMemoria()) {
                continue;
            }
            if (mejor == null || c.getMemoriaRAM() > mejor.getMemoriaRAM()
                    || (c.getMemoriaRAM() == mejor.getMemoriaRAM()
                    && c.getCiclosBloqueado() < mejor.getCiclosBloqueado())) {
                mejor = c;
            }
        }
        return mejor;
    }

    private boolean iniciarSuspension(Proceso victima, Proceso nuevo) {
        ServicioSistema servicio = sistema;
        if (servicio == null) {
            return false;
        }
        synchronized (victima) {
            if (victima.getEstado() != EstadoProceso.BLOQUEADO || victima.isEnTransitoSwap()) {
                return false;
            }
            // se marca antes de pedir el swap: si el disco o un semaforo lo despiertan enseguida, lo sabra
            victima.marcarSuspendiendo();
        }
        ramPorLiberar.addAndGet(victima.getMemoriaRAM());
        if (!servicio.solicitarSwap(victima, TipoOperacion.SUSPENDER, cicloLog)) {
            ramPorLiberar.addAndGet(-victima.getMemoriaRAM());
            victima.terminarTransitoSwap();
            avisarUnaVez("Mediano plazo: no se pudo suspender a " + victima.getNombre()
                    + " para admitir a " + nuevo.getNombre() + " (sin espacio en swap).");
            return false;
        }
        registrar("Mediano plazo: suspende a " + victima.getNombre() + " (bloqueado, RAM "
                + victima.getMemoriaRAM() + ") para hacerle lugar a " + nuevo.getNombre() + ".");
        return true;
    }

    /**
     * Cuando hay RAM libre y no hay procesos nuevos esperando (ellos tienen
     * prioridad, porque la suspension se hizo para ellos), se trae de swap al
     * primer listo/suspendido. Se reserva la RAM al pedirlo y el proceso vuelve
     * a listos cuando el disco termina de leerlo.
     */
    private void reactivarSuspendidos(long ciclo) {
        if (!nuevos.estaVacia() || ramPorLiberar.get() > 0) {
            return;
        }
        ServicioSistema servicio = sistema;
        if (servicio == null) {
            return;
        }
        Proceso[] lista = listosSuspendidos.obtenerListaProcesos();
        for (int i = 0; i < lista.length; i++) {
            Proceso p = lista[i];
            if (p.isEnTransitoSwap()) {
                continue;
            }
            if (!memoria.reservar(p.getMemoriaRAM())) {
                return;   // por orden de llegada: si el primero no cabe, nadie se le adelanta
            }
            synchronized (p) {
                if (p.getEstado() != EstadoProceso.LISTO_SUSPENDIDO || p.isEnTransitoSwap()) {
                    memoria.liberar(p.getMemoriaRAM());
                    continue;
                }
                p.marcarReactivando();
                p.setEnMemoria(true);
            }
            if (!servicio.solicitarSwap(p, TipoOperacion.REACTIVAR, ciclo)) {
                memoria.liberar(p.getMemoriaRAM());
                p.setEnMemoria(false);
                p.terminarTransitoSwap();
                return;
            }
            registrar("Mediano plazo: reactiva a " + p.getNombre() + " (RAM " + p.getMemoriaRAM()
                    + ", libre " + memoria.getLibre() + ").");
        }
    }

    /**
     * El disco termino la solicitud de swap de un proceso de este computador.
     * Suspension: se libera su RAM y pasa a bloqueado/suspendido (o a
     * listo/suspendido si lo que esperaba ya ocurrio). Reactivacion: se liberan
     * sus bloques de swap y vuelve a listos. Si el proceso ya habia terminado
     * (deadline) no se hace nada.
     */
    public void completarSwap(Proceso proceso, TipoOperacion operacion, long ciclo) {
        ServicioSistema servicio = sistema;
        synchronized (proceso) {
            if (proceso.getEstado() == EstadoProceso.TERMINADO) {
                return;
            }
            if (operacion == TipoOperacion.SUSPENDER && proceso.isSuspendiendo()
                    && bloqueados.quitar(proceso)) {
                memoria.liberar(proceso.getMemoriaRAM());
                proceso.setEnMemoria(false);
                ramPorLiberar.addAndGet(-proceso.getMemoriaRAM());
                proceso.terminarTransitoSwap();
                proceso.incrementarVecesSuspendido();
                suspensiones.incrementAndGet();
                if (proceso.getMotivoBloqueo() == MotivoBloqueo.NINGUNO) {
                    proceso.setEstado(EstadoProceso.LISTO_SUSPENDIDO);
                    listosSuspendidos.encolar(proceso);
                } else {
                    proceso.setEstado(EstadoProceso.BLOQUEADO_SUSPENDIDO);
                    bloqueadosSuspendidos.encolar(proceso);
                }
                cicloLog = Math.max(cicloLog, ciclo);
                registrar(proceso.getNombre() + " suspendido: pasa a " + proceso.getEstado()
                        + " y libera " + proceso.getMemoriaRAM() + " de RAM (libre " + memoria.getLibre() + ").");
            } else if (operacion == TipoOperacion.REACTIVAR && proceso.isReactivando()
                    && listosSuspendidos.quitar(proceso)) {
                proceso.terminarTransitoSwap();
                proceso.setEstado(EstadoProceso.LISTO);
                listos.encolar(proceso);
                reactivaciones.incrementAndGet();
                if (servicio != null) {
                    servicio.liberarSwap(proceso);
                }
                cicloLog = Math.max(cicloLog, ciclo);
                registrar(proceso.getNombre() + " reactivado: vuelve a listos.");
            }
        }
    }

    private void avisarUnaVez(String aviso) {
        if (!aviso.equals(ultimoAviso)) {
            ultimoAviso = aviso;
            registrar(aviso);
        }
    }

    /** Veces que este kernel suspendio un proceso (para las metricas). */
    public int getSuspensiones() {
        return suspensiones.get();
    }

    /** Veces que este kernel reactivo un proceso (para las metricas). */
    public int getReactivaciones() {
        return reactivaciones.get();
    }

    // --- Consultas para la interfaz (copias: la vista solo observa) ---
    public Proceso[] obtenerNuevos() {
        return nuevos.obtenerListaProcesos();
    }

    public Proceso[] obtenerListos() {
        return listos.obtenerListaProcesos();
    }

    public Proceso[] obtenerBloqueados() {
        return bloqueados.obtenerListaProcesos();
    }

    public Proceso[] obtenerListosSuspendidos() {
        return listosSuspendidos.obtenerListaProcesos();
    }

    public Proceso[] obtenerBloqueadosSuspendidos() {
        return bloqueadosSuspendidos.obtenerListaProcesos();
    }

    public Proceso[] obtenerTerminados() {
        return terminados.obtenerListaProcesos();
    }

    public Memoria getMemoria() {
        return memoria;
    }

    /**
     * Cambia la política de planificación en caliente. Aplica desde el
     * siguiente ciclo. Devuelve false si la política es null.
     */
    public boolean cambiarPolitica(PoliticaPlanificacion nueva) {
        if (nueva == null) {
            return false;
        }
        this.politica = nueva;
        return true;
    }

    /**
     * Lo conecta el sistema al crear el computador.
     */
    public void setServicioSistema(ServicioSistema sistema) {
        this.sistema = sistema;
    }

    /**
     * Pasa a LISTO un proceso bloqueado por un semáforo. Lo llama el kernel de
     * otro computador (o el propio) cuando un semSignal lo despierta.
     */
    public void despertarBloqueado(Proceso proceso) {
        synchronized (proceso) {
            if (proceso.isSuspendiendo() && proceso.getEstado() == EstadoProceso.BLOQUEADO) {
                // se esta pasando a swap: ya no espera nada, al terminar quedara listo/suspendido
                proceso.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
            } else if (bloqueados.quitar(proceso)) {
                proceso.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
                proceso.setEstado(EstadoProceso.LISTO);
                listos.encolar(proceso);
            } else if (bloqueadosSuspendidos.quitar(proceso)) {
                // despierta en swap: pasa a listo/suspendido y espera a que haya RAM para reactivarlo
                proceso.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
                proceso.setEstado(EstadoProceso.LISTO_SUSPENDIDO);
                listosSuspendidos.encolar(proceso);
                registrar(proceso.getNombre() + " pasa a listo/suspendido (se cumplió lo que esperaba mientras estaba en swap).");
            }
        }
    }

    public PoliticaPlanificacion getPolitica() {
        return politica;
    }

    public Cpu getCpu() {
        return cpu;
    }
}