package com.appcontrol.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Programa instalado en el equipo (para elegir cual bloquear desde el grupo). */
@Entity
@Table(name = "device_app")
public class InstalledRowMa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "app_key", nullable = false, length = 255)
    private String appKey;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    protected InstalledRowMa() {
    }

    public InstalledRowMa(Long deviceId, String appKey, String displayName) {
        this.deviceId = deviceId;
        this.appKey = appKey;
        this.displayName = displayName;
    }

    public String getAppKey() {
        return appKey;
    }

    public String getDisplayName() {
        return displayName;
    }
}
