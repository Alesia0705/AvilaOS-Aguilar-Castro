package sincronizacion;

import modelo.Proceso;
import java.util.concurrent.Semaphore;

/**
 * Representación de un semáforo de sincronización en ÁvilaOS.
 * Permite controlar el acceso a recursos compartidos bloqueando procesos en su cola propia.
 */
public class Semaforo {
    private String nombre;
    private int valor;
    private ColaProcesos colaBloqueados;
    
    // Semáforo de Java para proteger las estructuras compartidas en reemplazo de synchronized
    private Semaphore mutexInterno;

    /**
     * Crea una instancia de un semáforo.
     * @param nombre Identificador del semáforo (ej: "mutex", "llenos", "vacios").
     * @param valorInicial Valor entero inicial del contador del semáforo.
     */
    public Semaforo(String nombre, int valorInicial) {
        this.nombre = nombre;
        this.valor = valorInicial;
        this.colaBloqueados = new ColaProcesos();
        // Inicializamos el mutex interno en 1 para garantizar exclusión mutua
        this.mutexInterno = new Semaphore(1);
    }

    /**
     * Operación semWait (Wait/P).
     * Decrementa el contador. Si el valor resulta menor a 0, el proceso se encola y bloquea.
     * @param proceso Proceso que solicita el recurso.
     * @return true si el proceso pudo continuar, false si fue bloqueado en la cola.
     */
    public boolean semWait(Proceso proceso) {
        // Rechazamos proceso null según lo solicitado
        if (proceso == null) {
            throw new IllegalArgumentException("El proceso no puede ser null en semWait");
        }

        boolean pudoContinuar = true;
        mutexInterno.acquireUninterruptibly();
        try {
            valor--;
            if (valor < 0) {
                colaBloqueados.encolar(proceso);
                pudoContinuar = false;
            }
        } finally {
            mutexInterno.release();
        }
        
        return pudoContinuar;
    }

    /**
     * Operación semSignal (Signal/V).
     * Incrementa el contador. Si hay procesos esperando, despierta al primero de la cola.
     * @return El proceso liberado para pasar a estado LISTO, o null si no había ninguno esperando.
     */
    public Proceso semSignal() {
        Proceso procesoDesbloqueado = null;
        mutexInterno.acquireUninterruptibly();
        try {
            valor++;
            if (valor <= 0) {
                procesoDesbloqueado = colaBloqueados.desencolar();
            }
        } finally {
            mutexInterno.release();
        }
        
        return procesoDesbloqueado;
    }

    /**
     * Saca al proceso de la cola del semáforo e incrementa el valor,
     * utilizado cuando el kernel termina bloqueados por deadline = 0.
     * @param proceso Proceso cuya espera se va a cancelar.
     */
    public void cancelarEspera(Proceso proceso) {
        if (proceso == null) return;
        
        mutexInterno.acquireUninterruptibly();
        try {
            // Corregido a quitar() para que coincida con la clase ColaProcesos
            boolean removido = colaBloqueados.quitar(proceso); 
            if (removido) {
                valor++; // Deshacemos el decremento previo
            }
        } finally {
            mutexInterno.release();
        }
    }

    public int getValor() {
        mutexInterno.acquireUninterruptibly();
        try {
            return valor;
        } finally {
            mutexInterno.release();
        }
    }
    
    public String getNombre() { return nombre; }
    public ColaProcesos getColaBloqueados() { return colaBloqueados; }
}