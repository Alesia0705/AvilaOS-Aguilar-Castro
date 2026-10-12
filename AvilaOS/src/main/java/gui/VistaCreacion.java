package gui;

import modelo.Proceso;
import sincronizacion.Buffer;
import sincronizacion.ColaProcesos;
import util.GestorArchivo;
import util.LogEventos;

import javax.swing.*;
import java.awt.*;
import modelo.enums.TipoProceso;

/**
 * Vista 1: Interfaz gráfica Swing para la configuración inicial de ÁvilaOS.
 * Permite definir parámetros globales, crear buffers e instanciar/encolar procesos reales en ColaProcesos.
 */
public class VistaCreacion extends JFrame {

    // Estructuras de datos del sistema y utilidades
    private final ColaProcesos colaProcesos = new ColaProcesos();
    private final LogEventos logEventos = new LogEventos(100); // Se especifica capacidad inicial (100 eventos)
    private int contadorProcesoID = 1;

    // Componentes Pestaña 1: Configuración Global
    private JTextField txtDuracionCiclo;
    private JTextField txtQuantum;
    private JTextField txtLatenciaRed;
    private JTextField txtNumComputadores;
    private JTextField txtRamPorComp;

    // Componentes Pestaña 2: Crear Buffer
    private JTextField txtBufferId;
    private JTextField txtBufferCapacidad;
    private JTextField txtBufferComputador;

    // Componentes Pestaña 3: Crear Proceso
    private JTextField txtNombreProceso;
    private JTextField txtRamProceso;
    private JTextField txtPrioridadProceso;
    private JTextField txtDeadlineProceso;
    private JComboBox<String> cbTipoProceso;
    private JTextField txtCompProceso;

    public VistaCreacion() {
        super("ÁvilaOS - Vista 1: Configuración del Sistema");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(550, 450);
        setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab("Configuración Global", crearPanelConfigGlobal());
        tabbedPane.addTab("Crear Buffer", crearPanelBuffer());
        tabbedPane.addTab("Crear Proceso", crearPanelProceso());

        add(tabbedPane);
    }

    // --- Panel 1: Configuración Global ---
    private JPanel crearPanelConfigGlobal() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        txtDuracionCiclo = new JTextField("1000", 15);
        txtQuantum = new JTextField("3", 15);
        txtLatenciaRed = new JTextField("2", 15);
        txtNumComputadores = new JTextField("2", 15);
        txtRamPorComp = new JTextField("1024", 15);

        agregarFila(panel, gbc, 0, "Duración de ciclo (ms):", txtDuracionCiclo);
        agregarFila(panel, gbc, 1, "Quantum Round Robin:", txtQuantum);
        agregarFila(panel, gbc, 2, "Latencia de Red (ciclos):", txtLatenciaRed);
        agregarFila(panel, gbc, 3, "Número de Computadores:", txtNumComputadores);
        agregarFila(panel, gbc, 4, "RAM por Computador (MB):", txtRamPorComp);

