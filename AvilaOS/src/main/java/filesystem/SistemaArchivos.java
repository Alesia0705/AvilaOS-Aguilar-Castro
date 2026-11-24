package filesystem;

import disco.Disco;
import java.util.concurrent.Semaphore;
import modelo.enums.ModoUsuario;
import modelo.enums.Permiso;
import modelo.enums.TipoOperacion;
import util.ListaEnlazada;

/**
 * Sistema de archivos jerarquico (tree-structured) de AvilaOS.
 * Gestiona directorios y archivos con dueno, permisos (PUBLICO / PRIVADO),
 * control de acceso segun ModoUsuario y asignacion/liberacion encadenada en Disco.
 */
public class SistemaArchivos {

    private final Disco disco;
    private final NodoArchivo raiz;
    private final Semaphore mutex;

    private volatile ModoUsuario modoActual;
    private volatile String usuarioActual;

    public SistemaArchivos(Disco disco) {
        if (disco == null) {
            throw new IllegalArgumentException("El sistema de archivos necesita un disco");
        }
        this.disco = disco;
        this.raiz = new NodoArchivo("/", "admin", Permiso.PUBLICO);
        this.mutex = new Semaphore(1);
        this.modoActual = ModoUsuario.ADMINISTRADOR;
        this.usuarioActual = "admin";
    }

    // ==================== SESION Y PERMISOS ====================

    public ModoUsuario getModoActual() {
        return modoActual;
    }

    public void setModoActual(ModoUsuario modoActual) {
        if (modoActual != null) {
            this.modoActual = modoActual;
        }
    }

    public String getUsuarioActual() {
        return usuarioActual;
    }

    public void setUsuarioActual(String usuarioActual) {
        if (usuarioActual != null && !usuarioActual.trim().isEmpty()) {
            this.usuarioActual = usuarioActual.trim();
        }
    }

    /**
     * Verifica si el solicitante tiene acceso al nodo:
     * - ADMINISTRADOR siempre tiene acceso.
     * - Permiso PUBLICO permite acceso a cualquier usuario.
     * - Permiso PRIVADO solo permite acceso a su dueno.
     */
    public boolean tieneAcceso(NodoArchivo nodo, ModoUsuario modo, String usuario) {
        if (nodo == null) {
            return false;
        }
        if (modo == ModoUsuario.ADMINISTRADOR) {
            return true;
        }
        if (nodo.getPermiso() == Permiso.PUBLICO) {
            return true;
        }
        return usuario != null && usuario.equalsIgnoreCase(nodo.getDueno());
    }

    /**
     * Verifica que el solicitante tenga permiso sobre toda la cadena desde la raiz hasta el nodo.
     */
    private boolean tieneAccesoEnRuta(NodoArchivo nodo, ModoUsuario modo, String usuario) {
        NodoArchivo actual = nodo;
        while (actual != null) {
            if (!tieneAcceso(actual, modo, usuario)) {
                return false;
            }
            actual = actual.getPadre();
        }
        return true;
    }

    /**
     * Decide si el solicitante puede hacer la operacion sobre la ruta segun las
     * reglas de los dos modos de uso:
     * - ADMINISTRADOR: todas las operaciones.
     * - USUARIO: lee archivos propios o publicos; crea archivos solo dentro de
     *   sus propios directorios; modifica o elimina solo lo que es suyo.
     *
     * @return null si se permite, o el motivo del rechazo listo para mostrar al usuario
     */
    public String verificarPermiso(TipoOperacion operacion, String ruta, ModoUsuario modo, String usuario) {
        adquirir();
        try {
            return verificarInterno(operacion, ruta, modo, usuario);
        } finally {
            mutex.release();
        }
    }

