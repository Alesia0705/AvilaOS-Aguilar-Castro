package disco;

import modelo.Proceso;
import modelo.enums.TipoOperacion;
import modelo.enums.TipoProceso;

/**
 * Prueba aislada del disco y las 4 politicas (FIFO, SSTF, SCAN, C-SCAN)
 * verificando los resultados exactos del Anexo y los casos P1 a P4 del enunciado.
 */
public class PruebaDiscoAnexo {

    public static void main(String[] args) {
        System.out.println("=== PRUEBA DEL ANEXO (Cabezal=50, Bloques=200, Cola=[82, 17, 43, 140, 24, 16, 190]) ===");
        int[] colaAnexo = {82, 17, 43, 140, 24, 16, 190};
        ejecutarPrueba("FIFO (esperado: 518)", new PoliticaFIFO(), 200, 50, colaAnexo);
        ejecutarPrueba("SSTF (esperado: 208)", new PoliticaSSTF(), 200, 50, colaAnexo);
        ejecutarPrueba("SCAN (esperado: 332)", new PoliticaSCAN(1), 200, 50, colaAnexo);
        ejecutarPrueba("C-SCAN (esperado: 192)", new PoliticaCSCAN(), 200, 50, colaAnexo);

        System.out.println("\n=== CASOS P1 A P4 DEL ENUNCIADO (Cabezal=50, Cola=[95, 180, 34, 119, 11, 123, 62, 64]) ===");
        int[] colaCasos = {95, 180, 34, 119, 11, 123, 62, 64};
        ejecutarPrueba("Caso P1-FIFO", new PoliticaFIFO(), 200, 50, colaCasos);
        ejecutarPrueba("Caso P2-SSTF", new PoliticaSSTF(), 200, 50, colaCasos);
        ejecutarPrueba("Caso P3-SCAN", new PoliticaSCAN(1), 200, 50, colaCasos);
        ejecutarPrueba("Caso P4-C-SCAN", new PoliticaCSCAN(), 200, 50, colaCasos);

        System.out.println("\n=== PRUEBA ASIGNACION ENCADENADA Y SWAP ===");
        Disco disco = new Disco(20, 5, 0, new PoliticaFIFO());
        int b1 = disco.asignarArchivo("/sistema/boot.bin", 4);
        int b2 = disco.asignarArchivo("/usuarios/notas.txt", 3);
        int s1 = disco.asignarSwap("P_Susp1", 4);
        System.out.println("boot.bin primer bloque: " + b1 + " | notas.txt primer bloque: " + b2 + " | swap: " + s1);
        System.out.println("Bloques libres normales: " + disco.contarBloquesLibres(false)
                + " | Libres swap: " + disco.contarBloquesLibres(true));
        disco.liberarCadena(b1);
        System.out.println("Tras eliminar boot.bin -> Libres normales: " + disco.contarBloquesLibres(false));
    }

    private static void ejecutarPrueba(String titulo, PoliticaDisco politica, int totalBloques, int cabezalInicial, int[] bloques) {
        Disco disco = new Disco(totalBloques, 0, cabezalInicial, politica);
        for (int i = 0; i < bloques.length; i++) {
            Proceso dummy = new Proceso("P_" + bloques[i], 2, 16, 1, 500, TipoProceso.IO_BOUND, 1);
            disco.encolarSolicitud(new SolicitudDisco(dummy, bloques[i], TipoOperacion.LEER, 0));
        }

        StringBuilder orden = new StringBuilder();
        long ciclo = 1;
        int prevAtendidas = 0;
        SolicitudDisco enCurso = null;

        while (disco.getSolicitudesAtendidas() < bloques.length) {
            disco.onTick(ciclo);
            if (disco.getSolicitudActual() != null) {
                enCurso = disco.getSolicitudActual();
            }
            if (disco.getSolicitudesAtendidas() > prevAtendidas) {
                if (orden.length() > 0) {
                    orden.append(" -> ");
                }
                orden.append(enCurso.getBloque());
                prevAtendidas = disco.getSolicitudesAtendidas();
            }
            ciclo++;
        }

        System.out.println(titulo);
        System.out.println("  Orden: " + orden);
        System.out.println("  Desplazamiento total: " + disco.getDesplazamientoTotal() + " bloques");
    }
}