package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import com.appcontrol.cloud.CloudDtoJt.AppDtoMa;
import com.appcontrol.cloud.CloudDtoJt.EventDtoJt;
import com.appcontrol.cloud.CloudDtoJt.MemberOutJt;
import com.appcontrol.cloud.CloudDtoJt.RuleDtoJt;
import com.appcontrol.cloud.CloudDtoJt.UsageDtoMa;
import com.appcontrol.cloud.CloudServiceMa;
import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.RuleServiceJt;
import com.formdev.flatlaf.FlatClientProperties;

/**
 * El equipo de otra persona visto desde quien la controla. Solo tres pestañas (Aplicaciones, Sitios y URLs e
 * Historial): no hay Panel Principal ni Grupos. Todo cambio de reglas pide la contraseña de esta cuenta.
 */
class MemberViewJtMa extends JPanel {

    private static final String[] RANGES_JT = {"Hoy", "Ayer", "Últimos 7 días", "Semana anterior", "Últimos 30 días",
        "📅  Elegir fecha…"};
    private static final int PICK_INDEX_JT = 5;
    private static final DateTimeFormatter DAY_HOUR_JT = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter HOUR_JT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    /** Todo lo que se baja de la nube de una vez. */
    record LoadedMa(List<UsageDtoMa> usage, List<EventDtoJt> events, List<AppDtoMa> apps,
            List<RuleDtoJt> rules, Map<String, String> status) {
    }

    private final CloudServiceMa cloudJt;
    private final AccountServiceMa accountJt;
    private final long groupIdJt;
    private final MemberOutJt memberJt;
    private final Runnable onBackJt;

    private final JComboBox<String> rangeJt = new JComboBox<>(new javax.swing.DefaultComboBoxModel<>(RANGES_JT));
    /** Día elegido con el calendario (índice 5 del selector de periodo). */
    private LocalDate pickedDayJt = LocalDate.now();
    private boolean pickingJt;
    private final JLabel statusJt = UiKitJtMa.subtitle("Cargando…");
    private final String groupNameJt;
    /** El control ya terminó: solo se ve el historial de lo que pasó mientras duró. */
    private final boolean historyOnlyJt;
    private JPanel headerPanelJt;
    private JPanel detailPanelJt;
    private final javax.swing.JTabbedPane tabsJt = new javax.swing.JTabbedPane();
    private final RemoteHistoryPanelJtMa historyPanelJt = new RemoteHistoryPanelJtMa(this);
    private LoadedMa loadedJt = new LoadedMa(List.of(), List.of(), List.of(), List.of(), Map.of());
    private final RemoteListPageJtMa appsPageJt = new RemoteListPageJtMa(this, BlockRuleJtMa.APP);
    private final RemoteListPageJtMa sitesPageJt = new RemoteListPageJtMa(this, BlockRuleJtMa.SITE);
    private boolean busyJt;

