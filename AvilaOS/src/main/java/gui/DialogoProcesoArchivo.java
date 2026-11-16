package gui;

import filesystem.SistemaArchivos;
import modelo.Proceso;
import modelo.enums.ModoUsuario;
import modelo.enums.TipoOperacion;

import javax.swing.*;
import java.awt.Component;
import java.awt.GridLayout;

/**
 * Diálogo para crear un proceso de E/S de archivo con el modo y usuario
 * activos. Antes de enviarlo valida los permisos: si el modo actual no puede
 * hacer la operación, explica por qué en un aviso (el reloj sigue corriendo).
 */
public final class DialogoProcesoArchivo {

    private DialogoProcesoArchivo() {
    }

    /**
     * @return el proceso listo para enviar, o null si se canceló o se rechazó
     */
    public static Proceso mostrar(Component padre, SistemaArchivos fs, int ramComputador, int numero) {
        ModoUsuario modo = fs.getModoActual();
        String usuario = fs.getUsuarioActual();

        JTextField txtNombre = new JTextField("PE" + numero);
        JComboBox<TipoOperacion> cmbOp = new JComboBox<>(new TipoOperacion[]{
            TipoOperacion.CREAR, TipoOperacion.LEER, TipoOperacion.ACTUALIZAR, TipoOperacion.ELIMINAR});
        JTextField txtRuta = new JTextField("/usuarios/" + usuario + "/archivo" + numero + ".txt");
        JTextField txtBloques = new JTextField("3");
        JTextField txtInstr = new JTextField("5");
        JTextField txtRam = new JTextField("10");
        JTextField txtDeadline = new JTextField("3000");

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Se crea como:"));
        form.add(new JLabel(modo == ModoUsuario.ADMINISTRADOR ? "administrador" : "usuario " + usuario));
        form.add(new JLabel("Nombre del proceso:"));
        form.add(txtNombre);
        form.add(new JLabel("Operación:"));
        form.add(cmbOp);
        form.add(new JLabel("Ruta del archivo:"));
        form.add(txtRuta);
        form.add(new JLabel("Bloques (crear / actualizar):"));
        form.add(txtBloques);
        form.add(new JLabel("Instrucciones (mínimo 2):"));
        form.add(txtInstr);
        form.add(new JLabel("RAM:"));
        form.add(txtRam);
        form.add(new JLabel("Deadline (ciclos):"));
        form.add(txtDeadline);

        int opcion = JOptionPane.showConfirmDialog(padre, form, "Nuevo proceso de E/S de archivo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opcion != JOptionPane.OK_OPTION) {
            return null;
        }
        try {
            TipoOperacion op = (TipoOperacion) cmbOp.getSelectedItem();
            Proceso p = FabricaSistema.crearProcesoArchivo(txtNombre.getText(), op, txtRuta.getText(),
                    txtBloques.getText(), txtRam.getText(), txtDeadline.getText(), txtInstr.getText(),
                    ramComputador, modo, usuario);
            String motivo = fs.verificarPermiso(op, p.getRutaArchivo(), modo, usuario);
            if (motivo != null) {
                JOptionPane.showMessageDialog(padre,
                        "Operación no permitida: " + (modo == ModoUsuario.ADMINISTRADOR
                        ? "el administrador" : "el usuario " + usuario) + " no puede " + op
                        + " " + p.getRutaArchivo() + ": " + motivo
                        + "\n\nLa simulación sigue corriendo; no se creó el proceso.",
                        "Operación no permitida", JOptionPane.INFORMATION_MESSAGE);
                return null;
            }
            return p;
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(padre, ex.getMessage(), "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }
}