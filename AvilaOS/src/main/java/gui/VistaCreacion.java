package gui;

import modelo.ConfiguracionComputador;
import modelo.Proceso;
import modelo.enums.TipoProceso;
import sincronizacion.Buffer;
import sincronizacion.ColaProcesos;
import util.GestorArchivo;
import util.LogEventos;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Vista 1: Interfaz gráfica Swing para la configuración inicial de ÁvilaOS.
 * Permite definir parámetros globales, RAM y política por computador (Issue 2),
 * crear buffers e instanciar/encolar procesos reales con campos dinámicos (Issue 1).
 */
public class VistaCreacion extends JFrame {

    // Estructuras de datos del sistema y utilidades
    private final ColaProcesos colaProcesos = new ColaProcesos();
    private final LogEventos logEventos = new LogEventos(100);

    // Componentes Pestaña 1: Configuración Global Parámetros Generales
    private JTextField txtDuracionCiclo;
    private JTextField txtQuantum;
    private JTextField txtLatenciaRed;
    private JTextField txtNumComputadores;

    // Componentes Dinámicos Pestaña 1 (Issue 2: Configuración por Computador)
    private JPanel panelDetalleComputadores;
    private final List<JTextField> listaTxtRamComps = new ArrayList<>();
    private final List<JComboBox<String>> listaCbPoliticaComps = new ArrayList<>();

    // Componentes Pestaña 2: Crear Buffer
    private JTextField txtBufferId;
    private JTextField txtBufferCapacidad;
    private JTextField txtBufferComputador;

    // Componentes Pestaña 3: Crear Proceso (Campos Base)
    private JTextField txtNombreProceso;
    private JTextField txtInstruccionesProceso;
    private JTextField txtRamProceso;
    private JTextField txtPrioridadProceso;
    private JTextField txtDeadlineProceso;
    private JComboBox<String> cbTipoProceso;
    private JTextField txtCompProceso;

    // Componentes Dinámicos Pestaña 3 (Issue 1)
    private JLabel lblCiclosCpu;
    private JTextField txtCiclosCpu;

    private JLabel lblCiclosIo;
    private JTextField txtCiclosIo;

    private JLabel lblBufferProceso;
    private JTextField txtBufferProceso;
    private JLabel lblCiclosProdCons;
    private JTextField txtCiclosProdCons;
    private JLabel lblCantElementos;
    private JTextField txtCantElementos;

    private JPanel panelProceso; // Referencia para refrescar layout dinámico

    public VistaCreacion() {
        super("ÁvilaOS - Vista 1: Configuración del Sistema");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(650, 680);
        setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab("Configuración Global", crearPanelConfigGlobal());
        tabbedPane.addTab("Crear Buffer", crearPanelBuffer());
        tabbedPane.addTab("Crear Proceso", crearPanelProceso());

        add(tabbedPane);
    }

