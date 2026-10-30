package planificacion;

import modelo.Proceso;
import modelo.enums.TipoPolitica;
import sincronizacion.ColaProcesos;

/**
 * Prioridades apropiativas: la prioridad 1 es la más alta.
 * Si llega un proceso con mejor prioridad que el que está en la CPU, lo expropia.
 */
public class PoliticaPrioridades implements PoliticaPlanificacion {

    @Override
    public TipoPolitica getTipo() {
        return TipoPolitica.PRIORIDADES;
    }

    /** Extrae el proceso con mejor prioridad (número menor). En empate, el más antiguo en cola. */
    @Override
    public Proceso seleccionarSiguiente(ColaProcesos listos) {
        Proceso[] copia = listos.obtenerListaProcesos();
        if (copia.length == 0) {
            return null;
        }
        Proceso mejor = copia[0];
        for (int i = 1; i < copia.length; i++) {
            // "<" estricto: en empate se queda el que llegó primero
            if (copia[i].getPrioridad() < mejor.getPrioridad()) {
                mejor = copia[i];
            }
        }
        listos.quitar(mejor);
        return mejor;
    }

    /** Expropia si hay algún listo con prioridad estrictamente mejor que el que corre. */
    @Override
    public boolean debeExpropiar(Proceso enEjecucion, ColaProcesos listos, int ciclosEnCpu) {
        Proceso[] copia = listos.obtenerListaProcesos();
        for (int i = 0; i < copia.length; i++) {
            if (copia[i].getPrioridad() < enEjecucion.getPrioridad()) {
                return true;
            }
        }
        return false;
    }
}