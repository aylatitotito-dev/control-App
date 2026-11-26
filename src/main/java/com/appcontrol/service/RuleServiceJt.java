package com.appcontrol.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.RuleStateJt;
import com.appcontrol.repository.BlockRuleRepositoryJt;

/** Crea, cambia, pausa y borra reglas de restriccion de aplicaciones y sitios. */
@Service
public class RuleServiceJt {

    /** Programas que jamas se pueden restringir: cerrarlos dejaria el equipo (o AppControl) inutilizable. */
    public static final Set<String> PROTECTED_JTMA = Set.of("java", "javaw", "explorer", "dwm", "csrss", "wininit",
            "winlogon", "services", "lsass", "svchost", "system", "smss", "appcontrol", "taskmgr", "conhost");

    private static final String HOST_REGEX_JTMA =
            "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$";

    private final BlockRuleRepositoryJt repoJtMa;

    public RuleServiceJt(BlockRuleRepositoryJt repoJtMa) {
        this.repoJtMa = repoJtMa;
    }

    /** Extrae solo el dominio de lo que escriba el usuario (sin esquema, puerto, ruta ni "www."). */
    public static String normalizeSiteJtMa(String rawJtMa) {
        String sJtMa = rawJtMa == null ? "" : rawJtMa.trim().toLowerCase(Locale.ROOT);
        sJtMa = sJtMa.replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        int cutJtMa = sJtMa.length();
        for (char chJtMa : new char[] {'/', '?', '#'}) {
            int idxJtMa = sJtMa.indexOf(chJtMa);
            if (idxJtMa >= 0) {
                cutJtMa = Math.min(cutJtMa, idxJtMa);
            }
        }
        sJtMa = sJtMa.substring(0, cutJtMa);
        int atJtMa = sJtMa.lastIndexOf('@');
        if (atJtMa >= 0) {
            sJtMa = sJtMa.substring(atJtMa + 1);
        }
        int colonJtMa = sJtMa.indexOf(':');
        if (colonJtMa >= 0) {
            sJtMa = sJtMa.substring(0, colonJtMa);
        }
        if (sJtMa.startsWith("www.")) {
            sJtMa = sJtMa.substring(4);
        }
        if (!sJtMa.matches(HOST_REGEX_JTMA)) {
            throw new IllegalArgumentException("Dominio no válido. Ejemplo: youtube.com");
        }
        return sJtMa;
    }

    /** Nombre de programa en minusculas y sin ".exe". Lanza error si esta protegido o vacio. */
    public static String normalizeAppJtMa(String rawJtMa) {
        String nameJtMa = rawJtMa == null ? "" : rawJtMa.trim();
        if (nameJtMa.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            nameJtMa = nameJtMa.substring(0, nameJtMa.length() - 4);
        }
        if (nameJtMa.isEmpty()) {
            throw new IllegalArgumentException("Escribe el nombre de la aplicación");
        }
        String keyJtMa = nameJtMa.toLowerCase(Locale.ROOT);
        if (PROTECTED_JTMA.contains(keyJtMa)) {
            throw new IllegalArgumentException("«" + nameJtMa + "» es un programa del sistema o de AppControl y no "
                    + "se puede restringir.");
        }
        return keyJtMa;
    }

    @Transactional(readOnly = true)
    public List<BlockRuleJtMa> listJtMa(String kindJtMa) {
        return repoJtMa.findByKindOrderByDisplayNameAsc(kindJtMa);
    }

    @Transactional(readOnly = true)
    public List<BlockRuleJtMa> listAllJtMa() {
        return repoJtMa.findAllByOrderByDisplayNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<BlockRuleJtMa> findJtMa(String kindJtMa, String keyJtMa) {
        return repoJtMa.findByKindAndTargetKey(kindJtMa, keyJtMa);
    }

    /**
     * Crea o actualiza la regla de un programa o sitio. Si ya existia conserva su estado.
     * Valida: dias (al menos uno) y franja distinta para SCHEDULE; 1 a 1440 minutos para QUOTA
     * Aplicaciones y sitios admiten los tres modos (los sitios se guardan; su aplicacion en el navegador es posterior).
     */
    @Transactional
    public BlockRuleJtMa saveJtMa(String kindJtMa, String rawTargetJtMa, String displayJtMa, RuleModeMa modeJtMa,
            int daysMaskJtMa, int startMinuteJtMa, int endMinuteJtMa, int quotaMinutesJtMa) {
        String keyJtMa;
        String shownJtMa;
        if (BlockRuleJtMa.SITE.equals(kindJtMa)) {
            keyJtMa = normalizeSiteJtMa(rawTargetJtMa);
            shownJtMa = keyJtMa;
        } else {
            keyJtMa = normalizeAppJtMa(rawTargetJtMa);
            shownJtMa = displayJtMa == null || displayJtMa.isBlank() ? keyJtMa : displayJtMa.trim();
        }
        if (modeJtMa == RuleModeMa.SCHEDULE) {
            if ((daysMaskJtMa & BlockRuleJtMa.ALL_DAYS) == 0) {
                throw new IllegalArgumentException("Elige al menos un día para el horario.");
            }
            if (startMinuteJtMa == endMinuteJtMa) {
                throw new IllegalArgumentException("La hora de inicio y la de fin no pueden ser iguales.");
            }
        }
        if (modeJtMa == RuleModeMa.QUOTA && (quotaMinutesJtMa < 1 || quotaMinutesJtMa > 1440)) {
            throw new IllegalArgumentException("La cuota debe estar entre 1 y 1440 minutos al día.");
        }
        String finalKeyJtMa = keyJtMa;
        String finalShownJtMa = shownJtMa;
        BlockRuleJtMa ruleJtMa = repoJtMa.findByKindAndTargetKey(kindJtMa, keyJtMa)
                .orElseGet(() -> new BlockRuleJtMa(kindJtMa, finalKeyJtMa, finalShownJtMa));
        ruleJtMa.configureJt(modeJtMa, daysMaskJtMa & BlockRuleJtMa.ALL_DAYS, startMinuteJtMa, endMinuteJtMa,
                quotaMinutesJtMa);
        return repoJtMa.save(ruleJtMa);
    }

    @Transactional
    public void setStateJtMa(Long idJtMa, RuleStateJt stateJtMa, Instant untilJtMa) {
        repoJtMa.findById(idJtMa).ifPresent(ruleJtMa -> {
            ruleJtMa.setStateJt(stateJtMa, untilJtMa);
            repoJtMa.save(ruleJtMa);
        });
    }

    @Transactional
    public void deleteJtMa(Long idJtMa) {
        repoJtMa.deleteById(idJtMa);
    }

    /** Las pausas que ya vencieron vuelven a estado activo. Devuelve cuantas reactivo. */
    @Transactional
    public int clearExpiredPausesJtMa(Instant nowJtMa) {
        int countJtMa = 0;
        for (BlockRuleJtMa ruleJtMa : repoJtMa.findAll()) {
            if (ruleJtMa.getState() == RuleStateJt.PAUSED
                    && (ruleJtMa.getPausedUntil() == null || !nowJtMa.isBefore(ruleJtMa.getPausedUntil()))) {
                ruleJtMa.setStateJt(RuleStateJt.ACTIVE, null);
                repoJtMa.save(ruleJtMa);
                countJtMa++;
            }
        }
        return countJtMa;
    }
}
