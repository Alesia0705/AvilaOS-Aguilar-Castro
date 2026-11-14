package util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import modelo.enums.TipoOperacion;
import modelo.enums.TipoPoliticaDisco;

/**
 * Lee el JSON de procesos y configuración del Proyecto 2: parámetros globales
 * (duración del ciclo, tamaño del swap, política inicial del disco), el
 * computador (RAM, política de CPU y quantum) y la lista de procesos de E/S de
 * archivos. Valida el tipo y el rango de cada dato y, si algo está mal, lanza
 * una IllegalArgumentException cuyo mensaje dice qué campo falló. No guarda
 * estado: solo lee y devuelve los datos.
 */
public final class GestorProcesosJson {

    private GestorProcesosJson() {
        // Clase de utilidad: solo métodos estáticos
    }

    /**
     * Datos de un proceso de E/S de archivos tal como vienen en el JSON.
     */
    public static final class DatosProceso {

        public final String nombre;
        public final int memoria;
        public final int deadline;
        public final int instrucciones;
        public final String ruta;
        public final TipoOperacion operacion;
        public final int bloquesArchivo;

        DatosProceso(String nombre, int memoria, int deadline, int instrucciones,
                String ruta, TipoOperacion operacion, int bloquesArchivo) {
            this.nombre = nombre;
            this.memoria = memoria;
            this.deadline = deadline;
            this.instrucciones = instrucciones;
            this.ruta = ruta;
            this.operacion = operacion;
            this.bloquesArchivo = bloquesArchivo;
        }
    }

    /**
     * Todo lo que trae el JSON de procesos y configuración.
     */
    public static final class Configuracion {

        public final int duracionCicloMs;
        public final int bloquesSwap;
        public final TipoPoliticaDisco politicaDisco;
        public final int ramComputador;
        /** Nombre de la política de CPU, como lo espera FabricaSistema ("FCFS", "EDF", "Round Robin", "Prioridades"). */
        public final String politicaCpu;
        public final int quantum;
        public final DatosProceso[] procesos;

        Configuracion(int duracionCicloMs, int bloquesSwap, TipoPoliticaDisco politicaDisco,
                int ramComputador, String politicaCpu, int quantum, DatosProceso[] procesos) {
            this.duracionCicloMs = duracionCicloMs;
            this.bloquesSwap = bloquesSwap;
            this.politicaDisco = politicaDisco;
            this.ramComputador = ramComputador;
            this.politicaCpu = politicaCpu;
            this.quantum = quantum;
            this.procesos = procesos;
        }
    }

    /**
     * Lee y valida el archivo.
     *
     * @throws IOException si no se puede leer el archivo
     * @throws IllegalArgumentException si el contenido no es válido (el mensaje
     * indica el campo)
     */
    public static Configuracion cargar(String ruta) throws IOException {
        if (ruta == null || ruta.trim().isEmpty()) {
            throw new IllegalArgumentException("No se indicó el archivo de procesos.");
        }
        JsonElement raiz;
        try (Reader lector = new FileReader(ruta)) {
            raiz = JsonParser.parseReader(lector);
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException("El archivo no es un JSON válido: " + ex.getMessage());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("No se pudo interpretar el archivo como JSON.");
        }
        if (raiz == null || !raiz.isJsonObject()) {
            throw new IllegalArgumentException("El archivo debe contener un objeto JSON con globalConfig, computer y processes.");
        }
        JsonObject objeto = raiz.getAsJsonObject();

        JsonObject global = objetoObligatorio(objeto, "globalConfig", "el archivo");
        int duracion = enteroObligatorio(global, "cycleDurationMs", 1, 60000, "globalConfig");
        int swap = enteroObligatorio(global, "swapSizeBlocks", 1, 500, "globalConfig");
        TipoPoliticaDisco politicaDisco = politicaDisco(textoObligatorio(global, "initialDiskPolicy", "globalConfig"));

        JsonObject computador = objetoObligatorio(objeto, "computer", "el archivo");
        int ram = enteroObligatorio(computador, "ramCapacity", 1, 1000000, "computer");
        String politicaCpu = politicaCpu(textoObligatorio(computador, "policy", "computer"));
        int quantum = enteroObligatorio(computador, "quantum", 0, 1000, "computer");
        if ("Round Robin".equals(politicaCpu) && quantum < 1) {
            throw new IllegalArgumentException("computer.quantum debe ser 1 o más cuando la política es Round Robin.");
        }
        if (quantum < 1) {
            quantum = 4; // las demás políticas no usan quantum: se deja el valor por defecto
        }

        JsonElement lista = objeto.get("processes");
        if (lista == null || !lista.isJsonArray()) {
            throw new IllegalArgumentException("Falta la lista \"processes\" (debe ser un arreglo).");
        }
        JsonArray arreglo = lista.getAsJsonArray();
        DatosProceso[] procesos = new DatosProceso[arreglo.size()];
        for (int i = 0; i < procesos.length; i++) {
            procesos[i] = leerProceso(arreglo.get(i), i + 1);
            for (int j = 0; j < i; j++) {
                if (procesos[j].nombre.equals(procesos[i].nombre)) {
                    throw new IllegalArgumentException("El nombre de proceso \"" + procesos[i].nombre + "\" está repetido.");
                }
            }
        }
        return new Configuracion(duracion, swap, politicaDisco, ram, politicaCpu, quantum, procesos);
    }

