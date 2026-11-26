package com.appcontrol.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.Timer;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Aplica el aspecto de la aplicación: modo de color (claro, oscuro o el de Windows) y color de acento (azul intenso por
 * defecto). Define la paleta completa (marco, superficie, bordes) y las esquinas redondeadas de todos los componentes.
 * La elección se recuerda entre sesiones.
 */
public final class ThemeManagerMa {

    public enum ModeJtMa { AUTO, LIGHT, DARK }

    /** Colores de acento: nombre, color en modo claro y color (más claro) en modo oscuro. */
    public enum AccentJtMa {
        AZUL("Azul", "#0B5CFF", "#4D8DFF"),
        PRIMAVERA("Primavera", "#0B3A9E", "#6F93F2"),
        AGAVE("Agave", "#0F6B6B", "#3FB5B5"),
        ROSA("Rosa", "#A3123F", "#E0587F");

        private final String labelJt;
        private final String lightJt;
        private final String darkJt;

        AccentJtMa(String labelJt, String lightJt, String darkJt) {
            this.labelJt = labelJt;
            this.lightJt = lightJt;
            this.darkJt = darkJt;
        }

        public String label() {
            return labelJt;
        }

        /** Color del círculo de muestra (el de modo claro). */
        public Color swatch() {
            return Color.decode(lightJt);
        }
    }

    private static final String REG_KEY_JT = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
    private static final Preferences PREFS_JT = Preferences.userNodeForPackage(ThemeManagerMa.class);

    private ModeJtMa modeJt = readModeJt();
    private AccentJtMa accentJt = readAccentJt();
    private Boolean darkJt;
    private Timer watcherJt;
    private final List<Runnable> listenersJt = new ArrayList<>();

    private static ModeJtMa readModeJt() {
        try {
            return ModeJtMa.valueOf(PREFS_JT.get("mode", "AUTO"));
        } catch (IllegalArgumentException exJt) {
            return ModeJtMa.AUTO;
        }
    }

    private static AccentJtMa readAccentJt() {
        try {
            return AccentJtMa.valueOf(PREFS_JT.get("accent", "AZUL"));
        } catch (IllegalArgumentException exJt) {
            return AccentJtMa.AZUL;
        }
    }