    MemberViewJtMa(CloudServiceMa cloudJt, AccountServiceMa accountJt, long groupIdJt, MemberOutJt memberJt,
            String groupNameJt, boolean historyOnlyJt, Runnable onBackJt) {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        this.cloudJt = cloudJt;
        this.accountJt = accountJt;
        this.groupIdJt = groupIdJt;
        this.memberJt = memberJt;
        this.onBackJt = onBackJt;
        this.groupNameJt = groupNameJt;
        this.historyOnlyJt = historyOnlyJt;

        JButton backJt = new JButton("←  Volver a Grupo " + groupNameJt);
        backJt.addActionListener(evJt -> onBackJt.run());
        JPanel backRowJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        backRowJt.setOpaque(false);
        backRowJt.add(backJt);

        JButton refreshJt = new JButton("Actualizar datos", IconsJtMa.of("refresh", 16));
        refreshJt.setToolTipText("Vuelve a pedir los datos de este equipo ahora mismo");
        refreshJt.addActionListener(evJt -> reloadJt());
        JButton screenJt = new JButton("Ver pantalla en vivo");
        screenJt.setVisible(!historyOnlyJt);
        screenJt.addActionListener(evJt -> ScreenViewerJtMa.openMa(javax.swing.SwingUtilities.getWindowAncestor(this),
                cloudJt, groupIdJt, memberJt.id(), memberJt.displayName()));
        JButton endJt = new JButton("Terminar control");
        endJt.setVisible(!historyOnlyJt);
        endJt.putClientProperty(FlatClientProperties.STYLE,
                "background: #FDECEC; foreground: #C62828; borderColor: #F5B5B5; focusedBorderColor: #C62828");
        endJt.addActionListener(evJt -> endControlJt());

        JPanel titleBlockJt = new JPanel(new BorderLayout(0, 6));
        titleBlockJt.setOpaque(false);
        titleBlockJt.add(UiKitJtMa.title(memberJt.displayName()), BorderLayout.NORTH);
        titleBlockJt.add(statusJt, BorderLayout.SOUTH);
        JPanel rightJt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightJt.setOpaque(false);
        rightJt.add(UiKitJtMa.subtitle("Periodo:"));
        rightJt.add(rangeJt);
        JButton dateJt = new JButton("📅  Elegir fecha");
        dateJt.setToolTipText("Abre el calendario para ver el historial de un día");
        dateJt.addActionListener(evJt -> chooseDateJt());
        rightJt.add(dateJt);
        rightJt.add(refreshJt);
        rightJt.add(screenJt);
        rightJt.add(endJt);
        JPanel rightWrapJt = new JPanel(new java.awt.GridBagLayout());
        rightWrapJt.setOpaque(false);
        rightWrapJt.add(rightJt);
        JPanel headJt = new JPanel(new BorderLayout(12, 0));
        headJt.setOpaque(false);
        headJt.add(titleBlockJt, BorderLayout.CENTER); // así el texto no se monta sobre los botones
        headJt.add(rightWrapJt, BorderLayout.EAST);

        headerPanelJt = new JPanel(new BorderLayout(0, 14));
        headerPanelJt.setOpaque(false);
        headerPanelJt.add(backRowJt, BorderLayout.NORTH);
        headerPanelJt.add(headJt, BorderLayout.CENTER);

        // ---- Detalle: nombre, X para cerrar, pestañas y "Configurar Bloqueos"
        tabsJt.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_TYPE,
                FlatClientProperties.TABBED_PANE_TAB_TYPE_UNDERLINED);
        tabsJt.putClientProperty(FlatClientProperties.STYLE, "tabHeight: 38");
        if (!historyOnlyJt) {
            tabsJt.addTab("Aplicaciones", appsPageJt);
            tabsJt.addTab("Sitios y URLs", sitesPageJt);
        }
        tabsJt.addTab(historyOnlyJt ? "Historial de Actividad" : "Historial", historyPanelJt);

        JPanel detailTitleJt = new JPanel(new BorderLayout(0, 2));
        detailTitleJt.setOpaque(false);
        detailTitleJt.add(UiKitJtMa.sectionTitle("Detalle de " + memberJt.displayName()), BorderLayout.NORTH);
        detailTitleJt.add(UiKitJtMa.subtitle(historyOnlyJt ? "Control terminado: solo el historial de ese tiempo"
                : "Presiona para gestionar o ver historial"), BorderLayout.SOUTH);
        JButton closeJt = new JButton(memberJt.displayName() + "   ✕");
        closeJt.setToolTipText("Cerrar el detalle y volver a ver todas las pantallas");
        closeJt.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 4,10,4,10");
        closeJt.setFocusable(false);
        closeJt.addActionListener(evJt -> onBackJt.run());
        JPanel closeWrapJt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeWrapJt.setOpaque(false);
        closeWrapJt.add(closeJt);
        JPanel detailHeadJt = new JPanel(new BorderLayout(12, 0));
        detailHeadJt.setOpaque(false);
        detailHeadJt.add(detailTitleJt, BorderLayout.CENTER);
        detailHeadJt.add(closeWrapJt, BorderLayout.EAST);

        JButton rulesBtnJt = new JButton("Configurar Bloqueos", IconsJtMa.of("gear", 16));
        rulesBtnJt.setVisible(!historyOnlyJt);
        rulesBtnJt.addActionListener(evJt -> manageRulesJt());
        JPanel footJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footJt.setOpaque(false);
        footJt.add(rulesBtnJt);

        detailPanelJt = UiKitJtMa.roundedPanel(16);
        detailPanelJt.setLayout(new BorderLayout(0, 10));
        detailPanelJt.add(detailHeadJt, BorderLayout.NORTH);
        detailPanelJt.add(tabsJt, BorderLayout.CENTER);
        detailPanelJt.add(footJt, BorderLayout.SOUTH);

        add(headerPanelJt, BorderLayout.NORTH);
        add(detailPanelJt, BorderLayout.CENTER);

