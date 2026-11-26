package com.appcontrol;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class AppControlApplicationJtMa {

    public static void main(String[] args) {
        if (!SingleInstanceMa.acquireMa(args)) {
            System.exit(0);
        }
        // headless(false): la app de escritorio necesita abrir ventanas Swing.
        new SpringApplicationBuilder(AppControlApplicationJtMa.class).headless(false).run(args);
    }
}
