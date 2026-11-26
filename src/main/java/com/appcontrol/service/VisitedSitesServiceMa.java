package com.appcontrol.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.model.SeenSiteJtMa;
import com.appcontrol.repository.SeenSiteRepositoryJtMa;

/** Guarda los dominios que el usuario ha visitado (solo dominio y fecha). */
@Service
public class VisitedSitesServiceMa {

    private final SeenSiteRepositoryJtMa repoJt;

    public VisitedSitesServiceMa(SeenSiteRepositoryJtMa repoJt) {
        this.repoJt = repoJt;
    }

    @Transactional
    public void recordJt(Map<String, Instant> seenJt) {
        for (Map.Entry<String, Instant> entryJt : seenJt.entrySet()) {
            SeenSiteJtMa siteJt = repoJt.findByHost(entryJt.getKey())
                    .orElseGet(() -> new SeenSiteJtMa(entryJt.getKey(), entryJt.getValue()));
            siteJt.seenAt(entryJt.getValue());
            repoJt.save(siteJt);
        }
    }

    @Transactional(readOnly = true)
    public List<SeenSiteJtMa> list() {
        return repoJt.findAllByOrderByLastSeenDesc();
    }
}
