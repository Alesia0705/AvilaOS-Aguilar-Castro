package util;

/**
 * Gestor del registro de eventos del sistema simulado ÁvilaOS.
 * Captura y da formato a las decisiones del sistema y eventos de sincronización.
 */
public class LogEventos {
    private String[] eventos;
    private int contador;
    private int capacidad;

    /**
     * Inicializa el registro de eventos con una capacidad dada.
     * @param capacidad Cantidad máxima de eventos en el historial.
     */
    public LogEventos(int capacidad) {
        this.capacidad = capacidad;
        this.eventos = new String[capacidad];
        this.contador = 0;
    }

    /**
     * Registra un evento en el historial incluyendo el ciclo de reloj global.
     * @param ciclo Ciclo de reloj en el que ocurrió el evento.
     * @param descripcion Detalle del evento registrado.
     */
    public synchronized void registrar(long ciclo, String descripcion) {
        String mensaje = "[Ciclo " + ciclo + "] " + descripcion;
        if (contador < capacidad) {
            eventos[contador] = mensaje;
            contador++;
        }
        System.out.println(mensaje); // Imprime en consola para depuración
    }

    /**
     * Obtiene el listado completo de eventos registrados.
     * @return Arreglo de textos con los eventos.
     */
    public synchronized String[] getEventos() {
        String[] copia = new String[contador];
        for (int i = 0; i < contador; i++) {
            copia[i] = eventos[i];
        }
        return copia;
    }

    /**
     * Limpia el historial de eventos.
     */
    public synchronized void limpiar() {
        this.contador = 0;
    }
}