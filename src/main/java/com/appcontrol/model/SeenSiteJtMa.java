package com.appcontrol.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Dominio visto en el historial de un navegador. Solo dominio y fechas: nunca la direccion completa. */
@Entity
@Table(name = "seen_site")
public class SeenSiteJtMa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 253)
    private String host;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    protected SeenSiteJtMa() {
    }

    public SeenSiteJtMa(String host, Instant seen) {
        this.host = host;
        this.firstSeen = seen;
        this.lastSeen = seen;
    }

    public void seenAt(Instant seenJt) {
        if (seenJt.isAfter(lastSeen)) {
            lastSeen = seenJt;
        }
        if (seenJt.isBefore(firstSeen)) {
            firstSeen = seenJt;
        }
    }

    public Long getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }
}
