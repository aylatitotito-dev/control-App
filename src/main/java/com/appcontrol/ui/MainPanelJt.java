package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.appcontrol.cloud.CloudLinkJt;
import com.appcontrol.cloud.CloudServiceMa;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.AppSettingsServiceMa;
import com.appcontrol.service.GroupServiceMa;
import com.appcontrol.service.InstalledAppsServiceJtMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.service.SummaryServiceMa;
import com.appcontrol.service.UsageServiceJt;
import com.appcontrol.service.VisitedSitesServiceMa;
import com.appcontrol.tracker.RefreshCoordinatorMa;
import com.appcontrol.web.SiteBlockingJtMa;
import com.formdev.flatlaf.FlatClientProperties;

/**
 * Ventana principal con el estilo de Zoom: barra superior (logo, búsqueda y cuenta), riel de iconos a la izquierda,
 * las pantallas sobre una superficie de esquinas redondeadas y la barra de estado abajo.
 */
public class MainPanelJt extends JPanel {

    private final CardLayout cardsJtMa = new CardLayout();
    private final JPanel contentJtMa = new JPanel(cardsJtMa);
    private final Map<String, PageMa> pagesJtMa = new LinkedHashMap<>();
    private final Map<String, RailButtonJtMa> buttonsJtMa = new LinkedHashMap<>();
    private final ThemeManagerMa themeJtMa;
    private final AccountServiceMa ownerJtMa;
    private final Runnable logoutJtMa;
    private final SettingsPageJtMa settingsPageJtMa;
    private String profileNameJtMa = "";
    private String profileInitialJtMa = "?";
    private Color profileColorJtMa;
    private final CloudServiceMa cloudJtMa;
    private final JPanel controlBadgeJtMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private final JLabel cloudStatusJtMa = new JLabel();
    private final JTextField searchJtMa = new JTextField();
    private final JButton avatarJtMa = new AvatarButtonJtMa(40);
    private final JButton updateBtnJtMa = new JButton("⬆  Actualización disponible");
    private com.appcontrol.update.UpdateServiceJt updateServiceJtMa;
    private com.appcontrol.update.UpdateInfoJtMa pendingUpdateJtMa;
    private Runnable exitForUpdateJtMa;
    private final java.util.Set<Long> promptedJtMa = new java.util.HashSet<>();
    private PageMa currentJtMa;

