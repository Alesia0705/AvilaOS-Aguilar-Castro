package disco;

import modelo.enums.TipoPoliticaDisco;

/**
 * Politica SCAN (algoritmo del elevador): avanza en una direccion atendiendo
 * solicitudes hasta llegar al extremo del disco y luego invierte su sentido.
 */
public class PoliticaSCAN implements PoliticaDisco {

    private int direccion; // 1 hacia bloques mayores, -1 hacia bloques menores

    public PoliticaSCAN() {
        this.direccion = 1;
    }

    public PoliticaSCAN(int direccionInicial) {
        this.direccion = (direccionInicial < 0) ? -1 : 1;
    }

    @Override
    public int elegir(SolicitudDisco[] pendientes, int cantidad, int cabezal, int totalBloques) {
        if (direccion == 1) {
            int idxSubiendo = buscarMenorMayorOIgual(pendientes, cantidad, cabezal);
            if (idxSubiendo != -1) {
                return idxSubiendo;
            }
            // No hay solicitudes hacia arriba: llega al extremo superior e invierte sentido
            direccion = -1;
            return buscarMayorMenorOIgual(pendientes, cantidad, cabezal);
        } else {
            int idxBajando = buscarMayorMenorOIgual(pendientes, cantidad, cabezal);
            if (idxBajando != -1) {
                return idxBajando;
            }
            // No hay solicitudes hacia abajo: llega a 0 e invierte sentido
            direccion = 1;
            return buscarMenorMayorOIgual(pendientes, cantidad, cabezal);
        }
    }

    private int buscarMenorMayorOIgual(SolicitudDisco[] pendientes, int cantidad, int cabezal) {
        int mejorIdx = -1;
        int mejorBloque = Integer.MAX_VALUE;
        for (int i = 0; i < cantidad; i++) {
            int b = pendientes[i].getBloque();
            if (b >= cabezal && b < mejorBloque) {
                mejorBloque = b;
                mejorIdx = i;
            }
        }
        return mejorIdx;
    }

    private int buscarMayorMenorOIgual(SolicitudDisco[] pendientes, int cantidad, int cabezal) {
        int mejorIdx = -1;
        int mejorBloque = Integer.MIN_VALUE;
        for (int i = 0; i < cantidad; i++) {
            int b = pendientes[i].getBloque();
            if (b <= cabezal && b > mejorBloque) {
                mejorBloque = b;
                mejorIdx = i;
            }
        }
        return mejorIdx;
    }

    @Override
    public TipoPoliticaDisco getTipo() {
        return TipoPoliticaDisco.SCAN;
    }

    @Override
    public int getDireccion() {
        return direccion;
    }
}