package gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import hardware.Cpu;
import reloj.EscuchaReloj;
import reloj.RelojGlobal;
import util.ListaEnlazada;

/**
 * Vista principal de la simulación de ÁvilaOS.
 * Desacoplada del motor de ejecución mediante la interfaz EscuchaReloj.
 */
public class VistaSimulacion extends JFrame implements EscuchaReloj {

    private RelojGlobal reloj;
    private ListaEnlazada<Cpu> cpus;

    // Componentes de interfaz
    private JLabel lblCicloReloj;
    private JButton btnPausa;
    private JButton btnPasoAPaso;
    private JSlider sliderVelocidad;
    private JTextArea txtLog;
    private JTable tablaProcesos;

    /**
     * Constructor de la Vista de Simulación.
     * @param reloj Instancia del RelojGlobal.
     * @param cpus Lista de CPUs registradas en el sistema.
     */
    public VistaSimulacion(RelojGlobal reloj, ListaEnlazada<Cpu> cpus) {
        this.reloj = reloj;
        this.cpus = cpus;

        initComponents();

        // Registrar la vista como escucha en el RelojGlobal de Alesia
        if (this.reloj != null) {
            this.reloj.registrar(this);
        }
    }

    /**
     * Método exigido por la interfaz EscuchaReloj.
     * Se ejecuta automáticamente en cada pulso del RelojGlobal.
     */
    @Override
    public void onTick(long cicloActual) {
        // Garantiza la actualización segura de la GUI en el hilo de Swing (EDT)
        SwingUtilities.invokeLater(() -> {
            actualizarReloj(cicloActual);
            actualizarCPUs();
            actualizarTablasYBuffers();
        });
    }

    private void actualizarReloj(long cicloActual) {
        if (lblCicloReloj != null) {
            lblCicloReloj.setText("Ciclo Global: " + cicloActual);
        }
    }

    private void actualizarCPUs() {
        if (cpus == null) return;

        for (int i = 0; i < cpus.getTamano(); i++) {
            Cpu cpu = cpus.obtener(i);
            // Lectura de registros para renderizar la tarjeta de CPU en la vista
        }
    }

    private void actualizarTablasYBuffers() {
        // Refresco de tablas de procesos (Listos, Bloqueados) y estado de buffers acotados
    }

    private void initComponents() {
        setTitle("ÁvilaOS - Simulación en Tiempo Real");
        setSize(1024, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- Panel Superior: Controles del Reloj ---
        JPanel panelSuperior = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblCicloReloj = new JLabel("Ciclo Global: 0");
        btnPausa = new JButton("Pausa / Reanudar");
        btnPasoAPaso = new JButton("Paso a Paso");
        sliderVelocidad = new JSlider(1, 10, 5);

        panelSuperior.add(lblCicloReloj);
        panelSuperior.add(btnPausa);
        panelSuperior.add(btnPasoAPaso);
        panelSuperior.add(new JLabel("Velocidad:"));
        panelSuperior.add(sliderVelocidad);

        add(panelSuperior, BorderLayout.NORTH);

        // --- Panel Central: Consola de Logs ---
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        JScrollPane scrollLog = new JScrollPane(txtLog);
        add(scrollLog, BorderLayout.CENTER);

        // --- Eventos de Botones integrados con el RelojGlobal ---
        btnPausa.addActionListener(e -> {
            if (reloj != null) {
                if (reloj.estaPausado()) {
                    reloj.reanudar();
                } else {
                    reloj.pausar();
                }
            }
        });

        btnPasoAPaso.addActionListener(e -> {
            if (reloj != null) {
                reloj.avanzarUnCiclo();
            }
        });

        sliderVelocidad.addChangeListener(e -> {
            if (reloj != null) {
                int valor = sliderVelocidad.getValue();
                // Ajustar duración del ciclo en ms según el slider (ej: 1000ms a 100ms)
                reloj.setDuracionCicloMs(1100 - (valor * 100));
            }
        });
    }
}