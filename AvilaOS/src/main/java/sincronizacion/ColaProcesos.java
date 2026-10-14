package sincronizacion;

import java.util.concurrent.Semaphore;
import modelo.Proceso;
import util.ListaEnlazada;

/**
 * Cola de procesos compartida entre hilos. Guarda los procesos en una
 * ListaEnlazada propia y la protege con un Semaphore(1) usado como mutex.
 *
 * @author Alesia Castro
 */
public class ColaProcesos {

    private final ListaEnlazada<Proceso> lista;
    private final Semaphore mutex;

    public ColaProcesos() {
        this.lista = new ListaEnlazada<>();
        this.mutex = new Semaphore(1);
    }

    /**
     * Agrega un proceso al final de la cola.
     */
    public void encolar(Proceso proceso) {
        try {
            mutex.acquire();
            try {
                lista.agregarAlFinal(proceso);
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Saca y devuelve el primer proceso de la cola, o null si está vacía.
     */
    public Proceso desencolar() {
        Proceso resultado = null;
        try {
            mutex.acquire();
            try {
                resultado = lista.quitarPrimero();
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return resultado;
    }
        /**
     * Devuelve el primer proceso sin sacarlo de la cola, o null si está vacía.
     */
    public Proceso obtenerPrimero() {
        Proceso resultado = null;
        try {
            mutex.acquire();
            try {
                resultado = lista.obtener(0);
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return resultado;
    }

    /**
     * Devuelve cuántos procesos hay en la cola.
     */
    public int getTamano() {
        int tamano = 0;
        try {
            mutex.acquire();
            try {
                tamano = lista.getTamano();
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return tamano;
    }

    /**
     * Indica si la cola no tiene procesos.
     */
    public boolean estaVacia() {
        return getTamano() == 0;
    }
        /**
     * Devuelve una copia de los procesos de la cola, en orden, como arreglo.
     * Es una foto del momento: si la cola cambia después, el arreglo no cambia.
     */
    public Proceso[] obtenerListaProcesos() {
        Proceso[] copia = new Proceso[0];
        try {
            mutex.acquire();
            try {
                copia = new Proceso[lista.getTamano()];
                for (int i = 0; i < copia.length; i++) {
                    copia[i] = lista.obtener(i);
                }
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return copia;
    }
}
