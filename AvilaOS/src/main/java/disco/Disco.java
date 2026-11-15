package disco;

import java.util.concurrent.Semaphore;
import kernel.ServicioSistema;
import modelo.enums.TipoOperacion;
import modelo.enums.TipoPoliticaDisco;
import reloj.EscuchaReloj;
import util.ListaEnlazada;

/**
 * Disco simulado (SD) local con asignacion encadenada de bloques, area de swap
 * y cola unica de solicitudes de E/S protegida con Semaphore.
 */
public class Disco implements EscuchaReloj {

    private final int totalBloques;
    private final BloqueDisco[] bloques;
    private int bloquesSwap;

    private int cabezal;
    private int direccion; // 1 hacia bloques mayores, -1 hacia menores
    private int velocidadCabezal; // posiciones que recorre el cabezal por ciclo

    private final ListaEnlazada<SolicitudDisco> colaSolicitudes;
    private final Semaphore mutexCola;
    private final Semaphore mutexBloques;

    private volatile PoliticaDisco politica;
    private volatile ServicioSistema sistema;

    // Estado de la solicitud actualmente en atencion
    private SolicitudDisco solicitudActual;
    private int destinoIntermedio; // -1 si va directo al bloque; totalBloques-1 o 0 en extremos SCAN/C-SCAN
    private boolean saltoCircularPendiente; // true si C-SCAN debe saltar de totalBloques-1 a 0 sin sumar desplazamiento
    private int ciclosServicioRestantes;

    // Metricas del disco
    private long desplazamientoTotal;
    private long tiempoEsperaTotal;
    private int solicitudesAtendidas;
    private final int[] atendidasPorPolitica;

    public Disco(int totalBloques, int bloquesSwap, int cabezalInicial, PoliticaDisco politicaInicial) {
        if (totalBloques <= 0) {
            throw new IllegalArgumentException("El disco debe tener al menos 1 bloque");
        }
        if (bloquesSwap < 0 || bloquesSwap >= totalBloques) {
            throw new IllegalArgumentException("Tamano de swap invalido");
        }
        if (cabezalInicial < 0 || cabezalInicial >= totalBloques) {
            throw new IllegalArgumentException("Posicion inicial del cabezal fuera de rango");
        }
        if (politicaInicial == null) {
            throw new IllegalArgumentException("El disco requiere una politica inicial");
        }

        this.totalBloques = totalBloques;
        this.bloques = new BloqueDisco[totalBloques];
        for (int i = 0; i < totalBloques; i++) {
            this.bloques[i] = new BloqueDisco(i);
        }

        this.cabezal = cabezalInicial;
        this.politica = politicaInicial;
        this.direccion = politicaInicial.getDireccion();
        this.velocidadCabezal = 1;

        this.colaSolicitudes = new ListaEnlazada<>();
        this.mutexCola = new Semaphore(1);
        this.mutexBloques = new Semaphore(1);

        this.solicitudActual = null;
        this.destinoIntermedio = -1;
        this.saltoCircularPendiente = false;
        this.ciclosServicioRestantes = 0;

        this.desplazamientoTotal = 0;
        this.tiempoEsperaTotal = 0;
        this.solicitudesAtendidas = 0;
        this.atendidasPorPolitica = new int[TipoPoliticaDisco.values().length];

        configurarSwap(bloquesSwap);
    }

    /**
     * Reserva como area de intercambio los ultimos bloquesSwap bloques LIBRES del
     * disco (los bloques ya ocupados por archivos se saltan). Asi el area de swap
     * nunca pisa a un archivo cargado desde el JSON.
     *
     * @throws IllegalArgumentException si no hay suficientes bloques libres
     */
    public final void configurarSwap(int bloquesSwap) {
        adquirir(mutexBloques);
        try {
            int libres = 0;
            for (int i = 0; i < totalBloques; i++) {
                if (!bloques[i].isOcupado() || bloques[i].isEsSwap()) {
                    libres++;
                }
            }
            if (bloquesSwap < 0 || bloquesSwap > libres) {
                throw new IllegalArgumentException("El swap pide " + bloquesSwap
                        + " bloques, pero el disco solo tiene " + libres + " libres.");
            }
            this.bloquesSwap = bloquesSwap;
            int pendientes = bloquesSwap;
            for (int i = totalBloques - 1; i >= 0; i--) {
                boolean disponible = !bloques[i].isOcupado() || bloques[i].isEsSwap();
                if (pendientes > 0 && disponible) {
                    bloques[i].setEsSwap(true);
                    pendientes--;
                } else {
                    bloques[i].setEsSwap(false);
                }
            }
        } finally {
            mutexBloques.release();
        }
    }

