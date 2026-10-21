package kernel;

import hardware.Memoria;
import modelo.Proceso;
import modelo.enums.EstadoProceso;
import sincronizacion.ColaProcesos;
import hardware.Cpu;
import planificacion.PoliticaPlanificacion;
import modelo.enums.MotivoBloqueo;
import modelo.enums.TipoProceso;

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
            if (enBloqueados[i].getDeadline() == 0) {
                bloqueados.quitar(enBloqueados[i]);
                finalizarProceso(enBloqueados[i], ciclo);
            }
        }

        Proceso enCpu = cpu.getProcesoActual();
        if (enCpu != null) {
            enCpu.decrementarDeadline();
        }
    }

    /**
     * Avanza un ciclo a los procesos bloqueados por E/S. Cuando su espera llega
     * a 0 vuelven a la cola de listos.
     */
    private void atenderBloqueados() {
        Proceso[] bloqueadosAhora = bloqueados.obtenerListaProcesos();
        for (Proceso p : bloqueadosAhora) {
            if (p.getMotivoBloqueo() != MotivoBloqueo.ESPERA_ES) {
                continue; // los de semáforo/red los despierta otro mecanismo
            }
            p.incrementarCiclosBloqueado();
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
     * Pasa un proceso a terminados: libera su RAM, registra el ciclo en que el
     * kernel lo detectó terminado y lo guarda. Si termina con tiempoRestante >
     * 0, significa que perdió su deadline (útil para las métricas).
     */
    private void finalizarProceso(Proceso proceso, long ciclo) {
        memoria.liberar(proceso.getMemoriaRAM());
        proceso.setEstado(EstadoProceso.TERMINADO);
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

    public PoliticaPlanificacion getPolitica() {
        return politica;
    }

    public Cpu getCpu() {
        return cpu;
    }
}