    // --- Panel 1: Configuración Global (Issue 2) ---
    private JPanel crearPanelConfigGlobal() {
        JPanel panelPrincipal = new JPanel(new BorderLayout(10, 10));
        panelPrincipal.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel panelGenerales = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 12, 6, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        txtDuracionCiclo = new JTextField("1000", 12);
        txtQuantum = new JTextField("3", 12);
        txtLatenciaRed = new JTextField("2", 12);
        txtNumComputadores = new JTextField("2", 12);

        agregarFila(panelGenerales, gbc, 0, "Duración de ciclo (ms):", txtDuracionCiclo);
        agregarFila(panelGenerales, gbc, 1, "Quantum Round Robin:", txtQuantum);
        agregarFila(panelGenerales, gbc, 2, "Latencia de Red (ciclos):", txtLatenciaRed);

        // Fila de Número de Computadores con botón de actualización
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        panelGenerales.add(new JLabel("Número de Computadores:"), gbc);

        JPanel panelCompInput = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panelCompInput.add(txtNumComputadores);
        JButton btnAplicarComps = new JButton("Aplicar PCs");
        btnAplicarComps.addActionListener(e -> generarCamposComputadores());
        panelCompInput.add(btnAplicarComps);

        gbc.gridx = 1;
        panelGenerales.add(panelCompInput, gbc);

        // Panel contenedor dinámico de RAM y Política por Computador
        panelDetalleComputadores = new JPanel();
        panelDetalleComputadores.setLayout(new BoxLayout(panelDetalleComputadores, BoxLayout.Y_AXIS));
        JScrollPane scrollComputadores = new JScrollPane(panelDetalleComputadores);
        scrollComputadores.setBorder(BorderFactory.createTitledBorder("Configuración por Computador (RAM y Política Inicial)"));
        scrollComputadores.setPreferredSize(new Dimension(580, 220));

        // Botón Guardar
        JButton btnGuardar = new JButton("Guardar Configuración Global en CSV");
        btnGuardar.addActionListener(e -> guardarConfiguracionCSV());

        panelPrincipal.add(panelGenerales, BorderLayout.NORTH);
        panelPrincipal.add(scrollComputadores, BorderLayout.CENTER);
        panelPrincipal.add(btnGuardar, BorderLayout.SOUTH);

        // Estado inicial con 2 computadores
        generarCamposComputadores();

        return panelPrincipal;
    }

    /**
     * Genera dinámicamente las casillas de RAM y Selección de Política para cada Computador (Issue 2).
     */
    private void generarCamposComputadores() {
        Integer numComps = validarRangoEntero(txtNumComputadores.getText(), "Número de Computadores", 1, 10);
        if (numComps == null) return;

        panelDetalleComputadores.removeAll();
        listaTxtRamComps.clear();
        listaCbPoliticaComps.clear();

        String[] politicasDisponibles = new String[]{"Round Robin", "FCFS", "Prioridades", "SRTF"};

        for (int i = 1; i <= numComps; i++) {
            JPanel panelPC = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
            panelPC.setBorder(BorderFactory.createEtchedBorder());

            JLabel lblPC = new JLabel("PC " + i + ":");
            lblPC.setFont(lblPC.getFont().deriveFont(Font.BOLD));

            JLabel lblRam = new JLabel("RAM (MB):");
            JTextField txtRam = new JTextField("1024", 8);

            JLabel lblPolitica = new JLabel("Política:");
            JComboBox<String> cbPolitica = new JComboBox<>(politicasDisponibles);

            panelPC.add(lblPC);
            panelPC.add(lblRam);
            panelPC.add(txtRam);
            panelPC.add(lblPolitica);
            panelPC.add(cbPolitica);

            listaTxtRamComps.add(txtRam);
            listaCbPoliticaComps.add(cbPolitica);

            panelDetalleComputadores.add(panelPC);
        }

        panelDetalleComputadores.revalidate();
        panelDetalleComputadores.repaint();
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
        panelProceso = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 12, 5, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        txtNombreProceso = new JTextField("Proceso_1", 15);
        txtInstruccionesProceso = new JTextField("20", 15);
        txtRamProceso = new JTextField("128", 15);
        txtPrioridadProceso = new JTextField("1", 15);
        txtDeadlineProceso = new JTextField("10", 15);
        cbTipoProceso = new JComboBox<>(new String[]{"CPU Bound", "IO Bound", "Productor", "Consumidor"});
        txtCompProceso = new JTextField("1", 15);

        // Instanciación de componentes dinámicos (Issue 1)
        lblCiclosCpu = new JLabel("Ciclos CPU:");
        txtCiclosCpu = new JTextField("5", 15);

        lblCiclosIo = new JLabel("Ciclos I/O:");
        txtCiclosIo = new JTextField("3", 15);

        lblBufferProceso = new JLabel("ID del Buffer:");
        txtBufferProceso = new JTextField("BUF-1", 15);

        lblCiclosProdCons = new JLabel("Ciclos Prod/Cons:");
        txtCiclosProdCons = new JTextField("2", 15);

        lblCantElementos = new JLabel("Cantidad Elementos:");
        txtCantElementos = new JTextField("1", 15);

        // Agregar filas base
        agregarFila(panelProceso, gbc, 0, "Nombre del Proceso:", txtNombreProceso);
        agregarFila(panelProceso, gbc, 1, "Cantidad de instrucciones:", txtInstruccionesProceso);
        agregarFila(panelProceso, gbc, 2, "Memoria RAM (MB):", txtRamProceso);
        agregarFila(panelProceso, gbc, 3, "Prioridad (1-5):", txtPrioridadProceso);
        agregarFila(panelProceso, gbc, 4, "Deadline (Ciclos):", txtDeadlineProceso);
        agregarFila(panelProceso, gbc, 5, "Tipo de Proceso:", cbTipoProceso);
        agregarFila(panelProceso, gbc, 6, "Computador Asignado:", txtCompProceso);

        // Agregar filas dinámicas al layout
        agregarFilaComponente(panelProceso, gbc, 7, lblCiclosCpu, txtCiclosCpu);
        agregarFilaComponente(panelProceso, gbc, 8, lblCiclosIo, txtCiclosIo);
        agregarFilaComponente(panelProceso, gbc, 9, lblBufferProceso, txtBufferProceso);
        agregarFilaComponente(panelProceso, gbc, 10, lblCiclosProdCons, txtCiclosProdCons);
        agregarFilaComponente(panelProceso, gbc, 11, lblCantElementos, txtCantElementos);

        JButton btnCrearProceso = new JButton("Guardar / Crear Proceso");
        btnCrearProceso.addActionListener(e -> crearYEncolarProcesoAccion());

        gbc.gridx = 0; gbc.gridy = 12; gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 12, 8, 12);
        panelProceso.add(btnCrearProceso, gbc);

