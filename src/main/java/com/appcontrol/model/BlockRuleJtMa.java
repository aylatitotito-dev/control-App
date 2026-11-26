package com.appcontrol.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Regla de restriccion para una aplicacion (kind APP, clave = nombre del programa en minusculas)
 * o un sitio web (kind SITE, clave = dominio).
 * Los dias se guardan como bits: lunes = 1, martes = 2, ... domingo = 64.
 */
@Entity
@Table(name = "block_rule", uniqueConstraints = @UniqueConstraint(columnNames = {"rule_kind", "target_key"}))
public class BlockRuleJtMa {

    public static final String APP = "APP";
    public static final String SITE = "SITE";
    public static final int ALL_DAYS = 127;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_kind", nullable = false, length = 8)
    private String kind;

    @Column(name = "target_key", nullable = false, length = 253)
    private String targetKey;

    @Column(name = "display_name", nullable = false, length = 253)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_mode", nullable = false, length = 16)
    private RuleModeMa mode = RuleModeMa.ALWAYS;

    @Column(name = "days_mask")
    private Integer daysMask = ALL_DAYS;

    @Column(name = "start_minute")
    private Integer startMinute = 0;

    @Column(name = "end_minute")
    private Integer endMinute = 0;

    @Column(name = "quota_minutes")
    private Integer quotaMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_state", nullable = false, length = 16)
    private RuleStateJt state = RuleStateJt.ACTIVE;

    @Column(name = "paused_until")
    private Instant pausedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BlockRuleJtMa() {
    }

    public BlockRuleJtMa(String kind, String targetKey, String displayName) {
        this.kind = kind;
        this.targetKey = targetKey;
        this.displayName = displayName;
    }

    public void configureJt(RuleModeMa modeJt, int daysMaskJt, int startMinuteJt, int endMinuteJt, int quotaMinutesJt) {
        this.mode = modeJt;
        this.daysMask = daysMaskJt;
        this.startMinute = startMinuteJt;
        this.endMinute = endMinuteJt;
        this.quotaMinutes = quotaMinutesJt;
    }

    public void setStateJt(RuleStateJt stateJt, Instant untilJt) {
        this.state = stateJt;
        this.pausedUntil = stateJt == RuleStateJt.PAUSED ? untilJt : null;
    }

    public void rename(String displayNameJt) {
        this.displayName = displayNameJt;
    }

    public Long getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public RuleModeMa getMode() {
        return mode == null ? RuleModeMa.ALWAYS : mode;
    }

    public int getDaysMask() {
        return daysMask == null ? ALL_DAYS : daysMask;
    }

    public int getStartMinute() {
        return startMinute == null ? 0 : startMinute;
    }

    public int getEndMinute() {
        return endMinute == null ? 0 : endMinute;
    }

    public int getQuotaMinutes() {
        return quotaMinutes == null ? 0 : quotaMinutes;
    }

    public RuleStateJt getState() {
        return state == null ? RuleStateJt.ACTIVE : state;
    }

    public Instant getPausedUntil() {
        return pausedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
