package avilaos;

import gui.VistaCreacion;

/**
 * Clase principal de inicio para ÁvilaOS.
 */
public class AvilaOS {

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            new VistaCreacion().setVisible(true);
        });
    }
}