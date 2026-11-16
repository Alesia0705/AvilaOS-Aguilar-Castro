package kernel;

import disco.Disco;
import filesystem.SistemaArchivos;
import gui.FabricaSistema;
import modelo.Proceso;
import modelo.enums.ModoUsuario;
import modelo.enums.TipoOperacion;
import reloj.RelojGlobal;
import sistema.SistemaDistribuido;
import util.GestorArchivosJson;
import util.GestorProcesosJson;
import util.LogEventos;

/**
 * Prueba de los modos administrador / usuario, sin interfaz.
 * Uso (desde la carpeta AvilaOS): PruebaModosUsuario [usuario]
 * 1) Comprueba las reglas de permisos directamente.
 * 2) Ejecuta los procesos del enunciado como ese usuario (por defecto user1):
 *    los que violan las reglas se rechazan con explicación y el resto termina.
 */
public class PruebaModosUsuario {

    private static int fallos = 0;

    private static void esperar(String que, boolean ok) {
        System.out.println((ok ? "  OK   " : "  FALLA ") + que);
        if (!ok) {
            fallos++;
        }
    }

    public static void main(String[] args) throws Exception {
        String usuario = args.length > 0 ? args[0] : "user1";
        GestorProcesosJson.Configuracion c = GestorProcesosJson.cargar("../json/procesos_enunciado.json");
        GestorArchivosJson.DatosSistemaArchivos d = GestorArchivosJson.cargar("../json/sistema_archivos_enunciado.json");
        Disco disco = new Disco(d.totalBloques, 0, d.cabezalInicial, FabricaSistema.crearPoliticaDisco(c.politicaDisco));
        SistemaArchivos fs = new SistemaArchivos(disco);
        GestorArchivosJson.aplicar(d, fs);
        disco.configurarSwap(c.bloquesSwap);

        System.out.println("=== 1. Reglas de permisos (usuario " + usuario + ") ===");
        ModoUsuario U = ModoUsuario.USUARIO;
        ModoUsuario A = ModoUsuario.ADMINISTRADOR;
        esperar("usuario NO elimina archivo del sistema",
                fs.verificarPermiso(TipoOperacion.ELIMINAR, "/sistema/boot.bin", U, usuario) != null);
        esperar("usuario NO actualiza archivo del sistema",
                fs.verificarPermiso(TipoOperacion.ACTUALIZAR, "/sistema/core.dll", U, usuario) != null);
        esperar("usuario SÍ lee archivo público",
                fs.verificarPermiso(TipoOperacion.LEER, "/sistema/config.sys", U, usuario) == null);
        esperar("usuario SÍ lee su archivo privado",
                fs.verificarPermiso(TipoOperacion.LEER, "/usuarios/notas.txt", U, usuario) == null);
        esperar("otro usuario NO lee archivo privado ajeno",
                fs.verificarPermiso(TipoOperacion.LEER, "/usuarios/notas.txt", U, "intruso") != null);
        esperar("usuario NO crea archivos en /sistema",
                fs.verificarPermiso(TipoOperacion.CREAR, "/sistema/nuevo.txt", U, usuario) != null);
        esperar("administrador SÍ elimina archivo del sistema",
                fs.verificarPermiso(TipoOperacion.ELIMINAR, "/sistema/boot.bin", A, "admin") == null);
        esperar("el rechazo trae una explicación en español",
                fs.verificarPermiso(TipoOperacion.ELIMINAR, "/sistema/boot.bin", U, usuario).length() > 10);

        System.out.println("=== 2. Simulación completa como usuario " + usuario + " ===");
        RelojGlobal r = new RelojGlobal(1);
        SistemaDistribuido s = new SistemaDistribuido(1, r);
        LogEventos log = new LogEventos(5000);
        s.setLogEventos(log);
        s.agregarComputador(c.ramComputador, FabricaSistema.crearPolitica(c.politicaCpu, c.quantum));
        s.conectarDisco(disco, fs);
        fs.setModoActual(U);
        fs.setUsuarioActual(usuario);
        for (GestorProcesosJson.DatosProceso dp : c.procesos) {
            Proceso p = FabricaSistema.crearProcesoArchivo(dp, c.ramComputador);
            p.setModoSolicitante(U);
            p.setUsuarioSolicitante(usuario);
            s.enviarProceso(p);
        }
        r.arrancar();
        Kernel k = s.getComputador(1).getKernel();
        long fin = System.currentTimeMillis() + 20000;
        while (k.obtenerTerminados().length < c.procesos.length && System.currentTimeMillis() < fin) {
            Thread.sleep(50);
        }
        r.pausar();
        Thread.sleep(100);
        for (String l : log.getEventos()) {
            if (l.contains("no puede")) {
                System.out.println(l);
            }
        }
        System.out.println("  terminados=" + k.obtenerTerminados().length + "/" + c.procesos.length
                + " rechazos=" + s.getRechazos() + " ciclo=" + r.getCiclo());
        esperar("todos los procesos terminan (los rechazados también)",
                k.obtenerTerminados().length == c.procesos.length);
        System.out.println(fallos == 0 ? "== TODO OK" : "== HAY " + fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }
}