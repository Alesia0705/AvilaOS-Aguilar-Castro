package disco;

import modelo.enums.TipoPoliticaDisco;

/**
 * Política de planificación del disco. El disco le entrega las solicitudes
 * pendientes (en orden de llegada) y la posición del cabezal, y la política
 * responde cuál atender. Para agregar una política nueva basta con crear otra
 * clase que implemente esta interfaz: el disco no cambia.
 */
public interface PoliticaDisco {

    /**
     * Elige la solicitud que se atiende a continuación.
     *
     * @param pendientes solicitudes en cola, en orden de llegada (no vacío)
     * @param cantidad cuántas posiciones del arreglo son válidas
     * @param cabezal bloque en el que está el cabezal
     * @param totalBloques cantidad de bloques del disco
     * @return la posición (índice) en el arreglo de la solicitud elegida
     */
    int elegir(SolicitudDisco[] pendientes, int cantidad, int cabezal, int totalBloques);

    /**
     * @return la política que implementa esta clase
     */
    TipoPoliticaDisco getTipo();

    /**
     * @return 1 si el cabezal se mueve hacia los bloques mayores, -1 hacia los
     * menores (para mostrarlo en la interfaz); 1 en las políticas sin dirección
     */
    int getDireccion();
}