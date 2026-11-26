package com.appcontrol.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lista las aplicaciones instaladas SIN usar PowerShell:
 * - accesos directos (.lnk) del menu Inicio, leidos en Java (nombre visible + programa, el mismo nombre que usa el medidor);
 * - registro de programas instalados, con el comando de Windows "reg query" (version, editor y fecha).
 */
@Service
public class InstalledAppsServiceJtMa {

    /** name = nombre visible ("Google Chrome"), exe = programa sin ".exe" ("chrome"). Version/editor/fecha pueden ser null. */
    public record InstalledAppJt(String name, String exe, String version, String publisher, String installDate,
            Long sizeKb, String path) {

        public InstalledAppJt(String name, String exe) {
            this(name, exe, null, null, null, null, null);
        }

        public InstalledAppJt(String name, String exe, String version, String publisher, String installDate) {
            this(name, exe, version, publisher, installDate, null, null);
        }
    }

    private static final Logger logMa = LoggerFactory.getLogger(InstalledAppsServiceJtMa.class);
    private static final Pattern NOISE_MA = Pattern.compile(
            "(?i).*(uninstall|desinstal|readme|l[eé]eme|\\bhelp\\b|ayuda|licen[sc]|manual|documenta|release notes|"
                    + "website|sitio web|changelog|what's new).*");
    private static final Pattern BAD_EXE_MA = Pattern.compile("(?i)(unins.*|.*install.*|.*setup.*|msiexec|update.*)");
    private static final Pattern REG_VALUE_MA = Pattern.compile("^ {4}(.+?) {4}(REG_[A-Z_]+)(?: {4}(.*))?$");
    private static final String[] UNINSTALL_KEYS_MA = {
        "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall"};

    private volatile List<InstalledAppJt> cacheMa = List.of();

    public List<InstalledAppJt> cached() {
        return cacheMa;
    }

