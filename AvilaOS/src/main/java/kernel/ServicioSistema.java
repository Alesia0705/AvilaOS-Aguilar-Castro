package kernel;

import modelo.Proceso;
import modelo.enums.TipoOperacion;
import sincronizacion.Buffer;

/**
 * Lo que el kernel necesita saber del resto del sistema distribuido: dónde
 * están los buffers, cuánto cuesta la red y cómo despertar a un proceso que
 * vive en otro computador. Así el kernel no depende de la clase concreta del
 * sistema.
 * @author Alesia Castro
 */
public interface ServicioSistema {

    /** @return el buffer con ese id, o null si no existe */
    Buffer buscarBuffer(String id);

    /** @return ciclos extra que cuesta cada acceso a un buffer remoto */
    int getLatenciaRed();

    /** Pasa a LISTO a un proceso bloqueado, en el computador donde vive. */
    void despertar(Proceso proceso);
    
    /** Registra un evento en el log del sistema (si hay uno conectado). */
    void registrarEvento(long ciclo, String descripcion);
    
    /**
     * Genera la solicitud de E/S al disco para este proceso y la encola.
     * @return false si no se pudo (no hay disco, ruta invalida, sin permiso...)
     */
    boolean solicitarDisco(Proceso proceso, long ciclo);

    /**
     * Pide al disco guardar (SUSPENDER) o traer de vuelta (REACTIVAR) la imagen
     * de un proceso en el area de swap. Para suspender reserva los bloques de
     * swap (1 bloque por unidad de RAM). Es una solicitud mas en la cola del disco.
     * @return false si no hay disco o no hay espacio en swap
     */
    boolean solicitarSwap(Proceso proceso, TipoOperacion operacion, long ciclo);

    /** El disco termino una solicitud de swap: el kernel del proceso concluye la suspension o reactivacion. */
    void completarSwap(Proceso proceso, TipoOperacion operacion, long ciclo);

    /** Libera los bloques de swap que el proceso tenga reservados. */
    void liberarSwap(Proceso proceso);
}