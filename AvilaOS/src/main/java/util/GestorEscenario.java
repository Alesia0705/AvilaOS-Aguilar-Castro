package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Guarda y carga en un solo archivo CSV toda la configuración inicial del
 * sistema: parámetros globales, computadores (RAM y política), buffers y
 * procesos. El archivo tiene cuatro secciones, cada una con su título (línea
 * que empieza por #) y su fila de encabezado:
 *
 * <pre>
 * # PARAMETROS GLOBALES
 * DuracionCiclo,Quantum,LatenciaRed
 * 500,4,2
 * # COMPUTADORES
 * Computador,RAM,PoliticaInicial
 * 1,100,Round Robin
 * # BUFFERS
 * IDBuffer,Capacidad,Computador
 * B1,3,1
 * # PROCESOS
 * Nombre,Tipo,Instrucciones,RAM,Prioridad,Deadline,Computador,CiclosCPU,CiclosIO,Buffer,CiclosProdCons,Elementos
 * P1,CPU Bound,20,10,1,1000,1,,,,,
 * </pre>
 *
 * Esta clase solo se ocupa del formato del archivo: devuelve los valores como
 * texto. La validación de tipo y rango de cada dato la hace después la
 * interfaz, con las mismas reglas que al escribirlos a mano. No usa
 * colecciones de java.util.
 */
public final class GestorEscenario {

    private static final String ENCODING = "UTF-8";

    private static final String TITULO_GLOBALES = "# PARAMETROS GLOBALES";
    private static final String TITULO_COMPUTADORES = "# COMPUTADORES";
    private static final String TITULO_BUFFERS = "# BUFFERS";
    private static final String TITULO_PROCESOS = "# PROCESOS";

    private static final String[] ENCABEZADO_GLOBALES = {"DuracionCiclo", "Quantum", "LatenciaRed"};
    private static final String[] ENCABEZADO_COMPUTADORES = {"Computador", "RAM", "PoliticaInicial"};
    private static final String[] ENCABEZADO_BUFFERS = {"IDBuffer", "Capacidad", "Computador"};
    private static final String[] ENCABEZADO_PROCESOS = {"Nombre", "Tipo", "Instrucciones", "RAM", "Prioridad",
        "Deadline", "Computador", "CiclosCPU", "CiclosIO", "Buffer", "CiclosProdCons", "Elementos"};

    private GestorEscenario() {
    }

    /**
     * Datos leídos del archivo, todavía como texto.
     */
    public static final class Escenario {

        /** {duración del ciclo, quantum, latencia de red} */
        public String[] globales;
        /** Cada fila: {número de computador, RAM, política inicial} */
        public String[][] computadores;
        /** Cada fila: {id, capacidad, computador anfitrión} */
        public String[][] buffers;
        /** Cada fila: los 12 campos del proceso, en el orden del encabezado */
        public String[][] procesos;
    }

    // ------------------------------------------------------------------ guardar
    public static void guardar(String ruta, String[] globales, String[][] computadores,
            String[][] buffers, String[][] procesos) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ruta), ENCODING))) {
            escribirSeccion(bw, TITULO_GLOBALES, ENCABEZADO_GLOBALES, new String[][]{globales});
            escribirSeccion(bw, TITULO_COMPUTADORES, ENCABEZADO_COMPUTADORES, computadores);
            escribirSeccion(bw, TITULO_BUFFERS, ENCABEZADO_BUFFERS, buffers);
            escribirSeccion(bw, TITULO_PROCESOS, ENCABEZADO_PROCESOS, procesos);
        }
    }

    private static void escribirSeccion(BufferedWriter bw, String titulo, String[] encabezado,
            String[][] filas) throws IOException {
        bw.write(titulo);
        bw.newLine();
        escribirFila(bw, encabezado);
        for (int i = 0; i < filas.length; i++) {
            escribirFila(bw, filas[i]);
        }
    }

    private static void escribirFila(BufferedWriter bw, String[] campos) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escapar(campos[i]));
        }
        bw.write(sb.toString());
        bw.newLine();
    }

    /** Un campo con coma, comillas o saltos de línea va entre comillas. */
    private static String escapar(String campo) {
        String texto = (campo == null) ? "" : campo;
        if (texto.indexOf(',') >= 0 || texto.indexOf('"') >= 0 || texto.indexOf('\n') >= 0
                || texto.indexOf('\r') >= 0) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }

    // ------------------------------------------------------------------- cargar
    /**
     * Lee el archivo. Lanza FileNotFoundException si no existe e
     * IllegalArgumentException (con el número de línea) si el formato no es el
     * esperado.
     */
    public static Escenario cargar(String ruta) throws IOException {
        File archivo = new File(ruta);
        if (!archivo.isFile()) {
            throw new FileNotFoundException("El archivo " + ruta + " no existe.");
        }

        Escenario e = new Escenario();
        e.globales = null;
        e.computadores = new String[0][];
        e.buffers = new String[0][];
        e.procesos = new String[0][];

        int seccion = 0; // 0 = ninguna, 1 = globales, 2 = computadores, 3 = buffers, 4 = procesos
        boolean encabezadoPendiente = false;
        int numeroLinea = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(archivo), ENCODING))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                numeroLinea++;
                if (numeroLinea == 1 && linea.length() > 0 && linea.charAt(0) == '﻿') {
                    linea = linea.substring(1); // marca de orden de bytes que añaden algunos editores
                }
                linea = linea.trim();
                if (linea.isEmpty()) {
                    continue;
                }

                if (linea.startsWith("#")) {
                    seccion = seccionDe(linea, numeroLinea);
                    encabezadoPendiente = true;
                    continue;
                }
                if (seccion == 0) {
                    throw error(numeroLinea, "hay datos antes del primer título de sección (por ejemplo \""
                            + TITULO_GLOBALES + "\").");
                }
                if (encabezadoPendiente) {
                    encabezadoPendiente = false; // la primera línea tras el título es el encabezado
                    continue;
                }

                String[] campos = separar(linea, numeroLinea);
                int esperados = columnas(seccion);
                if (campos.length != esperados) {
                    throw error(numeroLinea, "se esperaban " + esperados + " columnas en la sección "
                            + nombre(seccion) + " y hay " + campos.length + ".");
                }
                if (seccion == 1) {
                    if (e.globales != null) {
                        throw error(numeroLinea, "la sección de parámetros globales debe tener una sola fila.");
                    }
                    e.globales = campos;
                } else if (seccion == 2) {
                    e.computadores = agregar(e.computadores, campos);
                } else if (seccion == 3) {
                    e.buffers = agregar(e.buffers, campos);
                } else {
                    e.procesos = agregar(e.procesos, campos);
                }
            }
        }

        if (e.globales == null) {
            throw new IllegalArgumentException("Falta la sección \"" + TITULO_GLOBALES + "\" con sus valores.");
        }
        if (e.computadores.length == 0) {
            throw new IllegalArgumentException("Falta la sección \"" + TITULO_COMPUTADORES
                    + "\" o no tiene computadores.");
        }
        return e;
    }

    private static int seccionDe(String titulo, int numeroLinea) {
        String t = titulo.toUpperCase();
        if (t.contains("GLOBALES")) {
            return 1;
        }
        if (t.contains("COMPUTADORES")) {
            return 2;
        }
        if (t.contains("BUFFERS")) {
            return 3;
        }
        if (t.contains("PROCESOS")) {
            return 4;
        }
        throw error(numeroLinea, "título de sección desconocido: \"" + titulo + "\".");
    }

    private static int columnas(int seccion) {
        if (seccion == 1) {
            return ENCABEZADO_GLOBALES.length;
        }
        if (seccion == 2) {
            return ENCABEZADO_COMPUTADORES.length;
        }
        if (seccion == 3) {
            return ENCABEZADO_BUFFERS.length;
        }
        return ENCABEZADO_PROCESOS.length;
    }

    private static String nombre(int seccion) {
        if (seccion == 1) {
            return "de parámetros globales";
        }
        if (seccion == 2) {
            return "de computadores";
        }
        if (seccion == 3) {
            return "de buffers";
        }
        return "de procesos";
    }

    private static IllegalArgumentException error(int numeroLinea, String mensaje) {
        return new IllegalArgumentException("Archivo CSV, línea " + numeroLinea + ": " + mensaje);
    }

    /**
     * Separa una línea por comas respetando los campos entre comillas. Los
     * campos vacíos del final se conservan.
     */
    static String[] separar(String linea, int numeroLinea) {
        String[] campos = new String[8];
        int cantidad = 0;
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                        actual.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    actual.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == ',') {
                if (cantidad == campos.length) {
                    campos = crecer(campos);
                }
                campos[cantidad++] = actual.toString().trim();
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        if (entreComillas) {
            throw error(numeroLinea, "hay unas comillas sin cerrar.");
        }
        if (cantidad == campos.length) {
            campos = crecer(campos);
        }
        campos[cantidad++] = actual.toString().trim();

        String[] resultado = new String[cantidad];
        System.arraycopy(campos, 0, resultado, 0, cantidad);
        return resultado;
    }

    private static String[] crecer(String[] viejo) {
        String[] nuevo = new String[viejo.length * 2];
        System.arraycopy(viejo, 0, nuevo, 0, viejo.length);
        return nuevo;
    }

    private static String[][] agregar(String[][] filas, String[] fila) {
        String[][] nuevo = new String[filas.length + 1][];
        System.arraycopy(filas, 0, nuevo, 0, filas.length);
        nuevo[filas.length] = fila;
        return nuevo;
    }
}