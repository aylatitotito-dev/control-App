package com.appcontrol.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.AppUsageJtMa;
import com.appcontrol.service.InstalledAppsServiceJtMa;
import com.appcontrol.service.InstalledAppsServiceJtMa.InstalledAppJt;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.service.UsageServiceJt;

/** Dibuja la pantalla Aplicaciones en lista, iconos medianos y pequenos (target/ui/apps-*.png). */
class AppsPageRenderJtTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void renderizaLasTresVistas() throws Exception {
        for (boolean darkJtMa : new boolean[] {false, true}) {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    new ThemeManagerMa().applyJt(darkJtMa);
                    renderJt(darkJtMa);
                } catch (Exception exJtMa) {
                    throw new RuntimeException(exJtMa);
                }
            });
        }
        assertTrue(new File("target/ui/apps-lista-claro.png").length() > 1000);
        assertTrue(new File("target/ui/apps-medianos-oscuro.png").length() > 1000);
    }

    private void renderJt(boolean darkJtMa) throws Exception {
        InstalledAppsServiceJtMa installedJtMa = mock(InstalledAppsServiceJtMa.class);
        when(installedJtMa.cached()).thenReturn(List.of(
                new InstalledAppJt("Android Studio", "studio64", "2026.1", "Google LLC", "18/08/2026", 2_300_000L, null),
                new InstalledAppJt("Antigravity", "antigravity", "1.23.2", "Google", "24/08/2026", 1_010_000L, null),
                new InstalledAppJt("Armoury Crate", "ArmouryCrate", "6.7.6.0", "ASUSTeK COMPUTER INC.", "20/10/2026",
                        694_000L, null),
                new InstalledAppJt("Asistencia rápida", "quickassist", "2.0.35.0", "Microsoft Corp.", "18/03/2026", 40L,
                        null),
                new InstalledAppJt("Google Chrome", "chrome", "130.0", "Google LLC", "12/11/2026", 612_000L, null),
                new InstalledAppJt("Steam", "steam", "9.9", "Valve", "02/09/2026", 1_800_000L, null),
                new InstalledAppJt("Visual Studio Code", "Code", "1.95", "Microsoft Corporation", "05/11/2026",
                        380_000L, null),
                new InstalledAppJt("Kleopatra", "kleopatra"), new InstalledAppJt("OBS Studio", "obs64", "30.2", "OBS", null,
                        null, null)));
        RuleServiceJt rulesJtMa = mock(RuleServiceJt.class);
        BlockRuleJtMa chromeRuleJtMa = new BlockRuleJtMa("APP", "chrome", "Google Chrome");
        chromeRuleJtMa.configureJt(RuleModeMa.QUOTA, 127, 0, 0, 180);
        BlockRuleJtMa steamRuleJtMa = new BlockRuleJtMa("APP", "steam", "Steam");
        steamRuleJtMa.configureJt(RuleModeMa.ALWAYS, 127, 0, 0, 0);
        BlockRuleJtMa codeRuleJtMa = new BlockRuleJtMa("APP", "code", "Visual Studio Code");
        codeRuleJtMa.configureJt(RuleModeMa.SCHEDULE, 0b0011111, 8 * 60, 14 * 60, 0);
        when(rulesJtMa.listJtMa("APP")).thenReturn(List.of(chromeRuleJtMa, steamRuleJtMa, codeRuleJtMa));
        AccountServiceMa accountJtMa = mock(AccountServiceMa.class);
        UsageServiceJt usageJtMa = mock(UsageServiceJt.class);
        LocalDate dayJtMa = LocalDate.now();
        AppUsageJtMa chromeJtMa = new AppUsageJtMa("chrome", dayJtMa);
        chromeJtMa.addSeconds(125 * 60);
        when(usageJtMa.topToday()).thenReturn(List.of(chromeJtMa));
        when(usageJtMa.todayByAppJtMa()).thenReturn(Map.of("chrome", 125 * 60L));

        String suffixJtMa = darkJtMa ? "oscuro" : "claro";
        AppsCatalogPageJtMa pageJtMa = new AppsCatalogPageJtMa(installedJtMa, rulesJtMa, accountJtMa, usageJtMa);
        pageJtMa.setViewMa(AppsCatalogPageJtMa.ViewJt.LIST);
        save(pageJtMa, "target/ui/apps-lista-" + suffixJtMa + ".png");
        pageJtMa.setViewMa(AppsCatalogPageJtMa.ViewJt.GRID4);
        save(pageJtMa, "target/ui/apps-medianos-" + suffixJtMa + ".png");
        pageJtMa.setViewMa(AppsCatalogPageJtMa.ViewJt.GRID9);
        save(pageJtMa, "target/ui/apps-pequenos-" + suffixJtMa + ".png");
    }

    private static void save(Container pageJtMa, String pathJtMa) throws Exception {
        pageJtMa.setSize(1000, 680);
        layoutAll(pageJtMa);
        layoutAll(pageJtMa); // segunda pasada: las vistas en cuadricula dependen del ancho ya asignado
        BufferedImage imgJtMa = new BufferedImage(1000, 680, BufferedImage.TYPE_INT_RGB);
        Graphics2D gJtMa = imgJtMa.createGraphics();
        gJtMa.setColor(UIManager.getColor("Panel.background"));
        gJtMa.fillRect(0, 0, 1000, 680);
        pageJtMa.printAll(gJtMa);
        gJtMa.dispose();
        File fileJtMa = new File(pathJtMa);
        fileJtMa.getParentFile().mkdirs();
        ImageIO.write(imgJtMa, "png", fileJtMa);
    }

    private static void layoutAll(Container cJtMa) {
        cJtMa.doLayout();
        for (Component childJtMa : cJtMa.getComponents()) {
            if (childJtMa instanceof Container ccJtMa) {
                layoutAll(ccJtMa);
            }
        }
    }
}
