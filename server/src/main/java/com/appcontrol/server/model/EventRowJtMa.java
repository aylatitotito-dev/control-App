package com.appcontrol.server.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Un evento del historial (sesion de app, visita a un sitio o bloqueo). Nunca URL completa ni titulos. */
@Entity
@Table(name = "device_event", uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "client_id"}))
public class EventRowJtMa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** Id del evento en la base local del equipo; sirve para no duplicarlo si se sube dos veces. */
    @Column(name = "client_id", nullable = false)
    private long clientId;

    @Column(name = "event_type", nullable = false, length = 12)
    private String type;

    @Column(name = "target_key", nullable = false, length = 255)
    private String targetKey;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "hits", nullable = false)
    private int hits;

    protected EventRowJtMa() {
    }

    public EventRowJtMa(Long deviceId, long clientId) {
        this.deviceId = deviceId;
        this.clientId = clientId;
    }

    public void update(String type, String targetKey, String displayName, Instant startedAt, Instant endedAt,
            int hits) {
        this.type = type;
        this.targetKey = targetKey;
        this.displayName = displayName;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.hits = hits;
    }

    public long getClientId() {
        return clientId;
    }

    public String getType() {
        return type;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public int getHits() {
        return hits;
    }
}
