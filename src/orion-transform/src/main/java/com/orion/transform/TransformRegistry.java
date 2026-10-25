package com.orion.transform;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registro de transformaciones. Indexa por identificador y por tipo de
 * entrada para poblar los menus contextuales del grafo.
 */
public class TransformRegistry {

    private static final TransformRegistry INSTANCE = createInstance();

    private static TransformRegistry createInstance() { return new TransformRegistry(); }

    public static TransformRegistry getInstance() { return INSTANCE; }

    /** Registro adicional (pruebas o contextos aislados). */
    public TransformRegistry() {}

    private final Map<String, Transform> transforms = new LinkedHashMap<>();

    public void register(Transform t) {
        if (t != null) transforms.put(t.id(), t);
    }

    public void registerAll(Iterable<? extends Transform> ts) {
        ts.forEach(this::register);
    }

    public Optional<Transform> byId(String id) {
        return Optional.ofNullable(transforms.get(id));
    }

    public List<Transform> forInput(EntityType type) {
        List<Transform> out = new ArrayList<>();
        for (Transform t : transforms.values()) {
            if (t.inputs().contains(type)) out.add(t);
        }
        return out;
    }

    /** Ejecuta una transformacion midiendo su duracion. */
    public TransformResult run(String transformId, OrionEntity input, TransformContext ctx) {
        Transform t = transforms.get(transformId);
        if (t == null) {
            return new TransformResult(false, transformId, "Transformación desconocida: " + transformId, 0);
        }
        long start = System.currentTimeMillis();
        try {
            TransformResult r = t.apply(input, ctx);
            return r;
        } catch (Exception e) {
            return new TransformResult(false, transformId, "Fallo: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    public List<Transform> all() { return new ArrayList<>(transforms.values()); }

    public int size() { return transforms.size(); }
}
