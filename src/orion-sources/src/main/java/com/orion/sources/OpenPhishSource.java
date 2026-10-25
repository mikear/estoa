package com.orion.sources;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OpenPhish: feed comunitario de URLs de phishing activas. La membresia se
 * comprueba contra el feed descargado con cache de 1 hora.
 */
public class OpenPhishSource implements SourceConnector {

    public static final String ID = "openphish";

    private final TtlCache<Set<String>> feed = new TtlCache<>(Duration.ofHours(1));

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "OpenPhish (fuente comunitaria)"; }

    @Override
    public String description() { return "Detección de URLs de phishing contra la fuente pública con caché horaria."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.URL_REPUTATION); }

    @Override
    public SourceResult query(SourceQuery q) {
        String url = q.value().trim();
        if (url.isEmpty()) return SourceResult.error(id(), "URL vacia");
        try {
            Set<String> urls = feed.get(this::download);
            boolean exact = urls.contains(url);
            boolean hostHit = !exact && urls.stream().anyMatch(u -> u.contains(hostOf(url)));
            if (exact || hostHit) {
                SourceResult r = SourceResult.ok(id(), "URL/equipo catalogado como phishing por OpenPhish");
                OrionEntity e = OrionEntity.of(EntityType.URL, url);
                e.addSource(id());
                e.attr("phishing", true).attr("checkedAt", java.time.Instant.now().toString());
                e.setRiskScore(80);
                r.entity(e);
                r.raw("phishing", true);
                return r;
            }
            SourceResult r = SourceResult.ok(id(), "Sin coincidencia en OpenPhish");
            r.raw("phishing", false).raw("feedSize", urls.size());
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "OpenPhish no disponible: " + e.getMessage());
        }
    }

    private String hostOf(String url) {
        return com.orion.core.util.NetUtil.normalizeDomain(url);
    }

    private Set<String> download() {
        try {
            String body = Http.get("https://openphish.com/feed.txt");
            return body.lines().filter(l -> !l.isBlank()).limit(20_000).collect(Collectors.toSet());
        } catch (Exception e) {
            return new HashSet<>();
        }
    }
}
