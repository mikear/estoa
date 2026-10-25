package com.orion.correlation;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.util.NetUtil;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Las 8 reglas heuristicas de correlacion del motor Estoa. Cada regla produce
 * hallazgos con confianza y las evidencias (claves de entidades) asociadas.
 */
public final class StandardRules {

    private StandardRules() {}

    /** 1. Infraestructura comun: >= 3 IPs de riesgo en el mismo ASN. */
    public static CorrelationRule sharedAsn() {
        return new CorrelationRule("shared-asn", "Infraestructura ASN común",
                "Tres o más IPs marcadas como maliciosas comparten sistema autónomo.") {
            @Override
            public List<Finding> evaluate(OrionGraph g) {
                List<Finding> out = new ArrayList<>();
                Map<String, List<GraphNode>> byAsn = g.getNodes().stream()
                        .filter(n -> n.getEntity().getType() == EntityType.IPV4)
                        .filter(n -> n.getEntity().getRiskScore() >= 50)
                        .filter(n -> connectedToAsn(g, n))
                        .collect(Collectors.groupingBy(n -> asnOf(g, n)));
                for (var e : byAsn.entrySet()) {
                    if (e.getValue().size() >= 3) {
                        double conf = Math.min(95, 55 + e.getValue().size() * 8);
                        out.add(finding(conf, e.getValue().size() + " IPs maliciosas residen en " + e.getKey()
                                + " (posible infraestructura dedicada de C2)",
                                keys(e.getValue())));
                    }
                }
                return out;
            }
        };
    }

    private static boolean connectedToAsn(OrionGraph g, GraphNode n) {
        return !asnOf(g, n).isEmpty();
    }

    private static String asnOf(OrionGraph g, GraphNode n) {
        return g.edgesOf(n.getKey()).stream()
                .filter(e -> e.getRelation().name().equals("BELONGS_TO"))
                .map(e -> e.other(n).getEntity().getValue())
                .findFirst().orElse("");
    }

    /** 2. Certificado compartido entre dominios distintos. */
    public static CorrelationRule sharedCertificate() {
        return new CorrelationRule("shared-cert", "Certificado compartido",
                "Un mismo certificado TLS cubre varios dominios del caso.") {
            @Override
            public List<Finding> evaluate(OrionGraph g) {
                List<Finding> out = new ArrayList<>();
                for (GraphNode cert : g.getNodes()) {
                    if (cert.getEntity().getType() != EntityType.CERTIFICATE) continue;
                    List<GraphNode> domains = g.edgesOf(cert.getKey()).stream()
                            .filter(e -> e.getRelation().name().equals("HAS_CERTIFICATE"))
                            .map(e -> e.getSource()).distinct().toList();
                    if (domains.size() >= 2) {
                        out.add(finding(70, "El certificado " + cert.getEntity().getValue()
                                + " cubre " + domains.size() + " dominios: posible operador común",
                                keys(domains)));
                    }
                }
                return out;
            }
        };
    }

    /** 3. Solapamiento de registrador con ventana temporal corta. */
    public static CorrelationRule registrarOverlap() {
        return new CorrelationRule("registrar-overlap", "Alta de dominios en cascada",
                "Varios dominios registrados con el mismo registrador en fechas próximas.") {
            @Override
            public List<Finding> evaluate(OrionGraph g) {
                List<Finding> out = new ArrayList<>();
                Map<String, List<GraphNode>> byRegistrar = g.getNodes().stream()
                        .filter(n -> n.getEntity().getType() == EntityType.DOMAIN)
                        .filter(n -> n.getEntity().attr("registrar") != null)
                        .collect(Collectors.groupingBy(n -> n.getEntity().attrStr("registrar", "?")));
                for (var e : byRegistrar.entrySet()) {
                    if (e.getValue().size() >= 3) {
                        out.add(finding(60, e.getValue().size() + " dominios del caso registrados vía "
                                + e.getKey() + ": patrón típico de kit de phishing/campaña",
                                keys(e.getValue())));
                    }
                }
                return out;
            }
        };
    }

    /** 4. Confirmación multifuente: entidad marcada por >= 2 fuentes independientes. */
    public static CorrelationRule multiSourceConfirmation() {
        return new CorrelationRule("multi-source", "Confirmación multifuente",
                "Entidad señalada por dos o más fuentes independientes con riesgo alto.") {
            @Override
            public List<Finding> evaluate(OrionGraph g) {
                List<Finding> out = new ArrayList<>();
                for (GraphNode n : g.getNodes()) {
                    OrionEntity e = n.getEntity();
                    if (e.getSources().size() >= 2 && e.getRiskScore() >= 60) {
                        out.add(finding(Math.min(95, 60 + e.getSources().size() * 10),
                                e.getType().label() + " " + e.getValue() + " confirmado por "
                                        + e.getSources().size() + " fuentes (" + String.join(", ", e.getSources()) + ")",
                                List.of(n.getKey())));
                    }
                }
                return out;
            }
        };
    }

