package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.AppUsageJtMa;
import com.appcontrol.service.InstalledAppsServiceJtMa;
import com.appcontrol.service.InstalledAppsServiceJtMa.InstalledAppJt;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.RuleLogicJtMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.service.UsageServiceJt;
import com.formdev.flatlaf.FlatClientProperties;

/** Aplicaciones del equipo, al estilo de Configuracion de Windows: buscar, filtrar, ordenar y elegir la vista. */
class AppsCatalogPageJtMa extends PageMa {

    enum ViewJt { LIST, GRID4, GRID9 }

    private static final String[] FILTERS_MA = {"Todas", "Con regla", "Bloqueo total", "Por horario",
        "Con cuota de tiempo", "Cuota agotada", "Sin regla"};
    private static final String[] SORTS_MA = {"Nombre (de la A a la Z)", "Nombre (de la Z a la A)",
        "Fecha de instalación", "Tamaño (de grande a pequeño)", "Tamaño (de pequeño a grande)", "Uso de hoy"};
    private static final DateTimeFormatter DATE_MA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final long RESCAN_MS_MA = 5 * 60 * 1000L;

    private record EntryJt(String name, String exe, String path, String details, Long sizeKb, LocalDate installed) {
    }

    private final InstalledAppsServiceJtMa installedMa;
    private final RuleServiceJt rulesSvcMa;
    private final AccountServiceMa accountMa;
    private final UsageServiceJt usageMa;

    /** Reglas que pusieron otras personas (grupos): se muestran con candado y no se pueden quitar desde aquí. */
    private java.util.function.Supplier<List<com.appcontrol.cloud.GroupRuleJtMa>> groupRulesMa = List::of;
    private final JTextField searchMa = new JTextField();
    private final JComboBox<String> filterMa = new JComboBox<>(FILTERS_MA);
    private final JComboBox<String> sortMa = new JComboBox<>(SORTS_MA);
    private final JLabel countMa = new JLabel(" ");
    private final JPanel itemsMa = new JPanel();
    private ViewJt viewMa = ViewJt.LIST;
    private String lastSigMa = "";
    private int pageMa;
    private final PagerJt pagerMa = new PagerJt(newPageMa -> {
        pageMa = newPageMa;
        rebuild(true);
    });
    private final JButton refreshBtnMa = new JButton();
    private boolean scanningMa;
    private long lastScanMa;

