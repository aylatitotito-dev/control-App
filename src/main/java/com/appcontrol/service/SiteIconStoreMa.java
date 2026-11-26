package com.appcontrol.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Carpeta de iconos de sitios (un PNG por dominio) en los datos de AppControl. */
public final class SiteIconStoreMa {

    private SiteIconStoreMa() {
    }

    public static Path dirMa() {
        String appDataMa = System.getenv("APPDATA");
        return Path.of(appDataMa != null ? appDataMa : System.getProperty("user.home"), "AppControl", "icons");
    }

    public static Path fileForMa(String hostMa) {
        return dirMa().resolve(hostMa.toLowerCase(Locale.ROOT) + ".png");
    }

    public static boolean hasMa(String hostMa) {
        return Files.isRegularFile(fileForMa(hostMa));
    }

    /** Un PNG de verdad empieza por la firma 89 50 4E 47. */
    public static boolean isPngMa(byte[] bytesMa) {
        return bytesMa != null && bytesMa.length > 8 && (bytesMa[0] & 0xFF) == 0x89 && bytesMa[1] == 'P'
                && bytesMa[2] == 'N' && bytesMa[3] == 'G';
    }
}
