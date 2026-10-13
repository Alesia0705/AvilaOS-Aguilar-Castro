package util;

import modelo.ConfiguracionComputador;
import modelo.Proceso;
import modelo.enums.TipoProceso;
import sincronizacion.ColaProcesos;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidad encargada de la lectura y escritura en disco de archivos CSV
 * para la configuración del sistema y la lista de procesos.
 */
public class GestorArchivo {

    private static final String ENCODING = "UTF-8";

    // --- 1. GUARDAR CONFIGURACIÓN COMPLETA EN CSV ---
    /**
     * Guarda la configuración global y el detalle de cada computador en configuracion.csv.
     */
    public static void guardarConfiguracionCompleta(
            String rutaArchivo,
            int duracionCiclo,
            int quantum,
            int latenciaRed,
            List<ConfiguracionComputador> listaComputadores) throws IOException {

        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(rutaArchivo), ENCODING))) {
            // Sección Global
            bw.write("# PARAMETROS GLOBALES");
            bw.newLine();
            bw.write("DuracionCiclo,Quantum,LatenciaRed,NumComputadores");
            bw.newLine();
            bw.write(duracionCiclo + "," + quantum + "," + latenciaRed + "," + listaComputadores.size());
            bw.newLine();
            bw.newLine();

            // Sección Computadores
            bw.write("# CONFIGURACION POR COMPUTADOR");
            bw.newLine();
            bw.write("ID_PC,RAM_MB,PoliticaInicial");
            bw.newLine();

            for (ConfiguracionComputador pc : listaComputadores) {
                bw.write(pc.getIdPC() + "," + pc.getMemoriaRAM() + "," + pc.getPoliticaPlanificacion());
                bw.newLine();
            }
        }
    }

    // --- 2. CARGAR CONFIGURACIÓN DESDE CSV ---
    /**
     * Lee configuracion.csv y retorna un objeto contenedor con los datos globales y la lista de PCs.
     */
    public static DatosConfiguracion cargarConfiguracion(String rutaArchivo) throws IOException {
        File file = new File(rutaArchivo);
        if (!file.exists()) {
            throw new FileNotFoundException("El archivo " + rutaArchivo + " no existe.");
        }

        int duracionCiclo = 1000;
        int quantum = 3;
        int latenciaRed = 2;
        List<ConfiguracionComputador> listaComps = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), ENCODING))) {
            String linea;
            boolean leyendoGlobales = false;
            boolean leyendoComputadores = false;

            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }

                if (linea.startsWith("DuracionCiclo")) {
                    leyendoGlobales = true;
                    leyendoComputadores = false;
                    continue;
                }

                if (linea.startsWith("ID_PC")) {
                    leyendoGlobales = false;
                    leyendoComputadores = true;
                    continue;
                }

                String[] tokens = linea.split(",");

                if (leyendoGlobales && tokens.length >= 4) {
                    duracionCiclo = Integer.parseInt(tokens[0].trim());
                    quantum = Integer.parseInt(tokens[1].trim());
                    latenciaRed = Integer.parseInt(tokens[2].trim());
                    leyendoGlobales = false;
                } else if (leyendoComputadores && tokens.length >= 3) {
                    int idPC = Integer.parseInt(tokens[0].trim());
                    int ram = Integer.parseInt(tokens[1].trim());
                    String politica = tokens[2].trim();
                    listaComps.add(new ConfiguracionComputador(idPC, ram, politica));
                }
            }
        }

        return new DatosConfiguracion(duracionCiclo, quantum, latenciaRed, listaComps);
    }

    // --- 3. GUARDAR COLA DE PROCESOS EN CSV ---
    /**
     * Exporta los procesos presentes en la cola hacia procesos.csv.
     */
    public static void guardarProcesosCSV(String rutaArchivo, ColaProcesos cola) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(rutaArchivo), ENCODING))) {
            bw.write("ID,Nombre,Instrucciones,RAM_MB,Prioridad,Deadline,Tipo,Computador,CiclosCPU,CiclosIO,IDBuffer,CiclosProdCons,CantidadElementos");
            bw.newLine();

            // Obtenemos copia o iteración de procesos
            List<Proceso> listaProcesos = cola.obtenerListaProcesos();
            for (Proceso p : listaProcesos) {
                StringBuilder sb = new StringBuilder();
                sb.append(p.getId()).append(",")
                  .append(p.getNombre()).append(",")
                  .append(p.getInstruccionesTotales()).append(",")
                  .append(p.getMemoriaRAM()).append(",")
                  .append(p.getPrioridad()).append(",")
                  .append(p.getDeadline()).append(",")
                  .append(p.getTipo().name()).append(",")
                  .append(p.getComputadorAsignado()).append(",")
                  .append(p.getCiclosCpu()).append(",")
                  .append(p.getCiclosIo()).append(",")
                  .append(p.getIdBuffer() != null ? p.getIdBuffer() : "").append(",")
                  .append(p.getCiclosProdCons()).append(",")
                  .append(p.getCantidadElementos());

                bw.write(sb.toString());
                bw.newLine();
            }
        }
    }

    // --- CLASE CONTENEDORA INTERNA ---
    public static class DatosConfiguracion {
        private final int duracionCiclo;
        private final int quantum;
        private final int latenciaRed;
        private final List<ConfiguracionComputador> listaComputadores;

        public DatosConfiguracion(int duracionCiclo, int quantum, int latenciaRed, List<ConfiguracionComputador> listaComputadores) {
            this.duracionCiclo = duracionCiclo;
            this.quantum = quantum;
            this.latenciaRed = latenciaRed;
            this.listaComputadores = listaComputadores;
        }

        public int getDuracionCiclo() { return duracionCiclo; }
        public int getQuantum() { return quantum; }
        public int getLatenciaRed() { return latenciaRed; }
        public List<ConfiguracionComputador> getListaComputadores() { return listaComputadores; }
    }
}