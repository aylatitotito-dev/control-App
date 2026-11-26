package com.appcontrol.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.appcontrol.web.SiteBlockingJtMa;

/** Sirve el script de proxy (solo accesible desde este equipo, el servidor escucha en 127.0.0.1). */
@RestController
public class PacControllerMa {

    private final SiteBlockingJtMa blockingMa;

    public PacControllerMa(SiteBlockingJtMa blockingMa) {
        this.blockingMa = blockingMa;
    }

    @GetMapping("/proxy.pac")
    public ResponseEntity<String> pacMa() {
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/x-ns-proxy-autoconfig"))
                .cacheControl(CacheControl.noStore())
                .body(blockingMa.pacJtMa());
    }
}
