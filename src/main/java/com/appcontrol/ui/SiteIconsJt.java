package com.appcontrol.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.appcontrol.service.SiteIconStoreMa;

/** Icono de un sitio (el favicon que ya tiene el navegador) o, si no esta, un cuadro con su inicial. */
final class SiteIconsJt {

    private static final Map<String, Icon> CACHE_JT = new ConcurrentHashMap<>();

    private SiteIconsJt() {
    }

    static boolean hasJt(String hostJt) {
        return SiteIconStoreMa.hasMa(hostJt);
    }

    static Icon ofJt(String hostJt, int sizeJt) {
        String keyJt = hostJt + "@" + sizeJt;
        Icon cachedJt = CACHE_JT.get(keyJt);
        if (cachedJt != null) {
            return cachedJt;
        }
        if (SiteIconStoreMa.hasMa(hostJt)) {
            try {
                BufferedImage imgJt = ImageIO.read(new File(SiteIconStoreMa.fileForMa(hostJt).toString()));
                if (imgJt != null) {
                    Icon iconJt = new ImageIcon(imgJt.getScaledInstance(sizeJt, sizeJt, Image.SCALE_SMOOTH));
                    CACHE_JT.put(keyJt, iconJt);
                    return iconJt;
                }
            } catch (Exception exJt) {
                // archivo danado: se usa la inicial
            }
        }
        return AppIconsMa.placeholder(hostJt, sizeJt);
    }
}
