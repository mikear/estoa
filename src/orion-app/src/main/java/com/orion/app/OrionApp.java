package com.orion.app;

import javafx.application.Application;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Punto de entrada de Estoa Threat Intelligence Platform. Arranca el contexto
 * Spring (composicion de servicios) y lanza la interfaz JavaFX.
 *
 * <p>Modos de uso:
 * <pre>
 *   java -jar estoa.jar                       GUI interactiva
 *   java -jar estoa.jar --demo                GUI con caso de demostracion
 *   java -jar estoa.jar --demo --screenshot DIR   capturas automaticas y salida
 *   java -jar estoa.jar --live OBS... [--out DIR] verificacion con datos reales
 * </pre>
 */
@SpringBootApplication
public class OrionApp {

    private static ConfigurableApplicationContext context;

    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains("--version")) {
            System.out.println("Estoa " + BuildInfo.version());
            return;
        }
        if (java.util.Arrays.asList(args).contains("--live")) {
            System.exit(LiveProbe.run(args));
        }
        System.setProperty("prism.order", "sw");
        SpringApplication app = new SpringApplication(OrionApp.class);
        app.setHeadless(false);
        context = app.run(args);
        Application.launch(OrionUi.class, args);
    }

    public static ConfigurableApplicationContext context() {
        return context;
    }
}
