package com.orion.reports;

import com.orion.core.util.JsonUtil;

/**
 * Exportadores de datos tabulares: CSV (separador seguro para locales ES)
 * y JSON estructurado del caso.
 */
public class DataExporter {

    public String csv(ReportType type, ReportModel m) {
        StringBuilder b = new StringBuilder();
        switch (type) {
            case IOC_REPORT, EXECUTIVE_SUMMARY -> {
                b.append("ioc,tipo,riesgo,confianza,fuentes,tlp\n");
                m.iocs().forEach(e -> b.append(csvField(e.getValue())).append(',')
                        .append(csvField(e.getType().name())).append(',')
                        .append((int) e.getRiskScore()).append(',')
                        .append(Math.min(100, e.getSources().size() * 34 + 20)).append('%').append(',')
                        .append(csvField(String.join(" ", e.getSources()))).append(',')
                        .append(csvField(e.getTlp().name())).append('\n'));
            }
            case ATTACK_SURFACE -> {
                b.append("tipo,activo,atributos,riesgo\n");
                m.inventory().stream()
                        .filter(e -> e.getType().name().matches("DOMAIN|IPV4|IPV6|URL|PORT|CERTIFICATE|NETBLOCK"))
                        .forEach(e -> b.append(csvField(e.getType().name())).append(',')
                                .append(csvField(e.getValue())).append(',')
                                .append(csvField(e.getAttributes().toString())).append(',')
                                .append((int) e.getRiskScore()).append('\n'));
            }
            case THREAT_ACTOR_PROFILE -> {
                b.append("actor,alias,origen,técnicas,riesgo\n");
                m.actors().forEach(a -> b.append(csvField(a.name())).append(',')
                        .append(csvField(a.aliases())).append(',')
                        .append(csvField(a.origin())).append(',')
                        .append(a.techniques()).append(',')
                        .append((int) a.risk()).append('\n'));
            }
            default -> {
                b.append("tipo,valor,etiqueta,riesgo,tlp,fuentes,primera_observación\n");
                m.inventory().forEach(e -> b.append(csvField(e.getType().name())).append(',')
                        .append(csvField(e.getValue())).append(',')
                        .append(csvField(e.getLabel())).append(',')
                        .append((int) e.getRiskScore()).append(',')
                        .append(csvField(e.getTlp().name())).append(',')
                        .append(csvField(String.join(" ", e.getSources()))).append(',')
                        .append(csvField(e.getFirstSeen().toString())).append('\n'));
            }
        }
        return b.toString();
    }

    public String json(ReportType type, ReportModel m) {
        var root = JsonUtil.mapper().createObjectNode();
        root.put("reportType", type.name());
        root.put("title", type.label());
        root.set("investigation", JsonUtil.mapper().valueToTree(m.investigation().snapshot()));
        root.put("generatedAt", m.generatedAt().toString());
        root.put("generatedBy", m.generatedBy());
        var stats = root.putObject("stats");
        stats.put("entities", m.graph().nodeCount());
        stats.put("edges", m.graph().edgeCount());
        stats.put("iocs", m.iocs().size());
        stats.put("averageRisk", m.risk().average());
        stats.put("critical", m.risk().critical());
        var entities = root.putArray("entities");
        m.inventory().forEach(e -> entities.add(JsonUtil.mapper().valueToTree(e.snapshot())));
        var edges = root.putArray("edges");
        m.graph().getEdges().forEach(e -> {
            var o = edges.addObject();
            o.put("from", e.getSource().getEntity().key());
            o.put("to", e.getTarget().getEntity().key());
            o.put("relation", e.getRelation().name());
            o.put("label", e.getLabel());
        });
        if (!m.findings().isEmpty()) {
            var findings = root.putArray("findings");
            m.findings().forEach(f -> {
                var o = findings.addObject();
                o.put("rule", f.ruleId());
                o.put("description", f.description());
                o.put("confidence", f.confidence());
            });
        }
        if (!m.hypotheses().isEmpty()) {
            var hyps = root.putArray("hypotheses");
            m.hypotheses().forEach(h -> {
                var o = hyps.addObject();
                o.put("statement", h.getStatement());
                o.put("confidence", h.getConfidence());
                o.put("status", h.getStatus().name());
            });
        }
        return JsonUtil.write(root);
    }

    private String csvField(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        // Excel ejecuta como formula todo valor que empiece por = + - @
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains(";") || v.contains("\n") || v.contains("\"")) {
            return '"' + v + '"';
        }
        return v;
    }
}
