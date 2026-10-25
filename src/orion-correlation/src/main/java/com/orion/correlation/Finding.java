package com.orion.correlation;

import java.util.List;

/** Hallazgo producido por una regla de correlacion. */
public record Finding(String ruleId, String ruleName, String description,
                      double confidence, List<String> entityKeys) {

    public String confidenceLabel() {
        if (confidence >= 80) return "Alta";
        if (confidence >= 50) return "Media";
        return "Baja";
    }
}
