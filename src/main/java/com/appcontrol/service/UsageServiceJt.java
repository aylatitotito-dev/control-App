package com.appcontrol.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.model.AppUsageJtMa;
import com.appcontrol.repository.AppUsageRepositoryJtMa;

@Service
public class UsageServiceJt {

    private final AppUsageRepositoryJtMa repoJtMa;

    public UsageServiceJt(AppUsageRepositoryJtMa repoJtMa) {
        this.repoJtMa = repoJtMa;
    }

    @Transactional
    public void recordJt(String appNameJtMa, long secondsJtMa) {
        LocalDate dayJtMa = LocalDate.now();
        AppUsageJtMa rowJtMa = repoJtMa.findByAppNameAndUsageDay(appNameJtMa, dayJtMa)
                .orElseGet(() -> new AppUsageJtMa(appNameJtMa, dayJtMa));
        rowJtMa.addSeconds(secondsJtMa);
        repoJtMa.save(rowJtMa);
    }

    @Transactional(readOnly = true)
    public List<AppUsageJtMa> topToday() {
        return repoJtMa.findByUsageDayOrderBySecondsDesc(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public long totalTodaySeconds() {
        return topToday().stream().mapToLong(AppUsageJtMa::getSeconds).sum();
    }

    /** Segundos usados hoy por app (clave en minusculas). */
    @Transactional(readOnly = true)
    public Map<String, Long> todayByAppJtMa() {
        Map<String, Long> mapJtMa = new HashMap<>();
        for (AppUsageJtMa rowJtMa : topToday()) {
            mapJtMa.put(rowJtMa.getAppName().toLowerCase(Locale.ROOT), rowJtMa.getSeconds());
        }
        return mapJtMa;
    }
}
