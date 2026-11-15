package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import filesystem.NodoArchivo;
import filesystem.SistemaArchivos;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import modelo.enums.ModoUsuario;
import modelo.enums.Permiso;

/**
 * Lee, valida y guarda la estructura jerarquica del sistema de archivos en formato JSON
 * (directorios, archivos, dueno, permisos, bloque inicial y tamano en bloques).
 */
public final class GestorArchivosJson {

    private GestorArchivosJson() {
    }

    public static final class DatosDirectorio {

        public final String ruta;
        public final String dueno;
        public final Permiso permiso;

        DatosDirectorio(String ruta, String dueno, Permiso permiso) {
            this.ruta = ruta;
            this.dueno = dueno;
            this.permiso = permiso;
        }
    }

    public static final class DatosArchivo {

        public final String ruta;
        public final int bloqueInicio;
        public final int tamanoBloques;
        public final String dueno;
        public final Permiso permiso;

        DatosArchivo(String ruta, int bloqueInicio, int tamanoBloques, String dueno, Permiso permiso) {
            this.ruta = ruta;
            this.bloqueInicio = bloqueInicio;
            this.tamanoBloques = tamanoBloques;
            this.dueno = dueno;
            this.permiso = permiso;
        }
    }

    public static final class DatosSistemaArchivos {

        public final DatosDirectorio[] directorios;
        public final DatosArchivo[] archivos;
        /** Tamaño del disco según diskConfig; -1 si el JSON no lo trae. */
        public final int totalBloques;
        /** Posición inicial del cabezal según diskConfig; -1 si el JSON no lo trae. */
        public final int cabezalInicial;

        DatosSistemaArchivos(DatosDirectorio[] directorios, DatosArchivo[] archivos,
                int totalBloques, int cabezalInicial) {
            this.directorios = directorios;
            this.archivos = archivos;
            this.totalBloques = totalBloques;
            this.cabezalInicial = cabezalInicial;
        }
    }

    /**
     * Lee y valida el archivo JSON del sistema de archivos.
     */
    public static DatosSistemaArchivos cargar(String rutaJson) throws IOException {
        if (rutaJson == null || rutaJson.trim().isEmpty()) {
            throw new IllegalArgumentException("No se indico el archivo JSON del sistema de archivos.");
        }
        JsonElement raiz;
        try (Reader lector = new FileReader(rutaJson)) {
            raiz = JsonParser.parseReader(lector);
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException("El archivo no es un JSON valido: " + ex.getMessage());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("No se pudo interpretar el archivo como JSON.");
        }
        if (raiz == null || !raiz.isJsonObject()) {
            throw new IllegalArgumentException("El archivo debe contener un objeto JSON con la lista \"files\".");
        }
        JsonObject objeto = raiz.getAsJsonObject();
        if (objeto.has("fileSystem")) {
            return cargarFormatoEnunciado(objeto);
        }

        // Directorios (opcional: si no viene, se crean automaticamente al cargar los archivos)
        DatosDirectorio[] directorios = new DatosDirectorio[0];
        JsonElement elemDirs = objeto.get("directories");
        if (elemDirs != null && !elemDirs.isJsonNull()) {
            if (!elemDirs.isJsonArray()) {
                throw new IllegalArgumentException("El campo \"directories\" debe ser un arreglo.");
            }
            JsonArray arrDirs = elemDirs.getAsJsonArray();
            directorios = new DatosDirectorio[arrDirs.size()];
            for (int i = 0; i < directorios.length; i++) {
                directorios[i] = leerDirectorio(arrDirs.get(i), i + 1);
            }
        }

        // Archivos (obligatorio)
        JsonElement elemFiles = objeto.get("files");
        if (elemFiles == null || !elemFiles.isJsonArray()) {
            throw new IllegalArgumentException("Falta la lista \"files\" (debe ser un arreglo).");
        }
        JsonArray arrFiles = elemFiles.getAsJsonArray();
        DatosArchivo[] archivos = new DatosArchivo[arrFiles.size()];
        for (int i = 0; i < archivos.length; i++) {
            archivos[i] = leerArchivo(arrFiles.get(i), i + 1);
            for (int j = 0; j < i; j++) {
                if (archivos[j].ruta.equals(archivos[i].ruta)) {
                    throw new IllegalArgumentException("La ruta de archivo \"" + archivos[i].ruta + "\" esta repetida.");
                }
            }
        }

        return new DatosSistemaArchivos(directorios, archivos, -1, -1);
    }


