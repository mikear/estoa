package com.orion.graph.layout;

import com.orion.graph.OrionGraph;

/**
 * Interfaz de los algoritmos de disposicion. Los layouts pueden ejecutarse
 * de golpe ({@link #apply}) o de forma incremental ({@link #step}) para
 * animar la transicion en el canvas.
 */
public interface Layout {

    /** Ejecuta el layout completo. */
    void apply(OrionGraph graph, double width, double height);

    /**
     * Avanza un tick del algoritmo. Devuelve true si el layout aun no se ha
     * estabilizado y conviene seguir animando.
     */
    default boolean step(OrionGraph graph, double width, double height) {
        apply(graph, width, height);
        return false;
    }

    /** Distancia ideal entre nodos para la densidad dada. */
    static double idealDistance(OrionGraph g, double w, double h) {
        int n = Math.max(1, g.nodeCount());
        return Math.min(220, Math.max(60, Math.sqrt((w * h) / (n * 1.6))));
    }
}
