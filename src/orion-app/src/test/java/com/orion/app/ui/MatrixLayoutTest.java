package com.orion.app.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatrixLayoutTest {

    @Test
    void ajustaCeldasAlEspacioDisponible() {
        assertEquals(240, MatrixLayout.cell(1, 1000, 800, 100, 120, 28, 34));
        assertEquals(64.6, MatrixLayout.cell(10, 1000, 800, 100, 120, 28, 34), 0.001);
        assertEquals(MatrixLayout.MIN_CELL,
                MatrixLayout.cell(80, 1000, 800, 150, 150, 28, 34));
    }

    @Test
    void mantieneMinimoParaNoDesaparecer() {
        assertEquals(MatrixLayout.MIN_CELL,
                MatrixLayout.cell(10, 200, 100, 150, 150, 28, 34));
    }
}