    AppsCatalogPageJtMa(InstalledAppsServiceJtMa installedMa, RuleServiceJt rulesSvcMa, AccountServiceMa accountMa,
            UsageServiceJt usageMa) {
        super("Aplicaciones");
        this.installedMa = installedMa;
        this.rulesSvcMa = rulesSvcMa;
        this.accountMa = accountMa;
        this.usageMa = usageMa;

        searchMa.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Buscar aplicaciones");
        searchMa.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_ICON, IconsJtMa.of("search", 16));
        searchMa.putClientProperty(FlatClientProperties.STYLE, "margin: 6,10,6,10");
        searchMa.setPreferredSize(new Dimension(340, 36));
        searchMa.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent eMa) {
                resetAndRebuild();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent eMa) {
                resetAndRebuild();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent eMa) {
                resetAndRebuild();
            }
        });
        filterMa.addActionListener(evMa -> resetAndRebuild());
        sortMa.addActionListener(evMa -> resetAndRebuild());

        JPanel searchRowMa = new JPanel(new BorderLayout());
        searchRowMa.setOpaque(false);
        JPanel searchWrapMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        searchWrapMa.setOpaque(false);
        searchWrapMa.add(searchMa);
        searchRowMa.add(searchWrapMa, BorderLayout.WEST);
        JPanel rightTopMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightTopMa.setOpaque(false);
        refreshBtnMa.setIcon(IconsJtMa.of("refresh", 18));
        refreshBtnMa.setToolTipText("Volver a leer los programas instalados (puede tardar unos segundos)");
        refreshBtnMa.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        refreshBtnMa.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 7,10,7,10");
        refreshBtnMa.setFocusable(false);
        refreshBtnMa.addActionListener(evMa -> scan());
        rightTopMa.add(refreshBtnMa);
        rightTopMa.add(viewButtons());
        searchRowMa.add(rightTopMa, BorderLayout.EAST);

        countMa.putClientProperty(FlatClientProperties.STYLE, "font: +1");
        JPanel optionsMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        optionsMa.setOpaque(false);
        optionsMa.add(UiKitJtMa.subtitle("Filtrar por:"));
        optionsMa.add(filterMa);
        optionsMa.add(javax.swing.Box.createHorizontalStrut(10));
        optionsMa.add(UiKitJtMa.subtitle("Ordenar por:"));
        optionsMa.add(sortMa);
        JPanel countRowMa = new JPanel(new BorderLayout());
        countRowMa.setOpaque(false);
        countRowMa.add(countMa, BorderLayout.WEST);
        countRowMa.add(optionsMa, BorderLayout.EAST);

        JPanel topMa = new JPanel(new BorderLayout(0, 16));
        topMa.setOpaque(false);
        topMa.add(UiKitJtMa.title("Aplicaciones"), BorderLayout.NORTH);
        topMa.add(searchRowMa, BorderLayout.CENTER);
        topMa.add(countRowMa, BorderLayout.SOUTH);

        itemsMa.setOpaque(false);
        JScrollPane scrollMa = UiKitJtMa.vscroll(itemsMa);

        JPanel centerMa = new JPanel(new BorderLayout(0, 12));
        centerMa.setOpaque(false);
        centerMa.add(scrollMa, BorderLayout.CENTER);
        JPanel bottomMa = new JPanel(new BorderLayout(0, 8));
        bottomMa.setOpaque(false);
        bottomMa.add(pagerMa, BorderLayout.NORTH);
        centerMa.add(bottomMa, BorderLayout.SOUTH);

        add(topMa, BorderLayout.NORTH);
        add(centerMa, BorderLayout.CENTER);

        if (installedMa.cached().isEmpty()) {
            scan();
        }
        rebuild(true);
    }

    private JPanel viewButtons() {
        JPanel panelMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        panelMa.setOpaque(false);
        ButtonGroup groupMa = new ButtonGroup();
        String[][] defsMa = {{"view-list", "Lista"}, {"view-grid4", "Iconos medianos"}, {"view-grid9", "Iconos pequeños"}};
        ViewJt[] viewsMa = ViewJt.values();
        for (int iMa = 0; iMa < defsMa.length; iMa++) {
            ViewJt vMa = viewsMa[iMa];
            JToggleButton btnMa = new JToggleButton(IconsJtMa.of(defsMa[iMa][0], 18));
            btnMa.setToolTipText(defsMa[iMa][1]);
            btnMa.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            btnMa.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 7,10,7,10");
            btnMa.setFocusable(false);
            btnMa.setSelected(vMa == viewMa);
            btnMa.addActionListener(evMa -> setViewMa(vMa));
            groupMa.add(btnMa);
            panelMa.add(btnMa);
        }
        return panelMa;
    }

    /** Cambia la vista (lista / iconos medianos / iconos pequenos). */
    void setViewMa(ViewJt newMa) {
        viewMa = newMa;
        pageMa = 0;
        rebuild(true);
    }

    private void scan() {
        if (scanningMa) {
            return;
        }
        scanningMa = true;
        refreshBtnMa.setEnabled(false);
        countMa.setText("Buscando aplicaciones…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                installedMa.scanMa();
                return null;
            }

            @Override
            protected void done() {
                scanningMa = false;
                refreshBtnMa.setEnabled(true);
                lastScanMa = System.currentTimeMillis();
                rebuild(true);
            }
        }.execute();
    }

    @Override
    void refreshMa() {
        if (!scanningMa && System.currentTimeMillis() - lastScanMa > RESCAN_MS_MA && lastScanMa != 0) {
            scan();
        }
        rebuild(false);
    }

    private static String detailsOf(InstalledAppJt appMa) {
        List<String> partsMa = new ArrayList<>();
        for (String vMa : new String[] {appMa.version(), appMa.publisher(), appMa.installDate()}) {
            if (vMa != null && !vMa.isBlank()) {
                partsMa.add(vMa);
            }
        }
        return partsMa.isEmpty() ? null : String.join("  |  ", partsMa);
    }

    private static LocalDate parseDate(String textMa) {
        try {
            return textMa == null ? null : LocalDate.parse(textMa, DATE_MA);
        } catch (Exception exMa) {
            return null;
        }
    }

    /** Reconstruye la lista. Si no force y nada cambio, no toca la pantalla (conserva el scroll). */
    void setGroupRulesJt(java.util.function.Supplier<List<com.appcontrol.cloud.GroupRuleJtMa>> groupRulesMa) {
        this.groupRulesMa = groupRulesMa;
    }

    private void rebuild(boolean forceMa) {
        Map<String, EntryJt> entriesMa = new LinkedHashMap<>();
        for (InstalledAppJt appMa : installedMa.cached()) {
            entriesMa.put(appMa.exe().toLowerCase(Locale.ROOT), new EntryJt(appMa.name(), appMa.exe(), appMa.path(),
                    detailsOf(appMa), appMa.sizeKb(), parseDate(appMa.installDate())));
        }
        Map<String, BlockRuleJtMa> rulesMa = new LinkedHashMap<>();
        for (BlockRuleJtMa ruleMa : rulesSvcMa.listJtMa(BlockRuleJtMa.APP)) {
            rulesMa.put(ruleMa.getTargetKey(), ruleMa);
            entriesMa.putIfAbsent(ruleMa.getTargetKey(),
                    new EntryJt(ruleMa.getDisplayName(), ruleMa.getTargetKey(), null, null, null, null));
        }
        Map<String, com.appcontrol.cloud.GroupRuleJtMa> groupMa = new LinkedHashMap<>();
        for (com.appcontrol.cloud.GroupRuleJtMa groupRuleMa : groupRulesMa.get()) {
            if (BlockRuleJtMa.APP.equals(groupRuleMa.getKind())) {
                String groupKeyMa = groupRuleMa.getTargetKey().toLowerCase(Locale.ROOT);
                groupMa.put(groupKeyMa, groupRuleMa);
                entriesMa.putIfAbsent(groupKeyMa,
                        new EntryJt(groupRuleMa.getDisplayName(), groupKeyMa, null, null, null, null));
            }
        }
        Map<String, Long> usedMa = usageMa.todayByAppJtMa();
        for (AppUsageJtMa useMa : usageMa.topToday()) {
            entriesMa.putIfAbsent(useMa.getAppName().toLowerCase(Locale.ROOT),
                    new EntryJt(useMa.getAppName(), useMa.getAppName(), null, null, null, null));
        }

        String queryMa = searchMa.getText().trim().toLowerCase(Locale.ROOT);
        String filterNameMa = (String) filterMa.getSelectedItem();
        String sortNameMa = (String) sortMa.getSelectedItem();
        List<EntryJt> shownMa = new ArrayList<>();
        for (EntryJt entryMa : entriesMa.values()) {
            String keyMa = entryMa.exe().toLowerCase(Locale.ROOT);
            BlockRuleJtMa ruleMa = rulesMa.get(keyMa);
            if (!queryMa.isEmpty() && !entryMa.name().toLowerCase(Locale.ROOT).contains(queryMa)
                    && !keyMa.contains(queryMa)) {
                continue;
            }
            boolean okMa = switch (filterNameMa == null ? "Todas" : filterNameMa) {
                case "Con regla" -> ruleMa != null || groupMa.containsKey(keyMa);
                case "Bloqueo total" -> ruleMa != null && ruleMa.getMode() == RuleModeMa.ALWAYS;
                case "Por horario" -> ruleMa != null && ruleMa.getMode() == RuleModeMa.SCHEDULE;
                case "Con cuota de tiempo" -> ruleMa != null && ruleMa.getMode() == RuleModeMa.QUOTA;
                case "Cuota agotada" -> ruleMa != null && ruleMa.getMode() == RuleModeMa.QUOTA
                        && RuleLogicJtMa.evaluateMa(ruleMa, java.time.LocalDateTime.now(), java.time.Instant.now(),
                                usedMa.getOrDefault(keyMa, 0L)).blockedNow();
                case "Sin regla" -> ruleMa == null && !groupMa.containsKey(keyMa);
                default -> true;
            };
            if (okMa) {
                shownMa.add(entryMa);
            }
        }
        shownMa.sort(comparator(sortNameMa, usedMa));
        pageMa = PagerJt.clampJt(pageMa, shownMa.size());
        List<EntryJt> pageItemsMa = PagerJt.sliceJt(shownMa, pageMa);

        StringBuilder sigMa = new StringBuilder().append(viewMa).append('|').append(queryMa).append('|')
                .append(filterNameMa).append('|').append(sortNameMa).append('|').append(scanningMa)
                .append('|').append(pageMa).append('/').append(shownMa.size());
        for (EntryJt entryMa : pageItemsMa) {
            String keyMa = entryMa.exe().toLowerCase(Locale.ROOT);
            BlockRuleJtMa ruleMa = rulesMa.get(keyMa);
            sigMa.append(';').append(keyMa).append(groupMa.containsKey(keyMa) ? "G" : "").append(':')
                    .append(ruleSig(ruleMa))
                    .append(':').append(usedMa.getOrDefault(keyMa, 0L) / 60);
        }
        if (!forceMa && sigMa.toString().equals(lastSigMa)) {
            return;
        }
        lastSigMa = sigMa.toString();

        itemsMa.removeAll();
        if (viewMa == ViewJt.LIST) {
            itemsMa.setLayout(new BoxLayout(itemsMa, BoxLayout.Y_AXIS));
        } else {
            itemsMa.setLayout(new WrapLayoutMa(FlowLayout.LEFT, 12, 12));
        }
        for (EntryJt entryMa : pageItemsMa) {
            String keyMa = entryMa.exe().toLowerCase(Locale.ROOT);
            itemsMa.add(item(entryMa, rulesMa.get(keyMa), usedMa.getOrDefault(keyMa, 0L), groupMa.get(keyMa)));
            if (viewMa == ViewJt.LIST) {
                itemsMa.add(javax.swing.Box.createVerticalStrut(8));
            }
        }
        if (shownMa.isEmpty() && !scanningMa) {
            itemsMa.setLayout(new BorderLayout());
            itemsMa.add(UiKitJtMa.note(installedMa.cached().isEmpty()
                    ? "No se encontraron aplicaciones."
                    : "Ninguna aplicación coincide con la búsqueda o el filtro."), BorderLayout.NORTH);
        }
        if (!scanningMa) {
            int totalMa = entriesMa.size();
            countMa.setText(shownMa.size() == totalMa
                    ? "Se encontraron " + totalMa + (totalMa == 1 ? " aplicación" : " aplicaciones")
                    : "Se encontraron " + shownMa.size() + " de " + totalMa + " aplicaciones");
        }
        pagerMa.showJt(pageMa, shownMa.size());
        itemsMa.revalidate();
        itemsMa.repaint();
    }

    /** Cambia la busqueda, el filtro o el orden: se vuelve a la primera pagina. */
    private void resetAndRebuild() {
        pageMa = 0;
        rebuild(true);
    }

    private static Comparator<EntryJt> comparator(String sortNameMa, Map<String, Long> usedMa) {
        Comparator<EntryJt> byNameMa = Comparator.comparing(eMa -> eMa.name().toLowerCase(Locale.ROOT));
        if (sortNameMa == null) {
            return byNameMa;
        }
        return switch (sortNameMa) {
            case "Nombre (de la Z a la A)" -> byNameMa.reversed();
            case "Fecha de instalación" -> Comparator.comparing((EntryJt eMa) -> eMa.installed(),
                    Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())).thenComparing(byNameMa);
            case "Tamaño (de grande a pequeño)" -> Comparator.comparing((EntryJt eMa) -> eMa.sizeKb(),
                    Comparator.nullsLast(Comparator.<Long>reverseOrder())).thenComparing(byNameMa);
            case "Tamaño (de pequeño a grande)" -> Comparator.comparing((EntryJt eMa) -> eMa.sizeKb(),
                    Comparator.nullsLast(Comparator.<Long>naturalOrder())).thenComparing(byNameMa);
            case "Uso de hoy" -> Comparator.comparing((EntryJt eMa) ->
                    -usedMa.getOrDefault(eMa.exe().toLowerCase(Locale.ROOT), 0L)).thenComparing(byNameMa);
            default -> byNameMa;
        };
    }

    // ------------------------------------------------------------------ elementos

    private JPanel item(EntryJt entryMa, BlockRuleJtMa ruleMa, long usedSecMa,
            com.appcontrol.cloud.GroupRuleJtMa groupRuleMa) {
        int iconSizeMa = viewMa == ViewJt.GRID9 ? 24 : 32;
        JLabel iconMa = new JLabel(AppIconsMa.placeholder(entryMa.name(), iconSizeMa));
        iconMa.setPreferredSize(new Dimension(iconSizeMa, iconSizeMa));
        AppIconsMa.load(entryMa.path(), iconSizeMa, realMa -> iconMa.setIcon(realMa));

        JPanel cardMa = UiKitJtMa.roundedPanel(viewMa == ViewJt.GRID9 ? 8 : 12);
        cardMa.setLayout(new BorderLayout(12, 0));
        JPanel iconWrapMa = new JPanel(new java.awt.GridBagLayout());
        iconWrapMa.setOpaque(false);
        iconWrapMa.add(iconMa);

        JButton moreMa = new JButton("···");
        moreMa.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        moreMa.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 4,10,4,10; font: bold +2");
        moreMa.setFocusable(false);
        moreMa.addActionListener(evMa -> (groupRuleMa != null ? GroupLockJtMa.infoMenuJtMa(groupRuleMa)
                : RuleActionsMa.menuMa(this, rulesSvcMa, accountMa, BlockRuleJtMa.APP,
                entryMa.exe().toLowerCase(Locale.ROOT), entryMa.name(),
                rulesSvcMa.findJtMa(BlockRuleJtMa.APP, entryMa.exe().toLowerCase(Locale.ROOT)).orElse(null),
                () -> rebuild(true))).show(moreMa, 0, moreMa.getHeight()));

        ChipMa chipMa = null;
        RuleActionsMa.ChipSpecJt specMa = null;
        RuleLogicJtMa.StatusMa statusMa = null;
        if (ruleMa != null) {
            statusMa = RuleLogicJtMa.evaluateMa(ruleMa, java.time.LocalDateTime.now(), java.time.Instant.now(),
                    usedSecMa);
            specMa = RuleActionsMa.chipMa(ruleMa, statusMa);
            chipMa = new ChipMa(specMa.text(), specMa.kind());
        }

        if (groupRuleMa != null && ruleMa == null) {
            chipMa = new ChipMa(GroupLockJtMa.chipTextJtMa(groupRuleMa), ChipMa.KindJtMa.DANGER);
        }

        JPanel nameMa = new JPanel(new GridLayout(viewMa == ViewJt.GRID9 ? 1 : 2, 1));
        nameMa.setOpaque(false);
        JLabel titleMa = new JLabel(entryMa.name());
        nameMa.add(titleMa);
        if (viewMa != ViewJt.GRID9) {
            String subMa = viewMa == ViewJt.LIST ? entryMa.details() : shortDetails(entryMa);
            if (subMa == null) {
                subMa = entryMa.exe() + (usedSecMa > 0 ? "  |  hoy " + FmtJt.durationJtMa(usedSecMa) : "");
            } else if (usedSecMa > 0 && viewMa == ViewJt.LIST) {
                subMa = subMa + "  |  hoy " + FmtJt.durationJtMa(usedSecMa);
            }
            if (statusMa != null && viewMa == ViewJt.LIST) {
                subMa = subMa + "  |  " + statusMa.label();
            }
            nameMa.add(UiKitJtMa.subtitle(subMa));
        }

        JPanel eastMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        eastMa.setOpaque(false);
        if (statusMa != null && statusMa.enforcing() && ruleMa.getMode() == RuleModeMa.QUOTA
                && ruleMa.getQuotaMinutes() > 0 && viewMa == ViewJt.LIST) {
            eastMa.add(BrowsePageJt.progressJt(usedSecMa / (ruleMa.getQuotaMinutes() * 60.0)));
        }
        if (chipMa != null) {
            eastMa.add(chipMa);
        }
        if (viewMa == ViewJt.LIST && entryMa.sizeKb() != null) {
            JLabel sizeMa = new JLabel(FmtJt.sizeJtMa(entryMa.sizeKb()), JLabel.RIGHT);
            sizeMa.setPreferredSize(new Dimension(70, 20));
            eastMa.add(sizeMa);
        }
        eastMa.add(moreMa);

        cardMa.add(iconWrapMa, BorderLayout.WEST);
        cardMa.add(nameMa, BorderLayout.CENTER);
        if (viewMa == ViewJt.GRID9) {
            // vista compacta: el estado va como punto de color en el titulo y el menu al final
            if (specMa != null) {
                titleMa.setToolTipText(specMa.text());
                titleMa.setText(entryMa.name() + "  ●");
                titleMa.putClientProperty(FlatClientProperties.STYLE, specMa.kind() == ChipMa.KindJtMa.DANGER
                        ? "foreground: #D13438" : specMa.kind() == ChipMa.KindJtMa.WARN
                        ? "foreground: #C58A00" : "foreground: $Label.disabledForeground");
            }
            cardMa.add(moreMa, BorderLayout.EAST);
        } else if (viewMa == ViewJt.GRID4) {
            JPanel southMa = new JPanel(new BorderLayout());
            southMa.setOpaque(false);
            JPanel leftChipMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            leftChipMa.setOpaque(false);
            if (chipMa != null) {
                leftChipMa.add(chipMa);
            }
            southMa.add(leftChipMa, BorderLayout.CENTER);
            southMa.add(moreMa, BorderLayout.EAST);
            cardMa.add(southMa, BorderLayout.SOUTH);
        } else {
            cardMa.add(eastMa, BorderLayout.EAST);
        }

        switch (viewMa) {
            case LIST -> cardMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
            case GRID4 -> cardMa.setPreferredSize(new Dimension(270, 112));
            case GRID9 -> cardMa.setPreferredSize(new Dimension(210, 52));
        }
        return cardMa;
    }

    private static String shortDetails(EntryJt entryMa) {
        String dMa = entryMa.details();
        if (dMa == null) {
            return null;
        }
        String[] partsMa = dMa.split("\\s+\\|\\s+");
        return partsMa.length >= 2 ? partsMa[0] + "  |  " + partsMa[1] : partsMa[0];
    }

    /** Resumen corto de una regla para detectar cambios y no redibujar de mas. */
    private static String ruleSig(BlockRuleJtMa ruleMa) {
        if (ruleMa == null) {
            return "-";
        }
        return ruleMa.getMode() + "/" + ruleMa.getState() + "/" + ruleMa.getDaysMask() + "/" + ruleMa.getStartMinute()
                + "/" + ruleMa.getEndMinute() + "/" + ruleMa.getQuotaMinutes() + "/" + ruleMa.getPausedUntil();
    }
}
