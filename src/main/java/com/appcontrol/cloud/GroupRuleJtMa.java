package com.appcontrol.cloud;

import java.time.Instant;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.RuleStateJt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Regla que viene de un grupo (la puso quien nos controla). Se guarda aqui para seguir aplicandose sin internet.
 * No se puede editar ni quitar desde este equipo: solo desaparece cuando termina el control.
 */
@Entity
@Table(name = "group_rule")
public class GroupRuleJtMa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "group_name", nullable = false, length = 80)
    private String groupName;

    @Column(name = "owner_name", length = 80)
    private String ownerName;

    @Column(name = "rule_kind", nullable = false, length = 8)
    private String kind;

    @Column(name = "target_key", nullable = false, length = 253)
    private String targetKey;

    @Column(name = "display_name", nullable = false, length = 253)
    private String displayName;

    @Column(name = "rule_mode", nullable = false, length = 16)
    private String mode;

    @Column(name = "rule_state", nullable = false, length = 16)
    private String state;

    @Column(name = "days_mask", nullable = false)
    private int daysMask;

    @Column(name = "start_minute", nullable = false)
    private int startMinute;

    @Column(name = "end_minute", nullable = false)
    private int endMinute;

    @Column(name = "quota_minutes", nullable = false)
    private int quotaMinutes;

    /** Fin del control por horario; null si es permanente. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected GroupRuleJtMa() {
    }

    public GroupRuleJtMa(Long memberId, String groupName, String ownerName, CloudDtoJt.RuleDtoJt dtoJt,
            Instant expiresAt) {
        this.memberId = memberId;
        this.groupName = groupName;
        this.ownerName = ownerName;
        this.kind = dtoJt.kind();
        this.targetKey = dtoJt.targetKey();
        this.displayName = dtoJt.displayName();
        this.mode = dtoJt.mode();
        this.state = dtoJt.state();
        this.daysMask = dtoJt.daysMask();
        this.startMinute = dtoJt.startMinute();
        this.endMinute = dtoJt.endMinute();
        this.quotaMinutes = dtoJt.quotaMinutes();
        this.expiresAt = expiresAt;
    }

    /** ¿Es la misma regla (aunque sea otra fila)? Sirve para no borrar y volver a guardar lo que no cambió. */
    public boolean sameAsMa(GroupRuleJtMa otherMa) {
        return java.util.Objects.equals(memberId, otherMa.memberId) && java.util.Objects.equals(kind, otherMa.kind)
                && java.util.Objects.equals(targetKey, otherMa.targetKey) && java.util.Objects.equals(mode, otherMa.mode)
                && java.util.Objects.equals(state, otherMa.state) && daysMask == otherMa.daysMask
                && startMinute == otherMa.startMinute && endMinute == otherMa.endMinute
                && quotaMinutes == otherMa.quotaMinutes && java.util.Objects.equals(expiresAt, otherMa.expiresAt)
                && java.util.Objects.equals(ownerName, otherMa.ownerName)
                && java.util.Objects.equals(groupName, otherMa.groupName);
    }

    public Long memberIdMa() {
        return memberId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getKind() {
        return kind;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTargetKey() {
        return targetKey;
    }

    /** Texto corto para mostrar: "Bloqueo total", "Horario 22:00–06:00" o "Límite 60 min/día". */
    public String summaryMa() {
        return switch (mode) {
            case "SCHEDULE" -> String.format("Horario %02d:%02d–%02d:%02d", startMinute / 60, startMinute % 60,
                    endMinute / 60, endMinute % 60);
            case "QUOTA" -> "Límite " + quotaMinutes + " min/día";
            default -> "Bloqueo total";
        };
    }

    /** La misma regla, con la forma que entienden los motores de bloqueo (no se guarda en block_rule). */
    public BlockRuleJtMa toRuleJt() {
        BlockRuleJtMa ruleJt = new BlockRuleJtMa(kind, targetKey, displayName);
        ruleJt.configureJt(RuleModeMa.valueOf(mode), daysMask, startMinute, endMinute, quotaMinutes);
        ruleJt.setStateJt(RuleStateJt.valueOf(state), null);
        return ruleJt;
    }
}
