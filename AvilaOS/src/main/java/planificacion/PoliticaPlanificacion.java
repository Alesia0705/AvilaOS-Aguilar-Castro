package planificacion;

import modelo.Proceso;
import modelo.enums.TipoPolitica;
import sincronizacion.ColaProcesos;

/**
 * Contrato que cumple toda política de planificación (patrón Strategy).
 * El kernel solo conoce esta interfaz, así que cambiar de política
 * en tiempo de ejecución es cambiar de objeto, sin tocar el kernel.
 */
public interface PoliticaPlanificacion {

    /** Devuelve qué política es (FCFS, EDF, ROUND_ROBIN, PRIORIDADES). */
    TipoPolitica getTipo();

    /**
     * Elige el siguiente proceso de la cola de listos y lo EXTRAE de ella.
     * Devuelve null si la cola está vacía.
     */
    Proceso seleccionarSiguiente(ColaProcesos listos);

    /**
     * Indica si el proceso que está en la CPU debe ser expropiado ahora.
     * @param enEjecucion proceso que ocupa la CPU
     * @param listos cola de procesos listos (solo se consulta, no se modifica)
     * @param ciclosEnCpu ciclos consecutivos que lleva ejecutándose
     */
    boolean debeExpropiar(Proceso enEjecucion, ColaProcesos listos, int ciclosEnCpu);
}