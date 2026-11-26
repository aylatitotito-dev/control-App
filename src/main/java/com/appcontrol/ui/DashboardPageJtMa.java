package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.AppUsageJtMa;
import com.appcontrol.model.UserGroupJt;
import com.appcontrol.service.GroupServiceMa;
import com.appcontrol.service.RuleLogicJtMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.service.UsageServiceJt;
import com.formdev.flatlaf.FlatClientProperties;

/** Panel principal: resumen del dia, reglas con su estado, y acceso a bloqueos y grupos. */
class DashboardPageJtMa extends PageMa {

    private static final double WARN_RATIO_MA = 0.85;
    private static final int MAX_ROWS_MA = 8;

    private final UsageServiceJt usageMa;
    private final RuleServiceJt ruleSvcMa;
    private final GroupServiceMa groupsMa;
    private final Consumer<String> navMa;

    private final JLabel totalMa = UiKitJtMa.bigValue();
    private final JLabel totalNoteMa = UiKitJtMa.subtitle(" ");
    private final JLabel topNameMa = UiKitJtMa.bigValue();
    private final JLabel topNoteMa = UiKitJtMa.subtitle(" ");
    private final JLabel rulesMa = UiKitJtMa.bigValue();
    private final JPanel chipsMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JPanel rowsMa = new JPanel();
    private final JPanel groupRowsMa = new JPanel();

    DashboardPageJtMa(UsageServiceJt usageMa, RuleServiceJt ruleSvcMa, GroupServiceMa groupsMa,
            Consumer<String> navMa) {
        super("Panel Principal");
        this.usageMa = usageMa;
        this.ruleSvcMa = ruleSvcMa;
        this.groupsMa = groupsMa;
        this.navMa = navMa;
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        chipsMa.setOpaque(false);
        rowsMa.setOpaque(false);
        rowsMa.setLayout(new BoxLayout(rowsMa, BoxLayout.Y_AXIS));
        groupRowsMa.setOpaque(false);
        groupRowsMa.setLayout(new BoxLayout(groupRowsMa, BoxLayout.Y_AXIS));

        JPanel bodyMa = new JPanel(new BorderLayout(0, 16));
        bodyMa.setOpaque(false);
        bodyMa.setBorder(BorderFactory.createEmptyBorder(20, 24, 24, 24));
        bodyMa.add(buildCards(), BorderLayout.NORTH);
        bodyMa.add(buildMain(), BorderLayout.CENTER);

        add(UiKitJtMa.vscroll(bodyMa), BorderLayout.CENTER);
        refreshMa();
    }

    private JPanel buildCards() {
        JPanel cardsMa = new JPanel(new GridLayout(1, 2, 16, 0));
        cardsMa.setOpaque(false);

        JPanel timeMa = UiKitJtMa.roundedPanel(16);
        timeMa.setLayout(new BoxLayout(timeMa, BoxLayout.Y_AXIS));
        timeMa.add(UiKitJtMa.subtitle("Tiempo activo hoy"));
        timeMa.add(javax.swing.Box.createVerticalStrut(8));
        timeMa.add(totalMa);
        timeMa.add(javax.swing.Box.createVerticalStrut(8));
        timeMa.add(totalNoteMa);

        JPanel topMa = UiKitJtMa.roundedPanel(16);
        topMa.setLayout(new BoxLayout(topMa, BoxLayout.Y_AXIS));
        topMa.add(UiKitJtMa.subtitle("Más usado hoy"));
        topMa.add(javax.swing.Box.createVerticalStrut(8));
        topMa.add(topNameMa);
        topMa.add(javax.swing.Box.createVerticalStrut(8));
        topMa.add(topNoteMa);

        for (JPanel cMa : List.of(timeMa, topMa)) {
            for (java.awt.Component childMa : cMa.getComponents()) {
                ((JComponent) childMa).setAlignmentX(LEFT_ALIGNMENT);
            }
            cardsMa.add(cMa);
        }
        return cardsMa;
    }