    private String verificarInterno(TipoOperacion operacion, String ruta, ModoUsuario modo, String usuario) {
        if (operacion == null) {
            return "no se indicó la operación.";
        }
        String limpia = normalizarRuta(ruta);
        if (limpia == null) {
            return "la ruta \"" + ruta + "\" no es válida (debe empezar con \"/\", por ejemplo /usuarios/notas.txt).";
        }
        boolean admin = (modo == ModoUsuario.ADMINISTRADOR);
        String quien = admin ? "el administrador" : "el usuario " + usuario;

        if (operacion == TipoOperacion.CREAR) {
            if ("/".equals(limpia)) {
                return "no se puede crear la raíz del sistema de archivos.";
            }
            String rutaPadre = obtenerRutaPadre(limpia);
            NodoArchivo padre = buscarNodoInterno(rutaPadre);
            if (padre == null || !padre.isEsDirectorio()) {
                return "el directorio \"" + rutaPadre + "\" no existe.";
            }
            if (padre.buscarHijo(obtenerNombreFinal(limpia)) != null) {
                return "\"" + limpia + "\" ya existe.";
            }
            if (!admin) {
                String privado = motivoAccesoEnRuta(padre, modo, usuario);
                if (privado != null) {
                    return privado;
                }
                if (!padre.getDueno().equalsIgnoreCase(usuario)) {
                    return "en modo usuario solo se pueden crear archivos dentro de los directorios propios, y \""
                            + rutaPadre + "\" pertenece a " + padre.getDueno() + ".";
                }
            }
            return null;
        }

        NodoArchivo nodo = buscarNodoInterno(limpia);
        if (nodo == null) {
            return "\"" + limpia + "\" no existe.";
        }
        if (operacion == TipoOperacion.LEER) {
            if (nodo.isEsDirectorio()) {
                return "\"" + limpia + "\" es un directorio, no un archivo.";
            }
            return admin ? null : motivoAccesoEnRuta(nodo, modo, usuario);
        }
        if (operacion == TipoOperacion.ACTUALIZAR || operacion == TipoOperacion.ELIMINAR) {
            if (nodo == raiz) {
                return "no se puede modificar la raíz del sistema de archivos.";
            }
            if (admin) {
                return null;
            }
            String privado = motivoAccesoEnRuta(nodo, modo, usuario);
            if (privado != null) {
                return privado;
            }
            if (!nodo.getDueno().equalsIgnoreCase(usuario)) {
                return "en modo usuario no se pueden modificar archivos del sistema ni de otros usuarios, y \""
                        + limpia + "\" pertenece a " + nodo.getDueno() + ".";
            }
            if (operacion == TipoOperacion.ELIMINAR && nodo.isEsDirectorio()) {
                NodoArchivo ajeno = primerNodoAjeno(nodo, usuario);
                if (ajeno != null) {
                    return "el directorio contiene \"" + ajeno.getRutaCompleta() + "\", que pertenece a "
                            + ajeno.getDueno() + ".";
                }
            }
            return null;
        }
        return "la operación " + operacion + " no es una operación de archivo.";
    }

    /** Explica que nodo de la ruta (si lo hay) es privado de otro usuario; null si tiene acceso. */
    private String motivoAccesoEnRuta(NodoArchivo nodo, ModoUsuario modo, String usuario) {
        NodoArchivo actual = nodo;
        while (actual != null) {
            if (!tieneAcceso(actual, modo, usuario)) {
                return "\"" + actual.getRutaCompleta() + "\" es privado y pertenece a " + actual.getDueno()
                        + "; en modo usuario solo se acceden los archivos propios o públicos.";
            }
            actual = actual.getPadre();
        }
        return null;
    }

    private NodoArchivo primerNodoAjeno(NodoArchivo nodo, String usuario) {
        NodoArchivo[] hijos = nodo.getHijosArray();
        for (int i = 0; i < hijos.length; i++) {
            if (!hijos[i].getDueno().equalsIgnoreCase(usuario)) {
                return hijos[i];
            }
            if (hijos[i].isEsDirectorio()) {
                NodoArchivo ajeno = primerNodoAjeno(hijos[i], usuario);
                if (ajeno != null) {
                    return ajeno;
                }
            }
        }
        return null;
    }

    // ==================== BUSQUEDA Y RESOLUCION DE RUTAS ====================

    public NodoArchivo getRaiz() {
        return raiz;
    }

    public Disco getDisco() {
        return disco;
    }

    public NodoArchivo buscarNodo(String ruta) {
        adquirir();
        try {
            return buscarNodoInterno(ruta);
        } finally {
            mutex.release();
        }
    }

