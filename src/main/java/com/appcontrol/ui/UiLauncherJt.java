package com.appcontrol.ui;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.AppSettingsServiceMa;
import com.appcontrol.service.GroupServiceMa;
import com.appcontrol.service.InstalledAppsServiceJtMa;
import com.appcontrol.service.NotificationServiceJtMa;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.service.UsageServiceJt;
import com.appcontrol.service.VisitedSitesServiceMa;
import com.appcontrol.service.SummaryServiceMa;
import com.appcontrol.tracker.RefreshCoordinatorMa;
import com.appcontrol.web.SiteBlockingJtMa;

/**
 * Abre la ventana principal cuando Spring termina de arrancar y crea el icono de la bandeja del sistema.
 * Cerrar la ventana la oculta (las reglas siguen aplicandose); salir de verdad se hace desde la bandeja
 * y pide el PIN si hay uno configurado.
 */
@Component
@ConditionalOnProperty(name = "appcontrol.ui.enabled", havingValue = "true", matchIfMissing = true)
public class UiLauncherJt implements ApplicationRunner {

    private final ConfigurableApplicationContext ctxJtMa;
    private final UsageServiceJt usageJtMa;
    private final RuleServiceJt rulesJtMa;
    private final AccountServiceMa accountJtMa;
    private final GroupServiceMa groupsJtMa;
    private final com.appcontrol.cloud.CloudServiceMa cloudJtMa;
    private final InstalledAppsServiceJtMa installedJtMa;
    private final VisitedSitesServiceMa visitedJtMa;
    private final NotificationServiceJtMa noticesJtMa;
    private final SiteBlockingJtMa blockingJtMa;
    private final ActivityServiceMa activityJtMa;
    private final RefreshCoordinatorMa refreshJtMa;
    private final AppSettingsServiceMa settingsJtMa;
    private final SummaryServiceMa summaryJtMa;
    private final com.appcontrol.update.UpdateServiceJt updateJtMa;

    public UiLauncherJt(ConfigurableApplicationContext ctxJtMa, UsageServiceJt usageJtMa, RuleServiceJt rulesJtMa,
            AccountServiceMa accountJtMa, GroupServiceMa groupsJtMa, InstalledAppsServiceJtMa installedJtMa,
            VisitedSitesServiceMa visitedJtMa, NotificationServiceJtMa noticesJtMa, SiteBlockingJtMa blockingJtMa,
            ActivityServiceMa activityJtMa, RefreshCoordinatorMa refreshJtMa,
            AppSettingsServiceMa settingsJtMa, SummaryServiceMa summaryJtMa,
            com.appcontrol.cloud.CloudServiceMa cloudJtMa, com.appcontrol.update.UpdateServiceJt updateJtMa) {
        this.updateJtMa = updateJtMa;
        this.ctxJtMa = ctxJtMa;
        this.cloudJtMa = cloudJtMa;
        this.usageJtMa = usageJtMa;
        this.rulesJtMa = rulesJtMa;
        this.accountJtMa = accountJtMa;
        this.groupsJtMa = groupsJtMa;
        this.installedJtMa = installedJtMa;
        this.visitedJtMa = visitedJtMa;
        this.noticesJtMa = noticesJtMa;
        this.blockingJtMa = blockingJtMa;
        this.activityJtMa = activityJtMa;
        this.refreshJtMa = refreshJtMa;
        this.settingsJtMa = settingsJtMa;
        this.summaryJtMa = summaryJtMa;
    }

