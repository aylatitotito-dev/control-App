package com.appcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.model.ActivityEventJt;
import com.appcontrol.repository.ActivityEventRepositoryMa;

/** El historial contra una base de datos real: agrupa sesiones y visitas, no duplica, y cuenta bloqueos. */
@DataJpaTest
@Import(ActivityServiceMa.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext
class ActivityServiceMaTest {

    private static final Instant T0 = Instant.parse("2026-11-24T15:00:00Z");
    private static final Instant ALL_FROM = T0.minus(Duration.ofDays(1));
    private static final Instant ALL_TO = T0.plus(Duration.ofDays(1));

    @Autowired
    private ActivityServiceMa activity;

    @Autowired
    private ActivityEventRepositoryMa repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    private static Instant at(long secondsFromT0) {
        return T0.plusSeconds(secondsFromT0);
    }

    @Test
    void lasMuestrasSeguidasDeUnaAppFormanUnaSesion() {
        activity.touchAppJt("chrome", "chrome", at(0));
        activity.touchAppJt("chrome", "chrome", at(5));
        activity.touchAppJt("chrome", "chrome", at(30));

        List<ActivityEventJt> events = activity.listJt(ALL_FROM, ALL_TO);
        assertEquals(1, events.size());
        assertEquals(30, events.get(0).duration().getSeconds());
        assertEquals("APP", events.get(0).getKind());
    }

    @Test
    void cambiarDeAppYVolverCreaSesionesSeparadas() {
        activity.touchAppJt("chrome", "chrome", at(0));
        activity.touchAppJt("chrome", "chrome", at(20));
        activity.touchAppJt("code", "Code", at(25));
        activity.touchAppJt("code", "Code", at(60));
        activity.touchAppJt("chrome", "chrome", at(65));

        List<ActivityEventJt> events = activity.listJt(ALL_FROM, ALL_TO);
        assertEquals(3, events.size());
        // mas reciente primero
        assertEquals("chrome", events.get(0).getTargetKey());
        assertEquals("code", events.get(1).getTargetKey());
        assertEquals("chrome", events.get(2).getTargetKey());
    }

    @Test
    void unaPausaLargaCierraLaSesion() {
        activity.touchAppJt("steam", "Steam", at(0));
        activity.touchAppJt("steam", "Steam", at(10));
        activity.touchAppJt("steam", "Steam", at(10 + 120));   // 2 min despues: otra sesion

        assertEquals(2, activity.listJt(ALL_FROM, ALL_TO).size());
    }

    @Test
    void lasVisitasSeguidasAUnSitioSeAgrupanYLasLejanasNo() {
        activity.recordSiteVisitsJt(Map.of("youtube.com",
                List.of(at(0), at(180), at(420), at(30 * 60), at(31 * 60))));

        List<ActivityEventJt> events = activity.listJt(ALL_FROM, ALL_TO);
        assertEquals(2, events.size());
        ActivityEventJt later = events.get(0);
        ActivityEventJt first = events.get(1);
        assertEquals(3, first.getHits());
        assertEquals(420, first.duration().getSeconds());
        assertEquals(2, later.getHits());
    }

    @Test
    void leerDosVecesElMismoHistorialNoDuplicaNada() {
        Map<String, List<Instant>> visits = Map.of("github.com", List.of(at(0), at(120), at(900)));
        activity.recordSiteVisitsJt(visits);
        activity.recordSiteVisitsJt(visits);
        activity.recordSiteVisitsJt(visits);

        List<ActivityEventJt> events = activity.listJt(ALL_FROM, ALL_TO);
        assertEquals(2, events.size());
        assertEquals(3, events.stream().mapToInt(ActivityEventJt::getHits).sum());
    }

    @Test
    void unaVisitaNuevaAlargaLaEntradaDeAntes() {
        activity.recordSiteVisitsJt(Map.of("github.com", List.of(at(0), at(60))));
        activity.recordSiteVisitsJt(Map.of("github.com", List.of(at(60 + 5 * 60))));

        List<ActivityEventJt> events = activity.listJt(ALL_FROM, ALL_TO);
        assertEquals(1, events.size());
        assertEquals(3, events.get(0).getHits());
    }

    @Test
    void losBloqueosRepetidosSeCuentanComoUnoYLosLejanosSonNuevos() {
        activity.recordBlockJt(ActivityEventJt.BLOCK_SITE, "facebook.com", "facebook.com", at(0));
        activity.recordBlockJt(ActivityEventJt.BLOCK_SITE, "facebook.com", "facebook.com", at(20));
        activity.recordBlockJt(ActivityEventJt.BLOCK_SITE, "facebook.com", "facebook.com", at(5 * 60));
        activity.recordBlockJt(ActivityEventJt.BLOCK_APP, "steam", "Steam", at(30));

        List<ActivityEventJt> events = activity.listJt(ALL_FROM, ALL_TO);
        assertEquals(3, events.size());
        long facebookJt = events.stream().filter(eJt -> eJt.getTargetKey().equals("facebook.com")).count();
        assertEquals(2, facebookJt);
        assertTrue(events.stream().anyMatch(eJt -> eJt.getKind().equals("BLOCK_SITE") && eJt.getHits() == 2));
    }

    @Test
    void volverAUnaPestanaYaAbiertaQuedaAnotado() {
        // 15:00 abre WhatsApp (el navegador lo anota como visita)
        activity.recordSiteVisitsJt(Map.of("whatsapp.com", List.of(at(0))));
        // sigue en WhatsApp 2 minutos (muestras de "pestana al frente")
        activity.touchSiteJt("whatsapp.com", at(60));
        activity.touchSiteJt("whatsapp.com", at(120));
        // se va a otro sitio 20 minutos
        for (long sec = 180; sec <= 20 * 60; sec += 300) {   // una muestra cada 5 minutos (en la vida real, cada 5 s)
            activity.touchSiteJt("bitlearning.com", at(sec));
        }
        // vuelve a WhatsApp SIN recargar: el navegador no anota nada, pero la pestana al frente si
        activity.touchSiteJt("whatsapp.com", at(21 * 60));
        activity.touchSiteJt("whatsapp.com", at(22 * 60));

        List<ActivityEventJt> events = activity.listJt(ALL_FROM, ALL_TO);
        List<ActivityEventJt> whatsapp = events.stream().filter(eJt -> eJt.getTargetKey().equals("whatsapp.com"))
                .toList();
        assertEquals(2, whatsapp.size(), "dos entradas de WhatsApp: la primera y la de cuando volvio");
        // listJt va del mas reciente al mas antiguo
        assertEquals(60, whatsapp.get(0).duration().getSeconds());
        assertEquals(120, whatsapp.get(1).duration().getSeconds());
        assertEquals(1, events.stream().filter(eJt -> eJt.getTargetKey().equals("bitlearning.com")).count());
    }

    @Test
    void lasMuestrasSeguidasDeUnSitioNoDuplicanLaEntrada() {
        activity.touchSiteJt("youtube.com", at(0));
        activity.touchSiteJt("youtube.com", at(5));
        activity.touchSiteJt("youtube.com", at(10));
        activity.recordSiteVisitsJt(Map.of("youtube.com", List.of(at(7))));   // ya cubierta
        assertEquals(1, activity.listJt(ALL_FROM, ALL_TO).size());
    }

    @Test
    void elDiaSeFiltraPorRango() {
        activity.touchAppJt("chrome", "chrome", at(0));
        activity.touchAppJt("code", "Code", T0.plus(Duration.ofDays(2)));

        assertEquals(1, activity.listJt(ALL_FROM, ALL_TO).size());
        assertEquals(2, activity.listJt(ALL_FROM, T0.plus(Duration.ofDays(3))).size());
    }

    @Test
    void loQueTieneMasDeNoventaDiasSeBorra() {
        Instant old = Instant.now().minus(Duration.ofDays(100));
        Instant recent = Instant.now().minus(Duration.ofDays(2));
        activity.touchAppJt("viejo", "viejo", old);
        activity.touchAppJt("reciente", "reciente", recent);

        activity.purgeOldJt();

        List<ActivityEventJt> left = repo.findAll();
        assertEquals(1, left.size());
        assertEquals("reciente", left.get(0).getTargetKey());
    }
}
