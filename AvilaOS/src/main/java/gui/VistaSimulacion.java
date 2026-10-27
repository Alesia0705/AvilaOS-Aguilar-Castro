package gui;

import sistema.SistemaDistribuido;
import computador.Computador;
import modelo.Proceso;
import modelo.enums.EstadoProceso;
import modelo.enums.TipoProceso;
import planificacion.*;
import sincronizacion.Buffer;
import util.LogEventos;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Vista 2: Ejecución, monitoreo y controles en caliente conectados al SistemaDistribuido real.
 */
public class VistaSimulacion extends JFrame {

    private final SistemaDistribuido sistemaDistribuido;
    private final LogEventos logEventos;
    private final Timer timerSimulacion;
    private final int duracionCiclo;
    private long cicloActual = 0;

    // Tablas de las 4 Colas de Procesos
    private DefaultTableModel modNuevos, modListos, modBloqueados, modTerminados;
    private JTable tabNuevos, tabListos, tabBloqueados, tabTerminados;

    // Tabla de Estado de CPUs / Computadores
    private DefaultTableModel modComputadores;
    private JTable tabComputadores;

    // Tabla de Buffers
    private DefaultTableModel modBuffers;
    private JTable tabBuffers;

    // Monitoreo y Controles
    private JTextArea txtLog;
    private JProgressBar progressMemoria;
    private JTextField txtNuevoQuantum;
    private JComboBox<String> comboComputadores;
    private JComboBox<String> comboNuevaPolitica;

    public VistaSimulacion(int duracionCiclo, SistemaDistribuido sistemaDistribuido, LogEventos logEventos) {
        this.duracionCiclo = duracionCiclo;
        this.sistemaDistribuido = sistemaDistribuido;
        this.logEventos = logEventos;

        setTitle("AvilaOS - Monitor de Simulación en Tiempo Real (Vista 2)");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        initComponents();

        // 1. Cargar datos inmediatamente en pantalla al abrir
        actualizarTablasEInterfaz();

        // 2. Iniciar el Timer para simular el avance de los ciclos
        timerSimulacion = new Timer(duracionCiclo, e -> ejecutarCicloSimulacion());
        timerSimulacion.start();
    }

