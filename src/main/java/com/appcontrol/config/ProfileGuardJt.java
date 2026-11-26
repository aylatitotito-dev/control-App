package com.appcontrol.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Valida al arrancar que el perfil activo (desktop/local/azure) sea coherente con el host de la BD.
 * Falla rapido para evitar escribir en la BD equivocada.
 */
@Component
public class ProfileGuardJt {

    private static final Logger logJtMa = LoggerFactory.getLogger(ProfileGuardJt.class);

    private final Environment envJtMa;
    private final String urlJtMa;

    public ProfileGuardJt(Environment envJtMa, @Value("${spring.datasource.url}") String urlJtMa) {
        this.envJtMa = envJtMa;
        this.urlJtMa = urlJtMa;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkOnReadyJtMa() {
        String[] activeJtMa = envJtMa.getActiveProfiles();
        // Sin perfil activo explicito, Spring usa spring.profiles.default (local).
        List<String> profilesJtMa = Arrays.asList(activeJtMa.length > 0 ? activeJtMa : envJtMa.getDefaultProfiles());
        validateJtMa(profilesJtMa, urlJtMa);
        logJtMa.info("Perfil {} -> BD {}", profilesJtMa, urlJtMa);
    }

    /** Estatico para probarlo sin contexto Spring ni BD. */
    public static void validateJtMa(List<String> profilesJtMa, String urlJtMa) {
        boolean localHostJtMa = urlJtMa.contains("//localhost") || urlJtMa.contains("//127.0.0.1");
        boolean h2FileJtMa = urlJtMa.startsWith("jdbc:h2:file:");
        if (profilesJtMa.contains("desktop") && !h2FileJtMa) {
            throw new IllegalStateException("Perfil 'desktop' requiere H2 en archivo: " + urlJtMa);
        }
        if (profilesJtMa.contains("local") && !localHostJtMa) {
            throw new IllegalStateException("Perfil 'local' apunta a host no local: " + urlJtMa);
        }
        if (profilesJtMa.contains("azure") && (localHostJtMa || h2FileJtMa)) {
            throw new IllegalStateException("Perfil 'azure' apunta a BD local/embebida: " + urlJtMa);
        }
        if (!profilesJtMa.contains("desktop") && !profilesJtMa.contains("local") && !profilesJtMa.contains("azure")) {
            throw new IllegalStateException("Debe activarse un perfil: desktop, local o azure");
        }
    }
}
