package filesystem;

import disco.Disco;
import disco.PoliticaFIFO;
import modelo.Proceso;
import modelo.enums.ModoUsuario;
import modelo.enums.Permiso;
import modelo.enums.TipoOperacion;
import modelo.enums.TipoProceso;
import planificacion.PoliticaFCFS;
import reloj.RelojGlobal;
import sistema.SistemaDistribuido;
import util.GestorArchivosJson;

public class PruebaFilesystemJson {

    public static void main(String[] args) throws Exception {
        Disco disco = new Disco(500, 100, 50, new PoliticaFIFO());
        SistemaArchivos fs = new SistemaArchivos(disco);

        // 1. Prueba Issue #60: Carga del JSON inicial del enunciado
        GestorArchivosJson.cargarEn("../json/sistema_archivos_ejemplo.json", fs);
        System.out.println("1. Archivos cargados desde JSON: " + fs.obtenerTodosLosArchivos().length + " (esperado: 8)");
        System.out.println("   Bloques libres tras carga: " + disco.contarBloquesLibres(false) + " (esperado: 362)");

        // 2. Prueba Issue #56: CRUD + Permisos (USUARIO vs ADMINISTRADOR)
        fs.setUsuarioActual("usuario1");
        fs.setModoActual(ModoUsuario.USUARIO);

        fs.crearDirectorio("/usuarios/secreto", Permiso.PRIVADO);
        NodoArchivo archivoPrivado = fs.crearArchivo("/usuarios/secreto/clave.txt", 4, Permiso.PRIVADO);
        System.out.println("2. Creado archivo privado en bloque inicial: " + archivoPrivado.getPrimerBloque());

        // Otro usuario en modo USUARIO intenta leer o borrar el archivo privado -> debe fallar
        fs.setUsuarioActual("invitado");
        NodoArchivo lecturaDenegada = fs.leerArchivo("/usuarios/secreto/clave.txt", ModoUsuario.USUARIO, "invitado");
        boolean borradoDenegado = fs.eliminar("/usuarios/secreto/clave.txt");
        System.out.println("   Lectura por invitado (debe ser null): " + lecturaDenegada);
        System.out.println("   Borrado por invitado (debe ser false): " + borradoDenegado);

        // ADMINISTRADOR puede leer y actualizar incluso archivos privados de otros
        fs.setModoActual(ModoUsuario.ADMINISTRADOR);
        boolean actualizado = fs.actualizar("/usuarios/secreto/clave.txt", "clave_v2.txt", 6, null,
                ModoUsuario.ADMINISTRADOR, "admin");
        System.out.println("   Update por admin (debe ser true): " + actualizado);

        // 3. Prueba Issue #60: Guardado a JSON y recarga en un disco limpio
        String rutaSalida = "../json/salida_prueba_fs.json";
        GestorArchivosJson.guardar(rutaSalida, fs);

        Disco disco2 = new Disco(500, 100, 50, new PoliticaFIFO());
        SistemaArchivos fs2 = new SistemaArchivos(disco2);
        GestorArchivosJson.cargarEn(rutaSalida, fs2);
        System.out.println("3. Archivos recargados desde JSON guardado: " + fs2.obtenerTodosLosArchivos().length + " (esperado: 9)");

        // 4. Prueba Issue #55: Conexion con SistemaDistribuido.solicitarDisco
        RelojGlobal reloj = new RelojGlobal(10);
        SistemaDistribuido sistema = new SistemaDistribuido(0, reloj);
        sistema.agregarComputador(1024, new PoliticaFCFS());
        sistema.conectarDisco(disco, fs);

        Proceso p = new Proceso("Proc_Test", 4, 16, 1, 100, TipoProceso.ES_ARCHIVO, 1);
        p.setOperacion(TipoOperacion.LEER);
        p.setRutaArchivo("/sistema/boot.bin");

        boolean aceptada = sistema.solicitarDisco(p, 1);
        System.out.println("4. Solicitud encolada en SistemaDistribuido (debe ser true): " + aceptada);
        System.out.println("   Solicitudes pendientes en disco: " + disco.getCantidadPendientes() + " (esperado: 1)");

        // 5. Limpieza de bloques al eliminar directorio completo
        fs.eliminar("/usuarios/secreto");
        System.out.println("5. Bloques libres tras eliminar /usuarios/secreto: " + disco.contarBloquesLibres(false) + " (esperado: 362)");
    }
}