    // ==================== ASIGNACION ENCADENADA DE BLOQUES ====================

    /**
     * Asigna cantidadBloques libres (fuera de swap) usando asignacion encadenada.
     *
     * @return el indice del primer bloque asignado, o -1 si no hay espacio suficiente
     */
    public int asignarArchivo(String rutaArchivo, int cantidadBloques) {
        if (cantidadBloques <= 0) {
            return -1;
        }
        adquirir(mutexBloques);
        try {
            if (contarBloquesLibres(false) < cantidadBloques) {
                return -1;
            }
            int primerBloque = -1;
            int anterior = -1;
            int asignados = 0;

            for (int i = 0; i < totalBloques && asignados < cantidadBloques; i++) {
                if (!bloques[i].isEsSwap() && !bloques[i].isOcupado()) {
                    bloques[i].ocupar(rutaArchivo, -1);
                    if (primerBloque == -1) {
                        primerBloque = i;
                    }
                    if (anterior != -1) {
                        bloques[anterior].setSiguienteBloque(i);
                    }
                    anterior = i;
                    asignados++;
                }
            }
            return primerBloque;
        } finally {
            mutexBloques.release();
        }
    }

    /**
     * Asigna un rango contiguo fijo (usado al cargar el JSON inicial del sistema de archivos)
     * enlazando cada bloque con el siguiente.
     */
    public boolean asignarBloquesFijos(String rutaArchivo, int startBlock, int cantidadBloques) {
        if (startBlock < 0 || cantidadBloques <= 0 || startBlock + cantidadBloques > totalBloques) {
            return false;
        }
        adquirir(mutexBloques);
        try {
            for (int i = startBlock; i < startBlock + cantidadBloques; i++) {
                if (bloques[i].isOcupado()) {
                    return false;
                }
            }
            for (int i = startBlock; i < startBlock + cantidadBloques; i++) {
                int siguiente = (i + 1 < startBlock + cantidadBloques) ? (i + 1) : -1;
                bloques[i].ocupar(rutaArchivo, siguiente);
            }
            return true;
        } finally {
            mutexBloques.release();
        }
    }

    /**
     * Libera todos los bloques encadenados a partir de primerBloque.
     */
    public void liberarCadena(int primerBloque) {
        adquirir(mutexBloques);
        try {
            int actual = primerBloque;
            while (actual >= 0 && actual < totalBloques) {
                int siguiente = bloques[actual].getSiguienteBloque();
                bloques[actual].liberar();
                actual = siguiente;
            }
        } finally {
            mutexBloques.release();
        }
    }

    /**
     * Reserva bloques en el area de intercambio (swap) para un proceso suspendido.
     *
     * @return primer bloque asignado en swap, o -1 si no hay espacio suficiente
     */
    public int asignarSwap(String nombreProceso, int cantidadBloques) {
        if (cantidadBloques <= 0) {
            return -1;
        }
        adquirir(mutexBloques);
        try {
            if (contarBloquesLibres(true) < cantidadBloques) {
                return -1;
            }
            int primerBloque = -1;
            int anterior = -1;
            int asignados = 0;

            for (int i = 0; i < totalBloques && asignados < cantidadBloques; i++) {
                if (bloques[i].isEsSwap() && !bloques[i].isOcupado()) {
                    bloques[i].ocupar("SWAP:" + nombreProceso, -1);
                    if (primerBloque == -1) {
                        primerBloque = i;
                    }
                    if (anterior != -1) {
                        bloques[anterior].setSiguienteBloque(i);
                    }
                    anterior = i;
                    asignados++;
                }
            }
            return primerBloque;
        } finally {
            mutexBloques.release();
        }
    }