        // Evento de cambio en el ComboBox
        cbTipoProceso.addActionListener(e -> actualizarCamposDinamicos());
        actualizarCamposDinamicos(); // Estado inicial

        return panelProceso;
    }

    private void agregarFila(JPanel panel, GridBagConstraints gbc, int fila, String etiqueta, JComponent componente) {
        gbc.gridx = 0; gbc.gridy = fila; gbc.gridwidth = 1;
        panel.add(new JLabel(etiqueta), gbc);
        gbc.gridx = 1;
        panel.add(componente, gbc);
    }

    private void agregarFilaComponente(JPanel panel, GridBagConstraints gbc, int fila, JLabel label, JComponent componente) {
        gbc.gridx = 0; gbc.gridy = fila; gbc.gridwidth = 1;
        panel.add(label, gbc);
        gbc.gridx = 1;
        panel.add(componente, gbc);
    }

    /**
     * Muestra u oculta los campos dinámicos según el tipo de proceso seleccionado (Issue 1).
     */
    private void actualizarCamposDinamicos() {
        String tipoSeleccionado = (String) cbTipoProceso.getSelectedItem();

        // Ocultar todo por defecto
        lblCiclosCpu.setVisible(false);
        txtCiclosCpu.setVisible(false);

        lblCiclosIo.setVisible(false);
        txtCiclosIo.setVisible(false);

        lblBufferProceso.setVisible(false);
        txtBufferProceso.setVisible(false);
        lblCiclosProdCons.setVisible(false);
        txtCiclosProdCons.setVisible(false);
        lblCantElementos.setVisible(false);
        txtCantElementos.setVisible(false);

        if (tipoSeleccionado != null) {
            switch (tipoSeleccionado) {
                case "CPU Bound":
                    lblCiclosCpu.setVisible(true);
                    txtCiclosCpu.setVisible(true);
                    break;
                case "IO Bound":
                    lblCiclosIo.setVisible(true);
                    txtCiclosIo.setVisible(true);
                    break;
                case "Productor":
                case "Consumidor":
                    lblBufferProceso.setVisible(true);
                    txtBufferProceso.setVisible(true);
                    lblCiclosProdCons.setVisible(true);
                    txtCiclosProdCons.setVisible(true);
                    lblCantElementos.setVisible(true);
                    txtCantElementos.setVisible(true);
                    break;
            }
        }

        panelProceso.revalidate();
        panelProceso.repaint();
    }

    // --- Lógica de Validaciones ---

    private boolean esCampoTextoValido(String texto, String nombreCampo) {
        if (texto == null || texto.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "El campo '" + nombreCampo + "' no puede estar vacío.", "Campo Requerido", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private Integer validarEnteroPositivo(String texto, String nombreCampo) {
        if (!esCampoTextoValido(texto, nombreCampo)) return null;
        try {
            int num = Integer.parseInt(texto.trim());
            if (num <= 0) {
                JOptionPane.showMessageDialog(this, "El campo '" + nombreCampo + "' debe ser un número entero positivo mayor a 0.", "Valor Inválido", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            return num;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "El campo '" + nombreCampo + "' debe ser un número entero válido.", "Error de Formato", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private Integer validarRangoEntero(String texto, String nombreCampo, int min, int max) {
        Integer num = validarEnteroPositivo(texto, nombreCampo);
        if (num == null) return null;
        if (num < min || num > max) {
            JOptionPane.showMessageDialog(this, "El campo '" + nombreCampo + "' debe estar comprendido entre " + min + " y " + max + ".", "Rango Inválido", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return num;
    }

    // Lógica 1: Guardar Configuración Global en CSV (Issue 2)
    private void guardarConfiguracionCSV() {
        Integer ciclo = validarEnteroPositivo(txtDuracionCiclo.getText(), "Duración de Ciclo");
        if (ciclo == null) return;

        Integer quantum = validarEnteroPositivo(txtQuantum.getText(), "Quantum");
        if (quantum == null) return;

        Integer latencia = validarEnteroPositivo(txtLatenciaRed.getText(), "Latencia de Red");
        if (latencia == null) return;

        Integer comps = validarRangoEntero(txtNumComputadores.getText(), "Número de Computadores", 1, 10);
        if (comps == null) return;

        List<ConfiguracionComputador> listaPC = new ArrayList<>();
        StringBuilder resumenConfig = new StringBuilder();

        for (int i = 0; i < listaTxtRamComps.size(); i++) {
            Integer ramPC = validarEnteroPositivo(listaTxtRamComps.get(i).getText(), "RAM PC " + (i + 1));
            if (ramPC == null) return;

            String politicaPC = (String) listaCbPoliticaComps.get(i).getSelectedItem();
            ConfiguracionComputador pcConfig = new ConfiguracionComputador(i + 1, ramPC, politicaPC);
            listaPC.add(pcConfig);

            resumenConfig.append("PC ").append(i + 1).append(" [RAM: ").append(ramPC).append("MB, Política: ").append(politicaPC).append("] | ");
        }

        try {
            GestorArchivo.guardarConfiguracionCompleta("configuracion.csv", ciclo, quantum, latencia, listaPC);
            logEventos.registrar(0L, "Configuración global guardada en configuracion.csv con " + listaPC.size() + " computadores.");

            JOptionPane.showMessageDialog(this, 
                    "Configuración guardada exitosamente en 'configuracion.csv'\n\n" +
                    "Resumen de Computadores:\n" + resumenConfig.toString().replace(" | ", "\n"), 
                    "Éxito", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar configuración: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Lógica 2: Instanciar Buffer
    private void crearBufferAccion() {
        String idStr = txtBufferId.getText().trim();
        if (!esCampoTextoValido(idStr, "ID del Buffer")) return;

        Integer cap = validarEnteroPositivo(txtBufferCapacidad.getText(), "Capacidad Acotada");
        if (cap == null) return;

        Integer comp = validarEnteroPositivo(txtBufferComputador.getText(), "Computador Anfitrión");
        if (comp == null) return;

        try {
            Buffer buffer = new Buffer(idStr, cap, comp);
            logEventos.registrar(0L, "Buffer " + idStr + " (Capacidad: " + cap + ") creado en PC " + comp);

            JOptionPane.showMessageDialog(this, 
                    "Buffer " + idStr + " instanciado exitosamente.", 
                    "Buffer Creado", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al crear buffer: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Lógica 3: Instanciar Proceso, Asignar Datos Dinámicos y Encolar en ColaProcesos
    private void crearYEncolarProcesoAccion() {
        String nombre = txtNombreProceso.getText().trim();
        if (!esCampoTextoValido(nombre, "Nombre del Proceso")) return;

        Integer instrucciones = validarEnteroPositivo(txtInstruccionesProceso.getText(), "Cantidad de Instrucciones");
        if (instrucciones == null) return;

        Integer ram = validarEnteroPositivo(txtRamProceso.getText(), "Memoria RAM");
        if (ram == null) return;

        Integer prioridad = validarRangoEntero(txtPrioridadProceso.getText(), "Prioridad", 1, 5);
        if (prioridad == null) return;

        Integer deadline = validarEnteroPositivo(txtDeadlineProceso.getText(), "Deadline");
        if (deadline == null) return;

        Integer computador = validarEnteroPositivo(txtCompProceso.getText(), "Computador Asignado");
        if (computador == null) return;

        String tipoTexto = cbTipoProceso.getSelectedItem().toString();
        TipoProceso tipo = convertirTipo(tipoTexto);

        try {
            Proceso nuevoProceso = new Proceso(nombre, instrucciones, ram, prioridad, deadline, tipo, computador);

            // Validaciones y asignación de atributos dinámicos (Issue 1)
            switch (tipoTexto) {
                case "CPU Bound":
                    Integer cCpu = validarEnteroPositivo(txtCiclosCpu.getText(), "Ciclos CPU");
                    if (cCpu == null) return;
                    nuevoProceso.setCiclosCpu(cCpu);
                    break;
                case "IO Bound":
                    Integer cIo = validarEnteroPositivo(txtCiclosIo.getText(), "Ciclos I/O");
                    if (cIo == null) return;
                    nuevoProceso.setCiclosIo(cIo);
                    break;
                case "Productor":
                case "Consumidor":
                    String idBuf = txtBufferProceso.getText().trim();
                    if (!esCampoTextoValido(idBuf, "ID del Buffer")) return;
                    Integer cPC = validarEnteroPositivo(txtCiclosProdCons.getText(), "Ciclos Prod/Cons");
                    if (cPC == null) return;
                    Integer cantEl = validarEnteroPositivo(txtCantElementos.getText(), "Cantidad Elementos");
                    if (cantEl == null) return;

                    nuevoProceso.setIdBuffer(idBuf);
                    nuevoProceso.setCiclosProdCons(cPC);
                    nuevoProceso.setCantidadElementos(cantEl);
                    break;
            }

            colaProcesos.encolar(nuevoProceso);

            logEventos.registrar(0L, "Proceso encolado: " + nuevoProceso + " | Procesos en cola: " + colaProcesos.getTamano());

            JOptionPane.showMessageDialog(this,
                    "¡Proceso encolado con éxito!\n\n" +
                            "ID: " + nuevoProceso.getId() + "\n" +
                            "Nombre: " + nuevoProceso.getNombre() + "\n" +
                            "Instrucciones: " + nuevoProceso.getInstruccionesTotales() + "\n" +
                            "RAM: " + nuevoProceso.getMemoriaRAM() + " MB\n" +
                            "Tipo: " + nuevoProceso.getTipo() + "\n" +
                            "Total de procesos en cola: " + colaProcesos.getTamano(),
                    "Proceso Registrado", JOptionPane.INFORMATION_MESSAGE);

            txtNombreProceso.setText("Proceso_" + (nuevoProceso.getId() + 1));

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al instanciar proceso: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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