        rangeJt.addActionListener(evJt -> {
            if (pickingJt) {
                return;
            }
            if (rangeJt.getSelectedIndex() == PICK_INDEX_JT && isShowing()) {
                pickDayJt();
                return;
            }
            reloadJt();
        });
        reloadJt();
    }

    // ------------------------------------------------------------------ pestañas

    // ------------------------------------------------------------------ datos

    private LocalDate fromDayJt() {
        return switch (rangeJt.getSelectedIndex()) {
            case 1 -> LocalDate.now().minusDays(1);
            case 2 -> LocalDate.now().minusDays(6);
            case 3 -> LocalDate.now().minusDays(13);
            case 4 -> LocalDate.now().minusDays(29);
            case PICK_INDEX_JT -> pickedDayJt;
            default -> LocalDate.now();
        };
    }

    private LocalDate toDayJt() {
        return switch (rangeJt.getSelectedIndex()) {
            case 1 -> LocalDate.now().minusDays(1);
            case 3 -> LocalDate.now().minusDays(7);
            case PICK_INDEX_JT -> pickedDayJt;
            default -> LocalDate.now();
        };
    }

    /** Para los botones «Elegir fecha» (la cabecera y el historial). */
    void chooseDateJt() {
        pickDayJt();
    }

    /** Abre el calendario; el día elegido pasa a ser el periodo (y se escribe en el selector). */
    private void pickDayJt() {
        LocalDate todayJt = LocalDate.now();
        LocalDate dayJt = DatePickerMa.showMa(this, pickedDayJt, todayJt.minusDays(HistoryDaysJt.KEEP_DAYS_JT), todayJt);
        pickingJt = true;
        try {
            if (dayJt == null) {
                rangeJt.setSelectedIndex(0);
            } else {
                pickedDayJt = dayJt;
                javax.swing.DefaultComboBoxModel<String> modelJt = (javax.swing.DefaultComboBoxModel<String>) rangeJt.getModel();
                modelJt.removeElementAt(PICK_INDEX_JT);
                modelJt.insertElementAt("📅  " + dayJt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), PICK_INDEX_JT);
                rangeJt.setSelectedIndex(PICK_INDEX_JT);
            }
        } finally {
            pickingJt = false;
        }
        reloadJt();
    }

    /** Pide a la nube todo lo de este equipo (puede tardar: no llamar desde el hilo de la ventana). */
    private LoadedMa fetchJt() {
        LocalDate fromJt = fromDayJt();
        LocalDate toJt = toDayJt();
        Instant fromInstantJt = fromJt.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toInstantJt = toJt.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long memberIdJt = memberJt.id();
        if (historyOnlyJt) {
            Map<String, String> infoJt = new java.util.HashMap<>();
            try {
                StringBuilder tramosJt = new StringBuilder();
                for (com.appcontrol.cloud.CloudDtoJt.SessionDtoJt sessionJt : cloudJt.client().sessions(groupIdJt, memberIdJt)) {
                    tramosJt.append(tramosJt.length() == 0 ? "" : " · ").append(DAY_HOUR_JT.format(sessionJt.startedAt()))
                            .append(" a ").append(sessionJt.endedAt() == null ? "ahora" : DAY_HOUR_JT.format(sessionJt.endedAt()));
                }
                infoJt.put("sessionsText", tramosJt.toString());
            } catch (RuntimeException exJt) {
                infoJt.put("sessionsText", "");
            }
            return new LoadedMa(List.of(), cloudJt.client().events(groupIdJt, memberIdJt, fromInstantJt, toInstantJt),
                    List.of(), List.of(), infoJt);
        }
        return new LoadedMa(cloudJt.client().usage(groupIdJt, memberIdJt, fromJt, toJt),
                cloudJt.client().events(groupIdJt, memberIdJt, fromInstantJt, toInstantJt),
                cloudJt.client().apps(groupIdJt, memberIdJt), cloudJt.client().rules(groupIdJt, memberIdJt),
                statusOrEmptyJt(memberIdJt));
    }

    private Map<String, String> statusOrEmptyJt(long memberIdJt) {
        try {
            return cloudJt.client().memberStatus(groupIdJt, memberIdJt);
        } catch (RuntimeException exJt) {
            return Map.of();
        }
    }

    /** "● Conectado · Actualizado hoy 22:33 · Envía telemetría cada ~45s", con aviso si algo no se cumple. */
    private static String statusTextJt(Map<String, String> statusJt, String periodJt) {
        boolean liveJt = false;
        try {
            liveJt = !statusJt.isEmpty() && Long.parseLong(statusJt.getOrDefault("secondsAgo", "999999")) < 150;
        } catch (NumberFormatException exJt) {
            liveJt = false;
        }
        String dotJt = liveJt ? "<span style='color:#2e9e4f'>●</span>" : "<span style='color:#9a9a9a'>●</span>";
        String stateJt = liveJt ? "Conectado" : "Sin señal reciente";
        StringBuilder outJt = new StringBuilder("<html>").append(dotJt).append("  ").append(stateJt)
                .append(" · Actualizado ").append(periodJt).append(" ")
                .append(HOUR_JT.format(Instant.now())).append(" · Envía telemetría cada ~45s");
        String problemJt = switch (statusJt.getOrDefault("sitios", "")) {
            case "CONFLICT" -> "no puede bloquear sitios: ese equipo ya usa otro proxy o configuración de red";
            case "PROXY_DOWN" -> "no puede bloquear sitios: no pudo abrir su proxy local";
            case "OFF" -> "no bloquea sitios: lo apagaron en Ajustes";
            case "UNSUPPORTED" -> "no puede bloquear sitios: no es Windows";
            default -> "";
        };
        if (!problemJt.isEmpty()) {
            outJt.append("<br><span style='color:#C62828'>⚠ Su equipo ").append(problemJt).append("</span>");
        }
        if (statusJt.isEmpty()) {
            outJt.append("<br><span style='color:#9a9a9a'>Aún no informó su estado (puede tener una versión anterior).</span>");
        } else {
            outJt.append("<br><span style='color:#9a9a9a'>AppControl ").append(statusJt.getOrDefault("version", "?"))
                    .append("</span>");
        }
        return outJt.append("</html>").toString();
    }

    /** Lo usan las listas de tarjetas al pulsar su botón de actualizar (corre en segundo plano). */
    void fetchNowJt() {
        loadedJt = fetchJt();
    }

    /** Lo último que se bajó de la nube. */
    LoadedMa loadedNowJt() {
        return loadedJt;
    }

    void reloadJt() {
        if (busyJt) {
            return;
        }
        busyJt = true;
        statusJt.setText("Actualizando…");
        UiAsyncJt.runJt(this::fetchJt, loadedNowJt -> {
            busyJt = false;
            loadedJt = loadedNowJt;
            if (historyOnlyJt) {
                String tramosJt = loadedNowJt.status().getOrDefault("sessionsText", "");
                statusJt.setText("<html><span style='color:#9a9a9a'>●</span>  Control terminado · solo ves lo que pasó mientras "
                        + "estuvo bajo control" + (tramosJt.isEmpty() ? "" : "<br><span style='color:#9a9a9a'>Control: " + tramosJt
                        + "</span>") + "</html>");
            } else {
                statusJt.setText(statusTextJt(loadedNowJt.status(), rangeJt.getSelectedIndex() == 0 ? "hoy"
                        : rangeJt.getSelectedIndex() == 1 ? "ayer"
                        : rangeJt.getSelectedIndex() == PICK_INDEX_JT ? "el "
                        + pickedDayJt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : "los " + rangeJt.getSelectedItem()));
                tabsJt.setTitleAt(0, "Aplicaciones (" + appsPageJt.countJt() + ")");
                tabsJt.setTitleAt(1, "Sitios y URLs (" + sitesPageJt.countJt() + ")");
            }
            appsPageJt.rebuildMa(true);
            sitesPageJt.rebuildMa(true);
            fillHistoryJt();
        }, errorJt -> {
            busyJt = false;
            statusJt.setText(errorJt);
        });
    }

    private RuleDtoJt ruleOfJt(String kindJt, String keyJt) {
        for (RuleDtoJt ruleJt : loadedJt.rules()) {
            if (ruleJt.kind().equals(kindJt) && ruleJt.targetKey().equalsIgnoreCase(keyJt)) {
                return ruleJt;
            }
        }
        return null;
    }

    /** Una fila de la lista de aplicaciones o de sitios (tiempo en segundos; visitas solo para sitios). */
    record RowMa(String key, String name, long seconds, long visits, RuleDtoJt rule) {
    }

    List<RowMa> rowsJt(String kindJt) {
        return BlockRuleJtMa.APP.equals(kindJt) ? appRowsJt() : siteRowsJt();
    }

    private List<RowMa> appRowsJt() {
        Map<String, long[]> secondsJt = new LinkedHashMap<>();
        Map<String, String> namesJt = new HashMap<>();
        for (UsageDtoMa usageJt : loadedJt.usage()) {
            String keyJt = usageJt.appKey().toLowerCase(Locale.ROOT);
            secondsJt.computeIfAbsent(keyJt, kJt -> new long[1])[0] += usageJt.seconds();
            namesJt.put(keyJt, usageJt.displayName());
        }
        for (AppDtoMa appJt : loadedJt.apps()) {
            String keyJt = appJt.appKey().toLowerCase(Locale.ROOT);
            secondsJt.computeIfAbsent(keyJt, kJt -> new long[1]);
            namesJt.putIfAbsent(keyJt, appJt.displayName());
        }
        for (RuleDtoJt ruleJt : loadedJt.rules()) {
            if (BlockRuleJtMa.APP.equals(ruleJt.kind())) {
                String keyJt = ruleJt.targetKey().toLowerCase(Locale.ROOT);
                secondsJt.computeIfAbsent(keyJt, kJt -> new long[1]);
                namesJt.putIfAbsent(keyJt, ruleJt.displayName());
            }
        }
        List<RowMa> outJt = new ArrayList<>();
        for (Map.Entry<String, long[]> entryJt : secondsJt.entrySet()) {
            String keyJt = entryJt.getKey();
            outJt.add(new RowMa(keyJt, namesJt.getOrDefault(keyJt, keyJt), entryJt.getValue()[0], 0,
                    ruleOfJt(BlockRuleJtMa.APP, keyJt)));
        }
        return outJt;
    }

    private List<RowMa> siteRowsJt() {
        Map<String, long[]> statsJt = new LinkedHashMap<>(); // dominio -> {visitas, segundos}
        for (EventDtoJt eventJt : loadedJt.events()) {
            if ("SITE".equals(eventJt.type())) {
                long[] rowJt = statsJt.computeIfAbsent(eventJt.targetKey().toLowerCase(Locale.ROOT), kJt -> new long[2]);
                rowJt[0] += Math.max(1, eventJt.hits());
                rowJt[1] += Math.max(0, Duration.between(eventJt.startedAt(), eventJt.endedAt()).toSeconds());
            }
        }
        for (RuleDtoJt ruleJt : loadedJt.rules()) {
            if (BlockRuleJtMa.SITE.equals(ruleJt.kind())) {
                statsJt.computeIfAbsent(ruleJt.targetKey().toLowerCase(Locale.ROOT), kJt -> new long[2]);
            }
        }
        List<RowMa> outJt = new ArrayList<>();
        for (Map.Entry<String, long[]> entryJt : statsJt.entrySet()) {
            outJt.add(new RowMa(entryJt.getKey(), entryJt.getKey(), entryJt.getValue()[1], entryJt.getValue()[0],
                    ruleOfJt(BlockRuleJtMa.SITE, entryJt.getKey())));
        }
        return outJt;
    }

    private void fillHistoryJt() {
        historyPanelJt.reloadMa();
    }

    /** Encabezado del equipo (nombre, estado, periodo y botones). Se puede poner arriba de la pantalla del grupo. */
    JPanel headerJt() {
        return headerPanelJt;
    }

    /** Panel "Detalle de …" con las pestañas; se muestra al lado de las pantallas. */
    JPanel detailJt() {
        return detailPanelJt;
    }

    boolean historyOnlyJt() {
        return historyOnlyJt;
    }

    long memberIdJt() {
        return memberJt.id();
    }

    /** Lista de las reglas que hay en ese equipo, para cambiarlas o quitarlas. */
    private void manageRulesJt() {
        List<RuleDtoJt> rulesJt = loadedJt.rules();
        if (rulesJt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Este equipo todavía no tiene reglas. Usa «Restringir aplicación» o "
                    + "«Bloquear un sitio» para poner la primera.", "Configurar bloqueos", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        javax.swing.DefaultListModel<RuleDtoJt> modelJt = new javax.swing.DefaultListModel<>();
        rulesJt.forEach(modelJt::addElement);
        javax.swing.JList<RuleDtoJt> listJt = new javax.swing.JList<>(modelJt);
        listJt.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> listMaJt, Object valueJt,
                    int indexJt, boolean selectedJt, boolean focusJt) {
                RuleDtoJt ruleJt = (RuleDtoJt) valueJt;
                return super.getListCellRendererComponent(listMaJt, (BlockRuleJtMa.APP.equals(ruleJt.kind()) ? "App · "
                        : "Sitio · ") + ruleJt.displayName() + " — " + RemoteRuleDialogMa.textMa(ruleJt), indexJt,
                        selectedJt, focusJt);
            }
        });
        listJt.setSelectedIndex(0);
        javax.swing.JScrollPane scrollJt = new javax.swing.JScrollPane(listJt);
        scrollJt.setPreferredSize(new java.awt.Dimension(460, 220));
        Object[] optionsJt = {"Cambiar", "Quitar", "Cerrar"};
        int choiceJt = JOptionPane.showOptionDialog(this, scrollJt, "Reglas de " + memberJt.displayName(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, optionsJt, optionsJt[2]);
        RuleDtoJt pickedJt = listJt.getSelectedValue();
        if (pickedJt == null) {
            return;
        }
        if (choiceJt == 0) {
            configureKeyJt(pickedJt.kind(), pickedJt.targetKey(), pickedJt.displayName());
        } else if (choiceJt == 1) {
            removeKeyJt(pickedJt.kind(), pickedJt.targetKey());
        }
    }

    String memberNameJt() {
        return memberJt.displayName();
    }

    // ------------------------------------------------------------------ reglas

    void configureKeyJt(String kindJt, String keyJt, String displayJt) {
        RuleDtoJt newJt = RemoteRuleDialogMa.showMa(this, kindJt, keyJt, displayJt, ruleOfJt(kindJt, keyJt));
        if (newJt != null) {
            saveRulesJt(replaceJt(newJt));
        }
    }

    void addSiteJt() {
        String rawJt = JOptionPane.showInputDialog(this, "Dominio del sitio (por ejemplo: youtube.com)",
                "Bloquear un sitio", JOptionPane.PLAIN_MESSAGE);
        if (rawJt == null) {
            return;
        }
        try {
            String domainJt = RuleServiceJt.normalizeSiteJtMa(rawJt);
            RuleDtoJt newJt = RemoteRuleDialogMa.showMa(this, BlockRuleJtMa.SITE, domainJt, domainJt,
                    ruleOfJt(BlockRuleJtMa.SITE, domainJt));
            if (newJt != null) {
                saveRulesJt(replaceJt(newJt));
            }
        } catch (IllegalArgumentException exJt) {
            JOptionPane.showMessageDialog(this, exJt.getMessage(), "AppControl", JOptionPane.WARNING_MESSAGE);
        }
    }

    void removeKeyJt(String kindJt, String keyJt) {
        List<RuleDtoJt> restJt = new ArrayList<>();
        for (RuleDtoJt ruleJt : loadedJt.rules()) {
            if (!(ruleJt.kind().equals(kindJt) && ruleJt.targetKey().equalsIgnoreCase(keyJt))) {
                restJt.add(ruleJt);
            }
        }
        saveRulesJt(restJt);
    }

    private List<RuleDtoJt> replaceJt(RuleDtoJt newJt) {
        List<RuleDtoJt> outJt = new ArrayList<>();
        for (RuleDtoJt ruleJt : loadedJt.rules()) {
            if (!(ruleJt.kind().equals(newJt.kind()) && ruleJt.targetKey().equalsIgnoreCase(newJt.targetKey()))) {
                outJt.add(ruleJt);
            }
        }
        outJt.add(newJt);
        return outJt;
    }

    private void saveRulesJt(List<RuleDtoJt> rulesJt) {
        if (!RuleActionsMa.unlockMa(this, accountJt)) {
            return;
        }
        long memberIdJt = memberJt.id();
        UiAsyncJt.runJt(() -> {
            cloudJt.client().setRules(groupIdJt, memberIdJt, rulesJt);
            return Boolean.TRUE;
        }, okJt -> reloadJt(), errorJt -> JOptionPane.showMessageDialog(this, errorJt, "AppControl",
                JOptionPane.WARNING_MESSAGE));
    }

    private void endControlJt() {
        if (JOptionPane.showConfirmDialog(this, "¿Terminar el control de «" + memberJt.displayName()
                + "»? Sus reglas se quitarán de ese equipo.", "Terminar control", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION || !RuleActionsMa.unlockMa(this, accountJt)) {
            return;
        }
        long memberIdJt = memberJt.id();
        UiAsyncJt.runJt(() -> {
            cloudJt.client().endControl(memberIdJt);
            return Boolean.TRUE;
        }, okJt -> onBackJt.run(), errorJt -> JOptionPane.showMessageDialog(this, errorJt, "AppControl",
                JOptionPane.WARNING_MESSAGE));
    }
}
