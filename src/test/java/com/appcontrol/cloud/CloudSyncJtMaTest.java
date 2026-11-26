package com.appcontrol.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.cloud.CloudDtoJt.RuleDtoJt;
import com.appcontrol.cloud.CloudDtoJt.SyncMembershipMa;
import com.appcontrol.cloud.CloudDtoJt.SyncOutJt;
import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.InstalledAppsServiceJtMa;

/** Lo que hace la app con lo que baja de la nube: guarda las reglas del grupo, las vence a su hora y las quita al terminar. */
@DataJpaTest
@Import(CloudServiceMa.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext
class CloudSyncJtMaTest {

    @Autowired
    private CloudServiceMa cloudJt;
    @Autowired
    private GroupRuleRepositoryMa rulesJt;
    @Autowired
    private CloudLinkRepositoryJtMa linksJt;
    @MockBean
    private CloudClientMa clientJt;
    @MockBean
    private AccountServiceMa accountJt;
    @MockBean
    private ActivityServiceMa activityJt;
    @MockBean
    private InstalledAppsServiceJtMa installedJt;

    @BeforeEach
    void cleanJt() {
        rulesJt.deleteAll();
        linksJt.deleteAll();
        when(clientJt.hasToken()).thenReturn(true);
        when(installedJt.cached()).thenReturn(List.of());
        when(activityJt.listJt(any(), any())).thenReturn(List.of());
    }

    private static RuleDtoJt rob() {
        return new RuleDtoJt("APP", "roblox", "Roblox", "ALWAYS", "ACTIVE", 127, 0, 0, 0);
    }

    private static SyncMembershipMa member(String statusJt, Instant untilJt, List<RuleDtoJt> rulesJt) {
        return new SyncMembershipMa(5L, 1L, "Casa", "Papá", "MEMBER", "ACTIVE", statusJt,
                untilJt == null ? "PERMANENT" : "TIMED", Instant.now().minusSeconds(60), untilJt, false, null, rulesJt);
    }

    private void serverSays(Instant serverTimeJt, SyncMembershipMa... membersJt) {
        when(clientJt.sync()).thenReturn(new SyncOutJt(serverTimeJt, List.of(membersJt)));
    }

    @Test
    void guardaLasReglasDelGrupoYLosMotoresLasVen() {
        serverSays(Instant.now(), member("ACCEPTED", null, List.of(rob())));
        cloudJt.syncNowMa();

        List<BlockRuleJtMa> activeJt = cloudJt.activeJt(BlockRuleJtMa.APP);
        assertEquals(1, activeJt.size());
        assertEquals("roblox", activeJt.get(0).getTargetKey());
        assertEquals(RuleModeMa.ALWAYS, activeJt.get(0).getMode());
        assertTrue(cloudJt.activeJt(BlockRuleJtMa.SITE).isEmpty());
        assertEquals(1, cloudJt.controlledByMa().size());
        // no se mezclan con las reglas locales: viven en su propia tabla
        assertEquals(1, rulesJt.count());
    }

    @Test
    void siElControlNoEstaAceptadoNoHayReglas() {
        serverSays(Instant.now(), member("REQUESTED", null, List.of(rob())));
        cloudJt.syncNowMa();
        assertTrue(cloudJt.activeJt(BlockRuleJtMa.APP).isEmpty());
        assertEquals(1, cloudJt.pendingRequestsMa().size());
        assertTrue(cloudJt.controlledByMa().isEmpty());
    }

    @Test
    void alTerminarElControlSeQuitanLasReglas() {
        serverSays(Instant.now(), member("ACCEPTED", null, List.of(rob())));
        cloudJt.syncNowMa();
        assertEquals(1, cloudJt.activeJt(BlockRuleJtMa.APP).size());

        serverSays(Instant.now(), member("NONE", null, List.of()));
        cloudJt.syncNowMa();
        assertTrue(cloudJt.activeJt(BlockRuleJtMa.APP).isEmpty());
        assertEquals(0, rulesJt.count());
    }

    @Test
    void laReglaPorHorarioVenceSolaAunqueNoHayaInternet() {
        Instant untilJt = Instant.now().plusSeconds(120);
        serverSays(Instant.now(), member("ACCEPTED", untilJt, List.of(rob())));
        cloudJt.syncNowMa();
        assertEquals(1, cloudJt.activeJt(BlockRuleJtMa.APP).size());

        // Sin volver a sincronizar, si el reloj del servidor ya paso la hora de fin, la regla no se aplica.
        serverSays(Instant.now().plusSeconds(3600), member("ACCEPTED", untilJt, List.of(rob())));
        cloudJt.syncNowMa();
        assertTrue(cloudJt.nowMa().isAfter(untilJt));
        assertTrue(cloudJt.activeJt(BlockRuleJtMa.APP).isEmpty());
        assertTrue(cloudJt.controlledByMa().isEmpty());
    }

    @Test
    void adelantarElRelojDeLaPcNoSirveSiElServidorDiceOtraHora() {
        // El servidor dice que son las 15:00 pero la PC "cree" que es una hora despues: manda el servidor.
        Instant serverNowJt = Instant.now().minus(Duration.ofHours(1));
        Instant untilJt = serverNowJt.plusSeconds(1800);
        serverSays(serverNowJt, member("ACCEPTED", untilJt, List.of(rob())));
        cloudJt.syncNowMa();
        assertEquals(1, cloudJt.activeJt(BlockRuleJtMa.APP).size(), "para el servidor aun faltan 30 minutos");
    }

    @Test
    void soloSubeDatosSiAlguienControlaConPermiso() {
        serverSays(Instant.now(), member("NONE", null, List.of()));
        cloudJt.syncNowMa();
        verify(clientJt, never()).putUsage(any());
        verify(clientJt, never()).putEvents(any());

        serverSays(Instant.now(), member("ACCEPTED", null, List.of(rob())));
        cloudJt.syncNowMa();
        verify(clientJt).putUsage(any());
        verify(clientJt).putEvents(any());
    }

    @Test
    void sinInternetSeConservanLasReglasYaGuardadas() {
        serverSays(Instant.now(), member("ACCEPTED", null, List.of(rob())));
        cloudJt.syncNowMa();
        when(clientJt.sync()).thenThrow(new CloudClientMa.CloudExceptionMa(0, true, "Sin conexión"));
        cloudJt.syncJt();
        assertEquals(1, cloudJt.activeJt(BlockRuleJtMa.APP).size());
        assertTrue(!cloudJt.isOnlineMa());
    }
}
