package com.appcontrol.ui;

import javax.swing.Icon;
import javax.swing.UIManager;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/** Iconos SVG que toman el color del texto del tema (claro/oscuro). */
final class IconsJtMa {

    private IconsJtMa() {
    }

    /** Icono con un color propio (no cambia con el tema), para el menu lateral. */
    static Icon colored(String nameMa, int sizeMa, String hexMa) {
        java.awt.Color colorMa = java.awt.Color.decode(hexMa);
        FlatSVGIcon iconMa = new FlatSVGIcon("icons/" + nameMa + ".svg", sizeMa, sizeMa);
        iconMa.setColorFilter(new FlatSVGIcon.ColorFilter(oldMa -> colorMa));
        return iconMa;
    }

    static Icon of(String nameMa, int sizeMa) {
        FlatSVGIcon iconMa = new FlatSVGIcon("icons/" + nameMa + ".svg", sizeMa, sizeMa);
        iconMa.setColorFilter(new FlatSVGIcon.ColorFilter(colorMa -> UIManager.getColor("Label.foreground")));
        return iconMa;
    }
}
