package disco;

import gui.FabricaSistema;
import kernel.Kernel;
import modelo.Proceso;
import modelo.enums.TipoPoliticaDisco;
import modelo.enums.TipoProceso;
import reloj.RelojGlobal;
import sistema.SistemaDistribuido;
import filesystem.SistemaArchivos;
import util.GestorArchivosJson;
import util.GestorProcesosJson;
import util.LogEventos;

/**
 * Compara las cuatro políticas de disco con los mismos procesos y el mismo
 * sistema de archivos. Uso (desde la carpeta AvilaOS): PruebaPoliticasDisco [procesos.json]
 * Los deadlines se amplían para que ningún proceso muera y la comparación sea justa.
 */
public class PruebaPoliticasDisco {

    public static void main(String[] args) throws Exception {
        String archivo = args.length > 0 ? args[0] : "procesos_enunciado.json";
        System.out.println("=== Comparación de políticas de disco con " + archivo + " ===");
        System.out.printf("%-8s %-10s %-14s %-12s %-10s%n", "Política", "Ciclos", "Desplazamiento", "Atendidas", "Terminados");
        for (TipoPoliticaDisco tipo : TipoPoliticaDisco.values()) {
            GestorProcesosJson.Configuracion c = GestorProcesosJson.cargar("../json/" + archivo);
            GestorArchivosJson.DatosSistemaArchivos d = GestorArchivosJson.cargar("../json/sistema_archivos_enunciado.json");
            Disco disco = new Disco(d.totalBloques, 0, d.cabezalInicial, FabricaSistema.crearPoliticaDisco(tipo));
            SistemaArchivos fs = new SistemaArchivos(disco);
            GestorArchivosJson.aplicar(d, fs);
            disco.configurarSwap(c.bloquesSwap);
            RelojGlobal r = new RelojGlobal(1);
            SistemaDistribuido s = new SistemaDistribuido(1, r);
            s.setLogEventos(new LogEventos(5000));
            s.agregarComputador(c.ramComputador, FabricaSistema.crearPolitica(c.politicaCpu, c.quantum));
            s.conectarDisco(disco, fs);
            for (GestorProcesosJson.DatosProceso p : c.procesos) {
                Proceso proceso = new Proceso(p.nombre, p.instrucciones, p.memoria, 1, 1000000,
                        TipoProceso.ES_ARCHIVO, 1);
                proceso.setOperacion(p.operacion);
                proceso.setRutaArchivo(p.ruta);
                proceso.setBloquesArchivo(p.bloquesArchivo);
                s.enviarProceso(proceso);
            }
            r.arrancar();
            Kernel k = s.getComputador(1).getKernel();
            long fin = System.currentTimeMillis() + 30000;
            while (k.obtenerTerminados().length < c.procesos.length && System.currentTimeMillis() < fin) {
                Thread.sleep(20);
            }
            r.pausar();
            Thread.sleep(50);
            System.out.printf("%-8s %-10d %-14d %-12d %d/%d%n", tipo, r.getCiclo(), disco.getDesplazamientoTotal(),
                    disco.getSolicitudesAtendidas(), k.obtenerTerminados().length, c.procesos.length);
        }
        System.exit(0);
    }
}