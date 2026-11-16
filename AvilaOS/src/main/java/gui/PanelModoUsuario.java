package gui;

import filesystem.SistemaArchivos;
import modelo.enums.ModoUsuario;

import javax.swing.*;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/**
 * Selector del modo de operación (administrador / usuario) y del nombre del
 * usuario activo. Se usa en la vista de carga (modo inicial) y en la vista de
 * simulación (cambio en vivo). Cambiar de modo no detiene el reloj.
 */
public class PanelModoUsuario extends JPanel {

    private final JComboBox<ModoUsuario> cmbModo = new JComboBox<>(ModoUsuario.values());
    private final JTextField txtUsuario = new JTextField("usuario1", 10);
    private SistemaArchivos fs;
    private Consumer<String> aviso;

    public PanelModoUsuario() {
        super(new FlowLayout(FlowLayout.LEFT, 8, 2));
        add(new JLabel("Modo:"));
        add(cmbModo);
        add(new JLabel("Usuario:"));
        add(txtUsuario);
        cmbModo.addActionListener(e -> aplicar(true));
        txtUsuario.addActionListener(e -> aplicar(true));
        txtUsuario.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                aplicar(true);
            }
        });
        actualizarCampos();
    }

    /**
     * Conecta el selector al sistema de archivos: desde aquí los cambios se
     * aplican de inmediato y se avisan (para el log).
     */
    public void conectar(SistemaArchivos fs, Consumer<String> aviso) {
        this.fs = fs;
        this.aviso = aviso;
        cmbModo.setSelectedItem(fs.getModoActual());
        if (fs.getUsuarioActual() != null) {
            txtUsuario.setText(fs.getUsuarioActual());
        }
        actualizarCampos();
    }

    public ModoUsuario getModo() {
        return (ModoUsuario) cmbModo.getSelectedItem();
    }

    public String getUsuario() {
        String u = txtUsuario.getText().trim();
        return u.isEmpty() ? "usuario1" : u;
    }

    private void actualizarCampos() {
        txtUsuario.setEnabled(getModo() == ModoUsuario.USUARIO);
    }

    private void aplicar(boolean avisar) {
        actualizarCampos();
        if (fs == null) {
            return;
        }
        ModoUsuario anterior = fs.getModoActual();
        String usuarioAnterior = fs.getUsuarioActual();
        fs.setModoActual(getModo());
        fs.setUsuarioActual(getUsuario());
        boolean cambio = anterior != getModo()
                || (getModo() == ModoUsuario.USUARIO && !getUsuario().equals(usuarioAnterior));
        if (avisar && cambio && aviso != null) {
            aviso.accept("Modo activo: " + (getModo() == ModoUsuario.ADMINISTRADOR
                    ? "administrador" : "usuario " + getUsuario()) + ".");
        }
    }
}