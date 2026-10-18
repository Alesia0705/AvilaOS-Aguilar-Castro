package planificacion;

import modelo.Proceso;
import modelo.enums.TipoPolitica;
import sincronizacion.ColaProcesos;

/**
 * First Come, First Served: se atiende en orden de llegada.
 * No es apropiativa: un proceso conserva la CPU hasta que termina o se bloquea.
 */
public class PoliticaFCFS implements PoliticaPlanificacion {

    @Override
    public TipoPolitica getTipo() {
        return TipoPolitica.FCFS;
    }

    /** El primero de la cola es el que más lleva esperando: se extrae y se devuelve. */
    @Override
    public Proceso seleccionarSiguiente(ColaProcesos listos) {
        return listos.desencolar();
    }

    /** FCFS nunca le quita la CPU a un proceso que ya la tiene. */
    @Override
    public boolean debeExpropiar(Proceso enEjecucion, ColaProcesos listos, int ciclosEnCpu) {
        return false;
    }
}