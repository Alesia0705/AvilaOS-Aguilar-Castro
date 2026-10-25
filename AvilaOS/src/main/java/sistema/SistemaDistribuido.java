package sistema;

import computador.Computador;
import modelo.Proceso;
import planificacion.PoliticaPlanificacion;
import sincronizacion.Buffer;
import reloj.RelojGlobal;
import kernel.ServicioSistema;
import hardware.Cpu;
import kernel.Kernel;

/**
 * Sistema distribuido de ÁvilaOS: reúne los computadores del clúster y el
 * parámetro de latencia de red. Es el punto de entrada para crear computadores
 * y enviarles procesos.
 *
 * @author Alesia Castro
 */
public class SistemaDistribuido implements ServicioSistema {

    private Computador[] computadores;
    private int cantidadComputadores;
    private Buffer[] buffers;
    private int cantidadBuffers;
    private volatile int latenciaRed;
    private final RelojGlobal reloj;

    /**
     * @param latenciaRed ciclos extra que cuesta cada acceso a un buffer remoto
     * (>= 0)
     * @param reloj reloj global en el que se registran los computadores
     */
    public SistemaDistribuido(int latenciaRed, RelojGlobal reloj) {
        if (latenciaRed < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa");
        }
        if (reloj == null) {
            throw new IllegalArgumentException("El sistema necesita un reloj global");
        }
        this.latenciaRed = latenciaRed;
        this.reloj = reloj;
        this.computadores = new Computador[4];
        this.cantidadComputadores = 0;
        this.buffers = new Buffer[4];
        this.cantidadBuffers = 0;
    }

    /**
     * Crea un computador nuevo y lo agrega al sistema. Los ids van de 1 en
     * adelante.
     */
    public Computador agregarComputador(int capacidadRam, PoliticaPlanificacion politica) {
        if (cantidadComputadores == computadores.length) {
            ampliarComputadores();
        }
        Computador nuevo = new Computador(cantidadComputadores + 1, capacidadRam, politica);
        computadores[cantidadComputadores] = nuevo;
        nuevo.getKernel().setServicioSistema(this);
        cantidadComputadores++;
        reloj.registrar(nuevo);
        return nuevo;
    }

    /**
     * Duplica el arreglo a mano (sin java.util).
     */
    private void ampliarComputadores() {
        Computador[] mayor = new Computador[computadores.length * 2];
        for (int i = 0; i < cantidadComputadores; i++) {
            mayor[i] = computadores[i];
        }
        computadores = mayor;
    }

    /**
     * @return el computador con ese id, o null si no existe
     */
    public Computador getComputador(int id) {
        for (int i = 0; i < cantidadComputadores; i++) {
            if (computadores[i].getId() == id) {
                return computadores[i];
            }
        }
        return null;
    }

    /**
     * @return false si el computador asignado al proceso no existe
     */
    public boolean enviarProceso(Proceso proceso) {
        if (proceso == null) {
            return false;
        }
        Computador destino = getComputador(proceso.getComputadorAsignado());
        if (destino == null) {
            return false;
        }
        proceso.prepararProdCons();
        destino.recibirProceso(proceso);
        return true;
    }

    /**
     * Crea un buffer en la RAM de su computador anfitrión: reserva 1 unidad de
     * RAM por cada espacio de capacidad.
     *
     * @return el buffer creado, o null si el id está repetido, el anfitrión no
     * existe, la capacidad no es válida o no hay RAM suficiente
     */
    public Buffer crearBuffer(String id, int capacidad, int idAnfitrion) {
        if (id == null || id.isEmpty() || getBuffer(id) != null) {
            return null;
        }
        Computador anfitrion = getComputador(idAnfitrion);
        if (anfitrion == null) {
            return null;
        }
        // reservar rechaza capacidad <= 0 y devuelve false si no cabe, sin cambiar nada
        if (!anfitrion.getMemoria().reservar(capacidad)) {
            return null;
        }
        if (cantidadBuffers == buffers.length) {
            ampliarBuffers();
        }
        Buffer nuevo = new Buffer(id, capacidad, idAnfitrion);
        buffers[cantidadBuffers] = nuevo;
        cantidadBuffers++;
        return nuevo;
    }

