package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

import com.appcontrol.service.AppSettingsServiceMa;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.tracker.ActiveWindowTrackerJtMa;
import com.appcontrol.web.SiteBlockingJtMa;
import com.formdev.flatlaf.FlatClientProperties;

/** Ajustes: filas como en Configuracion de Windows (titulo y descripcion a la izquierda, control a la derecha). */
class SettingsPageJtMa extends PageMa {


    private final ThemeManagerMa themeMa;
    private final AccountServiceMa accountMa;
    private final Runnable logoutMa;
    private final SiteBlockingJtMa blockingMa;
    private final AppSettingsServiceMa settingsMa;
    private final JButton focusToggleMa = new JButton();
    private final JLabel focusDescMa = UiKitJtMa.subtitle(" ");
    /** Si es "true", cerrar la ventana cierra AppControl del todo (pide la contraseña) en vez de seguir en segundo plano. */
    static final String CLOSE_EXITS_SETTING_MA = "close_exits";
    /**
     * TEMPORAL (etapa de pruebas): mientras nadie elija otra cosa en Ajustes, cerrar la ventana cierra AppControl del
     * todo y sin contraseña. Para volver al modo normal (segundo plano) basta poner esto en false.
     */
    static final boolean CLOSE_EXITS_TEST_DEFAULT_MA = false;
    private final JButton closeToggleMa = new JButton();
    private final JLabel closeDescMa = UiKitJtMa.subtitle(" ");
    private final JButton siteToggleMa = new JButton();
    private final JLabel siteDescMa = UiKitJtMa.subtitle(" ");
    private final JButton profileBtnMa = new JButton("Editar perfil");
    private final JButton passwordBtnMa = new JButton("Cambiar contraseña");
    private Runnable onProfileChangedMa = () -> { };
    private final JButton updateBtnMa = new JButton("Buscar actualización");
    private final JLabel updateDescMa = UiKitJtMa.subtitle(" ");
    private com.appcontrol.update.UpdateServiceJt updateServiceMa;
    private com.appcontrol.update.UpdateInfoJtMa foundMa;
    private Runnable exitForUpdateMa;
    private java.util.function.Consumer<com.appcontrol.update.UpdateInfoJtMa> onFoundMa = infoMa -> { };
    private final JButton logoutBtnMa = new JButton("Cerrar sesión");
    private final JLabel accountDescMa = UiKitJtMa.subtitle(" ");
    private boolean syncingMa;

    SettingsPageJtMa(ThemeManagerMa themeMa, AccountServiceMa accountMa, SiteBlockingJtMa blockingMa,
            AppSettingsServiceMa settingsMa, Runnable logoutMa) {
        super("Ajustes");
        this.themeMa = themeMa;
        this.accountMa = accountMa;
        this.logoutMa = logoutMa;
        this.blockingMa = blockingMa;
        this.settingsMa = settingsMa;

        profileBtnMa.addActionListener(evMa -> editProfileMa());
        passwordBtnMa.addActionListener(evMa -> changePasswordMa());
        logoutBtnMa.addActionListener(evMa -> logoutMa.run());
        siteToggleMa.addActionListener(evMa -> toggleSiteBlockingMa());
        focusToggleMa.addActionListener(evMa -> toggleSiteFocusMa());
        closeToggleMa.addActionListener(evMa -> toggleCloseExitsMa());

        Path dataMa = Path.of(System.getenv("APPDATA") != null ? System.getenv("APPDATA")
                : System.getProperty("user.home"), "AppControl", "data");
        JButton openMa = new JButton("Abrir carpeta");
        openMa.addActionListener(evMa -> openFolderMa(dataMa));

        JPanel accountButtonsMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        accountButtonsMa.setOpaque(false);
        accountButtonsMa.add(profileBtnMa);
        accountButtonsMa.add(passwordBtnMa);
        accountButtonsMa.add(logoutBtnMa);

        JPanel rowsMa = new JPanel();
        rowsMa.setOpaque(false);
        rowsMa.setLayout(new BoxLayout(rowsMa, BoxLayout.Y_AXIS));
        AppearancePanelJt appearanceMa = new AppearancePanelJt(themeMa);
        appearanceMa.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        rowsMa.add(appearanceMa);
        rowsMa.add(Box.createVerticalStrut(18));
        rowsMa.add(rowMa("Cuenta", accountDescMa, accountButtonsMa));
        rowsMa.add(Box.createVerticalStrut(8));
        rowsMa.add(rowMa("Bloqueo de sitios web", siteDescMa, siteToggleMa));
        rowsMa.add(Box.createVerticalStrut(8));
        rowsMa.add(rowMa("Sitio en primer plano", focusDescMa, focusToggleMa));
        rowsMa.add(Box.createVerticalStrut(8));
        rowsMa.add(rowMa("Al cerrar la ventana", closeDescMa, closeToggleMa));
        rowsMa.add(Box.createVerticalStrut(8));
        updateDescMa.setText("Versión instalada: " + com.appcontrol.BuildInfoMa.labelMa()
                + ". Se actualiza sin reinstalar.");
        updateBtnMa.addActionListener(evMa -> searchOrApplyUpdateMa());
        rowsMa.add(rowMa("Actualizaciones", updateDescMa, updateBtnMa));
        rowsMa.add(Box.createVerticalStrut(8));
        rowsMa.add(rowMa("Tus datos", UiKitJtMa.subtitle(dataMa.toString()), openMa));
        rowsMa.add(Box.createVerticalStrut(8));
        rowsMa.add(rowMa("Modo sin conexión", UiKitJtMa.subtitle("Todo funciona en este equipo sin Internet. Azure "
                + "solo se usará para grupos y vínculos remotos."), new ChipMa("Activo", ChipMa.KindJtMa.OK)));
        rowsMa.add(Box.createVerticalStrut(8));
        rowsMa.add(rowMa("Acerca de AppControl", UiKitJtMa.subtitle("Versión " + com.appcontrol.BuildInfoMa.labelMa() + "  |  Java "
                + System.getProperty("java.version")), null));

        JPanel holderMa = new JPanel(new BorderLayout());
        holderMa.setOpaque(false);
        holderMa.add(rowsMa, BorderLayout.NORTH);

        add(UiKitJtMa.title("Configuración"), BorderLayout.NORTH);
        add(UiKitJtMa.vscroll(holderMa), BorderLayout.CENTER);
        refreshMa();
    }

