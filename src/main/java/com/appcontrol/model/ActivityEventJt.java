package com.appcontrol.model;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Un momento del historial: una sesion de uso de una aplicacion, un grupo de visitas a un sitio, o un bloqueo.
 * Solo se guarda el nombre del programa o el dominio y las horas; nunca la direccion completa ni el contenido.
 */
@Entity
@Table(name = "activity_event", indexes = @Index(name = "idx_activity_started", columnList = "started_at"))
public class ActivityEventJt {

    public static final String APP = "APP";
    public static final String SITE = "SITE";
    public static final String BLOCK_APP = "BLOCK_APP";
    public static final String BLOCK_SITE = "BLOCK_SITE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "event_kind", nullable = false, length = 12)
    private String kind;

    @Column(name = "target_key", nullable = false, length = 253)
    private String targetKey;

    @Column(name = "display_name", nullable = false, length = 253)
    private String displayName;

    /** Visitas agrupadas (sitios) o intentos (bloqueos). 1 para sesiones de aplicaciones. */
    @Column(name = "hits", nullable = false)
    private int hits = 1;

    protected ActivityEventJt() {
    }

    public ActivityEventJt(String kind, String targetKey, String displayName, Instant startedAt) {
        this.kind = kind;
        this.targetKey = targetKey;
        this.displayName = displayName;
        this.startedAt = startedAt;
        this.endedAt = startedAt;
    }

    /** Extiende el evento hasta "untilJt" y suma una visita/intento si addHitJt. */
    public void extendTo(Instant untilJt, boolean addHitJt) {
        if (untilJt.isAfter(endedAt)) {
            endedAt = untilJt;
        }
        if (addHitJt) {
            hits++;
        }
    }

    public Duration duration() {
        return Duration.between(startedAt, endedAt);
    }

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
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

    public int getHits() {
        return hits;
    }
}
