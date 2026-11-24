package avilaos;

import gui.VistaCarga;

/**
 * Punto de entrada de ÁvilaOS. Abre la Vista 1, donde se cargan los dos JSON
 * obligatorios (procesos y sistema de archivos) antes de iniciar el reloj.
 */
public class AvilaOS {

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            new VistaCarga().setVisible(true);
        });
    }
}