    @Override
    public void run(ApplicationArguments argsJtMa) {
        SwingUtilities.invokeLater(() -> {
            ThemeManagerMa themeJtMa = new ThemeManagerMa();
            themeJtMa.startJt();

            JFrame frameJtMa = new JFrame("AppControl");
            frameJtMa.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            if (LogoMa.colorMa() != null) {
                frameJtMa.setIconImages(java.util.List.of(LogoMa.squareMa(256), LogoMa.squareMa(64),
                        LogoMa.squareMa(32), LogoMa.squareMa(16)));
            }
            // Entrada: crear cuenta / iniciar sesion; el panel principal se arma al entrar.
            CardLayout cardsJtMa = new CardLayout();
            JPanel rootJtMa = new JPanel(cardsJtMa);
            AuthPanelJtMa[] authJtMa = new AuthPanelJtMa[1];
            MainPanelJt[] mainJtMa = new MainPanelJt[1];
            Runnable logoutJtMa = () -> {
                accountJtMa.logoutJt();
                authJtMa[0].showStartJt();
                cardsJtMa.show(rootJtMa, "auth");
            };
            Runnable enterJtMa = () -> {
                if (mainJtMa[0] == null) {
                    mainJtMa[0] = new MainPanelJt(usageJtMa, rulesJtMa, accountJtMa, groupsJtMa, installedJtMa,
                            visitedJtMa, blockingJtMa, activityJtMa, refreshJtMa, settingsJtMa, summaryJtMa, logoutJtMa, themeJtMa, cloudJtMa);
                    mainJtMa[0].setUpdateServiceJt(updateJtMa, () -> exitApp(frameJtMa, themeJtMa));
                    rootJtMa.add(mainJtMa[0], "main");
                }
                cardsJtMa.show(rootJtMa, "main");
            };
            authJtMa[0] = new AuthPanelJtMa(accountJtMa, enterJtMa);
            rootJtMa.add(authJtMa[0], "auth");
            if (accountJtMa.isSessionActiveJt()) {
                enterJtMa.run();
            } else {
                cardsJtMa.show(rootJtMa, "auth");
            }
            frameJtMa.setContentPane(rootJtMa);
            frameJtMa.setMinimumSize(new Dimension(860, 560));
            frameJtMa.setSize(1060, 700);
            frameJtMa.setLocationRelativeTo(null);

            TrayIcon trayJtMa = createTray(frameJtMa, themeJtMa);
            boolean[] toldJtMa = {false};
            frameJtMa.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent evJtMa) {
                    if (trayJtMa == null) {
                        exitApp(frameJtMa, themeJtMa);
                        return;
                    }
                    boolean chosenJtMa = !settingsJtMa.get(SettingsPageJtMa.CLOSE_EXITS_SETTING_MA, "").isEmpty();
                    if (settingsJtMa.getBoolean(SettingsPageJtMa.CLOSE_EXITS_SETTING_MA,
                            SettingsPageJtMa.CLOSE_EXITS_TEST_DEFAULT_MA)) {
                        // Etapa de pruebas: sin elección del usuario, cerrar la ventana sale sin pedir contraseña.
                        // Si el usuario lo activó a propósito en Ajustes, salir sigue pidiendo la contraseña.
                        if (!chosenJtMa || RuleActionsMa.unlockMa(frameJtMa, accountJtMa)) {
                            exitApp(frameJtMa, themeJtMa);
                        }
                        return;
                    }
                    frameJtMa.setVisible(false);
                    if (!toldJtMa[0]) {
                        toldJtMa[0] = true;
                        trayJtMa.displayMessage("AppControl", "Sigue funcionando en segundo plano. Ábrelo desde "
                                + "el icono de la bandeja.", TrayIcon.MessageType.INFO);
                    }
                }
            });
            frameJtMa.setVisible(true);
            // Abrir AppControl otra vez (Escritorio, barra de tareas, Inicio) solo trae esta ventana al frente.
            com.appcontrol.SingleInstanceMa.onActivateMa(() -> showWindow(frameJtMa));
        });
    }

    /** Crea el icono de la bandeja con "Abrir" y "Salir". Devuelve null si el sistema no tiene bandeja. */
    private TrayIcon createTray(JFrame frameJtMa, ThemeManagerMa themeJtMa) {
        if (!SystemTray.isSupported()) {
            return null;
        }
        try {
            PopupMenu menuJtMa = new PopupMenu();
            MenuItem openJtMa = new MenuItem("Abrir AppControl");
            openJtMa.addActionListener(evJtMa -> showWindow(frameJtMa));
            MenuItem exitJtMa = new MenuItem("Salir de AppControl");
            exitJtMa.addActionListener(evJtMa -> SwingUtilities.invokeLater(() -> {
                frameJtMa.setVisible(true);
                if (RuleActionsMa.unlockMa(frameJtMa, accountJtMa)) {
                    exitApp(frameJtMa, themeJtMa);
                }
            }));
            menuJtMa.add(openJtMa);
            menuJtMa.addSeparator();
            menuJtMa.add(exitJtMa);

            TrayIcon trayJtMa = new TrayIcon(trayImage(), "AppControl", menuJtMa);
            trayJtMa.setImageAutoSize(true);
            trayJtMa.addActionListener(evJtMa -> showWindow(frameJtMa));
            SystemTray.getSystemTray().add(trayJtMa);
            noticesJtMa.setHandlerMa((titleJtMa, textJtMa) -> trayJtMa.displayMessage(titleJtMa, textJtMa,
                    TrayIcon.MessageType.WARNING));
            return trayJtMa;
        } catch (Exception exJtMa) {
            return null;
        }
    }

    private static void showWindow(JFrame frameJtMa) {
        SwingUtilities.invokeLater(() -> {
            frameJtMa.setVisible(true);
            frameJtMa.setState(JFrame.NORMAL);
            frameJtMa.setAlwaysOnTop(true);
            frameJtMa.toFront();
            frameJtMa.requestFocus();
            frameJtMa.setAlwaysOnTop(false);
        });
    }

    private void exitApp(JFrame frameJtMa, ThemeManagerMa themeJtMa) {
        themeJtMa.stopJt();
        frameJtMa.dispose();
        int codeJtMa = org.springframework.boot.SpringApplication.exit(ctxJtMa);
        System.exit(codeJtMa);
    }

    /** El logo de AppControl; si el archivo no se pudiera leer, un circulo azul con una A. */
    private static BufferedImage trayImage() {
        if (LogoMa.colorMa() != null) {
            return LogoMa.squareMa(32);
        }
        BufferedImage imgJtMa = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gJtMa = imgJtMa.createGraphics();
        gJtMa.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gJtMa.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gJtMa.setColor(new Color(0x00, 0x67, 0xC0));
        gJtMa.fillOval(1, 1, 30, 30);
        gJtMa.setColor(Color.WHITE);
        gJtMa.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        FontMetrics fmJtMa = gJtMa.getFontMetrics();
        gJtMa.drawString("A", (32 - fmJtMa.stringWidth("A")) / 2, (32 - fmJtMa.getHeight()) / 2 + fmJtMa.getAscent());
        gJtMa.dispose();
        return imgJtMa;
    }
}
