package com.appcontrol.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.RuleStateJt;
import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.NotificationServiceJtMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.service.UsageServiceJt;

/** Prueba real: lanza un proceso y comprueba que el motor lo cierra cuando hay una regla activa (solo Windows). */
class RuleEnforcerJtTest {

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    private static Process startPing() throws Exception {
        return new ProcessBuilder("ping", "-n", "120", "127.0.0.1").redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static final ActivityServiceMa ACTIVITY = mock(ActivityServiceMa.class);

    private static RuleEnforcerMa enforcer(BlockRuleJtMa rule, List<String> notes) {
        RuleServiceJt rules = mock(RuleServiceJt.class);
        when(rules.listJtMa("APP")).thenReturn(List.of(rule));
        UsageServiceJt usage = mock(UsageServiceJt.class);
        when(usage.todayByAppJtMa()).thenReturn(Map.of());
        NotificationServiceJtMa notices = new NotificationServiceJtMa();
        notices.setHandlerMa((title, text) -> notes.add(text));
        return new RuleEnforcerMa(rules, usage, notices, ACTIVITY, 800);
    }

    @Test
    void cierraUnProgramaConBloqueoTotal() throws Exception {
        Assumptions.assumeTrue(windows());
        BlockRuleJtMa rule = new BlockRuleJtMa("APP", "ping", "Ping de prueba");
        rule.configureJt(RuleModeMa.ALWAYS, 127, 0, 0, 0);
        List<String> notes = new ArrayList<>();
        RuleEnforcerMa enforcer = enforcer(rule, notes);

        Process proc = startPing();
        try {
            Thread.sleep(500);
            assertTrue(proc.isAlive());
            enforcer.enforceJtMa();          // pide el cierre
            Thread.sleep(1200);
            enforcer.enforceJtMa();          // pasado el plazo, lo fuerza
            assertTrue(proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS), "el programa debia cerrarse");
            assertEquals(1, notes.size());
            verify(ACTIVITY).recordBlockJt(eq("BLOCK_APP"), eq("ping"), any(), any());
            assertTrue(notes.get(0).contains("bloqueada por regla activa"));
        } finally {
            proc.destroyForcibly();
        }
    }

    @Test
    void noTocaUnProgramaConLaReglaPausada() throws Exception {
        Assumptions.assumeTrue(windows());
        BlockRuleJtMa rule = new BlockRuleJtMa("APP", "ping", "Ping de prueba");
        rule.configureJt(RuleModeMa.ALWAYS, 127, 0, 0, 0);
        rule.setStateJt(RuleStateJt.PAUSED, java.time.Instant.now().plusSeconds(600));
        List<String> notes = new ArrayList<>();
        RuleEnforcerMa enforcer = enforcer(rule, notes);

        Process proc = startPing();
        try {
            Thread.sleep(500);
            enforcer.enforceJtMa();
            Thread.sleep(1500);
            enforcer.enforceJtMa();
            assertTrue(proc.isAlive(), "con la regla pausada no debe cerrarse");
            assertTrue(notes.isEmpty());
        } finally {
            proc.destroyForcibly();
        }
    }

    @Test
    void jamasCierraProgramasProtegidos() {
        assertTrue(RuleServiceJt.PROTECTED_JTMA.contains("explorer"));
        assertTrue(RuleServiceJt.PROTECTED_JTMA.contains("java"));
        assertFalse(RuleServiceJt.PROTECTED_JTMA.contains("steam"));
    }
}
