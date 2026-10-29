package gui;

import modelo.Proceso;
import reloj.RelojGlobal;
import sincronizacion.ColaProcesos;
import sistema.SistemaDistribuido;
import util.GestorArchivo;
import util.GestorEscenario;
import util.LogEventos;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Vista 1: configuración inicial del sistema (parámetros globales, computadores
 * con su RAM y política, buffers y procesos). Todos los datos se validan (tipo y
 * rango) antes de crear nada; al iniciar se arma el sistema real y se abre la
 * Vista 2.
 */
public class VistaCreacion extends JFrame {

    private static final String[] COLUMNAS_COMPUTADORES = {"Computador", "RAM", "Política inicial"};
    private static final String[] COLUMNAS_PROCESOS = {"Nombre", "Tipo", "Instrucciones", "RAM", "Prioridad",
        "Deadline", "Computador", "Ciclos CPU", "Ciclos E/S", "Buffer", "Ciclos P/C", "Elementos"};
    private static final String[] COLUMNAS_BUFFERS = {"ID Buffer", "Capacidad", "Computador anfitrión"};

    private JTextField txtDuracionCiclo;
    private JTextField txtQuantum;
    private JTextField txtLatencia;

    private DefaultTableModel modeloComputadores;
    private DefaultTableModel modeloProcesos;
    private DefaultTableModel modeloBuffers;
    private JTable tablaComputadores;
    private JTable tablaProcesos;
    private JTable tablaBuffers;

    /**
     * Datos ya validados de toda la configuración.
     */
    private static class Datos {

        int duracionCiclo;
        int quantum;
        int latencia;
        int[] ramComputadores;
        String[] politicas;
        String[] idsBuffers;
        int[] capacidades;
        int[] anfitriones;
        Proceso[] procesos;
    }

