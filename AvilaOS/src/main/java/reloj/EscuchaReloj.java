package reloj;

/**
 * Componente que quiere enterarse de cada ciclo del reloj global.
 * El reloj llama a onTick una vez por ciclo, y no pasa al siguiente
 * hasta que todos los escuchas terminaron de ejecutarlo.
 *
 * @author Alesia Castro
 */
public interface EscuchaReloj {

    /**
     * Se llama una vez por ciclo de reloj.
     *
     * @param ciclo número del ciclo actual, empezando en 1
     */
    void onTick(long ciclo);
}