    /**
     * Formato del enunciado: { "diskConfig": {totalBlocks, initialHead},
     * "fileSystem": [ {path, type, owner, permissions, startBlock, blocks} ] }.
     */
    private static DatosSistemaArchivos cargarFormatoEnunciado(JsonObject objeto) {
        JsonElement elemDisco = objeto.get("diskConfig");
        if (elemDisco == null || !elemDisco.isJsonObject()) {
            throw new IllegalArgumentException("Falta el objeto \"diskConfig\".");
        }
        JsonObject disco = elemDisco.getAsJsonObject();
        int total = enteroObligatorio(disco, "totalBlocks", 1, 100000, "diskConfig");
        int cabezal = enteroObligatorio(disco, "initialHead", 0, 100000, "diskConfig");
        if (cabezal >= total) {
            throw new IllegalArgumentException("diskConfig.initialHead (" + cabezal
                    + ") debe ser menor que totalBlocks (" + total + ").");
        }
        JsonElement elemFs = objeto.get("fileSystem");
        if (elemFs == null || !elemFs.isJsonArray()) {
            throw new IllegalArgumentException("\"fileSystem\" debe ser un arreglo.");
        }
        JsonArray arr = elemFs.getAsJsonArray();
        DatosDirectorio[] dirs = new DatosDirectorio[arr.size()];
        DatosArchivo[] archs = new DatosArchivo[arr.size()];
        int nd = 0;
        int na = 0;
        for (int i = 0; i < arr.size(); i++) {
            String contexto = "fileSystem[" + (i + 1) + "]";
            if (!arr.get(i).isJsonObject()) {
                throw new IllegalArgumentException(contexto + " debe ser un objeto.");
            }
            JsonObject obj = arr.get(i).getAsJsonObject();
            String ruta = textoObligatorio(obj, "path", contexto).trim();
            if (!ruta.startsWith("/") || ruta.length() < 2) {
                throw new IllegalArgumentException(contexto + ".path debe empezar con \"/\" (por ejemplo /sistema).");
            }
            String tipo = textoObligatorio(obj, "type", contexto).trim().toUpperCase();
            String dueno = textoOpcional(obj, "owner", "admin");
            Permiso permiso = leerPermiso(textoOpcional(obj, "permissions", "PUBLICO"), contexto);
            for (int j = 0; j < nd; j++) {
                if (dirs[j].ruta.equals(ruta)) {
                    throw new IllegalArgumentException("La ruta \"" + ruta + "\" esta repetida.");
                }
            }
            for (int j = 0; j < na; j++) {
                if (archs[j].ruta.equals(ruta)) {
                    throw new IllegalArgumentException("La ruta \"" + ruta + "\" esta repetida.");
                }
            }
            if (tipo.equals("DIRECTORY")) {
                dirs[nd++] = new DatosDirectorio(ruta, dueno, permiso);
            } else if (tipo.equals("FILE")) {
                int inicio = enteroObligatorio(obj, "startBlock", 0, 100000, contexto);
                int bloques = enteroObligatorio(obj, "blocks", 1, 100000, contexto);
                if (inicio + bloques > total) {
                    throw new IllegalArgumentException(contexto + ": el archivo \"" + ruta
                            + "\" no cabe en un disco de " + total + " bloques.");
                }
                archs[na++] = new DatosArchivo(ruta, inicio, bloques, dueno, permiso);
            } else {
                throw new IllegalArgumentException(contexto + ".type \"" + tipo + "\" no es valido (use FILE o DIRECTORY).");
            }
        }
        DatosDirectorio[] dirsFinal = new DatosDirectorio[nd];
        for (int i = 0; i < nd; i++) {
            dirsFinal[i] = dirs[i];
        }
        DatosArchivo[] archsFinal = new DatosArchivo[na];
        for (int i = 0; i < na; i++) {
            archsFinal[i] = archs[i];
        }
        return new DatosSistemaArchivos(dirsFinal, archsFinal, total, cabezal);
    }

