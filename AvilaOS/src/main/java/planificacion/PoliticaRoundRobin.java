package planificacion;

import modelo.Proceso;
import modelo.enums.TipoPolitica;
import sincronizacion.ColaProcesos;

/**
 * Round Robin: cada proceso usa la CPU como máximo un quantum de ciclos.
 * Si lo agota y hay otros procesos listos, se expropia y vuelve al final de la cola.
 */
public class PoliticaRoundRobin implements PoliticaPlanificacion {

    public static final int QUANTUM_POR_DEFECTO = 4;

    // volatile: lo cambia la interfaz gráfica y lo lee el hilo del kernel
    private volatile int quantum;

    public PoliticaRoundRobin() {
        this(QUANTUM_POR_DEFECTO);
    }

    public PoliticaRoundRobin(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que 0");
        }
        this.quantum = quantum;
    }

    @Override
    public TipoPolitica getTipo() {
        return TipoPolitica.ROUND_ROBIN;
    }

    /** Orden de llegada, igual que FCFS: sale el primero de la cola. */
    @Override
    public Proceso seleccionarSiguiente(ColaProcesos listos) {
        return listos.desencolar();
    }

    /** Expropia si agotó su quantum y hay alguien esperando la CPU. */
    @Override
    public boolean debeExpropiar(Proceso enEjecucion, ColaProcesos listos, int ciclosEnCpu) {
        return ciclosEnCpu >= quantum && !listos.estaVacia();
    }

    /** Cambia el quantum en caliente. Devuelve false (y no cambia nada) si no es válido. */
    public boolean setQuantum(int nuevoQuantum) {
        if (nuevoQuantum <= 0) {
            return false;
        }
        this.quantum = nuevoQuantum;
        return true;
    }

    public int getQuantum() {
        return quantum;
    }
}