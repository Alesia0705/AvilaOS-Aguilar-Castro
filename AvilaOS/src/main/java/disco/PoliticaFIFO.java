package disco;

import modelo.enums.TipoPoliticaDisco;

/**
 * Politica FIFO (First-In, First-Out): atiende las solicitudes en estricto orden de llegada.
 */
public class PoliticaFIFO implements PoliticaDisco {

    @Override
    public int elegir(SolicitudDisco[] pendientes, int cantidad, int cabezal, int totalBloques) {
        return 0;
    }

    @Override
    public TipoPoliticaDisco getTipo() {
        return TipoPoliticaDisco.FIFO;
    }

    @Override
    public int getDireccion() {
        return 1;
    }
}