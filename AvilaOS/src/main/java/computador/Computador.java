package computador;

import hardware.Cpu;
import hardware.Memoria;
import kernel.Kernel;
import modelo.Proceso;
import planificacion.PoliticaPlanificacion;
import reloj.EscuchaReloj;

/**
 * Un computador del sistema distribuido: une su CPU, su memoria y su kernel.
 * Todos los computadores son instancias de esta misma clase; para agregar
 * otro basta con crear otra instancia y registrarla en el reloj global.
 */
public class Computador implements EscuchaReloj {

    private final int id;
    private final Memoria memoria;
    private final Cpu cpu;
    private final Kernel kernel;

    // Último ciclo que dio el reloj; lo usa recibirProceso para fijar la llegada
    private volatile long cicloActual;

    public Computador(int id, int capacidadRam, PoliticaPlanificacion politica) {
        if (capacidadRam <= 0) {
            throw new IllegalArgumentException("La RAM del computador debe ser mayor que 0");
        }
        this.id = id;
        this.memoria = new Memoria(capacidadRam);
        this.cpu = new Cpu(id);
        this.kernel = new Kernel(memoria, cpu, politica);
        this.cicloActual = 0;
    }

    /**
     * El reloj global llama a este método en cada ciclo. Se registra el
     * Computador (y no la CPU y el kernel por separado) para garantizar el
     * orden: primero el kernel decide y después la CPU ejecuta.
     */
    @Override
    public void onTick(long ciclo) {
        cicloActual = ciclo;
        kernel.planificar(ciclo);
        cpu.onTick(ciclo);
    }

    /** Entrega un proceso nuevo a este computador. Devuelve false si es null. */
    public boolean recibirProceso(Proceso proceso) {
        return kernel.recibirProceso(proceso, cicloActual);
    }

    public int getId() {
        return id;
    }

    public Memoria getMemoria() {
        return memoria;
    }

    public Cpu getCpu() {
        return cpu;
    }

    public Kernel getKernel() {
        return kernel;
    }
}