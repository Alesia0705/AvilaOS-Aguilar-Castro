package gui;

import modelo.ConfiguracionComputador;
import modelo.Proceso;
import modelo.enums.TipoProceso;
import sincronizacion.ColaProcesos;
import util.ListaEnlazada;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Vista 2: Pantalla principal de Simulación y Monitoreo en tiempo real para ÁvilaOS.
 * Integra las 3 características principales:
 * 1. Planificación multialgoritmo (RR, FCFS, Prioridades, SRTF).
 * 2. Bloqueo y desbloqueo por ráfagas de E/S (IO Bound).
 * 3. Sincronización de Productor-Consumidor sobre Buffers Acotados.
 * 
 * Totalmente compatible con las llamadas estándar de ListaEnlazada (obtener, agregarAlFinal, vaciar, getTamano).
 */
public class VistaSimulacion extends JFrame {

    // Referencias al núcleo del sistema
    private final ColaProcesos colaListos;
    private final ColaProcesos colaBloqueados;
    private final ListaEnlazada<Proceso> listaTerminados;
    private final ListaEnlazada<ConfiguracionComputador> listaComputadores;

    // Estado del reloj y temporizador
    private boolean enEjecucion = false;
    private int velocidadMs = 1000;
    private int cicloActual = 0;
    private final Timer timerSimulacion;

    // Control dinámico de CPUs y Buffers
    private EstadoCPU[] arregloCPUs;
    private final ListaEnlazada<ControlBuffer> listaBuffers = new ListaEnlazada<>();
    private final ListaEnlazada<ProcesoBloqueadoInfo> listaBloqueadosInfo = new ListaEnlazada<>();

    // Componentes gráficos
    private JButton btnIniciar;
    private JButton btnPausar;
    private JButton btnPasoAPaso;
    private JSlider sliderVelocidad;
    private JLabel lblEstadoReloj;
    private JLabel lblCicloActual;

    private JPanel panelCPUs;
    private JTable tablaListos;
    private JTable tablaBloqueados;
    private JTable tablaTerminados;

    private DefaultTableModel modelListos;
    private DefaultTableModel modelBloqueados;
    private DefaultTableModel modelTerminados;
    private JTextArea txtConsolaLogs;

    // --- Estructuras Internas Auxiliares ---
    private static class EstadoCPU {
        ConfiguracionComputador config;
        Proceso procesoActual = null;
        int pc = 0;
        int mar = 0;
        int quantumUsado = 0;

        JLabel lblEstado;
        JLabel lblProceso;
        JLabel lblPCMAR;
        JLabel lblRAMInfo;
    }

    private static class ControlBuffer {
        String idBuffer;
        int capacidad;
        int ocupacionActual = 0;

        ControlBuffer(String idBuffer, int capacidad) {
            this.idBuffer = idBuffer;
            this.capacidad = capacidad;
        }
    }

    private static class ProcesoBloqueadoInfo {
        Proceso proceso;
        int ciclosRestantesBloqueado;
        String motivo; // "I/O Bound", "Buffer Lleno", "Buffer Vacío"

        ProcesoBloqueadoInfo(Proceso proceso, int ciclosRestantes, String motivo) {
            this.proceso = proceso;
            this.ciclosRestantesBloqueado = ciclosRestantes;
            this.motivo = motivo;
        }
    }

    // Constructor Principal
    public VistaSimulacion(ColaProcesos colaListos, 
                           ColaProcesos colaBloqueados, 
                           ListaEnlazada<Proceso> listaTerminados,
                           ListaEnlazada<ConfiguracionComputador> listaComputadores) {
        super("ÁvilaOS - Vista 2: Monitoreo y Simulación");
        this.colaListos = colaListos;
        this.colaBloqueados = colaBloqueados;
        this.listaTerminados = listaTerminados;
        this.listaComputadores = listaComputadores;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Inicializar buffers por defecto
        listaBuffers.agregarAlFinal(new ControlBuffer("BUF-1", 5));

        // Construir la interfaz
        add(crearPanelSuperiorControles(), BorderLayout.NORTH);
        add(crearPanelCentralMonitoreo(), BorderLayout.CENTER);
        add(crearPanelInferiorConsola(), BorderLayout.SOUTH);

        timerSimulacion = new Timer(velocidadMs, e -> ejecutarCicloSimulacion());

        generarTarjetasCPUs();
        actualizarTablasGUI();
    }

