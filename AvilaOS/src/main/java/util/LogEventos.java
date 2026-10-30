package util;

/**
 * Gestor del registro de eventos del sistema simulado ÁvilaOS.
 * Captura y da formato a las decisiones del sistema y eventos de sincronización.
 * Permite registrar un observador (GUI) para streaming de logs en tiempo real.
 */
public class LogEventos {

    /**
     * Interfaz funcional para notificar nuevos logs a la interfaz gráfica.
     */
    @FunctionalInterface
    public interface EscuchaLog {
        void onNuevoLog(String mensaje);
    }

    private String[] eventos;
    private int contador;
    private int capacidad;
    private EscuchaLog escucha;

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
     * Asigna el observador (UI) que recibirá los logs en vivo.
     * @param escucha Instancia que implementa EscuchaLog.
     */
    public synchronized void setEscucha(EscuchaLog escucha) {
        this.escucha = escucha;
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
        
        System.out.println(mensaje); // Consola de depuración en IDE

        // Notificar a la interfaz gráfica si hay un escuchador registrado
        if (escucha != null) {
            escucha.onNuevoLog(mensaje);
        }
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