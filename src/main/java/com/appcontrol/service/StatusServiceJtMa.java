package com.appcontrol.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.appcontrol.dto.StatusDtoJt;

/** Verifica contexto Spring y conectividad con MySQL (SELECT 1). */
@Service
public class StatusServiceJtMa {

    private final JdbcTemplate jdbcMa;
    private final Environment envMa;
    private final String appNameMa;

    public StatusServiceJtMa(JdbcTemplate jdbcMa, Environment envMa,
                         @Value("${spring.application.name}") String appNameMa) {
        this.jdbcMa = jdbcMa;
        this.envMa = envMa;
        this.appNameMa = appNameMa;
    }

    public StatusDtoJt checkMa() {
        String[] activeMa = envMa.getActiveProfiles();
        List<String> profilesMa = Arrays.asList(activeMa.length > 0 ? activeMa : envMa.getDefaultProfiles());
        try {
            Integer pingMa = jdbcMa.queryForObject("SELECT 1", Integer.class);
            boolean okMa = Integer.valueOf(1).equals(pingMa);
            return new StatusDtoJt(appNameMa, profilesMa, okMa, okMa ? "OK" : "Respuesta inesperada");
        } catch (Exception exMa) {
            // No exponer detalles internos (credenciales/host) en la respuesta.
            return new StatusDtoJt(appNameMa, profilesMa, false, "BD no disponible");
        }
    }
}