    public VistaSimulacion(ColaProcesos colaListos, ColaProcesos colaBloqueados, ListaEnlazada<Proceso> listaTerminados) {
        this(colaListos, colaBloqueados, listaTerminados, null);
    }

    // --- Panel Superior: Controles ---
    private JPanel crearPanelSuperiorControles() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Control del Reloj del Sistema"));

        btnIniciar = new JButton("▶ Iniciar");
        btnPausar = new JButton("⏸ Pausar");
        btnPasoAPaso = new JButton("⏭ Paso a Paso");
        btnPausar.setEnabled(false);

        lblEstadoReloj = new JLabel("Estado: Detenido");
        lblEstadoReloj.setFont(lblEstadoReloj.getFont().deriveFont(Font.BOLD));
        
        lblCicloActual = new JLabel("Ciclo: 0");
        lblCicloActual.setFont(lblCicloActual.getFont().deriveFont(Font.BOLD));

        JLabel lblVelocidad = new JLabel("Velocidad (ms/ciclo):");
        sliderVelocidad = new JSlider(100, 2000, 1000);
        sliderVelocidad.setMajorTickSpacing(500);
        sliderVelocidad.setPaintTicks(true);
        sliderVelocidad.setPaintLabels(true);
        sliderVelocidad.setPreferredSize(new Dimension(200, 45));

        btnIniciar.addActionListener(e -> iniciarSimulacion());
        btnPausar.addActionListener(e -> pausarSimulacion());
        btnPasoAPaso.addActionListener(e -> ejecutarCicloPasoAPaso());
        sliderVelocidad.addChangeListener(e -> {
            velocidadMs = sliderVelocidad.getValue();
            timerSimulacion.setDelay(velocidadMs);
        });

        panel.add(btnIniciar);
        panel.add(btnPausar);
        panel.add(btnPasoAPaso);
        panel.add(new JSeparator(JSeparator.VERTICAL));
        panel.add(lblEstadoReloj);
        panel.add(lblCicloActual);
        panel.add(new JSeparator(JSeparator.VERTICAL));
        panel.add(lblVelocidad);
        panel.add(sliderVelocidad);

