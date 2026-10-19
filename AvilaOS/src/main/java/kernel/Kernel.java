package kernel;

import hardware.Memoria;
import modelo.Proceso;
import modelo.enums.EstadoProceso;
import sincronizacion.ColaProcesos;

/**
 * Kernel de un computador: decide qué proceso está en cada estado.
 * Mantiene las colas de nuevos, listos, bloqueados y terminados, y admite
 * procesos a memoria. El hardware (Memoria, Cpu) solo obedece; las decisiones
 * se toman aquí.
 */
public class Kernel {

    private final Memoria memoria;
    private final ColaProcesos nuevos;
    private final ColaProcesos listos;
    private final ColaProcesos bloqueados;
    private final ColaProcesos terminados;

    public Kernel(Memoria memoria) {
        if (memoria == null) {
            throw new IllegalArgumentException("El kernel necesita una memoria");
        }
        this.memoria = memoria;
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
     * Intenta admitir a memoria los procesos nuevos, por orden de llegada.
     * - Si su RAM cabe, se reserva y pasa a listos.
     * - Si no cabe ahora, se queda esperando en nuevos.
     * - Si su RAM es inválida (<= 0) o mayor que toda la memoria del computador,
     *   nunca podrá entrar: se manda a terminados para no atascar la simulación.
     * Solo se llama desde el hilo del kernel; la interfaz solo agrega a nuevos.
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
}