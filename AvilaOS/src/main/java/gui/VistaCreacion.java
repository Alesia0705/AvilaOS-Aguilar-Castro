package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import util.GestorArchivo;

/**
 * Vista 1 del simulador ÁvilaOS.
 * Permite al usuario configurar parámetros globales, crear buffers y definir procesos[cite: 2, 7].
 */
public class VistaCreacion extends JFrame {

    // Componentes de Configuración Global
    private JTextField txtDuracionCiclo;
    private JTextField txtQuantum;
    private JTextField txtLatenciaRed;
    private JTextField txtNumComputadores;
    private JTextField txtRamPorComp;

    // Componentes de Creación de Buffers
    private JTextField txtBufferId;
    private JTextField txtBufferCapacidad;
    private JTextField txtBufferAnfitrion;

    // Componentes de Creación de Procesos
    private JTextField txtProcesoNombre;
    private JTextField txtProcesoMemoria;
    private JTextField txtProcesoPrioridad;
    private JTextField txtProcesoDeadline;
    private JComboBox<String> cbTipoProceso;
    private JComboBox<String> cbAsignacionComp;
    private JTextField txtCiclosProceso;
    private JTextField txtBufferAsociado;
    private JTextField txtElementosReq;

    public VistaCreacion() {
        setTitle("ÁvilaOS - Vista 1: Configuración del Sistema");
        setSize(650, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Configuración Global", crearPanelConfigGlobal());
        tabbedPane.addTab("Crear Buffer", crearPanelBuffer());
        tabbedPane.addTab("Crear Proceso", crearPanelProceso());

        add(tabbedPane);
    }

    /**
     * Construye la pestaña para parámetros globales de la simulación[cite: 3, 7].
     */
    private JPanel crearPanelConfigGlobal() {
        JPanel panel = new JPanel(new GridLayout(7, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        txtDuracionCiclo = new JTextField("1000");
        txtQuantum = new JTextField("3");
        txtLatenciaRed = new JTextField("2");
        txtNumComputadores = new JTextField("2");
        txtRamPorComp = new JTextField("1024");

        panel.add(new JLabel("Duración de ciclo (ms):"));
        panel.add(txtDuracionCiclo);
        panel.add(new JLabel("Quantum Round Robin:"));
        panel.add(txtQuantum);
        panel.add(new JLabel("Latencia de Red (ciclos):"));
        panel.add(txtLatenciaRed);
        panel.add(new JLabel("Número de Computadores:"));
        panel.add(txtNumComputadores);
        panel.add(new JLabel("RAM por Computador (MB):"));
        panel.add(txtRamPorComp);

        JButton btnGuardar = new JButton("Guardar en CSV");
        btnGuardar.addActionListener(e -> guardarConfiguracionCSV());
        panel.add(btnGuardar);

        return panel;
    }

    /**
     * Construye la pestaña para definir buffers acotados[cite: 2, 6].
     */
    private JPanel crearPanelBuffer() {
        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        txtBufferId = new JTextField("B1");
        txtBufferCapacidad = new JTextField("5");
        txtBufferAnfitrion = new JTextField("1");

        panel.add(new JLabel("ID del Buffer:"));
        panel.add(txtBufferId);
        panel.add(new JLabel("Capacidad Máxima:"));
        panel.add(txtBufferCapacidad);
        panel.add(new JLabel("ID Computador Anfitrión:"));
        panel.add(txtBufferAnfitrion);

        JButton btnCrearBuffer = new JButton("Crear Buffer");
        btnCrearBuffer.addActionListener(e -> crearBuffer());
        panel.add(btnCrearBuffer);

        return panel;
    }

    /**
     * Construye la pestaña para formulario de creación de procesos[cite: 2, 5].
     */
    private JPanel crearPanelProceso() {
        JPanel panel = new JPanel(new GridLayout(9, 2, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        txtProcesoNombre = new JTextField("P1");
        txtProcesoMemoria = new JTextField("128");
        txtProcesoPrioridad = new JTextField("1");
        txtProcesoDeadline = new JTextField("20");
        txtCiclosProceso = new JTextField("5");
        txtBufferAsociado = new JTextField("B1");
        txtElementosReq = new JTextField("3");

        cbTipoProceso = new JComboBox<>(new String[]{"CPU_BOUND", "IO_BOUND", "PRODUCTOR", "CONSUMIDOR"});
        cbAsignacionComp = new JComboBox<>(new String[]{"Automático (ÁvilaOS)", "Computador 1", "Computador 2"});

        panel.add(new JLabel("Nombre Proceso:"));
        panel.add(txtProcesoNombre);
        panel.add(new JLabel("RAM Requerida (MB):"));
        panel.add(txtProcesoMemoria);
        panel.add(new JLabel("Prioridad:"));
        panel.add(txtProcesoPrioridad);
        panel.add(new JLabel("Deadline (ciclos):"));
        panel.add(txtProcesoDeadline);
        panel.add(new JLabel("Tipo de Proceso:"));
        panel.add(cbTipoProceso);
        panel.add(new JLabel("Asignación de Computador:"));
        panel.add(cbAsignacionComp);
        panel.add(new JLabel("Ciclos de Ejecución / Frecuencia:"));
        panel.add(txtCiclosProceso);
        panel.add(new JLabel("ID Buffer (Productor/Consumidor):"));
        panel.add(txtBufferAsociado);

        JButton btnCrearProceso = new JButton("Crear Proceso");
        btnCrearProceso.addActionListener(e -> crearProceso());
        panel.add(btnCrearProceso);

        return panel;
    }

    /**
     * Valida las entradas numéricas antes de guardar la configuración[cite: 10].
     */
    private void guardarConfiguracionCSV() {
        try {
            int duracion = Integer.parseInt(txtDuracionCiclo.getText());
            int numComp = Integer.parseInt(txtNumComputadores.getText());
            int ram = Integer.parseInt(txtRamPorComp.getText());
            int latencia = Integer.parseInt(txtLatenciaRed.getText());

            boolean exito = GestorArchivo.guardarConfiguracion("config_simulacion.csv", duracion, numComp, ram, latencia);
            if (exito) {
                JOptionPane.showMessageDialog(this, "Configuración guardada exitosamente en CSV.");
            } else {
                JOptionPane.showMessageDialog(this, "Error al guardar el archivo CSV.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Por favor ingrese valores numéricos válidos.", "Error de Validación", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void crearBuffer() {
        try {
            String id = txtBufferId.getText();
            int capacidad = Integer.parseInt(txtBufferCapacidad.getText());
            int anfitrion = Integer.parseInt(txtBufferAnfitrion.getText());

            if (id.isEmpty() || capacidad <= 0) {
                JOptionPane.showMessageDialog(this, "La capacidad debe ser mayor a 0 y el ID no puede estar vacío.");
                return;
            }

            JOptionPane.showMessageDialog(this, "Buffer " + id + " creado correctamente.");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Capacidad y Anfitrión deben ser enteros.", "Error de Validación", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void crearProceso() {
        try {
            String nombre = txtProcesoNombre.getText();
            int memoria = Integer.parseInt(txtProcesoMemoria.getText());
            int prioridad = Integer.parseInt(txtProcesoPrioridad.getText());
            int deadline = Integer.parseInt(txtProcesoDeadline.getText());

            if (nombre.isEmpty() || memoria <= 0 || deadline <= 0) {
                JOptionPane.showMessageDialog(this, "Complete todos los campos obligatorios del proceso correctamente.");
                return;
            }

            JOptionPane.showMessageDialog(this, "Proceso " + nombre + " agregado al sistema.");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Memoria, prioridad y deadline deben ser números enteros válidos.", "Error de Validación", JOptionPane.WARNING_MESSAGE);
        }
    }
}