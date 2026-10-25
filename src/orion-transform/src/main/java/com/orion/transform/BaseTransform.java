package com.orion.transform;

import com.orion.core.model.EntityType;

import java.util.Set;

/** Clase base compacta para definir transformaciones. */
public abstract class BaseTransform implements Transform {

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final Set<EntityType> inputs;
    private final Set<EntityType> outputs;
    private final boolean requiresApiKey;

    protected BaseTransform(String id, String name, String description, Category category,
                            Set<EntityType> inputs, Set<EntityType> outputs, boolean requiresApiKey) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.inputs = inputs;
        this.outputs = outputs;
        this.requiresApiKey = requiresApiKey;
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }
    @Override public String description() { return description; }
    @Override public Category category() { return category; }
    @Override public Set<EntityType> inputs() { return inputs; }
    @Override public Set<EntityType> outputs() { return outputs; }
    @Override public boolean requiresApiKey() { return requiresApiKey; }
}
