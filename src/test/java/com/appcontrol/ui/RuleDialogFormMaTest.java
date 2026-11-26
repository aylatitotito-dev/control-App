package com.appcontrol.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.repository.BlockRuleRepositoryJt;
import com.appcontrol.service.RuleServiceJt;

/** El editor de reglas debe ofrecer los TRES modos, tanto para aplicaciones como para sitios web. */
class RuleDialogFormMaTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static List<String> radios(Container rootMa) {
        List<String> outMa = new ArrayList<>();
        for (Component childMa : rootMa.getComponents()) {
            if (childMa instanceof JRadioButton rbMa) {
                outMa.add(rbMa.getText());
            } else if (childMa instanceof Container cMa) {
                outMa.addAll(radios(cMa));
            }
        }
        return outMa;
    }

    /** Dibuja el formulario al doble de tamaño (para que se lea bien en el manual). */
    private static void renderMa(java.awt.Container panelMa, String nameMa) {
        try {
            int widthMa = 620;
            int heightMa = panelMa.getPreferredSize().height + 20;
            panelMa.setSize(widthMa, heightMa);
            layoutAll(panelMa);
            layoutAll(panelMa);
            BufferedImage imgMa = new BufferedImage(widthMa * 2, heightMa * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D gMa = imgMa.createGraphics();
            gMa.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            gMa.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS,
                    java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            gMa.scale(2, 2);
            gMa.setColor(UIManager.getColor("Panel.background"));
            gMa.fillRect(0, 0, widthMa, heightMa);
            panelMa.printAll(gMa);
            gMa.dispose();
            File fileMa = new File("target/ui/" + nameMa + ".png");
            fileMa.getParentFile().mkdirs();
            ImageIO.write(imgMa, "png", fileMa);
        } catch (java.io.IOException exMa) {
            throw new java.io.UncheckedIOException(exMa);
        }
    }

    private static JRadioButton radioMa(Container rootMa, String textMa) {
        for (Component childMa : rootMa.getComponents()) {
            if (childMa instanceof JRadioButton rbMa && textMa.equals(rbMa.getText())) {
                return rbMa;
            } else if (childMa instanceof Container cMa) {
                JRadioButton foundMa = radioMa(cMa, textMa);
                if (foundMa != null) {
                    return foundMa;
                }
            }
        }
        return null;
    }

    @Test
    void dibujaCadaModoYElEstadoParaElManual() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            new ThemeManagerMa().applyJt(false);
            RuleServiceJt rulesMa = mock(RuleServiceJt.class);
            String[][] modesMa = {{"Bloqueo total", "total"}, {"Bloquear en ciertos horarios", "horario"},
                {"Cuota diaria de tiempo", "cuota"}};
            for (String[] modeMa : modesMa) {
                RuleDialogJt.FormJt formMa = RuleDialogJt.buildJt(rulesMa, BlockRuleJtMa.APP, "steam", "Steam", null,
                        okMa -> { });
                radioMa(formMa.panel(), modeMa[0]).doClick();
                renderMa(formMa.panel(), "regla-modo-" + modeMa[1]);
            }
            BlockRuleJtMa existingMa = mock(BlockRuleJtMa.class);
            when(existingMa.getMode()).thenReturn(RuleModeMa.ALWAYS);
            when(existingMa.getState()).thenReturn(com.appcontrol.model.RuleStateJt.PAUSED);
            when(existingMa.getDaysMask()).thenReturn(31);
            renderMa(RuleDialogJt.buildJt(rulesMa, BlockRuleJtMa.APP, "steam", "Steam", existingMa, okMa -> { }).panel(),
                    "regla-modo-estado");
        });
    }

    @Test
    void aplicacionesYSitiosTienenLosTresModos() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            new ThemeManagerMa().applyJt(false);
            RuleServiceJt rulesMa = mock(RuleServiceJt.class);
            for (String kindMa : new String[] {BlockRuleJtMa.APP, BlockRuleJtMa.SITE}) {
                RuleDialogJt.FormJt formMa = RuleDialogJt.buildJt(rulesMa, kindMa, null, null, null, okMa -> { });
                assertEquals(List.of("Bloqueo total", "Bloquear en ciertos horarios", "Cuota diaria de tiempo"),
                        radios(formMa.panel()), "modos para " + kindMa);
                try {
                    formMa.panel().setSize(580, 460);
                    layoutAll(formMa.panel());
                    layoutAll(formMa.panel());
                    BufferedImage imgMa = new BufferedImage(580, 460, BufferedImage.TYPE_INT_RGB);
                    Graphics2D gMa = imgMa.createGraphics();
                    gMa.setColor(UIManager.getColor("Panel.background"));
                    gMa.fillRect(0, 0, 580, 460);
                    formMa.panel().printAll(gMa);
                    gMa.dispose();
                    File fileMa = new File("target/ui/regla-" + kindMa.toLowerCase() + ".png");
                    fileMa.getParentFile().mkdirs();
                    ImageIO.write(imgMa, "png", fileMa);
                } catch (Exception exMa) {
                    throw new RuntimeException(exMa);
                }
            }
        });
    }

    @Test
    void elServicioAceptaCuotaParaSitios() {
        BlockRuleRepositoryJt repoMa = mock(BlockRuleRepositoryJt.class);
        when(repoMa.findByKindAndTargetKey(any(), any())).thenReturn(Optional.empty());
        when(repoMa.save(any())).thenAnswer(invMa -> invMa.getArgument(0));
        RuleServiceJt rulesMa = new RuleServiceJt(repoMa);
        assertDoesNotThrow(() -> rulesMa.saveJtMa(BlockRuleJtMa.SITE, "https://www.youtube.com/watch", null,
                RuleModeMa.QUOTA, 127, 0, 0, 60));
    }

    private static void layoutAll(Container cMa) {
        cMa.doLayout();
        for (Component childMa : cMa.getComponents()) {
            if (childMa instanceof Container ccMa) {
                layoutAll(ccMa);
            }
        }
    }
}