    /**
     * Carga un archivo JSON y pobla directamente un SistemaArchivos existente.
     */
    public static void cargarEn(String rutaJson, SistemaArchivos sistema) throws IOException {
        if (sistema == null) {
            throw new IllegalArgumentException("El sistema de archivos no puede ser null.");
        }
        DatosSistemaArchivos datos = cargar(rutaJson);
        aplicar(datos, sistema);
    }

    /**
     * Aplica los datos validados sobre el SistemaArchivos y su Disco asociado.
     */
    public static void aplicar(DatosSistemaArchivos datos, SistemaArchivos sistema) {
        if (datos == null || sistema == null) {
            throw new IllegalArgumentException("Los datos y el sistema de archivos son obligatorios.");
        }
        for (int i = 0; i < datos.directorios.length; i++) {
            DatosDirectorio dir = datos.directorios[i];
            sistema.crearDirectorio(dir.ruta, dir.dueno, dir.permiso, ModoUsuario.ADMINISTRADOR, "admin");
        }
        for (int i = 0; i < datos.archivos.length; i++) {
            DatosArchivo arch = datos.archivos[i];
            NodoArchivo creado = sistema.cargarArchivoDesdeJson(
                    arch.ruta, arch.bloqueInicio, arch.tamanoBloques, arch.dueno, arch.permiso);
            if (creado == null) {
                throw new IllegalArgumentException("No se pudo asignar en disco el archivo \""
                        + arch.ruta + "\" (bloque inicial " + arch.bloqueInicio
                        + ", tamano " + arch.tamanoBloques + " bloques).");
            }
        }
    }

