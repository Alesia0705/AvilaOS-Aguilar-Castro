package kernel;

import disco.*; import filesystem.*; import util.*; import modelo.*; import gui.FabricaSistema;
import modelo.enums.*; import reloj.RelojGlobal; import sistema.SistemaDistribuido;
/**
 * Prueba del planificador de mediano plazo con el reloj real (sin interfaz).
 * Uso (desde la carpeta AvilaOS): PruebaMedianoPlazo procesos_swap.json
 * Con RAM 48 y procesos de 16 solo caben 3: los demas obligan a suspender bloqueados.
 * Al final deben haber terminado todos, con RAM usada 0 y todo el swap libre.
 */
public class PruebaMedianoPlazo { public static void main(String[] a) throws Exception {
  GestorProcesosJson.Configuracion c = GestorProcesosJson.cargar("../json/" + (a.length > 0 ? a[0] : "procesos_swap.json"));
  GestorArchivosJson.DatosSistemaArchivos d = GestorArchivosJson.cargar("../json/sistema_archivos_enunciado.json");
  Disco disco = new Disco(d.totalBloques,0,d.cabezalInicial,FabricaSistema.crearPoliticaDisco(c.politicaDisco));
  SistemaArchivos fs = new SistemaArchivos(disco); GestorArchivosJson.aplicar(d, fs); disco.configurarSwap(c.bloquesSwap);
  RelojGlobal r = new RelojGlobal(1); SistemaDistribuido s = new SistemaDistribuido(1,r);
  LogEventos log = new LogEventos(5000); s.setLogEventos(log);
  s.agregarComputador(c.ramComputador, FabricaSistema.crearPolitica(c.politicaCpu,c.quantum)); s.conectarDisco(disco, fs);
  for (GestorProcesosJson.DatosProceso p : c.procesos) s.enviarProceso(FabricaSistema.crearProcesoArchivo(p,c.ramComputador));
  r.arrancar();
  Kernel k = s.getComputador(1).getKernel();
  long fin = System.currentTimeMillis()+20000;
  while (k.obtenerTerminados().length < c.procesos.length && System.currentTimeMillis()<fin) Thread.sleep(50);
  r.pausar(); Thread.sleep(100);
  for (String l : log.getEventos()) if (true || l.contains("Mediano") || l.contains("suspend") || l.contains("reactiv") || l.contains("SUSPENDER")|| l.contains("REACTIVAR") || l.contains("swap")) System.out.println(l);
  System.out.println("== ciclo=" + r.getCiclo() + " terminados=" + k.obtenerTerminados().length + "/" + c.procesos.length
     + " nuevos=" + k.obtenerNuevos().length + " listos=" + k.obtenerListos().length + " bloq=" + k.obtenerBloqueados().length
     + " listosSusp=" + k.obtenerListosSuspendidos().length + " bloqSusp=" + k.obtenerBloqueadosSuspendidos().length
     + " susp=" + k.getSuspensiones() + " react=" + k.getReactivaciones() + " RAMusada=" + k.getMemoria().getUsada()
     + " swapLibre=" + disco.contarBloquesLibres(true) + " desplazamiento=" + disco.getDesplazamientoTotal());
  System.exit(0);
}}