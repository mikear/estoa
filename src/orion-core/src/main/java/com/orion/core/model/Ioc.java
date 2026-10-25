package com.orion.core.model;

/**
 * Indicador de compromiso (IOC) con nivel de confianza y estado de vida.
 * Un IOC es una vista operativa sobre una entidad del grafo.
 */
public class Ioc {

    public enum IocStatus { NEW, ACTIVE, MITIGATED, EXPIRED, UNKNOWN }

    private final String entityKey;
    private final EntityType type;
    private final String value;
    private double confidence;   // 0-100
    private int sightings;
    private IocStatus status = IocStatus.NEW;

    public Ioc(EntityType type, String value, double confidence) {
        this.type = type;
        this.value = value;
        this.confidence = confidence;
        this.entityKey = type.name() + ":" + value.toLowerCase();
    }

    public String getEntityKey() { return entityKey; }
    public EntityType getType() { return type; }
    public String getValue() { return value; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double c) { this.confidence = Math.max(0, Math.min(100, c)); }
    public int getSightings() { return sightings; }
    public void incrementSightings() { sightings++; }
    public IocStatus getStatus() { return status; }
    public void setStatus(IocStatus status) { this.status = status == null ? IocStatus.NEW : status; }

    public String confidenceLabel() {
        if (confidence >= 80) return "Alta";
        if (confidence >= 50) return "Media";
        if (confidence >= 25) return "Baja";
        return "Muy baja";
    }
}
