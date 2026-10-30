/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package modelo.enums;

/**
 * Motivo por el cual un proceso está en estado BLOQUEADO.
 * @author Alesia Castro
 */
public enum MotivoBloqueo {
    NINGUNO,
    ESPERA_ES,
    SEM_BUFFER_LLENO,
    SEM_BUFFER_VACIO,
    SEM_MUTEX,
    LATENCIA_RED
}
