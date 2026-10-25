package com.orion.reports;

import com.orion.core.model.OrionEntity;

import java.util.stream.Collectors;

/**
 * Exportador HTML: plantilla profesional autocontenida (CSS inline) con
 * cabecera Estoa, clasificacion TLP, tablas y secciones segun el tipo.
 */
public class HtmlExporter {

    public String render(ReportType type, ReportModel m) {
        StringBuilder b = new StringBuilder(32 * 1024);
        b.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\">");
        b.append("<title>").append(esc(type.label())).append(" - ESTOA</title>");
        b.append("""
<style>
 :root { --bg:#f6f8fa; --panel:#ffffff; --ink:#1f2328; --muted:#57606a; --accent:#0969da; --line:#d0d7de;
         --danger:#cf222e; --warn:#9a6700; --ok:#1a7f37; }
 body { font-family:'Segoe UI',Helvetica,Arial,sans-serif; background:var(--bg); color:var(--ink); margin:0; }
 .page { max-width:920px; margin:32px auto; background:var(--panel); border:1px solid var(--line); border-radius:8px; padding:40px 48px; }
 header { border-bottom:3px solid var(--accent); padding-bottom:16px; margin-bottom:24px; }
 .brand { font-size:22px; font-weight:700; letter-spacing:2px; color:#24292f; }
 .brand span { color:var(--accent); }
 .meta { color:var(--muted); font-size:12.5px; margin-top:6px; }
 h1 { font-size:24px; margin:0 0 4px; }
 .tlp { display:inline-block; padding:2px 10px; border-radius:10px; font-size:11px; font-weight:700; color:#fff; background:#d29922; }
 h2 { font-size:17px; border-bottom:1px solid var(--line); padding-bottom:6px; margin-top:30px; }
 table { width:100%; border-collapse:collapse; font-size:12.5px; margin:10px 0; }
 th { background:#eaeef2; text-align:left; padding:7px 9px; border:1px solid var(--line); font-size:11.5px; text-transform:uppercase; letter-spacing:.4px; }
 td { padding:6px 9px; border:1px solid var(--line); vertical-align:top; }
 tr:nth-child(even) td { background:#fbfcfd; }
 .risk { font-weight:700; }
 .r-crit { color:var(--danger); } .r-high { color:#bc4c00; } .r-med { color:var(--warn); } .r-low { color:var(--ok); }
 .note { background:#fff8c5; border:1px solid #d4a72c66; padding:10px 14px; border-radius:6px; font-size:13px; }
 .footer { margin-top:36px; padding-top:12px; border-top:1px solid var(--line); color:var(--muted); font-size:11.5px; }
 ul { padding-left:20px; } li { margin:3px 0; }
 .kpis { display:flex; gap:14px; flex-wrap:wrap; margin:14px 0; }
 .kpi { flex:1 1 120px; background:#f6f8fa; border:1px solid var(--line); border-radius:8px; padding:12px 16px; }
 .kpi b { display:block; font-size:22px; }
 .kpi small { color:var(--muted); text-transform:uppercase; font-size:10.5px; letter-spacing:.6px; }
</style></head><body><div class="page">
""");
        b.append("<header><div class=\"brand\">OR<span>ION</span> INTELIGENCIA DE AMENAZAS</div>");
        b.append("<div class=\"meta\">").append(type.label()).append(" &middot; generado ")
         .append(ReportGenerator.TS.format(m.generatedAt())).append(" &middot; ")
         .append(esc(m.generatedBy())).append("</div></header>");
        b.append("<h1>").append(esc(m.investigation().getName())).append("</h1>");
        b.append("<div><span class=\"tlp\">").append(esc(m.investigation().getClassification())).append("</span></div>");
        b.append("<div class=\"meta\">Caso ").append(esc(m.investigation().getId()))
         .append(" &middot; analista: ").append(esc(m.investigation().getAnalyst()))
         .append(" &middot; estado: ").append(m.investigation().getStatus().label())
         .append(" &middot; prioridad: ").append(m.investigation().getPriority().label()).append("</div>");

        if (!m.investigation().getDescription().isBlank()) {
            b.append("<p>").append(esc(m.investigation().getDescription())).append("</p>");
        }

        b.append("<div class=\"kpis\">");
        kpi(b, m.graph().nodeCount(), "Entidades");
        kpi(b, m.graph().edgeCount(), "Relaciones");
        kpi(b, m.iocs().size(), "IOCs");
        kpi(b, (int) Math.round(m.risk().average()), "Riesgo medio");
        kpi(b, m.risk().critical(), "Críticos");
        b.append("</div>");

        switch (type) {
            case EXECUTIVE_SUMMARY -> executive(b, m);
            case CASE_SUMMARY -> caseSummary(b, m);
            case IOC_REPORT -> iocReport(b, m);
            case THREAT_ACTOR_PROFILE -> actorProfile(b, m);
            case CAMPAIGN_TIMELINE -> timeline(b, m);
            case ATTACK_SURFACE -> attackSurface(b, m);
            case TECHNICAL_INVESTIGATION -> technical(b, m);
        }

