package com.appcontrol.server.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Segundos de uso de un programa en un dia (lo sube el equipo; solo programa y tiempo). */
@Entity
@Table(name = "device_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "app_key", "usage_day"}))
public class UsageRowJt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "app_key", nullable = false, length = 255)
    private String appKey;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "usage_day", nullable = false)
    private LocalDate day;

    @Column(name = "seconds", nullable = false)
    private long seconds;

    protected UsageRowJt() {
    }

    public UsageRowJt(Long deviceId, String appKey, String displayName, LocalDate day, long seconds) {
        this.deviceId = deviceId;
        this.appKey = appKey;
        this.displayName = displayName;
        this.day = day;
        this.seconds = seconds;
    }

    public void update(String displayName, long seconds) {
        this.displayName = displayName;
        this.seconds = seconds;
    }

    public String getAppKey() {
        return appKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDate getDay() {
        return day;
    }

    public long getSeconds() {
        return seconds;
    }
}
