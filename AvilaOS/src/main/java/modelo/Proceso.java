package modelo;

import modelo.enums.EstadoProceso;
import modelo.enums.TipoProceso;
import modelo.enums.MotivoBloqueo;

/**
 * Representa un proceso dentro del simulador ÁvilaOS.
 */
public class Proceso {

    // Contador compartido por todos los procesos: garantiza IDs únicos en todo el sistema
    private static int siguienteId = 1;

    private static synchronized int generarId() {
        return siguienteId++;
    }

    private int id;
    private String nombre;
    private int memoriaRAM;
    private int prioridad;
    private int deadline;
    private TipoProceso tipo;
    private int computadorAsignado;
    private EstadoProceso estado;
    private int instruccionesTotales;
    private int tiempoRestante;
    private int pc;
    private int mar;
    private MotivoBloqueo motivoBloqueo;
    private long cicloLlegada;
    private long cicloPrimeraEjecucion = -1;
    private long cicloFinalizacion = -1;
    private int ciclosBloqueado;
    // ciclos que el proceso pasa esperando en la cola de listos (tiempo de espera)
    private int ciclosEspera;

    // --- Esperas con duración fija (E/S, latencia de red) ---
    private int ciclosEsperaRestantes;
    private int ciclosCpuEnRafaga;
    // ciclos bloqueado esperando un semáforo (métricas del productor-consumidor)
    private int ciclosBloqueadoSemaforo;

    // --- Productor/consumidor: estado del protocolo ---
    // 0 = trabajando; 1 a 5 = paso del protocolo que le toca ejecutar
    private int faseProdCons;
    // ciclos de trabajo ya hechos para el elemento actual
    private int trabajoRealizado;
    private int elementosProcesados;

    // --- Campos Dinámicos por Tipo de Proceso (Issue 1) ---
    private int ciclosCpu;
    private int ciclosIo;
    private String idBuffer;
    private int ciclosProdCons;
    private int cantidadElementos;

    /**
     * Constructor para instanciar un nuevo proceso desde la GUI.
     */
    public Proceso(String nombre, int instruccionesTotales, int memoriaRAM, int prioridad, int deadline, TipoProceso tipo, int computadorAsignado) {
        this.id = generarId();
        this.nombre = nombre;
        this.memoriaRAM = memoriaRAM;
        this.prioridad = prioridad;
        this.deadline = deadline;
        this.tipo = tipo;
        this.computadorAsignado = computadorAsignado;
        this.estado = EstadoProceso.NUEVO;
        this.instruccionesTotales = instruccionesTotales;
        this.tiempoRestante = instruccionesTotales;
        this.pc = 0;
        this.mar = 0;
        this.motivoBloqueo = MotivoBloqueo.NINGUNO;
    }

