/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package hardware;

import java.util.concurrent.Semaphore;
import modelo.Proceso;
import modelo.enums.ModoCpu;

/**
 * Procesador de un computador. Ejecuta una instrucción por ciclo del proceso
 * que el kernel le asigna y cuenta los ciclos que pasa en cada modo
 * (usuario, sistema operativo, ocioso) para calcular su utilización.
 * No decide qué proceso corre: eso es trabajo del kernel.
 *
 * @author Alesia Castro
 */
public class Cpu {

    private final int idComputador;
    private Proceso procesoActual;
    private ModoCpu modo;

    private int ciclosUsuario;
    private int ciclosSistema;
    private int ciclosOcioso;

    private final Semaphore mutex;

    public Cpu(int idComputador) {
        this.idComputador = idComputador;
        this.procesoActual = null;
        this.modo = ModoCpu.OCIOSO;
        this.ciclosUsuario = 0;
        this.ciclosSistema = 0;
        this.ciclosOcioso = 0;
        this.mutex = new Semaphore(1);
    }
        /**
     * Pone un proceso en la CPU y la deja en modo usuario. Si la CPU ya
     * tiene un proceso, no hace nada y devuelve false.
     */
    public boolean asignar(Proceso proceso) {
        boolean asignado = false;
        try {
            mutex.acquire();
            try {
                if (procesoActual == null && proceso != null) {
                    procesoActual = proceso;
                    modo = ModoCpu.USUARIO;
                    asignado = true;
                }
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return asignado;
    }
}