package com.orion.reports;

import com.orion.correlation.CorrelationEngine;
import com.orion.core.model.EntityType;
import com.orion.core.model.Investigation;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.graph.OrionGraph;
import com.orion.intel.AttackMatrixService;
import com.orion.intel.RiskScoringService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReportsTest {

    private ReportModel demoModel() {
        Investigation inv = new Investigation("Campaña LockBit sector salud");
        inv.setDescription("Investigacion de intrusion con doble extorsion");
        inv.addTag("ransomware");
        inv.addNote("C2 confirmado por dos fuentes");
        OrionGraph g = new OrionGraph();
        OrionEntity d = OrionEntity.of(EntityType.DOMAIN, "cdn-update-example.ru");
        d.setRiskScore(72);
        d.addSource("crtsh");
        d.attr("registrar", "Registrar X");
        g.addEntity(d);
        OrionEntity ip = OrionEntity.of(EntityType.IPV4, "185.220.101.5");
        ip.setRiskScore(85);
        ip.addSource("feodo");
        ip.addSource("threatfox");
        ip.attr("feodoC2", true).attr("country", "NL").attr("asn", "AS9009");
        g.addEntity(ip);
        g.addEdge(d, ip, RelationshipType.RESOLVES_TO, "resuelve a");
        g.addEdge(ip, OrionEntity.of(EntityType.ASN, "AS9009"), RelationshipType.BELONGS_TO, "pertenece a");
        g.addEdge(OrionEntity.of(EntityType.MALWARE, "LockBit 3.0"), d, RelationshipType.HOSTS, "aloja");

        RiskScoringService.CaseRisk risk = new RiskScoringService().caseSummary(g);
        AttackMatrixService matrix = new AttackMatrixService();
        CorrelationEngine engine = new CorrelationEngine();
        var report = engine.run(g);
        return new ReportModel(inv, g, risk, matrix.actorProfiles(g), matrix.matrix(g),
                report.findings(), report.hypotheses(), "Estoa 1.0 (test)", java.time.Instant.now());
    }

    @Test
    void sevenTypesSixFormats() {
        assertEquals(7, ReportType.values().length);
        assertEquals(6, ReportFormat.values().length);
    }

    @Test
    void htmlRendersAllTypes() {
        HtmlExporter exp = new HtmlExporter();
        for (ReportType t : ReportType.values()) {
            String html = exp.render(t, demoModel());
            assertTrue(html.contains("INTELIGENCIA DE AMENAZAS"), t.name());
            assertTrue(html.contains("LockBit"), t.name());
            assertTrue(html.contains("</html>"), t.name());
        }
    }

    @Test
    void pdfGeneratesValidHeader() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PdfExporter().render(ReportType.TECHNICAL_INVESTIGATION, demoModel(), out);
        byte[] pdf = out.toByteArray();
        assertTrue(pdf.length > 2000);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        // estructura de trailer valida
        String tail = new String(pdf, pdf.length - 64, 64, java.nio.charset.StandardCharsets.US_ASCII);
        assertTrue(tail.contains("%%EOF"));
    }

    @Test
    void csvEscapesAndContainsIocs() {
        String csv = new DataExporter().csv(ReportType.IOC_REPORT, demoModel());
        assertTrue(csv.startsWith("ioc,tipo,riesgo"));
        assertTrue(csv.contains("185.220.101.5"));
        String inv = new DataExporter().csv(ReportType.CASE_SUMMARY, demoModel());
        assertTrue(inv.contains("cdn-update-example.ru"));
    }

    @Test
    void jsonContainsGraphStructure() {
        String json = new DataExporter().json(ReportType.CASE_SUMMARY, demoModel());
        assertTrue(json.contains("\"entities\""));
        assertTrue(json.contains("\"edges\""));
        assertTrue(json.contains("RESOLVES_TO"));
        var node = com.orion.core.util.JsonUtil.parse(json);
        assertEquals(4, node.path("stats").path("entities").asInt());
    }

    @Test
    void svgAndPngRenderGraph() throws Exception {
        GraphImageExporter exp = new GraphImageExporter();
        String svg = exp.renderSvg(demoModel(), 1200, 800);
        assertTrue(svg.startsWith("<?xml"));
        assertTrue(svg.contains("<circle"));
        assertTrue(svg.contains("cdn-update-example.ru"));

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        exp.renderPng(demoModel(), 1200, 800, png);
        assertTrue(png.size() > 5000);
        // firma PNG
        assertEquals((byte) 0x89, png.toByteArray()[0]);
    }

    @Test
    void generatorWritesFiles(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        ReportGenerator gen = new ReportGenerator();
        ReportModel m = demoModel();
        for (ReportFormat f : ReportFormat.values()) {
            Path target = dir.resolve(ReportGenerator.filename(ReportType.CASE_SUMMARY, f));
            gen.generate(ReportType.CASE_SUMMARY, f, m, target);
            assertTrue(Files.size(target) > 100, f.name());
        }
    }

    @Test
    void allTypesAllFormatsGenerate(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        ReportGenerator gen = new ReportGenerator();
        ReportModel m = demoModel();
        for (ReportType t : ReportType.values()) {
            for (ReportFormat f : ReportFormat.values()) {
                Path target = dir.resolve(t.name() + "." + f.extension());
                gen.generate(t, f, m, target);
                assertTrue(Files.size(target) > 10, t.name() + "/" + f.name());
            }
        }
    }
}
