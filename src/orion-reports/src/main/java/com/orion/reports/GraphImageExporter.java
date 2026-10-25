package com.orion.reports;

import com.orion.graph.GraphEdge;
import com.orion.graph.GraphNode;
import com.orion.graph.OrionGraph;
import com.orion.graph.layout.CollisionResolver;
import com.orion.graph.layout.ForceDirectedLayout;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Renderizador del grafo a imagen raster (PNG via AWT, headless-safe) y
 * vectorial (SVG por construccion directa), reutilizando el motor de layout
 * force-directed y el resolvedor de colisiones.
 */
public class GraphImageExporter {

    public void renderPng(ReportModel m, int w, int h, OutputStream out) throws java.io.IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x0d, 0x11, 0x17));
        g.fillRect(0, 0, w, h);
        drawTitle(g, m, w);
        layout(m.graph(), w, h - 60, 30);
        draw(g, m.graph(), 30, 60, false);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    public String renderSvg(ReportModel m, int w, int h) {
        layout(m.graph(), w, h - 60, 30);
        StringBuilder b = new StringBuilder(64 * 1024);
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        b.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(w).append("\" height=\"").append(h)
         .append("\" viewBox=\"0 0 ").append(w).append(" ").append(h).append("\">\n");
        b.append("<rect width=\"100%\" height=\"100%\" fill=\"#0d1117\"/>\n");
        b.append("<text x=\"24\" y=\"34\" fill=\"#58a6ff\" font-family=\"sans-serif\" font-size=\"17\" font-weight=\"bold\">ESTOA - INTELIGENCIA DE AMENAZAS</text>\n");
        b.append("<text x=\"24\" y=\"52\" fill=\"#8b949e\" font-family=\"sans-serif\" font-size=\"12\">")
         .append(xml(m.investigation().getName())).append(" - ")
         .append(m.graph().nodeCount()).append(" entidades / ").append(m.graph().edgeCount()).append(" relaciones</text>\n");
        for (GraphEdge e : m.graph().getEdges()) {
            double x1 = e.getSource().getX(), y1 = e.getSource().getY() + 60;
            double x2 = e.getTarget().getX(), y2 = e.getTarget().getY() + 60;
            b.append("<line x1=\"").append(fmt(x1)).append("\" y1=\"").append(fmt(y1))
             .append("\" x2=\"").append(fmt(x2)).append("\" y2=\"").append(fmt(y2))
             .append("\" stroke=\"#30363d\" stroke-width=\"1.4\"/>");
            b.append("<text x=\"").append(fmt((x1 + x2) / 2)).append("\" y=\"").append(fmt((y1 + y2) / 2 - 3))
             .append("\" fill=\"#6e7681\" font-family=\"sans-serif\" font-size=\"9\">")
             .append(xml(e.getLabel())).append("</text>\n");
        }
        for (GraphNode n : m.graph().getNodes()) {
            String color = n.getEntity().getType().color();
            b.append("<circle cx=\"").append(fmt(n.getX())).append("\" cy=\"").append(fmt(n.getY() + 60))
             .append("\" r=\"").append(fmt(n.radius())).append("\" fill=\"").append(color)
             .append("\" stroke=\"#0d1117\" stroke-width=\"2\"/>");
            if (n.getEntity().getRiskScore() >= 80) {
                b.append("<circle cx=\"").append(fmt(n.getX())).append("\" cy=\"").append(fmt(n.getY() + 60))
                 .append("\" r=\"").append(fmt(n.radius() + 4)).append("\" fill=\"none\" stroke=\"#f85149\" stroke-width=\"1.5\" stroke-dasharray=\"3 3\"/>");
            }
            b.append("<text x=\"").append(fmt(n.getX())).append("\" y=\"").append(fmt(n.getY() + 60 + n.radius() + 13))
             .append("\" fill=\"#c9d1d9\" font-family=\"sans-serif\" font-size=\"10\" text-anchor=\"middle\">")
             .append(xml(truncate(n.getEntity().getLabel(), 24))).append("</text>\n");
        }
        b.append("</svg>\n");
        return b.toString();
    }

    private void drawTitle(Graphics2D g, ReportModel m, int w) {
        g.setColor(new Color(0x58, 0xa6, 0xff));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 17f));
        g.drawString("ESTOA - INTELIGENCIA DE AMENAZAS", 24, 34);
        g.setColor(new Color(0x8b, 0x94, 0x9e));
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
        g.drawString(m.investigation().getName() + " - " + m.graph().nodeCount()
                + " entidades / " + m.graph().edgeCount() + " relaciones", 24, 52);
    }

    private void draw(Graphics2D g, OrionGraph graph, int ox, int oy, boolean dummy) {
        // aristas
        g.setStroke(new BasicStroke(1.4f));
        for (GraphEdge e : graph.getEdges()) {
            g.setColor(new Color(0x30, 0x36, 0x3d));
            g.drawLine((int) e.getSource().getX(), (int) (e.getSource().getY() + oy),
                    (int) e.getTarget().getX(), (int) (e.getTarget().getY() + oy));
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        for (GraphEdge e : graph.getEdges()) {
            g.setColor(new Color(0x6e, 0x76, 0x81));
            int mx = (int) ((e.getSource().getX() + e.getTarget().getX()) / 2);
            int my = (int) ((e.getSource().getY() + e.getTarget().getY()) / 2 + oy) - 3;
            g.drawString(truncate(e.getLabel(), 22), mx, my);
        }
        // nodos
        for (GraphNode n : graph.getNodes()) {
            int x = (int) n.getX();
            int y = (int) (n.getY() + oy);
            if (n.getEntity().getRiskScore() >= 80) {
                g.setColor(new Color(0xf8, 0x51, 0x49));
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10, new float[]{3, 3}, 0));
                g.draw(new Ellipse2D.Double(x - n.radius() - 4, y - n.radius() - 4,
                        (n.radius() + 4) * 2, (n.radius() + 4) * 2));
            }
            g.setColor(Color.decode(n.getEntity().getType().color()));
            g.fillOval(x - (int) n.radius(), y - (int) n.radius(),
                    (int) n.radius() * 2, (int) n.radius() * 2);
            g.setColor(new Color(0x0d, 0x11, 0x17));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(x - (int) n.radius(), y - (int) n.radius(),
                    (int) n.radius() * 2, (int) n.radius() * 2);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.setColor(Color.BLACK);
            FontMetrics fm = g.getFontMetrics();
            String letter = n.getEntity().getType().name().substring(0, 1);
            g.drawString(letter, x - fm.stringWidth(letter) / 2, y + fm.getAscent() / 2 - 1);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.setColor(new Color(0xc9, 0xd1, 0xd9));
            String label = truncate(n.getEntity().getLabel(), 24);
            g.drawString(label, x - fm.stringWidth(label) / 2, y + (int) n.radius() + 13);
        }
    }

    private void layout(OrionGraph g, int w, int h, int margin) {
        List<GraphNode> nodes = new ArrayList<>(g.getNodes());
        if (nodes.isEmpty()) return;
        new ForceDirectedLayout().apply(g, w - margin * 2, h - margin * 2);
        CollisionResolver.resolve(g, 24);
        for (GraphNode n : nodes) {
            n.setX(n.getX() + margin);
            n.setY(n.getY() + margin);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String fmt(double d) {
        return String.format("%.1f", d);
    }

    private String xml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
