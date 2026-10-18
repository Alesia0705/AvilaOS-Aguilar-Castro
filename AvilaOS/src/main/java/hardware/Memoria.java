/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package hardware;

import java.util.concurrent.Semaphore;

/**
 * RAM de un computador. Lleva la cuenta de cuánta memoria hay en total
 * y cuánta está ocupada por procesos. La consultan el kernel (para admitir
 * procesos) y la interfaz (para mostrar el uso), por eso la protege un Semaphore.
 *
 * @author Alesia Castro
 */
public class Memoria {

    private final int capacidadTotal;
    private int usada;
    private final Semaphore mutex;

    public Memoria(int capacidadTotal) {
        this.capacidadTotal = capacidadTotal;
        this.usada = 0;
        this.mutex = new Semaphore(1);
    }
        /**
     * Indica si hay suficiente memoria libre para la cantidad pedida.
     * Solo consulta: no reserva nada.
     */
    public boolean hayEspacio(int cantidad) {
        boolean cabe = false;
        try {
            mutex.acquire();
            try {
                cabe = (capacidadTotal - usada) >= cantidad;
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return cabe;
    }
}