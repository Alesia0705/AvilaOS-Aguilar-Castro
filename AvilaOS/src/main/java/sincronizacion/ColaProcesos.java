package sincronizacion;

/**
 * Estructura de nodo simple para la lista enlazada propia.
 * Almacena la referencia a un proceso y al siguiente nodo de la cola.
 */
class NodoProceso {
    private Object proceso;
    private NodoProceso siguiente;

    /**
     * Constructor del nodo.
     * @param proceso Objeto proceso que se almacenará en el nodo.
     */
    public NodoProceso(Object proceso) {
        this.proceso = proceso;
        this.siguiente = null;
    }

    public Object getProceso() { return proceso; }
    public NodoProceso getSiguiente() { return siguiente; }
    public void setSiguiente(NodoProceso siguiente) { this.siguiente = siguiente; }
}

/**
 * Estructura de datos dinámica (Cola FIFO) programada desde cero.
 * Cumple con la restricción de no utilizar las colecciones de java.util.
 */
public class ColaProcesos {
    private NodoProceso cabeza;
    private NodoProceso cola;
    private int tamano;

    /**
     * Inicializa una cola vacía.
     */
    public ColaProcesos() {
        this.cabeza = null;
        this.cola = null;
        this.tamano = 0;
    }

    /**
     * Inserta un nuevo proceso al final de la cola.
     * @param proceso Proceso a encolar.
     */
    public synchronized void encolar(Object proceso) {
        NodoProceso nuevo = new NodoProceso(proceso);
        if (estaVacia()) {
            cabeza = nuevo;
        } else {
            cola.setSiguiente(nuevo);
        }
        cola = nuevo;
        tamano++;
    }

    /**
     * Extrae y retorna el primer proceso de la cola (FIFO).
     * @return El proceso extraído o null si la cola está vacía.
     */
    public synchronized Object desencolar() {
        if (estaVacia()) return null;
        Object proceso = cabeza.getProceso();
        cabeza = cabeza.getSiguiente();
        if (cabeza == null) cola = null;
        tamano--;
        return proceso;
    }

    /**
     * Verifica si la cola no contiene elementos.
     * @return true si está vacía, false en caso contrario.
     */
    public boolean estaVacia() { return cabeza == null; }

    /**
     * Obtiene la cantidad de procesos actualmente encolados.
     * @return Número de elementos en la cola.
     */
    public int getTamano() { return tamano; }
}