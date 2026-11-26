package com.appcontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ajuste simple clave/valor guardado en este equipo (por ejemplo, el hash del PIN). */
@Entity
@Table(name = "app_setting")
public class AppSettingMa {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 512)
    private String value;

    protected AppSettingMa() {
    }

    public AppSettingMa(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String valueJt) {
        this.value = valueJt;
    }
}
