package sistema;

import computador.Computador;
import modelo.Proceso;
import planificacion.PoliticaPlanificacion;

/**
 * Sistema distribuido de ÁvilaOS: reúne los computadores del clúster y el
 * parámetro de latencia de red. Es el punto de entrada para crear computadores
 * y enviarles procesos.
 * @author Alesia Castro
 */
public class SistemaDistribuido {

    private Computador[] computadores;
    private int cantidadComputadores;
    private volatile int latenciaRed;

    /**
     * @param latenciaRed ciclos extra que cuesta cada acceso a un buffer remoto (>= 0)
     */
    public SistemaDistribuido(int latenciaRed) {
        if (latenciaRed < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa");
        }
        this.latenciaRed = latenciaRed;
        this.computadores = new Computador[4];
        this.cantidadComputadores = 0;
    }

    /**
     * Crea un computador nuevo y lo agrega al sistema. Los ids van de 1 en adelante.
     */
    public Computador agregarComputador(int capacidadRam, PoliticaPlanificacion politica) {
        if (cantidadComputadores == computadores.length) {
            ampliarComputadores();
        }
        Computador nuevo = new Computador(cantidadComputadores + 1, capacidadRam, politica);
        computadores[cantidadComputadores] = nuevo;
        cantidadComputadores++;
        return nuevo;
    }

    /** Duplica el arreglo a mano (sin java.util). */
    private void ampliarComputadores() {
        Computador[] mayor = new Computador[computadores.length * 2];
        for (int i = 0; i < cantidadComputadores; i++) {
            mayor[i] = computadores[i];
        }
        computadores = mayor;
    }

    /** @return el computador con ese id, o null si no existe */
    public Computador getComputador(int id) {
        for (int i = 0; i < cantidadComputadores; i++) {
            if (computadores[i].getId() == id) {
                return computadores[i];
            }
        }
        return null;
    }

    /** @return false si el computador asignado al proceso no existe */
    public boolean enviarProceso(Proceso proceso) {
        if (proceso == null) {
            return false;
        }
        Computador destino = getComputador(proceso.getComputadorAsignado());
        if (destino == null) {
            return false;
        }
        destino.recibirProceso(proceso);
        return true;
    }

    public int getCantidadComputadores() { return cantidadComputadores; }

    public int getLatenciaRed() { return latenciaRed; }

    public boolean setLatenciaRed(int latenciaRed) {
        if (latenciaRed < 0) {
            return false;
        }
        this.latenciaRed = latenciaRed;
        return true;
    }
}