    public int contarBloquesLibres(boolean enSwap) {
        int libres = 0;
        for (int i = 0; i < totalBloques; i++) {
            if (bloques[i].isEsSwap() == enSwap && !bloques[i].isOcupado()) {
                libres++;
            }
        }
        return libres;
    }

    // ==================== COLA DE SOLICITUDES Y PLANIFICACION ====================

    /**
     * Encola una solicitud de E/S en la cola unica del disco.
     */
    public boolean encolarSolicitud(SolicitudDisco solicitud) {
        if (solicitud == null || solicitud.getBloque() < 0 || solicitud.getBloque() >= totalBloques) {
            return false;
        }
        adquirir(mutexCola);
        try {
            colaSolicitudes.agregarAlFinal(solicitud);
            return true;
        } finally {
            mutexCola.release();
        }
    }

    /**
     * Devuelve una instantanea de las solicitudes pendientes en orden de llegada.
     */
    public SolicitudDisco[] obtenerPendientes() {
        adquirir(mutexCola);
        try {
            int n = colaSolicitudes.getTamano();
            SolicitudDisco[] arr = new SolicitudDisco[n];
            for (int i = 0; i < n; i++) {
                arr[i] = colaSolicitudes.obtener(i);
            }
            return arr;
        } finally {
            mutexCola.release();
        }
    }

    @Override
    public void onTick(long ciclo) {
        if (solicitudActual == null) {
            seleccionarSiguienteSolicitud();
        }
        if (solicitudActual == null) {
            return;
        }

        // 1. Si hay un extremo intermedio (SCAN o C-SCAN), mover primero hacia ese extremo
        if (destinoIntermedio != -1) {
            moverCabezalHacia(destinoIntermedio);
            if (cabezal == destinoIntermedio) {
                destinoIntermedio = -1;
                if (saltoCircularPendiente) {
                    // C-SCAN salta de totalBloques-1 a 0 sin sumar desplazamiento
                    cabezal = 0;
                    saltoCircularPendiente = false;
                }
            }
            return;
        }

        // 2. Mover cabezal hacia el bloque de la solicitud actual
        int objetivo = solicitudActual.getBloque();
        if (cabezal != objetivo) {
            moverCabezalHacia(objetivo);
            return;
        }

        // 3. El cabezal ya esta sobre el bloque: ejecutar lectura/escritura (1 ciclo por bloque)
        ciclosServicioRestantes--;
        if (ciclosServicioRestantes <= 0) {
            completarSolicitudActual(ciclo);
        }
    }

    private void seleccionarSiguienteSolicitud() {
        adquirir(mutexCola);
        try {
            int cantidad = colaSolicitudes.getTamano();
            if (cantidad == 0) {
                return;
            }
            SolicitudDisco[] arr = new SolicitudDisco[cantidad];
            for (int i = 0; i < cantidad; i++) {
                arr[i] = colaSolicitudes.obtener(i);
            }

            PoliticaDisco pol = this.politica;
            int dirAntes = pol.getDireccion();
            int elegido = pol.elegir(arr, cantidad, cabezal, totalBloques);
            SolicitudDisco sel = arr[elegido];
            colaSolicitudes.quitar(sel);

            this.solicitudActual = sel;
            // cada bloque leido o escrito cuesta un ciclo de servicio
            this.ciclosServicioRestantes = Math.max(1, sel.getCantidadBloques());
            this.destinoIntermedio = -1;
            this.saltoCircularPendiente = false;

            if (pol.getTipo() == TipoPoliticaDisco.SCAN) {
                int dirDespues = pol.getDireccion();
                if (dirAntes == 1 && dirDespues == -1 && cabezal < totalBloques - 1) {
                    this.destinoIntermedio = totalBloques - 1;
                } else if (dirAntes == -1 && dirDespues == 1 && cabezal > 0) {
                    this.destinoIntermedio = 0;
                }
                this.direccion = dirDespues;
            } else if (pol.getTipo() == TipoPoliticaDisco.C_SCAN) {
                this.direccion = 1;
                if (sel.getBloque() < cabezal) {
                    if (cabezal < totalBloques - 1) {
                        this.destinoIntermedio = totalBloques - 1;
                        this.saltoCircularPendiente = true;
                    } else {
                        this.cabezal = 0;
                    }
                }
            } else {
                if (sel.getBloque() > cabezal) {
                    this.direccion = 1;
                } else if (sel.getBloque() < cabezal) {
                    this.direccion = -1;
                }
            }
        } finally {
            mutexCola.release();
        }
    }