    public VistaCreacion() {
        setTitle("ÁvilaOS - Configuración del sistema (Vista 1)");
        setSize(1150, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        initUI();
    }

    private void initUI() {
        // Parámetros globales
        JPanel panelConfig = new JPanel(new GridLayout(1, 6, 10, 10));
        panelConfig.setBorder(BorderFactory.createTitledBorder("Parámetros globales"));
        txtDuracionCiclo = new JTextField("500");
        txtQuantum = new JTextField("4");
        txtLatencia = new JTextField("2");
        panelConfig.add(new JLabel("Duración del ciclo (ms):"));
        panelConfig.add(txtDuracionCiclo);
        panelConfig.add(new JLabel("Quantum de Round Robin:"));
        panelConfig.add(txtQuantum);
        panelConfig.add(new JLabel("Latencia de red (ciclos):"));
        panelConfig.add(txtLatencia);
        add(panelConfig, BorderLayout.NORTH);

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Computadores", construirPanelComputadores());
        pestanas.addTab("Buffers / Semáforos", construirPanelBuffers());
        pestanas.addTab("Procesos iniciales", construirPanelProcesos());
        pestanas.setSelectedIndex(2);
        add(pestanas, BorderLayout.CENTER);

        JPanel panelAcciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCargarEscenario = new JButton("Cargar escenario (CSV)");
        btnCargarEscenario.addActionListener(e -> cargarEscenario());
        JButton btnGuardarEscenario = new JButton("Guardar escenario (CSV)");
        btnGuardarEscenario.addActionListener(e -> guardarEscenario());
        JButton btnGuardarCSV = new JButton("Guardar solo procesos (CSV)");
        btnGuardarCSV.addActionListener(e -> guardarProcesosCSV());
        JButton btnIniciar = new JButton("Iniciar simulación");
        btnIniciar.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnIniciar.setBackground(new Color(40, 167, 69));
        btnIniciar.setForeground(Color.WHITE);
        btnIniciar.addActionListener(e -> iniciarSimulacion());
        panelAcciones.add(btnCargarEscenario);
        panelAcciones.add(btnGuardarEscenario);
        panelAcciones.add(btnGuardarCSV);
        panelAcciones.add(btnIniciar);
        add(panelAcciones, BorderLayout.SOUTH);
    }

    private JPanel construirPanelComputadores() {
        modeloComputadores = new DefaultTableModel(COLUMNAS_COMPUTADORES, 0) {
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return columna != 0;
            }
        };
        modeloComputadores.addRow(new Object[]{"PC 1", "100", "Round Robin"});
        modeloComputadores.addRow(new Object[]{"PC 2", "100", "FCFS"});
        tablaComputadores = new JTable(modeloComputadores);
        tablaComputadores.getColumnModel().getColumn(2).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(FabricaSistema.POLITICAS)));

        JButton btnAgregar = new JButton("Agregar computador");
        btnAgregar.addActionListener(e -> {
            detenerEdicion();
            modeloComputadores.addRow(new Object[]{"PC " + (modeloComputadores.getRowCount() + 1),
                "100", "Round Robin"});
        });
        JButton btnQuitar = new JButton("Quitar último");
        btnQuitar.addActionListener(e -> {
            detenerEdicion();
            if (modeloComputadores.getRowCount() > 2) {
                modeloComputadores.removeRow(modeloComputadores.getRowCount() - 1);
            } else {
                JOptionPane.showMessageDialog(this, "Debe haber al menos 2 computadores.",
                        "Computadores", JOptionPane.WARNING_MESSAGE);
            }
        });

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.add(btnAgregar);
        botones.add(btnQuitar);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JScrollPane(tablaComputadores), BorderLayout.CENTER);
        panel.add(botones, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel construirPanelBuffers() {
        modeloBuffers = new DefaultTableModel(COLUMNAS_BUFFERS, 0);
        modeloBuffers.addRow(new Object[]{"B1", "3", "1"});
        tablaBuffers = new JTable(modeloBuffers);

        JButton btnAgregar = new JButton("Agregar buffer");
        btnAgregar.addActionListener(e -> {
            detenerEdicion();
            modeloBuffers.addRow(new Object[]{"B" + (modeloBuffers.getRowCount() + 1), "3", "1"});
        });
        JButton btnQuitar = new JButton("Quitar último");
        btnQuitar.addActionListener(e -> {
            detenerEdicion();
            if (modeloBuffers.getRowCount() > 0) {
                modeloBuffers.removeRow(modeloBuffers.getRowCount() - 1);
            }
        });

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.add(btnAgregar);
        botones.add(btnQuitar);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JScrollPane(tablaBuffers), BorderLayout.CENTER);
        panel.add(botones, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel construirPanelProcesos() {
        modeloProcesos = new DefaultTableModel(COLUMNAS_PROCESOS, 0);
        //                                  nombre  tipo          instr RAM  prio dead   PC   cCPU cIO  buffer cPC  elem
        modeloProcesos.addRow(new Object[]{"P1", "CPU Bound", "20", "10", "1", "1000", "1", "", "", "", "", ""});
        modeloProcesos.addRow(new Object[]{"P2", "IO Bound", "20", "10", "2", "1000", "2", "3", "4", "", "", ""});
        modeloProcesos.addRow(new Object[]{"P3", "Productor", "10", "10", "3", "1000", "1", "", "", "B1", "2", "5"});
        modeloProcesos.addRow(new Object[]{"P4", "Consumidor", "10", "10", "4", "1000", "2", "", "", "B1", "2", "5"});
        tablaProcesos = new JTable(modeloProcesos);
        tablaProcesos.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tablaProcesos.getColumnModel().getColumn(1).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(FabricaSistema.TIPOS)));

        JButton btnAgregar = new JButton("Agregar proceso");
        btnAgregar.addActionListener(e -> {
            detenerEdicion();
            int n = modeloProcesos.getRowCount() + 1;
            modeloProcesos.addRow(new Object[]{"P" + n, "CPU Bound", "20", "10", "1", "1000", "1",
                "", "", "", "", ""});
        });
        JButton btnQuitar = new JButton("Quitar último");
        btnQuitar.addActionListener(e -> {
            detenerEdicion();
            if (modeloProcesos.getRowCount() > 0) {
                modeloProcesos.removeRow(modeloProcesos.getRowCount() - 1);
            }
        });

        JLabel ayuda = new JLabel("  Ciclos CPU y E/S solo para IO Bound; Buffer, Ciclos P/C y Elementos solo para Productor y Consumidor.");

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.add(btnAgregar);
        botones.add(btnQuitar);
        botones.add(ayuda);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JScrollPane(tablaProcesos), BorderLayout.CENTER);
        panel.add(botones, BorderLayout.SOUTH);
        return panel;
    }

    private void detenerEdicion() {
        JTable[] tablas = {tablaComputadores, tablaBuffers, tablaProcesos};
        for (int i = 0; i < tablas.length; i++) {
            if (tablas[i] != null && tablas[i].isEditing()) {
                tablas[i].getCellEditor().stopCellEditing();
            }
        }
    }

    /**
     * Lee y valida toda la configuración. Si algo está mal, lanza
     * IllegalArgumentException con el mensaje para el usuario.
     */
    private Datos leerYValidar() {
        detenerEdicion();
        Datos d = new Datos();
        d.duracionCiclo = FabricaSistema.entero("Duración del ciclo (ms)", txtDuracionCiclo.getText(), 1, 60000);
        d.quantum = FabricaSistema.entero("Quantum de Round Robin", txtQuantum.getText(), 1, 1000);
        d.latencia = FabricaSistema.entero("Latencia de red (ciclos)", txtLatencia.getText(), 1, 1000);

        int numPC = modeloComputadores.getRowCount();
        if (numPC < 2) {
            throw new IllegalArgumentException("Debe haber al menos 2 computadores.");
        }
        d.ramComputadores = new int[numPC];
        d.politicas = new String[numPC];
        for (int i = 0; i < numPC; i++) {
            d.ramComputadores[i] = FabricaSistema.entero("RAM del computador " + (i + 1),
                    modeloComputadores.getValueAt(i, 1), 1, 1000000);
            d.politicas[i] = String.valueOf(modeloComputadores.getValueAt(i, 2));
            boolean conocida = false;
            for (int j = 0; j < FabricaSistema.POLITICAS.length; j++) {
                if (FabricaSistema.POLITICAS[j].equals(d.politicas[i])) {
                    conocida = true;
                }
            }
            if (!conocida) {
                throw new IllegalArgumentException("La política del computador " + (i + 1) + " (\""
                        + d.politicas[i] + "\") no existe. Use: Round Robin, FCFS, EDF o Prioridades.");
            }
        }

        int numBuf = modeloBuffers.getRowCount();
        d.idsBuffers = new String[numBuf];
        d.capacidades = new int[numBuf];
        d.anfitriones = new int[numBuf];
        for (int i = 0; i < numBuf; i++) {
            d.idsBuffers[i] = FabricaSistema.texto("ID del buffer " + (i + 1), modeloBuffers.getValueAt(i, 0), 20);
            for (int j = 0; j < i; j++) {
                if (d.idsBuffers[j].equals(d.idsBuffers[i])) {
                    throw new IllegalArgumentException("El buffer \"" + d.idsBuffers[i] + "\" está repetido.");
                }
            }
            d.capacidades[i] = FabricaSistema.entero("Capacidad del buffer " + d.idsBuffers[i],
                    modeloBuffers.getValueAt(i, 1), 1, 1000);
            d.anfitriones[i] = FabricaSistema.entero("Computador anfitrión del buffer " + d.idsBuffers[i],
                    modeloBuffers.getValueAt(i, 2), 1, numPC);
            if (d.capacidades[i] > d.ramComputadores[d.anfitriones[i] - 1]) {
                throw new IllegalArgumentException("El buffer \"" + d.idsBuffers[i]
                        + "\" no cabe en la RAM del computador " + d.anfitriones[i] + ".");
            }
        }

        int numProc = modeloProcesos.getRowCount();
        d.procesos = new Proceso[numProc];
        for (int i = 0; i < numProc; i++) {
            try {
                d.procesos[i] = FabricaSistema.crearProceso(
                        valor(i, 0), valor(i, 1), valor(i, 2), valor(i, 3), valor(i, 4), valor(i, 5),
                        valor(i, 6), valor(i, 7), valor(i, 8), valor(i, 9), valor(i, 10), valor(i, 11),
                        numPC, d.ramComputadores, d.idsBuffers);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Proceso de la fila " + (i + 1) + ": " + ex.getMessage());
            }
        }
        return d;
    }

    private String valor(int fila, int columna) {
        Object v = modeloProcesos.getValueAt(fila, columna);
        return (v == null) ? "" : v.toString();
    }

    // ------------------------------------------------------------ escenario CSV
    private String[][] volcarTabla(DefaultTableModel modelo, int columnas, boolean numerarPrimera) {
        String[][] filas = new String[modelo.getRowCount()][columnas];
        for (int i = 0; i < filas.length; i++) {
            for (int j = 0; j < columnas; j++) {
                Object v = modelo.getValueAt(i, j);
                filas[i][j] = (v == null) ? "" : v.toString().trim();
            }
            if (numerarPrimera) {
                filas[i][0] = String.valueOf(i + 1);
            }
        }
        return filas;
    }

    /**
     * Guarda en un CSV toda la configuración de la pantalla (globales,
     * computadores, buffers y procesos). Solo guarda configuraciones válidas.
     */
    private void guardarEscenario() {
        try {
            leerYValidar();
            JFileChooser selector = new JFileChooser(new File("."));
            selector.setDialogTitle("Guardar escenario");
            selector.setSelectedFile(new File("escenario.csv"));
            if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File archivo = selector.getSelectedFile();
            if (!archivo.getName().toLowerCase().endsWith(".csv")) {
                archivo = new File(archivo.getPath() + ".csv");
            }
            if (archivo.exists() && JOptionPane.showConfirmDialog(this,
                    "El archivo " + archivo.getName() + " ya existe. ¿Reemplazarlo?", "Guardar escenario",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            String[] globales = {txtDuracionCiclo.getText().trim(), txtQuantum.getText().trim(),
                txtLatencia.getText().trim()};
            GestorEscenario.guardar(archivo.getPath(), globales,
                    volcarTabla(modeloComputadores, 3, true),
                    volcarTabla(modeloBuffers, 3, false),
                    volcarTabla(modeloProcesos, 12, false));
            JOptionPane.showMessageDialog(this, "Escenario guardado en " + archivo.getName() + ".",
                    "CSV", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Datos inválidos", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar el CSV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Carga un escenario desde un CSV y rellena la pantalla. Si el formato del
     * archivo es incorrecto no se cambia nada; si el formato es correcto pero
     * algún dato no cumple los rangos, se carga igual y se avisa para que el
     * usuario lo corrija.
     */
    private void cargarEscenario() {
        detenerEdicion();
        JFileChooser selector = new JFileChooser(new File("."));
        selector.setDialogTitle("Cargar escenario");
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        GestorEscenario.Escenario escenario;
        try {
            escenario = GestorEscenario.cargar(selector.getSelectedFile().getPath());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Archivo inválido", JOptionPane.ERROR_MESSAGE);
            return;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo leer el CSV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        txtDuracionCiclo.setText(escenario.globales[0]);
        txtQuantum.setText(escenario.globales[1]);
        txtLatencia.setText(escenario.globales[2]);

        modeloComputadores.setRowCount(0);
        for (int i = 0; i < escenario.computadores.length; i++) {
            modeloComputadores.addRow(new Object[]{"PC " + (i + 1), escenario.computadores[i][1],
                escenario.computadores[i][2]});
        }
        modeloBuffers.setRowCount(0);
        for (int i = 0; i < escenario.buffers.length; i++) {
            modeloBuffers.addRow((Object[]) escenario.buffers[i]);
        }
        modeloProcesos.setRowCount(0);
        for (int i = 0; i < escenario.procesos.length; i++) {
            modeloProcesos.addRow((Object[]) escenario.procesos[i]);
        }

        try {
            leerYValidar();
            JOptionPane.showMessageDialog(this, "Escenario cargado: " + escenario.computadores.length
                    + " computadores, " + escenario.buffers.length + " buffers y " + escenario.procesos.length
                    + " procesos.", "CSV", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "El archivo se cargó, pero hay datos que corregir:\n"
                    + ex.getMessage(), "Datos inválidos", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void guardarProcesosCSV() {
        try {
            Datos d = leerYValidar();
            ColaProcesos cola = new ColaProcesos();
            for (int i = 0; i < d.procesos.length; i++) {
                cola.encolar(d.procesos[i]);
            }
            GestorArchivo.guardarProcesosCSV("procesos.csv", cola);
            JOptionPane.showMessageDialog(this, "Procesos guardados en procesos.csv.",
                    "CSV", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Datos inválidos", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar el CSV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void iniciarSimulacion() {
        Datos d;
        try {
            d = leerYValidar();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Datos inválidos", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            RelojGlobal reloj = new RelojGlobal(d.duracionCiclo);
            SistemaDistribuido sistema = new SistemaDistribuido(d.latencia, reloj);
            LogEventos log = new LogEventos(5000);

            for (int i = 0; i < d.ramComputadores.length; i++) {
                sistema.agregarComputador(d.ramComputadores[i],
                        FabricaSistema.crearPolitica(d.politicas[i], d.quantum));
            }
            for (int i = 0; i < d.idsBuffers.length; i++) {
                if (sistema.crearBuffer(d.idsBuffers[i], d.capacidades[i], d.anfitriones[i]) == null) {
                    throw new IllegalArgumentException("No se pudo crear el buffer \"" + d.idsBuffers[i]
                            + "\" (id repetido o sin RAM suficiente en su computador).");
                }
            }
            for (int i = 0; i < d.procesos.length; i++) {
                if (!sistema.enviarProceso(d.procesos[i])) {
                    throw new IllegalArgumentException("No se pudo enviar el proceso \""
                            + d.procesos[i].getNombre() + "\" a su computador.");
                }
            }

            log.registrar(0L, "Sistema configurado: " + d.ramComputadores.length + " computadores, "
                    + d.idsBuffers.length + " buffers, " + d.procesos.length + " procesos.");

            VistaSimulacion vista2 = new VistaSimulacion(reloj, sistema);
            vista2.setLogEventos(log);
            vista2.setVisible(true);
            reloj.arrancar();
            log.registrar(0L, "Simulación iniciada.");
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo iniciar", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al iniciar la simulación: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VistaCreacion().setVisible(true));
    }
}