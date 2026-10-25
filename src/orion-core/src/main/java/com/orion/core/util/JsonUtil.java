package com.orion.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.function.Consumer;

/** ObjectMapper compartido y utilidades JSON seguras. */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonUtil() {}

    public static ObjectMapper mapper() { return MAPPER; }

    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    public static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static String text(JsonNode node, String field) {
        if (node == null) return "";
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }

    public static int intVal(JsonNode node, String field, int def) {
        if (node == null) return def;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? def : v.asInt(def);
    }

    /** Ejecuta con y captura cualquier excepcion en un mensaje de log. */
    public static void quietly(Runnable r, Consumer<String> onError) {
        try {
            r.run();
        } catch (Exception e) {
            if (onError != null) onError.accept(e.getMessage());
        }
    }
}
