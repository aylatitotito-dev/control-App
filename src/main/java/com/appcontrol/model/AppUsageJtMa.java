package com.appcontrol.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Segundos de uso de una aplicacion en un dia. */
@Entity
@Table(name = "app_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"app_name", "usage_day"}))
public class AppUsageJtMa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_name", nullable = false, length = 120)
    private String appName;

    @Column(name = "usage_day", nullable = false)
    private LocalDate usageDay;

    @Column(nullable = false)
    private long seconds;

    protected AppUsageJtMa() {
    }

    public AppUsageJtMa(String appName, LocalDate usageDay) {
        this.appName = appName;
        this.usageDay = usageDay;
    }

    public void addSeconds(long extraMa) {
        this.seconds += extraMa;
    }

    public Long getId() {
        return id;
    }

    public String getAppName() {
        return appName;
    }

    public LocalDate getUsageDay() {
        return usageDay;
    }

    public long getSeconds() {
        return seconds;
    }
}
