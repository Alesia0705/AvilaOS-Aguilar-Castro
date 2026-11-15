package gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import modelo.Proceso;
import reloj.RelojGlobal;
import sistema.SistemaDistribuido;
import util.GestorProcesosJson;
import util.LogEventos;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Vista 1 del Proyecto 2: carga los dos archivos JSON obligatorios (procesos y
 * sistema de archivos). Mientras falte alguno, la simulación no puede iniciar
 * ni el reloj arrancar. Hay un único computador, con la RAM y la política que
 * traiga el JSON de procesos.
 */
public class VistaCarga extends JFrame {

    private JLabel lblProcesos;
    private JLabel lblArchivos;
    private JTextArea txtResumen;
    private JButton btnIniciar;

    private GestorProcesosJson.Configuracion config;
    private String rutaSistemaArchivos;

    public VistaCarga() {
        setTitle("ÁvilaOS - Carga de configuración (Vista 1)");
        setSize(720, 480);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        initUI();
    }

    private void initUI() {
        JPanel archivos = new JPanel(new GridLayout(2, 2, 10, 10));
        archivos.setBorder(BorderFactory.createTitledBorder("Archivos obligatorios (JSON)"));
        JButton btnProcesos = new JButton("Cargar procesos (JSON)");
        btnProcesos.addActionListener(e -> cargarProcesos());
        JButton btnArchivos = new JButton("Cargar sistema de archivos (JSON)");
        btnArchivos.addActionListener(e -> cargarSistemaArchivos());
        lblProcesos = new JLabel("Sin cargar");
        lblProcesos.setForeground(new Color(180, 0, 0));
        lblArchivos = new JLabel("Sin cargar");
        lblArchivos.setForeground(new Color(180, 0, 0));
        archivos.add(btnProcesos);
        archivos.add(lblProcesos);
        archivos.add(btnArchivos);
        archivos.add(lblArchivos);
        add(archivos, BorderLayout.NORTH);

        txtResumen = new JTextArea();
        txtResumen.setEditable(false);
        txtResumen.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtResumen.setText("Carga los dos archivos para poder iniciar la simulación.");
        JScrollPane scroll = new JScrollPane(txtResumen);
        scroll.setBorder(BorderFactory.createTitledBorder("Resumen de la configuración"));
        add(scroll, BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnIniciar = new JButton("Iniciar simulación");
        btnIniciar.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnIniciar.setBackground(new Color(40, 167, 69));
        btnIniciar.setForeground(Color.WHITE);
        btnIniciar.setEnabled(false);
        btnIniciar.addActionListener(e -> iniciarSimulacion());
        acciones.add(btnIniciar);
        add(acciones, BorderLayout.SOUTH);
    }

    private File elegirJson(String titulo) {
        JFileChooser selector = new JFileChooser(new File("."));
        selector.setDialogTitle(titulo);
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return selector.getSelectedFile();
    }

    private void cargarProcesos() {
        File archivo = elegirJson("Cargar procesos (JSON)");
        if (archivo == null) {
            return;
        }
        try {
            config = GestorProcesosJson.cargar(archivo.getPath());
            lblProcesos.setText(archivo.getName() + " (" + config.procesos.length + " procesos)");
            lblProcesos.setForeground(new Color(0, 130, 0));
        } catch (IllegalArgumentException | IOException ex) {
            config = null;
            lblProcesos.setText("Sin cargar");
            lblProcesos.setForeground(new Color(180, 0, 0));
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Archivo de procesos inválido",
                    JOptionPane.ERROR_MESSAGE);
        }
        actualizar();
    }

    /**
     * Por ahora solo comprueba que el JSON tenga diskConfig y fileSystem. El
     * sistema de archivos real lo carga el módulo de archivos (#56) usando la
     * ruta guardada en rutaSistemaArchivos.
     */
    private void cargarSistemaArchivos() {
        File archivo = elegirJson("Cargar sistema de archivos (JSON)");
        if (archivo == null) {
            return;
        }
        try (Reader lector = new FileReader(archivo)) {
            JsonElement raiz = JsonParser.parseReader(lector);
            if (!raiz.isJsonObject()) {
                throw new IllegalArgumentException("La raíz del archivo debe ser un objeto JSON.");
            }
            JsonObject obj = raiz.getAsJsonObject();
            if (!obj.has("diskConfig") || !obj.get("diskConfig").isJsonObject()) {
                throw new IllegalArgumentException("Falta el objeto \"diskConfig\".");
            }
            if (!obj.has("fileSystem") || !obj.get("fileSystem").isJsonArray()) {
                throw new IllegalArgumentException("Falta el arreglo \"fileSystem\".");
            }
            rutaSistemaArchivos = archivo.getPath();
            lblArchivos.setText(archivo.getName() + " (" + obj.getAsJsonArray("fileSystem").size()
                    + " entradas)");
            lblArchivos.setForeground(new Color(0, 130, 0));
        } catch (RuntimeException | IOException ex) {
            rutaSistemaArchivos = null;
            lblArchivos.setText("Sin cargar");
            lblArchivos.setForeground(new Color(180, 0, 0));
            JOptionPane.showMessageDialog(this, "Archivo de sistema de archivos inválido: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        actualizar();
    }

    private void actualizar() {
        btnIniciar.setEnabled(config != null && rutaSistemaArchivos != null);
        if (config == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Duración del ciclo: ").append(config.duracionCicloMs).append(" ms\n");
        sb.append("Swap: ").append(config.bloquesSwap).append(" bloques\n");
        sb.append("Política de disco inicial: ").append(config.politicaDisco).append("\n");
        sb.append("RAM: ").append(config.ramComputador).append("   Política CPU: ")
                .append(config.politicaCpu).append("   Quantum: ").append(config.quantum).append("\n\n");
        sb.append("Procesos:\n");
        for (int i = 0; i < config.procesos.length; i++) {
            GestorProcesosJson.DatosProceso p = config.procesos[i];
            sb.append(String.format("  %-12s mem=%-4d instr=%-3d %-10s %s%n", p.nombre, p.memoria,
                    p.instrucciones, p.operacion, p.ruta));
        }
        txtResumen.setText(sb.toString());
        txtResumen.setCaretPosition(0);
    }

    private void iniciarSimulacion() {
        if (config == null || rutaSistemaArchivos == null) {
            JOptionPane.showMessageDialog(this, "Debes cargar los dos archivos JSON antes de iniciar.",
                    "Faltan archivos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            RelojGlobal reloj = new RelojGlobal(config.duracionCicloMs);
            SistemaDistribuido sistema = new SistemaDistribuido(1, reloj);
            LogEventos log = new LogEventos(5000);
            sistema.setLogEventos(log);

            sistema.agregarComputador(config.ramComputador,
                    FabricaSistema.crearPolitica(config.politicaCpu, config.quantum));

            for (int i = 0; i < config.procesos.length; i++) {
                Proceso p = FabricaSistema.crearProcesoArchivo(config.procesos[i], config.ramComputador);
                if (!sistema.enviarProceso(p)) {
                    throw new IllegalArgumentException("No se pudo enviar el proceso \"" + p.getNombre()
                            + "\" al computador.");
                }
            }

            log.registrar(0L, "Sistema configurado: 1 computador, " + config.procesos.length + " procesos.");
            // TODO #56: cargar aquí el sistema de archivos desde rutaSistemaArchivos.

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
        SwingUtilities.invokeLater(() -> new VistaCarga().setVisible(true));
    }
}