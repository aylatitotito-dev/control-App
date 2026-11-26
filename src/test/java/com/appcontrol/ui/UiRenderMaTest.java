package com.appcontrol.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.AppUsageJtMa;
import com.appcontrol.model.UserGroupJt;
import com.appcontrol.model.SeenSiteJtMa;
import com.appcontrol.model.ActivityEventJt;
import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.GroupServiceMa;
import com.appcontrol.service.InstalledAppsServiceJtMa;
import com.appcontrol.service.InstalledAppsServiceJtMa.InstalledAppJt;
import com.appcontrol.service.VisitedSitesServiceMa;
import com.appcontrol.tracker.RefreshCoordinatorMa;
import com.appcontrol.web.SiteBlockingJtMa;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.service.UsageServiceJt;

/** Dibuja la interfaz con datos de ejemplo en tema claro y oscuro (target/ui/*.png) para revisar el diseno. */
class UiRenderMaTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void renderizaTemaClaroYOscuro() throws Exception {
        for (boolean darkJt : new boolean[] {false, true}) {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    new ThemeManagerMa().applyJt(darkJt);
                    renderJt(darkJt);
                } catch (Exception exJt) {
                    throw new RuntimeException(exJt);
                }
            });
        }
        assertTrue(new File("target/ui/resumen-claro.png").length() > 1000);
        assertTrue(new File("target/ui/resumen-oscuro.png").length() > 1000);
    }

    @Test
    void formatoDeDuracion() {
        assertEquals("45 s", FmtJt.durationJtMa(45));
        assertEquals("12 min", FmtJt.durationJtMa(12 * 60));
        assertEquals("2 h 15 min", FmtJt.durationJtMa(2 * 3600 + 15 * 60));
    }

    private void renderJt(boolean darkJt) throws Exception {
        UsageServiceJt usageJt = mock(UsageServiceJt.class);
        LocalDate dayJt = LocalDate.now();
        AppUsageJtMa chromeJt = new AppUsageJtMa("chrome", dayJt);
        chromeJt.addSeconds(2 * 3600 + 5 * 60);
        AppUsageJtMa steamJt = new AppUsageJtMa("steam", dayJt);
        steamJt.addSeconds(80 * 60);
        AppUsageJtMa codeJt = new AppUsageJtMa("Code", dayJt);
        codeJt.addSeconds(35 * 60);
        AppUsageJtMa wordJt = new AppUsageJtMa("WINWORD", dayJt);
        wordJt.addSeconds(9 * 60);
        List<AppUsageJtMa> topJt = List.of(chromeJt, steamJt, codeJt, wordJt);
        when(usageJt.topToday()).thenReturn(topJt);
        when(usageJt.totalTodaySeconds()).thenReturn(4 * 3600L + 9 * 60);
        when(usageJt.todayByAppJtMa()).thenReturn(Map.of("steam", 80 * 60L, "chrome", 125 * 60L, "code", 35 * 60L));

        RuleServiceJt rulesJt = mock(RuleServiceJt.class);
        BlockRuleJtMa chromeRuleJt = new BlockRuleJtMa("APP", "chrome", "Google Chrome");
        chromeRuleJt.configureJt(RuleModeMa.QUOTA, 127, 0, 0, 180);
        BlockRuleJtMa steamRuleJt = new BlockRuleJtMa("APP", "steam", "Steam");
        steamRuleJt.configureJt(RuleModeMa.QUOTA, 127, 0, 0, 100);
        BlockRuleJtMa codeRuleJt = new BlockRuleJtMa("APP", "code", "Visual Studio Code");
        codeRuleJt.configureJt(RuleModeMa.QUOTA, 127, 0, 0, 240);
        BlockRuleJtMa pokerAppJt = new BlockRuleJtMa("APP", "poker-app", "Poker App");
        pokerAppJt.configureJt(RuleModeMa.ALWAYS, 127, 0, 0, 0);
        BlockRuleJtMa gameJt = new BlockRuleJtMa("APP", "juego", "Juego de prueba");
        gameJt.configureJt(RuleModeMa.SCHEDULE, 0b0011111, 8 * 60, 14 * 60, 0);
        BlockRuleJtMa pokerSiteJt = new BlockRuleJtMa("SITE", "poker.net", "poker.net");
        pokerSiteJt.configureJt(RuleModeMa.ALWAYS, 127, 0, 0, 0);
        BlockRuleJtMa nightSiteJt = new BlockRuleJtMa("SITE", "tiktok.com", "tiktok.com");
        nightSiteJt.configureJt(RuleModeMa.SCHEDULE, 127, 22 * 60, 6 * 60, 0);
        when(rulesJt.listJtMa("APP")).thenReturn(List.of(chromeRuleJt, steamRuleJt, codeRuleJt, pokerAppJt, gameJt));
        when(rulesJt.listJtMa("SITE")).thenReturn(List.of(pokerSiteJt, nightSiteJt));
        AccountServiceMa accountJt = mock(AccountServiceMa.class);
        when(accountJt.usernameJt()).thenReturn("Juan Torres");
        when(accountJt.hasAccount()).thenReturn(true);
        GroupServiceMa groupsJt = mock(GroupServiceMa.class);
        when(groupsJt.list()).thenReturn(List.of(new UserGroupJt("Mi familia", "K7QM-X2PD")));

        InstalledAppsServiceJtMa installedJt = mock(InstalledAppsServiceJtMa.class);
        when(installedJt.cached()).thenReturn(List.of(new InstalledAppJt("Access", "MSACCESS"),
                new InstalledAppJt("Brave", "brave"), new InstalledAppJt("Google Chrome", "chrome"),
                new InstalledAppJt("Kleopatra", "kleopatra"), new InstalledAppJt("OBS Studio", "obs64"),
                new InstalledAppJt("Steam", "steam"), new InstalledAppJt("Visual Studio Code", "Code")));
        VisitedSitesServiceMa visitedJt = mock(VisitedSitesServiceMa.class);
        when(visitedJt.list()).thenReturn(List.of(new SeenSiteJtMa("youtube.com", Instant.now().minusSeconds(300)),
                new SeenSiteJtMa("github.com", Instant.now().minusSeconds(3 * 3600)),
                new SeenSiteJtMa("poker.net", Instant.now().minusSeconds(26 * 3600))));
        SiteBlockingJtMa blockingJt = mock(SiteBlockingJtMa.class);
        when(blockingJt.statusJtMa()).thenReturn(SiteBlockingJtMa.StatusMa.ACTIVE);
        when(blockingJt.isEnabledJtMa()).thenReturn(true);
        ActivityServiceMa activityJt = mock(ActivityServiceMa.class);
        Instant baseJt = Instant.now().minusSeconds(3 * 3600);
        ActivityEventJt chromeEvJt = new ActivityEventJt("APP", "chrome", "chrome", baseJt);
        chromeEvJt.extendTo(baseJt.plusSeconds(25 * 60), false);
        ActivityEventJt ytEvJt = new ActivityEventJt("SITE", "youtube.com", "youtube.com", baseJt.plusSeconds(1800));
        ytEvJt.extendTo(baseJt.plusSeconds(1800 + 12 * 60), true);
        ytEvJt.extendTo(baseJt.plusSeconds(1800 + 12 * 60), true);
        ActivityEventJt blockEvJt = new ActivityEventJt("BLOCK_SITE", "facebook.com", "facebook.com",
                baseJt.plusSeconds(3600));
        blockEvJt.extendTo(baseJt.plusSeconds(3620), true);
        ActivityEventJt steamEvJt = new ActivityEventJt("BLOCK_APP", "steam", "steam", baseJt.plusSeconds(5400));
        ActivityEventJt codeEvJt = new ActivityEventJt("APP", "code", "Code", baseJt.plusSeconds(7200));
        codeEvJt.extendTo(baseJt.plusSeconds(7200 + 40 * 60), false);
        when(activityJt.listJt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(codeEvJt, steamEvJt, blockEvJt, ytEvJt, chromeEvJt));
        com.appcontrol.service.SummaryServiceMa summaryJt = mock(com.appcontrol.service.SummaryServiceMa.class);
        when(summaryJt.summarizeJt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(HistorySummaryRenderMaTest.sample());
        MainPanelJt panelJt = new MainPanelJt(usageJt, rulesJt, accountJt, groupsJt,
                installedJt, visitedJt, blockingJt, activityJt, mock(RefreshCoordinatorMa.class),
                mock(com.appcontrol.service.AppSettingsServiceMa.class), summaryJt, () -> { }, new ThemeManagerMa(),
                mock(com.appcontrol.cloud.CloudServiceMa.class));
        String suffixJt = darkJt ? "oscuro" : "claro";
        String[] namesJt = {"resumen", "aplicaciones", "sitios", "historial", "grupos", "ajustes"};
        List<JToggleButton> buttonsJt = findButtons(panelJt);
        for (int iJt = 0; iJt < namesJt.length; iJt++) {
            buttonsJt.get(iJt).doClick();
            save(panelJt, "target/ui/" + namesJt[iJt] + "-" + suffixJt + ".png");
        }
    }

    private static List<JToggleButton> findButtons(Container rootJt) {
        java.util.ArrayList<JToggleButton> outJt = new java.util.ArrayList<>();
        collect(rootJt, outJt);
        return outJt;
    }

    private static void collect(Container cJt, List<JToggleButton> outJt) {
        for (Component childJt : cJt.getComponents()) {
            if (childJt instanceof JToggleButton bJt) {
                outJt.add(bJt);
            } else if (childJt instanceof Container ccJt) {
                collect(ccJt, outJt);
            }
        }
    }

    private static void save(MainPanelJt panelJt, String pathJt) throws Exception {
        panelJt.setSize(1060, 700);
        layoutAll(panelJt);
        BufferedImage imgJt = new BufferedImage(1060, 700, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D gJt = imgJt.createGraphics();
        gJt.setColor(javax.swing.UIManager.getColor("Panel.background"));
        gJt.fillRect(0, 0, 1060, 700);
        panelJt.printAll(gJt);
        gJt.dispose();
        File fileJt = new File(pathJt);
        fileJt.getParentFile().mkdirs();
        ImageIO.write(imgJt, "png", fileJt);
    }

    private static void layoutAll(Container cJt) {
        cJt.doLayout();
        for (Component childJt : cJt.getComponents()) {
            if (childJt instanceof Container ccJt) {
                layoutAll(ccJt);
            }
        }
    }
}