    private static DatosProceso leerProceso(JsonElement elemento, int numero) {
        String contexto = "processes[" + numero + "]";
        if (elemento == null || !elemento.isJsonObject()) {
            throw new IllegalArgumentException(contexto + " debe ser un objeto.");
        }
        JsonObject p = elemento.getAsJsonObject();
        String nombre = textoObligatorio(p, "name", contexto).trim();
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException(contexto + ".name no puede estar vacío.");
        }
        int memoria = enteroObligatorio(p, "memory", 1, 1000000, contexto);
        int deadline = enteroObligatorio(p, "deadline", 1, 1000000, contexto);
        // La primera instrucción es la operación de archivo; hacen falta más para terminar después
        int instrucciones = enteroObligatorio(p, "instructions", 2, 100000, contexto);
        String ruta = textoObligatorio(p, "targetPath", contexto).trim();
        if (!ruta.startsWith("/") || ruta.length() < 2) {
            throw new IllegalArgumentException(contexto + ".targetPath debe ser una ruta que empiece con \"/\" (por ejemplo /sistema/boot.bin).");
        }
        TipoOperacion operacion = operacion(textoObligatorio(p, "operation", contexto), contexto);
        int bloques = enteroObligatorio(p, "fileSizeBlocks", 0, 500, contexto);
        if (operacion == TipoOperacion.CREAR && bloques < 1) {
            throw new IllegalArgumentException(contexto + ".fileSizeBlocks debe ser 1 o más cuando la operación es CREATE.");
        }
        return new DatosProceso(nombre, memoria, deadline, instrucciones, ruta, operacion, bloques);
    }

    // ---------- Conversión de nombres ----------
    private static TipoOperacion operacion(String texto, String contexto) {
        String t = texto.trim().toUpperCase();
        if (t.equals("CREATE") || t.equals("CREAR")) {
            return TipoOperacion.CREAR;
        }
        if (t.equals("READ") || t.equals("LEER")) {
            return TipoOperacion.LEER;
        }
        if (t.equals("UPDATE") || t.equals("ACTUALIZAR")) {
            return TipoOperacion.ACTUALIZAR;
        }
        if (t.equals("DELETE") || t.equals("ELIMINAR")) {
            return TipoOperacion.ELIMINAR;
        }
        throw new IllegalArgumentException(contexto + ".operation \"" + texto
                + "\" no es válida (use CREATE, READ, UPDATE o DELETE).");
    }

    private static TipoPoliticaDisco politicaDisco(String texto) {
        String t = texto.trim().toUpperCase().replace("-", "_");
        if (t.equals("FIFO")) {
            return TipoPoliticaDisco.FIFO;
        }
        if (t.equals("SSTF")) {
            return TipoPoliticaDisco.SSTF;
        }
        if (t.equals("SCAN")) {
            return TipoPoliticaDisco.SCAN;
        }
        if (t.equals("C_SCAN") || t.equals("CSCAN")) {
            return TipoPoliticaDisco.C_SCAN;
        }
        throw new IllegalArgumentException("globalConfig.initialDiskPolicy \"" + texto
                + "\" no es válida (use FIFO, SSTF, SCAN o C-SCAN).");
    }

    private static String politicaCpu(String texto) {
        String t = texto.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        if (t.equals("FCFS")) {
            return "FCFS";
        }
        if (t.equals("EDF")) {
            return "EDF";
        }
        if (t.equals("ROUND_ROBIN") || t.equals("RR")) {
            return "Round Robin";
        }
        if (t.equals("PRIORIDADES") || t.equals("PRIORITY")) {
            return "Prioridades";
        }
        throw new IllegalArgumentException("computer.policy \"" + texto
                + "\" no es válida (use FCFS, EDF, Round Robin o Prioridades).");
    }

    // ---------- Lectura de campos con validación ----------
    private static JsonObject objetoObligatorio(JsonObject padre, String clave, String contexto) {
        JsonElement e = padre.get(clave);
        if (e == null || !e.isJsonObject()) {
            throw new IllegalArgumentException("Falta el objeto \"" + clave + "\" en " + contexto + ".");
        }
        return e.getAsJsonObject();
    }

    private static String textoObligatorio(JsonObject padre, String clave, String contexto) {
        JsonElement e = padre.get(clave);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Falta el texto \"" + clave + "\" en " + contexto + ".");
        }
        return e.getAsString();
    }

    private static int enteroObligatorio(JsonObject padre, String clave, int minimo, int maximo, String contexto) {
        JsonElement e = padre.get(clave);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(contexto + "." + clave + " debe ser un número entero.");
        }
        double valor = e.getAsDouble();
        if (valor != Math.floor(valor) || Double.isInfinite(valor)) {
            throw new IllegalArgumentException(contexto + "." + clave + " debe ser un número entero (sin decimales).");
        }
        if (valor < minimo || valor > maximo) {
            throw new IllegalArgumentException(contexto + "." + clave + " debe estar entre " + minimo + " y " + maximo + ".");
        }
        return (int) valor;
    }
}