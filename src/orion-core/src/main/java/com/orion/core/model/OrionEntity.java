package com.orion.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entidad de investigacion. Un nodo del grafo siempre esta respaldado por una
 * OrionEntity; sus atributos son dinamicos y dependen de las fuentes que la
 * han enriquecido (WHOIS, DNS, geolocalizacion, reputacion...).
 */
public class OrionEntity {

    private final String id;
    private final EntityType type;
    private String value;
    private String label;
    private double riskScore;
    private Tlp tlp = Tlp.CLEAR;
    private String source = "manual";
    private Instant firstSeen = Instant.now();
    private Instant lastSeen = Instant.now();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final List<String> sources = new ArrayList<>();

    public OrionEntity(EntityType type, String value) {
        this.id = UUID.randomUUID().toString();
        this.type = type == null ? EntityType.INDICATOR : type;
        this.value = value == null ? "" : value.trim();
        this.label = this.value;
    }

    public static OrionEntity of(EntityType type, String value) {
        return new OrionEntity(type, value);
    }

    /** Identidad canonica de la entidad: tipo + valor normalizado. */
    public String key() {
        return type.name() + ":" + value.toLowerCase();
    }

    public OrionEntity attr(String name, Object val) {
        attributes.put(name, val);
        touch();
        return this;
    }

    public Object attr(String name) { return attributes.get(name); }
    public String attrStr(String name, String def) {
        Object v = attributes.get(name);
        return v == null ? def : String.valueOf(v);
    }

    public void addSource(String sourceId) {
        if (sourceId != null && !sources.contains(sourceId)) sources.add(sourceId);
    }

    private void touch() { this.lastSeen = Instant.now(); }

    public String getId() { return id; }
    public EntityType getType() { return type; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; this.label = value; touch(); }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = Math.max(0, Math.min(100, riskScore)); touch(); }
    public Tlp getTlp() { return tlp; }
    public void setTlp(Tlp tlp) { this.tlp = tlp == null ? Tlp.CLEAR : tlp; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getFirstSeen() { return firstSeen; }
    public Instant getLastSeen() { return lastSeen; }
    public Map<String, Object> getAttributes() { return attributes; }
    public List<String> getSources() { return sources; }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type.name());
        m.put("value", value);
        m.put("label", label);
        m.put("risk", riskScore);
        m.put("tlp", tlp.name());
        m.put("attributes", new LinkedHashMap<>(attributes));
        m.put("sources", List.copyOf(sources));
        return m;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OrionEntity e && key().equals(e.key());
    }

    @Override
    public int hashCode() { return key().hashCode(); }

    @Override
    public String toString() { return type.label() + " [" + value + "]"; }
}
