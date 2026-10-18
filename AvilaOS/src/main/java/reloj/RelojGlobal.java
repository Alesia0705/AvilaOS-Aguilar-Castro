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
     * Registra un escucha (por ejemplo, un computador). Se le crea un hilo
     * propio que queda esperando el primer ciclo.
     */
    public void registrar(EscuchaReloj escucha) {
        Participante participante = new Participante(escucha);
        try {
            mutex.acquire();
            try {
                participantes.agregarAlFinal(participante);
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        Thread hilo = new Thread(participante);
        hilo.setDaemon(true);
        hilo.start();
    }
        /**
     * Da un ciclo completo: avanza el contador, avisa a todos los
     * participantes que empiecen y espera a que todos terminen.
     */
    private void darCiclo() {
        try {
            int cantidad = 0;
            mutex.acquire();
            try {
                ciclo++;
                cantidad = participantes.getTamano();
                for (int i = 0; i < cantidad; i++) {
                    participantes.obtener(i).inicioCiclo.release();
                }
            } finally {
                mutex.release();
            }
            for (int i = 0; i < cantidad; i++) {
                finCiclo.acquire();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
