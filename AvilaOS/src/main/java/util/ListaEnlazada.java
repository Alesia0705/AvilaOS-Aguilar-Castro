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
        /**
     * Saca el primer elemento de la lista y lo devuelve.
     * Devuelve null si la lista está vacía.
     */
    public T quitarPrimero() {
        if (cabeza == null) {
            return null;
        }
        T dato = cabeza.getDato();
        cabeza = cabeza.getSiguiente();
        if (cabeza == null) {
            cola = null;
        }
        tamano--;
        return dato;
    }
    
        /**
     * Devuelve el elemento de la posición indicada sin sacarlo de la lista.
     * La posición 0 es el primer elemento. Devuelve null si el índice no es válido.
     */
    public T obtener(int indice) {
        if (indice < 0 || indice >= tamano) {
            return null;
        }
        Nodo<T> actual = cabeza;
        for (int i = 0; i < indice; i++) {
            actual = actual.getSiguiente();
        }
        return actual.getDato();
    }
    
        /**
     * Saca de la lista el elemento indicado, esté donde esté.
     * Devuelve true si lo encontró y false si no estaba.
     */
    public boolean quitar(T dato) {
        Nodo<T> anterior = null;
        Nodo<T> actual = cabeza;
        while (actual != null) {
            if (actual.getDato() == dato) {
                if (anterior == null) {
                    cabeza = actual.getSiguiente();
                } else {
                    anterior.setSiguiente(actual.getSiguiente());
                }
                if (actual == cola) {
                    cola = anterior;
                }
                tamano--;
                return true;
            }
            anterior = actual;
            actual = actual.getSiguiente();
        }
        return false;
    }
    
    public int getTamano() { return tamano; }

    public boolean estaVacia() { return tamano == 0; }
}