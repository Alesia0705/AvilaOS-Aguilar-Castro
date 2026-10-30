package planificacion;

import modelo.Proceso;
import modelo.enums.TipoPolitica;
import sincronizacion.ColaProcesos;

/**
 * EDF (Earliest Deadline First) apropiativa: se ejecuta el proceso con el
 * deadline restante más pequeño. El kernel va decrementando el deadline de
 * cada proceso en cada ciclo, así que el valor que se compara es "cuánto
 * falta para vencer", no una fecha fija.
 */
public class PoliticaEDF implements PoliticaPlanificacion {

    @Override
    public TipoPolitica getTipo() {
        return TipoPolitica.EDF;
    }

    /** Extrae el proceso más urgente (menor deadline restante). En empate, el más antiguo en cola. */
    @Override
    public Proceso seleccionarSiguiente(ColaProcesos listos) {
        Proceso[] copia = listos.obtenerListaProcesos();
        if (copia.length == 0) {
            return null;
        }
        Proceso masUrgente = copia[0];
        for (int i = 1; i < copia.length; i++) {
            if (copia[i].getDeadline() < masUrgente.getDeadline()) {
                masUrgente = copia[i];
            }
        }
        listos.quitar(masUrgente);
        return masUrgente;
    }

    /** Expropia si hay algún listo con deadline restante estrictamente menor que el que corre. */
    @Override
    public boolean debeExpropiar(Proceso enEjecucion, ColaProcesos listos, int ciclosEnCpu) {
        Proceso[] copia = listos.obtenerListaProcesos();
        for (int i = 0; i < copia.length; i++) {
            if (copia[i].getDeadline() < enEjecucion.getDeadline()) {
                return true;
            }
        }
        return false;
    }
}