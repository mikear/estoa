package com.orion.reports;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.orion.core.model.OrionEntity;

import java.awt.Color;
import java.io.OutputStream;
import java.util.List;

/**
 * Exportador PDF (OpenPDF) con tipografia base Helvetica, cabecera de marca,
 * pie con paginacion y marca de agua CONFIDENCIAL en todas las paginas.
 */
public class PdfExporter {

    private static final Font H1 = new Font(Font.HELVETICA, 17, Font.BOLD, new Color(0x24, 0x29, 0x2f));
    private static final Font H2 = new Font(Font.HELVETICA, 12.5f, Font.BOLD, new Color(0x09, 0x69, 0xda));
    private static final Font BODY = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, new Color(0x1f, 0x23, 0x28));
    private static final Font SMALL = new Font(Font.HELVETICA, 8f, Font.NORMAL, new Color(0x57, 0x60, 0x6a));
    private static final Font BOLD = new Font(Font.HELVETICA, 9.5f, Font.BOLD, new Color(0x1f, 0x23, 0x28));

    public void render(ReportType type, ReportModel m, OutputStream out) throws java.io.IOException {
        Document doc = new Document(PageSize.A4, 52, 52, 64, 56);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Branding());
            doc.open();

            Paragraph brand = new Paragraph("ESTOA - INTELIGENCIA DE AMENAZAS", new Font(Font.HELVETICA, 9, Font.BOLD, new Color(0x09, 0x69, 0xda)));
            brand.setSpacingAfter(2);
            doc.add(brand);
            doc.add(new Paragraph(type.label(), SMALL));
            Paragraph title = new Paragraph(m.investigation().getName(), H1);
            title.setSpacingBefore(8);
            title.setSpacingAfter(4);
            doc.add(title);
            doc.add(meta(m));

            Paragraph intro = new Paragraph(
                    "Caso " + m.investigation().getId()
                            + " - analista " + m.investigation().getAnalyst()
                            + " - estado " + m.investigation().getStatus().label()
                            + " - prioridad " + m.investigation().getPriority().label()
                            + ". Generado " + ReportGenerator.TS.format(m.generatedAt())
                            + " por " + m.generatedBy() + ".", SMALL);
            intro.setSpacingAfter(10);
            doc.add(intro);

            if (!m.investigation().getDescription().isBlank()) {
                doc.add(par(m.investigation().getDescription()));
            }

            switch (type) {
                case EXECUTIVE_SUMMARY -> executive(doc, m);
                case CASE_SUMMARY, TECHNICAL_INVESTIGATION -> caseSummary(doc, m, type == ReportType.TECHNICAL_INVESTIGATION);
                case IOC_REPORT -> iocReport(doc, m);
                case THREAT_ACTOR_PROFILE -> actorProfile(doc, m);
                case CAMPAIGN_TIMELINE -> timeline(doc, m);
                case ATTACK_SURFACE -> attackSurface(doc, m);
            }

            if (!m.investigation().getNotes().isEmpty()) {
                doc.add(h2("Notas del analista"));
                for (String n : m.investigation().getNotes()) doc.add(bullet(com.orion.core.model.Investigation.displayNote(n)));
            }
        } catch (Exception e) {
            throw new java.io.IOException("Fallo generando PDF: " + e.getMessage(), e);
        } finally {
            if (doc != null) doc.close();
        }
    }

    private Paragraph meta(ReportModel m) {
        Paragraph p = new Paragraph(m.investigation().getClassification(),
                new Font(Font.HELVETICA, 8f, Font.BOLD, new Color(0xd2, 0x99, 0x22)));
        p.setSpacingAfter(8);
        return p;
    }

    private void executive(Document doc, ReportModel m) throws Exception {
        doc.add(h2("Valoración"));
        doc.add(par("El caso integra " + m.graph().nodeCount() + " entidades y " + m.graph().edgeCount()
                + " relaciones. Riesgo agregado medio: " + Math.round(m.risk().average())
                + "/100, con " + m.risk().critical() + " entidades críticas y " + m.risk().high()
                + " de riesgo alto. Priorice la contención de los indicadores siguientes y valide las hipótesis pendientes."));
        doc.add(h2(m.iocTableTitle()));
        iocTable(doc, m, 15);
        if (!m.hypotheses().isEmpty()) {
            doc.add(h2("Hipótesis de correlación"));
            for (var h : m.hypotheses()) doc.add(bullet(h.getStatement() + " [" + h.getStatus().label() + "]"));
        }
        doc.add(h2("Recomendaciones"));
        doc.add(bullet("Bloquear los IOCs de confianza alta en los controles perimetrales."));
        doc.add(bullet("Búsqueda retrospectiva de las técnicas ATT&CK observadas en los registros de detección."));
        doc.add(bullet("Vigilar renovaciones de dominios y certificados del adversario."));
    }

    private void caseSummary(Document doc, ReportModel m, boolean technical) throws Exception {
        doc.add(h2("Inventario de entidades"));
        PdfPTable t = table(4, "Tipo", "Valor", "Riesgo", "Fuentes");
        for (OrionEntity e : m.inventory()) {
            t.addCell(cell(e.getType().label()));
            t.addCell(cell(e.getValue()));
            t.addCell(cell(String.valueOf((int) e.getRiskScore())));
            t.addCell(cell(String.join(", ", e.getSources())));
        }
        doc.add(t);
        doc.add(h2(m.iocTableTitle()));
        iocTable(doc, m, Integer.MAX_VALUE);
        if (technical) {
            doc.add(h2("Hallazgos de correlación"));
            if (m.findings().isEmpty()) doc.add(par("Sin hallazgos activos."));
            for (var f : m.findings()) {
                doc.add(bullet("[" + f.ruleName() + "] " + f.description() + " (confianza "
                        + Math.round(f.confidence()) + "%)"));
            }
            doc.add(h2("Hipótesis"));
            if (m.hypotheses().isEmpty()) doc.add(par("Sin hipótesis generadas."));
            for (var h : m.hypotheses()) doc.add(bullet(h.getStatement()));
            doc.add(h2("Matriz ATT&CK"));
            List<AttackMatrixStub> rows = matrixObserved(m);
            if (rows.isEmpty()) doc.add(par("Sin técnicas ATT&CK en el caso."));
            PdfPTable mt = table(2, "Táctica", "Técnicas observadas");
            for (AttackMatrixStub r : rows) {
                mt.addCell(cell(r.tactic()));
                mt.addCell(cell(String.valueOf(r.count())));
            }
            doc.add(mt);
        }
    }

    private record AttackMatrixStub(String tactic, int count) {}

    private List<AttackMatrixStub> matrixObserved(ReportModel m) {
        return m.matrix().stream()
                .filter(t -> t.observedCount() > 0)
                .map(t -> new AttackMatrixStub(t.tacticNameEs() + " (" + t.tacticId() + ")", t.observedCount()))
                .toList();
    }

    private void iocReport(Document doc, ReportModel m) throws Exception {
        doc.add(h2(m.iocTableTitle()));
        iocTable(doc, m, Integer.MAX_VALUE);
        doc.add(par("Los indicadores se priorizan por puntuación compuesta (multifuente, atributos, conectividad y tipo). Las claves de API comerciales nunca se exportan."));
    }

    private void iocTable(Document doc, ReportModel m, int limit) throws Exception {
        PdfPTable t = table(4, "IOC", "Tipo", "Riesgo", "Fuentes");
        m.iocs().stream().limit(limit).forEach(e -> {
            t.addCell(cell(e.getValue()));
            t.addCell(cell(e.getType().label()));
            t.addCell(cell(String.valueOf((int) e.getRiskScore())));
            t.addCell(cell(String.join(", ", e.getSources())));
        });
        doc.add(t);
    }

    private void actorProfile(Document doc, ReportModel m) throws Exception {
        if (m.actors().isEmpty()) {
            doc.add(par("No hay actores identificados en el caso."));
            return;
        }
        for (var a : m.actors()) {
            doc.add(h2(a.name()));
            doc.add(par("Alias: " + a.aliases() + " - Origen: " + a.origin() + " - Riesgo: " + (int) a.risk() + "/100"));
            doc.add(par(a.description()));
            doc.add(par("Técnicas vinculadas en el caso: " + a.techniques()));
        }
    }

    private void timeline(Document doc, ReportModel m) throws Exception {
        doc.add(h2("Cronología"));
        PdfPTable t = table(2, "Instante", "Evento");
        t.addCell(cell(ReportGenerator.TS.format(m.investigation().getCreatedAt())));
        t.addCell(cell("Apertura del caso " + m.investigation().getId()));
        m.graph().getNodes().stream()
                .map(n -> n.getEntity())
                .sorted((x, y) -> x.getFirstSeen().compareTo(y.getFirstSeen()))
                .limit(60)
                .forEach(e -> {
                    t.addCell(cell(ReportGenerator.TS.format(e.getFirstSeen())));
                    t.addCell(cell("Observado " + e.getType().label() + " " + e.getValue()));
                });
        doc.add(t);
    }

    private void attackSurface(Document doc, ReportModel m) throws Exception {
        doc.add(h2("Activos expuestos"));
        PdfPTable t = table(3, "Tipo", "Activo", "Riesgo");
        m.inventory().stream()
                .filter(e -> e.getType().name().matches("DOMAIN|IPV4|IPV6|URL|PORT|CERTIFICATE|NETBLOCK"))
                .forEach(e -> {
                    t.addCell(cell(e.getType().label()));
                    t.addCell(cell(e.getValue()));
                    t.addCell(cell(String.valueOf((int) e.getRiskScore())));
                });
        doc.add(t);
    }

    // ---- helpers ----

    private Paragraph h2(String text) {
        Paragraph p = new Paragraph(text, H2);
        p.setSpacingBefore(12);
        p.setSpacingAfter(5);
        return p;
    }

    private Paragraph par(String text) {
        Paragraph p = new Paragraph(text, BODY);
        p.setSpacingAfter(6);
        return p;
    }

    private Paragraph bullet(String text) {
        Paragraph p = new Paragraph("- " + text, BODY);
        p.setIndentationLeft(10);
        p.setSpacingAfter(3);
        return p;
    }

    private PdfPTable table(int cols, String... headers) {
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setSpacingAfter(8);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, BOLD));
            c.setBackgroundColor(new Color(0xea, 0xee, 0xf2));
            c.setPadding(5);
            t.addCell(c);
        }
        return t;
    }

    private PdfPCell cell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, BODY));
        c.setPadding(4);
        return c;
    }

    /** Cabecera, pie y marca de agua CONFIDENCIAL. */
    private static class Branding extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContentUnder();
            cb.saveState();
            cb.beginText();
            cb.setFontAndSize(BaseFontHolder.get(), 42);
            cb.setColorFill(new Color(0xf0, 0xf2, 0xf4));
            cb.showTextAligned(Element.ALIGN_CENTER, "CONFIDENCIAL", 300, 300, 45);
            cb.endText();
            cb.restoreState();

            cb.saveState();
            cb.beginText();
            cb.setFontAndSize(BaseFontHolder.get(), 7.5f);
            cb.setColorFill(new Color(0x57, 0x60, 0x6a));
            cb.showTextAligned(Element.ALIGN_LEFT, "ESTOA - Plataforma de inteligencia de amenazas", 52, 30, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, "Página " + writer.getPageNumber(), 543, 30, 0);
            cb.endText();
            cb.restoreState();
        }
    }

    static final class BaseFontHolder {
        private static com.lowagie.text.pdf.BaseFont font;

        static com.lowagie.text.pdf.BaseFont get() {
            if (font == null) {
                try {
                    font = com.lowagie.text.pdf.BaseFont.createFont(com.lowagie.text.pdf.BaseFont.HELVETICA,
                            com.lowagie.text.pdf.BaseFont.CP1252, com.lowagie.text.pdf.BaseFont.NOT_EMBEDDED);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return font;
        }
    }
}