    private void initComponents() {
        // --- PANEL SUPERIOR: Controles en Caliente ---
        JPanel panelCaliente = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panelCaliente.setBorder(BorderFactory.createTitledBorder("Controles en Caliente"));

        panelCaliente.add(new JLabel("Nuevo Quantum RR:"));
        txtNuevoQuantum = new JTextField("5", 4);
        panelCaliente.add(txtNuevoQuantum);

        JButton btnAplicarQuantum = new JButton("Cambiar Quantum");
        btnAplicarQuantum.addActionListener(e -> cambiarQuantumEnCaliente());
        panelCaliente.add(btnAplicarQuantum);

        panelCaliente.add(new JSeparator(JSeparator.VERTICAL));

        panelCaliente.add(new JLabel("PC:"));
        comboComputadores = new JComboBox<>();
        actualizarComboComputadores();
        panelCaliente.add(comboComputadores);

        panelCaliente.add(new JLabel("Política:"));
        comboNuevaPolitica = new JComboBox<>(new String[]{"Round Robin", "FCFS", "Prioridades", "EDF"});
        panelCaliente.add(comboNuevaPolitica);

        JButton btnAplicarPolitica = new JButton("Cambiar Política");
        btnAplicarPolitica.addActionListener(e -> cambiarPoliticaEnCaliente());
        panelCaliente.add(btnAplicarPolitica);

        JToggleButton btnPausa = new JToggleButton("Pausar");
        btnPausa.addActionListener(e -> {
            if (btnPausa.isSelected()) {
                timerSimulacion.stop();
                btnPausa.setText("Reanudar");
            } else {
                timerSimulacion.start();
                btnPausa.setText("Pausar");
            }
        });
        panelCaliente.add(btnPausa);

        add(panelCaliente, BorderLayout.NORTH);

        // --- PANEL CENTRAL: Pestañas ---
        JTabbedPane tabbedPane = new JTabbedPane();

        // 1. CPUs y Memoria
        JPanel panelCPUs = new JPanel(new BorderLayout(5, 5));
        modComputadores = new DefaultTableModel(
                new Object[]{"PC ID", "Estado CPU / Dispositivo", "Memoria Principal"}, 0);
        tabComputadores = new JTable(modComputadores);
        panelCPUs.add(new JScrollPane(tabComputadores), BorderLayout.CENTER);

        progressMemoria = new JProgressBar(0, 100);
        progressMemoria.setStringPainted(true);
        JPanel panelMem = new JPanel(new BorderLayout());
        panelMem.setBorder(BorderFactory.createTitledBorder("Uso Global de Memoria RAM"));
        panelMem.add(progressMemoria, BorderLayout.CENTER);
        panelCPUs.add(panelMem, BorderLayout.SOUTH);

        tabbedPane.addTab("CPUs & Memoria", panelCPUs);

        // 2. Colas de Procesos
        JPanel panelColas = new JPanel(new GridLayout(2, 2, 5, 5));

        modNuevos = new DefaultTableModel(new Object[]{"ID", "Nombre", "PC Asignado"}, 0);
        tabNuevos = new JTable(modNuevos);
        panelColas.add(crearPanelTabla("Cola: NUEVOS", tabNuevos));

        modListos = new DefaultTableModel(new Object[]{"ID", "Nombre", "Progreso", "PC Asignado"}, 0);
        tabListos = new JTable(modListos);
        panelColas.add(crearPanelTabla("Cola: LISTOS", tabListos));

        modBloqueados = new DefaultTableModel(new Object[]{"ID", "Nombre", "Motivo Bloqueo"}, 0);
        tabBloqueados = new JTable(modBloqueados);
        panelColas.add(crearPanelTabla("Cola: BLOQUEADOS", tabBloqueados));

        modTerminados = new DefaultTableModel(new Object[]{"ID", "Nombre", "Instrucciones Totales"}, 0);
        tabTerminados = new JTable(modTerminados);
        panelColas.add(crearPanelTabla("Cola: TERMINADOS", tabTerminados));

        tabbedPane.addTab("Las 4 Colas de Procesos", panelColas);

        // 3. Buffers
        modBuffers = new DefaultTableModel(new Object[]{"Buffer ID", "Elementos / Capacidad", "PC Anfitrión"}, 0);
        tabBuffers = new JTable(modBuffers);
        tabbedPane.addTab("Buffers & Semáforos", new JScrollPane(tabBuffers));

        // 4. Log
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tabbedPane.addTab("Log de Eventos", new JScrollPane(txtLog));

        add(tabbedPane, BorderLayout.CENTER);

        // --- PANEL INFERIOR ---
        JPanel panelInferior = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCrearProcesoAlVuelo = new JButton("+ Proceso al Vuelo");
        btnCrearProcesoAlVuelo.addActionListener(e -> crearProcesoAlVuelo());
        panelInferior.add(btnCrearProcesoAlVuelo);

        add(panelInferior, BorderLayout.SOUTH);
    }

