package sincronizacion;

import modelo.Proceso;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Representa la cola de procesos en espera o listos para ejecución en ÁvilaOS.
 * Implementa concurrencia segura para permitir operaciones sincronizadas.
 */
public class ColaProcesos {

    private final Queue<Proceso> cola = new LinkedList<>();

    /**
     * Agrega un proceso a la cola.
     */
    public synchronized void encolar(Proceso proceso) {
        if (proceso != null) {
            cola.add(proceso);
        }
    }

    /**
     * Desencola y retorna el primer proceso en la cola.
     */
    public synchronized Proceso desencolar() {
        return cola.poll();
    }

    /**
     * Consulta el primer proceso de la cola sin removerlo.
     */
    public synchronized Proceso obtenerPrimero() {
        return cola.peek();
    }

    /**
     * Retorna la cantidad total de procesos actualmente en cola.
     */
    public synchronized int getTamano() {
        return cola.size();
    }

    /**
     * Verifica si la cola no contiene procesos.
     */
    public synchronized boolean estaVacia() {
        return cola.isEmpty();
    }

    /**
     * Retorna una copia de la lista de procesos para iteración, exportación o renderizado GUI.
     */
    public synchronized List<Proceso> obtenerListaProcesos() {
        return new ArrayList<>(cola);
    }
}