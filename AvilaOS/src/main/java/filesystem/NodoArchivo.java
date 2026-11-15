package filesystem;

import modelo.enums.Permiso;
import util.ListaEnlazada;

/**
 * Nodo del arbol jerarquico del sistema de archivos (puede ser archivo o directorio).
 */
public class NodoArchivo {

    private String nombre;
    private final boolean esDirectorio;
    private String dueno;
    private Permiso permiso;
    private int tamanoBloques;
    private int primerBloque;
    private NodoArchivo padre;
    private final ListaEnlazada<NodoArchivo> hijos;

    /**
     * Constructor para directorios.
     */
    public NodoArchivo(String nombre, String dueno, Permiso permiso) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del directorio no puede estar vacio");
        }
        this.nombre = nombre.trim();
        this.esDirectorio = true;
        this.dueno = (dueno == null || dueno.trim().isEmpty()) ? "admin" : dueno.trim();
        this.permiso = (permiso == null) ? Permiso.PUBLICO : permiso;
        this.tamanoBloques = 0;
        this.primerBloque = -1;
        this.padre = null;
        this.hijos = new ListaEnlazada<>();
    }

    /**
     * Constructor para archivos.
     */
    public NodoArchivo(String nombre, String dueno, Permiso permiso, int tamanoBloques, int primerBloque) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del archivo no puede estar vacio");
        }
        if (tamanoBloques <= 0) {
            throw new IllegalArgumentException("Un archivo debe ocupar al menos 1 bloque");
        }
        this.nombre = nombre.trim();
        this.esDirectorio = false;
        this.dueno = (dueno == null || dueno.trim().isEmpty()) ? "admin" : dueno.trim();
        this.permiso = (permiso == null) ? Permiso.PUBLICO : permiso;
        this.tamanoBloques = tamanoBloques;
        this.primerBloque = primerBloque;
        this.padre = null;
        this.hijos = null;
    }

    /**
     * Reconstruye la ruta absoluta desde la raiz (ej: "/sistema/boot.bin").
     */
    public String getRutaCompleta() {
        if (padre == null) {
            return "/";
        }
        String rutaPadre = padre.getRutaCompleta();
        if ("/".equals(rutaPadre)) {
            return "/" + nombre;
        }
        return rutaPadre + "/" + nombre;
    }

    public NodoArchivo buscarHijo(String nombreHijo) {
        if (!esDirectorio || hijos == null || nombreHijo == null) {
            return null;
        }
        int n = hijos.getTamano();
        for (int i = 0; i < n; i++) {
            NodoArchivo hijo = hijos.obtener(i);
            if (hijo.getNombre().equals(nombreHijo)) {
                return hijo;
            }
        }
        return null;
    }

    public void agregarHijo(NodoArchivo hijo) {
        if (!esDirectorio || hijos == null || hijo == null) {
            return;
        }
        hijo.setPadre(this);
        hijos.agregarAlFinal(hijo);
    }

    public boolean eliminarHijo(NodoArchivo hijo) {
        if (!esDirectorio || hijos == null || hijo == null) {
            return false;
        }
        boolean eliminado = hijos.quitar(hijo);
        if (eliminado) {
            hijo.setPadre(null);
        }
        return eliminado;
    }

    public NodoArchivo[] getHijosArray() {
        if (!esDirectorio || hijos == null) {
            return new NodoArchivo[0];
        }
        int n = hijos.getTamano();
        NodoArchivo[] arr = new NodoArchivo[n];
        for (int i = 0; i < n; i++) {
            arr[i] = hijos.obtener(i);
        }
        return arr;
    }

    public int getCantidadHijos() {
        return (esDirectorio && hijos != null) ? hijos.getTamano() : 0;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        if (nombre != null && !nombre.trim().isEmpty()) {
            this.nombre = nombre.trim();
        }
    }

    public boolean isEsDirectorio() {
        return esDirectorio;
    }

    public String getDueno() {
        return dueno;
    }

    public void setDueno(String dueno) {
        if (dueno != null && !dueno.trim().isEmpty()) {
            this.dueno = dueno.trim();
        }
    }

    public Permiso getPermiso() {
        return permiso;
    }

    public void setPermiso(Permiso permiso) {
        if (permiso != null) {
            this.permiso = permiso;
        }
    }

    public int getTamanoBloques() {
        return tamanoBloques;
    }

    public void setTamanoBloques(int tamanoBloques) {
        this.tamanoBloques = tamanoBloques;
    }

    public int getPrimerBloque() {
        return primerBloque;
    }

    public void setPrimerBloque(int primerBloque) {
        this.primerBloque = primerBloque;
    }

    public NodoArchivo getPadre() {
        return padre;
    }

    public void setPadre(NodoArchivo padre) {
        this.padre = padre;
    }

    @Override
    public String toString() {
        if (esDirectorio) {
            return nombre + "/ [" + dueno + ", " + permiso + "]";
        }
        return nombre + " (" + tamanoBloques + " blq, ini=" + primerBloque + ") [" + dueno + ", " + permiso + "]";
    }
}