    private NodoArchivo buscarNodoInterno(String ruta) {
        String limpia = normalizarRuta(ruta);
        if (limpia == null) {
            return null;
        }
        if ("/".equals(limpia)) {
            return raiz;
        }
        String[] partes = limpia.substring(1).split("/");
        NodoArchivo actual = raiz;
        for (int i = 0; i < partes.length; i++) {
            if (partes[i].isEmpty()) {
                continue;
            }
            actual = actual.buscarHijo(partes[i]);
            if (actual == null) {
                return null;
            }
        }
        return actual;
    }

    // ==================== OPERACIONES CRUD ====================

    /**
     * Crea un directorio usando el modo y usuario activos de la sesion.
     */
    public NodoArchivo crearDirectorio(String ruta, Permiso permiso) {
        return crearDirectorio(ruta, usuarioActual, permiso, modoActual, usuarioActual);
    }

    /**
     * CREATE (Directorio): crea un subdirectorio en la ruta indicada.
     */
    public NodoArchivo crearDirectorio(String ruta, String dueno, Permiso permiso,
            ModoUsuario modo, String solicitante) {
        if (modo != ModoUsuario.ADMINISTRADOR) {
            return null;   // los directorios solo los crea el administrador
        }
        adquirir();
        try {
            String limpia = normalizarRuta(ruta);
            if (limpia == null || "/".equals(limpia)) {
                return null;
            }
            String rutaPadre = obtenerRutaPadre(limpia);
            String nombreNuevo = obtenerNombreFinal(limpia);
            if (nombreNuevo.isEmpty()) {
                return null;
            }

            NodoArchivo padre = buscarNodoInterno(rutaPadre);
            if (padre == null || !padre.isEsDirectorio()) {
                return null;
            }
            if (!tieneAccesoEnRuta(padre, modo, solicitante)) {
                return null;
            }
            if (padre.buscarHijo(nombreNuevo) != null) {
                return null;
            }

            NodoArchivo nuevoDir = new NodoArchivo(nombreNuevo, dueno, permiso);
            padre.agregarHijo(nuevoDir);
            return nuevoDir;
        } finally {
            mutex.release();
        }
    }

    /**
     * Crea un archivo usando el modo y usuario activos de la sesion.
     */
    public NodoArchivo crearArchivo(String ruta, int tamanoBloques, Permiso permiso) {
        return crearArchivo(ruta, tamanoBloques, usuarioActual, permiso, modoActual, usuarioActual);
    }

    /**
     * CREATE (Archivo): valida permisos, reserva bloques encadenados en el disco
     * y registra el nodo en el directorio padre.
     */
    public NodoArchivo crearArchivo(String ruta, int tamanoBloques, String dueno, Permiso permiso,
            ModoUsuario modo, String solicitante) {
        if (tamanoBloques <= 0) {
            return null;
        }
        adquirir();
        try {
            if (verificarInterno(TipoOperacion.CREAR, ruta, modo, solicitante) != null) {
                return null;
            }
            String limpia = normalizarRuta(ruta);
            if (limpia == null || "/".equals(limpia)) {
                return null;
            }
            String rutaPadre = obtenerRutaPadre(limpia);
            String nombreNuevo = obtenerNombreFinal(limpia);
            if (nombreNuevo.isEmpty()) {
                return null;
            }

            NodoArchivo padre = buscarNodoInterno(rutaPadre);
            if (padre == null || !padre.isEsDirectorio()) {
                return null;
            }
            if (!tieneAccesoEnRuta(padre, modo, solicitante)) {
                return null;
            }
            if (padre.buscarHijo(nombreNuevo) != null) {
                return null;
            }

            int primerBloque = disco.asignarArchivo(limpia, tamanoBloques);
            if (primerBloque < 0) {
                return null; // No hay espacio suficiente en disco
            }

            NodoArchivo archivo = new NodoArchivo(nombreNuevo, dueno, permiso, tamanoBloques, primerBloque);
            padre.agregarHijo(archivo);
            return archivo;
        } finally {
            mutex.release();
        }
    }

