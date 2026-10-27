package gui;

import modelo.Proceso;
import modelo.enums.TipoProceso;
import planificacion.PoliticaEDF;
import planificacion.PoliticaFCFS;
import planificacion.PoliticaPlanificacion;
import planificacion.PoliticaPrioridades;
import planificacion.PoliticaRoundRobin;

/**
 * Validación de los datos que escribe el usuario y creación de procesos y
 * políticas a partir de ellos. La usan las dos vistas, para que las reglas de
 * tipo de dato y rango sean las mismas al configurar y al crear al vuelo.
 *
 * Todos los métodos lanzan IllegalArgumentException con un mensaje listo para
 * mostrar al usuario.
 * @author Alesia Castro
 */
public final class FabricaSistema {

    public static final String[] TIPOS = {"CPU Bound", "IO Bound", "Productor", "Consumidor"};
    public static final String[] POLITICAS = {"Round Robin", "FCFS", "EDF", "Prioridades"};

    private FabricaSistema() {
    }

    /**
     * Convierte un texto en un entero dentro de [min, max].
     */
    public static int entero(String campo, Object valor, int min, int max) {
        String texto = (valor == null) ? "" : valor.toString().trim();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("\"" + campo + "\" está vacío.");
        }
        int numero;
        try {
            numero = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + campo + "\" debe ser un número entero (escribiste \""
                    + texto + "\").");
        }
        if (numero < min || numero > max) {
            throw new IllegalArgumentException("\"" + campo + "\" debe estar entre " + min + " y " + max
                    + " (escribiste " + numero + ").");
        }
        return numero;
    }

    /**
     * Texto no vacío de hasta 'maximo' caracteres.
     */
    public static String texto(String campo, Object valor, int maximo) {
        String texto = (valor == null) ? "" : valor.toString().trim();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("\"" + campo + "\" está vacío.");
        }
        if (texto.length() > maximo) {
            throw new IllegalArgumentException("\"" + campo + "\" no puede tener más de " + maximo + " caracteres.");
        }
        return texto;
    }

    public static TipoProceso tipo(String texto) {
        if ("CPU Bound".equals(texto)) {
            return TipoProceso.CPU_BOUND;
        }
        if ("IO Bound".equals(texto)) {
            return TipoProceso.IO_BOUND;
        }
        if ("Productor".equals(texto)) {
            return TipoProceso.PRODUCTOR;
        }
        if ("Consumidor".equals(texto)) {
            return TipoProceso.CONSUMIDOR;
        }
        throw new IllegalArgumentException("Tipo de proceso desconocido: \"" + texto + "\".");
    }

    public static PoliticaPlanificacion crearPolitica(String nombre, int quantum) {
        if ("Round Robin".equals(nombre)) {
            return new PoliticaRoundRobin(quantum);
        }
        if ("FCFS".equals(nombre)) {
            return new PoliticaFCFS();
        }
        if ("EDF".equals(nombre)) {
            return new PoliticaEDF();
        }
        if ("Prioridades".equals(nombre)) {
            return new PoliticaPrioridades();
        }
        throw new IllegalArgumentException("Política desconocida: \"" + nombre + "\".");
    }

    /**
     * Valida todos los campos de un proceso y lo crea.
     *
     * @param ramComputador RAM del computador al que se asigna (el proceso no
     * puede pedir más)
     * @param idsBuffers identificadores de los buffers que existen
     */
    public static Proceso crearProceso(String nombre, String tipo, Object instrucciones, Object ram,
            Object prioridad, Object deadline, Object computador, Object ciclosCpu, Object ciclosIo,
            String idBuffer, Object ciclosProdCons, Object elementos,
            int cantidadComputadores, int[] ramPorComputador, String[] idsBuffers) {

        String nombreValido = texto("Nombre", nombre, 30);
        TipoProceso tipoValido = tipo(tipo);
        int instr = entero("Instrucciones", instrucciones, 1, 100000);
        int ramProceso = entero("RAM del proceso", ram, 1, 1000000);
        int prio = entero("Prioridad", prioridad, 1, 100);
        int dead = entero("Deadline (ciclos)", deadline, 1, 1000000);
        int pc = entero("Computador", computador, 1, cantidadComputadores);

        if (ramProceso > ramPorComputador[pc - 1]) {
            throw new IllegalArgumentException("El proceso \"" + nombreValido + "\" pide " + ramProceso
                    + " de RAM, pero el computador " + pc + " solo tiene " + ramPorComputador[pc - 1] + ".");
        }

        Proceso proceso = new Proceso(nombreValido, instr, ramProceso, prio, dead, tipoValido, pc);

        if (tipoValido == TipoProceso.IO_BOUND) {
            proceso.setCiclosCpu(entero("Ciclos de CPU antes de la E/S", ciclosCpu, 1, 1000));
            proceso.setCiclosIo(entero("Ciclos de espera de E/S", ciclosIo, 1, 1000));
        }
        if (tipoValido == TipoProceso.PRODUCTOR || tipoValido == TipoProceso.CONSUMIDOR) {
            String buffer = (idBuffer == null) ? "" : idBuffer.trim();
            boolean existe = false;
            for (int i = 0; i < idsBuffers.length; i++) {
                if (idsBuffers[i].equals(buffer)) {
                    existe = true;
                }
            }
            if (!existe) {
                throw new IllegalArgumentException("El buffer \"" + buffer + "\" del proceso \""
                        + nombreValido + "\" no existe.");
            }
            proceso.setIdBuffer(buffer);
            proceso.setCiclosProdCons(entero("Ciclos de trabajo por elemento", ciclosProdCons, 1, 1000));
            proceso.setCantidadElementos(entero("Cantidad de elementos", elementos, 1, 10000));
        }
        return proceso;
    }
}