        return panel;
    }

    // --- Panel Central: CPUs y Tablas ---
    private JPanel crearPanelCentralMonitoreo() {
        JPanel panelCentral = new JPanel(new GridLayout(1, 2, 10, 10));
        panelCentral.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        panelCPUs = new JPanel();
        panelCPUs.setLayout(new BoxLayout(panelCPUs, BoxLayout.Y_AXIS));
        JScrollPane scrollCPUs = new JScrollPane(panelCPUs);
        scrollCPUs.setBorder(BorderFactory.createTitledBorder("Estado de Computadores / CPUs"));

        JPanel panelColas = new JPanel(new GridLayout(3, 1, 5, 5));

        modelListos = crearModeloTabla();
        modelBloqueados = crearModeloTabla();
        modelTerminados = crearModeloTabla();

        tablaListos = new JTable(modelListos);
        tablaBloqueados = new JTable(modelBloqueados);
        tablaTerminados = new JTable(modelTerminados);

        panelColas.add(crearScrollTabla(tablaListos, "Cola de Listos"));
        panelColas.add(crearScrollTabla(tablaBloqueados, "Cola de Bloqueados"));
        panelColas.add(crearScrollTabla(tablaTerminados, "Procesos Terminados"));

        panelCentral.add(scrollCPUs);
        panelCentral.add(panelColas);

        return panelCentral;
    }

    public void generarTarjetasCPUs() {
        panelCPUs.removeAll();
        int cantidadComps = (listaComputadores != null && listaComputadores.getTamano() > 0) 
                            ? listaComputadores.getTamano() : 2;

        arregloCPUs = new EstadoCPU[cantidadComps];

        for (int i = 0; i < cantidadComps; i++) {
            EstadoCPU cpu = new EstadoCPU();
            if (listaComputadores != null && i < listaComputadores.getTamano()) {
                cpu.config = listaComputadores.obtener(i);
            } else {
                cpu.config = new ConfiguracionComputador(i + 1, 1024, "Round Robin");
            }

            arregloCPUs[i] = cpu;
            panelCPUs.add(crearTarjetaCPUWidget(cpu));
            panelCPUs.add(Box.createRigidArea(new Dimension(0, 8)));
        }

        panelCPUs.revalidate();
        panelCPUs.repaint();
    }

    private JPanel crearTarjetaCPUWidget(EstadoCPU cpu) {
        JPanel card = new JPanel(new GridLayout(4, 1, 2, 2));
        card.setBorder(BorderFactory.createTitledBorder("Computador " + cpu.config.getIdPC() + " (" + cpu.config.getPoliticaPlanificacion() + ")"));
        card.setMaximumSize(new Dimension(500, 110));

        cpu.lblEstado = new JLabel("Estado CPU: IDLE (Inactivo)");
        cpu.lblEstado.setForeground(Color.BLUE);
        
        cpu.lblProceso = new JLabel("Proceso Actual: Ninguno");
        cpu.lblPCMAR = new JLabel("PC: 0  |  MAR: 0");
        cpu.lblRAMInfo = new JLabel("Memoria RAM: " + cpu.config.getMemoriaRAM() + " MB");

        card.add(cpu.lblEstado);
        card.add(cpu.lblProceso);
        card.add(cpu.lblPCMAR);
        card.add(cpu.lblRAMInfo);

        return card;
    }

    private DefaultTableModel crearModeloTabla() {
        String[] columnas = {"ID", "Nombre", "Tipo", "PC", "Instrucciones", "RAM"};
        return new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private JScrollPane crearScrollTabla(JTable tabla, String titulo) {
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createTitledBorder(titulo));
        return scroll;
    }

    private JPanel crearPanelInferiorConsola() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Consola de Eventos del Sistema"));
        panel.setPreferredSize(new Dimension(1000, 150));

        txtConsolaLogs = new JTextArea();
        txtConsolaLogs.setEditable(false);
        txtConsolaLogs.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        JScrollPane scrollLogs = new JScrollPane(txtConsolaLogs);
        panel.add(scrollLogs, BorderLayout.CENTER);

        return panel;
    }

    // --- MOTOR PRINCIPAL DE EJECUCIÓN SÍNCRONA ---
    private synchronized void ejecutarCicloSimulacion() {
        cicloActual++;
        lblCicloActual.setText("Ciclo: " + cicloActual);

        // A. Actualizar estado de procesos bloqueados (I/O y Buffers)
        procesarColaBloqueados();

        // B. Asignar procesos a las CPUs según sus algoritmos de planificación
        planificarProcesosEnCPUs();

        // C. Ejecutar ciclo de instrucción en cada CPU
        for (EstadoCPU cpu : arregloCPUs) {
            if (cpu.procesoActual != null) {
                Proceso p = cpu.procesoActual;
                cpu.pc++;
                cpu.mar += 4;
                cpu.quantumUsado++;

                logEvent("PC " + cpu.config.getIdPC() + " [" + cpu.config.getPoliticaPlanificacion() + "] ejecutando '" 
                         + p.getNombre() + "' | Instr " + cpu.pc + "/" + p.getInstruccionesTotales());

                // 1. Verificación de E/S (IO Bound)
                if (p.getTipo() == TipoProceso.IO_BOUND && p.getCiclosIo() > 0 && cpu.pc % p.getCiclosIo() == 0 && cpu.pc < p.getInstruccionesTotales()) {
                    logEvent("⚠️ Proceso '" + p.getNombre() + "' genera ráfaga de E/S. Se desplaza a Bloqueados.");
                    bloquearProceso(p, 3, "I/O Bound");
                    cpu.procesoActual = null;
                    cpu.pc = 0;
                    cpu.quantumUsado = 0;
                    continue;
                }

                // 2. Verificación de Sincronización Productor / Consumidor
                if (p.getTipo() == TipoProceso.PRODUCTOR) {
                    ControlBuffer buf = obtenerOInsertarBuffer(p.getIdBuffer() != null ? p.getIdBuffer() : "BUF-1");
                    if (buf.ocupacionActual >= buf.capacidad) {
                        logEvent("⛔ Buffer " + buf.idBuffer + " LLENO (" + buf.ocupacionActual + "/" + buf.capacidad + "). Productor '" + p.getNombre() + "' bloqueado.");
                        bloquearProceso(p, 999, "Buffer Lleno");
                        cpu.procesoActual = null;
                        cpu.pc = 0;
                        cpu.quantumUsado = 0;
                        continue;
                    } else {
                        buf.ocupacionActual++;
                        logEvent("📦 Productor '" + p.getNombre() + "' produjo un elemento. Buffer " + buf.idBuffer + ": " + buf.ocupacionActual + "/" + buf.capacidad);
                        intentarDesbloquearConsumidores(buf.idBuffer);
                    }
                } else if (p.getTipo() == TipoProceso.CONSUMIDOR) {
                    ControlBuffer buf = obtenerOInsertarBuffer(p.getIdBuffer() != null ? p.getIdBuffer() : "BUF-1");
                    if (buf.ocupacionActual <= 0) {
                        logEvent("⛔ Buffer " + buf.idBuffer + " VACÍO. Consumidor '" + p.getNombre() + "' bloqueado.");
                        bloquearProceso(p, 999, "Buffer Vacío");
                        cpu.procesoActual = null;
                        cpu.pc = 0;
                        cpu.quantumUsado = 0;
                        continue;
                    } else {
                        buf.ocupacionActual--;
                        logEvent("🛒 Consumidor '" + p.getNombre() + "' consumió un elemento. Buffer " + buf.idBuffer + ": " + buf.ocupacionActual + "/" + buf.capacidad);
                        intentarDesbloquearProductores(buf.idBuffer);
                    }
                }

                // 3. Finalización del proceso
                if (cpu.pc >= p.getInstruccionesTotales()) {
                    logEvent("✅ ¡Proceso '" + p.getNombre() + "' (ID: " + p.getId() + ") FINALIZADO exitosamente!");
                    removerProcesoDeListos(p.getId());
                    listaTerminados.agregarAlFinal(p);

                    cpu.procesoActual = null;
                    cpu.pc = 0;
                    cpu.quantumUsado = 0;
                    continue;
                }

                // 4. Expropiación por Quantum (Round Robin)
                if ("Round Robin".equalsIgnoreCase(cpu.config.getPoliticaPlanificacion()) && cpu.quantumUsado >= 3) {
                    logEvent("⏱ Quantum expirado para '" + p.getNombre() + "'. Reencolando.");
                    cpu.procesoActual = null;
                    cpu.pc = 0;
                    cpu.quantumUsado = 0;
                }
            }
        }

        actualizarTarjetasCPUGUI();
        actualizarTablasGUI();
    }

    // --- ALGORITMOS DE SELECCIÓN DE PLANIFICACIÓN ---
    private void planificarProcesosEnCPUs() {
        if (colaListos == null || colaListos.estaVacia()) return;

        Proceso[] candidatos = colaListos.obtenerListaProcesos();

        for (EstadoCPU cpu : arregloCPUs) {
            if (cpu.procesoActual == null) {
                Proceso seleccionado = seleccionarSegunPolitica(candidatos, cpu.config.getIdPC(), cpu.config.getPoliticaPlanificacion());
                if (seleccionado != null) {
                    cpu.procesoActual = seleccionado;
                    cpu.pc = 0;
                    cpu.mar = 100 * cpu.config.getIdPC();
                    cpu.quantumUsado = 0;
                    logEvent("🚀 CPU " + cpu.config.getIdPC() + " asignada a '" + seleccionado.getNombre() + "' vía " + cpu.config.getPoliticaPlanificacion());
                }
            }
        }
    }

    private Proceso seleccionarSegunPolitica(Proceso[] candidatos, int idPC, String politica) {
        Proceso elegido = null;

        for (Proceso p : candidatos) {
            if (p == null || p.getComputadorAsignado() != idPC || estaEnEjecucionOBloqueado(p.getId())) continue;

            if (elegido == null) {
                elegido = p;
                continue;
            }

            switch (politica.toUpperCase()) {
                case "PRIORIDADES":
                    if (p.getPrioridad() < elegido.getPrioridad()) { // Prioridad 1 es la mayor
                        elegido = p;
                    }
                    break;
                case "SRTF":
                    if (p.getInstruccionesTotales() < elegido.getInstruccionesTotales()) {
                        elegido = p;
                    }
                    break;
                case "FCFS":
                case "ROUND ROBIN":
                default:
                    // FCFS y RR toman el primero disponible según orden FIFO
                    break;
            }
        }
        return elegido;
    }

    // --- LÓGICA DE BLOQUEOS Y BUFFERS ---
    private void bloquearProceso(Proceso p, int ciclos, String motivo) {
        removerProcesoDeListos(p.getId());
        colaBloqueados.encolar(p);
        listaBloqueadosInfo.agregarAlFinal(new ProcesoBloqueadoInfo(p, ciclos, motivo));
    }

    private void removerInfoBloqueadoPorId(int idProceso) {
        ListaEnlazada<ProcesoBloqueadoInfo> nuevaLista = new ListaEnlazada<>();
        for (int i = 0; i < listaBloqueadosInfo.getTamano(); i++) {
            ProcesoBloqueadoInfo info = listaBloqueadosInfo.obtener(i);
            if (info != null && info.proceso.getId() != idProceso) {
                nuevaLista.agregarAlFinal(info);
            }
        }
        listaBloqueadosInfo.vaciar();
        for (int i = 0; i < nuevaLista.getTamano(); i++) {
            listaBloqueadosInfo.agregarAlFinal(nuevaLista.obtener(i));
        }
    }

    private void procesarColaBloqueados() {
        for (int i = 0; i < listaBloqueadosInfo.getTamano(); i++) {
            ProcesoBloqueadoInfo info = listaBloqueadosInfo.obtener(i);
            if (info != null && "I/O Bound".equals(info.motivo)) {
                info.ciclosRestantesBloqueado--;
                if (info.ciclosRestantesBloqueado <= 0) {
                    logEvent("🔓 Proceso '" + info.proceso.getNombre() + "' completó E/S y regresa a Listos.");
                    removerDeBloqueados(info.proceso.getId());
                    colaListos.encolar(info.proceso);
                    removerInfoBloqueadoPorId(info.proceso.getId());
                    i--;
                }
            }
        }
    }

    private void intentarDesbloquearConsumidores(String idBuffer) {
        for (int i = 0; i < listaBloqueadosInfo.getTamano(); i++) {
            ProcesoBloqueadoInfo info = listaBloqueadosInfo.obtener(i);
            if (info != null && "Buffer Vacío".equals(info.motivo)) {
                logEvent("🔓 Desbloqueando Consumidor '" + info.proceso.getNombre() + "' tras producción en " + idBuffer);
                removerDeBloqueados(info.proceso.getId());
                colaListos.encolar(info.proceso);
                removerInfoBloqueadoPorId(info.proceso.getId());
                break;
            }
        }
    }

    private void intentarDesbloquearProductores(String idBuffer) {
        for (int i = 0; i < listaBloqueadosInfo.getTamano(); i++) {
            ProcesoBloqueadoInfo info = listaBloqueadosInfo.obtener(i);
            if (info != null && "Buffer Lleno".equals(info.motivo)) {
                logEvent("🔓 Desbloqueando Productor '" + info.proceso.getNombre() + "' tras consumo en " + idBuffer);
                removerDeBloqueados(info.proceso.getId());
                colaListos.encolar(info.proceso);
                removerInfoBloqueadoPorId(info.proceso.getId());
                break;
            }
        }
    }

    private ControlBuffer obtenerOInsertarBuffer(String id) {
        for (int i = 0; i < listaBuffers.getTamano(); i++) {
            ControlBuffer buf = listaBuffers.obtener(i);
            if (buf.idBuffer.equalsIgnoreCase(id)) return buf;
        }
        ControlBuffer nuevo = new ControlBuffer(id, 5);
        listaBuffers.agregarAlFinal(nuevo);
        return nuevo;
    }

    private boolean estaEnEjecucionOBloqueado(int idProceso) {
        for (EstadoCPU cpu : arregloCPUs) {
            if (cpu.procesoActual != null && cpu.procesoActual.getId() == idProceso) return true;
        }
        for (int i = 0; i < listaBloqueadosInfo.getTamano(); i++) {
            if (listaBloqueadosInfo.obtener(i).proceso.getId() == idProceso) return true;
        }
        return false;
    }

    private void removerProcesoDeListos(int idProceso) {
        if (colaListos == null || colaListos.estaVacia()) return;
        Proceso[] listos = colaListos.obtenerListaProcesos();
        if (listos.length > 0 && listos[0] != null && listos[0].getId() == idProceso) {
            colaListos.desencolar();
        }
    }

    private void removerDeBloqueados(int idProceso) {
        if (colaBloqueados == null || colaBloqueados.estaVacia()) return;
        Proceso[] bloqueados = colaBloqueados.obtenerListaProcesos();
        if (bloqueados.length > 0 && bloqueados[0] != null && bloqueados[0].getId() == idProceso) {
            colaBloqueados.desencolar();
        }
    }

    private void actualizarTarjetasCPUGUI() {
        if (arregloCPUs == null) return;
        for (EstadoCPU cpu : arregloCPUs) {
            if (cpu.procesoActual != null) {
                cpu.lblEstado.setText("Estado CPU: EJECUTANDO (" + cpu.config.getPoliticaPlanificacion() + ")");
                cpu.lblEstado.setForeground(new Color(0, 150, 0));
                cpu.lblProceso.setText("Proceso: " + cpu.procesoActual.getNombre() + " (" + cpu.procesoActual.getTipo() + ")");
                cpu.lblPCMAR.setText("PC: " + cpu.pc + "  |  MAR: 0x" + Integer.toHexString(cpu.mar).toUpperCase());
            } else {
                cpu.lblEstado.setText("Estado CPU: IDLE (Inactivo)");
                cpu.lblEstado.setForeground(Color.BLUE);
                cpu.lblProceso.setText("Proceso Actual: Ninguno");
                cpu.lblPCMAR.setText("PC: 0  |  MAR: 0");
            }
        }
    }

    public void actualizarTablasGUI() {
        modelListos.setRowCount(0);
        modelBloqueados.setRowCount(0);
        modelTerminados.setRowCount(0);

        if (colaListos != null) {
            Proceso[] listos = colaListos.obtenerListaProcesos();
            for (Proceso p : listos) {
                if (p != null && !estaEnEjecucionOBloqueado(p.getId())) {
                    modelListos.addRow(new Object[]{p.getId(), p.getNombre(), p.getTipo(), p.getComputadorAsignado(), p.getInstruccionesTotales(), p.getMemoriaRAM()});
                }
            }
        }

        if (colaBloqueados != null) {
            Proceso[] bloqueados = colaBloqueados.obtenerListaProcesos();
            for (Proceso p : bloqueados) {
                if (p != null) {
                    modelBloqueados.addRow(new Object[]{p.getId(), p.getNombre(), p.getTipo(), p.getComputadorAsignado(), p.getInstruccionesTotales(), p.getMemoriaRAM()});
                }
            }
        }

        if (listaTerminados != null) {
            for (int i = 0; i < listaTerminados.getTamano(); i++) {
                Proceso p = listaTerminados.obtener(i);
                if (p != null) {
                    modelTerminados.addRow(new Object[]{p.getId(), p.getNombre(), p.getTipo(), p.getComputadorAsignado(), p.getInstruccionesTotales(), p.getMemoriaRAM()});
                }
            }
        }
    }

    private void iniciarSimulacion() {
        enEjecucion = true;
        btnIniciar.setEnabled(false);
        btnPausar.setEnabled(true);
        btnPasoAPaso.setEnabled(false);
        lblEstadoReloj.setText("Estado: EN EJECUCIÓN");
        lblEstadoReloj.setForeground(new Color(0, 120, 0));
        timerSimulacion.start();
        logEvent("Simulación iniciada.");
    }

    private void pausarSimulacion() {
        enEjecucion = false;
        btnIniciar.setEnabled(true);
        btnPausar.setEnabled(false);
        btnPasoAPaso.setEnabled(true);
        lblEstadoReloj.setText("Estado: PAUSADO");
        lblEstadoReloj.setForeground(Color.RED);
        timerSimulacion.stop();
        logEvent("Simulación pausada.");
    }

    private void ejecutarCicloPasoAPaso() {
        ejecutarCicloSimulacion();
    }

    public void logEvent(String mensaje) {
        txtConsolaLogs.append("[LOG] " + mensaje + "\n");
        txtConsolaLogs.setCaretPosition(txtConsolaLogs.getDocument().getLength());
    }

    public boolean isEnEjecucion() { return enEjecucion; }
    public int getVelocidadMs() { return velocidadMs; }
    public void setCicloActual(int ciclo) {
        this.cicloActual = ciclo;
        this.lblCicloActual.setText("Ciclo: " + cicloActual);
    }
}