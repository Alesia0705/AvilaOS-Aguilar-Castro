package sincronizacion;

/**
 * Representación de un semáforo de sincronización en ÁvilaOS.
 * Permite controlar el acceso a recursos compartidos bloqueando procesos en su cola propia.
 */
public class Semaforo {
    private String nombre;
    private int valor;
    private ColaProcesos colaBloqueados;

    /**
     * Crea una instancia de un semáforo.
     * @param nombre Identificador del semáforo (ej: "mutex", "llenos", "vacios").
     * @param valorInicial Valor entero inicial del contador del semáforo.
     */
    public Semaforo(String nombre, int valorInicial) {
        this.nombre = nombre;
        this.valor = valorInicial;
        this.colaBloqueados = new ColaProcesos();
    }

    /**
     * Operación semWait (Wait/P).
     * Decrementa el contador. Si el valor resulta menor a 0, el proceso se encola y bloquea.
     * @param proceso Proceso que solicita el recurso.
     * @return true si el proceso pudo continuar, false si fue bloqueado en la cola.
     */
    public synchronized boolean semWait(Object proceso) {
        valor--;
        if (valor < 0) {
            colaBloqueados.encolar(proceso);
            return false;
        }
        return true;
    }

    /**
     * Operación semSignal (Signal/V).
     * Incrementa el contador. Si hay procesos esperando, despierta al primero de la cola.
     * @return El proceso liberado para pasar a estado LISTO, o null si no había ninguno esperando.
     */
    public synchronized Object semSignal() {
        valor++;
        if (valor <= 0) {
            Object procesoDesbloqueado = colaBloqueados.desencolar();
            if (procesoDesbloqueado != null) {
                return procesoDesbloqueado;
            }
        }
        return null;
    }

    public int getValor() { return valor; }
    public String getNombre() { return nombre; }
    public ColaProcesos getColaBloqueados() { return colaBloqueados; }
}