    private void ampliarBuffers() {
        Buffer[] mayor = new Buffer[buffers.length * 2];
        for (int i = 0; i < cantidadBuffers; i++) {
            mayor[i] = buffers[i];
        }
        buffers = mayor;
    }

    /**
     * @return el buffer con ese id, o null si no existe
     */
    public Buffer getBuffer(String id) {
        for (int i = 0; i < cantidadBuffers; i++) {
            if (buffers[i].getId().equals(id)) {
                return buffers[i];
            }
        }
        return null;
    }

    /**
     * Buffer en la posición i (0 a cantidadBuffers - 1), para que las vistas
     * los recorran.
     */
    public Buffer getBufferEn(int posicion) {
        if (posicion < 0 || posicion >= cantidadBuffers) {
            return null;
        }
        return buffers[posicion];
    }

    @Override
    public Buffer buscarBuffer(String id) {
        return getBuffer(id);
    }

    /**
     * Despierta a un proceso bloqueado en el kernel del computador donde vive.
     */
    @Override
    public void despertar(Proceso proceso) {
        Computador destino = getComputador(proceso.getComputadorAsignado());
        if (destino != null) {
            destino.getKernel().despertarBloqueado(proceso);
        }
    }

    public int getCantidadBuffers() {
        return cantidadBuffers;
    }
        /** CPU de cada computador: para la utilización del sistema y la gráfica. */
    public Cpu[] getCpus() {
        Cpu[] cpus = new Cpu[cantidadComputadores];
        for (int i = 0; i < cantidadComputadores; i++) {
            cpus[i] = computadores[i].getCpu();
        }
        return cpus;
    }

    /** Procesos terminados de todos los computadores. */
    public Proceso[] getProcesosTerminados() {
        Proceso[][] grupos = new Proceso[cantidadComputadores][];
        for (int i = 0; i < cantidadComputadores; i++) {
            grupos[i] = computadores[i].getKernel().obtenerTerminados();
        }
        return unir(grupos);
    }

    /** Todos los procesos del sistema: nuevos, listos, bloqueados, en CPU y terminados. */
    public Proceso[] getTodosLosProcesos() {
        Proceso[][] grupos = new Proceso[cantidadComputadores * 5][];
        for (int i = 0; i < cantidadComputadores; i++) {
            Kernel kernel = computadores[i].getKernel();
            Proceso enCpu = computadores[i].getCpu().getProcesoActual();
            grupos[i * 5] = kernel.obtenerNuevos();
            grupos[i * 5 + 1] = kernel.obtenerListos();
            grupos[i * 5 + 2] = kernel.obtenerBloqueados();
            grupos[i * 5 + 3] = kernel.obtenerTerminados();
            grupos[i * 5 + 4] = (enCpu == null) ? new Proceso[0] : new Proceso[]{enCpu};
        }
        return unir(grupos);
    }

    /** Junta varios arreglos en uno. Primero se toman todos y luego se cuenta, porque los hilos siguen corriendo. */
    private Proceso[] unir(Proceso[][] grupos) {
        int total = 0;
        for (int i = 0; i < grupos.length; i++) {
            total += grupos[i].length;
        }
        Proceso[] todos = new Proceso[total];
        int posicion = 0;
        for (int i = 0; i < grupos.length; i++) {
            for (int j = 0; j < grupos[i].length; j++) {
                todos[posicion] = grupos[i][j];
                posicion++;
            }
        }
        return todos;
    }
    public int getCantidadComputadores() {
        return cantidadComputadores;
    }

    @Override
    public int getLatenciaRed() {
        return latenciaRed;
    }

    public RelojGlobal getReloj() {
        return reloj;
    }

    public boolean setLatenciaRed(int latenciaRed) {
        if (latenciaRed < 0) {
            return false;
        }
        this.latenciaRed = latenciaRed;
        return true;
    }
}
