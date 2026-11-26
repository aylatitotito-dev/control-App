package com.appcontrol.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JComponent;

import com.appcontrol.model.ActivityEventJt;
import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.InstalledAppsServiceJtMa;
import com.appcontrol.service.InstalledAppsServiceJtMa.InstalledAppJt;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.tracker.RefreshCoordinatorMa;

/** Historial: linea de tiempo del dia con las aplicaciones usadas, los sitios visitados y los bloqueos. */
class HistoryPageMa extends BrowsePageJt {

    private static final String[] FILTERS_JT = {"Todo", "Aplicaciones", "Sitios web", "Bloqueos"};
    private static final String[] SORTS_JT = {"Más reciente primero", "Más antiguo primero", "Mayor duración"};
    private static final DateTimeFormatter HOUR_JT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_HOUR_JT = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    /** Las sesiones de aplicaciones de menos de esto son un salto de ventana, no uso real. */
    private static final long MIN_APP_SECONDS_JT = 15;
    private static final int DAYS_BACK_JT = 7;

    private final ActivityServiceMa activityJt;
    private final RuleServiceJt rulesJt;
    private final AccountServiceMa accountJt;
    private final InstalledAppsServiceJtMa installedJt;
    private final RefreshCoordinatorMa refreshJt;
    private final JComboBox<String> dayBoxJt;
    private int iconSigJt;

    HistoryPageMa(ActivityServiceMa activityJt, RuleServiceJt rulesJt, AccountServiceMa accountJt,
            InstalledAppsServiceJtMa installedJt, RefreshCoordinatorMa refreshJt,
            javax.swing.ComboBoxModel<String> dayModelJt) {
        super("Historial", null);
        setBorder(javax.swing.BorderFactory.createEmptyBorder());
        this.dayBoxJt = new JComboBox<>(dayModelJt);
        HistoryDaysJt.attachPickerJt(dayBoxJt, this);
        this.activityJt = activityJt;
        this.rulesJt = rulesJt;
        this.accountJt = accountJt;
        this.installedJt = installedJt;
        this.refreshJt = refreshJt;

        buildMa();
    }

    @Override
    void refreshNowMa() {
        refreshJt.refreshSitesMa();
    }

    @Override
    String[] filtersMa() {
        return FILTERS_JT;
    }

    @Override
    String[] sortsMa() {
        return SORTS_JT;
    }

    @Override
    String placeholderMa() {
        return "Buscar en el historial";
    }

    @Override
    String nounMa(int amountJt) {
        return amountJt == 1 ? "registro" : "registros";
    }

    @Override
    String emptyTextMa() {
        return "No hay actividad registrada con ese filtro en este día.";
    }

    @Override
    JComponent extraActionMa() {
        dayBoxJt.addActionListener(evJt -> {
            resetPageMa();
            rebuildMa(true);
        });
        return dayBoxJt;
    }

    @Override
    String signatureExtraMa() {
        return "ic=" + AppIconsMa.cacheSizeJt() + ":" + iconSigJt;
    }

    @Override
    ResultJt loadMa(String queryJt, String filterNameJt, String sortNameJt) {
        ZoneId zoneJt = ZoneId.systemDefault();
        HistoryDaysJt.RangeJt rangeJt = HistoryDaysJt.rangeFor(dayBoxJt.getSelectedIndex(), dayBoxJt.getItemCount());
        boolean weekJt = rangeJt.multiDay();
        Instant fromJt = rangeJt.first().atStartOfDay(zoneJt).toInstant();
        Instant toJt = rangeJt.last().plusDays(1).atStartOfDay(zoneJt).toInstant();

        Map<String, InstalledAppJt> installedByExeJt = new HashMap<>();
        for (InstalledAppJt appJt : installedJt.cached()) {
            installedByExeJt.put(appJt.exe().toLowerCase(Locale.ROOT), appJt);
        }
        Map<String, BlockRuleJtMa> appRulesJt = new HashMap<>();
        for (BlockRuleJtMa ruleJt : rulesJt.listJtMa(BlockRuleJtMa.APP)) {
            appRulesJt.put(ruleJt.getTargetKey(), ruleJt);
        }
        Map<String, BlockRuleJtMa> siteRulesJt = new HashMap<>();
        for (BlockRuleJtMa ruleJt : rulesJt.listJtMa(BlockRuleJtMa.SITE)) {
            siteRulesJt.put(ruleJt.getTargetKey(), ruleJt);
        }

        List<ActivityEventJt> eligibleJt = new ArrayList<>();
        for (ActivityEventJt eventJt : activityJt.listJt(fromJt, toJt)) {
            boolean shortAppJt = ActivityEventJt.APP.equals(eventJt.getKind())
                    && eventJt.duration().getSeconds() < MIN_APP_SECONDS_JT;
            if (!shortAppJt) {
                eligibleJt.add(eventJt);
            }
        }

        List<ActivityEventJt> shownJt = new ArrayList<>();
        for (ActivityEventJt eventJt : eligibleJt) {
            String nameJt = displayName(eventJt, installedByExeJt);
            if (!queryJt.isEmpty() && !nameJt.toLowerCase(Locale.ROOT).contains(queryJt)
                    && !eventJt.getTargetKey().contains(queryJt)) {
                continue;
            }
            boolean okJt = switch (filterNameJt == null ? "Todo" : filterNameJt) {
                case "Aplicaciones" -> ActivityEventJt.APP.equals(eventJt.getKind());
                case "Sitios web" -> ActivityEventJt.SITE.equals(eventJt.getKind());
                case "Bloqueos" -> eventJt.getKind().startsWith("BLOCK");
                default -> true;
            };
            if (okJt) {
                shownJt.add(eventJt);
            }
        }
        switch (sortNameJt == null ? SORTS_JT[0] : sortNameJt) {
            case "Más antiguo primero" -> shownJt.sort(Comparator.comparing(ActivityEventJt::getStartedAt));
            case "Mayor duración" -> shownJt.sort(Comparator.comparing((ActivityEventJt eJt) -> eJt.duration())
                    .reversed().thenComparing(ActivityEventJt::getStartedAt, Comparator.reverseOrder()));
            default -> shownJt.sort(Comparator.comparing(ActivityEventJt::getStartedAt).reversed());
        }

        int withSiteIconJt = 0;
        List<BrowseItemMa> itemsJt = new ArrayList<>();
        for (ActivityEventJt eventJt : shownJt) {
            boolean siteJt = ActivityEventJt.SITE.equals(eventJt.getKind())
                    || ActivityEventJt.BLOCK_SITE.equals(eventJt.getKind());
            if (siteJt && SiteIconsJt.hasJt(eventJt.getTargetKey())) {
                withSiteIconJt++;
            }
            itemsJt.add(itemFor(eventJt, weekJt, installedByExeJt, siteJt ? siteRulesJt : appRulesJt, siteJt));
        }
        iconSigJt = withSiteIconJt;
        return new ResultJt(itemsJt, eligibleJt.size());
    }