    /**
     * Carga inicial desde JSON: crea directorios intermedios si faltan y asigna
     * el rango de bloques fijo indicado en el archivo de configuracion.
     */
    public NodoArchivo cargarArchivoDesdeJson(String ruta, int startBlock, int tamanoBloques,
            String dueno, Permiso permiso) {
        adquirir();
        try {
            String limpia = normalizarRuta(ruta);
            if (limpia == null || "/".equals(limpia) || tamanoBloques <= 0) {
                return null;
            }
            String[] partes = limpia.substring(1).split("/");
            NodoArchivo actual = raiz;
            for (int i = 0; i < partes.length - 1; i++) {
                if (partes[i].isEmpty()) {
                    continue;
                }
                NodoArchivo siguiente = actual.buscarHijo(partes[i]);
                if (siguiente == null) {
                    siguiente = new NodoArchivo(partes[i], "admin", Permiso.PUBLICO);
                    actual.agregarHijo(siguiente);
                } else if (!siguiente.isEsDirectorio()) {
                    return null;
                }
                actual = siguiente;
            }

            String nombreArchivo = partes[partes.length - 1];
            if (nombreArchivo.isEmpty() || actual.buscarHijo(nombreArchivo) != null) {
                return null;
            }

            if (!disco.asignarBloquesFijos(limpia, startBlock, tamanoBloques)) {
                return null;
            }

            NodoArchivo archivo = new NodoArchivo(nombreArchivo, dueno, permiso, tamanoBloques, startBlock);
            actual.agregarHijo(archivo);
            return archivo;
        } finally {
            mutex.release();
        }
    }

    /**
     * READ: valida existencia y permisos de lectura sobre el archivo y su ruta.
     *
     * @return el NodoArchivo si la lectura es valida, o null si no existe o no hay permiso
     */
    public NodoArchivo leerArchivo(String ruta, ModoUsuario modo, String solicitante) {
        adquirir();
        try {
            NodoArchivo nodo = buscarNodoInterno(ruta);
            if (nodo == null || nodo.isEsDirectorio()) {
                return null;
            }
            if (!tieneAccesoEnRuta(nodo, modo, solicitante)) {
                return null;
            }
            return nodo;
        } finally {
            mutex.release();
        }
    }

