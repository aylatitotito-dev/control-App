package com.appcontrol.cloud;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Copia local de mis membresias en grupos (para mostrarlas y para el aviso "controlado por" aunque no haya internet). */
@Entity
@Table(name = "cloud_link")
public class CloudLinkJt {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "group_name", nullable = false, length = 80)
    private String groupName;

    @Column(name = "owner_name", length = 80)
    private String ownerName;

    @Column(name = "member_role", nullable = false, length = 10)
    private String role;

    @Column(name = "member_status", nullable = false, length = 10)
    private String status;

    @Column(name = "control_status", nullable = false, length = 10)
    private String controlStatus;

    @Column(name = "control_kind", length = 10)
    private String controlKind;

    @Column(name = "control_from")
    private Instant controlFrom;

    @Column(name = "control_until")
    private Instant controlUntil;

    @Column(name = "member_can_leave", nullable = false)
    private boolean memberCanLeave;

    /** Cuándo empezó el control que está en marcha (según la nube). */
    @Column(name = "control_started")
    private Instant controlStarted;

    protected CloudLinkJt() {
    }

    public CloudLinkJt(CloudDtoJt.SyncMembershipMa dtoJt) {
        this.memberId = dtoJt.memberId();
        this.groupId = dtoJt.groupId();
        this.groupName = dtoJt.groupName();
        this.ownerName = dtoJt.ownerName();
        this.role = dtoJt.role();
        this.status = dtoJt.status();
        this.controlStatus = dtoJt.controlStatus();
        this.controlKind = dtoJt.controlKind();
        this.controlFrom = dtoJt.controlFrom();
        this.controlUntil = dtoJt.controlUntil();
        this.memberCanLeave = dtoJt.memberCanLeave();
        this.controlStarted = dtoJt.sessionStart();
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public String getControlStatus() {
        return controlStatus;
    }

    public String getControlKind() {
        return controlKind;
    }

    public Instant getControlFrom() {
        return controlFrom;
    }

    public Instant getControlStarted() {
        return controlStarted;
    }

    public Instant getControlUntil() {
        return controlUntil;
    }

    public boolean isMemberCanLeave() {
        return memberCanLeave;
    }

    /** Soy una persona controlada (no la dueña) y el control esta aceptado. */
    public boolean controlledByOther(Instant nowJt) {
        return "MEMBER".equals(role) && "ACCEPTED".equals(controlStatus)
                && (controlUntil == null || nowJt.isBefore(controlUntil));
    }
}
