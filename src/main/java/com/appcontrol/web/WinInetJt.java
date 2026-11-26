package com.appcontrol.web;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/** Aviso a Windows/navegadores de que cambio la configuracion de proxy (para que la relean al momento). */
interface WinInetJt extends Library {

    int OPTION_SETTINGS_CHANGED_JT = 39;
    int OPTION_REFRESH_JT = 37;

    boolean InternetSetOptionW(Pointer handleJt, int optionJt, Pointer bufferJt, int lengthJt);

    /** Pide a Windows que vuelva a leer la configuracion de proxy. No hace nada si falla. */
    static void refreshJt() {
        try {
            WinInetJt libJt = Native.load("wininet", WinInetJt.class);
            libJt.InternetSetOptionW(null, OPTION_SETTINGS_CHANGED_JT, null, 0);
            libJt.InternetSetOptionW(null, OPTION_REFRESH_JT, null, 0);
        } catch (Throwable exJt) {
            // sin wininet (no es Windows): nada que avisar
        }
    }
}
