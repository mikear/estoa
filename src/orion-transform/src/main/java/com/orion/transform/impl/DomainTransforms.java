package com.orion.transform.impl;

import com.orion.core.model.EntityType;
import com.orion.transform.Transform;
import com.orion.core.model.OrionEntity;
import com.orion.sources.Operation;
import com.orion.sources.SourceQuery;
import com.orion.sources.SourceResult;
import com.orion.transform.BaseTransform;
import com.orion.transform.TransformContext;
import com.orion.transform.TransformResult;

import java.util.Set;

/**
 * Transformaciones sobre dominios: DNS, WHOIS/RDAP, subdominios y
 * certificados (Certificate Transparency).
 */
public final class DomainTransforms {

    private DomainTransforms() {}

    public static BaseTransform dnsRecords() {
        return new BaseTransform("domain-dns", "Resolver registros DNS (DoH)",
                "A, AAAA, MX, NS y TXT via DNS sobre HTTPS de Google/Cloudflare.",
                Transform.Category.INFRASTRUCTURE,
                Set.of(EntityType.DOMAIN), Set.of(EntityType.IPV4, EntityType.IPV6, EntityType.DNS_RECORD), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return collect(ctx, input, "Resolución DNS",
                        Operation.DNS_A, Operation.DNS_AAAA, Operation.DNS_MX, Operation.DNS_NS, Operation.DNS_TXT);
            }
        };
    }

    public static BaseTransform whois() {
        return new BaseTransform("domain-whois", "Datos de registro (RDAP)",
                "Registrador, fechas de alta/caducidad, NS y estados del dominio.",
                Transform.Category.ENRICHMENT,
                Set.of(EntityType.DOMAIN), Set.of(EntityType.REGISTRAR, EntityType.ORGANIZATION), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return collect(ctx, input, "Registro del dominio", Operation.WHOIS);
            }
        };
    }

    public static BaseTransform subdomains() {
        return new BaseTransform("domain-subdomains", "Descubrir subdominios (crt.sh)",
                "Subdominios presentes en los logs de Certificate Transparency.",
                Transform.Category.ENRICHMENT,
                Set.of(EntityType.DOMAIN), Set.of(EntityType.DOMAIN), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return collect(ctx, input, "Subdominios", Operation.SUBDOMAINS);
            }
        };
    }

    public static BaseTransform certificates() {
        return new BaseTransform("domain-certificates", "Certificados CT (crt.sh)",
                "Certificados emitidos para el dominio con emisor y vigencia.",
                Transform.Category.HISTORICAL,
                Set.of(EntityType.DOMAIN), Set.of(EntityType.CERTIFICATE), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return collect(ctx, input, "Certificados", Operation.CERTIFICATES);
            }
        };
    }
    public static BaseTransform siteProbe() {
        return new BaseTransform("domain-site-probe", "Inspección del sitio (HTTP)",
                "Estado HTTP, título, CMS, formularios, favicon, correos visibles, sitios enlazados y cabeceras de seguridad.",
                Transform.Category.INFRASTRUCTURE,
                Set.of(EntityType.DOMAIN, EntityType.URL),
                Set.of(EntityType.DOMAIN, EntityType.SOFTWARE, EntityType.EMAIL, EntityType.HASH), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return collect(ctx, input, "Inspección del sitio", Operation.SITE_PROBE);
            }
        };
    }

    public static BaseTransform breaches() {
        return new BaseTransform("domain-breaches", "Filtraciones de datos (HIBP)",
                "Brechas públicas que afectan al dominio, con fecha, volumen y datos expuestos.",
                Transform.Category.THREAT_INTEL,
                Set.of(EntityType.DOMAIN), Set.of(EntityType.INCIDENT), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return collect(ctx, input, "Filtraciones", Operation.BREACHES);
            }
        };
    }

    public static BaseTransform infostealer() {
        return new BaseTransform("domain-infostealer", "Credenciales infostealer (Hudson Rock)",
                "Credenciales de empleados y clientes comprometidas por infostealers.",
                Transform.Category.THREAT_INTEL,
                Set.of(EntityType.DOMAIN), Set.of(EntityType.INCIDENT, EntityType.URL), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return collect(ctx, input, "Infostealer", Operation.INFOSTEALER);
            }
        };
    }

    /** Ejecuta varias operaciones de varios conectores y agrega el resultado. */
    static TransformResult collect(TransformContext ctx, OrionEntity input, String title, Operation... ops) {
        long start = System.currentTimeMillis();
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.List<java.util.concurrent.Future<com.orion.sources.SourceResult>> pending = new java.util.ArrayList<>();
        // las fuentes no dependen entre si: en serie el transform se eterniza
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            for (Operation op : ops) {
                for (var connector : ctx.sources().forOperation(op)) {
                    names.add(connector.name());
                    pending.add(pool.submit(() -> connector.query(
                            com.orion.sources.SourceQuery.of(op, input.getValue()))));
                }
            }
            java.util.List<OrionEntity> entities = new java.util.ArrayList<>();
            java.util.List<com.orion.core.model.EdgeDraft> edges = new java.util.ArrayList<>();
            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < pending.size(); i++) {
                String name = names.get(i);
                try {
                    com.orion.sources.SourceResult sr = pending.get(i).get();
                    entities.addAll(sr.getEntities());
                    edges.addAll(sr.getEdges());
                    summary.append(name).append(": ").append(sr.getMessage()).append("; ");
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    summary.append(name).append(": interrumpido; ");
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    summary.append(name).append(": fallo (").append(cause.getMessage()).append("); ");
                }
            }
            String msg = summary.isEmpty() ? "Sin fuentes capaces de resolver la operación"
                    : summary.substring(0, Math.min(400, summary.length()));
            TransformResult result = new TransformResult(true, "multi", msg, System.currentTimeMillis() - start);
            entities.forEach(result::entity);
            edges.forEach(result::edge);
            return result;
        }
    }
}
