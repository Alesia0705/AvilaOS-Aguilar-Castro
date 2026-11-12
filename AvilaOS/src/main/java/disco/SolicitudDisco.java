package disco;

import modelo.Proceso;
import modelo.enums.TipoOperacion;

/**
 * Una solicitud de E/S al disco. La genera un proceso de E/S cuando obtiene la
 * CPU y se queda en la cola del disco hasta que el disco la atiende.
 */
public class SolicitudDisco {

    private final Proceso proceso;
    private final int bloque;
    private final TipoOperacion operacion;
    private final long cicloLlegada;

    public SolicitudDisco(Proceso proceso, int bloque, TipoOperacion operacion, long cicloLlegada) {
        if (proceso == null || operacion == null) {
            throw new IllegalArgumentException("La solicitud necesita un proceso y una operación");
        }
        if (bloque < 0) {
            throw new IllegalArgumentException("El bloque no puede ser negativo");
        }
        this.proceso = proceso;
        this.bloque = bloque;
        this.operacion = operacion;
        this.cicloLlegada = cicloLlegada;
    }

    public Proceso getProceso() {
        return proceso;
    }

    public int getBloque() {
        return bloque;
    }

    public TipoOperacion getOperacion() {
        return operacion;
    }

    public long getCicloLlegada() {
        return cicloLlegada;
    }
}