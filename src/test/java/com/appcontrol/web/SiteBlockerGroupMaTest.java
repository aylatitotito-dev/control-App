package com.appcontrol.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.appcontrol.cloud.GroupRulesSourceMa;
import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.RuleServiceJt;

/** Un sitio bloqueado por un grupo se bloquea igual que uno bloqueado en este equipo. */
class SiteBlockerGroupMaTest {

    @Test
    void bloqueaUnSitioQueVieneDeUnGrupo() {
        RuleServiceJt serviceMa = mock(RuleServiceJt.class);
        when(serviceMa.listJtMa("SITE")).thenReturn(List.of());
        BlockRuleJtMa fromGroupMa = new BlockRuleJtMa(BlockRuleJtMa.SITE, "tiktok.com", "tiktok.com");
        fromGroupMa.configureJt(RuleModeMa.ALWAYS, BlockRuleJtMa.ALL_DAYS, 0, 0, 0);
        GroupRulesSourceMa sourceMa = mock(GroupRulesSourceMa.class);
        when(sourceMa.activeJt("SITE")).thenReturn(List.of(fromGroupMa));

        SiteBlockerMa blockerMa = new SiteBlockerMa(serviceMa, mock(ActivityServiceMa.class), 0, true);
        blockerMa.setGroupRulesJt(sourceMa);

        assertTrue(blockerMa.isBlockedJt("tiktok.com"));
        assertTrue(blockerMa.isBlockedJt("www.tiktok.com"));
        assertFalse(blockerMa.isBlockedJt("wikipedia.org"));
    }

    @Test
    void sinNubeSigueFuncionandoSoloConReglasLocales() {
        RuleServiceJt serviceMa = mock(RuleServiceJt.class);
        BlockRuleJtMa localMa = new BlockRuleJtMa(BlockRuleJtMa.SITE, "facebook.com", "facebook.com");
        localMa.configureJt(RuleModeMa.ALWAYS, BlockRuleJtMa.ALL_DAYS, 0, 0, 0);
        when(serviceMa.listJtMa("SITE")).thenReturn(List.of(localMa));

        SiteBlockerMa blockerMa = new SiteBlockerMa(serviceMa, mock(ActivityServiceMa.class), 0, true);

        assertTrue(blockerMa.isBlockedJt("facebook.com"));
        assertFalse(blockerMa.isBlockedJt("tiktok.com"));
    }
}
