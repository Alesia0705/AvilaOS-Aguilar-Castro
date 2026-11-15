package sistema;

import computador.Computador;
import disco.Disco;
import disco.SolicitudDisco;
import filesystem.NodoArchivo;
import filesystem.SistemaArchivos;
import modelo.Proceso;
import modelo.enums.Permiso;
import modelo.enums.TipoOperacion;
import planificacion.PoliticaPlanificacion;
import sincronizacion.Buffer;
import reloj.RelojGlobal;
import kernel.ServicioSistema;
import hardware.Cpu;
import kernel.Kernel;
import util.LogEventos;

/**
 * Sistema distribuido de ÁvilaOS: reúne los computadores del clúster, el
 * parámetro de latencia de red, el disco simulado y el sistema de archivos.
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
    // volatile: la interfaz lo conecta y lo leen los hilos de los computadores
    private volatile LogEventos log;
    private volatile Disco disco;
    private volatile SistemaArchivos sistemaArchivos;

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
     * Conecta el disco y su sistema de archivos al sistema distribuido y registra
     * el disco en el reloj global.
     */
    public void conectarDisco(Disco disco, SistemaArchivos sistemaArchivos) {
        this.disco = disco;
        this.sistemaArchivos = sistemaArchivos;
        if (disco != null) {
            disco.setSistema(this);
            reloj.registrar(disco);
        }
    }

    /**
     * Conecta el disco y crea automáticamente un SistemaArchivos asociado.
     */
    public void conectarDisco(Disco disco) {
        conectarDisco(disco, (disco != null) ? new SistemaArchivos(disco) : null);
    }

    public Disco getDisco() {
        return disco;
    }

    public SistemaArchivos getSistemaArchivos() {
        return sistemaArchivos;
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

    /** Conecta el log de eventos. Puede ser null (sin log). */
    public void setLogEventos(LogEventos log) {
        this.log = log;
    }

    @Override
    public void registrarEvento(long ciclo, String descripcion) {
        LogEventos actual = log;
        if (actual != null) {
            actual.registrar(ciclo, descripcion);
        }
    }

    @Override
    public boolean solicitarDisco(Proceso proceso, long ciclo) {
        if (proceso == null) {
            return false;
        }
        Disco d = this.disco;
        SistemaArchivos fs = this.sistemaArchivos;
        if (d == null || fs == null) {
            registrarEvento(ciclo, "No hay disco conectado: se rechaza la solicitud de "
                    + proceso.getNombre() + ".");
            return false;
        }

        String ruta = proceso.getRutaArchivo();
        TipoOperacion op = proceso.getOperacion();
        if (ruta == null || ruta.trim().isEmpty() || op == null) {
            registrarEvento(ciclo, "Solicitud de archivo inválida para " + proceso.getNombre() + ".");
            return false;
        }

        int bloqueObjetivo;
        int cantidadBloques = 1;
        switch (op) {
            case CREAR -> {
                NodoArchivo creado = fs.crearArchivo(ruta, proceso.getBloquesArchivo(), Permiso.PUBLICO);
                if (creado == null) {
                    registrarEvento(ciclo, "Error al crear " + ruta + " para " + proceso.getNombre()
                            + " (sin espacio, ruta inválida o sin permiso).");
                    return false;
                }
                bloqueObjetivo = creado.getPrimerBloque();
                cantidadBloques = creado.getTamanoBloques();
            }
            case LEER -> {
                NodoArchivo nodo = fs.leerArchivo(ruta, fs.getModoActual(), fs.getUsuarioActual());
                if (nodo == null) {
                    registrarEvento(ciclo, "Error al leer " + ruta + " para " + proceso.getNombre()
                            + " (no existe o acceso denegado).");
                    return false;
                }
                bloqueObjetivo = nodo.getPrimerBloque();
                cantidadBloques = Math.max(1, nodo.getTamanoBloques());
            }
            case ACTUALIZAR -> {
                NodoArchivo nodo = fs.leerArchivo(ruta, fs.getModoActual(), fs.getUsuarioActual());
                if (nodo == null) {
                    registrarEvento(ciclo, "Error al actualizar " + ruta + " para " + proceso.getNombre()
                            + " (no existe o acceso denegado).");
                    return false;
                }
                int nuevosBloques = proceso.getBloquesArchivo();
                if (nuevosBloques > 0 && nuevosBloques != nodo.getTamanoBloques()) {
                    if (!fs.actualizar(ruta, null, nuevosBloques, null, fs.getModoActual(), fs.getUsuarioActual())) {
                        registrarEvento(ciclo, "Error al redimensionar " + ruta + " para " + proceso.getNombre() + ".");
                        return false;
                    }
                }
                bloqueObjetivo = nodo.getPrimerBloque();
                cantidadBloques = Math.max(1, nodo.getTamanoBloques());
            }
            case ELIMINAR -> {
                NodoArchivo nodo = fs.buscarNodo(ruta);
                if (nodo == null) {
                    registrarEvento(ciclo, "Error al eliminar " + ruta + " para " + proceso.getNombre()
                            + " (no existe).");
                    return false;
                }
                int bloqueAntes = nodo.isEsDirectorio() ? 0 : nodo.getPrimerBloque();
                if (!fs.eliminar(ruta)) {
                    registrarEvento(ciclo, "Acceso denegado al eliminar " + ruta + " para " + proceso.getNombre() + ".");
                    return false;
                }
                bloqueObjetivo = Math.max(0, bloqueAntes);
                cantidadBloques = Math.max(1, nodo.getTamanoBloques());
            }
            default -> {
                return false;
            }
        }

        SolicitudDisco solicitud = new SolicitudDisco(proceso, bloqueObjetivo, op, ciclo, cantidadBloques);
        if (!d.encolarSolicitud(solicitud)) {
            registrarEvento(ciclo, "No se pudo encolar solicitud de disco para " + proceso.getNombre() + ".");
            return false;
        }
        registrarEvento(ciclo, "Proceso " + proceso.getNombre() + " solicita " + op
                + " en " + ruta + " (bloque " + bloqueObjetivo + ").");
        return true;
    }

    @Override
    public boolean solicitarSwap(Proceso proceso, TipoOperacion operacion, long ciclo) {
        Disco d = this.disco;
        if (proceso == null || d == null) {
            return false;
        }
        int bloque;
        if (operacion == TipoOperacion.SUSPENDER) {
            // 1 unidad de RAM = 1 bloque de swap
            bloque = d.asignarSwap(proceso.getNombre(), proceso.getMemoriaRAM());
            if (bloque < 0) {
                registrarEvento(ciclo, "Sin espacio en swap para suspender a " + proceso.getNombre()
                        + " (necesita " + proceso.getMemoriaRAM() + " bloques, hay "
                        + d.contarBloquesLibres(true) + " libres).");
                return false;
            }
            proceso.setBloqueSwap(bloque);
        } else if (operacion == TipoOperacion.REACTIVAR) {
            bloque = proceso.getBloqueSwap();
            if (bloque < 0) {
                return false;
            }
        } else {
            return false;
        }
        SolicitudDisco solicitud = new SolicitudDisco(proceso, bloque, operacion, ciclo, proceso.getMemoriaRAM());
        if (!d.encolarSolicitud(solicitud)) {
            if (operacion == TipoOperacion.SUSPENDER) {
                d.liberarCadena(bloque);
                proceso.setBloqueSwap(-1);
            }
            return false;
        }
        registrarEvento(ciclo, "Proceso " + proceso.getNombre() + " solicita " + operacion
                + " en swap (bloque " + bloque + ", " + proceso.getMemoriaRAM() + " bloques).");
        return true;
    }

    @Override
    public void completarSwap(Proceso proceso, TipoOperacion operacion, long ciclo) {
        Computador destino = getComputador(proceso.getComputadorAsignado());
        if (destino != null) {
            destino.getKernel().completarSwap(proceso, operacion, ciclo);
        }
    }

    @Override
    public void liberarSwap(Proceso proceso) {
        Disco d = this.disco;
        if (d != null && proceso != null && proceso.getBloqueSwap() >= 0) {
            d.liberarCadena(proceso.getBloqueSwap());
            proceso.setBloqueSwap(-1);
        }
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