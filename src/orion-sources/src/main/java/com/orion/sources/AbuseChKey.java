package com.orion.sources;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Clave compartida de las APIs de abuse.ch. Una sola clave de
 * auth.abuse.ch habilita URLhaus, ThreatFox y MalwareBazaar; se acepta
 * guardada como {@code abusech} o bajo el identificador de cada conector.
 */
final class AbuseChKey {

    static final String SERVICE = "abusech";
    static final String HEADER = "Auth-Key";

    private AbuseChKey() {}

    /** Primera clave configurada entre el servicio compartido y los conectores. */
    static String resolve(String... connectors) {
        ApiKeyManager manager = SourceRegistry.getInstance().apiKeyManager();
        String shared = manager.getKey(SERVICE);
        if (shared != null && !shared.isBlank()) return shared.trim();
        for (String id : connectors) {
            String key = manager.getKey(id);
            if (key != null && !key.isBlank()) return key.trim();
        }
        return null;
    }

    static Map<String, String> headers(String key) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put(HEADER, key);
        return h;
    }

    /** Mensaje unico y accionable cuando falta la clave. */
    static String missingMessage() {
        return "Sin clave de abuse.ch: registre una clave gratuita de auth.abuse.ch "
                + "como '" + SERVICE + "' (Fuentes > Gestión de claves API).";
    }
}