    private void moverCabezalHacia(int objetivo) {
        int diff = objetivo - cabezal;
        if (diff == 0) {
            return;
        }
        int paso = Math.min(Math.abs(diff), velocidadCabezal);
        if (diff > 0) {
            cabezal += paso;
            direccion = 1;
        } else {
            cabezal -= paso;
            direccion = -1;
        }
        desplazamientoTotal += paso;
    }

    private void completarSolicitudActual(long ciclo) {
        SolicitudDisco terminada = solicitudActual;
        solicitudActual = null;

        solicitudesAtendidas++;
        atendidasPorPolitica[politica.getTipo().ordinal()]++;
        long espera = Math.max(0, ciclo - terminada.getCicloLlegada());
        tiempoEsperaTotal += espera;

        ServicioSistema srv = this.sistema;
        if (srv != null) {
            srv.registrarEvento(ciclo, "Disco atiende solicitud de "
                    + terminada.getProceso().getNombre() + " (" + terminada.getOperacion()
                    + ") en el bloque " + terminada.getBloque()
                    + " (" + politica.getTipo() + ").");
            TipoOperacion op = terminada.getOperacion();
            if (op == TipoOperacion.SUSPENDER || op == TipoOperacion.REACTIVAR) {
                // la suspension o reactivacion de un proceso la concluye el kernel de su computador
                srv.completarSwap(terminada.getProceso(), op, ciclo);
            } else {
                srv.despertar(terminada.getProceso());
            }
        }
    }

    private void adquirir(Semaphore sem) {
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== GETTERS Y SETTERS ====================

    public void setPolitica(PoliticaDisco nuevaPolitica) {
        if (nuevaPolitica != null) {
            this.politica = nuevaPolitica;
            this.direccion = nuevaPolitica.getDireccion();
        }
    }

    public PoliticaDisco getPolitica() {
        return politica;
    }

    public void setSistema(ServicioSistema sistema) {
        this.sistema = sistema;
    }

    public void setVelocidadCabezal(int velocidadCabezal) {
        if (velocidadCabezal > 0) {
            this.velocidadCabezal = velocidadCabezal;
        }
    }

    public int getTotalBloques() {
        return totalBloques;
    }

    public int getBloquesSwap() {
        return bloquesSwap;
    }

    public BloqueDisco getBloque(int indice) {
        if (indice < 0 || indice >= totalBloques) {
            return null;
        }
        return bloques[indice];
    }

    public BloqueDisco[] getBloques() {
        return bloques;
    }

    public int getCabezal() {
        return cabezal;
    }

    public int getDireccion() {
        return direccion;
    }

    public SolicitudDisco getSolicitudActual() {
        return solicitudActual;
    }

    public int getCantidadPendientes() {
        adquirir(mutexCola);
        try {
            return colaSolicitudes.getTamano();
        } finally {
            mutexCola.release();
        }
    }

    public long getDesplazamientoTotal() {
        return desplazamientoTotal;
    }

    public int getSolicitudesAtendidas() {
        return solicitudesAtendidas;
    }

    public double getTiempoEsperaPromedio() {
        return (solicitudesAtendidas == 0) ? 0.0 : (double) tiempoEsperaTotal / solicitudesAtendidas;
    }

    public int getAtendidasPorPolitica(TipoPoliticaDisco tipo) {
        return atendidasPorPolitica[tipo.ordinal()];
    }
}