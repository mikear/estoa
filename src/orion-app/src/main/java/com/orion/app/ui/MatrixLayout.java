package com.orion.app.ui;

/** Calculos de encaje de la matriz de adyacencia, sin dependencias de JavaFX. */
final class MatrixLayout {

    static final double MIN_CELL = 8;
    static final double MAX_CELL = 240;

    private MatrixLayout() {
    }

    static double cell(int count, double availableWidth, double availableHeight,
                       double left, double top, double right, double bottom) {
        if (count <= 0) return MIN_CELL;
        double width = Math.max(1, availableWidth - left - right);
        double height = Math.max(1, availableHeight - top - bottom);
        double fitted = Math.min(width / count, height / count);
        return Math.max(MIN_CELL, Math.min(MAX_CELL, fitted));
    }
}
