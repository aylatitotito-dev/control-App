package com.appcontrol.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.appcontrol.cloud.CloudClientMa.CloudExceptionMa;
import com.appcontrol.cloud.CloudDtoJt.ControlInMa;
import com.appcontrol.cloud.CloudDtoJt.GroupOutMa;
import com.appcontrol.cloud.CloudDtoJt.RuleDtoJt;
import com.appcontrol.cloud.CloudDtoJt.SyncMembershipMa;
import com.appcontrol.cloud.CloudDtoJt.UsageDtoMa;
import com.appcontrol.service.AppSettingsServiceMa;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Prueba EN VIVO contra la API real de Azure, con dos equipos de prueba ("papa" y "hijo"). Solo corre si se define
 * la variable APPCONTROL_LIVE=1 (deja un grupo de prueba en la nube). No toca la base ni el token de la app real.
 */
@EnabledIfEnvironmentVariable(named = "APPCONTROL_LIVE", matches = "1")
class CloudLiveJtMaTest {

    private static CloudClientMa newClient(Map<String, String> storeJt) {
        AppSettingsServiceMa settingsJt = mock(AppSettingsServiceMa.class);
        when(settingsJt.get(anyString(), anyString())).thenAnswer(invJt -> storeJt.getOrDefault(invJt.getArgument(0),
                invJt.getArgument(1)));
        org.mockito.Mockito.doAnswer(invJt -> storeJt.put(invJt.getArgument(0), invJt.getArgument(1)))
                .when(settingsJt).set(anyString(), anyString());
        ObjectMapper mapperJt = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return new CloudClientMa(mapperJt, settingsJt, "https://appcontrol-api-50205.azurewebsites.net");
    }

    @Test
    void elFlujoCompletoContraAzure() {
        CloudClientMa dadJt = newClient(new HashMap<>());
        CloudClientMa kidJt = newClient(new HashMap<>());
        dadJt.saveToken(dadJt.register("PC de prueba (papá)").token());
        kidJt.saveToken(kidJt.register("PC de prueba (hijo)").token());

        GroupOutMa groupJt = dadJt.createGroup("Grupo de prueba en vivo");
        assertNotNull(groupJt.code());
        GroupOutMa joinedJt = kidJt.join(groupJt.code(), "Hijo de prueba");
        assertEquals("PENDING", joinedJt.status());
        assertEquals(1, dadJt.members(groupJt.id()).stream().filter(mJt -> "PENDING".equals(mJt.status())).count());

        dadJt.approve(groupJt.id(), joinedJt.memberId());
        dadJt.requestControl(groupJt.id(), joinedJt.memberId(),
                new ControlInMa("TIMED", null, Instant.now().plusSeconds(3600), true));
        // todavia no aceptó: el papa no puede poner reglas ni ver nada
        try {
            dadJt.setRules(groupJt.id(), joinedJt.memberId(), List.of());
            assertFalse(true, "debio rechazarlo");
        } catch (CloudExceptionMa exJt) {
            assertEquals(403, exJt.status());
        }
        SyncMembershipMa pendingJt = kidJt.sync().memberships().get(0);
        assertEquals("REQUESTED", pendingJt.controlStatus());

        kidJt.accept(joinedJt.memberId());
        dadJt.setRules(groupJt.id(), joinedJt.memberId(), List.of(
                new RuleDtoJt("APP", "roblox", "Roblox", "ALWAYS", "ACTIVE", 127, 0, 0, 0),
                new RuleDtoJt("SITE", "tiktok.com", "tiktok.com", "QUOTA", "ACTIVE", 127, 0, 0, 30)));
        SyncMembershipMa activeJt = kidJt.sync().memberships().get(0);
        assertEquals("ACCEPTED", activeJt.controlStatus());
        assertEquals(2, activeJt.rules().size());
        assertEquals(30, activeJt.rules().stream().filter(rJt -> "SITE".equals(rJt.kind())).findFirst().get()
                .quotaMinutes());

        kidJt.putUsage(List.of(new UsageDtoMa("chrome", "Chrome", LocalDate.now(), 1234)));
        assertEquals(1234, dadJt.usage(groupJt.id(), joinedJt.memberId(), LocalDate.now(), LocalDate.now()).get(0)
                .seconds());

        // el hijo (que tiene permiso) termina el control y las reglas desaparecen
        kidJt.endControl(joinedJt.memberId());
        assertTrue(kidJt.sync().memberships().get(0).rules().isEmpty());
        try {
            dadJt.usage(groupJt.id(), joinedJt.memberId(), LocalDate.now(), LocalDate.now());
            assertFalse(true, "debio rechazarlo");
        } catch (CloudExceptionMa exJt) {
            assertEquals(403, exJt.status());
        }
    }
}
