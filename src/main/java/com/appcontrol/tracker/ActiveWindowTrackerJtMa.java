package com.appcontrol.tracker;

import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.AppSettingsServiceMa;
import com.appcontrol.service.UsageServiceJt;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

/**
 * Registra cada pocos segundos cual es la aplicacion en primer plano (solo su nombre de programa).
 * Si esa aplicacion es un NAVEGADOR (y el usuario no lo desactivo en Ajustes), lee en ese instante el titulo de su
 * ventana unicamente para deducir que sitio esta al frente comparandolo con el historial del navegador;
 * el titulo no se guarda ni se escribe en ningun registro. En cualquier otra aplicacion no se lee ningun titulo.
 * No lee teclas ni contenido. Si no hay actividad de teclado/raton por un rato, no suma tiempo.
 */
@Component
@ConditionalOnProperty(name = "appcontrol.tracker.enabled", havingValue = "true", matchIfMissing = true)
public class ActiveWindowTrackerJtMa {

    public static final String SITE_FOCUS_SETTING_MA = "site_focus";

    private static final Logger logMa = LoggerFactory.getLogger(ActiveWindowTrackerJtMa.class);
    private static final long IDLE_LIMIT_MS_MA = 120_000;
    private static final long SETTING_CACHE_MS_MA = 30_000;

    private final UsageServiceJt usageMa;
    private final ActivityServiceMa activityMa;
    private final SiteTitleIndexJt titlesMa;
    private final AppSettingsServiceMa settingsMa;
    private final boolean windowsMa = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    private final long stepSecondsMa;
    private boolean siteFocusMa = true;
    private long settingReadAtMa;

    public ActiveWindowTrackerJtMa(UsageServiceJt usageMa, ActivityServiceMa activityMa, SiteTitleIndexJt titlesMa,
            AppSettingsServiceMa settingsMa,
            @org.springframework.beans.factory.annotation.Value("${appcontrol.tracker.interval-ms:5000}") long intervalMsMa) {
        this.usageMa = usageMa;
        this.activityMa = activityMa;
        this.titlesMa = titlesMa;
        this.settingsMa = settingsMa;
        this.stepSecondsMa = Math.max(1, intervalMsMa / 1000);
    }

    @Scheduled(fixedDelayString = "${appcontrol.tracker.interval-ms:5000}")
    public void sampleMa() {
        if (!windowsMa) {
            return;
        }
        try {
            if (idleMsMa() > IDLE_LIMIT_MS_MA) {
                return;
            }
            WinDef.HWND hwndMa = User32.INSTANCE.GetForegroundWindow();
            String appMa = hwndMa == null ? null : exeNameMa(hwndMa);
            if (appMa == null || appMa.equalsIgnoreCase("AppControl")) {
                return;
            }
            Instant nowMa = Instant.now();
            String keyMa = appMa.toLowerCase(Locale.ROOT);
            usageMa.recordJt(appMa, stepSecondsMa);
            activityMa.touchAppJt(keyMa, appMa, nowMa);

            // Solo en navegadores: que sitio esta al frente (volver a una pestana ya abierta no crea una "visita")
            if (SiteTitleIndexJt.isBrowserJt(keyMa) && siteFocusEnabledMa()) {
                String domainMa = titlesMa.domainForJt(windowTitleMa(hwndMa));
                if (domainMa != null) {
                    activityMa.touchSiteJt(domainMa, nowMa);
                }
            }
        } catch (Exception | LinkageError exMa) {
            logMa.warn("No se pudo leer la aplicación activa: {}", exMa.toString());
        }
    }

    /** Lee el ajuste (como mucho cada 30 s, para no consultar la base en cada muestra). */
    private boolean siteFocusEnabledMa() {
        long nowMsMa = System.currentTimeMillis();
        if (nowMsMa - settingReadAtMa > SETTING_CACHE_MS_MA) {
            siteFocusMa = settingsMa.getBoolean(SITE_FOCUS_SETTING_MA, true);
            settingReadAtMa = nowMsMa;
        }
        return siteFocusMa;
    }

    private static long idleMsMa() {
        WinUser.LASTINPUTINFO infoMa = new WinUser.LASTINPUTINFO();
        if (!User32.INSTANCE.GetLastInputInfo(infoMa)) {
            return 0;
        }
        long nowMa = Kernel32.INSTANCE.GetTickCount() & 0xFFFFFFFFL;
        long lastMa = infoMa.dwTime & 0xFFFFFFFFL;
        return (nowMa - lastMa) & 0xFFFFFFFFL;
    }

    /** Titulo de la ventana; solo se llama cuando el programa al frente es un navegador. */
    private static String windowTitleMa(WinDef.HWND hwndMa) {
        char[] bufMa = new char[512];
        int lenMa = User32.INSTANCE.GetWindowText(hwndMa, bufMa, bufMa.length);
        return lenMa > 0 ? new String(bufMa, 0, lenMa) : null;
    }

    private static String exeNameMa(WinDef.HWND hwndMa) {
        IntByReference pidMa = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwndMa, pidMa);
        WinNT.HANDLE handleMa = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false,
                pidMa.getValue());
        if (handleMa == null) {
            return null;
        }
        try {
            char[] bufMa = new char[1024];
            IntByReference sizeMa = new IntByReference(bufMa.length);
            if (!Kernel32.INSTANCE.QueryFullProcessImageName(handleMa, 0, bufMa, sizeMa)) {
                return null;
            }
            String pathMa = new String(bufMa, 0, sizeMa.getValue());
            String nameMa = pathMa.substring(pathMa.lastIndexOf('\\') + 1);
            return nameMa.toLowerCase().endsWith(".exe") ? nameMa.substring(0, nameMa.length() - 4) : nameMa;
        } finally {
            Kernel32.INSTANCE.CloseHandle(handleMa);
        }
    }
}
