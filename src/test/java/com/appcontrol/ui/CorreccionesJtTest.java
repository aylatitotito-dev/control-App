package com.appcontrol.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.Test;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.RuleStateJt;
import com.appcontrol.service.RuleLogicJtMa;

/** La etiqueta de la cuota dice los minutos reales y las fechas salen en español sin depender del Java instalado. */
class CorreccionesJtTest {

    @Test
    void laEtiquetaDeLaCuotaBajaConElTiempo() {
        BlockRuleJtMa ruleJt = mock(BlockRuleJtMa.class);
        when(ruleJt.getState()).thenReturn(RuleStateJt.ACTIVE);
        when(ruleJt.getMode()).thenReturn(RuleModeMa.QUOTA);
        when(ruleJt.getQuotaMinutes()).thenReturn(5);
        RuleLogicJtMa.StatusMa statusJt = new RuleLogicJtMa.StatusMa(true, false, true, 175, "");
        assertEquals("Quedan 3 min", RuleActionsMa.chipMa(ruleJt, statusJt).text());
    }

    @Test
    void losDiasYMesesSalenEnEspanol() {
        LocalDate dateJt = LocalDate.of(2026, 11, 24);
        assertEquals("Martes 24/11", SpanishDatesJtMa.dayAndDateJtMa(dateJt));
        assertEquals("Mar 24", SpanishDatesJtMa.shortDayJtMa(dateJt));
        assertEquals("Noviembre", SpanishDatesJtMa.monthJtMa(Month.NOVEMBER));
    }
}
