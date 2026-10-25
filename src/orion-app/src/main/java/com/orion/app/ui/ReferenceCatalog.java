package com.orion.app.ui;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Referencias externas derivadas de los atributos y fuentes de una entidad. */
public final class ReferenceCatalog {

    private static final String[] URL_ATTRIBUTES = {
            "finalUrl", "url", "link", "sourceUrl", "reference", "references", "faviconUrl"
    };

    private ReferenceCatalog() {}

    public static List<String> of(OrionEntity entity) {
        if (entity == null) return List.of();
        Set<String> refs = new LinkedHashSet<>();
        for (String key : URL_ATTRIBUTES) {
            addUrls(refs, entity.attrStr(key, ""));
        }

        String sources = String.join(",", entity.getSources()).toLowerCase(Locale.ROOT);
        switch (entity.getType()) {
            case CVE -> {
                addUrl(refs, "https://nvd.nist.gov/vuln/detail/" + entity.getValue());
                if (sources.contains("cisa-kev")) {
                    addUrl(refs, "https://www.cisa.gov/known-exploited-vulnerabilities-catalog");
                }
            }
            case ATTCK_TECHNIQUE -> addUrl(refs,
                    "https://attack.mitre.org/techniques/" + entity.getValue().replace('.', '/') + "/");
            case ATTCK_TACTIC -> addUrl(refs,
                    "https://attack.mitre.org/tactics/" + entity.getValue() + "/");
            case APT, THREAT_ACTOR -> {
                if (entity.getValue().matches("G\\d+")) {
                    addUrl(refs, "https://attack.mitre.org/groups/" + entity.getValue() + "/");
                }
            }
            case DOMAIN -> {
                if (sources.contains("crtsh")) addUrl(refs, "https://crt.sh/?q=%25." + entity.getValue());
                if (sources.contains("urlscan")) addUrl(refs, "https://urlscan.io/domain/" + entity.getValue());
            }
            default -> { }
        }

        String scanId = entity.attrStr("scanId", "");
        if (!scanId.isBlank()) addUrl(refs, "https://urlscan.io/result/" + scanId + "/");
        return refs.stream().limit(12).toList();
    }

    private static void addUrls(Set<String> refs, String raw) {
        if (raw == null || raw.isBlank()) return;
        for (String token : raw.split("[,\\s]+")) {
            addUrl(refs, token.replace("[", "").replace("]", "").replace("\"", ""));
        }
    }

    private static void addUrl(Set<String> refs, String url) {
        if (url == null) return;
        String clean = url.trim();
        if (clean.startsWith("http://") || clean.startsWith("https://")) refs.add(clean);
    }
}
