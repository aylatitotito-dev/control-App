package com.appcontrol.ui;

import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/** El logo de AppControl (cronometro con pieza de rompecabezas): version de color y version blanca. */
final class LogoMa {

    private static BufferedImage colorMa;
    private static BufferedImage whiteMa;

    private LogoMa() {
    }

    private static BufferedImage load(String fileMa) {
        try (InputStream inMa = LogoMa.class.getResourceAsStream("/images/" + fileMa)) {
            return inMa == null ? null : ImageIO.read(inMa);
        } catch (IOException exMa) {
            return null;
        }
    }

    /** Logo a color (para fondos claros y para el icono de la ventana). */
    static synchronized BufferedImage colorMa() {
        if (colorMa == null) {
            colorMa = load("logo-mark.png");
        }
        return colorMa;
    }

    /** Silueta blanca (para el panel azul). */
    static synchronized BufferedImage whiteMa() {
        if (whiteMa == null) {
            whiteMa = load("logo-mark-white.png");
        }
        return whiteMa;
    }

    /** Escala con buena calidad para que alto sea heightMa (mantiene la proporcion). */
    static Image scaledMa(BufferedImage srcMa, int heightMa) {
        if (srcMa == null) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        int widthMa = Math.max(1, srcMa.getWidth() * heightMa / srcMa.getHeight());
        BufferedImage outMa = new BufferedImage(widthMa, heightMa, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gMa = outMa.createGraphics();
        gMa.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gMa.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gMa.drawImage(srcMa, 0, 0, widthMa, heightMa, null);
        gMa.dispose();
        return outMa;
    }

    /** Imagen cuadrada con el logo centrado (iconos de ventana y bandeja). */
    static BufferedImage squareMa(int sizeMa) {
        BufferedImage srcMa = colorMa();
        BufferedImage outMa = new BufferedImage(sizeMa, sizeMa, BufferedImage.TYPE_INT_ARGB);
        if (srcMa == null) {
            return outMa;
        }
        int inner = Math.max(1, sizeMa - 2);
        double scaleMa = Math.min((double) inner / srcMa.getWidth(), (double) inner / srcMa.getHeight());
        int wMa = Math.max(1, (int) Math.round(srcMa.getWidth() * scaleMa));
        int hMa = Math.max(1, (int) Math.round(srcMa.getHeight() * scaleMa));
        Graphics2D gMa = outMa.createGraphics();
        gMa.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gMa.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gMa.drawImage(srcMa, (sizeMa - wMa) / 2, (sizeMa - hMa) / 2, wMa, hMa, null);
        gMa.dispose();
        return outMa;
    }
}
