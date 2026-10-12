package modelo;

/**
 * Representa un proceso dentro del simulador ÁvilaOS.
 */
public class Proceso {

    private int id;
    private String nombre;
    private int memoriaRAM;
    private int prioridad;
    private int deadline;
    private String tipo; // "CPU Bound", "IO Bound", "Productor", "Consumidor"
    private int computadorAsignado;
    private String estado; // "LISTO", "EJECUCION", "BLOQUEADO", "TERMINADO"

    /**
     * Constructor para instanciar un nuevo proceso desde la GUI.
     */
    public Proceso(int id, String nombre, int memoriaRAM, int prioridad, int deadline, String tipo, int computadorAsignado) {
        this.id = id;
        this.nombre = nombre;
        this.memoriaRAM = memoriaRAM;
        this.prioridad = prioridad;
        this.deadline = deadline;
        this.tipo = tipo;
        this.computadorAsignado = computadorAsignado;
        this.estado = "LISTO";
    }

    // Getters y Setters
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public int getMemoriaRAM() { return memoriaRAM; }
    public int getPrioridad() { return prioridad; }
    public int getDeadline() { return deadline; }
    public String getTipo() { return tipo; }
    public int getComputadorAsignado() { return computadorAsignado; }
    public String getEstado() { return estado; }

    public void setEstado(String estado) { this.estado = estado; }

    @Override
    public String toString() {
        return "Proceso{" + "ID=" + id + ", Nombre='" + nombre + '\'' + ", RAM=" + memoriaRAM + "MB, Tipo='" + tipo + '\'' + '}';
    }
}