        JButton btnGuardar = new JButton("Guardar en CSV");
        btnGuardar.addActionListener(e -> guardarConfiguracionCSV());

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 12, 8, 12);
        panel.add(btnGuardar, gbc);

        return panel;
    }

    // --- Panel 2: Crear Buffer ---
    private JPanel crearPanelBuffer() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        txtBufferId = new JTextField("BUF-1", 15);
        txtBufferCapacidad = new JTextField("10", 15);
        txtBufferComputador = new JTextField("1", 15);

        agregarFila(panel, gbc, 0, "ID del Buffer:", txtBufferId);
        agregarFila(panel, gbc, 1, "Capacidad Acotada:", txtBufferCapacidad);
        agregarFila(panel, gbc, 2, "Computador Anfitrión:", txtBufferComputador);

        JButton btnCrearBuffer = new JButton("Crear Buffer");
        btnCrearBuffer.addActionListener(e -> crearBufferAccion());

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 12, 8, 12);
        panel.add(btnCrearBuffer, gbc);

        return panel;
    }

    // --- Panel 3: Crear Proceso ---
    private JPanel crearPanelProceso() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 12, 6, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        txtNombreProceso = new JTextField("Proceso_1", 15);
        txtRamProceso = new JTextField("128", 15);
        txtPrioridadProceso = new JTextField("1", 15);
        txtDeadlineProceso = new JTextField("10", 15);
        cbTipoProceso = new JComboBox<>(new String[]{"CPU Bound", "IO Bound", "Productor", "Consumidor"});
        txtCompProceso = new JTextField("1", 15);

        agregarFila(panel, gbc, 0, "Nombre del Proceso:", txtNombreProceso);
        agregarFila(panel, gbc, 1, "Memoria RAM (MB):", txtRamProceso);
        agregarFila(panel, gbc, 2, "Prioridad (1-5):", txtPrioridadProceso);
        agregarFila(panel, gbc, 3, "Deadline (Ciclos):", txtDeadlineProceso);
        agregarFila(panel, gbc, 4, "Tipo de Proceso:", cbTipoProceso);
        agregarFila(panel, gbc, 5, "Computador Asignado:", txtCompProceso);

        JButton btnCrearProceso = new JButton("Guardar / Crear Proceso");
        btnCrearProceso.addActionListener(e -> crearYEncolarProcesoAccion());

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 12, 8, 12);
        panel.add(btnCrearProceso, gbc);

        return panel;
    }

    private void agregarFila(JPanel panel, GridBagConstraints gbc, int fila, String etiqueta, JComponent componente) {
        gbc.gridx = 0; gbc.gridy = fila; gbc.gridwidth = 1;
        panel.add(new JLabel(etiqueta), gbc);
        gbc.gridx = 1;
        panel.add(componente, gbc);
    }

    // Lógica 1: Guardar Configuración Global en CSV
    private void guardarConfiguracionCSV() {
        try {
            int ciclo = Integer.parseInt(txtDuracionCiclo.getText().trim());
            int quantum = Integer.parseInt(txtQuantum.getText().trim());
            int latencia = Integer.parseInt(txtLatenciaRed.getText().trim());
            int comps = Integer.parseInt(txtNumComputadores.getText().trim());

            GestorArchivo.guardarConfiguracion("configuracion.csv", ciclo, quantum, latencia, comps);
            logEventos.registrar(0L, "Configuración global guardada en configuracion.csv");

            JOptionPane.showMessageDialog(this, 
                    "Configuración guardada exitosamente en configuracion.csv", 
                    "Éxito", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar configuración: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Lógica 2: Instanciar Buffer
    private void crearBufferAccion() {
        try {
            String idStr = txtBufferId.getText().trim();
            int cap = Integer.parseInt(txtBufferCapacidad.getText().trim());
            int comp = Integer.parseInt(txtBufferComputador.getText().trim());

            Buffer buffer = new Buffer(idStr, cap, comp);
            logEventos.registrar(0L, "Buffer " + idStr + " (Capacidad: " + cap + ") creado en PC " + comp);

            JOptionPane.showMessageDialog(this, 
                    "Buffer " + idStr + " instanciado exitosamente.", 
                    "Buffer Creado", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al crear buffer: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Lógica 3: Instanciar Proceso y Encolar en ColaProcesos
    private void crearYEncolarProcesoAccion() {
        try {
            String nombre = txtNombreProceso.getText().trim();
            int ram = Integer.parseInt(txtRamProceso.getText().trim());
            int prioridad = Integer.parseInt(txtPrioridadProceso.getText().trim());
            int deadline = Integer.parseInt(txtDeadlineProceso.getText().trim());
            TipoProceso tipo = convertirTipo(cbTipoProceso.getSelectedItem().toString());
            int computador = Integer.parseInt(txtCompProceso.getText().trim());

            Proceso nuevoProceso = new Proceso(contadorProcesoID++, nombre, ram, prioridad, deadline, tipo, computador);

            colaProcesos.encolar(nuevoProceso);

            logEventos.registrar(0L, "Proceso encolado: " + nuevoProceso + " | Procesos en cola: " + colaProcesos.getTamano());

            JOptionPane.showMessageDialog(this,
                    "¡Proceso encolado con éxito!\n\n" +
                            "ID: " + nuevoProceso.getId() + "\n" +
                            "Nombre: " + nuevoProceso.getNombre() + "\n" +
                            "RAM: " + nuevoProceso.getMemoriaRAM() + " MB\n" +
                            "Tipo: " + nuevoProceso.getTipo() + "\n" +
                            "Total de procesos en cola: " + colaProcesos.getTamano(),
                    "Proceso Registrado", JOptionPane.INFORMATION_MESSAGE);

            txtNombreProceso.setText("Proceso_" + contadorProcesoID);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Por favor ingrese números válidos en RAM, Prioridad, Deadline y Computador.", "Error de Formato", JOptionPane.ERROR_MESSAGE);
        }
    }
/**
 * Convierte el texto elegido en el desplegable al enum correspondiente.
 */
private TipoProceso convertirTipo(String texto) {
    switch (texto) {
        case "CPU Bound": return TipoProceso.CPU_BOUND;
        case "IO Bound": return TipoProceso.IO_BOUND;
        case "Productor": return TipoProceso.PRODUCTOR;
        case "Consumidor": return TipoProceso.CONSUMIDOR;
        default: throw new IllegalArgumentException("Tipo de proceso desconocido: " + texto);
    }
}
    public ColaProcesos getColaProcesos() {
        return colaProcesos;
    }
}