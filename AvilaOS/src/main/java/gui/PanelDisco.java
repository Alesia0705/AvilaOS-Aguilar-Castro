package gui;

import disco.BloqueDisco;
import disco.Disco;
import disco.SolicitudDisco;
import filesystem.NodoArchivo;
import filesystem.SistemaArchivos;
import modelo.enums.TipoPoliticaDisco;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Pestaña "Disco y archivos" de la Vista 2: árbol del sistema de archivos,
 * mapa de bloques con la posición y dirección del cabezal, cola de E/S con la
 * operación de cada solicitud, tabla de asignación de archivos y selector de
 * la política de disco. Solo LEE del disco y del sistema de archivos; la única
 * orden que da es cambiar la política de planificación de disco.
 */
public class PanelDisco extends JPanel {

    private static final String[] COLUMNAS_COLA = {"Proceso", "Operación", "Bloque", "Ciclo de llegada", "Estado"};
    private static final String[] COLUMNAS_ASIGNACION = {"Archivo", "Bloque inicial", "Bloques", "Dueño",
        "Permiso", "Creado por", "Cadena de bloques"};
    private static final int MAX_CADENA = 14;

    private final Disco disco;
    private final SistemaArchivos fs;
    private final Consumer<String> aviso;

    private final JSpinner spnVelocidad = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
    private final JLabel lblInfo = new JLabel(" ");
    private final JLabel lblCola = new JLabel(" ");
    private final JComboBox<TipoPoliticaDisco> cmbPolitica = new JComboBox<>(TipoPoliticaDisco.values());
    private final DefaultTableModel modeloCola = new DefaultTableModel(COLUMNAS_COLA, 0);
    private final DefaultTableModel modeloAsignacion = new DefaultTableModel(COLUMNAS_ASIGNACION, 0);
    private final DefaultTreeModel modeloArbol = new DefaultTreeModel(new DefaultMutableTreeNode("/"));
    private final JTree arbol = new JTree(modeloArbol);
    private final MapaBloques mapa = new MapaBloques();
    private String firmaArbol = "";

    /**
     * @param aviso recibe un texto cada vez que el usuario cambia la política
     * (la vista lo manda al log); puede ser null
     */
    public PanelDisco(Disco disco, SistemaArchivos fs, Consumer<String> aviso) {
        if (disco == null || fs == null) {
            throw new IllegalArgumentException("El panel necesita el disco y el sistema de archivos");
        }
        this.disco = disco;
        this.fs = fs;
        this.aviso = aviso;
        setLayout(new BorderLayout(5, 5));
        add(construirNorte(), BorderLayout.NORTH);
        add(construirCentro(), BorderLayout.CENTER);
        cmbPolitica.setSelectedItem(disco.getPolitica().getTipo());
        spnVelocidad.setValue(disco.getVelocidadCabezal());
        actualizar();
    }

