package com.orion.reports;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Fachada de generacion de informes: dado un tipo, formato y modelo, produce
 * el artefacto en el flujo o ruta indicados.
 */
public class ReportGenerator {

    public static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    public void generate(ReportType type, ReportFormat format, ReportModel model, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            generate(type, format, model, out);
        }
    }

    public void generate(ReportType type, ReportFormat format, ReportModel model, OutputStream out) throws IOException {
        switch (format) {
            case HTML -> out.write(new HtmlExporter().render(type, model).getBytes(StandardCharsets.UTF_8));
            case PDF -> new PdfExporter().render(type, model, out);
            case CSV -> out.write(new DataExporter().csv(type, model).getBytes(StandardCharsets.UTF_8));
            case JSON -> out.write(new DataExporter().json(type, model).getBytes(StandardCharsets.UTF_8));
            case PNG -> new GraphImageExporter().renderPng(model, 1600, 1000, out);
            case SVG -> out.write(new GraphImageExporter().renderSvg(model, 1600, 1000).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Nombre de archivo sugerido. */
    public static String filename(ReportType type, ReportFormat format) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        return "estoa-" + type.name().toLowerCase().replace('_', '-') + "-" + stamp + "." + format.extension();
    }
}