    private static JPanel rowMa(String titleMa, JLabel descMa, JComponent controlMa) {
        JPanel cardMa = UiKitJtMa.roundedPanel(16);
        cardMa.setLayout(new BorderLayout(16, 0));
        JPanel textMa = new JPanel(new java.awt.GridLayout(2, 1, 0, 2));
        textMa.setOpaque(false);
        JLabel titleLabelMa = new JLabel(titleMa);
        titleLabelMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        textMa.add(titleLabelMa);
        textMa.add(descMa);
        cardMa.add(textMa, BorderLayout.CENTER);
        if (controlMa != null) {
            JPanel eastMa = new JPanel(new java.awt.GridBagLayout());
            eastMa.setOpaque(false);
            eastMa.add(controlMa);
            cardMa.add(eastMa, BorderLayout.EAST);
        }
        cardMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        return cardMa;
    }

    /** Cambia la contrasena de la cuenta: pide la actual y la nueva dos veces. */
    void setUpdateServiceJt(com.appcontrol.update.UpdateServiceJt serviceMa, Runnable exitMa,
            java.util.function.Consumer<com.appcontrol.update.UpdateInfoJtMa> onFoundMa) {
        this.updateServiceMa = serviceMa;
        this.exitForUpdateMa = exitMa;
        this.onFoundMa = onFoundMa;
    }

    /** Primer toque: busca una versión nueva. Si hay una, el botón pasa a "Actualizar ahora". */
    private void searchOrApplyUpdateMa() {
        if (updateServiceMa == null) {
            return;
        }
        if (foundMa != null) {
            UpdateFlowJt.startJt(this, updateServiceMa, foundMa, exitForUpdateMa);
            return;
        }
        if (com.appcontrol.BuildInfoMa.isDevMa()) {
            updateDescMa.setText("Esta copia corre desde el código (desarrollo): no usa actualizaciones.");
            return;
        }
        updateBtnMa.setEnabled(false);
        updateDescMa.setText("Buscando…");
        UiAsyncJt.runJt(() -> updateServiceMa.checkJt(), resultMa -> {
            updateBtnMa.setEnabled(true);
            if (resultMa.isPresent()) {
                foundMa = resultMa.get();
                onFoundMa.accept(foundMa);
                updateDescMa.setText("Hay una versión nueva (compilación del "
                        + com.appcontrol.BuildInfoMa.localTextMa(foundMa.build()) + "). Instalada: " + com.appcontrol.BuildInfoMa.labelMa());
                updateBtnMa.setText("Actualizar ahora");
            } else {
                updateDescMa.setText("Estás al día. Versión instalada: " + com.appcontrol.BuildInfoMa.labelMa());
            }
        }, errorMa -> {
            updateBtnMa.setEnabled(true);
            updateDescMa.setText("No se pudo buscar (¿sin internet?).");
        });
    }

