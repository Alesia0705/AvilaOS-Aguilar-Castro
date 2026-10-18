/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package reloj;

import java.util.concurrent.Semaphore;
import util.ListaEnlazada;

/**
 * Reloj global del sistema. En cada ciclo avisa a todos los escuchas
 * registrados (los computadores) y espera a que todos terminen antes de pasar
 * al siguiente, con una barrera hecha con semáforos. Así todos los computadores
 * avanzan sincronizados aunque cada uno corra en su hilo.
 *
 * @author Alesia Castro
 */
public class RelojGlobal implements Runnable {

    private volatile long ciclo;
    private volatile int duracionCicloMs;
    private volatile boolean encendido;

    private final Semaphore finCiclo;
    private final Semaphore mutex;
    private final ListaEnlazada<Participante> participantes;

    public RelojGlobal(int duracionCicloMs) {
        this.ciclo = 0;
        this.duracionCicloMs = duracionCicloMs;
        this.encendido = false;
        this.finCiclo = new Semaphore(0);
        this.mutex = new Semaphore(1);
        this.participantes = new ListaEnlazada<>();
    }

    @Override
    public void run() {
        // el bucle del reloj se escribe en un paso posterior
    }

    /**
     * Hilo asociado a un escucha. Espera su turno al comienzo de cada ciclo,
     * ejecuta el onTick del escucha y avisa al reloj que terminó.
     */
    private class Participante implements Runnable {

        private final EscuchaReloj escucha;
        private final Semaphore inicioCiclo;

        Participante(EscuchaReloj escucha) {
            this.escucha = escucha;
            this.inicioCiclo = new Semaphore(0);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    inicioCiclo.acquire();
                    try {
                        escucha.onTick(ciclo);
                    } finally {
                        finCiclo.release();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
