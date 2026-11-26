package com.appcontrol.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.model.ActivityEventJt;
import com.appcontrol.repository.ActivityEventRepositoryMa;

/** Historial de actividad: sesiones de aplicaciones, visitas a sitios y bloqueos, cada uno con su hora. */
@Service
public class ActivityServiceMa {

    /** Si pasan mas de 60 s sin ver la misma aplicacion al frente, la sesion termino. */
    public static final Duration APP_GAP_JT = Duration.ofSeconds(60);
    /** Visitas al mismo dominio con menos de 10 min entre una y otra forman una sola entrada. */
    public static final Duration SITE_GAP_JT = Duration.ofMinutes(10);
    /** Intentos de bloqueo repetidos dentro de 60 s se cuentan como uno solo. */
    public static final Duration BLOCK_GAP_JT = Duration.ofSeconds(60);
    public static final int KEEP_DAYS_JT = 90;

    private final ActivityEventRepositoryMa repoJt;

    public ActivityServiceMa(ActivityEventRepositoryMa repoJt) {
        this.repoJt = repoJt;
    }

    /**
     * Llamar cada vez que se ve una aplicacion al frente. Si es la misma que la ultima sesion y no paso mucho,
     * la alarga; si cambio de aplicacion o hubo una pausa, abre una sesion nueva.
     */
    @Transactional
    public void touchAppJt(String appKeyJt, String displayJt, Instant nowJt) {
        Optional<ActivityEventJt> lastJt = repoJt.findTopByKindOrderByEndedAtDesc(ActivityEventJt.APP);
        if (lastJt.isPresent() && lastJt.get().getTargetKey().equals(appKeyJt)
                && Duration.between(lastJt.get().getEndedAt(), nowJt).compareTo(APP_GAP_JT) <= 0) {
            lastJt.get().extendTo(nowJt, false);
            repoJt.save(lastJt.get());
        } else {
            repoJt.save(new ActivityEventJt(ActivityEventJt.APP, appKeyJt, displayJt, nowJt));
        }
    }

    /**
     * Llamar cada vez que se ve un sitio web en la pestana al frente. Si el sitio ya tenia una entrada reciente
     * (menos de 10 min), la alarga; si no, abre una nueva. Asi volver a una pestana que ya estaba abierta queda anotado.
     */
    @Transactional
    public void touchSiteJt(String domainJt, Instant nowJt) {
        Optional<ActivityEventJt> lastJt = repoJt.findTopByKindAndTargetKeyOrderByEndedAtDesc(ActivityEventJt.SITE,
                domainJt);
        if (lastJt.isPresent() && Duration.between(lastJt.get().getEndedAt(), nowJt).compareTo(SITE_GAP_JT) <= 0) {
            lastJt.get().extendTo(nowJt, false);
            repoJt.save(lastJt.get());
        } else {
            repoJt.save(new ActivityEventJt(ActivityEventJt.SITE, domainJt, domainJt, nowJt));
        }
    }

    /**
     * Registra visitas a sitios (dominio -> horas de cada visita). Agrupa las seguidas en una entrada y
     * no repite lo que ya estaba guardado si se vuelve a leer el mismo historial.
     */
    @Transactional
    public void recordSiteVisitsJt(Map<String, List<Instant>> visitsJt) {
        for (Map.Entry<String, List<Instant>> entryJt : visitsJt.entrySet()) {
            String domainJt = entryJt.getKey();
            List<Instant> sortedJt = new ArrayList<>(entryJt.getValue());
            sortedJt.sort(Instant::compareTo);
            ActivityEventJt currentJt = repoJt.findTopByKindAndTargetKeyOrderByEndedAtDesc(ActivityEventJt.SITE,
                    domainJt).orElse(null);
            boolean touchedJt = false;
            for (Instant visitJt : sortedJt) {
                if (currentJt != null && !visitJt.isAfter(currentJt.getEndedAt())) {
                    continue;   // ya estaba guardada (se leyo dos veces el mismo historial)
                }
                if (currentJt != null && Duration.between(currentJt.getEndedAt(), visitJt).compareTo(SITE_GAP_JT) <= 0) {
                    currentJt.extendTo(visitJt, true);
                } else {
                    if (currentJt != null && touchedJt) {
                        repoJt.save(currentJt);
                    }
                    currentJt = new ActivityEventJt(ActivityEventJt.SITE, domainJt, domainJt, visitJt);
                }
                touchedJt = true;
            }
            if (currentJt != null && touchedJt) {
                repoJt.save(currentJt);
            }
        }
    }

    /** Un bloqueo (kind BLOCK_APP o BLOCK_SITE). Los intentos seguidos sobre lo mismo se suman en una entrada. */
    @Transactional
    public void recordBlockJt(String kindJt, String keyJt, String displayJt, Instant nowJt) {
        Optional<ActivityEventJt> lastJt = repoJt.findTopByKindAndTargetKeyOrderByEndedAtDesc(kindJt, keyJt);
        if (lastJt.isPresent() && Duration.between(lastJt.get().getEndedAt(), nowJt).compareTo(BLOCK_GAP_JT) <= 0) {
            lastJt.get().extendTo(nowJt, true);
            repoJt.save(lastJt.get());
        } else {
            repoJt.save(new ActivityEventJt(kindJt, keyJt, displayJt, nowJt));
        }
    }

    /** Eventos que empezaron en [from, to), del mas reciente al mas antiguo. */
    @Transactional(readOnly = true)
    public List<ActivityEventJt> listJt(Instant fromJt, Instant toJt) {
        return repoJt.findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(fromJt, toJt);
    }

    /** Borra lo que tenga mas de 90 dias (una vez al dia). */
    @Scheduled(initialDelay = 60_000, fixedDelay = 24 * 60 * 60 * 1000L)
    @Transactional
    public void purgeOldJt() {
        Instant limitJt = LocalDate.now().minusDays(KEEP_DAYS_JT).atStartOfDay(ZoneId.systemDefault()).toInstant();
        repoJt.deleteByStartedAtBefore(limitJt);
    }
}
