package com.orion.intel;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RiskScore;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;

import java.util.List;

/**
 * Puntuacion de riesgo compuesta (0-100) con factores explicables:
 * corroboracion por fuentes, atributos de amenaza (incluye CVSS, EPSS y KEV),
 * conectividad en el caso y tipo de entidad. Es una heuristica de triaje,
 * no una evaluacion cuantitativa ni el resultado de un escaneo.
 */
public class RiskScoringService {

    public RiskScore score(OrionEntity e, OrionGraph g) {
        RiskScore rs = new RiskScore();

        // factor 1: corroboración por fuentes (máx. 24; no mide independencia real)
        int nSources = Math.max(e.getSources().size(), e.getSource().equals("manual") ? 0 : 1);
        double f1 = Math.min(24, nSources * 8.0);
        rs.addFactor("Confirmación multifuente", 0.24, f1,
                nSources + " fuente(s): " + (e.getSources().isEmpty() ? e.getSource() : String.join(", ", e.getSources())));

        // factor 2: atributos de amenaza (máx. 50)
        double f2 = 0;
        StringBuilder detail = new StringBuilder();
        if (Boolean.TRUE.equals(e.attr("feodoC2")) || Boolean.TRUE.equals(e.attr("phishing"))) {
            f2 += 35;
            detail.append("catalogado como C2/phishing; ");
        }
        if (Boolean.TRUE.equals(e.attr("kev"))) {
            f2 += 35;
            detail.append("KEV (explotación confirmada); ");
        }
        double cvss = parseDouble(e.attr("cvss"));
        if (cvss >= 9) {
            f2 += 40;
            detail.append("CVSS ").append(cvss).append(" crítico; ");
        } else if (cvss >= 7) {
            f2 += 25;
            detail.append("CVSS ").append(cvss).append(" alto; ");
        } else if (cvss >= 4) {
            f2 += 12;
            detail.append("CVSS ").append(cvss).append(" medio; ");
        }
        double epss = parseDouble(e.attr("epss"));
        if (epss >= 0.5) {
            f2 += 15;
            detail.append("EPSS ").append(epss).append(" alto; ");
        } else if (epss >= 0.1) {
            f2 += 8;
            detail.append("EPSS ").append(epss).append(" medio; ");
        }
        int vtMal = parseInt(e.attr("vtMalicious"));
        if (vtMal > 0) {
            f2 += Math.min(15, vtMal);
            detail.append(vtMal).append(" motores VT; ");
        }
        String family = e.attrStr("family", "");
        if (!family.isEmpty() && e.getType() == EntityType.HASH) {
            f2 += 12;
            detail.append("familia ").append(family).append("; ");
        }
        f2 = Math.min(50, f2);
        rs.addFactor("Atributos de amenaza", 0.50, f2, detail.isEmpty() ? "sin atributos de amenaza" : detail.toString());

        // factor 3: conectividad en el caso (max 15)
        int degree = g == null ? 0 : g.degree(e.key());
        double f3 = Math.min(15, degree * 3.0);
        rs.addFactor("Conectividad", 0.15, f3, degree + " relaciones en el caso");

        // factor 4: tipo de entidad con riesgo estructural (max 12)
        double f4 = switch (e.getType()) {
            case MALWARE, APT, THREAT_ACTOR -> 12;
            case IPV4, IPV6, URL -> 8;
            case DOMAIN, HASH -> 6;
            case CVE, EXPLOIT -> 10;
            default -> 2;
        };
        rs.addFactor("Tipo de entidad", 0.11, f4, e.getType().label());

        return rs;
    }

    private double parseDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private int parseInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /** Resumen de riesgo del caso completo para el panel e informes. */
    public CaseRisk caseSummary(OrionGraph g) {
        int critical = 0, high = 0, medium = 0, low = 0;
        for (GraphNode n : g.getNodes()) {
            double r = n.getEntity().getRiskScore();
            if (r >= 80) critical++;
            else if (r >= 60) high++;
            else if (r >= 35) medium++;
            else low++;
        }
        return new CaseRisk(critical, high, medium, low, g.averageRisk());
    }

    public record CaseRisk(int critical, int high, int medium, int low, double average) {
        public List<Integer> distribution() { return List.of(critical, high, medium, low); }
    }
}