    /** Vuelve a leer las aplicaciones instaladas (llamar fuera del hilo de la interfaz). */
    public List<InstalledAppJt> scanMa() {
        if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
            return cacheMa;
        }
        try {
            List<String> linesMa = new ArrayList<>();
            linesMa.addAll(startMenuLinesMa());
            for (String keyMa : UNINSTALL_KEYS_MA) {
                linesMa.addAll(parseRegistryMa(regQueryMa(keyMa)));
            }
            cacheMa = parseMa(linesMa);
            logMa.info("Aplicaciones instaladas encontradas: {}", cacheMa.size());
        } catch (Exception exMa) {
            logMa.warn("No se pudo listar las aplicaciones instaladas: {}", exMa.toString());
        }
        return cacheMa;
    }

    // ---------------------------------------------------------------- menu Inicio (.lnk)

    private static List<String> startMenuLinesMa() {
        List<String> outMa = new ArrayList<>();
        List<Path> rootsMa = new ArrayList<>();
        String programDataMa = System.getenv("ProgramData");
        String appDataMa = System.getenv("APPDATA");
        if (programDataMa != null) {
            rootsMa.add(Path.of(programDataMa, "Microsoft", "Windows", "Start Menu", "Programs"));
        }
        if (appDataMa != null) {
            rootsMa.add(Path.of(appDataMa, "Microsoft", "Windows", "Start Menu", "Programs"));
        }
        for (Path rootMa : rootsMa) {
            if (!Files.isDirectory(rootMa)) {
                continue;
            }
            try (Stream<Path> walkMa = Files.walk(rootMa)) {
                walkMa.filter(pMa -> pMa.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lnk"))
                        .forEach(pMa -> {
                            try {
                                String targetMa = lnkTargetMa(Files.readAllBytes(pMa));
                                if (targetMa != null && targetMa.toLowerCase(Locale.ROOT).endsWith(".exe")) {
                                    String nameMa = pMa.getFileName().toString();
                                    nameMa = nameMa.substring(0, nameMa.length() - 4);
                                    outMa.add("S\t" + nameMa + "\t" + baseNameNoExt(targetMa) + "\t" + targetMa);
                                }
                            } catch (Exception exMa) {
                                // acceso directo ilegible: se omite
                            }
                        });
            } catch (IOException exMa) {
                logMa.debug("No se pudo recorrer {}: {}", rootMa, exMa.toString());
            }
        }
        return outMa;
    }

    private static String baseNameNoExt(String pathMa) {
        String nameMa = pathMa.substring(Math.max(pathMa.lastIndexOf('\\'), pathMa.lastIndexOf('/')) + 1);
        return nameMa.toLowerCase(Locale.ROOT).endsWith(".exe") ? nameMa.substring(0, nameMa.length() - 4) : nameMa;
    }

    /**
     * Ruta de destino de un acceso directo .lnk (formato Shell Link): lee LinkInfo -> LocalBasePath + sufijo.
     * Devuelve null si el acceso directo no guarda ruta local (por ejemplo, apps de la Store).
     */
    public static String lnkTargetMa(byte[] dataMa) {
        if (dataMa.length < 0x4C) {
            return null;
        }
        ByteBuffer bbMa = ByteBuffer.wrap(dataMa).order(ByteOrder.LITTLE_ENDIAN);
        if (bbMa.getInt(0) != 0x4C) {
            return null;
        }
        int flagsMa = bbMa.getInt(0x14);
        int posMa = 0x4C;
        if ((flagsMa & 0x1) != 0) { // HasLinkTargetIDList
            if (posMa + 2 > dataMa.length) {
                return null;
            }
            posMa += 2 + (bbMa.getShort(posMa) & 0xFFFF);
        }
        if ((flagsMa & 0x2) == 0 || posMa + 28 > dataMa.length) { // HasLinkInfo
            return null;
        }
        int infoMa = posMa;
        int headerSizeMa = bbMa.getInt(infoMa + 4);
        int infoFlagsMa = bbMa.getInt(infoMa + 8);
        if ((infoFlagsMa & 0x1) == 0) { // VolumeIDAndLocalBasePath
            return null;
        }
        int baseOffMa = bbMa.getInt(infoMa + 16);
        int suffixOffMa = bbMa.getInt(infoMa + 24);
        String baseMa;
        String suffixMa;
        if (headerSizeMa >= 0x24 && infoMa + 36 <= dataMa.length) {
            baseMa = utf16At(dataMa, infoMa + bbMa.getInt(infoMa + 28));
            suffixMa = utf16At(dataMa, infoMa + bbMa.getInt(infoMa + 32));
        } else {
            Charset ansiMa = Charset.forName("windows-1252");
            baseMa = ansiAt(dataMa, infoMa + baseOffMa, ansiMa);
            suffixMa = ansiAt(dataMa, infoMa + suffixOffMa, ansiMa);
        }
        if (baseMa == null || baseMa.isEmpty()) {
            return null;
        }
        return baseMa + (suffixMa == null ? "" : suffixMa);
    }

    private static String ansiAt(byte[] dataMa, int offMa, Charset csMa) {
        if (offMa <= 0 || offMa >= dataMa.length) {
            return null;
        }
        int endMa = offMa;
        while (endMa < dataMa.length && dataMa[endMa] != 0) {
            endMa++;
        }
        return new String(dataMa, offMa, endMa - offMa, csMa);
    }

    private static String utf16At(byte[] dataMa, int offMa) {
        if (offMa <= 0 || offMa + 1 >= dataMa.length) {
            return null;
        }
        int endMa = offMa;
        while (endMa + 1 < dataMa.length && !(dataMa[endMa] == 0 && dataMa[endMa + 1] == 0)) {
            endMa += 2;
        }
        return new String(dataMa, offMa, endMa - offMa, StandardCharsets.UTF_16LE);
    }

    // ---------------------------------------------------------------- registro (reg query)

    private static List<String> regQueryMa(String keyMa) throws IOException {
        // chcp 65001 para que "reg" escriba en UTF-8 y se conserven tildes y enies.
        Process procMa = new ProcessBuilder("cmd", "/c", "chcp 65001 >nul & reg query \"" + keyMa + "\" /s")
                .redirectErrorStream(true).start();
        List<String> linesMa = new ArrayList<>();
        try (BufferedReader rMa = new BufferedReader(
                new InputStreamReader(procMa.getInputStream(), StandardCharsets.UTF_8))) {
            String lineMa;
            while ((lineMa = rMa.readLine()) != null) {
                linesMa.add(lineMa);
            }
        }
        return linesMa;
    }

    /**
     * Convierte la salida de "reg query ... /s" en lineas
     * "R\tNombre\tprograma\tversion\teditor\tfecha", omitiendo componentes del sistema y actualizaciones.
     */
    public static List<String> parseRegistryMa(List<String> regLinesMa) {
        List<String> outMa = new ArrayList<>();
        Map<String, String> curMa = new HashMap<>();
        for (String lineMa : regLinesMa) {
            if (lineMa.startsWith("HKEY_") || lineMa.startsWith("HKLM") || lineMa.startsWith("HKCU")) {
                flushRegistryMa(curMa, outMa);
                curMa = new HashMap<>();
                continue;
            }
            Matcher mMa = REG_VALUE_MA.matcher(lineMa);
            if (mMa.matches()) {
                curMa.put(mMa.group(1), mMa.group(3) == null ? "" : mMa.group(3).trim());
            }
        }
        flushRegistryMa(curMa, outMa);
        return outMa;
    }

    private static void flushRegistryMa(Map<String, String> valuesMa, List<String> outMa) {
        String nameMa = valuesMa.get("DisplayName");
        if (nameMa == null || nameMa.isBlank() || "0x1".equals(valuesMa.get("SystemComponent"))
                || valuesMa.containsKey("ParentKeyName")) {
            return;
        }
        String exeMa = "";
        String exePathMa = "";
        String iconMa = valuesMa.get("DisplayIcon");
        if (iconMa != null) {
            String pathMa = iconMa.split(",")[0].trim().replace("\"", "");
            if (pathMa.toLowerCase(Locale.ROOT).endsWith(".exe")) {
                exeMa = baseNameNoExt(pathMa);
                exePathMa = pathMa;
            }
        }
        if (exeMa.isEmpty()) {
            exePathMa = guessExeInFolderMa(valuesMa.get("InstallLocation"), nameMa);
            exeMa = exePathMa.isEmpty() ? "" : baseNameNoExt(exePathMa);
        }
        String sizeMa = "";
        String rawSizeMa = valuesMa.get("EstimatedSize");
        if (rawSizeMa != null) {
            try {
                sizeMa = String.valueOf(Long.decode(rawSizeMa));
            } catch (NumberFormatException exMa) {
                sizeMa = "";
            }
        }
        outMa.add("R\t" + nameMa + "\t" + exeMa + "\t" + valuesMa.getOrDefault("DisplayVersion", "") + "\t"
                + valuesMa.getOrDefault("Publisher", "") + "\t" + valuesMa.getOrDefault("InstallDate", "") + "\t"
                + sizeMa + "\t" + exePathMa);
    }

    private static final Pattern HELPER_EXE_MA = Pattern.compile(
            "(?i)(unins.*|.*(setup|install|update|upgrade|crash|helper|report|elevat|redist|service|agent|"
                    + "notification).*|msiexec)");

    /** Si la carpeta de instalacion tiene un unico programa "principal", devuelve su ruta completa (o ""). */
    static String guessExeInFolderMa(String folderMa, String displayNameMa) {
        if (folderMa == null || folderMa.isBlank()) {
            return "";
        }
        try {
            Path dirMa = Path.of(folderMa.replace("\"", "").trim());
            if (!Files.isDirectory(dirMa)) {
                return "";
            }
            List<String> candidatesMa = new ArrayList<>();
            try (Stream<Path> listMa = Files.list(dirMa)) {
                listMa.map(pMa -> pMa.getFileName().toString())
                        .filter(nMa -> nMa.toLowerCase(Locale.ROOT).endsWith(".exe"))
                        .map(nMa -> nMa.substring(0, nMa.length() - 4))
                        .filter(nMa -> !HELPER_EXE_MA.matcher(nMa).matches())
                        .forEach(candidatesMa::add);
            }
            if (candidatesMa.size() == 1) {
                return dirMa.resolve(candidatesMa.get(0) + ".exe").toString();
            }
            String firstWordMa = displayNameMa.split("[\\s_-]+")[0].toLowerCase(Locale.ROOT);
            List<String> sameMa = candidatesMa.stream()
                    .filter(nMa -> nMa.toLowerCase(Locale.ROOT).startsWith(firstWordMa)).toList();
            return sameMa.size() == 1 ? dirMa.resolve(sameMa.get(0) + ".exe").toString() : "";
        } catch (Exception exMa) {
            return "";
        }
    }

    // ---------------------------------------------------------------- union de fuentes

    private static String field(String[] partsMa, int idxMa) {
        if (idxMa >= partsMa.length) {
            return null;
        }
        String vMa = partsMa[idxMa].trim();
        return vMa.isEmpty() ? null : vMa;
    }

    /** "20260818" -> "18/08/2026"; otro formato se devuelve igual. */
    static String prettyDate(String rawMa) {
        if (rawMa != null && rawMa.matches("\\d{8}")) {
            return rawMa.substring(6, 8) + "/" + rawMa.substring(4, 6) + "/" + rawMa.substring(0, 4);
        }
        return rawMa;
    }

    /**
     * Une las dos fuentes. Lineas "S\tNombre\tprograma" (menu Inicio) y
     * "R\tNombre\tprograma\tversion\teditor\tfecha" (registro). Devuelve lista sin duplicados, ordenada por nombre.
     */
    public static List<InstalledAppJt> parseMa(List<String> linesMa) {
        Map<String, InstalledAppJt> byExeMa = new LinkedHashMap<>();
        List<String[]> registryMa = new ArrayList<>();
        for (String rawMa : linesMa) {
            String[] partsMa = rawMa.split("\t", -1);
            if (partsMa.length < 3) {
                continue;
            }
            String nameMa = field(partsMa, 1);
            String exeMa = field(partsMa, 2);
            if ("R".equals(partsMa[0])) {
                if (nameMa != null && !NOISE_MA.matcher(nameMa).matches()) {
                    registryMa.add(partsMa);
                }
                continue;
            }
            if (!"S".equals(partsMa[0]) || nameMa == null || exeMa == null || NOISE_MA.matcher(nameMa).matches()
                    || BAD_EXE_MA.matcher(exeMa).matches()) {
                continue;
            }
            byExeMa.merge(exeMa.toLowerCase(Locale.ROOT),
                    new InstalledAppJt(nameMa, exeMa, null, null, null, null, field(partsMa, 3)),
                    (oldMa, newMa) -> oldMa.name().length() <= newMa.name().length() ? oldMa : newMa);
        }

        for (String[] regMa : registryMa) {
            String nameMa = field(regMa, 1);
            String exeMa = field(regMa, 2);
            String versionMa = field(regMa, 3);
            String publisherMa = field(regMa, 4);
            String dateMa = prettyDate(field(regMa, 5));
            Long sizeKbMa = null;
            try {
                String rawSizeMa = field(regMa, 6);
                sizeKbMa = rawSizeMa == null ? null : Long.valueOf(rawSizeMa);
            } catch (NumberFormatException exMa) {
                sizeKbMa = null;
            }
            String pathMa = field(regMa, 7);

            String matchKeyMa = null;
            if (exeMa != null && byExeMa.containsKey(exeMa.toLowerCase(Locale.ROOT))) {
                matchKeyMa = exeMa.toLowerCase(Locale.ROOT);
            } else {
                String lowerMa = nameMa.toLowerCase(Locale.ROOT);
                for (Map.Entry<String, InstalledAppJt> eMa : byExeMa.entrySet()) {
                    String sMa = eMa.getValue().name().toLowerCase(Locale.ROOT);
                    if (sMa.equals(lowerMa) || (sMa.length() >= 4 && lowerMa.contains(sMa))
                            || (lowerMa.length() >= 4 && sMa.contains(lowerMa))) {
                        matchKeyMa = eMa.getKey();
                        break;
                    }
                }
            }
            if (matchKeyMa != null) {
                InstalledAppJt baseMa = byExeMa.get(matchKeyMa);
                byExeMa.put(matchKeyMa, new InstalledAppJt(baseMa.name(), baseMa.exe(), versionMa, publisherMa, dateMa,
                        sizeKbMa, baseMa.path() != null ? baseMa.path() : pathMa));
            } else if (exeMa != null && !BAD_EXE_MA.matcher(exeMa).matches()) {
                byExeMa.putIfAbsent(exeMa.toLowerCase(Locale.ROOT),
                        new InstalledAppJt(nameMa, exeMa, versionMa, publisherMa, dateMa, sizeKbMa, pathMa));
            }
        }
        List<InstalledAppJt> outMa = new ArrayList<>(byExeMa.values());
        outMa.sort(Comparator.comparing(aMa -> aMa.name().toLowerCase(Locale.ROOT)));
        return outMa;
    }
}
