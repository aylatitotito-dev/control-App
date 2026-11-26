package com.appcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.appcontrol.service.InstalledAppsServiceJtMa.InstalledAppJt;
import com.appcontrol.tracker.BrowserHistoryCollectorJt;

class BrowserAndAppsParsingJtTest {

    @Test
    void dominioSeReduceAlPrincipalSinRutaNiPuerto() {
        assertEquals("youtube.com", BrowserHistoryCollectorJt.domainOfJtMa("https://www.youtube.com:443/watch?v=abc#t"));
        assertEquals("ejemplo.com.pe", BrowserHistoryCollectorJt.domainOfJtMa("http://mail.ejemplo.com.pe/inbox"));
        assertEquals("poker.net", BrowserHistoryCollectorJt.domainOfJtMa("https://poker.net"));
    }

    @Test
    void descartaLoQueNoEsUnSitioReal() {
        assertNull(BrowserHistoryCollectorJt.domainOfJtMa("chrome://settings"));
        assertNull(BrowserHistoryCollectorJt.domainOfJtMa("http://localhost:8080/api"));
        assertNull(BrowserHistoryCollectorJt.domainOfJtMa("http://192.168.1.10/panel"));
        assertNull(BrowserHistoryCollectorJt.domainOfJtMa("https://impresora.local/x"));
        assertNull(BrowserHistoryCollectorJt.domainOfJtMa("https://fonts.gstatic.com/s/a.woff"));
        assertNull(BrowserHistoryCollectorJt.domainOfJtMa("no es url"));
        assertNull(BrowserHistoryCollectorJt.domainOfJtMa(null));
    }

    @Test
    void listaDeAplicacionesSeLimpiaYOrdena() {
        List<InstalledAppJt> appsJtMa = InstalledAppsServiceJtMa.parseMa(List.of(
                "S\tSteam\tsteam", "S\tGoogle Chrome\tchrome", "S\tUninstall Steam\tuninstall", "S\tLéeme\treadme",
                "S\tChrome\tchrome", "S\tDesinstalar Foo\tunins000", "basura sin separador", "S\tAccess\tMSACCESS"));
        assertEquals(List.of("Access", "Chrome", "Steam"), appsJtMa.stream().map(InstalledAppJt::name).toList());
    }

    @Test
    void registroAgregaVersionEditorYFecha() {
        List<InstalledAppJt> appsJtMa = InstalledAppsServiceJtMa.parseMa(List.of(
                "S\tGoogle Chrome\tchrome",
                "R\tGoogle Chrome\tchrome\t130.0\tGoogle LLC\t20260818",
                "R\tOtro Programa 2.0\tOtroProg\t2.0\tAcme\t",
                "R\tMicrosoft Visual C++ 2015 Redistributable\t\t14.0\tMicrosoft\t20260101",
                "R\tDocker Desktop\tDocker Desktop Installer\t4.6\tDocker Inc.\t"));
        assertEquals(List.of("Google Chrome", "Otro Programa 2.0"), appsJtMa.stream().map(InstalledAppJt::name).toList());
        InstalledAppJt chromeJtMa = appsJtMa.get(0);
        assertEquals("130.0", chromeJtMa.version());
        assertEquals("Google LLC", chromeJtMa.publisher());
        assertEquals("18/08/2026", chromeJtMa.installDate());
    }

    @Test
    void registroSeConvierteEnLineas() {
        List<String> regJtMa = List.of(
                "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Foo",
                "    DisplayName    REG_SZ    Foo Editor",
                "    DisplayVersion    REG_SZ    1.2.3",
                "    Publisher    REG_SZ    Acme S.A.",
                "    InstallDate    REG_SZ    20260105",
                "    EstimatedSize    REG_DWORD    0x800",
                "    DisplayIcon    REG_SZ    \"C:\\Program Files\\Foo\\foo.exe\",0",
                "",
                "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Sys",
                "    DisplayName    REG_SZ    Componente oculto",
                "    SystemComponent    REG_DWORD    0x1",
                "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Upd",
                "    DisplayName    REG_SZ    Parche de seguridad",
                "    ParentKeyName    REG_SZ    OperatingSystem");
        List<String> outJtMa = InstalledAppsServiceJtMa.parseRegistryMa(regJtMa);
        assertEquals(List.of("R\tFoo Editor\tfoo\t1.2.3\tAcme S.A.\t20260105\t2048\tC:\\Program Files\\Foo\\foo.exe"), outJtMa);
    }

    @Test
    void accesoDirectoLnkDevuelveLaRutaDelPrograma() {
        byte[] lnkJtMa = fakeLnk("C:\\Program Files\\Foo", "\\foo.exe");
        assertEquals("C:\\Program Files\\Foo\\foo.exe", InstalledAppsServiceJtMa.lnkTargetMa(lnkJtMa));
        assertNull(InstalledAppsServiceJtMa.lnkTargetMa(new byte[10]));
    }

    /** Construye un .lnk minimo: cabecera + LinkInfo con ruta local ANSI. */
    private static byte[] fakeLnk(String baseJtMa, String suffixJtMa) {
        byte[] baseBytesJtMa = (baseJtMa + "\0").getBytes(StandardCharsets.US_ASCII);
        byte[] sufBytesJtMa = (suffixJtMa + "\0").getBytes(StandardCharsets.US_ASCII);
        int infoHeaderJtMa = 28;
        int baseOffJtMa = infoHeaderJtMa;
        int sufOffJtMa = baseOffJtMa + baseBytesJtMa.length;
        int infoSizeJtMa = sufOffJtMa + sufBytesJtMa.length;
        ByteBuffer bufJtMa = ByteBuffer.allocate(0x4C + infoSizeJtMa).order(ByteOrder.LITTLE_ENDIAN);
        bufJtMa.putInt(0, 0x4C);
        bufJtMa.putInt(0x14, 0x2); // HasLinkInfo
        int posJtMa = 0x4C;
        bufJtMa.putInt(posJtMa, infoSizeJtMa);
        bufJtMa.putInt(posJtMa + 4, infoHeaderJtMa);
        bufJtMa.putInt(posJtMa + 8, 1); // VolumeIDAndLocalBasePath
        bufJtMa.putInt(posJtMa + 16, baseOffJtMa);
        bufJtMa.putInt(posJtMa + 24, sufOffJtMa);
        bufJtMa.position(posJtMa + baseOffJtMa);
        bufJtMa.put(baseBytesJtMa);
        bufJtMa.put(sufBytesJtMa);
        return bufJtMa.array();
    }
}