    /** true si Windows usa tema oscuro para las aplicaciones. Fuera de Windows: claro. */
    public static boolean systemIsDarkJt() {
        if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
            return false;
        }
        try {
            Process procJt = new ProcessBuilder("reg", "query", REG_KEY_JT, "/v", "AppsUseLightTheme")
                    .redirectErrorStream(true).start();
            String outJt = new String(procJt.getInputStream().readAllBytes()).trim();
            procJt.waitFor();
            return outJt.endsWith("0x0");
        } catch (Exception exJt) {
            return false;
        }
    }

    // ------------------------------------------------------------------ colores del aspecto (para el resto de la interfaz)

    /** Fondo del marco: barra superior, riel de iconos y bordes de la ventana. */
    public static Color frameColorJt() {
        Color colorJt = UIManager.getColor("AppControl.frame");
        return colorJt != null ? colorJt : new Color(0xE8EEFA);
    }

    /** Fondo de la superficie blanca (en oscuro, gris oscuro) donde se muestran las pantallas. */
    public static Color surfaceColorJt() {
        Color colorJt = UIManager.getColor("AppControl.surface");
        return colorJt != null ? colorJt : Color.WHITE;
    }

    public static Color borderColorJt() {
        Color colorJt = UIManager.getColor("AppControl.border");
        return colorJt != null ? colorJt : new Color(0xDCE3EF);
    }

    public static Color hoverColorJt() {
        Color colorJt = UIManager.getColor("AppControl.hover");
        return colorJt != null ? colorJt : new Color(0xD9E3F7);
    }

    /** Fondo del elemento elegido en el riel. */
    public static Color selectedColorJt() {
        Color colorJt = UIManager.getColor("AppControl.selected");
        return colorJt != null ? colorJt : Color.WHITE;
    }

    /** Gris de los iconos y textos secundarios. */
    public static Color mutedColorJt() {
        Color colorJt = UIManager.getColor("AppControl.muted");
        return colorJt != null ? colorJt : new Color(0x5A6578);
    }

    public static Color accentColorJt() {
        Color colorJt = UIManager.getColor("Component.accentColor");
        return colorJt != null ? colorJt : Color.decode("#0B5CFF");
    }

    /** "#RRGGBB" de un color. */
    public static String hexJt(Color colorJt) {
        return String.format("#%02X%02X%02X", colorJt.getRed(), colorJt.getGreen(), colorJt.getBlue());
    }

    // ------------------------------------------------------------------ aplicar

    /** Aplica un tema concreto (tambien lo usan las pruebas). */
    public void applyJt(boolean wantDarkJt) {
        Map<String, String> defaultsJt = new HashMap<>();
        defaultsJt.put("@accentColor", wantDarkJt ? accentJt.darkJt : accentJt.lightJt);
        defaultsJt.put("@background", wantDarkJt ? "#1F2229" : "#FFFFFF");
        defaultsJt.put("AppControl.frame", wantDarkJt ? "#14161A" : "#E8EEFA");
        defaultsJt.put("AppControl.surface", wantDarkJt ? "#1F2229" : "#FFFFFF");
        defaultsJt.put("AppControl.border", wantDarkJt ? "#343A46" : "#DCE3EF");
        defaultsJt.put("AppControl.hover", wantDarkJt ? "#2A2F39" : "#D9E3F7");
        defaultsJt.put("AppControl.selected", wantDarkJt ? "#2B303A" : "#FFFFFF");
        defaultsJt.put("AppControl.muted", wantDarkJt ? "#A3ACBB" : "#5A6578");
        defaultsJt.put("Component.borderColor", wantDarkJt ? "#343A46" : "#DCE3EF");
        defaultsJt.put("Button.borderColor", wantDarkJt ? "#3B4250" : "#CBD4E3");
        defaultsJt.put("Button.background", wantDarkJt ? "#262A33" : "#FFFFFF");
        defaultsJt.put("Button.hoverBackground", wantDarkJt ? "#2E333E" : "#F1F5FC");
        defaultsJt.put("TextField.background", wantDarkJt ? "#262A33" : "#FFFFFF");
        defaultsJt.put("ComboBox.background", wantDarkJt ? "#262A33" : "#FFFFFF");
        defaultsJt.put("TabbedPane.underlineColor", "$Component.accentColor");
        defaultsJt.put("TabbedPane.selectedForeground", wantDarkJt ? "#FFFFFF" : "#111827");
        defaultsJt.put("Component.arc", "12");
        defaultsJt.put("Button.arc", "12");
        defaultsJt.put("TextComponent.arc", "12");
        defaultsJt.put("ProgressBar.arc", "999");
        defaultsJt.put("ScrollBar.thumbArc", "999");
        defaultsJt.put("ScrollBar.width", "12");
        defaultsJt.put("Popup.borderCornerRadius", "12");
        defaultsJt.put("PopupMenu.borderCornerRadius", "12");
        FlatLaf.setGlobalExtraDefaults(defaultsJt);
        if (wantDarkJt) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        darkJt = wantDarkJt;
        FlatLaf.updateUI();
        listenersJt.forEach(Runnable::run);
    }

    public ModeJtMa mode() {
        return modeJt;
    }

    public AccentJtMa accent() {
        return accentJt;
    }

    public boolean isDark() {
        return darkJt != null && darkJt;
    }

    /** Texto para el boton: "Claro", "Oscuro" o "Claro (Auto)". */
    public String labelJt() {
        String nowJt = isDark() ? "Oscuro" : "Claro";
        return switch (modeJt) {
            case AUTO -> nowJt + " (Auto)";
            default -> nowJt;
        };
    }

    public void setModeJt(ModeJtMa newJt) {
        modeJt = newJt;
        PREFS_JT.put("mode", newJt.name());
        applyJt(wantDarkNowJt());
    }

    public void setAccentJt(AccentJtMa newJt) {
        accentJt = newJt;
        PREFS_JT.put("accent", newJt.name());
        applyJt(wantDarkNowJt());
    }

    public void addListenerJt(Runnable runJt) {
        listenersJt.add(runJt);
    }

    private boolean wantDarkNowJt() {
        return switch (modeJt) {
            case AUTO -> systemIsDarkJt();
            case LIGHT -> false;
            case DARK -> true;
        };
    }

    /** Aplica el tema segun el modo y, en automatico, vigila a Windows cada 3 s. Llamar desde el hilo de Swing. */
    public void startJt() {
        applyJt(wantDarkNowJt());
        watcherJt = new Timer(3000, evJt -> {
            if (modeJt == ModeJtMa.AUTO) {
                boolean nowDarkJt = systemIsDarkJt();
                if (darkJt == null || nowDarkJt != darkJt) {
                    applyJt(nowDarkJt);
                }
            }
        });
        watcherJt.start();
    }

    public void stopJt() {
        if (watcherJt != null) {
            watcherJt.stop();
        }
    }
}
