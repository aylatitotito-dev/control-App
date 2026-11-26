package com.appcontrol.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.model.ActivityEventJt;
import com.appcontrol.model.AppUsageJtMa;
import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.repository.AppUsageRepositoryJtMa;

/**
 * Resumen por dias: tiempo total, aplicaciones y sitios ordenados por tiempo, y en cuantos dias se supero la cuota.
 * El tiempo de aplicaciones sale del medidor (exacto por dia); el de sitios, de las sesiones con la pestana al frente
 * (aproximado: antes de existir ese seguimiento solo hay visitas).
 */
@Service
public class SummaryServiceMa {

    /** Tiempo total de un dia. */
    public record DayTotalJt(LocalDate day, long seconds) {
    }

    /**
     * Una fila del resumen. quotaMinutes y daysOver solo tienen sentido para aplicaciones con cuota
     * (daysOver = en cuantos dias del periodo se llego al limite; 0 y quota 0 si no hay cuota).
     */
    public record RankedJt(String key, String display, long seconds, long hits, int quotaMinutes, int daysOver) {
    }

    public record SummaryJt(long totalSeconds, List<DayTotalJt> perDay, List<RankedJt> apps, List<RankedJt> sites) {
    }

    private final AppUsageRepositoryJtMa usageJt;
    private final ActivityServiceMa activityJt;
    private final RuleServiceJt rulesJt;

    public SummaryServiceMa(AppUsageRepositoryJtMa usageJt, ActivityServiceMa activityJt, RuleServiceJt rulesJt) {
        this.usageJt = usageJt;
        this.activityJt = activityJt;
        this.rulesJt = rulesJt;
    }

    /** Resumen de los dias desde "from" hasta "to" (ambos incluidos). */
    @Transactional(readOnly = true)
    public SummaryJt summarizeJt(LocalDate fromJt, LocalDate toJt) {
        ZoneId zoneJt = ZoneId.systemDefault();
        List<AppUsageJtMa> usageRowsJt = usageJt.findByUsageDayBetween(fromJt, toJt);
        List<ActivityEventJt> eventsJt = new ArrayList<>();
        for (ActivityEventJt eventJt : activityJt.listJt(fromJt.atStartOfDay(zoneJt).toInstant(),
                toJt.plusDays(1).atStartOfDay(zoneJt).toInstant())) {
            if (ActivityEventJt.SITE.equals(eventJt.getKind())) {
                eventsJt.add(eventJt);
            }
        }
        Map<String, Integer> quotasJt = new HashMap<>();
        for (BlockRuleJtMa ruleJt : rulesJt.listJtMa(BlockRuleJtMa.APP)) {
            if (ruleJt.getMode() == RuleModeMa.QUOTA && ruleJt.getQuotaMinutes() > 0) {
                quotasJt.put(ruleJt.getTargetKey(), ruleJt.getQuotaMinutes());
            }
        }
        return aggregateJt(usageRowsJt, eventsJt, fromJt, toJt, quotasJt);
    }

    /** Parte pura del calculo (se prueba sin base de datos). */
    public static SummaryJt aggregateJt(List<AppUsageJtMa> usageRowsJt, List<ActivityEventJt> siteEventsJt,
            LocalDate fromJt, LocalDate toJt, Map<String, Integer> quotaMinutesByAppJt) {
        Map<LocalDate, Long> perDayJt = new LinkedHashMap<>();
        for (LocalDate dayJt = fromJt; !dayJt.isAfter(toJt); dayJt = dayJt.plusDays(1)) {
            perDayJt.put(dayJt, 0L);
        }
        Map<String, String> displayJt = new HashMap<>();
        Map<String, Long> secondsJt = new HashMap<>();
        Map<String, Integer> daysOverJt = new HashMap<>();
        long totalJt = 0;
        for (AppUsageJtMa rowJt : usageRowsJt) {
            if (rowJt.getUsageDay().isBefore(fromJt) || rowJt.getUsageDay().isAfter(toJt)) {
                continue;
            }
            String keyJt = rowJt.getAppName().toLowerCase(Locale.ROOT);
            displayJt.putIfAbsent(keyJt, rowJt.getAppName());
            secondsJt.merge(keyJt, rowJt.getSeconds(), Long::sum);
            perDayJt.merge(rowJt.getUsageDay(), rowJt.getSeconds(), Long::sum);
            totalJt += rowJt.getSeconds();
            Integer quotaJt = quotaMinutesByAppJt.get(keyJt);
            if (quotaJt != null && rowJt.getSeconds() >= quotaJt * 60L) {
                daysOverJt.merge(keyJt, 1, Integer::sum);
            }
        }
        List<RankedJt> appsJt = new ArrayList<>();
        for (Map.Entry<String, Long> entryJt : secondsJt.entrySet()) {
            String keyJt = entryJt.getKey();
            appsJt.add(new RankedJt(keyJt, displayJt.get(keyJt), entryJt.getValue(), 0,
                    quotaMinutesByAppJt.getOrDefault(keyJt, 0), daysOverJt.getOrDefault(keyJt, 0)));
        }
        appsJt.sort(Comparator.comparingLong(RankedJt::seconds).reversed().thenComparing(RankedJt::display));

        Map<String, long[]> sitesJt = new HashMap<>();   // dominio -> [segundos, visitas]
        for (ActivityEventJt eventJt : siteEventsJt) {
            long[] accJt = sitesJt.computeIfAbsent(eventJt.getTargetKey(), kJt -> new long[2]);
            accJt[0] += eventJt.duration().getSeconds();
            accJt[1] += eventJt.getHits();
        }
        List<RankedJt> siteListJt = new ArrayList<>();
        for (Map.Entry<String, long[]> entryJt : sitesJt.entrySet()) {
            siteListJt.add(new RankedJt(entryJt.getKey(), entryJt.getKey(), entryJt.getValue()[0],
                    entryJt.getValue()[1], 0, 0));
        }
        siteListJt.sort(Comparator.comparingLong(RankedJt::seconds).reversed()
                .thenComparing(Comparator.comparingLong(RankedJt::hits).reversed()).thenComparing(RankedJt::display));

        List<DayTotalJt> daysJt = new ArrayList<>();
        perDayJt.forEach((dayJt, secJt) -> daysJt.add(new DayTotalJt(dayJt, secJt)));
        return new SummaryJt(totalJt, daysJt, appsJt, siteListJt);
    }
}
