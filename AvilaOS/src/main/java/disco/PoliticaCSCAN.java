package disco;

import modelo.enums.TipoPoliticaDisco;

/**
 * Politica C-SCAN (Circular SCAN): atiende solicitudes en sentido ascendente
 * hasta el extremo superior, salta al bloque 0 y continua subiendo.
 */
public class PoliticaCSCAN implements PoliticaDisco {

    @Override
    public int elegir(SolicitudDisco[] pendientes, int cantidad, int cabezal, int totalBloques) {
        int mejorIdx = -1;
        int mejorBloque = Integer.MAX_VALUE;

        // 1. Buscar la solicitud mas cercana >= cabezal
        for (int i = 0; i < cantidad; i++) {
            int b = pendientes[i].getBloque();
            if (b >= cabezal && b < mejorBloque) {
                mejorBloque = b;
                mejorIdx = i;
            }
        }
        if (mejorIdx != -1) {
            return mejorIdx;
        }

        // 2. Si no hay mayores o iguales, salta a 0 y toma la de menor numero de bloque
        for (int i = 0; i < cantidad; i++) {
            int b = pendientes[i].getBloque();
            if (b < mejorBloque) {
                mejorBloque = b;
                mejorIdx = i;
            }
        }
        return mejorIdx;
    }

    @Override
    public TipoPoliticaDisco getTipo() {
        return TipoPoliticaDisco.C_SCAN;
    }

    @Override
    public int getDireccion() {
        return 1;
    }
}