    public MainPanelJt(UsageServiceJt usageJtMa, RuleServiceJt rulesJtMa, AccountServiceMa accountJtMa, GroupServiceMa groupsJtMa,
            InstalledAppsServiceJtMa installedJtMa, VisitedSitesServiceMa visitedJtMa, SiteBlockingJtMa blockingJtMa,
            ActivityServiceMa activityJtMa, RefreshCoordinatorMa refreshJtMa, AppSettingsServiceMa settingsJtMa, SummaryServiceMa summaryJtMa, Runnable logoutJtMa,
            ThemeManagerMa themeJtMa, CloudServiceMa cloudJtMa) {
        super(new BorderLayout());
        setOpaque(false);
        this.themeJtMa = themeJtMa;
        this.cloudJtMa = cloudJtMa;
        this.ownerJtMa = accountJtMa;
        this.logoutJtMa = logoutJtMa;
        loadProfileJtMa();

        register(new DashboardPageJtMa(usageJtMa, rulesJtMa, groupsJtMa, this::showPage));
        AppsCatalogPageJtMa appsPageJtMa = new AppsCatalogPageJtMa(installedJtMa, rulesJtMa, accountJtMa, usageJtMa);
        appsPageJtMa.setGroupRulesJt(cloudJtMa::groupRulesMa);
        register(appsPageJtMa);
        SitesPageJt sitesPageJtMa = new SitesPageJt(rulesJtMa, accountJtMa, visitedJtMa, refreshJtMa);
        sitesPageJtMa.setGroupRulesJt(cloudJtMa::groupRulesMa);
        register(sitesPageJtMa);
        // Historial: linea de tiempo y resumen comparten el selector de dia
        javax.swing.ComboBoxModel<String> daysJtMa = HistoryDaysJt.newModelJt();
        register(new HistoryTabsPageJtMa(
                new HistoryPageMa(activityJtMa, rulesJtMa, accountJtMa, installedJtMa, refreshJtMa, daysJtMa),
                new HistorySummaryJt(summaryJtMa, installedJtMa, daysJtMa)));
        register(new GroupsPageMa(cloudJtMa, accountJtMa));
        settingsPageJtMa = new SettingsPageJtMa(themeJtMa, accountJtMa, blockingJtMa, settingsJtMa, logoutJtMa);
        settingsPageJtMa.setOnProfileChangedJt(this::refreshProfileJtMa);
        register(settingsPageJtMa);

        contentJtMa.setOpaque(false);
        SurfaceJtMa surfaceJtMa = new SurfaceJtMa();
        surfaceJtMa.add(contentJtMa, BorderLayout.CENTER);
        JPanel centerWrapJtMa = new JPanel(new BorderLayout());
        centerWrapJtMa.setOpaque(false);
        centerWrapJtMa.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 12));
        centerWrapJtMa.add(surfaceJtMa, BorderLayout.CENTER);

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildRail(), BorderLayout.WEST);
        add(centerWrapJtMa, BorderLayout.CENTER);

        // Ctrl+E lleva el cursor al buscador, como en Zoom
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK), "focusSearch");
        getActionMap().put("focusSearch", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evJtMa) {
                searchJtMa.requestFocusInWindow();
                searchJtMa.selectAll();
            }
        });

        showPage("Panel Principal");
        themeJtMa.addListenerJt(() -> {
            buttonsJtMa.values().forEach(RailButtonJtMa::refreshIconJtMa);
            PageMa settingsRefreshJtMa = pagesJtMa.get("Ajustes");
            if (settingsRefreshJtMa != null) {
                settingsRefreshJtMa.refreshMa();
            }
            if (currentJtMa != null) {
                currentJtMa.refreshMa();
            }
            repaint();
        });
        new Timer(5000, evJtMa -> currentJtMa.refreshMa()).start();
        new Timer(5000, evJtMa -> refreshCloudJtMa()).start();
    }

    /**
     * Activa las actualizaciones: busca una versión nueva poco después de abrir y cada 4 horas. Si hay una, aparece el botón
     * "Actualización disponible" arriba (y en Configuración). Sin internet no pasa nada.
     */
    void setUpdateServiceJt(com.appcontrol.update.UpdateServiceJt serviceJtMa, Runnable exitJtMa) {
        this.updateServiceJtMa = serviceJtMa;
        this.exitForUpdateJtMa = exitJtMa;
        settingsPageJtMa.setUpdateServiceJt(serviceJtMa, exitJtMa, this::onUpdateFoundJtMa);
        Timer firstJtMa = new Timer(25_000, evJtMa -> checkUpdateJtMa());
        firstJtMa.setRepeats(false);
        firstJtMa.start();
        new Timer(4 * 60 * 60 * 1000, evJtMa -> checkUpdateJtMa()).start();
    }

    private void checkUpdateJtMa() {
        if (updateServiceJtMa == null || pendingUpdateJtMa != null) {
            return;
        }
        UiAsyncJt.runJt(() -> updateServiceJtMa.checkJt(), foundJtMa -> foundJtMa.ifPresent(this::onUpdateFoundJtMa),
                errorJtMa -> { });
    }

    private void onUpdateFoundJtMa(com.appcontrol.update.UpdateInfoJtMa infoJtMa) {
        pendingUpdateJtMa = infoJtMa;
        updateBtnJtMa.setVisible(true);
        revalidate();
    }

    /** El marco (barra superior, riel y bordes) se pinta con su color propio; las pantallas van encima. */
    @Override
    protected void paintComponent(Graphics gJtMa) {
        gJtMa.setColor(ThemeManagerMa.frameColorJt());
        gJtMa.fillRect(0, 0, getWidth(), getHeight());
    }

    private void register(PageMa pageJtMa) {
        pagesJtMa.put(pageJtMa.navTitle(), pageJtMa);
        contentJtMa.add(pageJtMa, pageJtMa.navTitle());
    }

    // ------------------------------------------------------------------ riel de iconos

    private JPanel buildRail() {
        JPanel railJtMa = new JPanel();
        railJtMa.setOpaque(false);
        railJtMa.setLayout(new BoxLayout(railJtMa, BoxLayout.Y_AXIS));
        railJtMa.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        railJtMa.setPreferredSize(new Dimension(104, 0));

        ButtonGroup groupJtMa = new ButtonGroup();
        String[][] navJtMa = {
            {"Inicio", "Panel Principal", "home"}, {"Aplicaciones", "Aplicaciones", "block"},
            {"Sitios", "Sitios y URLs", "globe"}, {"Historial", "Historial", "history"},
            {"Grupos", "Grupos y Sesiones", "users"}};
        for (String[] itemJtMa : navJtMa) {
            railJtMa.add(railButtonJtMa(itemJtMa[0], itemJtMa[1], itemJtMa[2], groupJtMa));
            railJtMa.add(Box.createVerticalStrut(4));
        }
        railJtMa.add(Box.createVerticalGlue());
        railJtMa.add(railButtonJtMa("Configuración", "Ajustes", "gear", groupJtMa));
        return railJtMa;
    }

    private RailButtonJtMa railButtonJtMa(String labelJtMa, String keyJtMa, String iconJtMa, ButtonGroup groupJtMa) {
        RailButtonJtMa btnJtMa = new RailButtonJtMa(labelJtMa, iconJtMa);
        btnJtMa.setAlignmentX(CENTER_ALIGNMENT);
        btnJtMa.addActionListener(evJtMa -> showPage(keyJtMa));
        groupJtMa.add(btnJtMa);
        buttonsJtMa.put(keyJtMa, btnJtMa);
        return btnJtMa;
    }

    // ------------------------------------------------------------------ barra superior

    private JPanel buildTopBar() {
        JPanel barJtMa = new JPanel(new BorderLayout(16, 0));
        barJtMa.setOpaque(false);
        barJtMa.setBorder(BorderFactory.createEmptyBorder(8, 16, 6, 16));

        JLabel brandJtMa = new JLabel("AppControl");
        brandJtMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        if (LogoMa.colorMa() != null) {
            brandJtMa.setIcon(new ImageIcon(LogoMa.scaledMa(LogoMa.colorMa(), 26)));
            brandJtMa.setIconTextGap(10);
        }
        brandJtMa.setPreferredSize(new Dimension(190, 40));

        searchJtMa.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Buscar (Ctrl+E)");
        searchJtMa.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, IconsJtMa.of("search", 16));
        searchJtMa.putClientProperty(FlatClientProperties.STYLE,
                "arc: 999; borderWidth: 0; margin: 4,12,4,12; background: $AppControl.hover");
        searchJtMa.setPreferredSize(new Dimension(520, 38));
        UiKitJtMa.onChange(searchJtMa, this::showSearchResultsJtMa);
        searchJtMa.addActionListener(evJtMa -> {
            List<String[]> matchesJtMa = searchMatchesJtMa();
            if (!matchesJtMa.isEmpty()) {
                goToJtMa(matchesJtMa.get(0)[1]);
            }
        });
        JPanel searchWrapJtMa = new JPanel(new GridBagLayout());
        searchWrapJtMa.setOpaque(false);
        searchWrapJtMa.add(searchJtMa);

        avatarJtMa.setToolTipText("Tu cuenta");
        avatarJtMa.addActionListener(evJtMa -> accountMenuJtMa().show(avatarJtMa, avatarJtMa.getWidth()
                - 300, avatarJtMa.getHeight() + 6));
        controlBadgeJtMa.setOpaque(false);
        JPanel rightJtMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightJtMa.setOpaque(false);
        rightJtMa.add(controlBadgeJtMa);
        updateBtnJtMa.setVisible(false);
        updateBtnJtMa.setToolTipText("Hay una versión nueva de AppControl: se actualiza sin reinstalar");
        updateBtnJtMa.putClientProperty(FlatClientProperties.STYLE,
                "arc: 999; background: $Component.accentColor; foreground: #FFFFFF; borderWidth: 0; font: bold; margin: 6,16,6,16");
        updateBtnJtMa.addActionListener(evJtMa -> {
            if (updateServiceJtMa != null && pendingUpdateJtMa != null) {
                UpdateFlowJt.startJt(this, updateServiceJtMa, pendingUpdateJtMa, exitForUpdateJtMa);
            }
        });
        rightJtMa.add(updateBtnJtMa);
        rightJtMa.add(avatarJtMa);
        JButton logoutTopJtMa = new JButton(IconsJtMa.colored("logout", 22, ThemeManagerMa.hexJt(ThemeManagerMa.mutedColorJt())));
        logoutTopJtMa.setToolTipText("Cerrar sesión");
        logoutTopJtMa.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        logoutTopJtMa.setFocusable(false);
        logoutTopJtMa.setPreferredSize(new Dimension(40, 40));
        logoutTopJtMa.addActionListener(evJtMa -> logoutJtMa.run());
        themeJtMa.addListenerJt(() -> logoutTopJtMa.setIcon(
                IconsJtMa.colored("logout", 22, ThemeManagerMa.hexJt(ThemeManagerMa.mutedColorJt()))));
        rightJtMa.add(logoutTopJtMa);

        barJtMa.add(brandJtMa, BorderLayout.WEST);
        barJtMa.add(searchWrapJtMa, BorderLayout.CENTER);
        barJtMa.add(rightJtMa, BorderLayout.EAST);
        return barJtMa;
    }

    /** Lugares a los que se puede saltar desde el buscador: {texto, destino}. */
    private static final String[][] SEARCH_ITEMS_JTMA = {
        {"Inicio · Panel principal", "Panel Principal"}, {"Aplicaciones", "Aplicaciones"},
        {"Sitios y URLs", "Sitios y URLs"}, {"Historial · Línea de tiempo", "Historial"},
        {"Historial · Resumen por día", "Historial:resumen"}, {"Grupos y Sesiones", "Grupos y Sesiones"},
        {"Configuración", "Ajustes"}, {"Editar perfil", "@perfil"}};

    private List<String[]> searchMatchesJtMa() {
        String queryJtMa = searchJtMa.getText().trim().toLowerCase();
        List<String[]> outJtMa = new ArrayList<>();
        if (queryJtMa.isEmpty()) {
            return outJtMa;
        }
        for (String[] itemJtMa : SEARCH_ITEMS_JTMA) {
            if (itemJtMa[0].toLowerCase().contains(queryJtMa)) {
                outJtMa.add(itemJtMa);
            }
        }
        return outJtMa;
    }

    private void showSearchResultsJtMa() {
        List<String[]> matchesJtMa = searchMatchesJtMa();
        if (matchesJtMa.isEmpty() || !searchJtMa.isShowing()) {
            return;
        }
        JPopupMenu menuJtMa = new JPopupMenu();
        menuJtMa.setFocusable(false);
        for (String[] itemJtMa : matchesJtMa) {
            JMenuItem entryJtMa = new JMenuItem(itemJtMa[0]);
            entryJtMa.addActionListener(evJtMa -> goToJtMa(itemJtMa[1]));
            menuJtMa.add(entryJtMa);
        }
        menuJtMa.show(searchJtMa, 0, searchJtMa.getHeight() + 4);
        searchJtMa.requestFocusInWindow();
    }

    private void goToJtMa(String targetJtMa) {
        searchJtMa.setText("");
        if ("@perfil".equals(targetJtMa)) {
            showPage("Ajustes");
            settingsPageJtMa.openProfileEditorMa();
        } else {
            showPage(targetJtMa);
        }
    }

    /** El menú de la cuenta: nombre, estado de la nube, perfil, configuración y cerrar sesión. */
    private JPopupMenu accountMenuJtMa() {
        JPopupMenu menuJtMa = new JPopupMenu();
        JPanel headJtMa = new JPanel();
        headJtMa.setOpaque(false);
        headJtMa.setLayout(new BoxLayout(headJtMa, BoxLayout.Y_AXIS));
        headJtMa.setBorder(BorderFactory.createEmptyBorder(16, 24, 12, 24));
        AvatarButtonJtMa bigJtMa = new AvatarButtonJtMa(64);
        bigJtMa.setEnabled(false);
        bigJtMa.setAlignmentX(CENTER_ALIGNMENT);
        JLabel nameJtMa = new JLabel(profileNameJtMa);
        nameJtMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        nameJtMa.setAlignmentX(CENTER_ALIGNMENT);
        JLabel modeJtMa = UiKitJtMa.subtitle("Uso personal");
        modeJtMa.setAlignmentX(CENTER_ALIGNMENT);
        headJtMa.add(bigJtMa);
        headJtMa.add(Box.createVerticalStrut(8));
        headJtMa.add(nameJtMa);
        headJtMa.add(modeJtMa);
        menuJtMa.add(headJtMa);
        menuJtMa.addSeparator();

        boolean onlineJtMa = cloudJtMa.isOnlineMa();
        JMenuItem statusJtMa = new JMenuItem(onlineJtMa ? "●  Conectado a la nube" : "○  Sin conexión (modo local)");
        statusJtMa.setEnabled(false);
        menuJtMa.add(statusJtMa);
        JMenuItem profileJtMa = new JMenuItem("Editar perfil");
        profileJtMa.addActionListener(evJtMa -> goToJtMa("@perfil"));
        menuJtMa.add(profileJtMa);
        JMenuItem settingsJtMa = new JMenuItem("Configuración");
        settingsJtMa.addActionListener(evJtMa -> showPage("Ajustes"));
        menuJtMa.add(settingsJtMa);
        menuJtMa.addSeparator();
        JMenuItem logoutItemJtMa = new JMenuItem("Cerrar sesión");
        logoutItemJtMa.addActionListener(evJtMa -> logoutJtMa.run());
        menuJtMa.add(logoutItemJtMa);
        menuJtMa.setPreferredSize(new Dimension(300, menuJtMa.getPreferredSize().height));
        return menuJtMa;
    }

    // ------------------------------------------------------------------ perfil

    private void loadProfileJtMa() {
        String ownerNameJtMa = ownerJtMa.usernameJt();
        profileNameJtMa = ownerNameJtMa.isEmpty() ? System.getProperty("user.name", "Usuario") : ownerNameJtMa;
        profileInitialJtMa = profileNameJtMa.isEmpty() ? "?" : profileNameJtMa.substring(0, 1).toUpperCase();
        profileColorJtMa = parseColorJtMa(ownerJtMa.avatarColorJt());
    }

    private static Color parseColorJtMa(String hexJtMa) {
        try {
            return hexJtMa == null || hexJtMa.isEmpty() ? null : Color.decode(hexJtMa);
        } catch (NumberFormatException exJtMa) {
            return null;
        }
    }

    /** Se llama al guardar "Editar perfil": el círculo de arriba muestra enseguida el nombre y color nuevos. */
    private void refreshProfileJtMa() {
        loadProfileJtMa();
        avatarJtMa.repaint();
    }

    /** Círculo con la inicial de la persona (color de acento o el que eligió). */
    private final class AvatarButtonJtMa extends JButton {
        private final int sizeJtMa;

        AvatarButtonJtMa(int sizeJtMa) {
            this.sizeJtMa = sizeJtMa;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
            setPreferredSize(new Dimension(sizeJtMa, sizeJtMa));
            setMinimumSize(new Dimension(sizeJtMa, sizeJtMa));
            setMaximumSize(new Dimension(sizeJtMa, sizeJtMa)); // sin esto, el menú lo recortaba
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics gJtMa) {
            Graphics2D g2JtMa = (Graphics2D) gJtMa.create();
            g2JtMa.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2JtMa.setColor(profileColorJtMa != null ? profileColorJtMa : ThemeManagerMa.accentColorJt());
            g2JtMa.fillOval(0, 0, sizeJtMa, sizeJtMa);
            g2JtMa.setColor(Color.WHITE);
            g2JtMa.setFont(getFont().deriveFont(Font.BOLD, sizeJtMa * 0.45f));
            java.awt.FontMetrics fmJtMa = g2JtMa.getFontMetrics();
            g2JtMa.drawString(profileInitialJtMa, (sizeJtMa - fmJtMa.stringWidth(profileInitialJtMa)) / 2,
                    (sizeJtMa - fmJtMa.getHeight()) / 2 + fmJtMa.getAscent());
            g2JtMa.dispose();
        }
    }

    // ------------------------------------------------------------------ barra de estado

    private JPanel buildStatusBar() {
        JPanel barJtMa = new JPanel(new BorderLayout());
        barJtMa.setOpaque(false);
        barJtMa.setBorder(BorderFactory.createEmptyBorder(5, 18, 7, 18));
        JLabel leftJtMa = new JLabel("<html><span style='color:#2e9e4f'>●</span>  Monitoreo activo · datos guardados en este equipo</html>");
        JLabel rightJtMa = cloudStatusJtMa;
        cloudStatusJtMa.setText("<html><span style='color:#9a9a9a'>●</span>  Sin conexión a Azure (modo local)</html>");
        leftJtMa.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        rightJtMa.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        barJtMa.add(leftJtMa, BorderLayout.WEST);
        barJtMa.add(rightJtMa, BorderLayout.EAST);
        return barJtMa;
    }

    /** Cada 5 s: pregunta por solicitudes de control nuevas, muestra "controlado por" y el estado de la nube. */
    private void refreshCloudJtMa() {
        java.time.format.DateTimeFormatter hourJtMa = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                .withZone(java.time.ZoneId.systemDefault());
        controlBadgeJtMa.removeAll();
        for (CloudLinkJt linkJtMa : cloudJtMa.controlledByMa()) {
            String untilJtMa = linkJtMa.getControlUntil() == null ? "permanente"
                    : "hasta " + hourJtMa.format(linkJtMa.getControlUntil());
            controlBadgeJtMa.add(new ChipMa("Controlado por " + linkJtMa.getOwnerName() + " · " + untilJtMa,
                    ChipMa.KindJtMa.WARN));
        }
        controlBadgeJtMa.revalidate();
        controlBadgeJtMa.repaint();
        boolean onlineJtMa = cloudJtMa.isOnlineMa();
        cloudStatusJtMa.setText(onlineJtMa
                ? "<html><span style='color:#2e9e4f'>●</span>  Conectado a la nube de Azure</html>"
                : "<html><span style='color:#9a9a9a'>●</span>  Sin conexión a Azure (modo local)</html>");
        for (CloudLinkJt linkJtMa : cloudJtMa.pendingRequestsMa()) {
            if (promptedJtMa.add(linkJtMa.getMemberId())) {
                SwingUtilities.invokeLater(() -> ControlPromptJt.askJt(this, cloudJtMa, ownerJtMa, linkJtMa,
                        this::refreshCloudJtMa));
                break;
            }
        }
    }

    /** Muestra una pantalla por su titulo de menu. */
    void showPage(String requestedJtMa) {
        // "Historial:resumen" abre el Historial directamente en la pestaña Resumen
        int colonJtMa = requestedJtMa.indexOf(':');
        String titleJtMa = colonJtMa > 0 ? requestedJtMa.substring(0, colonJtMa) : requestedJtMa;
        boolean summaryJtMa = colonJtMa > 0 && "resumen".equals(requestedJtMa.substring(colonJtMa + 1));
        PageMa pageJtMa = pagesJtMa.get(titleJtMa);
        if (pageJtMa == null) {
            return;
        }
        if (summaryJtMa && pageJtMa instanceof HistoryTabsPageJtMa tabsJtMa) {
            tabsJtMa.showSummaryMa();
        }
        currentJtMa = pageJtMa;
        JToggleButton btnJtMa = buttonsJtMa.get(titleJtMa);
        if (btnJtMa != null) {
            btnJtMa.setSelected(true);
        }
        buttonsJtMa.values().forEach(RailButtonJtMa::refreshIconJtMa);
        pageJtMa.refreshMa();
        cardsJtMa.show(contentJtMa, titleJtMa);
    }

    /** Para las pruebas: el componente del riel (por si hay que comprobar el aspecto). */
    JComponent railButtonForTestJtMa(String keyJtMa) {
        return buttonsJtMa.get(keyJtMa);
    }
}
