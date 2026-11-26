package com.appcontrol.ui;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.SeenSiteJtMa;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.RuleLogicJtMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.service.VisitedSitesServiceMa;
import com.appcontrol.tracker.RefreshCoordinatorMa;

/** Sitios web que se estan visitando (de los navegadores) y los que tienen una regla. */
class SitesPageJt extends BrowsePageJt {

    private record RowMa(String host, Instant lastSeen, BlockRuleJtMa rule) {
    }

    private static final String[] FILTERS_MA = {"Todos", "Con regla", "Sin regla"};
    private static final String[] SORTS_MA = {"Visita más reciente", "Nombre (de la A a la Z)",
        "Nombre (de la Z a la A)", "Con regla primero"};

    private final RuleServiceJt rulesMa;
    private final AccountServiceMa accountMa;
    private final VisitedSitesServiceMa visitedMa;
    private final RefreshCoordinatorMa refreshMa;
    private int iconCountMa;
    /** Reglas que pusieron otras personas (grupos): se muestran con candado y no se pueden quitar desde aquí. */
    private java.util.function.Supplier<List<com.appcontrol.cloud.GroupRuleJtMa>> groupRulesMa = List::of;

    void setGroupRulesJt(java.util.function.Supplier<List<com.appcontrol.cloud.GroupRuleJtMa>> groupRulesMa) {
        this.groupRulesMa = groupRulesMa;
    }

    SitesPageJt(RuleServiceJt rulesMa, AccountServiceMa accountMa, VisitedSitesServiceMa visitedMa,
            RefreshCoordinatorMa refreshMa) {
        super("Sitios y URLs", "Sitios y URLs");
        this.rulesMa = rulesMa;
        this.accountMa = accountMa;
        this.visitedMa = visitedMa;
        this.refreshMa = refreshMa;
        buildMa();
    }

    @Override
    void refreshNowMa() {
        refreshMa.refreshSitesMa();
    }

    @Override
    String[] filtersMa() {
        return FILTERS_MA;
    }

    @Override
    String[] sortsMa() {
        return SORTS_MA;
    }

    @Override
    String placeholderMa() {
        return "Buscar sitios";
    }

    @Override
    String nounMa(int amountMa) {
        return amountMa == 1 ? "sitio" : "sitios";
    }

    @Override
    String emptyTextMa() {
        return "Ningún sitio coincide con la búsqueda o el filtro. Navega un momento con Chrome, Edge, Brave o "
                + "Firefox: la lista se actualiza cada pocos minutos.";
    }

    @Override
    JComponent extraActionMa() {
        JButton addMa = new JButton("+  Agregar sitio");
        addMa.addActionListener(evMa -> {
            if (RuleDialogJt.showJt(this, rulesMa, accountMa, BlockRuleJtMa.SITE, null, null, null)) {
                rebuildMa(true);
            }
        });
        return addMa;
    }

