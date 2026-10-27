package gui;

import computador.Computador;
import hardware.Cpu;
import kernel.Kernel;
import modelo.Proceso;
import modelo.enums.MotivoBloqueo;
import planificacion.PoliticaEDF;
import planificacion.PoliticaFCFS;
import planificacion.PoliticaPlanificacion;
import planificacion.PoliticaRoundRobin;
import reloj.RelojGlobal;
import sincronizacion.Buffer;
import sincronizacion.Semaforo;
import sistema.SistemaDistribuido;
import util.LogEventos;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Vista 2: simulación en tiempo real de ÁvilaOS.
 *
 * Esta vista solo OBSERVA y da órdenes: todo lo que muestra lo lee del
 * SistemaDistribuido (colas de cada kernel, CPU, memoria, buffers y
 * semáforos) y los controles actúan sobre el RelojGlobal y los kernels. No
 * calcula ni inventa ningún dato.
 */
public class VistaSimulacion extends JFrame {

    private static final String[] NOMBRES_POLITICAS = {"Round Robin", "FCFS", "EDF", "Prioridades"};
    private static final String[] COLUMNAS_PROCESOS = {"Proceso", "Tipo", "Estado", "PC", "MAR", "Restan", "Deadline"};
    private static final String[] COLUMNAS_BUFFERS = {"Buffer", "Anfitrión", "Capacidad", "Elementos",
        "mutex", "llenos", "vacíos", "Procesos en espera"};
    private static final int PUNTOS_GRAFICA = 150;

    private final RelojGlobal reloj;
    private final SistemaDistribuido sistema;
    private LogEventos logEventos;

    // Controles superiores
    private final JLabel lblCiclo = new JLabel("Ciclo global: 0");
    private final JLabel lblEstadoReloj = new JLabel("Detenido");
    private final JButton btnPausa = new JButton("Iniciar / Pausar / Reanudar");
    private final JButton btnPaso = new JButton("Paso a paso");
    private final JSlider sliderDuracion = new JSlider(50, 2000, 500);
    private final JLabel lblDuracion = new JLabel("500 ms");

    // Un panel por computador
    private PanelComputador[] paneles;

    // Buffers
    private final DefaultTableModel modeloBuffers = new DefaultTableModel(COLUMNAS_BUFFERS, 0);

    // Log
    private final JTextArea txtLogs = new JTextArea();

    // Gráfica: una serie por computador
    private XYSeries[] series;
    private long[] ocupadosPrevios;
    private long[] totalesPrevios;

    private final Timer timerRefresco;