    // --- Getters y Setters Base ---
    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getMemoriaRAM() {
        return memoriaRAM;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public int getDeadline() {
        return deadline;
    }

    public TipoProceso getTipo() {
        return tipo;
    }

    public int getComputadorAsignado() {
        return computadorAsignado;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public void setEstado(EstadoProceso estado) {
        this.estado = estado;
    }

    public int getInstruccionesTotales() {
        return instruccionesTotales;
    }

    public int getTiempoRestante() {
        return tiempoRestante;
    }

    public int getPc() {
        return pc;
    }

    public int getMar() {
        return mar;
    }

    public MotivoBloqueo getMotivoBloqueo() {
        return motivoBloqueo;
    }

    public void setMotivoBloqueo(MotivoBloqueo motivoBloqueo) {
        this.motivoBloqueo = motivoBloqueo;
    }

    // --- Getters y Setters para Campos Dinámicos (Issue 1) ---
    public int getCiclosCpu() {
        return ciclosCpu;
    }

    public void setCiclosCpu(int ciclosCpu) {
        this.ciclosCpu = ciclosCpu;
    }

    public int getCiclosIo() {
        return ciclosIo;
    }

    public void setCiclosIo(int ciclosIo) {
        this.ciclosIo = ciclosIo;
    }

    public String getIdBuffer() {
        return idBuffer;
    }

    public void setIdBuffer(String idBuffer) {
        this.idBuffer = idBuffer;
    }

    public int getCiclosProdCons() {
        return ciclosProdCons;
    }

    public void setCiclosProdCons(int ciclosProdCons) {
        this.ciclosProdCons = ciclosProdCons;
    }

    public int getCantidadElementos() {
        return cantidadElementos;
    }

    public void setCantidadElementos(int cantidadElementos) {
        this.cantidadElementos = cantidadElementos;
    }

    /**
     * Ejecuta una instrucción: PC y MAR avanzan 1 y baja el tiempo restante.
     */
    public void ejecutarInstruccion() {
        pc++;
        mar++;
        tiempoRestante--;
    }

    /**
     * Descuenta un ciclo del deadline (si llega a 0, el proceso debe
     * terminarse).
     */
    public void decrementarDeadline() {
        if (deadline > 0) {
            deadline--;
        }
    }

    // --- Métricas ---
    public long getCicloLlegada() {
        return cicloLlegada;
    }

    public long getCicloPrimeraEjecucion() {
        return cicloPrimeraEjecucion;
    }

    public long getCicloFinalizacion() {
        return cicloFinalizacion;
    }

    public int getCiclosBloqueado() {
        return ciclosBloqueado;
    }

    public void setCicloLlegada(long cicloLlegada) {
        this.cicloLlegada = cicloLlegada;
    }

    public void setCicloPrimeraEjecucion(long ciclo) {
        this.cicloPrimeraEjecucion = ciclo;
    }

    public void setCicloFinalizacion(long ciclo) {
        this.cicloFinalizacion = ciclo;
    }

    /**
     * Suma un ciclo al tiempo que el proceso lleva bloqueado.
     */
    public void incrementarCiclosBloqueado() {
        ciclosBloqueado++;
    }

    public int getCiclosEspera() {
        return ciclosEspera;
    }

    /**
     * Suma un ciclo al tiempo que el proceso lleva esperando en listos.
     */
    public void incrementarCiclosEspera() {
        ciclosEspera++;
    }
    // --- Espera con duración fija y ráfagas de CPU ---

    public int getCiclosEsperaRestantes() {
        return ciclosEsperaRestantes;
    }

    public void setCiclosEsperaRestantes(int ciclos) {
        this.ciclosEsperaRestantes = (ciclos > 0) ? ciclos : 0;
    }

    /**
     * Descuenta un ciclo de espera (nunca baja de 0).
     */
    public void decrementarEspera() {
        if (ciclosEsperaRestantes > 0) {
            ciclosEsperaRestantes--;
        }
    }

    public int getCiclosCpuEnRafaga() {
        return ciclosCpuEnRafaga;
    }

    public void incrementarRafaga() {
        ciclosCpuEnRafaga++;
    }

    public void reiniciarRafaga() {
        ciclosCpuEnRafaga = 0;
    }

    // --- Productor/consumidor ---
    public boolean esProdCons() {
        return tipo == TipoProceso.PRODUCTOR || tipo == TipoProceso.CONSUMIDOR;
    }

    /**
     * Deja listo a un productor o consumidor: cada elemento cuesta
     * ciclosProdCons de trabajo más 5 instrucciones del protocolo con
     * semáforos, así que ese es el total de instrucciones que debe ejecutar. No
     * hace nada con otros tipos de proceso.
     */
    public void prepararProdCons() {
        if (!esProdCons()) {
            return;
        }
        if (ciclosProdCons < 1) {
            ciclosProdCons = 1;
        }
        this.instruccionesTotales = cantidadElementos * (ciclosProdCons + 5);
        this.tiempoRestante = this.instruccionesTotales;
        this.faseProdCons = 0;
        this.trabajoRealizado = 0;
        this.elementosProcesados = 0;
    }

    public int getFaseProdCons() {
        return faseProdCons;
    }

    public void setFaseProdCons(int fase) {
        this.faseProdCons = fase;
    }

    /**
     * true mientras ejecuta alguno de los 5 pasos del protocolo (modo sistema).
     */
    public boolean estaEnProtocolo() {
        return faseProdCons >= 1;
    }

    public int getTrabajoRealizado() {
        return trabajoRealizado;
    }

    public void incrementarTrabajo() {
        trabajoRealizado++;
    }

    public void reiniciarTrabajo() {
        trabajoRealizado = 0;
    }

    public int getElementosProcesados() {
        return elementosProcesados;
    }

    public void incrementarElementos() {
        elementosProcesados++;
    }

    public int getCiclosBloqueadoSemaforo() {
        return ciclosBloqueadoSemaforo;
    }

    public void incrementarCiclosBloqueadoSemaforo() {
        ciclosBloqueadoSemaforo++;
    }

    @Override
    public String toString() {
        return "Proceso{" + "ID=" + id + ", Nombre='" + nombre + '\'' + ", RAM=" + memoriaRAM + "MB, Tipo='" + tipo + '\'' + '}';
    }
}
