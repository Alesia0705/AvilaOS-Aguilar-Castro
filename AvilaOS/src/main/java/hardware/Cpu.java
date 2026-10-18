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
        /**
     * Saca de la CPU el proceso que tenía y lo devuelve (null si estaba
     * libre). La CPU queda ociosa.
     */
    public Proceso retirar() {
        Proceso retirado = null;
        try {
            mutex.acquire();
            try {
                retirado = procesoActual;
                procesoActual = null;
                modo = ModoCpu.OCIOSO;
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return retirado;
    }
        /**
     * Avanza un ciclo de reloj. En modo usuario ejecuta una instrucción del
     * proceso actual; en modo sistema operativo o sin proceso, el ciclo se
     * cuenta como de sistema o como ocioso. No decide nada: si el proceso
     * termina, el kernel se da cuenta y lo retira.
     */
    public void ejecutarCiclo() {
        try {
            mutex.acquire();
            try {
                if (modo == ModoCpu.SISTEMA_OPERATIVO) {
                    ciclosSistema++;
                } else if (procesoActual != null && procesoActual.getTiempoRestante() > 0) {
                    procesoActual.ejecutarInstruccion();
                    ciclosUsuario++;
                } else {
                    ciclosOcioso++;
                }
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
        /**
     * Cambia el modo de la CPU. Lo usa el kernel: SISTEMA_OPERATIVO mientras
     * hace su trabajo (planificar, cambiar de proceso) y USUARIO al volver
     * a correr un proceso.
     */
    public void setModo(ModoCpu nuevoModo) {
        try {
            mutex.acquire();
            try {
                modo = nuevoModo;
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}