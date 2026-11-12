package modelo.enums;

/**
 * Operaciones de E/S que un proceso puede pedirle al disco.
 */
public enum TipoOperacion {
    CREAR,
    LEER,
    ACTUALIZAR,
    ELIMINAR,
    SUSPENDER,
    REACTIVAR
}