    private JPanel crearPanelTabla(String titulo, JTable tabla) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(titulo));
        p.add(new JScrollPane(tabla), BorderLayout.CENTER);
        return p;
    }

    private void actualizarComboComputadores() {
        if (sistemaDistribuido == null) return;
        comboComputadores.removeAllItems();
        int count = sistemaDistribuido.getCantidadComputadores();
        for (int i = 0; i < count; i++) {
            comboComputadores.addItem("PC " + (i + 1));
        }
    }

    private void ejecutarCicloSimulacion() {
        try {
            cicloActual++;
            if (sistemaDistribuido != null) {
                int cantPC = sistemaDistribuido.getCantidadComputadores();
                boolean baseUno = (sistemaDistribuido.getComputador(0) == null);
                for (int i = 0; i < cantPC; i++) {
                    int idBusqueda = baseUno ? (i + 1) : i;
                    Computador pc = sistemaDistribuido.getComputador(idBusqueda);
                    if (pc != null) {
                        pc.onTick(cicloActual);
                    }
                }
            }
            actualizarTablasEInterfaz();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void actualizarTablasEInterfaz() {
        try {
            if (sistemaDistribuido == null) return;

            // Limpiar modelos
            modComputadores.setRowCount(0);
            modNuevos.setRowCount(0);
            modListos.setRowCount(0);
            modBloqueados.setRowCount(0);
            modTerminados.setRowCount(0);
            modBuffers.setRowCount(0);

            // 1. Cargar Computadores y CPUs formateados
            int cantPC = sistemaDistribuido.getCantidadComputadores();
            boolean baseUno = (sistemaDistribuido.getComputador(0) == null);

            for (int i = 0; i < cantPC; i++) {
                int idBusqueda = baseUno ? (i + 1) : i;
                Computador pc = sistemaDistribuido.getComputador(idBusqueda);

                if (pc != null) {
                    String cpuInfo = "CPU Activa";
                    if (pc.getCpu() != null) {
                        String str = pc.getCpu().toString();
                        cpuInfo = str.contains("@") ? "Ejecutando Instrucciones" : str;
                    }

                    String memInfo = "RAM OK";
                    if (pc.getMemoria() != null) {
                        String str = pc.getMemoria().toString();
                        memInfo = str.contains("@") ? "Memoria Principal Asignada" : str;
                    }

                    modComputadores.addRow(new Object[]{
                        "PC " + pc.getId(),
                        cpuInfo,
                        memInfo
                    });
                }
            }

            // 2. Cargar Procesos en las Colas
            Proceso[] todos = sistemaDistribuido.getTodosLosProcesos();
            if (todos != null) {
                for (Proceso p : todos) {
                    if (p == null) continue;
                    EstadoProceso estado = p.getEstado();
                    String pcAsig = "PC " + p.getComputadorAsignado();
                    String idMostrar = (p.getNombre() != null) ? p.getNombre() : "P" + p.getId();

                    if (estado == EstadoProceso.NUEVO) {
                        modNuevos.addRow(new Object[]{idMostrar, p.getNombre(), pcAsig});
                    } else if (estado == EstadoProceso.LISTO) {
                        modListos.addRow(new Object[]{idMostrar, p.getNombre(), p.getPc() + "/" + p.getInstruccionesTotales(), pcAsig});
                    } else if (estado == EstadoProceso.BLOQUEADO) {
                        String motivo = (p.getMotivoBloqueo() != null) ? p.getMotivoBloqueo().toString() : "Bloqueado";
                        modBloqueados.addRow(new Object[]{idMostrar, p.getNombre(), motivo});
                    } else if (estado == EstadoProceso.TERMINADO) {
                        modTerminados.addRow(new Object[]{idMostrar, p.getNombre(), p.getInstruccionesTotales()});
                    }
                }
            }

            // 3. Cargar Buffers
            int cantB = sistemaDistribuido.getCantidadBuffers();
            for (int i = 0; i < cantB; i++) {
                Buffer b = sistemaDistribuido.getBufferEn(i);
                if (b != null) {
                    modBuffers.addRow(new Object[]{
                        b.getId(),
                        b.getCantidadElementos() + " / " + b.getCapacidad(),
                        "PC " + b.getIdComputadorAnfitrion()
                    });
                }
            }

            // 4. Cargar Log de Eventos
            if (logEventos != null) {
                String[] eventos = logEventos.getEventos();
                if (eventos != null) {
                    StringBuilder sb = new StringBuilder();
                    for (String ev : eventos) {
                        if (ev != null) sb.append(ev).append("\n");
                    }
                    txtLog.setText(sb.toString());
                }
            }
        } catch (Exception ex) {
            System.err.println("Error al actualizar la interfaz: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void cambiarQuantumEnCaliente() {
        try {
            int nuevoQuantum = Integer.parseInt(txtNuevoQuantum.getText());
            if (logEventos != null) {
                logEventos.registrar(cicloActual, "Cambio en caliente: Quantum actualizado a " + nuevoQuantum);
            }
            actualizarTablasEInterfaz();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Ingrese un valor numérico para el quantum.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cambiarPoliticaEnCaliente() {
        String nuevaPoliticaStr = (String) comboNuevaPolitica.getSelectedItem();
        int indexPC = comboComputadores.getSelectedIndex() + 1;
        if (logEventos != null) {
            logEventos.registrar(cicloActual, "Cambio en caliente: Política de PC " + indexPC + " cambiada a " + nuevaPoliticaStr);
        }
        actualizarTablasEInterfaz();
    }

    private void crearProcesoAlVuelo() {
        int targetPC = comboComputadores.getSelectedIndex() + 1;

        String nombre = "P_Vuelo_" + (System.currentTimeMillis() % 1000);
        Proceso nuevo = new Proceso(nombre, 15, 64, 1, 100, TipoProceso.CPU_BOUND, targetPC);

        if (sistemaDistribuido != null) {
            sistemaDistribuido.enviarProceso(nuevo);
        }
        if (logEventos != null) {
            logEventos.registrar(cicloActual, "Proceso " + nombre + " creado al vuelo y enviado a PC " + targetPC);
        }
        actualizarTablasEInterfaz();
    }
}