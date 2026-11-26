package com.appcontrol.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Regla que el dueño puso a un miembro. El equipo la baja y la aplica solo, aunque el dueño no este conectado. */
@Entity
@Table(name = "remote_rule")
public class RemoteRuleMa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "rule_kind", nullable = false, length = 8)
    private String kind;

    @Column(name = "target_key", nullable = false, length = 255)
    private String targetKey;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "rule_mode", nullable = false, length = 10)
    private String mode;

    @Column(name = "rule_state", nullable = false, length = 10)
    private String state;

    @Column(name = "days_mask", nullable = false)
    private int daysMask;

    @Column(name = "start_minute", nullable = false)
    private int startMinute;

    @Column(name = "end_minute", nullable = false)
    private int endMinute;

    @Column(name = "quota_minutes", nullable = false)
    private int quotaMinutes;

    protected RemoteRuleMa() {
    }

    public RemoteRuleMa(Long memberId, String kind, String targetKey, String displayName, String mode, String state,
            int daysMask, int startMinute, int endMinute, int quotaMinutes) {
        this.memberId = memberId;
        this.kind = kind;
        this.targetKey = targetKey;
        this.displayName = displayName;
        this.mode = mode;
        this.state = state;
        this.daysMask = daysMask;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.quotaMinutes = quotaMinutes;
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

    public String getMode() {
        return mode;
    }

    public String getState() {
        return state;
    }

    public int getDaysMask() {
        return daysMask;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public int getQuotaMinutes() {
        return quotaMinutes;
    }
}