    public VistaSimulacion(RelojGlobal reloj, SistemaDistribuido sistema) {
        super("ÁvilaOS - Simulación en tiempo real");
        if (reloj == null || sistema == null) {
            throw new IllegalArgumentException("La vista necesita el reloj y el sistema");
        }
        this.reloj = reloj;
        this.sistema = sistema;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        java.awt.Dimension pantalla = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1250, pantalla.width - 40), Math.min(760, pantalla.height - 80));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        construirControles();
        construirCentro();
        construirGrafica();

        // La pantalla se refresca sola cada 250 ms, sin importar la velocidad del reloj
        timerRefresco = new Timer(250, e -> actualizar());
        timerRefresco.start();
    }

    // ------------------------------------------------------------------
    // Construcción de la interfaz
    // ------------------------------------------------------------------
    private void construirControles() {
        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        lblCiclo.setFont(lblCiclo.getFont().deriveFont(Font.BOLD, 14f));

        int duracionInicial = Math.max(50, Math.min(2000, reloj.getDuracionCicloMs()));
        sliderDuracion.setValue(duracionInicial);
        lblDuracion.setText(duracionInicial + " ms");
        sliderDuracion.setToolTipText("Duración de un ciclo de reloj en milisegundos (menos = más rápido)");

        btnPausa.addActionListener(e -> alternarPausa());
        btnPaso.addActionListener(e -> pasoAPaso());
        sliderDuracion.addChangeListener(e -> {
            int ms = sliderDuracion.getValue();
            reloj.setDuracionCicloMs(ms);
            lblDuracion.setText(ms + " ms");
        });

        fila1.add(lblCiclo);
        fila1.add(lblEstadoReloj);
        fila1.add(btnPausa);
        fila1.add(btnPaso);
        fila1.add(new JLabel("Duración del ciclo:"));
        fila1.add(sliderDuracion);
        fila1.add(lblDuracion);

        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        JButton btnProceso = new JButton("+ Proceso al vuelo");
        JButton btnBuffer = new JButton("+ Buffer al vuelo");
        btnProceso.addActionListener(e -> procesoAlVuelo());
        btnBuffer.addActionListener(e -> bufferAlVuelo());
        fila2.add(btnProceso);
        fila2.add(btnBuffer);

        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.add(fila1);
        panel.add(fila2);
        add(panel, BorderLayout.NORTH);
    }

    private void construirCentro() {
        JTabbedPane pestanas = new JTabbedPane();

        int cantidad = sistema.getCantidadComputadores();
        paneles = new PanelComputador[cantidad];
        for (int i = 0; i < cantidad; i++) {
            Computador c = sistema.getComputador(i + 1);
            paneles[i] = new PanelComputador(c);
            pestanas.addTab("Computador " + c.getId(), paneles[i]);
        }

        JTable tablaBuffers = new JTable(modeloBuffers);
        pestanas.addTab("Buffers y semáforos", new JScrollPane(tablaBuffers));

        txtLogs.setEditable(false);
        txtLogs.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtLogs.setLineWrap(true);
        txtLogs.setWrapStyleWord(true);
        JScrollPane scrollLogs = new JScrollPane(txtLogs);
        scrollLogs.setBorder(BorderFactory.createTitledBorder("Log de eventos"));

        scrollLogs.setPreferredSize(new Dimension(360, 100));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pestanas, scrollLogs);
        split.setResizeWeight(1.0); // el espacio sobrante es para las pestañas; el log conserva su ancho
        scrollLogs.setMinimumSize(new Dimension(280, 100));
        pestanas.setMinimumSize(new Dimension(500, 100));
        add(split, BorderLayout.CENTER);
        // la ventana ya tiene tamaño al mostrarse: colocamos el divisor dejando 360 px al log
        addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean colocado = false;

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                if (!colocado) {
                    colocado = true;
                    SwingUtilities.invokeLater(() -> split.setDividerLocation(Math.max(500, split.getWidth() - 360)));
                }
            }
        });
    }

    private void construirGrafica() {
        int cantidad = paneles.length;
        series = new XYSeries[cantidad];
        ocupadosPrevios = new long[cantidad];
        totalesPrevios = new long[cantidad];

        XYSeriesCollection datos = new XYSeriesCollection();
        for (int i = 0; i < cantidad; i++) {
            series[i] = new XYSeries("Computador " + paneles[i].getIdComputador());
            series[i].setMaximumItemCount(PUNTOS_GRAFICA);
            datos.addSeries(series[i]);
        }

        JFreeChart grafica = ChartFactory.createXYLineChart(
                "Utilización de CPU por computador",
                "Ciclo de reloj",
                "Uso de CPU (%)",
                datos,
                PlotOrientation.VERTICAL,
                true, true, false);
        grafica.getXYPlot().getRangeAxis().setRange(0.0, 100.0);

        ChartPanel panelGrafica = new ChartPanel(grafica);
        panelGrafica.setPreferredSize(new Dimension(1100, 200));
        add(panelGrafica, BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------
    // Log
    // ------------------------------------------------------------------
    /**
     * Conecta el log de eventos: cada línea nueva aparece en el panel de log.
     */
    public void setLogEventos(LogEventos log) {
        this.logEventos = log;
        if (log != null) {
            try {
                // primero lo que ya se había registrado, luego los eventos nuevos
                String[] previos = log.getEventos();
                for (int i = 0; i < previos.length; i++) {
                    txtLogs.append(previos[i] + "\n");
                }
                log.setEscucha(linea -> SwingUtilities.invokeLater(() -> {
                    txtLogs.append(linea + "\n");
                    txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
                }));
            } catch (Exception ignored) {
                // si el log no acepta escucha, la vista sigue funcionando sin él
            }
        }
    }

    private void registrar(String mensaje) {
        if (logEventos != null) {
            logEventos.registrar(reloj.getCiclo(), mensaje);
        }
    }

    // ------------------------------------------------------------------
    // Controles del reloj
    // ------------------------------------------------------------------
    private void alternarPausa() {
        if (!reloj.estaEncendido()) {
            reloj.arrancar();
            registrar("Simulación iniciada.");
        } else if (reloj.estaPausado()) {
            reloj.reanudar();
            registrar("Simulación reanudada.");
        } else {
            reloj.pausar();
            registrar("Simulación pausada.");
        }
    }

    private void pasoAPaso() {
        if (!reloj.estaEncendido()) {
            reloj.pausar();
            reloj.arrancar();
        } else if (!reloj.estaPausado()) {
            reloj.pausar();
        }
        reloj.avanzarUnCiclo();
        registrar("Paso a paso: un ciclo.");
    }

    // ------------------------------------------------------------------
    // Crear procesos y buffers con la simulación en marcha
    // ------------------------------------------------------------------
    private String[] idsBuffersActuales() {
        int cantidad = sistema.getCantidadBuffers();
        String[] ids = new String[cantidad];
        for (int i = 0; i < cantidad; i++) {
            ids[i] = sistema.getBufferEn(i).getId();
        }
        return ids;
    }

    private String[] numerosComputadores() {
        int cantidad = sistema.getCantidadComputadores();
        String[] numeros = new String[cantidad];
        for (int i = 0; i < cantidad; i++) {
            numeros[i] = String.valueOf(i + 1);
        }
        return numeros;
    }

    private void procesoAlVuelo() {
        int total = sistema.getTodosLosProcesos().length + 1;
        String[] idsBuffers = idsBuffersActuales();

        JTextField txtNombre = new JTextField("PV" + total);
        JComboBox<String> comboTipo = new JComboBox<>(FabricaSistema.TIPOS);
        JTextField txtInstrucciones = new JTextField("20");
        JTextField txtRam = new JTextField("10");
        JTextField txtPrioridad = new JTextField("1");
        JTextField txtDeadline = new JTextField("1000");
        JComboBox<String> comboComputador = new JComboBox<>(numerosComputadores());
        JTextField txtCiclosCpu = new JTextField("3");
        JTextField txtCiclosIo = new JTextField("4");
        JComboBox<String> comboBuffer = new JComboBox<>(idsBuffers);
        JTextField txtCiclosPC = new JTextField("2");
        JTextField txtElementos = new JTextField("5");

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Nombre:"));
        form.add(txtNombre);
        form.add(new JLabel("Tipo:"));
        form.add(comboTipo);
        form.add(new JLabel("Instrucciones:"));
        form.add(txtInstrucciones);
        form.add(new JLabel("RAM:"));
        form.add(txtRam);
        form.add(new JLabel("Prioridad:"));
        form.add(txtPrioridad);
        form.add(new JLabel("Deadline (ciclos):"));
        form.add(txtDeadline);
        form.add(new JLabel("Computador:"));
        form.add(comboComputador);
        form.add(new JLabel("IO Bound: ciclos de CPU:"));
        form.add(txtCiclosCpu);
        form.add(new JLabel("IO Bound: ciclos de E/S:"));
        form.add(txtCiclosIo);
        form.add(new JLabel("Prod/Cons: buffer:"));
        form.add(comboBuffer);
        form.add(new JLabel("Prod/Cons: ciclos por elemento:"));
        form.add(txtCiclosPC);
        form.add(new JLabel("Prod/Cons: elementos:"));
        form.add(txtElementos);

        int opcion = JOptionPane.showConfirmDialog(this, form, "Nuevo proceso al vuelo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opcion != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            int numPC = sistema.getCantidadComputadores();
            int[] rams = new int[numPC];
            for (int i = 0; i < numPC; i++) {
                rams[i] = sistema.getComputador(i + 1).getKernel().getMemoria().getCapacidadTotal();
            }
            String buffer = (comboBuffer.getSelectedItem() == null) ? "" : comboBuffer.getSelectedItem().toString();
            Proceso proceso = FabricaSistema.crearProceso(
                    txtNombre.getText(), (String) comboTipo.getSelectedItem(), txtInstrucciones.getText(),
                    txtRam.getText(), txtPrioridad.getText(), txtDeadline.getText(),
                    comboComputador.getSelectedItem(), txtCiclosCpu.getText(), txtCiclosIo.getText(),
                    buffer, txtCiclosPC.getText(), txtElementos.getText(), numPC, rams, idsBuffers);
            if (!sistema.enviarProceso(proceso)) {
                throw new IllegalArgumentException("No se pudo enviar el proceso al computador.");
            }
            registrar("Proceso " + proceso.getNombre() + " creado al vuelo y enviado al computador "
                    + proceso.getComputadorAsignado() + ".");
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Datos inválidos", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void bufferAlVuelo() {
        JTextField txtId = new JTextField("B" + (sistema.getCantidadBuffers() + 1));
        JTextField txtCapacidad = new JTextField("3");
        JComboBox<String> comboAnfitrion = new JComboBox<>(numerosComputadores());

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("ID del buffer:"));
        form.add(txtId);
        form.add(new JLabel("Capacidad:"));
        form.add(txtCapacidad);
        form.add(new JLabel("Computador anfitrión:"));
        form.add(comboAnfitrion);

        int opcion = JOptionPane.showConfirmDialog(this, form, "Nuevo buffer al vuelo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opcion != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            String id = FabricaSistema.texto("ID del buffer", txtId.getText(), 20);
            int capacidad = FabricaSistema.entero("Capacidad", txtCapacidad.getText(), 1, 1000);
            int anfitrion = FabricaSistema.entero("Computador anfitrión",
                    comboAnfitrion.getSelectedItem(), 1, sistema.getCantidadComputadores());
            if (sistema.crearBuffer(id, capacidad, anfitrion) == null) {
                throw new IllegalArgumentException("No se pudo crear el buffer \"" + id
                        + "\": el id ya existe o no hay RAM suficiente en el computador " + anfitrion + ".");
            }
            registrar("Buffer " + id + " creado al vuelo (capacidad " + capacidad
                    + ", computador " + anfitrion + ").");
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Datos inválidos", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------
    // Refresco: solo lee del sistema
    // ------------------------------------------------------------------
    private void actualizar() {
        long ciclo = reloj.getCiclo();
        lblCiclo.setText("Ciclo global: " + ciclo);
        if (!reloj.estaEncendido()) {
            lblEstadoReloj.setText("Detenido");
        } else if (reloj.estaPausado()) {
            lblEstadoReloj.setText("Pausado");
        } else {
            lblEstadoReloj.setText("Corriendo");
        }

        for (int i = 0; i < paneles.length; i++) {
            paneles[i].actualizar();
            actualizarSerie(i, ciclo);
        }
        actualizarBuffers();
    }

    /**
     * Utilización real de la CPU desde el último refresco: ciclos ocupados
     * (usuario + sistema) entre ciclos totales transcurridos.
     */
    private void actualizarSerie(int indice, long ciclo) {
        Cpu cpu = paneles[indice].getCpu();
        long usuario = cpu.getCiclosUsuario();
        long sistemaOp = cpu.getCiclosSistema();
        long ocioso = cpu.getCiclosOcioso();
        long ocupados = usuario + sistemaOp;
        long totales = ocupados + ocioso;

        long deltaTotales = totales - totalesPrevios[indice];
        if (deltaTotales > 0) {
            double uso = 100.0 * (ocupados - ocupadosPrevios[indice]) / deltaTotales;
            uso = Math.max(0.0, Math.min(100.0, uso));
            series[indice].add((double) ciclo, uso);
            ocupadosPrevios[indice] = ocupados;
            totalesPrevios[indice] = totales;
        }
    }

    private void actualizarBuffers() {
        modeloBuffers.setRowCount(0);
        for (int i = 0; i < sistema.getCantidadBuffers(); i++) {
            Buffer b = sistema.getBufferEn(i);
            if (b == null) {
                continue;
            }
            modeloBuffers.addRow(new Object[]{
                b.getId(),
                "Computador " + b.getIdComputadorAnfitrion(),
                b.getCapacidad(),
                b.getCantidadElementos(),
                b.getMutex().getValor(),
                b.getLlenos().getValor(),
                b.getVacios().getValor(),
                describirEspera(b)
            });
        }
    }

    private String describirEspera(Buffer b) {
        StringBuilder sb = new StringBuilder();
        agregarEspera(sb, "mutex", b.getMutex());
        agregarEspera(sb, "llenos", b.getLlenos());
        agregarEspera(sb, "vacíos", b.getVacios());
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private void agregarEspera(StringBuilder sb, String etiqueta, Semaforo semaforo) {
        Proceso[] esperando = semaforo.getColaBloqueados().obtenerListaProcesos();
        if (esperando.length == 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("  |  ");
        }
        sb.append(etiqueta).append(": ");
        for (int i = 0; i < esperando.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(esperando[i].getNombre());
        }
    }

    private static void llenarTabla(DefaultTableModel modelo, Proceso[] procesos) {
        modelo.setRowCount(0);
        for (int i = 0; i < procesos.length; i++) {
            Proceso p = procesos[i];
            String estado = String.valueOf(p.getEstado());
            if (p.getMotivoBloqueo() != MotivoBloqueo.NINGUNO) {
                estado = estado + " (" + p.getMotivoBloqueo() + ")";
            }
            modelo.addRow(new Object[]{
                p.getNombre(),
                p.getTipo(),
                estado,
                p.getPc(),
                p.getMar(),
                p.getTiempoRestante(),
                p.getDeadline()
            });
        }
    }

    private static int indiceDe(PoliticaPlanificacion politica) {
        if (politica instanceof PoliticaRoundRobin) {
            return 0;
        }
        if (politica instanceof PoliticaFCFS) {
            return 1;
        }
        if (politica instanceof PoliticaEDF) {
            return 2;
        }
        return 3;
    }

    // ------------------------------------------------------------------
    // Panel de un computador: estado, controles en caliente y 4 colas
    // ------------------------------------------------------------------
    private class PanelComputador extends JPanel {

        private final Computador computador;
        private final JLabel lblPolitica = new JLabel();
        private final JLabel lblCpu = new JLabel();
        private final JLabel lblMemoria = new JLabel();
        private final JComboBox<String> comboPolitica = new JComboBox<>(NOMBRES_POLITICAS);
        private final JTextField txtQuantum = new JTextField("4", 4);
        private final DefaultTableModel mNuevos = new DefaultTableModel(COLUMNAS_PROCESOS, 0);
        private final DefaultTableModel mListos = new DefaultTableModel(COLUMNAS_PROCESOS, 0);
        private final DefaultTableModel mBloqueados = new DefaultTableModel(COLUMNAS_PROCESOS, 0);
        private final DefaultTableModel mTerminados = new DefaultTableModel(COLUMNAS_PROCESOS, 0);

        PanelComputador(Computador computador) {
            super(new BorderLayout(5, 5));
            this.computador = computador;

            PoliticaPlanificacion actual = computador.getKernel().getPolitica();
            comboPolitica.setSelectedIndex(indiceDe(actual));
            if (actual instanceof PoliticaRoundRobin) {
                txtQuantum.setText(String.valueOf(((PoliticaRoundRobin) actual).getQuantum()));
            }

            JButton btnPolitica = new JButton("Cambiar política");
            JButton btnQuantum = new JButton("Cambiar quantum");
            btnPolitica.addActionListener(e -> cambiarPolitica());
            btnQuantum.addActionListener(e -> cambiarQuantum());

            JPanel estado = new JPanel(new GridLayout(3, 1));
            estado.add(lblPolitica);
            estado.add(lblCpu);
            estado.add(lblMemoria);

            JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            controles.add(new JLabel("Política:"));
            controles.add(comboPolitica);
            controles.add(btnPolitica);
            controles.add(new JLabel("Quantum:"));
            controles.add(txtQuantum);
            controles.add(btnQuantum);

            JPanel norte = new JPanel(new BorderLayout());
            norte.add(estado, BorderLayout.CENTER);
            norte.add(controles, BorderLayout.SOUTH);
            add(norte, BorderLayout.NORTH);

            JPanel colas = new JPanel(new GridLayout(2, 2, 5, 5));
            colas.add(tabla("Nuevos", mNuevos));
            colas.add(tabla("Listos", mListos));
            colas.add(tabla("Bloqueados", mBloqueados));
            colas.add(tabla("Terminados", mTerminados));
            add(colas, BorderLayout.CENTER);
        }

        private JScrollPane tabla(String titulo, DefaultTableModel modelo) {
            JScrollPane scroll = new JScrollPane(new JTable(modelo));
            scroll.setBorder(BorderFactory.createTitledBorder(titulo));
            return scroll;
        }

        int getIdComputador() {
            return computador.getId();
        }

        Cpu getCpu() {
            return computador.getCpu();
        }

        private void cambiarPolitica() {
            int indice = comboPolitica.getSelectedIndex();
            int quantum;
            try {
                quantum = leerQuantum();
            } catch (IllegalArgumentException ex) {
                if (!"Round Robin".equals(NOMBRES_POLITICAS[indice])) {
                    quantum = 4; // las demás políticas no usan quantum
                } else {
                    avisarQuantumInvalido(ex);
                    return;
                }
            }
            PoliticaPlanificacion nueva = FabricaSistema.crearPolitica(NOMBRES_POLITICAS[indice], quantum);
            if (computador.getKernel().cambiarPolitica(nueva)) {
                registrar("Computador " + computador.getId() + ": política cambiada a "
                        + NOMBRES_POLITICAS[indice] + ".");
            }
        }

        private int leerQuantum() {
            return FabricaSistema.entero("Quantum", txtQuantum.getText(), 1, 1000);
        }

        private void avisarQuantumInvalido(IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(VistaSimulacion.this,
                    "El quantum debe ser un entero mayor o igual a 1.\n" + ex.getMessage(),
                    "Quantum inválido", JOptionPane.ERROR_MESSAGE);
        }

        private void cambiarQuantum() {
            int quantum;
            try {
                quantum = leerQuantum();
            } catch (IllegalArgumentException ex) {
                avisarQuantumInvalido(ex);
                return;
            }
            PoliticaPlanificacion actual = computador.getKernel().getPolitica();
            if (actual instanceof PoliticaRoundRobin) {
                if (((PoliticaRoundRobin) actual).setQuantum(quantum)) {
                    registrar("Computador " + computador.getId() + ": quantum cambiado a " + quantum + ".");
                }
            } else {
                JOptionPane.showMessageDialog(VistaSimulacion.this,
                        "El computador " + computador.getId() + " no usa Round Robin.",
                        "Quantum", JOptionPane.INFORMATION_MESSAGE);
            }
        }

        void actualizar() {
            Kernel kernel = computador.getKernel();
            Cpu cpu = computador.getCpu();

            lblPolitica.setText("Política actual: " + kernel.getPolitica().getTipo());

            Proceso enCpu = cpu.getProcesoActual();
            String detalle = (enCpu == null)
                    ? "libre"
                    : enCpu.getNombre() + "  PC=" + cpu.getPc() + "  MAR=" + cpu.getMar();
            lblCpu.setText("CPU: modo " + cpu.getModo() + "  |  proceso en CPU: " + detalle);

            lblMemoria.setText("RAM: " + kernel.getMemoria().getUsada() + " / "
                    + kernel.getMemoria().getCapacidadTotal() + " ocupada");

            llenarTabla(mNuevos, kernel.obtenerNuevos());
            llenarTabla(mListos, kernel.obtenerListos());
            llenarTabla(mBloqueados, kernel.obtenerBloqueados());
            llenarTabla(mTerminados, kernel.obtenerTerminados());
        }
    }
}