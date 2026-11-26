package com.appcontrol.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

import com.appcontrol.service.AccountServiceMa;

/** Dibuja las pantallas de crear cuenta e iniciar sesion (target/ui/cuenta-*.png). */
class AuthPanelRenderJtTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static void layoutAll(Container cJt) {
        cJt.doLayout();
        for (Component childJt : cJt.getComponents()) {
            if (childJt instanceof Container ccJt) {
                layoutAll(ccJt);
            }
        }
    }

    private static void save(AuthPanelJtMa panelJt, String nameJt) throws Exception {
        panelJt.setSize(1000, 660);
        layoutAll(panelJt);
        layoutAll(panelJt);
        BufferedImage imgJt = new BufferedImage(1000, 660, BufferedImage.TYPE_INT_RGB);
        Graphics2D gJt = imgJt.createGraphics();
        gJt.setColor(UIManager.getColor("Panel.background"));
        gJt.fillRect(0, 0, 1000, 660);
        panelJt.printAll(gJt);
        gJt.dispose();
        File fileJt = new File("target/ui/" + nameJt + ".png");
        fileJt.getParentFile().mkdirs();
        ImageIO.write(imgJt, "png", fileJt);
        assertTrue(fileJt.length() > 1000);
    }

    @Test
    void renderizaCrearCuentaEIniciarSesion() throws Exception {
        for (boolean darkJt : new boolean[] {false, true}) {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    new ThemeManagerMa().applyJt(darkJt);
                    String suffixJt = darkJt ? "oscuro" : "claro";
                    AccountServiceMa noAccountJt = mock(AccountServiceMa.class);
                    save(new AuthPanelJtMa(noAccountJt, () -> { }), "cuenta-crear-" + suffixJt);

                    AccountServiceMa withAccountJt = mock(AccountServiceMa.class);
                    when(withAccountJt.hasAccount()).thenReturn(true);
                    when(withAccountJt.usernameJt()).thenReturn("Juan Torres");
                    save(new AuthPanelJtMa(withAccountJt, () -> { }), "cuenta-entrar-" + suffixJt);
                } catch (Exception exJt) {
                    throw new RuntimeException(exJt);
                }
            });
        }
    }
}
