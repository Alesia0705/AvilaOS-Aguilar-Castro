package kernel;

import modelo.Proceso;
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
}