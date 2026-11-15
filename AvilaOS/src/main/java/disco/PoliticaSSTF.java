package disco;

import modelo.enums.TipoPoliticaDisco;

/**
 * Politica SSTF (Shortest Seek Time First): elige la solicitud mas cercana
 * a la posicion actual del cabezal.
 */
public class PoliticaSSTF implements PoliticaDisco {

    @Override
    public int elegir(SolicitudDisco[] pendientes, int cantidad, int cabezal, int totalBloques) {
        int mejorIndice = 0;
        int menorDistancia = Math.abs(pendientes[0].getBloque() - cabezal);

        for (int i = 1; i < cantidad; i++) {
            int distancia = Math.abs(pendientes[i].getBloque() - cabezal);
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                mejorIndice = i;
            }
        }
        return mejorIndice;
    }

    @Override
    public TipoPoliticaDisco getTipo() {
        return TipoPoliticaDisco.SSTF;
    }

    @Override
    public int getDireccion() {
        return 1;
    }
}