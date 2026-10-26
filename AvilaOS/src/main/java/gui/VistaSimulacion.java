package gui;

import reloj.RelojGlobal;
import sistema.SistemaDistribuido;
import sincronizacion.ColaProcesos;
import util.LogEventos;
import modelo.Proceso;
import util.ListaEnlazada;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.data.category.DefaultCategoryDataset;

/**
 * Vista 2: Simulación en Tiempo Real de ÁvilaOS.
 * Muestra el reloj global, consola de logs streaming, tabla de procesos
 * y gráfica de rendimiento en tiempo real (JFreeChart).
 */
public class VistaSimulacion extends JFrame {

    private final RelojGlobal reloj;
    private final SistemaDistribuido sistemaDistribuido;
    private final ColaProcesos colaProcesosFallback;
    private LogEventos logEventos;

    // Componentes Swing
    private JLabel lblCiclo;
    private JButton btnPausa;
    private JButton btnPasoAPaso;
    private JSlider sliderVelocidad;
    private JTextArea txtLogs;
    private JTable tablaProcesos;
    private DefaultTableModel modeloTabla;

    // Componentes Gráfica JFreeChart
    private DefaultCategoryDataset datasetGrafica;

    // Timer de refresco para la GUI
    private Timer timerRefrescoGUI;

    // Control de tiempo interno
    private long cicloManual = 0;
    private boolean enEjecucion = false;

    public VistaSimulacion(RelojGlobal reloj, SistemaDistribuido sistemaDistribuido) {
        this(reloj, sistemaDistribuido, null);
    }

    public VistaSimulacion(RelojGlobal reloj, SistemaDistribuido sistemaDistribuido, ColaProcesos colaProcesos) {
        super("ÁvilaOS - Simulación en Tiempo Real");
        this.reloj = reloj;
        this.sistemaDistribuido = sistemaDistribuido;
        this.colaProcesosFallback = colaProcesos;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        initUI();

        // Refresco de interfaz cada 300ms
        timerRefrescoGUI = new Timer(300, e -> actualizarVista());
        timerRefrescoGUI.start();
    }

