package disco;

/**
 * Representa un bloque individual del disco simulado (SD).
 * Soporta asignacion encadenada mediante el puntero siguienteBloque (-1 si es el fin).
 */
public class BloqueDisco {

    private final int numero;
    private boolean ocupado;
    private boolean esSwap;
    private String propietario;
    private int siguienteBloque;

    public BloqueDisco(int numero) {
        this.numero = numero;
        this.ocupado = false;
        this.esSwap = false;
        this.propietario = null;
        this.siguienteBloque = -1;
    }

    public void ocupar(String propietario, int siguienteBloque) {
        this.ocupado = true;
        this.propietario = propietario;
        this.siguienteBloque = siguienteBloque;
    }

    public void liberar() {
        this.ocupado = false;
        this.propietario = null;
        this.siguienteBloque = -1;
    }

    public int getNumero() {
        return numero;
    }

    public boolean isOcupado() {
        return ocupado;
    }

    public boolean isEsSwap() {
        return esSwap;
    }

    public void setEsSwap(boolean esSwap) {
        this.esSwap = esSwap;
    }

    public String getPropietario() {
        return propietario;
    }

    public int getSiguienteBloque() {
        return siguienteBloque;
    }

    public void setSiguienteBloque(int siguienteBloque) {
        this.siguienteBloque = siguienteBloque;
    }

    @Override
    public String toString() {
        if (!ocupado) {
            return "Bloque " + numero + (esSwap ? " [SWAP LIBRE]" : " [LIBRE]");
        }
        return "Bloque " + numero + " [" + propietario + " -> " + siguienteBloque + "]";
    }
}