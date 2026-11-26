package com.appcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.RuleStateJt;

class RuleLogicJtTest {

    private static final int WEEKDAYS_MA = 0b0011111;

    private static BlockRuleJtMa rule(RuleModeMa modeMa, int daysMa, int startMa, int endMa, int quotaMa) {
        BlockRuleJtMa ruleMa = new BlockRuleJtMa(BlockRuleJtMa.APP, "juego", "Juego");
        ruleMa.configureJt(modeMa, daysMa, startMa, endMa, quotaMa);
        return ruleMa;
    }

    private static Instant at(LocalDateTime dtMa) {
        return dtMa.atZone(ZoneId.systemDefault()).toInstant();
    }

    // 2026-11-23 es lunes
    private static final LocalDateTime MONDAY_10_MA = LocalDateTime.of(2026, 11, 23, 10, 0);

    @Test
    void bloqueoTotalSiempreBloquea() {
        var stMa = RuleLogicJtMa.evaluateMa(rule(RuleModeMa.ALWAYS, 127, 0, 0, 0), MONDAY_10_MA, at(MONDAY_10_MA), 0);
        assertTrue(stMa.enforcing());
        assertTrue(stMa.blockedNow());
    }

    @Test
    void horarioEscolarBloqueaEntreSemanaDentroDeLaFranja() {
        BlockRuleJtMa ruleMa = rule(RuleModeMa.SCHEDULE, WEEKDAYS_MA, 8 * 60, 14 * 60, 0);
        assertTrue(RuleLogicJtMa.blockedBySchedule(ruleMa, MONDAY_10_MA));
        assertFalse(RuleLogicJtMa.blockedBySchedule(ruleMa, MONDAY_10_MA.withHour(14)));      // 14:00 ya es libre
        assertFalse(RuleLogicJtMa.blockedBySchedule(ruleMa, MONDAY_10_MA.withHour(7)));
        assertFalse(RuleLogicJtMa.blockedBySchedule(ruleMa, LocalDateTime.of(2026, 11, 28, 10, 0))); // sabado
    }

    @Test
    void descansoNocturnoCruzaLaMedianoche() {
        BlockRuleJtMa ruleMa = rule(RuleModeMa.SCHEDULE, BlockRuleJtMa.ALL_DAYS, 22 * 60, 6 * 60, 0);
        assertTrue(RuleLogicJtMa.blockedBySchedule(ruleMa, MONDAY_10_MA.withHour(23)));
        assertTrue(RuleLogicJtMa.blockedBySchedule(ruleMa, MONDAY_10_MA.withHour(3)));
        assertFalse(RuleLogicJtMa.blockedBySchedule(ruleMa, MONDAY_10_MA.withHour(12)));
    }

    @Test
    void nocturnoSoloLosViernesBloqueaLaMadrugadaDelSabado() {
        int fridayMa = 1 << 4;
        BlockRuleJtMa ruleMa = rule(RuleModeMa.SCHEDULE, fridayMa, 22 * 60, 6 * 60, 0);
        assertTrue(RuleLogicJtMa.blockedBySchedule(ruleMa, LocalDateTime.of(2026, 11, 28, 2, 0)));   // sabado 02:00
        assertFalse(RuleLogicJtMa.blockedBySchedule(ruleMa, LocalDateTime.of(2026, 11, 29, 2, 0)));  // domingo 02:00
    }

    @Test
    void cuotaAvisaConCincoMinutosYBloqueaAlAgotarse() {
        BlockRuleJtMa ruleMa = rule(RuleModeMa.QUOTA, 127, 0, 0, 120);
        var okMa = RuleLogicJtMa.evaluateMa(ruleMa, MONDAY_10_MA, at(MONDAY_10_MA), 60 * 60);
        assertFalse(okMa.blockedNow());
        assertFalse(okMa.quotaWarn());
        var warnMa = RuleLogicJtMa.evaluateMa(ruleMa, MONDAY_10_MA, at(MONDAY_10_MA), 115 * 60 + 30);
        assertTrue(warnMa.quotaWarn());
        assertFalse(warnMa.blockedNow());
        var doneMa = RuleLogicJtMa.evaluateMa(ruleMa, MONDAY_10_MA, at(MONDAY_10_MA), 120 * 60);
        assertTrue(doneMa.blockedNow());
        assertTrue(doneMa.label().startsWith("Cuota agotada"));
    }

    @Test
    void pausaYDesactivadaNoSeAplican() {
        BlockRuleJtMa ruleMa = rule(RuleModeMa.ALWAYS, 127, 0, 0, 0);
        ruleMa.setStateJt(RuleStateJt.PAUSED, at(MONDAY_10_MA).plusSeconds(1800));
        var pausedMa = RuleLogicJtMa.evaluateMa(ruleMa, MONDAY_10_MA, at(MONDAY_10_MA), 0);
        assertFalse(pausedMa.enforcing());
        assertTrue(pausedMa.label().startsWith("Pausada hasta"));
        // pasada la hora de la pausa, vuelve a bloquear
        var afterMa = RuleLogicJtMa.evaluateMa(ruleMa, MONDAY_10_MA, at(MONDAY_10_MA).plusSeconds(3600), 0);
        assertTrue(afterMa.blockedNow());
        ruleMa.setStateJt(RuleStateJt.INACTIVE, null);
        assertFalse(RuleLogicJtMa.evaluateMa(ruleMa, MONDAY_10_MA, at(MONDAY_10_MA), 0).enforcing());
    }

    @Test
    void textosLegibles() {
        assertEquals("L–V", RuleLogicJtMa.daysTextMa(WEEKDAYS_MA));
        assertEquals("Todos los días", RuleLogicJtMa.daysTextMa(127));
        assertEquals("L, X, V", RuleLogicJtMa.daysTextMa(1 | 4 | 16));
        assertEquals("2 h", RuleLogicJtMa.minutesText(120));
        assertEquals("1 h 30 min", RuleLogicJtMa.minutesText(90));
        assertEquals("45 min", RuleLogicJtMa.minutesText(45));
    }

    @Test
    void programasProtegidosYDominiosSeValidan() {
        assertThrows(IllegalArgumentException.class, () -> RuleServiceJt.normalizeAppJtMa("explorer.exe"));
        assertThrows(IllegalArgumentException.class, () -> RuleServiceJt.normalizeAppJtMa("  "));
        assertEquals("steam", RuleServiceJt.normalizeAppJtMa("Steam.EXE"));
        assertEquals("pokerstars.com", RuleServiceJt.normalizeSiteJtMa("https://www.PokerStars.com:8080/lobby"));
    }
}