    private JPanel buildMain() {
        // Izquierda: uso y limites de hoy
        JPanel leftMa = UiKitJtMa.roundedPanel(20);
        leftMa.setLayout(new BorderLayout(0, 12));
        JPanel leftHeadMa = new JPanel(new BorderLayout(12, 0));
        leftHeadMa.setOpaque(false);
        leftHeadMa.add(UiKitJtMa.sectionTitle("Uso y límites de hoy"), BorderLayout.WEST);
        JButton newRuleMa = new JButton("+  Nueva regla");
        newRuleMa.addActionListener(evMa -> navMa.accept("Aplicaciones"));
        leftHeadMa.add(newRuleMa, BorderLayout.EAST);
        JButton otherDaysMa = new JButton("Ver otros días  →");
        otherDaysMa.setToolTipText("Ayer, la semana pasada… en Historial > Resumen");
        otherDaysMa.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        otherDaysMa.addActionListener(evMa -> navMa.accept("Historial:resumen"));
        JPanel footLinkMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footLinkMa.setOpaque(false);
        footLinkMa.add(otherDaysMa);
        JPanel rowsHolderMa = new JPanel(new BorderLayout());
        rowsHolderMa.setOpaque(false);
        rowsHolderMa.add(rowsMa, BorderLayout.NORTH);
        leftMa.add(leftHeadMa, BorderLayout.NORTH);
        leftMa.add(rowsHolderMa, BorderLayout.CENTER);
        leftMa.add(footLinkMa, BorderLayout.SOUTH);

        // Derecha: configuracion y grupos
        JPanel rightMa = UiKitJtMa.roundedPanel(20);
        rightMa.setLayout(new BoxLayout(rightMa, BoxLayout.Y_AXIS));
        JLabel rtitleMa = UiKitJtMa.sectionTitle("Configuración y grupos");
        rtitleMa.setAlignmentX(LEFT_ALIGNMENT);
        rightMa.add(rtitleMa);
        rightMa.add(javax.swing.Box.createVerticalStrut(12));

        JPanel manageMa = UiKitJtMa.roundedPanel(14);
        manageMa.setLayout(new BoxLayout(manageMa, BoxLayout.Y_AXIS));
        manageMa.setAlignmentX(LEFT_ALIGNMENT);
        JLabel mtMa = new JLabel("Gestión de bloqueos y reglas");
        mtMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        mtMa.setAlignmentX(LEFT_ALIGNMENT);
        javax.swing.JTextArea mdMa = UiKitJtMa.note("Define sitios y aplicaciones bloqueadas y cuotas diarias por aplicación.");
        mdMa.setAlignmentX(LEFT_ALIGNMENT);
        JPanel mbMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        mbMa.setOpaque(false);
        mbMa.setAlignmentX(LEFT_ALIGNMENT);
        JButton appsBtnMa = new JButton("Aplicaciones");
        appsBtnMa.addActionListener(evMa -> navMa.accept("Aplicaciones"));
        JButton sitesBtnMa = new JButton("Sitios y URLs");
        sitesBtnMa.addActionListener(evMa -> navMa.accept("Sitios y URLs"));
        mbMa.add(appsBtnMa);
        mbMa.add(javax.swing.Box.createHorizontalStrut(10));
        mbMa.add(sitesBtnMa);
        manageMa.add(mtMa);
        manageMa.add(javax.swing.Box.createVerticalStrut(4));
        manageMa.add(mdMa);
        manageMa.add(javax.swing.Box.createVerticalStrut(10));
        manageMa.add(mbMa);
        manageMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, manageMa.getPreferredSize().height + 20));
        rightMa.add(manageMa);
        rightMa.add(javax.swing.Box.createVerticalStrut(18));

        JPanel groupsHeadMa = new JPanel(new BorderLayout(0, 8));
        groupsHeadMa.setOpaque(false);
        groupsHeadMa.setAlignmentX(LEFT_ALIGNMENT);
        groupsHeadMa.add(UiKitJtMa.sectionTitle("Grupos"), BorderLayout.NORTH);
        JPanel gbMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gbMa.setOpaque(false);
        JButton createMa = new JButton("+  Crear grupo");
        createMa.addActionListener(evMa -> createGroup());
        JButton joinMa = new JButton("Unirse con código");
        joinMa.setEnabled(false);
        joinMa.setToolTipText("Requiere conexión a Internet (siguiente etapa)");
        gbMa.add(createMa);
        gbMa.add(javax.swing.Box.createHorizontalStrut(8));
        gbMa.add(joinMa);
        groupsHeadMa.add(gbMa, BorderLayout.CENTER);
        groupsHeadMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        rightMa.add(groupsHeadMa);
        rightMa.add(javax.swing.Box.createVerticalStrut(10));
        groupRowsMa.setAlignmentX(LEFT_ALIGNMENT);
        rightMa.add(groupRowsMa);

        leftMa.setMinimumSize(new Dimension(380, 0));
        rightMa.setMinimumSize(new Dimension(300, 0));
        JPanel mainMa = new JPanel(new GridBagLayout());
        mainMa.setOpaque(false);
        GridBagConstraints gcMa = new GridBagConstraints();
        gcMa.fill = GridBagConstraints.BOTH;
        gcMa.weighty = 1;
        gcMa.anchor = GridBagConstraints.NORTH;
        gcMa.weightx = 0.58;
        gcMa.insets = new Insets(0, 0, 0, 8);
        mainMa.add(leftMa, gcMa);
        gcMa.weightx = 0.42;
        gcMa.insets = new Insets(0, 8, 0, 0);
        mainMa.add(rightMa, gcMa);
        return mainMa;
    }

    private void createGroup() {
        String nameMa = JOptionPane.showInputDialog(this, "Nombre del grupo (por ejemplo: Mi familia)",
                "Crear grupo", JOptionPane.PLAIN_MESSAGE);
        if (nameMa == null) {
            return;
        }
        try {
            groupsMa.createJt(nameMa);
            refreshMa();
        } catch (IllegalArgumentException exMa) {
            JOptionPane.showMessageDialog(this, exMa.getMessage(), "AppControl", JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    void refreshMa() {
        List<AppUsageJtMa> topMa = usageMa.topToday();
        Map<String, Long> todayMa = usageMa.todayByAppJtMa();
        List<BlockRuleJtMa> appRulesMa = ruleSvcMa.listJtMa(BlockRuleJtMa.APP);
        List<BlockRuleJtMa> siteListMa = ruleSvcMa.listJtMa(BlockRuleJtMa.SITE);
        java.time.LocalDateTime nowMa = java.time.LocalDateTime.now();
        java.time.Instant nowInstantMa = java.time.Instant.now();

        totalMa.setText(FmtJt.durationJtMa(usageMa.totalTodaySeconds()));
        totalNoteMa.setText("Medido en este equipo");
        topNameMa.setText(topMa.isEmpty() ? "—" : topMa.get(0).getAppName());
        topNoteMa.setText(topMa.isEmpty() ? "Aún sin datos"
                : FmtJt.durationJtMa(topMa.get(0).getSeconds())
                        + (topMa.size() > 1 ? "  ·  " + topMa.get(1).getAppName() + " "
                                + FmtJt.durationJtMa(topMa.get(1).getSeconds()) : ""));

        // Estado de las reglas
        int warnMa = 0;
        int overMa = 0;
        int okMa = 0;
        int blockedAppsMa = 0;
        List<JPanel> ruleRowsMa = new ArrayList<>();
        List<JPanel> blockedRowsMa = new ArrayList<>();
        Set<String> coveredMa = new HashSet<>();
        for (BlockRuleJtMa ruleMa : appRulesMa) {
            String keyMa = ruleMa.getTargetKey();
            coveredMa.add(keyMa);
            long usedMa = todayMa.getOrDefault(keyMa, 0L);
            RuleLogicJtMa.StatusMa statusMa = RuleLogicJtMa.evaluateMa(ruleMa, nowMa, nowInstantMa, usedMa);
            RuleActionsMa.ChipSpecJt specMa = RuleActionsMa.chipMa(ruleMa, statusMa);
            if (statusMa.enforcing() && ruleMa.getMode() == RuleModeMa.QUOTA) {
                long limitSecMa = ruleMa.getQuotaMinutes() * 60L;
                double ratioMa = (double) usedMa / limitSecMa;
                if (ratioMa >= 1) {
                    overMa++;
                } else if (ratioMa >= WARN_RATIO_MA) {
                    warnMa++;
                } else {
                    okMa++;
                }
                ruleRowsMa.add(limitRow(ruleMa.getDisplayName(), usedMa, limitSecMa, ratioMa));
            } else {
                if (statusMa.blockedNow()) {
                    blockedAppsMa++;
                } else {
                    okMa++;
                }
                blockedRowsMa.add(textRow(ruleMa.getDisplayName(), statusMa.label(),
                        new ChipMa(specMa.text(), specMa.kind())));
            }
        }
        List<JPanel> siteRowsMa = new ArrayList<>();
        for (BlockRuleJtMa siteMa : siteListMa) {
            RuleLogicJtMa.StatusMa siteStatusMa = RuleLogicJtMa.evaluateMa(siteMa, nowMa, nowInstantMa, 0);
            RuleActionsMa.ChipSpecJt siteSpecMa = RuleActionsMa.chipMa(siteMa, siteStatusMa);
            siteRowsMa.add(textRow(siteMa.getDisplayName(), "Sitio web · " + siteStatusMa.label(),
                    new ChipMa(siteSpecMa.text(), siteSpecMa.kind())));
        }
        List<JPanel> allowedRowsMa = new ArrayList<>();
        for (AppUsageJtMa usageRowMa : topMa) {
            if (!coveredMa.contains(usageRowMa.getAppName().toLowerCase(Locale.ROOT))) {
                allowedRowsMa.add(textRow(usageRowMa.getAppName(), FmtJt.durationJtMa(usageRowMa.getSeconds()) + " hoy",
                        new ChipMa("Permitido", ChipMa.KindJtMa.OK)));
            }
        }

        int totalRulesMa = appRulesMa.size() + siteListMa.size();
        rulesMa.setText(totalRulesMa + (totalRulesMa == 1 ? " regla activa" : " reglas activas"));
        chipsMa.removeAll();
        if (overMa > 0) {
            chipsMa.add(new ChipMa(overMa + " superado", ChipMa.KindJtMa.DANGER));
            chipsMa.add(javax.swing.Box.createHorizontalStrut(8));
        }
        if (warnMa > 0) {
            chipsMa.add(new ChipMa(warnMa + " al 85% o más", ChipMa.KindJtMa.WARN));
            chipsMa.add(javax.swing.Box.createHorizontalStrut(8));
        }
        chipsMa.add(new ChipMa((okMa + blockedAppsMa + siteListMa.size()) + " en orden", ChipMa.KindJtMa.NEUTRAL));
        chipsMa.revalidate();

        rowsMa.removeAll();
        List<JPanel> orderedMa = new ArrayList<>();
        orderedMa.addAll(ruleRowsMa);
        orderedMa.addAll(blockedRowsMa);
        orderedMa.addAll(siteRowsMa);
        orderedMa.addAll(allowedRowsMa);
        if (orderedMa.isEmpty()) {
            rowsMa.add(UiKitJtMa.note("Aún no hay datos ni reglas. AppControl registra el nombre de la aplicación "
                    + "activa mientras usas el equipo, sin leer lo que escribes."));
        }
        for (JPanel rowMa : orderedMa.subList(0, Math.min(MAX_ROWS_MA, orderedMa.size()))) {
            rowsMa.add(rowMa);
        }

        groupRowsMa.removeAll();
        List<UserGroupJt> groupListMa = groupsMa.list();
        if (groupListMa.isEmpty()) {
            groupRowsMa.add(UiKitJtMa.note("Aún no tienes grupos. Crea uno para obtener su código de invitación."));
        }
        for (UserGroupJt groupMa : groupListMa) {
            groupRowsMa.add(groupRow(groupMa));
            groupRowsMa.add(javax.swing.Box.createVerticalStrut(8));
        }
        rowsMa.revalidate();
        rowsMa.repaint();
        groupRowsMa.revalidate();
        groupRowsMa.repaint();
    }

    private static JPanel baseRow() {
        JPanel rowMa = new JPanel(new BorderLayout(12, 0));
        rowMa.setOpaque(false);
        rowMa.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, javax.swing.UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(10, 0, 10, 0)));
        rowMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        return rowMa;
    }

    private static JPanel nameBlock(String titleMa, String subMa) {
        JPanel blockMa = new JPanel(new GridLayout(subMa == null ? 1 : 2, 1));
        blockMa.setOpaque(false);
        blockMa.add(new JLabel(titleMa));
        if (subMa != null) {
            blockMa.add(UiKitJtMa.subtitle(subMa));
        }
        return blockMa;
    }

    private static JPanel textRow(String titleMa, String subMa, ChipMa chipMa) {
        JPanel rowMa = baseRow();
        rowMa.add(nameBlock(titleMa, subMa), BorderLayout.CENTER);
        JPanel eastMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        eastMa.setOpaque(false);
        eastMa.add(chipMa);
        rowMa.add(eastMa, BorderLayout.EAST);
        return rowMa;
    }

    private static JPanel limitRow(String nameMa, long usedSecMa, long limitSecMa, double ratioMa) {
        JPanel rowMa = baseRow();
        rowMa.add(nameBlock(nameMa, FmtJt.durationJtMa(usedSecMa) + " / " + FmtJt.durationJtMa(limitSecMa)),
                BorderLayout.CENTER);

        JProgressBar barMa = new JProgressBar(0, 100);
        barMa.setValue((int) Math.min(100, Math.round(ratioMa * 100)));
        barMa.setPreferredSize(new Dimension(90, 8));
        String colorMa = ratioMa >= 1 ? "#D13438" : ratioMa >= WARN_RATIO_MA ? "#E8A317" : null;
        if (colorMa != null) {
            barMa.putClientProperty(FlatClientProperties.STYLE, "foreground: " + colorMa);
        }
        JLabel pctMa = new JLabel(Math.round(ratioMa * 100) + "%");
        pctMa.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        long leftMinMa = Math.max(0, (limitSecMa - usedSecMa) / 60);
        JLabel leftMa = UiKitJtMa.subtitle(ratioMa >= 1 ? "Límite superado" : leftMinMa + " min restantes");

        JPanel topMa = new JPanel(new BorderLayout(8, 0));
        topMa.setOpaque(false);
        topMa.add(barMa, BorderLayout.CENTER);
        topMa.add(pctMa, BorderLayout.EAST);
        JPanel eastMa = new JPanel(new GridLayout(2, 1, 0, 2));
        eastMa.setOpaque(false);
        eastMa.setPreferredSize(new Dimension(170, 40));
        JPanel barWrapMa = new JPanel(new java.awt.GridBagLayout());
        barWrapMa.setOpaque(false);
        GridBagConstraints gcMa = new GridBagConstraints();
        gcMa.fill = GridBagConstraints.HORIZONTAL;
        gcMa.weightx = 1;
        barWrapMa.add(topMa, gcMa);
        JPanel leftWrapMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        leftWrapMa.setOpaque(false);
        leftWrapMa.add(leftMa);
        eastMa.add(barWrapMa);
        eastMa.add(leftWrapMa);
        rowMa.add(eastMa, BorderLayout.EAST);
        return rowMa;
    }

    private JPanel groupRow(UserGroupJt groupMa) {
        JPanel cardMa = UiKitJtMa.roundedPanel(12);
        cardMa.setLayout(new BorderLayout(10, 0));
        cardMa.setAlignmentX(LEFT_ALIGNMENT);

        JPanel textMa = new JPanel(new GridLayout(3, 1));
        textMa.setOpaque(false);
        JLabel nameMa = new JLabel(groupMa.getName());
        nameMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        JLabel codeMa = new JLabel("[ " + groupMa.getCode() + " ]");
        codeMa.setFont(new Font(Font.MONOSPACED, Font.PLAIN, nameMa.getFont().getSize()));
        textMa.add(nameMa);
        textMa.add(codeMa);
        textMa.add(UiKitJtMa.subtitle("Solo en este equipo"));

        JButton copyMa = new JButton("Copiar código");
        copyMa.addActionListener(evMa -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(groupMa.getCode()), null));
        JPanel eastMa = new JPanel(new java.awt.GridBagLayout());
        eastMa.setOpaque(false);
        eastMa.add(copyMa);

        cardMa.add(textMa, BorderLayout.CENTER);
        cardMa.add(eastMa, BorderLayout.EAST);
        cardMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        return cardMa;
    }
}
