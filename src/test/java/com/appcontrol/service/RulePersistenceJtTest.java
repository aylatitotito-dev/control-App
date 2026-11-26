package com.appcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.RuleStateJt;
import com.appcontrol.repository.BlockRuleRepositoryJt;

/** Contra una base de datos H2 real (en memoria): guardar, leer, pausar y borrar reglas de verdad. */
@DataJpaTest
@Import(RuleServiceJt.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext
class RulePersistenceJtTest {

    @Autowired
    private RuleServiceJt rules;

    @Autowired
    private BlockRuleRepositoryJt repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    @Test
    void guardaYLeeLaReglaDeFacebook() {
        BlockRuleJtMa saved = rules.saveJtMa(BlockRuleJtMa.SITE, "https://www.Facebook.com/", null,
                RuleModeMa.ALWAYS, BlockRuleJtMa.ALL_DAYS, 0, 0, 0);
        assertNotNull(saved.getId());

        List<BlockRuleJtMa> sites = rules.listJtMa(BlockRuleJtMa.SITE);
        assertEquals(1, sites.size());
        assertEquals("facebook.com", sites.get(0).getTargetKey());
        assertEquals(RuleModeMa.ALWAYS, sites.get(0).getMode());
        assertEquals(RuleStateJt.ACTIVE, sites.get(0).getState());
        assertTrue(rules.listJtMa(BlockRuleJtMa.APP).isEmpty());
    }

    @Test
    void editarUnaReglaNoLaDuplicaYConservaSuEstado() {
        BlockRuleJtMa first = rules.saveJtMa(BlockRuleJtMa.APP, "Steam.exe", "Steam", RuleModeMa.ALWAYS, 127, 0, 0, 0);
        rules.setStateJtMa(first.getId(), RuleStateJt.PAUSED, Instant.now().plusSeconds(600));
        rules.saveJtMa(BlockRuleJtMa.APP, "steam", "Steam", RuleModeMa.QUOTA, 127, 0, 0, 90);

        List<BlockRuleJtMa> apps = rules.listJtMa(BlockRuleJtMa.APP);
        assertEquals(1, apps.size());
        assertEquals(RuleModeMa.QUOTA, apps.get(0).getMode());
        assertEquals(90, apps.get(0).getQuotaMinutes());
        assertEquals(RuleStateJt.PAUSED, apps.get(0).getState());
    }

    @Test
    void pausaVencidaVuelveAActivaYBorrarFunciona() {
        BlockRuleJtMa rule = rules.saveJtMa(BlockRuleJtMa.SITE, "tiktok.com", null, RuleModeMa.SCHEDULE, 31, 8 * 60,
                14 * 60, 0);
        rules.setStateJtMa(rule.getId(), RuleStateJt.PAUSED, Instant.now().minusSeconds(5));
        assertEquals(1, rules.clearExpiredPausesJtMa(Instant.now()));
        assertEquals(RuleStateJt.ACTIVE, rules.findJtMa(BlockRuleJtMa.SITE, "tiktok.com").orElseThrow().getState());

        rules.deleteJtMa(rule.getId());
        assertFalse(rules.findJtMa(BlockRuleJtMa.SITE, "tiktok.com").isPresent());
    }
}
