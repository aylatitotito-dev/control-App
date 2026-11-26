package com.appcontrol.server.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Una persona/equipo dentro de un grupo y el estado de su "control": el dueño lo solicita y el miembro tiene que
 * aceptarlo en su equipo. Sin aceptacion no se aplica ninguna regla ni se puede ver su actividad.
 */
@Entity
@Table(name = "group_member", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "device_id"}))
public class MemberJt {

    public static final String OWNER = "OWNER";
    public static final String MEMBER = "MEMBER";
    public static final String PENDING = "PENDING";
    public static final String ACTIVE = "ACTIVE";
    public static final String NONE = "NONE";
    public static final String REQUESTED = "REQUESTED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String PERMANENT = "PERMANENT";
    public static final String TIMED = "TIMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "member_role", nullable = false, length = 10)
    private String role;

    @Column(name = "member_status", nullable = false, length = 10)
    private String status;

    @Column(name = "control_status", nullable = false, length = 10)
    private String controlStatus = NONE;

    @Column(name = "control_kind", length = 10)
    private String controlKind;

    @Column(name = "control_from")
    private Instant controlFrom;

    @Column(name = "control_until")
    private Instant controlUntil;

    @Column(name = "member_can_leave", nullable = false)
    private boolean memberCanLeave;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected MemberJt() {
    }

    public MemberJt(Long groupId, Long deviceId, String displayName, String role, String status, Instant joinedAt) {
        this.groupId = groupId;
        this.deviceId = deviceId;
        this.displayName = displayName;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    /** Control aceptado y dentro de su horario (si es por horario). */
    public boolean controlActive(Instant nowJt) {
        if (!ACCEPTED.equals(controlStatus)) {
            return false;
        }
        boolean startedJt = controlFrom == null || !nowJt.isBefore(controlFrom);
        boolean notEndedJt = controlUntil == null || nowJt.isBefore(controlUntil);
        return startedJt && notEndedJt;
    }

    /** El control por horario ya termino (aceptado o todavia solicitado). */
    public boolean controlEnded(Instant nowJt) {
        return !NONE.equals(controlStatus) && controlUntil != null && !nowJt.isBefore(controlUntil);
    }

    public void clearControl() {
        controlStatus = NONE;
        controlKind = null;
        controlFrom = null;
        controlUntil = null;
        memberCanLeave = false;
    }

    public void requestControl(String kind, Instant from, Instant until, boolean canLeave) {
        this.controlStatus = REQUESTED;
        this.controlKind = kind;
        this.controlFrom = from;
        this.controlUntil = until;
        this.memberCanLeave = canLeave;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getControlStatus() {
        return controlStatus;
    }

    public void setControlStatus(String controlStatus) {
        this.controlStatus = controlStatus;
    }

    public String getControlKind() {
        return controlKind;
    }

    public Instant getControlFrom() {
        return controlFrom;
    }

    public Instant getControlUntil() {
        return controlUntil;
    }

    public boolean isMemberCanLeave() {
        return memberCanLeave;
    }
}
