package com.appcontrol.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProfileGuardJtMaTest {

    private static final String LOCAL_URL_MA = "jdbc:mysql://localhost:3306/appcontrol";
    private static final String AZURE_URL_MA = "jdbc:mysql://srv.mysql.database.azure.com:3306/appcontrol";

    @Test
    void localConLocalhostOk() {
        assertDoesNotThrow(() -> ProfileGuardJt.validateJtMa(List.of("local"), LOCAL_URL_MA));
    }

    @Test
    void azureConHostRemotoOk() {
        assertDoesNotThrow(() -> ProfileGuardJt.validateJtMa(List.of("azure"), AZURE_URL_MA));
    }

    @Test
    void localConHostRemotoFalla() {
        assertThrows(IllegalStateException.class, () -> ProfileGuardJt.validateJtMa(List.of("local"), AZURE_URL_MA));
    }

    @Test
    void azureConLocalhostFalla() {
        assertThrows(IllegalStateException.class, () -> ProfileGuardJt.validateJtMa(List.of("azure"), LOCAL_URL_MA));
    }

    @Test
    void sinPerfilFalla() {
        assertThrows(IllegalStateException.class, () -> ProfileGuardJt.validateJtMa(List.of(), LOCAL_URL_MA));
    }

    private static final String H2_URL_MA = "jdbc:h2:file:C:/Users/x/AppControl/data/appcontrol;MODE=MySQL";

    @Test
    void desktopConH2ArchivoOk() {
        assertDoesNotThrow(() -> ProfileGuardJt.validateJtMa(List.of("desktop"), H2_URL_MA));
    }

    @Test
    void desktopConMysqlFalla() {
        assertThrows(IllegalStateException.class, () -> ProfileGuardJt.validateJtMa(List.of("desktop"), LOCAL_URL_MA));
    }

    @Test
    void azureConH2Falla() {
        assertThrows(IllegalStateException.class, () -> ProfileGuardJt.validateJtMa(List.of("azure"), H2_URL_MA));
    }
}
