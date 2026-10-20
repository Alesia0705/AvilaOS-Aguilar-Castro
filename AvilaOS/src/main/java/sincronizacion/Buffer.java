package sincronizacion;

import java.util.concurrent.Semaphore;

/**
 * Representa un Buffer Acotado ubicado en la RAM de un computador anfitrión.
 * Sincronizado mediante el algoritmo clásico de Productor-Consumidor con 3 semáforos.
 */
public class Buffer {
    private String id;
    private int capacidad;
    private int idComputadorAnfitrion;
    private Object[] arregloElementos;
    private int in;
    private int out;
    private int cantidadElementos;

    // Semáforos para el problema del Productor-Consumidor (Simulación del OS)
    private Semaforo mutex;  // Controla la exclusión mutua (inicial = 1)
    private Semaforo llenos; // Cuenta elementos producidos disponibles (inicial = 0)
    private Semaforo vacios; // Cuenta espacios disponibles en el buffer (inicial = capacidad)

    // Semáforo REAL de Java para proteger las estructuras compartidas internamente
    private Semaphore mutexInterno;

    /**
     * Inicializa un nuevo buffer acotado en la memoria de un computador específico.
     * @param id Identificador único del buffer.
     * @param capacidad Tamaño máximo de elementos que puede almacenar.
     * @param idComputadorAnfitrion ID del computador donde reside el buffer en RAM.
     */
    public Buffer(String id, int capacidad, int idComputadorAnfitrion) {
        // Validación de capacidad solicitada por Alesia
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad del buffer debe ser mayor a 0");
        }

        this.id = id;
        this.capacidad = capacidad;
        this.idComputadorAnfitrion = idComputadorAnfitrion;
        this.arregloElementos = new Object[capacidad];
        this.in = 0;
        this.out = 0;
        this.cantidadElementos = 0;

        // Inicialización de semáforos según la teoría de Stallings
        this.mutex = new Semaforo("mutex_" + id, 1);
        this.llenos = new Semaforo("llenos_" + id, 0);
        this.vacios = new Semaforo("vacios_" + id, capacidad);

        // Mutex interno de Java inicializado en 1
        this.mutexInterno = new Semaphore(1);
    }

    /**
     * Inserta un elemento en la posición circular 'in' del buffer.
     * @param elemento Objeto producido a almacenar.
     */
    public void insertar(Object elemento) {
        try {
            mutexInterno.acquire();
            arregloElementos[in] = elemento;
            in = (in + 1) % capacidad;
            cantidadElementos++;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutexInterno.release();
        }
    }

    /**
     * Extrae un elemento de la posición circular 'out' del buffer.
     * @return El objeto consumido.
     */
    public Object extraer() {
        Object elemento = null;
        try {
            mutexInterno.acquire();
            elemento = arregloElementos[out];
            arregloElementos[out] = null;
            out = (out + 1) % capacidad;
            cantidadElementos--;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutexInterno.release();
        }
        return elemento;
    }

    public Semaforo getMutex() { return mutex; }
    public Semaforo getLlenos() { return llenos; }
    public Semaforo getVacios() { return vacios; }
    public String getId() { return id; }
    public int getIdComputadorAnfitrion() { return idComputadorAnfitrion; }
    public int getCantidadElementos() { return cantidadElementos; }
    public int getCapacidad() { return capacidad; }
}