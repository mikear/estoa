package com.orion.app.ui;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RiskScore;
import com.orion.core.model.Tlp;
import com.orion.graph.GraphNode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public final class EntityViewSupport {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SHORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private EntityViewSupport() {
    }

    public static boolean matches(GraphNode node, String query, EntityType type,
                                  Tlp tlp, RiskScore.Level level) {
        if (node == null) return false;
        OrionEntity entity = node.getEntity();
        if (type != null && entity.getType() != type) return false;
        if (tlp != null && entity.getTlp() != tlp) return false;
        if (level != null && levelOf(entity.getRiskScore()) != level) return false;
        if (query == null || query.isBlank()) return true;
        return searchText(node).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    public static String searchText(GraphNode node) {
        OrionEntity entity = node.getEntity();
        StringBuilder text = new StringBuilder(160);
        text.append(entity.getValue()).append(' ')
                .append(entity.getType().label()).append(' ')
                .append(entity.getType().name()).append(' ')
                .append(entity.getSource());
        for (String source : entity.getSources()) {
            text.append(' ').append(source);
        }
        for (Map.Entry<String, Object> attribute : entity.getAttributes().entrySet()) {
            text.append(' ').append(attribute.getKey()).append(' ').append(attribute.getValue());
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    public static RiskScore.Level levelOf(double risk) {
        if (risk >= 80) return RiskScore.Level.CRITICAL;
        if (risk >= 60) return RiskScore.Level.HIGH;
        if (risk >= 35) return RiskScore.Level.MEDIUM;
        return RiskScore.Level.LOW;
    }

    public static String riskStyleClass(double risk) {
        return switch (levelOf(risk)) {
            case CRITICAL -> "risk-critical";
            case HIGH -> "risk-high";
            case MEDIUM -> "risk-medium";
            case LOW -> "risk-low";
        };
    }

    public static String sources(OrionEntity entity) {
        if (!entity.getSources().isEmpty()) return String.join(", ", entity.getSources());
        return entity.getSource();
    }

    public static String formatTime(Instant instant) {
        return instant == null ? "" : TIME_FORMAT.format(instant);
    }

    public static String formatShortTime(Instant instant) {
        return instant == null ? "" : SHORT_TIME_FORMAT.format(instant);
    }
}
