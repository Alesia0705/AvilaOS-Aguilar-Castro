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
    private final ColaProcesos terminados;

    // volatile: la interfaz puede cambiar la política mientras el kernel la usa
    private volatile PoliticaPlanificacion politica;
    private volatile ServicioSistema sistema;

    // Ciclos seguidos que lleva el proceso actual en la CPU (para el quantum)
    private int ciclosEnCpu;

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
        this.terminados = new ColaProcesos();
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
        for (int i = 0; i < pendientes.length; i++) {
            Proceso p = pendientes[i];
            int ram = p.getMemoriaRAM();
            if (ram <= 0 || ram > memoria.getCapacidadTotal()) {
                nuevos.quitar(p);
                p.setEstado(EstadoProceso.TERMINADO);
                terminados.encolar(p);
            } else if (memoria.reservar(ram)) {
                nuevos.quitar(p);
                p.setEstado(EstadoProceso.LISTO);
                listos.encolar(p);
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

        admitirNuevos();
        descontarDeadlines(ciclo);
        atenderBloqueados();
        revisarProcesoEnCpu(ciclo);
        expropiarSiCorresponde(actual);
        asignarCpuSiLibre(actual, ciclo);

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
            if (motivo != MotivoBloqueo.ESPERA_ES && motivo != MotivoBloqueo.LATENCIA_RED) {
                continue;
            }
            p.decrementarEspera();
            if (p.getCiclosEsperaRestantes() == 0) {
                bloqueados.quitar(p);
                p.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
                p.setEstado(EstadoProceso.LISTO);
                listos.encolar(p);
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
                p.setFaseProdCons(4);
                return true;
            case 4:
                despertarProceso(buffer.getMutex().semSignal());
                p.setFaseProdCons(5);
                return true;
            default:
                despertarProceso(segundo.semSignal());
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
            return true;
        }
        return false;
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
            despertarProceso(primero.semSignal());
            return;
        }
        switch (p.getFaseProdCons()) {
            case 2:
                despertarProceso(primero.semSignal());
                break;
            case 3:
                despertarProceso(buffer.getMutex().semSignal());
                despertarProceso(primero.semSignal());
                break;
            case 4:
                despertarProceso(buffer.getMutex().semSignal());
                despertarProceso(segundo.semSignal());
                break;
            case 5:
                despertarProceso(segundo.semSignal());
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
        memoria.liberar(proceso.getMemoriaRAM());
        proceso.setEstado(EstadoProceso.TERMINADO);
        proceso.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
        proceso.setCicloFinalizacion(ciclo);
        terminados.encolar(proceso);
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
            if (siguiente.getCicloPrimeraEjecucion() < 0) {
                siguiente.setCicloPrimeraEjecucion(ciclo);
            }
        } else {
            listos.encolar(siguiente);
        }
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
        if (bloqueados.quitar(proceso)) {
            proceso.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
            proceso.setEstado(EstadoProceso.LISTO);
            listos.encolar(proceso);
        }
    }

    public PoliticaPlanificacion getPolitica() {
        return politica;
    }

    public Cpu getCpu() {
        return cpu;
    }
}
