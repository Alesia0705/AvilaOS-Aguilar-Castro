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
}