    /** Abre la ventana de Editar perfil (la usan el buscador y el menú de la cuenta). */
    void openProfileEditorMa() {
        editProfileMa();
    }

    void setOnProfileChangedJt(Runnable onProfileChangedMa) {
        this.onProfileChangedMa = onProfileChangedMa;
    }

    private static final String[] AVATAR_COLORS_MA = {"", "#0B5CFF", "#7C3AED", "#DB2777", "#E5484D", "#E8832B",
        "#2EB872", "#12A5C4", "#475569"};

    /** Cambiar el nombre (que también es el usuario para entrar) y el color del avatar. Pide la contraseña. */
    private void editProfileMa() {
        if (accountMa.lockSecondsLeftJt() > 0) {
            RuleActionsMa.lockedMessageMa(this, accountMa);
            return;
        }
        javax.swing.JTextField nameMa = new javax.swing.JTextField(accountMa.usernameJt(), 20);
        JPasswordField passwordMa = new JPasswordField(20);
        String[] chosenMa = {accountMa.avatarColorJt()};
        JPanel swatchesMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        javax.swing.ButtonGroup groupMa = new javax.swing.ButtonGroup();
        for (String hexMa : AVATAR_COLORS_MA) {
            javax.swing.JToggleButton swatchMa = new javax.swing.JToggleButton(hexMa.isEmpty() ? "Auto" : "");
            swatchMa.setPreferredSize(new Dimension(hexMa.isEmpty() ? 52 : 30, 30));
            swatchMa.setFocusable(false);
            if (!hexMa.isEmpty()) {
                swatchMa.setBackground(java.awt.Color.decode(hexMa));
                swatchMa.putClientProperty(FlatClientProperties.STYLE, "arc: 999; borderWidth: 0");
            }
            swatchMa.setSelected(hexMa.equalsIgnoreCase(chosenMa[0]));
            swatchMa.addActionListener(evMa -> chosenMa[0] = hexMa);
            groupMa.add(swatchMa);
            swatchesMa.add(swatchMa);
        }
        JPanel panelMa = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        panelMa.add(new JLabel("Nombre (también es tu usuario para iniciar sesión):"));
        panelMa.add(nameMa);
        panelMa.add(new JLabel("Color de tu círculo:"));
        panelMa.add(swatchesMa);
        panelMa.add(new JLabel("Tu contraseña, para confirmar:"));
        panelMa.add(passwordMa);
        if (JOptionPane.showConfirmDialog(this, panelMa, "Editar perfil", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            AccountServiceMa.ResultJt resultMa = accountMa.updateProfileJt(new String(passwordMa.getPassword()),
                    nameMa.getText(), chosenMa[0]);
            switch (resultMa) {
                case OK -> {
                    refreshMa();
                    onProfileChangedMa.run();
                    JOptionPane.showMessageDialog(this, "Perfil guardado.", "AppControl",
                            JOptionPane.INFORMATION_MESSAGE);
                }
                case WRONG -> JOptionPane.showMessageDialog(this, "La contraseña no es correcta.", "AppControl",
                        JOptionPane.WARNING_MESSAGE);
                case LOCKED -> RuleActionsMa.lockedMessageMa(this, accountMa);
            }
        } catch (IllegalArgumentException exMa) {
            JOptionPane.showMessageDialog(this, exMa.getMessage(), "AppControl", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void changePasswordMa() {
        if (accountMa.lockSecondsLeftJt() > 0) {
            RuleActionsMa.lockedMessageMa(this, accountMa);
            return;
        }
        JPasswordField currentMa = new JPasswordField(18);
        JPasswordField firstMa = new JPasswordField(18);
        JPasswordField secondMa = new JPasswordField(18);
        JPanel panelMa = new JPanel(new java.awt.GridLayout(6, 1, 0, 4));
        panelMa.add(new JLabel("Contraseña actual:"));
        panelMa.add(currentMa);
        panelMa.add(new JLabel("Contraseña nueva (mínimo " + AccountServiceMa.MIN_PASSWORD_JT + " caracteres):"));
        panelMa.add(firstMa);
        panelMa.add(new JLabel("Repite la contraseña nueva:"));
        panelMa.add(secondMa);
        int okMa = JOptionPane.showConfirmDialog(this, panelMa, "Cambiar contraseña", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (okMa != JOptionPane.OK_OPTION) {
            return;
        }
        String newMa = new String(firstMa.getPassword());
        if (!newMa.equals(new String(secondMa.getPassword()))) {
            JOptionPane.showMessageDialog(this, "Las dos contraseñas nuevas no coinciden.", "AppControl",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            AccountServiceMa.ResultJt resultMa = accountMa.changePasswordJt(new String(currentMa.getPassword()), newMa);
            switch (resultMa) {
                case OK -> JOptionPane.showMessageDialog(this, "Contraseña cambiada.", "AppControl",
                        JOptionPane.INFORMATION_MESSAGE);
                case LOCKED -> RuleActionsMa.lockedMessageMa(this, accountMa);
                default -> JOptionPane.showMessageDialog(this, "La contraseña actual no es correcta.", "AppControl",
                        JOptionPane.WARNING_MESSAGE);
            }
        } catch (IllegalArgumentException exMa) {
            JOptionPane.showMessageDialog(this, exMa.getMessage(), "AppControl", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Activar o desactivar el bloqueo de sitios pide la contrasena. */
    private void toggleSiteBlockingMa() {
        boolean enabledMa = blockingMa.isEnabledJtMa();
        if (!RuleActionsMa.unlockMa(this, accountMa)) {
            return;
        }
        blockingMa.setEnabledJtMa(!enabledMa);
        refreshMa();
    }

    /** Activar o desactivar el seguimiento de la pestana al frente pide la contrasena. */
    private void toggleCloseExitsMa() {
        boolean onMa = settingsMa.getBoolean(CLOSE_EXITS_SETTING_MA, CLOSE_EXITS_TEST_DEFAULT_MA);
        if (!RuleActionsMa.unlockMa(this, accountMa)) {
            return;
        }
        settingsMa.set(CLOSE_EXITS_SETTING_MA, String.valueOf(!onMa));
        refreshMa();
    }

    private void toggleSiteFocusMa() {
        boolean onMa = settingsMa.getBoolean(ActiveWindowTrackerJtMa.SITE_FOCUS_SETTING_MA, true);
        if (!RuleActionsMa.unlockMa(this, accountMa)) {
            return;
        }
        settingsMa.set(ActiveWindowTrackerJtMa.SITE_FOCUS_SETTING_MA, String.valueOf(!onMa));
        refreshMa();
    }

    private void openFolderMa(Path dirMa) {
        try {
            if (Files.isDirectory(dirMa) && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dirMa.toFile());
            } else {
                JOptionPane.showMessageDialog(this, "La carpeta todavía no existe.", "AppControl",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception exMa) {
            JOptionPane.showMessageDialog(this, "No se pudo abrir la carpeta.", "AppControl",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    void refreshMa() {
        syncingMa = true;
        syncingMa = false;
        boolean focusOnMa = settingsMa.getBoolean(ActiveWindowTrackerJtMa.SITE_FOCUS_SETTING_MA, true);
        boolean closeExitsMa = settingsMa.getBoolean(CLOSE_EXITS_SETTING_MA, CLOSE_EXITS_TEST_DEFAULT_MA);
        closeToggleMa.setText(closeExitsMa ? "Seguir en segundo plano" : "Salir del todo");
        closeDescMa.setText(closeExitsMa
                ? "Al cerrar la ventana AppControl se cierra por completo (pide tu contraseña) y deja de bloquear. "
                        + "Útil para probar e instalar de nuevo."
                : "Al cerrar la ventana AppControl sigue en segundo plano para que las reglas se apliquen. Sale solo "
                        + "desde el icono junto al reloj, con contraseña.");
        focusToggleMa.setText(focusOnMa ? "Desactivar" : "Activar");
        focusDescMa.setText(focusOnMa
                ? "Activado: al volver a una pestaña que ya estaba abierta, queda anotado en el historial. Para saber "
                        + "qué sitio es, se mira un instante el título de la ventana del navegador; el título no se guarda."
                : "Desactivado: el historial solo anota un sitio cuando cargas una página nueva.");
        boolean siteOnMa = blockingMa.isEnabledJtMa();
        siteToggleMa.setText(siteOnMa ? "Desactivar" : "Activar");
        siteDescMa.setText(switch (blockingMa.statusJtMa()) {
            case ACTIVE -> "Activo: los sitios con regla se bloquean en el navegador (Chrome, Edge, Brave y Firefox "
                    + "si usan la red del sistema).";
            case NO_RULES -> siteOnMa ? "Listo. Se activa solo cuando agregues una regla de sitio."
                    : "Desactivado: las reglas de sitios no se aplican.";
            case OFF -> "Desactivado: las reglas de sitios no se aplican.";
            case PROXY_DOWN -> "No se pudo iniciar el proxy local (puerto ocupado).";
            case CONFLICT -> "No disponible: ya tienes un proxy o script de red propio y AppControl no lo cambia.";
            case UNSUPPORTED -> "Solo disponible en Windows.";
        });
        accountDescMa.setText("Sesión de " + accountMa.usernameJt() + ". Su contraseña protege las reglas.");
    }
}