        if (!m.investigation().getNotes().isEmpty()) {
            b.append("<h2>Notas del analista</h2><ul>");
            m.investigation().getNotes().forEach(n -> b.append("<li>").append(esc(com.orion.core.model.Investigation.displayNote(n))).append("</li>"));
            b.append("</ul>");
        }

        b.append("<div class=\"footer\">Documento generado automáticamente por la plataforma Estoa. ")
         .append("Clasificación: ").append(esc(m.investigation().getClassification()))
         .append(". Distribución restringida según protocolo TLP.</div>");
        b.append("</div></body></html>");
        return b.toString();
    }

    private void kpi(StringBuilder b, long value, String label) {
        b.append("<div class=\"kpi\"><b>").append(value).append("</b><small>").append(label).append("</small></div>");
    }

    private void executive(StringBuilder b, ReportModel m) {
        b.append("<h2>Valoración</h2>");
        b.append("<p>El caso <b>").append(esc(m.investigation().getName())).append("</b> integra ")
         .append(m.graph().nodeCount()).append(" entidades y ").append(m.graph().edgeCount())
         .append(" relaciones procedentes de las fuentes conectadas. El nivel de riesgo agregado es <b>")
         .append(riskClass((int) Math.round(m.risk().average()))).append(" (").append(Math.round(m.risk().average()))
         .append("/100)</b>, con ").append(m.risk().critical()).append(" entidades críticas y ")
         .append(m.risk().high()).append(" de riesgo alto. Se recomienda priorizar la contención de los IOCs de la tabla siguiente y validar las hipótesis pendientes del motor de correlación.</p>");
        iocTable(b, m, 15);
        if (!m.hypotheses().isEmpty()) {
            b.append("<h2>Hipótesis de correlación</h2><ul>");
            m.hypotheses().stream().limit(8).forEach(h -> b.append("<li>").append(esc(h.getStatement()))
                    .append(" <i>[").append(h.getStatus().label()).append("]</i></li>"));
            b.append("</ul>");
        }
        b.append("<h2>Recomendaciones</h2><ul>");
        b.append("<li>Bloquear los indicadores de confianza alta en los controles perimetrales.</li>");
        b.append("<li>Buscar las técnicas ATT&CK observadas en los registros de detección (búsqueda retrospectiva).</li>");
        b.append("<li>Revisar la caducidad de dominios y certificados relacionados para anticipar renovaciones del adversario.</li></ul>");
    }

    private void caseSummary(StringBuilder b, ReportModel m) {
        b.append("<h2>Inventario de entidades</h2>");
        b.append("<table><tr><th>Tipo</th><th>Valor</th><th>Riesgo</th><th>Fuentes</th><th>Primera observación</th></tr>");
        for (OrionEntity e : m.inventory()) {
            b.append("<tr><td>").append(esc(e.getType().label())).append("</td><td>")
             .append(esc(e.getValue())).append("</td><td class=\"risk ")
             .append(riskClass((int) e.getRiskScore())).append("\">").append((int) e.getRiskScore())
             .append("</td><td>").append(esc(String.join(", ", e.getSources()))).append("</td><td>")
             .append(ReportGenerator.TS.format(e.getFirstSeen())).append("</td></tr>");
        }
        b.append("</table>");
        iocTable(b, m, Integer.MAX_VALUE);
        if (!m.investigation().getTags().isEmpty()) {
            b.append("<h2>Etiquetas</h2><p>").append(esc(String.join(" &middot; ", m.investigation().getTags()))).append("</p>");
        }
    }

    private void iocReport(StringBuilder b, ReportModel m) {
        b.append("<h2>").append(m.iocTableTitle()).append("</h2>");
        iocTable(b, m, Integer.MAX_VALUE);
        b.append("<div class=\"note\">Los indicadores se priorizan por puntuación compuesta: confirmación multifuente, atributos de amenaza, conectividad y tipo de entidad. Las claves API de fuentes comerciales no se incluyen nunca en los artefactos exportados.</div>");
    }

    private void iocTable(StringBuilder b, ReportModel m, int limit) {
        b.append("<table><tr><th>IOC</th><th>Tipo</th><th>Riesgo</th><th>Confianza</th><th>Fuentes</th></tr>");
        m.iocs().stream().limit(limit).forEach(e -> {
            int conf = Math.min(100, e.getSources().size() * 34 + 20);
            b.append("<tr><td>").append(esc(e.getValue())).append("</td><td>").append(esc(e.getType().label()))
             .append("</td><td class=\"risk ").append(riskClass((int) e.getRiskScore())).append("\">")
             .append((int) e.getRiskScore()).append("</td><td>").append(conf).append("%</td><td>")
             .append(esc(String.join(", ", e.getSources()))).append("</td></tr>");
        });
        b.append("</table>");
    }

    private void actorProfile(StringBuilder b, ReportModel m) {
        if (m.actors().isEmpty()) {
            b.append("<p>No hay actores identificados en el caso. Ejecute transformaciones de perfil de actor sobre entidades APT o de tipo actor de amenaza.</p>");
            return;
        }
        for (var a : m.actors()) {
            b.append("<h2>").append(esc(a.name())).append("</h2>");
            b.append("<p><b>Alias:</b> ").append(esc(a.aliases())).append(" &middot; <b>Origen:</b> ")
             .append(esc(a.origin())).append(" &middot; <b>Riesgo:</b> ").append((int) a.risk()).append("/100</p>");
            b.append("<p>").append(esc(a.description())).append("</p>");
            b.append("<p><b>Técnicas vinculadas en el caso:</b> ").append(a.techniques()).append("</p>");
        }
    }

    private void timeline(StringBuilder b, ReportModel m) {
        b.append("<h2>Cronología</h2>");
        b.append("<table><tr><th>Instante</th><th>Evento</th></tr>");
        b.append("<tr><td>").append(ReportGenerator.TS.format(m.investigation().getCreatedAt()))
         .append("</td><td>Apertura del caso ").append(esc(m.investigation().getId())).append("</td></tr>");
        m.graph().getNodes().stream()
                .map(n -> n.getEntity())
                .sorted((x, y) -> x.getFirstSeen().compareTo(y.getFirstSeen()))
                .limit(60)
                .forEach(e -> b.append("<tr><td>").append(ReportGenerator.TS.format(e.getFirstSeen()))
                        .append("</td><td>Observado ").append(esc(e.getType().label())).append(" <b>")
                        .append(esc(e.getValue())).append("</b> (riesgo ").append((int) e.getRiskScore()).append(")</td></tr>"));
        m.investigation().getNotes().forEach(n ->
                b.append("<tr><td></td><td class=\"meta\">Nota: ").append(esc(com.orion.core.model.Investigation.displayNote(n))).append("</td></tr>"));
        b.append("</table>");
    }

    private void attackSurface(StringBuilder b, ReportModel m) {
        b.append("<h2>Activos expuestos</h2>");
        b.append("<table><tr><th>Tipo</th><th>Activo</th><th>Atributos relevantes</th><th>Riesgo</th></tr>");
        m.inventory().stream()
                .filter(e -> e.getType().name().matches("DOMAIN|IPV4|IPV6|URL|PORT|CERTIFICATE|NETBLOCK"))
                .forEach(e -> {
                    String attrs = e.getAttributes().entrySet().stream()
                            .filter(a -> a.getValue() != null && !String.valueOf(a.getValue()).isBlank())
                            .limit(4)
                            .map(a -> a.getKey() + ": " + a.getValue())
                            .collect(Collectors.joining("; "));
                    b.append("<tr><td>").append(esc(e.getType().label())).append("</td><td>")
                     .append(esc(e.getValue())).append("</td><td>").append(esc(attrs)).append("</td><td class=\"risk ")
                     .append(riskClass((int) e.getRiskScore())).append("\">").append((int) e.getRiskScore()).append("</td></tr>");
                });
        b.append("</table>");
    }

    private void technical(StringBuilder b, ReportModel m) {
        caseSummary(b, m);
        b.append("<h2>Hallazgos de correlación</h2>");
        if (m.findings().isEmpty()) {
            b.append("<p>Sin hallazgos de correlación activos.</p>");
        } else {
            b.append("<table><tr><th>Regla</th><th>Hallazgo</th><th>Confianza</th></tr>");
            m.findings().forEach(f -> b.append("<tr><td>").append(esc(f.ruleName())).append("</td><td>")
                    .append(esc(f.description())).append("</td><td>").append(Math.round(f.confidence()))
                    .append("%</td></tr>"));
            b.append("</table>");
        }
        b.append("<h2>Hipótesis</h2><ul>");
        if (m.hypotheses().isEmpty()) b.append("<li>Sin hipótesis generadas.</li>");
        m.hypotheses().forEach(h -> b.append("<li>").append(esc(h.getStatement())).append("</li>"));
        b.append("</ul>");
        b.append("<h2>Matriz ATT&CK observada</h2>");
        if (m.matrix().isEmpty()) {
            b.append("<p>Sin técnicas ATT&CK en el caso.</p>");
        } else {
            b.append("<table><tr><th>Táctica</th><th>Técnicas observadas</th></tr>");
            m.matrix().stream().filter(t -> t.observedCount() > 0).forEach(t ->
                    b.append("<tr><td>").append(esc(t.tacticNameEs())).append(" (").append(t.tacticId()).append(")</td><td>")
                     .append(t.observedCount()).append("</td></tr>"));
            b.append("</table>");
        }
    }

    private String riskClass(int risk) {
        if (risk >= 80) return "r-crit";
        if (risk >= 60) return "r-high";
        if (risk >= 35) return "r-med";
        return "r-low";
    }

    private String riskLabel(int risk) {
        if (risk >= 80) return "Crítico";
        if (risk >= 60) return "Alto";
        if (risk >= 35) return "Medio";
        return "Bajo";
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
