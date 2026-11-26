package com.appcontrol.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ComboBoxModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

import com.appcontrol.service.InstalledAppsServiceJtMa;
import com.appcontrol.service.SummaryServiceMa;
import com.appcontrol.service.SummaryServiceMa.DayTotalJt;
import com.appcontrol.service.SummaryServiceMa.RankedJt;
import com.appcontrol.service.SummaryServiceMa.SummaryJt;

/** Dibuja el Resumen del Historial: un dia y la semana (target/ui/resumen-dias-*.png). */
class HistorySummaryRenderMaTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    /** Datos de ejemplo de una semana. */
    static SummaryJt sample() {
        LocalDate today = LocalDate.now();
        long[] seconds = {4 * 3600L + 9 * 60, 5 * 3600L + 40 * 60, 2 * 3600L, 0, 6 * 3600L + 15 * 60, 3 * 3600L + 30 * 60,
            2 * 3600L + 5 * 60};
        List<DayTotalJt> days = new ArrayList<>();
        long total = 0;
        for (int n = 0; n < 7; n++) {
            days.add(new DayTotalJt(today.minusDays(6 - n), seconds[n]));
            total += seconds[n];
        }
        return new SummaryJt(total, days,
                List.of(new RankedJt("chrome", "chrome", 9 * 3600L, 0, 180, 4),
                        new RankedJt("code", "Code", 7 * 3600L, 0, 0, 0),
                        new RankedJt("steam", "steam", 3 * 3600L, 0, 120, 1),
                        new RankedJt("winword", "WINWORD", 40 * 60L, 0, 0, 0)),
                List.of(new RankedJt("youtube.com", "youtube.com", 2 * 3600L, 41, 0, 0),
                        new RankedJt("whatsapp.com", "whatsapp.com", 50 * 60L, 12, 0, 0),
                        new RankedJt("github.com", "github.com", 0, 33, 0, 0),
                        new RankedJt("tiktok.com", "tiktok.com", 0, 2, 0, 0)));
    }

    private static void layoutAll(Container cJt) {
        cJt.doLayout();
        for (Component childJt : cJt.getComponents()) {
            if (childJt instanceof Container ccJt) {
                layoutAll(ccJt);
            }
        }
    }

    private static void save(Container panelJt, String nameJt) throws Exception {
        panelJt.setSize(1000, 760);
        layoutAll(panelJt);
        layoutAll(panelJt);
        BufferedImage imgJt = new BufferedImage(1000, 760, BufferedImage.TYPE_INT_RGB);
        Graphics2D gJt = imgJt.createGraphics();
        gJt.setColor(UIManager.getColor("Panel.background"));
        gJt.fillRect(0, 0, 1000, 760);
        panelJt.printAll(gJt);
        gJt.dispose();
        File fileJt = new File("target/ui/" + nameJt + ".png");
        fileJt.getParentFile().mkdirs();
        ImageIO.write(imgJt, "png", fileJt);
        assertTrue(fileJt.length() > 1000);
    }

    @Test
    void renderizaUnDiaYLaSemana() throws Exception {
        for (boolean darkJt : new boolean[] {false, true}) {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    new ThemeManagerMa().applyJt(darkJt);
                    SummaryServiceMa service = mock(SummaryServiceMa.class);
                    SummaryJt week = sample();
                    SummaryJt oneDay = new SummaryJt(week.perDay().get(6).seconds(),
                            List.of(week.perDay().get(6)), week.apps(), week.sites());
                    when(service.summarizeJt(any(), any())).thenAnswer(invJt -> {
                        LocalDate from = invJt.getArgument(0);
                        LocalDate to = invJt.getArgument(1);
                        return from.equals(to) ? oneDay : week;
                    });
                    InstalledAppsServiceJtMa installed = mock(InstalledAppsServiceJtMa.class);
                    when(installed.cached()).thenReturn(List.of());
                    ComboBoxModel<String> model = HistoryDaysJt.newModelJt();
                    HistorySummaryJt panel = new HistorySummaryJt(service, installed, model);
                    String suffix = darkJt ? "oscuro" : "claro";

                    model.setSelectedItem(model.getElementAt(model.getSize() - 1));   // Ultimos 7 dias
                    panel.refreshJt(true);
                    save(panel, "resumen-dias-semana-" + suffix);

                    model.setSelectedItem(model.getElementAt(1));                    // Ayer
                    panel.refreshJt(true);
                    save(panel, "resumen-dias-dia-" + suffix);
                } catch (Exception exJt) {
                    throw new RuntimeException(exJt);
                }
            });
        }
    }
}