    private void guardarSistemaArchivos() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Guardar el sistema de archivos");
        selector.setSelectedFile(new java.io.File("sistema_archivos_guardado.json"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File destino = selector.getSelectedFile();
        if (!destino.getName().toLowerCase().endsWith(".json")) {
            destino = new java.io.File(destino.getPath() + ".json");
        }
        try {
            util.GestorArchivosJson.guardar(destino.getPath(), fs);
            if (aviso != null) {
                aviso.accept("Sistema de archivos guardado en " + destino.getName() + ".");
            }
            JOptionPane.showMessageDialog(this, "Sistema de archivos guardado en:\n" + destino.getPath(),
                    "Guardado", JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar el archivo: " + ex.getMessage(),
                    "Error al guardar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cambiarVelocidad() {
        int v = (Integer) spnVelocidad.getValue();
        disco.setVelocidadCabezal(v);
        if (aviso != null) {
            aviso.accept("Velocidad del cabezal cambiada a " + v + " bloque(s) por ciclo.");
        }
    }

    private JPanel construirNorte() {
        JButton btnCambiar = new JButton("Cambiar política de disco");
        btnCambiar.addActionListener(e -> cambiarPolitica());
        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        selector.add(new JLabel("Política de disco:"));
        selector.add(cmbPolitica);
        selector.add(btnCambiar);
        selector.add(new JLabel("(se puede cambiar con la simulación corriendo o pausada)"));
        spnVelocidad.setToolTipText("Bloques que recorre el cabezal por ciclo de reloj");
        JButton btnVelocidad = new JButton("Aplicar velocidad");
        btnVelocidad.addActionListener(e -> cambiarVelocidad());
        JPanel cabezal = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        cabezal.add(new JLabel("Velocidad del cabezal (bloques/ciclo):"));
        cabezal.add(spnVelocidad);
        cabezal.add(btnVelocidad);
        JButton btnGuardar = new JButton("Guardar sistema de archivos (JSON)");
        btnGuardar.addActionListener(e -> guardarSistemaArchivos());
        cabezal.add(btnGuardar);
        JPanel controles = new JPanel(new GridLayout(2, 1));
        controles.add(selector);
        controles.add(cabezal);

        JPanel info = new JPanel(new GridLayout(2, 1));
        lblInfo.setFont(lblInfo.getFont().deriveFont(Font.BOLD));
        info.add(lblInfo);
        info.add(lblCola);

        JPanel norte = new JPanel(new BorderLayout());
        norte.add(controles, BorderLayout.NORTH);
        norte.add(info, BorderLayout.CENTER);
        return norte;
    }

    private JSplitPane construirCentro() {
        JScrollPane scrollArbol = new JScrollPane(arbol);
        scrollArbol.setBorder(BorderFactory.createTitledBorder(
                "Sistema de archivos (nombre, bloques, dueño, permisos, primer bloque)"));
        scrollArbol.setPreferredSize(new Dimension(330, 200));

        JScrollPane scrollMapa = new JScrollPane(mapa);
        scrollMapa.setBorder(BorderFactory.createTitledBorder("Mapa de bloques del disco"));
        JScrollPane scrollCola = new JScrollPane(new JTable(modeloCola));
        scrollCola.setBorder(BorderFactory.createTitledBorder("Cola de E/S del disco"));
        JScrollPane scrollAsignacion = new JScrollPane(new JTable(modeloAsignacion));
        scrollAsignacion.setBorder(BorderFactory.createTitledBorder("Tabla de asignación de archivos (encadenada)"));

        // el mapa recibe la mitad de la altura; la cola y la tabla se reparten el resto
        JPanel derecha = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);
        gbc.gridy = 0;
        gbc.weighty = 0.5;
        derecha.add(scrollMapa, gbc);
        gbc.gridy = 1;
        gbc.weighty = 0.25;
        derecha.add(scrollCola, gbc);
        gbc.gridy = 2;
        derecha.add(scrollAsignacion, gbc);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollArbol, derecha);
        split.setResizeWeight(0.25);
        return split;
    }

    private void cambiarPolitica() {
        TipoPoliticaDisco tipo = (TipoPoliticaDisco) cmbPolitica.getSelectedItem();
        if (tipo == null || tipo == disco.getPolitica().getTipo()) {
            return;
        }
        disco.setPolitica(FabricaSistema.crearPoliticaDisco(tipo));
        if (aviso != null) {
            aviso.accept("Política de disco cambiada a " + tipo + ".");
        }
        actualizar();
    }

    /**
     * Relee el estado del disco y del sistema de archivos y lo muestra.
     */
    public void actualizar() {
        try {
            actualizarInfo();
            actualizarCola();
            actualizarArbolYAsignacion();
            mapa.repaint();
        } catch (RuntimeException ex) {
            // el simulador cambia el estado en otro hilo: si una lectura coincide con un cambio,
            // se reintenta en el siguiente refresco (250 ms)
        }
    }

    private void actualizarInfo() {
        int dir = disco.getDireccion();
        String sentido = (dir >= 0) ? "↑ hacia bloques mayores" : "↓ hacia bloques menores";
        lblInfo.setText("Política: " + disco.getPolitica().getTipo() + "   |   Cabezal en el bloque "
                + disco.getCabezal() + "   |   Velocidad: " + disco.getVelocidadCabezal() + " bloque(s)/ciclo   |   Dirección: " + sentido
                + "   |   Modo: " + (fs.getModoActual() == modelo.enums.ModoUsuario.ADMINISTRADOR
                ? "administrador" : "usuario " + fs.getUsuarioActual()));
        lblCola.setText("Pendientes: " + disco.getCantidadPendientes() + "   |   Atendidas: "
                + disco.getSolicitudesAtendidas() + "   |   Desplazamiento total: "
                + disco.getDesplazamientoTotal() + " bloques   |   Libres: "
                + disco.contarBloquesLibres(false) + "   |   Swap libre: " + disco.contarBloquesLibres(true)
                + " de " + disco.getBloquesSwap());
    }

    private void actualizarCola() {
        modeloCola.setRowCount(0);
        SolicitudDisco actual = disco.getSolicitudActual();
        if (actual != null) {
            modeloCola.addRow(fila(actual, "Atendiendo"));
        }
        SolicitudDisco[] pendientes = disco.obtenerPendientes();
        for (int i = 0; i < pendientes.length; i++) {
            modeloCola.addRow(fila(pendientes[i], "En espera"));
        }
    }

    private Object[] fila(SolicitudDisco s, String estado) {
        return new Object[]{s.getProceso().getNombre(), s.getOperacion(), s.getBloque(), s.getCicloLlegada(), estado};
    }

    private void actualizarArbolYAsignacion() {
        NodoArchivo[] archivos = fs.obtenerTodosLosArchivos();
        StringBuilder firma = new StringBuilder();
        modeloAsignacion.setRowCount(0);
        for (int i = 0; i < archivos.length; i++) {
            NodoArchivo a = archivos[i];
            String cadena = cadena(a);
            firma.append(a.getRutaCompleta()).append('|').append(a.getTamanoBloques()).append('|')
                    .append(a.getPrimerBloque()).append('|').append(a.getDueno()).append('|')
                    .append(a.getPermiso()).append('|').append(a.getCreadoPor()).append(';');
            modeloAsignacion.addRow(new Object[]{a.getRutaCompleta(), a.getPrimerBloque(), a.getTamanoBloques(),
                a.getDueno(), a.getPermiso(), a.getCreadoPor(), cadena});
        }
        // el árbol solo se reconstruye si cambió algo (así no se colapsa mientras se navega)
        if (!firma.toString().equals(firmaArbol)) {
            firmaArbol = firma.toString();
            DefaultMutableTreeNode raiz = new DefaultMutableTreeNode("/");
            agregarHijos(raiz, fs.getRaiz());
            modeloArbol.setRoot(raiz);
            for (int i = 0; i < arbol.getRowCount(); i++) {
                arbol.expandRow(i);
            }
        }
    }

    private String cadena(NodoArchivo archivo) {
        StringBuilder sb = new StringBuilder();
        int bloque = archivo.getPrimerBloque();
        int contados = 0;
        while (bloque >= 0 && bloque < disco.getTotalBloques() && contados < archivo.getTamanoBloques()) {
            if (contados == MAX_CADENA) {
                sb.append(" → …");
                break;
            }
            if (contados > 0) {
                sb.append(" → ");
            }
            sb.append(bloque);
            bloque = disco.getBloque(bloque).getSiguienteBloque();
            contados++;
        }
        return sb.toString();
    }

    private void agregarHijos(DefaultMutableTreeNode nodoArbol, NodoArchivo nodo) {
        NodoArchivo[] hijos = nodo.getHijosArray();
        for (int i = 0; i < hijos.length; i++) {
            NodoArchivo h = hijos[i];
            String texto;
            if (h.isEsDirectorio()) {
                texto = h.getNombre() + "/  [dueño: " + h.getDueno() + ", " + h.getPermiso() + "]";
            } else {
                texto = h.getNombre() + "  [" + h.getTamanoBloques() + " bloques, dueño: " + h.getDueno()
                        + ", " + h.getPermiso() + ", primer bloque: " + h.getPrimerBloque() + "]";
            }
            DefaultMutableTreeNode hijo = new DefaultMutableTreeNode(texto);
            nodoArbol.add(hijo);
            if (h.isEsDirectorio()) {
                agregarHijos(hijo, h);
            }
        }
    }

    /**
     * Dibuja un cuadrito por bloque: libre, ocupado, swap, objetivo de una
     * solicitud y el cabezal.
     */
    private class MapaBloques extends JPanel {

        private static final int CELDA = 22;
        private final Color libre = new Color(235, 235, 235);
        private final Color ocupado = new Color(70, 130, 200);
        private final Color swap = new Color(225, 170, 60);
        private final Color swapUsado = new Color(180, 100, 20);
        private final Color pendiente = new Color(250, 225, 60);
        private final Color cabezal = new Color(210, 30, 30);

        MapaBloques() {
            setBackground(Color.WHITE);
            setToolTipText("Azul: archivo · Gris: libre · Naranja: swap · Amarillo: solicitud pendiente · Rojo: cabezal");
        }

        private int columnas() {
            return Math.max(1, (Math.max(getWidth(), 300) - 10) / CELDA);
        }

        @Override
        public Dimension getPreferredSize() {
            int cols = columnas();
            int filas = (disco.getTotalBloques() + cols - 1) / cols;
            return new Dimension(10 + cols * CELDA, filas * CELDA + 26);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int cols = columnas();
            int total = disco.getTotalBloques();
            SolicitudDisco[] pend = disco.obtenerPendientes();
            SolicitudDisco actual = disco.getSolicitudActual();
            int head = disco.getCabezal();
            g.setFont(g.getFont().deriveFont(9f));
            for (int i = 0; i < total; i++) {
                int x = 5 + (i % cols) * CELDA;
                int y = 5 + (i / cols) * CELDA;
                BloqueDisco b = disco.getBloque(i);
                Color c = libre;
                if (b.isEsSwap()) {
                    c = b.isOcupado() ? swapUsado : swap;
                } else if (b.isOcupado()) {
                    c = ocupado;
                }
                boolean objetivo = (actual != null && actual.getBloque() == i);
                for (int k = 0; k < pend.length && !objetivo; k++) {
                    objetivo = pend[k] != null && pend[k].getBloque() == i;
                }
                g.setColor(c);
                g.fillRect(x, y, CELDA - 2, CELDA - 2);
                if (objetivo) {
                    g.setColor(pendiente);
                    g.fillRect(x + 4, y + 4, CELDA - 10, CELDA - 10);
                }
                if (i == head) {
                    g.setColor(cabezal);
                    g.drawRect(x, y, CELDA - 3, CELDA - 3);
                    g.drawRect(x + 1, y + 1, CELDA - 5, CELDA - 5);
                    g.fillRect(x + 6, y + 6, CELDA - 14, CELDA - 14);
                }
                if (i % 10 == 0) {
                    g.setColor(Color.DARK_GRAY);
                    g.drawString(String.valueOf(i), x + 1, y + 10);
                }
            }
            int filas = (total + cols - 1) / cols;
            g.setColor(Color.DARK_GRAY);
            g.drawString("Azul: archivo   Gris: libre   Naranja: swap   Amarillo: solicitud   Rojo: cabezal",
                    5, filas * CELDA + 18);
        }
    }
}