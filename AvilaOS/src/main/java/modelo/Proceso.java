package modelo;
import modelo.enums.EstadoProceso;
import modelo.enums.TipoProceso;
import modelo.enums.MotivoBloqueo;

/**
 * Representa un proceso dentro del simulador ÁvilaOS.
 */
public class Proceso {

    // Contador compartido por todos los procesos: garantiza IDs únicos en todo el sistema
    private static int siguienteId = 1;

    private static synchronized int generarId() {
        return siguienteId++;
    }

    private int id;
    private String nombre;
    private int memoriaRAM;
    private int prioridad;
    private int deadline;
    private TipoProceso tipo;
    private int computadorAsignado;
    private EstadoProceso estado;
    private int instruccionesTotales;
    private int tiempoRestante;
    private int pc;
    private int mar;
    private MotivoBloqueo motivoBloqueo;

    /**
     * Constructor para instanciar un nuevo proceso desde la GUI.
     */
    public Proceso(String nombre, int instruccionesTotales, int memoriaRAM, int prioridad, int deadline, TipoProceso tipo, int computadorAsignado) {
        this.id = generarId();
        this.nombre = nombre;
        this.memoriaRAM = memoriaRAM;
        this.prioridad = prioridad;
        this.deadline = deadline;
        this.tipo = tipo;
        this.computadorAsignado = computadorAsignado;
        this.estado = EstadoProceso.NUEVO;
        this.instruccionesTotales = instruccionesTotales;
        this.tiempoRestante = instruccionesTotales;
        this.pc = 0;
        this.mar = 0;
        this.motivoBloqueo = MotivoBloqueo.NINGUNO;
    }

    // Getters y Setters
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public int getMemoriaRAM() { return memoriaRAM; }
    public int getPrioridad() { return prioridad; }
    public int getDeadline() { return deadline; }
    public TipoProceso getTipo() { return tipo; }
    public int getComputadorAsignado() { return computadorAsignado; }
    public EstadoProceso getEstado() { return estado; }
    public void setEstado(EstadoProceso estado) { this.estado = estado; }
    public int getInstruccionesTotales() { return instruccionesTotales; }
    public int getTiempoRestante() { return tiempoRestante; }
    public int getPc() { return pc; }
    public int getMar() { return mar; }
    public MotivoBloqueo getMotivoBloqueo() { return motivoBloqueo; }
    public void setMotivoBloqueo(MotivoBloqueo motivoBloqueo) { this.motivoBloqueo = motivoBloqueo; }
    /**
     * Ejecuta una instrucción: PC y MAR avanzan 1 y baja el tiempo restante.
     */
    public void ejecutarInstruccion() {
        pc++;
        mar++;
        tiempoRestante--;
    }

    /**
     * Descuenta un ciclo del deadline (si llega a 0, el proceso debe terminarse).
     */
    public void decrementarDeadline() {
        if (deadline > 0) {
            deadline--;
        }
    }
    @Override
    public String toString() {
        return "Proceso{" + "ID=" + id + ", Nombre='" + nombre + '\'' + ", RAM=" + memoriaRAM + "MB, Tipo='" + tipo + '\'' + '}';
    }
}