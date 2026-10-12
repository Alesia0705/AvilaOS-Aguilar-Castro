package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Clase encargada de la lectura y escritura de archivos de configuración en formato CSV.
 * Permite guardar y cargar la configuración de la simulación.
 */
public class GestorArchivo {

    /**
     * Guarda la configuración básica del sistema en un archivo CSV.
     * @param rutaArchivo Ruta donde se creará o sobreescribirá el archivo.
     * @param duracionCiclo Duración de cada ciclo en ms.
     * @param numComputadores Cantidad de computadores en el clúster.
     * @param ramPorComp Tamaño de RAM asignado a cada computador.
     * @param latenciaRed Latencia de red en ciclos para accesos remotos.
     * @return true si se guardó con éxito, false si ocurrió un error.
     */
    public static boolean guardarConfiguracion(String rutaArchivo, int duracionCiclo, int numComputadores, int ramPorComp, int latenciaRed) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(rutaArchivo))) {
            writer.println("Parametro,Valor");
            writer.println("duracionCiclo," + duracionCiclo);
            writer.println("numComputadores," + numComputadores);
            writer.println("ramPorComp," + ramPorComp);
            writer.println("latenciaRed," + latenciaRed);
            return true;
        } catch (IOException e) {
            System.err.println("Error al guardar archivo CSV: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lee un archivo CSV de configuración y devuelve sus datos en líneas de texto.
     * @param rutaArchivo Ruta del archivo a leer.
     * @return Arreglo de líneas del archivo o null en caso de error.
     */
    public static String[] cargarConfiguracion(String rutaArchivo) {
        String[] lineas = new String[100]; // Límite arbitrario de líneas
        int contador = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (contador < lineas.length) {
                    lineas[contador] = linea;
                    contador++;
                }
            }
            
            // Recortar arreglo al tamaño real leído
            String[] resultado = new String[contador];
            for (int i = 0; i < contador; i++) {
                resultado[i] = lineas[i];
            }
            return resultado;
        } catch (IOException e) {
            System.err.println("Error al leer archivo CSV: " + e.getMessage());
            return null;
        }
    }
}