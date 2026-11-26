package com.appcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.appcontrol.model.ActivityEventJt;
import com.appcontrol.model.AppUsageJtMa;
import com.appcontrol.service.SummaryServiceMa.SummaryJt;

/** El resumen por dias: totales, orden, dias con cuota superada y tiempo de sitios. */
class SummaryServiceMaTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    private static AppUsageJtMa usage(String app, LocalDate day, long seconds) {
        AppUsageJtMa row = new AppUsageJtMa(app, day);
        row.addSeconds(seconds);
        return row;
    }

    private static ActivityEventJt site(String domain, long startSec, long endSec, int extraHits) {
        Instant base = Instant.parse("2026-11-24T15:00:00Z");
        ActivityEventJt event = new ActivityEventJt("SITE", domain, domain, base.plusSeconds(startSec));
        event.extendTo(base.plusSeconds(endSec), false);
        for (int n = 0; n < extraHits; n++) {
            event.extendTo(base.plusSeconds(endSec), true);
        }
        return event;
    }

    @Test
    void sumaPorAppPorDiaYOrdenaDeMayorAMenor() {
        List<AppUsageJtMa> rows = List.of(
                usage("chrome", MON, 3600), usage("Chrome", MON.plusDays(1), 1800), usage("code", MON, 7200),
                usage("steam", MON.plusDays(2), 600));
        SummaryJt summary = SummaryServiceMa.aggregateJt(rows, List.of(), MON, MON.plusDays(6), Map.of());

        assertEquals(3600 + 1800 + 7200 + 600, summary.totalSeconds());
        assertEquals(List.of("code", "chrome", "steam"), summary.apps().stream().map(rJt -> rJt.display().toLowerCase())
                .toList());
        assertEquals(5400, summary.apps().get(1).seconds(), "chrome y Chrome son la misma app");
        assertEquals(7, summary.perDay().size(), "siempre un valor por dia, aunque sea 0");
        assertEquals(7200 + 3600, summary.perDay().get(0).seconds());
        assertEquals(0, summary.perDay().get(6).seconds());
    }

    @Test
    void cuentaEnCuantosDiasSeLlegoALaCuota() {
        List<AppUsageJtMa> rows = List.of(
                usage("chrome", MON, 3 * 3600),                 // 3 h  (cuota 3 h: llega)
                usage("chrome", MON.plusDays(1), 3 * 3600 + 5), // supera
                usage("chrome", MON.plusDays(2), 2 * 3600));    // no llega
        SummaryJt summary = SummaryServiceMa.aggregateJt(rows, List.of(), MON, MON.plusDays(6), Map.of("chrome", 180));

        assertEquals(2, summary.apps().get(0).daysOver());
        assertEquals(180, summary.apps().get(0).quotaMinutes());
    }

    @Test
    void lasAppsSinCuotaNoTienenDiasSuperados() {
        SummaryJt summary = SummaryServiceMa.aggregateJt(List.of(usage("code", MON, 99999)), List.of(), MON, MON,
                Map.of("chrome", 60));
        assertEquals(0, summary.apps().get(0).daysOver());
        assertEquals(0, summary.apps().get(0).quotaMinutes());
    }

    @Test
    void ignoraLoQueEstaFueraDelPeriodo() {
        SummaryJt summary = SummaryServiceMa.aggregateJt(List.of(usage("chrome", MON.minusDays(1), 500),
                usage("chrome", MON, 100)), List.of(), MON, MON, Map.of());
        assertEquals(100, summary.totalSeconds());
    }

    @Test
    void sitiosPorTiempoYSiNoHayTiempoPorVisitas() {
        List<ActivityEventJt> events = List.of(
                site("youtube.com", 0, 1800, 2),     // 30 min, 3 visitas
                site("youtube.com", 4000, 4600, 0),  // 10 min, 1 visita
                site("github.com", 0, 0, 4),         // sin tiempo, 5 visitas
                site("tiktok.com", 0, 0, 0));        // sin tiempo, 1 visita
        SummaryJt summary = SummaryServiceMa.aggregateJt(List.of(), events, MON, MON, Map.of());

        assertEquals(List.of("youtube.com", "github.com", "tiktok.com"),
                summary.sites().stream().map(SummaryServiceMa.RankedJt::display).toList());
        assertEquals(2400, summary.sites().get(0).seconds());
        assertEquals(4, summary.sites().get(0).hits());
        assertEquals(5, summary.sites().get(1).hits());
        assertTrue(summary.sites().get(1).seconds() == 0);
    }
}
