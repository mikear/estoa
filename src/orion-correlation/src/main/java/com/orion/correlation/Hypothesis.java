package com.orion.correlation;

import java.util.List;

/**
 * Hipotesis de investigacion generada automaticamente a partir de los
 * hallazgos. El analista la confirma, la rechaza o la mantiene pendiente.
 */
public class Hypothesis {

    public enum Status { CONFIRMED("Confirmada"), PENDING("Por validar"), REJECTED("Descartada");

        private final String label;
        Status(String label) { this.label = label; }
        public String label() { return label; }
    }

    private final String statement;
    private final double confidence;
    private final String ruleId;
    private final List<String> supportingKeys;
    private Status status;

    public Hypothesis(String statement, double confidence, String ruleId, List<String> supportingKeys) {
        this.statement = statement;
        this.confidence = Math.max(0, Math.min(100, confidence));
        this.ruleId = ruleId;
        this.supportingKeys = List.copyOf(supportingKeys);
        this.status = confidence >= 85 ? Status.CONFIRMED : Status.PENDING;
    }

    public String getStatement() { return statement; }
    public double getConfidence() { return confidence; }
    public String getRuleId() { return ruleId; }
    public List<String> getSupportingKeys() { return supportingKeys; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status == null ? Status.PENDING : status; }
}
