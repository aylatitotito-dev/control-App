package com.appcontrol.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.model.AppSettingMa;
import com.appcontrol.repository.AppSettingRepositoryMa;

/** Ajustes simples (clave/valor) guardados en este equipo. */
@Service
public class AppSettingsServiceMa {

    private final AppSettingRepositoryMa repoJt;

    public AppSettingsServiceMa(AppSettingRepositoryMa repoJt) {
        this.repoJt = repoJt;
    }

    @Transactional(readOnly = true)
    public String get(String keyJt, String defaultJt) {
        return repoJt.findById(keyJt).map(AppSettingMa::getValue).orElse(defaultJt);
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String keyJt, boolean defaultJt) {
        return Boolean.parseBoolean(get(keyJt, String.valueOf(defaultJt)));
    }

    @Transactional
    public void set(String keyJt, String valueJt) {
        AppSettingMa settingJt = repoJt.findById(keyJt).orElseGet(() -> new AppSettingMa(keyJt, valueJt));
        settingJt.setValue(valueJt);
        repoJt.save(settingJt);
    }
}
