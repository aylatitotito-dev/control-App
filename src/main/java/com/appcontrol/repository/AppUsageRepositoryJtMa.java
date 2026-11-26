package com.appcontrol.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.appcontrol.model.AppUsageJtMa;

public interface AppUsageRepositoryJtMa extends JpaRepository<AppUsageJtMa, Long> {

    Optional<AppUsageJtMa> findByAppNameAndUsageDay(String appName, LocalDate usageDay);

    List<AppUsageJtMa> findByUsageDayOrderBySecondsDesc(LocalDate usageDay);

    List<AppUsageJtMa> findByUsageDayBetween(LocalDate from, LocalDate to);
}