    /**
     * UPDATE: actualiza nombre, permiso y/o tamano en bloques de un nodo existente.
     * Pasar nuevoNombre = null para conservar el nombre; nuevosBloques <= 0 para conservar tamano;
     * nuevoPermiso = null para conservar permiso.
     */
    public boolean actualizar(String ruta, String nuevoNombre, int nuevosBloques, Permiso nuevoPermiso,
            ModoUsuario modo, String solicitante) {
        adquirir();
        try {
            if (verificarInterno(TipoOperacion.ACTUALIZAR, ruta, modo, solicitante) != null) {
                return false;
            }
            NodoArchivo nodo = buscarNodoInterno(ruta);
            if (nodo == null || nodo == raiz) {
                return false;
            }
            if (!tieneAccesoEnRuta(nodo, modo, solicitante)) {
                return false;
            }

            // Validar cambio de nombre si se pidio
            if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
                String nombreLimpio = nuevoNombre.trim();
                if (!nombreLimpio.equals(nodo.getNombre())) {
                    if (nombreLimpio.contains("/") || nodo.getPadre().buscarHijo(nombreLimpio) != null) {
                        return false;
                    }
                }
            }

            // Validar redimensionamiento si es archivo y cambia cantidad de bloques
            if (!nodo.isEsDirectorio() && nuevosBloques > 0 && nuevosBloques != nodo.getTamanoBloques()) {
                int diferencia = nuevosBloques - nodo.getTamanoBloques();
                if (diferencia > 0 && disco.contarBloquesLibres(false) < diferencia) {
                    return false;
                }
                int bloqueAnterior = nodo.getPrimerBloque();
                String rutaDestino = nodo.getRutaCompleta();
                disco.liberarCadena(bloqueAnterior);
                int nuevoInicio = disco.asignarArchivo(rutaDestino, nuevosBloques);
                if (nuevoInicio < 0) {
                    return false;
                }
                nodo.setPrimerBloque(nuevoInicio);
                nodo.setTamanoBloques(nuevosBloques);
            }

            if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
                nodo.setNombre(nuevoNombre.trim());
            }
            if (nuevoPermiso != null) {
                nodo.setPermiso(nuevoPermiso);
            }
            return true;
        } finally {
            mutex.release();
        }
    }

    /**
     * Elimina un archivo o directorio usando el modo y usuario activos de la sesion.
     */
    public boolean eliminar(String ruta) {
        return eliminar(ruta, modoActual, usuarioActual);
    }

    /**
     * DELETE: elimina un archivo o directorio (recursivamente) y libera todos
     * sus bloques encadenados en el disco.
     */
    public boolean eliminar(String ruta, ModoUsuario modo, String solicitante) {
        adquirir();
        try {
            if (verificarInterno(TipoOperacion.ELIMINAR, ruta, modo, solicitante) != null) {
                return false;
            }
            NodoArchivo nodo = buscarNodoInterno(ruta);
            if (nodo == null || nodo == raiz) {
                return false;
            }
            if (!tieneAccesoEnRuta(nodo, modo, solicitante)) {
                return false;
            }
            if (!tieneAccesoSubarbol(nodo, modo, solicitante)) {
                return false;
            }

            liberarSubarbolEnDisco(nodo);
            NodoArchivo padre = nodo.getPadre();
            return padre != null && padre.eliminarHijo(nodo);
        } finally {
            mutex.release();
        }
    }

    private boolean tieneAccesoSubarbol(NodoArchivo nodo, ModoUsuario modo, String solicitante) {
        if (!tieneAcceso(nodo, modo, solicitante)) {
            return false;
        }
        if (nodo.isEsDirectorio()) {
            NodoArchivo[] hijos = nodo.getHijosArray();
            for (int i = 0; i < hijos.length; i++) {
                if (!tieneAccesoSubarbol(hijos[i], modo, solicitante)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void liberarSubarbolEnDisco(NodoArchivo nodo) {
        if (!nodo.isEsDirectorio()) {
            if (nodo.getPrimerBloque() >= 0) {
                disco.liberarCadena(nodo.getPrimerBloque());
            }
            return;
        }
        NodoArchivo[] hijos = nodo.getHijosArray();
        for (int i = 0; i < hijos.length; i++) {
            liberarSubarbolEnDisco(hijos[i]);
        }
    }

    /**
     * Devuelve todos los archivos registrados en el arbol (para la tabla de asignacion de la GUI).
     */
    public NodoArchivo[] obtenerTodosLosArchivos() {
        adquirir();
        try {
            ListaEnlazada<NodoArchivo> lista = new ListaEnlazada<>();
            recolectarArchivos(raiz, lista);
            int n = lista.getTamano();
            NodoArchivo[] arr = new NodoArchivo[n];
            for (int i = 0; i < n; i++) {
                arr[i] = lista.obtener(i);
            }
            return arr;
        } finally {
            mutex.release();
        }
    }

    private void recolectarArchivos(NodoArchivo actual, ListaEnlazada<NodoArchivo> acumulador) {
        if (!actual.isEsDirectorio()) {
            acumulador.agregarAlFinal(actual);
            return;
        }
        NodoArchivo[] hijos = actual.getHijosArray();
        for (int i = 0; i < hijos.length; i++) {
            recolectarArchivos(hijos[i], acumulador);
        }
    }

    // ==================== UTILIDADES DE RUTA ====================

    private String normalizarRuta(String ruta) {
        if (ruta == null) {
            return null;
        }
        String r = ruta.trim();
        if (!r.startsWith("/")) {
            return null;
        }
        while (r.length() > 1 && r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private String obtenerRutaPadre(String rutaNormalizada) {
        int ultimoSlash = rutaNormalizada.lastIndexOf('/');
        if (ultimoSlash <= 0) {
            return "/";
        }
        return rutaNormalizada.substring(0, ultimoSlash);
    }

    private String obtenerNombreFinal(String rutaNormalizada) {
        int ultimoSlash = rutaNormalizada.lastIndexOf('/');
        return rutaNormalizada.substring(ultimoSlash + 1);
    }

    private void adquirir() {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}