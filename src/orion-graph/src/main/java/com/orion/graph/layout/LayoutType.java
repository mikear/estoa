package com.orion.graph.layout;

/**
 * Los 8 layouts de visualizacion soportados por Estoa.
 */
public enum LayoutType {
    FORCE_DIRECTED("Fuerza dirigida (Barnes-Hut)", "Repulsión + muelles con aproximación quadtree"),
    ORGANIC("Orgánico", "Dirigido por fuerzas con mayor separación y relajación"),
    CIRCULAR("Circular", "Nodos sobre una circunferencia"),
    GRID("Rejilla", "Distribución uniforme en rejilla"),
    HIERARCHICAL("Jerárquico", "Niveles BFS desde los nodos de mayor grado"),
    RADIAL("Radial", "Anillos concéntricos alrededor del nodo raíz"),
    CLUSTER("Clústeres", "Componentes conexas agrupadas en islas"),
    RANDOM("Aleatorio", "Disposición aleatoria con margen");

    private final String label;
    private final String description;

    LayoutType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }
    public String description() { return description; }

    public Layout create() {
        return switch (this) {
            case FORCE_DIRECTED -> new ForceDirectedLayout();
            case ORGANIC -> new ForceDirectedLayout(1.55, 0.92, 0.035);
            case CIRCULAR -> StandardLayouts.circular();
            case GRID -> StandardLayouts.grid();
            case HIERARCHICAL -> StandardLayouts.hierarchical();
            case RADIAL -> StandardLayouts.radial();
            case CLUSTER -> StandardLayouts.cluster();
            case RANDOM -> StandardLayouts.random();
        };
    }
}
