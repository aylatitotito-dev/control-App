package com.appcontrol.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/** El calendario del Historial se dibuja completo (mes, días, días futuros apagados). */
class DatePickerRenderMaTest {

    @Test
    void dibujaElCalendario() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                new ThemeManagerMa().applyJt(false);
                LocalDate todayMa = LocalDate.now();
                Container contentMa = DatePickerMa.panelMa(todayMa.minusDays(2),
                        todayMa.minusDays(HistoryDaysJt.KEEP_DAYS_JT), todayMa, pickedMa -> { });
                Dimension sizeMa = contentMa.getPreferredSize();
                contentMa.setSize(sizeMa);
                contentMa.doLayout();
                layoutAllMa(contentMa);
                BufferedImage imgMa = new BufferedImage(sizeMa.width, sizeMa.height, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D gMa = imgMa.createGraphics();
                gMa.setColor(java.awt.Color.WHITE);
                gMa.fillRect(0, 0, sizeMa.width, sizeMa.height);
                contentMa.paint(gMa);
                gMa.dispose();
                File fileMa = new File("target/ui/calendario-historial.png");
                fileMa.getParentFile().mkdirs();
                ImageIO.write(imgMa, "png", fileMa);
                assertTrue(fileMa.length() > 500);
            } catch (Exception exMa) {
                throw new RuntimeException(exMa);
            }
        });
    }

    private static void layoutAllMa(Container containerMa) {
        containerMa.doLayout();
        for (java.awt.Component childMa : containerMa.getComponents()) {
            if (childMa instanceof Container innerMa) {
                layoutAllMa(innerMa);
            }
        }
    }
}
