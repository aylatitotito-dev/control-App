package com.appcontrol.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import com.appcontrol.cloud.CloudDtoJt.RuleDtoJt;
import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.ui.MemberViewJtMa.RowMa;
import com.formdev.flatlaf.FlatClientProperties;

/**
 * Lista de aplicaciones o de sitios de OTRO equipo, con la forma de las pantallas Aplicaciones y Sitios de este
 * equipo (buscador, filtro, orden y vistas) y columnas extra: tiempo, categoría, límite, estado y un botón para
 * poner o quitar la regla. Todo cambio de reglas sigue pidiendo la contraseña.
 */
final class RemoteListPageJtMa extends BrowsePageJt {

    private static final String[] FILTERS_JT = {"Todos", "Con regla", "Sin regla"};

    /** Por debajo de este ancho las filas no llevan columnas (caben en el panel de detalle). */
    private static final int COMPACT_BELOW_JT = 980;

    private final MemberViewJtMa hostJt;
    private final String kindJt;
    private boolean compactJt;

    RemoteListPageJtMa(MemberViewJtMa hostJt, String kindJt) {
        super(BlockRuleJtMa.APP.equals(kindJt) ? "Aplicaciones" : "Sitios y URLs", null);
        this.hostJt = hostJt;
        this.kindJt = kindJt;
        setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 0, 0, 0));
        buildMa();
    }

    /** Si el panel cambia de ancho (por ejemplo, se abre el detalle) las filas pasan de columnas a compactas. */
    @Override
    public void doLayout() {
        boolean nowJt = getWidth() > 0 && getWidth() < COMPACT_BELOW_JT;
        if (nowJt != compactJt) {
            compactJt = nowJt;
            javax.swing.SwingUtilities.invokeLater(() -> rebuildMa(true));
        }
        super.doLayout();
    }

    /** Cuántas aplicaciones o sitios hay en la lista (sin filtros), para el título de la pestaña. */
    int countJt() {
        return hostJt.rowsJt(kindJt).size();
    }

    private boolean appsJt() {
        return BlockRuleJtMa.APP.equals(kindJt);
    }

    @Override
    void refreshNowMa() {
        hostJt.fetchNowJt();
    }

    /** Estas listas solo se redibujan cuando el equipo trae datos nuevos, no solas cada 5 s. */
    @Override
    void refreshMa() {
    }

    @Override
    String[] filtersMa() {
        return FILTERS_JT;
    }

    @Override
    String[] sortsMa() {
        return appsJt() ? new String[] {"Más usadas", "Nombre (de la A a la Z)", "Con regla primero"}
                : new String[] {"Más visitados", "Nombre (de la A a la Z)", "Con regla primero"};
    }

    @Override
    String placeholderMa() {
        return appsJt() ? "Buscar aplicaciones…" : "Buscar sitios y URLs…";
    }

    @Override
    String nounMa(int amountJt) {
        if (appsJt()) {
            return amountJt == 1 ? "aplicación" : "aplicaciones";
        }
        return amountJt == 1 ? "sitio" : "sitios";
    }

    @Override
    String emptyTextMa() {
        return "Todavía no hay datos de este equipo. Pulsa actualizar: llegan cada ~45 segundos.";
    }

    @Override
    JComponent extraActionMa() {
        JButton addJt = new JButton(appsJt() ? "+  Restringir aplicación" : "+  Bloquear un sitio");
        if (!appsJt()) {
            addJt.putClientProperty(FlatClientProperties.STYLE,
                    "background: $Component.accentColor; foreground: #FFFFFF; borderWidth: 0; focusedBackground: darken($Component.accentColor,10%)");
        }
        addJt.addActionListener(evJt -> {
            if (appsJt()) {
                restrictAppJt();
            } else {
                hostJt.addSiteJt();
            }
        });
        return addJt;
    }

    /** Elegir una aplicación de las que ese equipo informó (o escribir su nombre de programa) y ponerle una regla. */
    private void restrictAppJt() {
        List<RowMa> knownJt = hostJt.rowsJt(BlockRuleJtMa.APP);
        JComboBox<String> boxJt = new JComboBox<>();
        boxJt.setEditable(true);
        knownJt.stream().map(RowMa::name).sorted(String.CASE_INSENSITIVE_ORDER).forEach(boxJt::addItem);
        boxJt.setSelectedItem(null);
        JPanel panelJt = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        panelJt.add(new javax.swing.JLabel("Aplicación (elige una o escribe el nombre del programa, ej. roblox):"));
        panelJt.add(boxJt);
        if (JOptionPane.showConfirmDialog(this, panelJt, "Restringir aplicación", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        Object pickedJt = boxJt.getSelectedItem();
        String textJt = pickedJt == null ? "" : pickedJt.toString().trim();
        if (textJt.isEmpty()) {
            return;
        }
        for (RowMa rowJt : knownJt) {
            if (rowJt.name().equalsIgnoreCase(textJt)) {
                hostJt.configureKeyJt(kindJt, rowJt.key(), rowJt.name());
                return;
            }
        }
        String keyJt = textJt.toLowerCase(Locale.ROOT).replace(".exe", "");
        hostJt.configureKeyJt(kindJt, keyJt, textJt);
    }

    @Override
    ResultJt loadMa(String queryJt, String filterNameJt, String sortNameJt) {
        compactJt = getWidth() > 0 && getWidth() < COMPACT_BELOW_JT;
        List<RowMa> allJt = hostJt.rowsJt(kindJt);
        List<RowMa> rowsJt = new ArrayList<>();
        for (RowMa rowJt : allJt) {
            if (!queryJt.isEmpty() && !rowJt.name().toLowerCase(Locale.ROOT).contains(queryJt)
                    && !rowJt.key().contains(queryJt)) {
                continue;
            }
            boolean okJt = switch (filterNameJt == null ? "Todos" : filterNameJt) {
                case "Con regla" -> rowJt.rule() != null;
                case "Sin regla" -> rowJt.rule() == null;
                default -> true;
            };
            if (okJt) {
                rowsJt.add(rowJt);
            }
        }
        Comparator<RowMa> byNameJt = Comparator.comparing(rowJt -> rowJt.name().toLowerCase(Locale.ROOT));
        Comparator<RowMa> byUseJt = Comparator.comparingLong((RowMa rowJt) -> -rowJt.seconds()).thenComparing(byNameJt);
        String sortJt = sortNameJt == null ? "" : sortNameJt;
        if (sortJt.startsWith("Nombre")) {
            rowsJt.sort(byNameJt);
        } else if (sortJt.startsWith("Con regla")) {
            rowsJt.sort(Comparator.comparing((RowMa rowJt) -> rowJt.rule() == null).thenComparing(byUseJt));
        } else {
            rowsJt.sort(byUseJt);
        }

        List<BrowseItemMa> itemsJt = new ArrayList<>();
        for (RowMa rowJt : rowsJt) {
            itemsJt.add(itemJt(rowJt));
        }
        return new ResultJt(itemsJt, allJt.size());
    }

    private BrowseItemMa itemJt(RowMa rowJt) {
        RuleDtoJt ruleJt = rowJt.rule();
        boolean usedJt = rowJt.seconds() > 0;
        String subJt;
        if (appsJt()) {
            subJt = !usedJt ? "Sin uso en este periodo" : rowJt.key().equalsIgnoreCase(rowJt.name()) ? " "
                    : rowJt.key();
        } else {
            subJt = rowJt.visits() == 0 ? "Sin visitas"
                    : rowJt.visits() + (rowJt.visits() == 1 ? " visita" : " visitas");
        }
        String chipJt;
        ChipMa.KindJtMa chipKindJt;
        if (ruleJt != null) {
            chipJt = RemoteRuleDialogMa.textMa(ruleJt);
            chipKindJt = "ACTIVE".equals(ruleJt.state()) ? ChipMa.KindJtMa.DANGER : ChipMa.KindJtMa.NEUTRAL;
        } else if (usedJt || !appsJt()) {
            chipJt = "✓ Permitido";
            chipKindJt = ChipMa.KindJtMa.OK;
        } else {
            chipJt = "No usado";
            chipKindJt = ChipMa.KindJtMa.NEUTRAL;
        }
        Double quotaRatioJt = null;
        String quotaLabelJt = null;
        if (ruleJt != null && "QUOTA".equals(ruleJt.mode()) && ruleJt.quotaMinutes() > 0) {
            quotaRatioJt = rowJt.seconds() / (ruleJt.quotaMinutes() * 60.0);
            quotaLabelJt = FmtJt.durationJtMa(ruleJt.quotaMinutes() * 60L);
        }
        String actionTextJt = ruleJt != null ? "Desbloquear / Editar"
                : appsJt() ? (usedJt ? "Limitar / Bloquear" : "Restringir") : "Configurar bloqueo";
        String categoryJt = appsJt() ? CategoryJtMa.appJtMa(rowJt.key()) : CategoryJtMa.siteJtMa(rowJt.key());
        if (compactJt) {
            String timeJt = usedJt ? FmtJt.durationJtMa(rowJt.seconds()) : "Sin uso";
            return new BrowseItemMa(rowJt.name(), timeJt + (categoryJt == null ? "" : " · " + categoryJt), chipJt,
                    chipKindJt, null, sizeJt -> appsJt() ? AppIconsMa.placeholder(rowJt.name(), sizeJt)
                            : SiteIconsJt.ofJt(rowJt.key(), sizeJt), () -> menuJt(rowJt));
        }
        BrowseItemMa.RowExtraMa extraJt = new BrowseItemMa.RowExtraMa(usedJt ? FmtJt.durationJtMa(rowJt.seconds()) : "",
                appsJt() ? CategoryJtMa.appJtMa(rowJt.key()) : CategoryJtMa.siteJtMa(rowJt.key()), quotaRatioJt,
                quotaLabelJt, actionTextJt, () -> hostJt.configureKeyJt(kindJt, rowJt.key(), rowJt.name()));
        return new BrowseItemMa(rowJt.name(), subJt, chipJt, chipKindJt, null,
                sizeJt -> appsJt() ? AppIconsMa.placeholder(rowJt.name(), sizeJt) : SiteIconsJt.ofJt(rowJt.key(), sizeJt),
                () -> menuJt(rowJt), extraJt);
    }

    private JPopupMenu menuJt(RowMa rowJt) {
        JPopupMenu menuJt = new JPopupMenu();
        JMenuItem configJt = new JMenuItem(rowJt.rule() == null ? "Poner una regla…" : "Cambiar la regla…");
        configJt.addActionListener(evJt -> hostJt.configureKeyJt(kindJt, rowJt.key(), rowJt.name()));
        menuJt.add(configJt);
        if (rowJt.rule() != null) {
            JMenuItem removeJt = new JMenuItem("Quitar la regla");
            removeJt.addActionListener(evJt -> hostJt.removeKeyJt(kindJt, rowJt.key()));
            menuJt.add(removeJt);
        }
        return menuJt;
    }
}
