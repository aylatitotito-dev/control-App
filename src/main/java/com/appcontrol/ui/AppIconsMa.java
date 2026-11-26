package com.appcontrol.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;

/** Iconos de las aplicaciones: el icono real del programa (si se puede leer) o un cuadro con su inicial. */
final class AppIconsMa {

    private static final Map<String, Icon> CACHE_JT = new ConcurrentHashMap<>();
    private static final ExecutorService POOL_JT = Executors.newSingleThreadExecutor(rJt -> {
        Thread tJt = new Thread(rJt, "app-icons");
        tJt.setDaemon(true);
        return tJt;
    });

    private AppIconsMa() {
    }

    /** Cuadro redondeado de color (segun el nombre) con la inicial. */
    /** El icono real si ya se cargo; si no, la inicial (y se pide cargarlo para la proxima vez). */
    static Icon cachedOrPlaceholder(String pathJt, String nameJt, int sizeJt) {
        if (pathJt != null && !pathJt.isBlank()) {
            Icon cachedJt = CACHE_JT.get(pathJt + "@" + sizeJt);
            if (cachedJt != null) {
                return cachedJt;
            }
            load(pathJt, sizeJt, loadedJt -> { });
        }
        return placeholder(nameJt, sizeJt);
    }

    static int cacheSizeJt() {
        return CACHE_JT.size();
    }

    static Icon placeholder(String nameJt, int sizeJt) {
        return new LetterIconJtMa(nameJt, sizeJt);
    }

    /** Carga el icono del programa en segundo plano; llama a doneJt (en el hilo de Swing) si lo consigue. */
    static void load(String pathJt, int sizeJt, Consumer<Icon> doneJt) {
        if (pathJt == null || pathJt.isBlank()) {
            return;
        }
        String keyJt = pathJt + "@" + sizeJt;
        Icon cachedJt = CACHE_JT.get(keyJt);
        if (cachedJt != null) {
            doneJt.accept(cachedJt);
            return;
        }
        POOL_JT.execute(() -> {
            try {
                File fileJt = new File(pathJt);
                if (!fileJt.isFile()) {
                    return;
                }
                Icon iconJt = FileSystemView.getFileSystemView().getSystemIcon(fileJt, sizeJt, sizeJt);
                if (iconJt != null) {
                    CACHE_JT.put(keyJt, iconJt);
                    SwingUtilities.invokeLater(() -> doneJt.accept(iconJt));
                }
            } catch (Throwable exJt) {
                // sin icono real: se queda el cuadro con la inicial
            }
        });
    }

    private static final class LetterIconJtMa implements Icon {
        private final String letterJt;
        private final int sizeJt;
        private final Color colorJt;

        LetterIconJtMa(String nameJt, int sizeJt) {
            this.sizeJt = sizeJt;
            String trimmedJt = nameJt == null ? "" : nameJt.trim();
            this.letterJt = trimmedJt.isEmpty() ? "?" : trimmedJt.substring(0, 1).toUpperCase(Locale.ROOT);
            float hueJt = (Math.abs(trimmedJt.toLowerCase(Locale.ROOT).hashCode()) % 360) / 360f;
            this.colorJt = Color.getHSBColor(hueJt, 0.45f, 0.70f);
        }

        @Override
        public void paintIcon(Component cJt, Graphics gJt, int xJt, int yJt) {
            Graphics2D g2Jt = (Graphics2D) gJt.create();
            g2Jt.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2Jt.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2Jt.setColor(colorJt);
            g2Jt.fillRoundRect(xJt, yJt, sizeJt, sizeJt, sizeJt / 4, sizeJt / 4);
            g2Jt.setColor(Color.WHITE);
            g2Jt.setFont(g2Jt.getFont().deriveFont(Font.BOLD, sizeJt * 0.5f));
            FontMetrics fmJt = g2Jt.getFontMetrics();
            g2Jt.drawString(letterJt, xJt + (sizeJt - fmJt.stringWidth(letterJt)) / 2,
                    yJt + (sizeJt - fmJt.getHeight()) / 2 + fmJt.getAscent());
            g2Jt.dispose();
        }

        @Override
        public int getIconWidth() {
            return sizeJt;
        }

        @Override
        public int getIconHeight() {
            return sizeJt;
        }
    }
}
