package modelo;
import modelo.enums.EstadoProceso;
import modelo.enums.TipoProceso;

/**
 * Representa un proceso dentro del simulador ÁvilaOS.
 */
public class Proceso {

    private int id;
    private String nombre;
    private int memoriaRAM;
    private int prioridad;
    private int deadline;
    private TipoProceso tipo;
    private int computadorAsignado;
    private EstadoProceso estado;

    /**
     * Constructor para instanciar un nuevo proceso desde la GUI.
     */
    public Proceso(int id, String nombre, int memoriaRAM, int prioridad, int deadline, TipoProceso tipo, int computadorAsignado) {
        this.id = id;
        this.nombre = nombre;
        this.memoriaRAM = memoriaRAM;
        this.prioridad = prioridad;
        this.deadline = deadline;
        this.tipo = tipo;
        this.computadorAsignado = computadorAsignado;
        this.estado = EstadoProceso.NUEVO;
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

    @Override
    public String toString() {
        return "Proceso{" + "ID=" + id + ", Nombre='" + nombre + '\'' + ", RAM=" + memoriaRAM + "MB, Tipo='" + tipo + '\'' + '}';
    }
}