package com.appcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import com.appcontrol.dto.StatusDtoJt;

class StatusServiceJtMaTest {

    private final MockEnvironment envMa = new MockEnvironment();

    StatusServiceJtMaTest() {
        envMa.setActiveProfiles("local");
    }

    @Test
    void dbUpCuandoSelectDevuelveUno() {
        JdbcTemplate jdbcMa = mock(JdbcTemplate.class);
        when(jdbcMa.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        StatusDtoJt dtoMa = new StatusServiceJtMa(jdbcMa, envMa, "appcontrol").checkMa();

        assertTrue(dtoMa.dbUp());
        assertEquals("appcontrol", dtoMa.app());
        assertEquals("local", dtoMa.profiles().get(0));
    }

    @Test
    void dbDownSinExponerDetalles() {
        JdbcTemplate jdbcMa = mock(JdbcTemplate.class);
        when(jdbcMa.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new CannotGetJdbcConnectionException("password=secreto"));

        StatusDtoJt dtoMa = new StatusServiceJtMa(jdbcMa, envMa, "appcontrol").checkMa();

        assertFalse(dtoMa.dbUp());
        assertEquals("BD no disponible", dtoMa.dbMessage());
    }
}
