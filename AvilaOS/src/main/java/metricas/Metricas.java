package metricas;

import hardware.Cpu;
import modelo.Proceso;

/**
 * Cálculo de las métricas de rendimiento. Todas se derivan de datos que ya
 * guardan la Cpu (contadores de ciclos) y el Proceso (ciclos de llegada y de
 * primera ejecución), así que esta clase no guarda estado propio.
 * El "tiempo" se mide en ciclos de reloj.
 */
public class Metricas {

    private Metricas() {
        // Clase de utilidad: solo métodos estáticos
    }

    /** Fracción de ciclos en que la CPU no estuvo ociosa (0.0 a 1.0). */
    public static double utilizacion(Cpu cpu) {
        int total = cpu.getCiclosTotales();
        if (total == 0) {
            return 0.0;
        }
        return (double) (total - cpu.getCiclosOcioso()) / total;
    }

    /** Utilización del sistema completo: todas las CPU juntas. */
    public static double utilizacionSistema(Cpu[] cpus) {
        int total = 0;
        int ociosos = 0;
        for (int i = 0; i < cpus.length; i++) {
            total = total + cpus[i].getCiclosTotales();
            ociosos = ociosos + cpus[i].getCiclosOcioso();
        }
        if (total == 0) {
            return 0.0;
        }
        return (double) (total - ociosos) / total;
    }

    /** Procesos que terminaron todas sus instrucciones (no los que murieron por deadline). */
    public static int procesosCompletados(Proceso[] terminados) {
        int completados = 0;
        for (int i = 0; i < terminados.length; i++) {
            if (terminados[i].getTiempoRestante() <= 0) {
                completados++;
            }
        }
        return completados;
    }

    /** Procesos completados por ciclo de reloj. */
    public static double throughput(Proceso[] terminados, long ciclosTranscurridos) {
        if (ciclosTranscurridos <= 0) {
            return 0.0;
        }
        return (double) procesosCompletados(terminados) / ciclosTranscurridos;
    }

    /** Promedio de (primera ejecución - llegada) de los procesos que ya ejecutaron. */
    public static double tiempoRespuestaPromedio(Proceso[] procesos) {
        long suma = 0;
        int cantidad = 0;
        for (int i = 0; i < procesos.length; i++) {
            if (procesos[i].getCicloPrimeraEjecucion() >= 0) {
                suma = suma + tiempoRespuesta(procesos[i]);
                cantidad++;
            }
        }
        if (cantidad == 0) {
            return 0.0;
        }
        return (double) suma / cantidad;
    }

    /** Fracción de procesos terminados que cumplieron su deadline (0.0 si no hay datos). */
    public static double cumplimientoDeadlines(Proceso[] terminados) {
        if (terminados.length == 0) {
            return 0.0;
        }
        return (double) procesosCompletados(terminados) / terminados.length;
    }

    /**
     * Equidad: índice de Jain sobre los tiempos de respuesta. Vale 1.0 si todos
     * esperaron lo mismo y baja a medida que unos procesos esperan mucho más que
     * otros. Devuelve 0.0 si todavía no hay datos.
     */
    public static double equidad(Proceso[] procesos) {
        double suma = 0;
        double sumaCuadrados = 0;
        int cantidad = 0;
        for (int i = 0; i < procesos.length; i++) {
            if (procesos[i].getCicloPrimeraEjecucion() >= 0) {
                double x = tiempoRespuesta(procesos[i]);
                suma = suma + x;
                sumaCuadrados = sumaCuadrados + x * x;
                cantidad++;
            }
        }
        if (cantidad == 0) {
            return 0.0;
        }
        if (sumaCuadrados == 0) {
            return 1.0;
        }
        return (suma * suma) / (cantidad * sumaCuadrados);
    }

    private static long tiempoRespuesta(Proceso proceso) {
        return proceso.getCicloPrimeraEjecucion() - proceso.getCicloLlegada();
    }
}