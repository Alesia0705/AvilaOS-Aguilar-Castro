package gui;

import sistema.SistemaDistribuido;
import computador.Computador;
import modelo.Proceso;
import modelo.enums.TipoProceso;
import planificacion.*;
import sincronizacion.ColaProcesos;
import reloj.RelojGlobal;
import util.GestorArchivo;
import util.LogEventos;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Vista 1: Configuración inicial del sistema, computadores, procesos y buffers.
 */
public class VistaCreacion extends JFrame {

    private JTextField txtDuracionCiclo;
    private JTextField txtQuantumGlobal;
    private JTextField txtNumComputadores;
    private JComboBox<String> comboPoliticaGlobal;

    private JTable tablaProcesos;
    private DefaultTableModel modeloProcesos;

    private JTable tablaBuffers;
    private DefaultTableModel modeloBuffers;

    public VistaCreacion() {
        setTitle("AvilaOS - Configuración del Sistema (Vista 1)");
        setSize(900, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        initUI();
    }

    private void initUI() {
        // Panel Superior: Parámetros Globales
        JPanel panelConfig = new JPanel(new GridLayout(2, 4, 10, 10));
        panelConfig.setBorder(BorderFactory.createTitledBorder("Parámetros Globales"));

        panelConfig.add(new JLabel("Duración Ciclo (ms):"));
        txtDuracionCiclo = new JTextField("1000");
        panelConfig.add(txtDuracionCiclo);

        panelConfig.add(new JLabel("Quantum por defecto:"));
        txtQuantumGlobal = new JTextField("5");
        panelConfig.add(txtQuantumGlobal);

        panelConfig.add(new JLabel("Número de Computadores:"));
        txtNumComputadores = new JTextField("2");
        panelConfig.add(txtNumComputadores);

        panelConfig.add(new JLabel("Política Global:"));
        comboPoliticaGlobal = new JComboBox<>(new String[]{"Round Robin", "FCFS", "Prioridades", "EDF"});
        panelConfig.add(comboPoliticaGlobal);

        add(panelConfig, BorderLayout.NORTH);

        // Panel Central: Tablas de Procesos y Buffers
        JTabbedPane tabbedPane = new JTabbedPane();

        // Pestaña Procesos (PC Asignado en base 1: PC 1 y PC 2)
        JPanel panelProcesos = new JPanel(new BorderLayout(5, 5));
        modeloProcesos = new DefaultTableModel(
                new Object[]{"ID", "Nombre", "Tipo", "Instrucciones", "PC Asignado", "Prioridad"}, 0);
        tablaProcesos = new JTable(modeloProcesos);
        
        // Datos de prueba iniciales
        modeloProcesos.addRow(new Object[]{"P1", "Proceso 1", "CPU Bound", "20", "1", "1"});
        modeloProcesos.addRow(new Object[]{"P2", "Proceso 2", "CPU Bound", "20", "2", "1"});
        modeloProcesos.addRow(new Object[]{"P3", "Productor", "Productor", "10", "1", "2"});
        modeloProcesos.addRow(new Object[]{"P4", "Consumidor", "Consumidor", "10", "2", "2"});

        JPanel panelBotonesProc = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnAgregarProc = new JButton("Agregar Proceso");
        btnAgregarProc.addActionListener(e -> agregarFilaProceso());
        panelBotonesProc.add(btnAgregarProc);

        panelProcesos.add(new JScrollPane(tablaProcesos), BorderLayout.CENTER);
        panelProcesos.add(panelBotonesProc, BorderLayout.SOUTH);

        // Pestaña Buffers
        JPanel panelBuffers = new JPanel(new BorderLayout(5, 5));
        modeloBuffers = new DefaultTableModel(new Object[]{"ID Buffer", "Capacidad Maxima"}, 0);
        tablaBuffers = new JTable(modeloBuffers);
        modeloBuffers.addRow(new Object[]{"B1", "5"});

        JPanel panelBotonesBuf = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnAgregarBuf = new JButton("Agregar Buffer");
        btnAgregarBuf.addActionListener(e -> modeloBuffers.addRow(new Object[]{"B" + (modeloBuffers.getRowCount() + 1), "5"}));
        panelBotonesBuf.add(btnAgregarBuf);

        panelBuffers.add(new JScrollPane(tablaBuffers), BorderLayout.CENTER);
        panelBuffers.add(panelBotonesBuf, BorderLayout.SOUTH);

        tabbedPane.addTab("Procesos Iniciales", panelProcesos);
        tabbedPane.addTab("Buffers / Semáforos", panelBuffers);

        add(tabbedPane, BorderLayout.CENTER);

        // Panel Inferior: Acciones
        JPanel panelAcciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardarCSV = new JButton("Guardar en CSV");
        btnGuardarCSV.addActionListener(e -> guardarConfiguracionCSV());

        JButton btnIniciar = new JButton("Iniciar Simulación");
        btnIniciar.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnIniciar.setBackground(new Color(40, 167, 69));
        btnIniciar.setForeground(Color.WHITE);
        btnIniciar.addActionListener(e -> iniciarSimulacion());

        panelAcciones.add(btnGuardarCSV);
        panelAcciones.add(btnIniciar);

        add(panelAcciones, BorderLayout.SOUTH);
    }

    private void agregarFilaProceso() {
        int nextId = modeloProcesos.getRowCount() + 1;
        modeloProcesos.addRow(new Object[]{
            "P" + nextId, "Proceso " + nextId, "CPU Bound", "15", "1", "1"
        });
    }

    private void guardarConfiguracionCSV() {
        try {
            ColaProcesos colaGuardar = new ColaProcesos();
            for (int i = 0; i < modeloProcesos.getRowCount(); i++) {
                String nombre = modeloProcesos.getValueAt(i, 1).toString();
                String tipoStr = modeloProcesos.getValueAt(i, 2).toString();
                int inst = Integer.parseInt(modeloProcesos.getValueAt(i, 3).toString());
                int pc = Integer.parseInt(modeloProcesos.getValueAt(i, 4).toString());
                if (pc == 0) pc = 1;
                int prio = Integer.parseInt(modeloProcesos.getValueAt(i, 5).toString());

                TipoProceso tipoEnum = TipoProceso.CPU_BOUND;
                if (tipoStr.equalsIgnoreCase("Productor")) tipoEnum = TipoProceso.PRODUCTOR;
                else if (tipoStr.equalsIgnoreCase("Consumidor")) tipoEnum = TipoProceso.CONSUMIDOR;

                Proceso p = new Proceso(nombre, inst, 64, prio, 100, tipoEnum, pc);
                if (tipoEnum == TipoProceso.PRODUCTOR || tipoEnum == TipoProceso.CONSUMIDOR) {
                    p.setIdBuffer("B1");
                }
                colaGuardar.encolar(p);
            }

            GestorArchivo.guardarProcesosCSV("procesos.csv", colaGuardar);
            JOptionPane.showMessageDialog(this, "Configuración guardada en CSV correctamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void iniciarSimulacion() {
        try {
            int duracionCiclo = Integer.parseInt(txtDuracionCiclo.getText());
            int quantumGlobal = Integer.parseInt(txtQuantumGlobal.getText());
            int numComputadores = Integer.parseInt(txtNumComputadores.getText());
            String polNombre = (String) comboPoliticaGlobal.getSelectedItem();

            RelojGlobal reloj = new RelojGlobal(duracionCiclo);
            SistemaDistribuido sistemaDistribuido = new SistemaDistribuido(10, reloj);
            LogEventos logEventos = new LogEventos(1000);

            // 1. Agregar Computadores con la política seleccionada
            for (int i = 0; i < numComputadores; i++) {
                PoliticaPlanificacion pol;
                if ("Round Robin".equals(polNombre)) {
                    pol = new PoliticaRoundRobin(quantumGlobal);
                } else if ("FCFS".equals(polNombre)) {
                    pol = new PoliticaFCFS();
                } else if ("Prioridades".equals(polNombre)) {
                    pol = new PoliticaPrioridades();
                } else {
                    pol = new PoliticaEDF();
                }
                sistemaDistribuido.agregarComputador(1024, pol);
            }

            // 2. Cargar Buffers asignándolos al PC 1 (Anfitrión)
            for (int i = 0; i < modeloBuffers.getRowCount(); i++) {
                String id = modeloBuffers.getValueAt(i, 0).toString();
                int capacidad = Integer.parseInt(modeloBuffers.getValueAt(i, 1).toString());
                sistemaDistribuido.crearBuffer(id, capacidad, 1);
            }

            // 3. Cargar Procesos vinculando Productor y Consumidor a B1
            for (int i = 0; i < modeloProcesos.getRowCount(); i++) {
                String nombre = modeloProcesos.getValueAt(i, 1).toString();
                String tipoStr = modeloProcesos.getValueAt(i, 2).toString();
                int instrucciones = Integer.parseInt(modeloProcesos.getValueAt(i, 3).toString());
                int pcAsignado = Integer.parseInt(modeloProcesos.getValueAt(i, 4).toString());
                if (pcAsignado == 0) pcAsignado = 1; // Ajuste automático a PC 1 si ingresaron 0
                int prioridad = Integer.parseInt(modeloProcesos.getValueAt(i, 5).toString());

                TipoProceso tipoEnum = TipoProceso.CPU_BOUND;
                if (tipoStr.equalsIgnoreCase("Productor")) tipoEnum = TipoProceso.PRODUCTOR;
                else if (tipoStr.equalsIgnoreCase("Consumidor")) tipoEnum = TipoProceso.CONSUMIDOR;

                Proceso p = new Proceso(nombre, instrucciones, 64, prioridad, 100, tipoEnum, pcAsignado);
                if (tipoEnum == TipoProceso.PRODUCTOR || tipoEnum == TipoProceso.CONSUMIDOR) {
                    p.setIdBuffer("B1");
                }
                sistemaDistribuido.enviarProceso(p);
            }

            logEventos.registrar(0L, "Simulación iniciada con " + numComputadores + " computadores.");

            // 4. Instanciar Vista 2
            VistaSimulacion vista2 = new VistaSimulacion(duracionCiclo, sistemaDistribuido, logEventos);
            vista2.setVisible(true);

            this.dispose();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Por favor verifica que los campos numéricos sean válidos.", "Error de Formato", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al iniciar la simulación: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VistaCreacion().setVisible(true));
    }
}