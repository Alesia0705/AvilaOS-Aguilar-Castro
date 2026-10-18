/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package reloj;

import java.util.concurrent.Semaphore;

/**
 * Reloj global del sistema. En cada ciclo avisa a todos los escuchas
 * registrados (los computadores) y espera a que todos terminen antes de
 * pasar al siguiente, con una barrera hecha con semáforos. Así todos los
 * computadores avanzan sincronizados aunque cada uno corra en su hilo.
 *
 * @author Alesia Castro
 */
public class RelojGlobal implements Runnable {

    private volatile long ciclo;
    private volatile int duracionCicloMs;
    private volatile boolean encendido;

    private final Semaphore finCiclo;
    private final Semaphore mutex;

    public RelojGlobal(int duracionCicloMs) {
        this.ciclo = 0;
        this.duracionCicloMs = duracionCicloMs;
        this.encendido = false;
        this.finCiclo = new Semaphore(0);
        this.mutex = new Semaphore(1);
    }

    @Override
    public void run() {
        // el bucle del reloj se escribe en un paso posterior
    }
}