    /**
     * Exporta el estado actual del arbol de directorios y archivos a un archivo JSON.
     */
    public static void guardar(String rutaJson, SistemaArchivos sistema) throws IOException {
        if (rutaJson == null || rutaJson.trim().isEmpty()) {
            throw new IllegalArgumentException("No se indico la ruta de salida del JSON.");
        }
        if (sistema == null) {
            throw new IllegalArgumentException("El sistema de archivos no puede ser null.");
        }

        JsonArray arrFs = new JsonArray();
        recolectarNodos(sistema.getRaiz(), arrFs);

        JsonObject config = new JsonObject();
        config.addProperty("totalBlocks", sistema.getDisco().getTotalBloques());
        config.addProperty("initialHead", sistema.getDisco().getCabezal());

        JsonObject raiz = new JsonObject();
        raiz.add("diskConfig", config);
        raiz.add("fileSystem", arrFs);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer escritor = new FileWriter(rutaJson)) {
            gson.toJson(raiz, escritor);
        }
    }

    private static void recolectarNodos(NodoArchivo actual, JsonArray arr) {
        if (actual == null) {
            return;
        }
        if (actual.isEsDirectorio()) {
            if (actual.getPadre() != null) {
                JsonObject objDir = new JsonObject();
                objDir.addProperty("path", actual.getRutaCompleta());
                objDir.addProperty("type", "DIRECTORY");
                objDir.addProperty("owner", actual.getDueno());
                objDir.addProperty("permissions", actual.getPermiso() == Permiso.PRIVADO ? "private" : "public");
                arr.add(objDir);
            }
            NodoArchivo[] hijos = actual.getHijosArray();
            for (int i = 0; i < hijos.length; i++) {
                recolectarNodos(hijos[i], arr);
            }
        } else {
            JsonObject objFile = new JsonObject();
            objFile.addProperty("path", actual.getRutaCompleta());
            objFile.addProperty("type", "FILE");
            objFile.addProperty("startBlock", actual.getPrimerBloque());
            objFile.addProperty("blocks", actual.getTamanoBloques());
            objFile.addProperty("owner", actual.getDueno());
            objFile.addProperty("permissions", actual.getPermiso() == Permiso.PRIVADO ? "private" : "public");
            arr.add(objFile);
        }
    }

    // ---------- Lectura y validacion interna ----------

    private static DatosDirectorio leerDirectorio(JsonElement elemento, int numero) {
        String contexto = "directories[" + numero + "]";
        if (elemento == null || !elemento.isJsonObject()) {
            throw new IllegalArgumentException(contexto + " debe ser un objeto.");
        }
        JsonObject obj = elemento.getAsJsonObject();
        String ruta = textoObligatorio(obj, "path", contexto).trim();
        if (!ruta.startsWith("/") || ruta.length() < 2) {
            throw new IllegalArgumentException(contexto + ".path debe empezar con \"/\" (por ejemplo /sistema).");
        }
        String dueno = textoOpcional(obj, "owner", "admin");
        Permiso permiso = leerPermiso(textoOpcional(obj, "permission", "PUBLICO"), contexto);
        return new DatosDirectorio(ruta, dueno, permiso);
    }

    private static DatosArchivo leerArchivo(JsonElement elemento, int numero) {
        String contexto = "files[" + numero + "]";
        if (elemento == null || !elemento.isJsonObject()) {
            throw new IllegalArgumentException(contexto + " debe ser un objeto.");
        }
        JsonObject obj = elemento.getAsJsonObject();
        String ruta = textoObligatorio(obj, "path", contexto).trim();
        if (!ruta.startsWith("/") || ruta.length() < 2) {
            throw new IllegalArgumentException(contexto + ".path debe empezar con \"/\" (por ejemplo /sistema/boot.bin).");
        }
        int bloqueInicio = enteroObligatorio(obj, "startBlock", 0, 100000, contexto);
        int tamanoBloques = enteroObligatorio(obj, "sizeBlocks", 1, 100000, contexto);
        String dueno = textoOpcional(obj, "owner", "admin");
        Permiso permiso = leerPermiso(textoOpcional(obj, "permission", "PUBLICO"), contexto);
        return new DatosArchivo(ruta, bloqueInicio, tamanoBloques, dueno, permiso);
    }

    private static Permiso leerPermiso(String texto, String contexto) {
        String t = texto.trim().toUpperCase();
        if (t.equals("PUBLICO") || t.equals("PUBLIC")) {
            return Permiso.PUBLICO;
        }
        if (t.equals("PRIVADO") || t.equals("PRIVATE")) {
            return Permiso.PRIVADO;
        }
        throw new IllegalArgumentException(contexto + ".permission \"" + texto
                + "\" no es valido (use PUBLICO o PRIVADO).");
    }

    private static String textoObligatorio(JsonObject padre, String clave, String contexto) {
        JsonElement e = padre.get(clave);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Falta el texto \"" + clave + "\" en " + contexto + ".");
        }
        return e.getAsString();
    }

    private static String textoOpcional(JsonObject padre, String clave, String porDefecto) {
        JsonElement e = padre.get(clave);
        if (e == null || e.isJsonNull()) {
            return porDefecto;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("El campo \"" + clave + "\" debe ser texto.");
        }
        String val = e.getAsString().trim();
        return val.isEmpty() ? porDefecto : val;
    }

    private static int enteroObligatorio(JsonObject padre, String clave, int minimo, int maximo, String contexto) {
        JsonElement e = padre.get(clave);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(contexto + "." + clave + " debe ser un numero entero.");
        }
        double valor = e.getAsDouble();
        if (valor != Math.floor(valor) || Double.isInfinite(valor)) {
            throw new IllegalArgumentException(contexto + "." + clave + " debe ser un numero entero (sin decimales).");
        }
        if (valor < minimo || valor > maximo) {
            throw new IllegalArgumentException(contexto + "." + clave + " debe estar entre " + minimo + " y " + maximo + ".");
        }
        return (int) valor;
    }
}