    @Override
    ResultJt loadMa(String queryMa, String filterNameMa, String sortNameMa) {
        Map<String, BlockRuleJtMa> ruleMapMa = new LinkedHashMap<>();
        for (BlockRuleJtMa ruleMa : rulesMa.listJtMa(BlockRuleJtMa.SITE)) {
            ruleMapMa.put(ruleMa.getTargetKey(), ruleMa);
        }
        Map<String, RowMa> mapMa = new LinkedHashMap<>();
        for (SeenSiteJtMa seenMa : visitedMa.list()) {
            mapMa.put(seenMa.getHost(), new RowMa(seenMa.getHost(), seenMa.getLastSeen(),
                    ruleMapMa.get(seenMa.getHost())));
        }
        for (BlockRuleJtMa ruleMa : ruleMapMa.values()) {
            mapMa.putIfAbsent(ruleMa.getTargetKey(), new RowMa(ruleMa.getTargetKey(), null, ruleMa));
        }

        Map<String, com.appcontrol.cloud.GroupRuleJtMa> groupMa = new LinkedHashMap<>();
        for (com.appcontrol.cloud.GroupRuleJtMa groupRuleMa : groupRulesMa.get()) {
            if (BlockRuleJtMa.SITE.equals(groupRuleMa.getKind())) {
                groupMa.put(groupRuleMa.getTargetKey(), groupRuleMa);
                mapMa.putIfAbsent(groupRuleMa.getTargetKey(), new RowMa(groupRuleMa.getTargetKey(), null, null));
            }
        }
        List<RowMa> rowsMa = new ArrayList<>();
        for (RowMa rowMa : mapMa.values()) {
            if (!queryMa.isEmpty() && !rowMa.host().contains(queryMa)) {
                continue;
            }
            boolean okMa = switch (filterNameMa == null ? "Todos" : filterNameMa) {
                case "Con regla" -> rowMa.rule() != null || groupMa.containsKey(rowMa.host());
                case "Sin regla" -> rowMa.rule() == null && !groupMa.containsKey(rowMa.host());
                default -> true;
            };
            if (okMa) {
                rowsMa.add(rowMa);
            }
        }
        Comparator<RowMa> byNameMa = Comparator.comparing(RowMa::host);
        Comparator<RowMa> recentMa = Comparator
                .comparing((RowMa rowMa) -> rowMa.lastSeen() == null ? Instant.EPOCH : rowMa.lastSeen())
                .reversed().thenComparing(byNameMa);
        switch (sortNameMa == null ? SORTS_MA[0] : sortNameMa) {
            case "Nombre (de la A a la Z)" -> rowsMa.sort(byNameMa);
            case "Nombre (de la Z a la A)" -> rowsMa.sort(byNameMa.reversed());
            case "Con regla primero" -> rowsMa.sort(Comparator.comparing((RowMa rowMa) -> rowMa.rule() == null)
                    .thenComparing(recentMa));
            default -> rowsMa.sort(recentMa);
        }

        LocalDateTime nowMa = LocalDateTime.now();
        Instant instantMa = Instant.now();
        List<BrowseItemMa> itemsMa = new ArrayList<>();
        for (RowMa rowMa : rowsMa) {
            String seenTextMa = rowMa.lastSeen() == null ? "Agregado a mano" : "Visto " + FmtJt.agoJtMa(rowMa.lastSeen());
            String chipMa = null;
            ChipMa.KindJtMa kindMa = null;
            String subMa = seenTextMa;
            if (rowMa.rule() != null) {
                RuleLogicJtMa.StatusMa statusMa = RuleLogicJtMa.evaluateMa(rowMa.rule(), nowMa, instantMa, 0);
                RuleActionsMa.ChipSpecJt specMa = RuleActionsMa.chipMa(rowMa.rule(), statusMa);
                chipMa = specMa.text();
                kindMa = specMa.kind();
                subMa = seenTextMa + "  |  " + statusMa.label();
            }
            com.appcontrol.cloud.GroupRuleJtMa lockMa = groupMa.get(rowMa.host());
            if (lockMa != null && rowMa.rule() == null) {
                chipMa = GroupLockJtMa.chipTextJtMa(lockMa);
                kindMa = ChipMa.KindJtMa.DANGER;
                subMa = seenTextMa + "  |  " + lockMa.summaryMa();
            }
            itemsMa.add(new BrowseItemMa(rowMa.host(), subMa, chipMa, kindMa, null,
                    sizeMa -> SiteIconsJt.ofJt(rowMa.host(), sizeMa),
                    () -> lockMa != null ? GroupLockJtMa.infoMenuJtMa(lockMa) : RuleActionsMa.menuMa(this, rulesMa, accountMa, BlockRuleJtMa.SITE, rowMa.host(), rowMa.host(),
                            rulesMa.findJtMa(BlockRuleJtMa.SITE, rowMa.host()).orElse(null), () -> rebuildMa(true))));
        }
        int withIconMa = 0;
        for (RowMa rowMa : rowsMa) {
            if (SiteIconsJt.hasJt(rowMa.host())) {
                withIconMa++;
            }
        }
        iconCountMa = withIconMa;
        return new ResultJt(itemsMa, mapMa.size());
    }

    @Override
    String signatureExtraMa() {
        return "icons=" + iconCountMa;
    }
}
