/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util;

/**
 * Lista enlazada genérica propia. Reemplaza a las colecciones de java.util,
 * que el enunciado prohíbe. No es segura entre hilos: quien la comparta
 * debe protegerla con un Semaphore.
 *
 * @author Alesia Castro
 * @param <T> tipo de dato que almacena la lista
 */
public class ListaEnlazada<T> {

    private Nodo<T> cabeza;
    private Nodo<T> cola;
    private int tamano;

    public ListaEnlazada() {
        this.cabeza = null;
        this.cola = null;
        this.tamano = 0;
    }

    /**
     * Agrega un elemento al final de la lista.
     */
    public void agregarAlFinal(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        if (cabeza == null) {
            cabeza = nuevo;
            cola = nuevo;
        } else {
            cola.setSiguiente(nuevo);
            cola = nuevo;
        }
        tamano++;
    }

    public int getTamano() { return tamano; }

    public boolean estaVacia() { return tamano == 0; }
}