    private String displayName(ActivityEventJt eventJt, Map<String, InstalledAppJt> installedByExeJt) {
        boolean appJt = ActivityEventJt.APP.equals(eventJt.getKind())
                || ActivityEventJt.BLOCK_APP.equals(eventJt.getKind());
        InstalledAppJt knownJt = appJt ? installedByExeJt.get(eventJt.getTargetKey()) : null;
        return knownJt != null ? knownJt.name() : eventJt.getDisplayName();
    }

    private BrowseItemMa itemFor(ActivityEventJt eventJt, boolean withDayJt,
            Map<String, InstalledAppJt> installedByExeJt, Map<String, BlockRuleJtMa> rulesByKeyJt, boolean siteJt) {
        ZoneId zoneJt = ZoneId.systemDefault();
        DateTimeFormatter fmtJt = withDayJt ? DAY_HOUR_JT : HOUR_JT;
        String startJt = eventJt.getStartedAt().atZone(zoneJt).format(fmtJt);
        long secondsJt = eventJt.duration().getSeconds();
        String rangeJt = secondsJt >= 60 ? startJt + " – " + eventJt.getEndedAt().atZone(zoneJt).format(HOUR_JT)
                : startJt;
        String nameJt = displayName(eventJt, installedByExeJt);
        String typeJt = siteJt ? "Sitio web" : "Aplicación";

        String subJt;
        String chipJt;
        ChipMa.KindJtMa kindJt;
        switch (eventJt.getKind()) {
            case ActivityEventJt.APP -> {
                subJt = rangeJt + "  |  " + durationText(secondsJt) + "  |  " + typeJt;
                chipJt = typeJt;
                kindJt = ChipMa.KindJtMa.NEUTRAL;
            }
            case ActivityEventJt.SITE -> {
                int visitsJt = eventJt.getHits();
                subJt = rangeJt + "  |  " + visitsJt + (visitsJt == 1 ? " visita" : " visitas")
                        + (secondsJt >= 60 ? "  |  " + durationText(secondsJt) : "") + "  |  " + typeJt;
                chipJt = typeJt;
                kindJt = ChipMa.KindJtMa.NEUTRAL;
            }
            default -> {
                int triesJt = eventJt.getHits();
                subJt = rangeJt + "  |  " + triesJt + (triesJt == 1 ? " intento" : " intentos") + "  |  " + typeJt;
                chipJt = "Bloqueado";
                kindJt = ChipMa.KindJtMa.DANGER;
            }
        }

        String keyJt = eventJt.getTargetKey();
        String pathJt = null;
        if (!siteJt) {
            InstalledAppJt appJt = installedByExeJt.get(keyJt);
            pathJt = appJt == null ? null : appJt.path();
        }
        String iconPathJt = pathJt;
        String kindRuleJt = siteJt ? BlockRuleJtMa.SITE : BlockRuleJtMa.APP;
        return new BrowseItemMa(nameJt, subJt, chipJt, kindJt, null,
                sizeJt -> siteJt ? SiteIconsJt.ofJt(keyJt, sizeJt)
                        : AppIconsMa.cachedOrPlaceholder(iconPathJt, nameJt, sizeJt),
                () -> RuleActionsMa.menuMa(this, rulesJt, accountJt, kindRuleJt, keyJt, nameJt,
                        rulesByKeyJt.get(keyJt), () -> rebuildMa(true)));
    }

    private static String durationText(long secondsJt) {
        return secondsJt < 60 ? secondsJt + " s" : FmtJt.durationJtMa(Duration.ofSeconds(secondsJt).getSeconds());
    }
}
