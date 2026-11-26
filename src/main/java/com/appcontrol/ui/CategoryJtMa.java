package com.appcontrol.ui;

import java.util.Locale;

/** Etiqueta de categoría para las listas del equipo de otra persona (solo orienta; no se guarda ni se envía). */
final class CategoryJtMa {

    private CategoryJtMa() {
    }

    /** Categoría de una aplicación por el nombre de su programa, o null si no se conoce. */
    static String appJtMa(String keyJtMa) {
        String lowerJtMa = keyJtMa == null ? "" : keyJtMa.toLowerCase(Locale.ROOT).replace(".exe", "");
        if (hasJtMa(lowerJtMa, "chrome", "msedge", "firefox", "brave", "opera", "vivaldi", "iexplore")) {
            return "Navegador";
        }
        if (hasJtMa(lowerJtMa, "explorer", "taskmgr", "searchhost", "applicationframehost", "systemsettings",
                "cmd", "windowsterminal", "powershell")) {
            return "Sistema";
        }
        if (hasJtMa(lowerJtMa, "snippingtool", "mspaint", "notepad", "calc", "winrar", "7zfm")) {
            return "Utilidades";
        }
        if (hasJtMa(lowerJtMa, "discord", "whatsapp", "telegram", "teams", "zoom", "slack", "skype", "signal")) {
            return "Mensajería";
        }
        if (hasJtMa(lowerJtMa, "steam", "roblox", "epicgames", "minecraft", "riot", "valorant", "fortnite", "league")) {
            return "Juegos";
        }
        if (hasJtMa(lowerJtMa, "spotify", "vlc", "wmplayer", "netflix", "obs")) {
            return "Multimedia";
        }
        if (hasJtMa(lowerJtMa, "winword", "excel", "powerpnt", "acrobat", "msaccess", "onenote", "outlook")) {
            return "Ofimática";
        }
        if (hasJtMa(lowerJtMa, "code", "devenv", "idea", "pycharm", "java", "cursor", "studio", "git", "node")) {
            return "Desarrollo";
        }
        if (hasJtMa(lowerJtMa, "claude", "chatgpt", "copilot")) {
            return "Productividad / IA";
        }
        return null;
    }

    /** Categoría de un sitio por su dominio, o null si no se conoce. */
    static String siteJtMa(String hostJtMa) {
        String lowerJtMa = hostJtMa == null ? "" : hostJtMa.toLowerCase(Locale.ROOT);
        if (hasJtMa(lowerJtMa, "whatsapp", "telegram", "discord", "messenger", "signal")) {
            return "Mensajería";
        }
        if (hasJtMa(lowerJtMa, "facebook", "instagram", "tiktok", "twitter", "x.com", "snapchat", "reddit",
                "pinterest", "linkedin")) {
            return "Redes Sociales";
        }
        if (hasJtMa(lowerJtMa, "google.", "bing.", "duckduckgo", "yahoo.", "ecosia")) {
            return "Buscador";
        }
        if (hasJtMa(lowerJtMa, "youtube", "netflix", "twitch", "spotify", "disney", "primevideo", "fanpelis",
                "pelisflix")) {
            return "Entretenimiento";
        }
        if (hasJtMa(lowerJtMa, "bet", "casino", "poker", "caliente.", "1xbet")) {
            return "Apuestas";
        }
        if (lowerJtMa.endsWith(".edu") || lowerJtMa.contains(".edu.") || hasJtMa(lowerJtMa, "classroom", "moodle",
                "coursera", "udemy", "khanacademy")) {
            return "Educativo";
        }
        if (hasJtMa(lowerJtMa, "claude.ai", "openai", "chatgpt", "github", "stackoverflow", "notion", "gemini")) {
            return "Productividad / IA";
        }
        if (hasJtMa(lowerJtMa, "microsoft", "azure", "office", "live.com", "outlook")) {
            return "Microsoft";
        }
        return null;
    }

    private static boolean hasJtMa(String textJtMa, String... partsJtMa) {
        for (String partJtMa : partsJtMa) {
            if (textJtMa.contains(partJtMa)) {
                return true;
            }
        }
        return false;
    }
}