    /** 5. Linaje de muestras: misma familia con varias muestras en el caso. */
    public static CorrelationRule hashLineage() {
        return new CorrelationRule("hash-lineage", "Linaje de muestras",
                "Una familia de malware está representada por varias muestras distintas.") {
            @Override
            public List<Finding> evaluate(OrionGraph g) {
                List<Finding> out = new ArrayList<>();
                for (GraphNode mal : g.getNodes()) {
                    if (mal.getEntity().getType() != EntityType.MALWARE) continue;
                    List<GraphNode> family = g.neighbors(mal.getKey()).stream()
                            .filter(n -> n.getEntity().getType() == EntityType.HASH).toList();
                    if (family.size() >= 2) {
                        out.add(finding(65, "La familia " + mal.getEntity().getValue() + " presenta "
                                + family.size() + " muestras en el caso: actividad de campaña sostenida",
                                keys(family)));
                    }
                }
                return out;
            }
        };
    }

    /** 6. Solapamiento TTP: dos APTs comparten >= 3 tecnicas. */
    public static CorrelationRule ttpOverlap() {
        return new CorrelationRule("ttp-overlap", "Solapamiento de TTPs",
                "Dos actores comparten tres o más técnicas ATT&CK: posible alias.") {
            @Override
            public List<Finding> evaluate(OrionGraph g) {
                List<Finding> out = new ArrayList<>();
                List<GraphNode> actors = g.getNodes().stream()
                        .filter(n -> n.getEntity().getType() == EntityType.APT
                                || n.getEntity().getType() == EntityType.THREAT_ACTOR).toList();
                for (int i = 0; i < actors.size(); i++) {
                    for (int j = i + 1; j < actors.size(); j++) {
                        GraphNode a = actors.get(i);
                        GraphNode b = actors.get(j);
                        List<GraphNode> shared = g.neighbors(a.getKey()).stream()
                                .filter(n -> n.getEntity().getType() == EntityType.ATTCK_TECHNIQUE)
                                .filter(n -> g.neighbors(b.getKey()).contains(n)).toList();
                        if (shared.size() >= 3) {
                            out.add(finding(75, a.getEntity().getValue() + " y " + b.getEntity().getValue()
                                    + " comparten " + shared.size() + " técnicas: evaluar si son el mismo actor",
                                    keys(List.of(a, b))));
                        }
                    }
                }
                return out;
            }
        };
    }

    /** 7. Similitud de dominios (typosquatting). */
    public static CorrelationRule domainSimilarity() {
        return new CorrelationRule("domain-similarity", "Dominios casi idénticos",
                "Pares de dominios con distancia de edición mínima: posible suplantación tipográfica.") {
            @Override
            public List<Finding> evaluate(OrionGraph g) {
                List<Finding> out = new ArrayList<>();
                List<GraphNode> domains = g.getNodes().stream()
                        .filter(n -> n.getEntity().getType() == EntityType.DOMAIN).toList();
                for (int i = 0; i < domains.size(); i++) {
                    for (int j = i + 1; j < domains.size(); j++) {
                        String a = domains.get(i).getEntity().getValue();
                        String b = domains.get(j).getEntity().getValue();
                        if (a.equals(b) || Math.abs(a.length() - b.length()) > 3) continue;
                        int d = NetUtil.levenshtein(a, b);
                        if (d >= 1 && d <= 2) {
                            out.add(finding(55, "Los dominios " + a + " y " + b
                                    + " difieren en " + d + " caracteres: posible suplantación",
                                    keys(List.of(domains.get(i), domains.get(j)))));
                        }
                    }
                }
                return out;
            }
        };
    }

    /** 8. Colocacion geografica y de red. */
    public static CorrelationRule geoColocation() {
        return new CorrelationRule("geo-colocation", "Colocación geográfica",
                "Tres o más entidades de riesgo en el mismo país y sistema autónomo.") {
            @Override
            public List<Finding> evaluate(OrionGraph g) {
                List<Finding> out = new ArrayList<>();
                Map<String, List<GraphNode>> byGeo = g.getNodes().stream()
                        .filter(n -> n.getEntity().getRiskScore() >= 45)
                        .filter(n -> !n.getEntity().attrStr("country", "").isEmpty())
                        .collect(Collectors.groupingBy(n -> n.getEntity().attrStr("country", "?")
                                + "/" + n.getEntity().attrStr("asn", "")));
                for (var e : byGeo.entrySet()) {
                    if (e.getValue().size() >= 3) {
                        out.add(finding(58, e.getValue().size() + " entidades de riesgo colocadas en "
                                + e.getKey() + ": valorar bloqueo agregado del bloque de red",
                                keys(e.getValue())));
                    }
                }
                return out;
            }
        };
    }

    private static List<String> keys(List<GraphNode> nodes) {
        return nodes.stream().map(GraphNode::getKey).toList();
    }

    public static List<CorrelationRule> all() {
        return List.of(sharedAsn(), sharedCertificate(), registrarOverlap(), multiSourceConfirmation(),
                hashLineage(), ttpOverlap(), domainSimilarity(), geoColocation());
    }
}