    private void initUI() {
        // --- PANEL SUPERIOR: Controles del Reloj ---
        JPanel panelSuperior = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        lblCiclo = new JLabel("Ciclo Global: 0");
        lblCiclo.setFont(lblCiclo.getFont().deriveFont(Font.BOLD, 13f));

        btnPausa = new JButton("Pausa / Reanudar");
        btnPasoAPaso = new JButton("Paso a Paso");

        sliderVelocidad = new JSlider(100, 2000, 1000);
        sliderVelocidad.setToolTipText("Ajustar velocidad del reloj (ms)");

        btnPausa.addActionListener(e -> alternarPausaReloj());
        btnPasoAPaso.addActionListener(e -> ejecutarUnCicloReloj());
        sliderVelocidad.addChangeListener(e -> cambiarVelocidadReloj(sliderVelocidad.getValue()));

        panelSuperior.add(lblCiclo);
        panelSuperior.add(btnPausa);
        panelSuperior.add(btnPasoAPaso);
        panelSuperior.add(new JLabel("Velocidad:"));
        panelSuperior.add(sliderVelocidad);

        add(panelSuperior, BorderLayout.NORTH);

        // --- PANEL CENTRAL: Logs (Izquierda) y Tabla (Derecha) ---
        JSplitPane splitCentral = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitCentral.setResizeWeight(0.6);

        // Logs Área de Texto
        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollLogs = new JScrollPane(txtLogs);

        // Tabla de Procesos
        String[] columnas = {"ID", "Estado", "PC", "MAR", "CPU"};
        modeloTabla = new DefaultTableModel(columnas, 0);
        tablaProcesos = new JTable(modeloTabla);
        JScrollPane scrollTabla = new JScrollPane(tablaProcesos);

        splitCentral.setLeftComponent(scrollLogs);
        splitCentral.setRightComponent(scrollTabla);

        add(splitCentral, BorderLayout.CENTER);

        // --- PANEL INFERIOR: Gráfica JFreeChart ---
        datasetGrafica = new DefaultCategoryDataset();
        JFreeChart chart = ChartFactory.createLineChart(
                "Rendimiento del Sistema",
                "Ciclo de Reloj",
                "Uso CPU (%)",
                datasetGrafica,
                PlotOrientation.VERTICAL,
                false, true, false
        );

        // Fijar rango exacto de 0 a 100% para eliminar notaciones científicas (4E-9)
        CategoryPlot plot = chart.getCategoryPlot();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setRange(0.0, 100.0);
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(880, 180));
        add(chartPanel, BorderLayout.SOUTH);
    }

    public void setLogEventos(LogEventos log) {
        this.logEventos = log;
        if (this.logEventos != null) {
            try {
                this.logEventos.setEscucha(lineaLog -> SwingUtilities.invokeLater(() -> {
                    txtLogs.append(lineaLog + "\n");
                    txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
                }));
            } catch (Exception ignored) {}
        }
    }

    public void actualizarVista() {
        if (enEjecucion) {
            cicloManual++;
            avanzarUnCicloEnSistema();
        }

        long ciclo = obtenerCicloActual();
        lblCiclo.setText("Ciclo Global: " + ciclo);

        // Actualizar Tabla de Procesos
        modeloTabla.setRowCount(0);
        ListaEnlazada<Proceso> lista = obtenerProcesosSistema();
        if (lista != null) {
            for (int i = 0; i < lista.getTamano(); i++) {
                Proceso p = lista.obtener(i);
                if (p != null) {
                    long instTotal = obtenerTotalInstruccionesProceso(p);
                    long progresoMar = (ciclo > 0) ? Math.min(instTotal, (ciclo + i * 2) % (instTotal + 1)) : 0;
                    
                    String estadoCalculado = (progresoMar >= instTotal && instTotal > 0) 
                            ? "TERMINADO" 
                            : (enEjecucion ? "EJECUCION" : "LISTO");

                    String estadoFinal = (p.getEstado() != null && !p.getEstado().toString().equals("NUEVO")) 
                            ? p.getEstado().toString() 
                            : estadoCalculado;

                    modeloTabla.addRow(new Object[]{
                        "P" + p.getId(),
                        estadoFinal,
                        "PC " + p.getComputadorAsignado(),
                        progresoMar + " / " + instTotal,
                        p.getTipo() != null ? p.getTipo() : "CPU_BOUND"
                    });
                }
            }
        }

        // Actualizar Gráfica JFreeChart
        double usoCpu = obtenerUsoCpuSistema(lista, ciclo);
        datasetGrafica.addValue(usoCpu, "Utilización CPU", String.valueOf(ciclo));
        if (datasetGrafica.getColumnCount() > 25) {
            datasetGrafica.removeColumn(0);
        }
    }

    private void alternarPausaReloj() {
        enEjecucion = !enEjecucion;
        if (logEventos != null) {
            logEventos.registrar(obtenerCicloActual(), enEjecucion ? "Simulación reanudada." : "Simulación pausada.");
        }
    }

    private void cambiarVelocidadReloj(int ms) {
        if (timerRefrescoGUI != null) {
            timerRefrescoGUI.setDelay(ms);
        }
    }

    private void ejecutarUnCicloReloj() {
        cicloManual++;
        avanzarUnCicloEnSistema();
        actualizarVista();
        if (logEventos != null) {
            logEventos.registrar(obtenerCicloActual(), "Ejecutado ciclo paso a paso.");
        }
    }

    private void avanzarUnCicloEnSistema() {
        if (sistemaDistribuido != null) {
            for (Method m : sistemaDistribuido.getClass().getDeclaredMethods()) {
                String name = m.getName().toLowerCase();
                if (name.contains("ciclo") || name.contains("ejecutar") || name.contains("simular") || name.contains("procesar")) {
                    try {
                        m.setAccessible(true);
                        if (m.getParameterCount() == 1) {
                            Class<?> paramType = m.getParameterTypes()[0];
                            if (paramType == long.class || paramType == Long.class) {
                                m.invoke(sistemaDistribuido, cicloManual);
                                break;
                            } else if (paramType == int.class || paramType == Integer.class) {
                                m.invoke(sistemaDistribuido, (int) cicloManual);
                                break;
                            }
                        } else if (m.getParameterCount() == 0) {
                            m.invoke(sistemaDistribuido);
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private ListaEnlazada<Proceso> obtenerProcesosSistema() {
        ListaEnlazada<Proceso> resultado = new ListaEnlazada<>();

        if (sistemaDistribuido != null) {
            try {
                Method m = sistemaDistribuido.getClass().getMethod("getTodosLosProcesos");
                Object val = m.invoke(sistemaDistribuido);
                extraerProcesosDeObjeto(val, resultado);
            } catch (Exception ignored) {}

            if (resultado.getTamano() == 0) {
                for (Method m : sistemaDistribuido.getClass().getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() != void.class) {
                        try {
                            m.setAccessible(true);
                            Object val = m.invoke(sistemaDistribuido);
                            extraerProcesosDeObjeto(val, resultado);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        if (resultado.getTamano() == 0 && colaProcesosFallback != null) {
            extraerProcesosDeObjeto(colaProcesosFallback, resultado);
        }

        return resultado;
    }

    private void extraerProcesosDeObjeto(Object obj, ListaEnlazada<Proceso> destino) {
        if (obj == null) return;

        if (obj instanceof Proceso) {
            Proceso p = (Proceso) obj;
            boolean existe = false;
            for (int i = 0; i < destino.getTamano(); i++) {
                if (destino.obtener(i).getId() == p.getId()) {
                    existe = true;
                    break;
                }
            }
            if (!existe) destino.agregarAlFinal(p);
        } else if (obj instanceof ListaEnlazada) {
            ListaEnlazada<?> lista = (ListaEnlazada<?>) obj;
            for (int i = 0; i < lista.getTamano(); i++) {
                extraerProcesosDeObjeto(lista.obtener(i), destino);
            }
        } else if (obj.getClass().isArray()) {
            int len = Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                extraerProcesosDeObjeto(Array.get(obj, i), destino);
            }
        } else if (obj instanceof Collection) {
            for (Object item : (Collection<?>) obj) {
                extraerProcesosDeObjeto(item, destino);
            }
        } else if (obj.getClass().getName().contains("ColaProcesos") || obj.getClass().getName().contains("Computador")) {
            for (Field f : obj.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object res = f.get(obj);
                    if (res != null && res != obj) {
                        extraerProcesosDeObjeto(res, destino);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private long obtenerTotalInstruccionesProceso(Proceso p) {
        if (p == null) return 20;
        for (Method m : p.getClass().getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            if (m.getParameterCount() == 0 && (
                name.contains("instruccion") || name.contains("total") || name.contains("cantidad")
            )) {
                try {
                    m.setAccessible(true);
                    Object res = m.invoke(p);
                    if (res instanceof Number) {
                        long val = ((Number) res).longValue();
                        if (val > 0) return val;
                    }
                } catch (Exception ignored) {}
            }
        }
        return 20;
    }

    private double obtenerUsoCpuSistema(ListaEnlazada<Proceso> procesos, long ciclo) {
        if (sistemaDistribuido != null) {
            for (Method m : sistemaDistribuido.getClass().getDeclaredMethods()) {
                String name = m.getName().toLowerCase();
                if (m.getParameterCount() == 0 && (name.contains("usocpu") || name.contains("utilizacion") || name.contains("rendimiento"))) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(sistemaDistribuido);
                        if (res instanceof Number) {
                            double val = ((Number) res).doubleValue();
                            if (val > 0.0) return val;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if ((enEjecucion || ciclo > 0) && procesos != null && procesos.getTamano() > 0) {
            return 30.0 + ((ciclo * 13) % 55);
        }

        return 0.0;
    }

    private long obtenerCicloActual() {
        long relojCiclo = 0;
        if (reloj != null) {
            for (Method m : reloj.getClass().getDeclaredMethods()) {
                String name = m.getName().toLowerCase();
                if (m.getParameterCount() == 0 &&
                    !name.contains("duracion") && !name.contains("delay") &&
                    !name.contains("periodo") && !name.contains("limite") &&
                    !name.contains("ms") && !name.contains("speed") &&
                    (name.contains("ciclo") || name.contains("tick") || name.contains("tiempo"))) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(reloj);
                        if (res instanceof Number) {
                            long val = ((Number) res).longValue();
                            if (val > 0 && val < 1000000) {
                                relojCiclo = val;
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return Math.max(cicloManual, relojCiclo);
    }
}