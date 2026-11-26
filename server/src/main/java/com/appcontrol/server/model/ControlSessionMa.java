package com.appcontrol.server.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un tramo de tiempo en que una persona estuvo bajo control (desde que aceptó hasta que terminó). Sirve para que
 * quien controló pueda seguir viendo el historial de ESE tiempo cuando el control ya terminó, y nada más.
 */
@Entity
@Table(name = "control_session")
public class ControlSessionMa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** null mientras el control sigue en marcha. */
    @Column(name = "ended_at")
    private Instant endedAt;

    protected ControlSessionMa() {
    }

    public ControlSessionMa(Long memberId, Instant startedAt) {
        this.memberId = memberId;
        this.startedAt = startedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void end(Instant endJt) {
        this.endedAt = endJt;
    }
}
