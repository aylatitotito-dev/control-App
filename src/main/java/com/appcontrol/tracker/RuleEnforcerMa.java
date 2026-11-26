package com.appcontrol.tracker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.ActivityEventJt;
import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.NotificationServiceJtMa;
import com.appcontrol.service.RuleLogicJtMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.service.UsageServiceJt;

/**
 * Aplica las reglas de aplicaciones: si una regla activa dice que un programa debe estar cerrado, lo cierra.
 * Primero pide el cierre con educacion (como pulsar la X, via "taskkill"); si a los 10 s sigue abierto, lo fuerza.
 * Nunca toca programas protegidos ni a AppControl. Los sitios web no se aplican aqui (etapa siguiente).
 */
@Component
@ConditionalOnProperty(name = "appcontrol.rules.enabled", havingValue = "true", matchIfMissing = true)
public class RuleEnforcerMa {

    private static final Logger logJtMa = LoggerFactory.getLogger(RuleEnforcerMa.class);
    private static final long REPEAT_NOTICE_MS_JTMA = 60_000;

    private final RuleServiceJt rulesJtMa;
    /** Reglas que vienen de un grupo (opcional: sin nube no hay ninguna). */
    private com.appcontrol.cloud.GroupRulesSourceMa groupRulesJtMa;
    private final UsageServiceJt usageJtMa;
    private final NotificationServiceJtMa noticesJtMa;
    private final ActivityServiceMa activityJtMa;
    private final long forceAfterMsJtMa;
    private final boolean windowsJtMa = System.getProperty("os.name", "").toLowerCase().startsWith("windows");

    /** pid -> cuando se pidio el cierre educado. */
    private final Map<Long, Instant> askedJtMa = new HashMap<>();
    /** clave de regla -> ultimo aviso de bloqueo mostrado. */
    private final Map<String, Instant> noticedJtMa = new HashMap<>();
    /** "clave|fecha" de los avisos de "quedan 5 minutos" ya mostrados hoy. */
    private final Set<String> warnedJtMa = new HashSet<>();

    public RuleEnforcerMa(RuleServiceJt rulesJtMa, UsageServiceJt usageJtMa, NotificationServiceJtMa noticesJtMa,
            ActivityServiceMa activityJtMa,
            @org.springframework.beans.factory.annotation.Value("${appcontrol.rules.force-after-ms:10000}")
            long forceAfterMsJtMa) {
        this.forceAfterMsJtMa = forceAfterMsJtMa;
        this.activityJtMa = activityJtMa;
        this.rulesJtMa = rulesJtMa;
        this.usageJtMa = usageJtMa;
        this.noticesJtMa = noticesJtMa;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setGroupRulesJtMa(com.appcontrol.cloud.GroupRulesSourceMa groupRulesJtMa) {
        this.groupRulesJtMa = groupRulesJtMa;
    }

    @Scheduled(initialDelay = 8_000, fixedDelayString = "${appcontrol.rules.interval-ms:4000}")
    public void enforceJtMa() {
        if (!windowsJtMa) {
            return;
        }
        try {
            Instant nowInstantJtMa = Instant.now();
            LocalDateTime nowJtMa = LocalDateTime.now();
            rulesJtMa.clearExpiredPausesJtMa(nowInstantJtMa);
            List<BlockRuleJtMa> appRulesJtMa = new ArrayList<>(rulesJtMa.listJtMa(BlockRuleJtMa.APP));
            if (groupRulesJtMa != null) {
                appRulesJtMa.addAll(groupRulesJtMa.activeJt(BlockRuleJtMa.APP));
            }
            if (appRulesJtMa.isEmpty()) {
                return;
            }
            Map<String, Long> usedJtMa = usageJtMa.todayByAppJtMa();
            Map<String, List<ProcessHandle>> runningJtMa = null;
            for (BlockRuleJtMa ruleJtMa : appRulesJtMa) {
                String keyJtMa = ruleJtMa.getTargetKey();
                RuleLogicJtMa.StatusMa statusJtMa = RuleLogicJtMa.evaluateMa(ruleJtMa, nowJtMa, nowInstantJtMa,
                        usedJtMa.getOrDefault(keyJtMa, 0L));
                if (!statusJtMa.enforcing()) {
                    continue;
                }
                if (statusJtMa.quotaWarn() && warnedJtMa.add(keyJtMa + "|" + LocalDate.now())) {
                    noticesJtMa.notifyMa("AppControl", "Te quedan " + Math.max(1, (statusJtMa.remainingSeconds() + 59) / 60)
                            + " minutos de " + ruleJtMa.getDisplayName() + " hoy.");
                }
                if (!statusJtMa.blockedNow()) {
                    continue;
                }
                if (runningJtMa == null) {
                    runningJtMa = runningByNameJtMa();
                }
                List<ProcessHandle> handlesJtMa = runningJtMa.get(keyJtMa);
                if (handlesJtMa == null || handlesJtMa.isEmpty()) {
                    continue;
                }
                for (ProcessHandle handleJtMa : handlesJtMa) {
                    if (closeJtMa(handleJtMa)) {
                        activityJtMa.recordBlockJt(ActivityEventJt.BLOCK_APP, keyJtMa, ruleJtMa.getDisplayName(),
                                nowInstantJtMa);
                    }
                }
                Instant lastJtMa = noticedJtMa.get(keyJtMa);
                if (lastJtMa == null || nowInstantJtMa.toEpochMilli() - lastJtMa.toEpochMilli() > REPEAT_NOTICE_MS_JTMA) {
                    noticedJtMa.put(keyJtMa, nowInstantJtMa);
                    noticesJtMa.notifyMa("AppControl", "Esta aplicación está bloqueada por regla activa: "
                            + ruleJtMa.getDisplayName());
                }
            }
            askedJtMa.keySet().removeIf(pidJtMa -> ProcessHandle.of(pidJtMa).map(hJtMa -> !hJtMa.isAlive())
                    .orElse(true));
        } catch (Exception exJtMa) {
            logJtMa.warn("No se pudieron aplicar las reglas: {}", exJtMa.toString());
        }
    }

    /** Programas en ejecucion agrupados por nombre (minusculas, sin .exe). */
    private static Map<String, List<ProcessHandle>> runningByNameJtMa() {
        long ownPidJtMa = ProcessHandle.current().pid();
        Map<String, List<ProcessHandle>> mapJtMa = new HashMap<>();
        ProcessHandle.allProcesses().forEach(handleJtMa -> {
            if (handleJtMa.pid() == ownPidJtMa) {
                return;
            }
            Optional<String> cmdJtMa = handleJtMa.info().command();
            if (cmdJtMa.isEmpty()) {
                return;
            }
            String pathJtMa = cmdJtMa.get();
            String nameJtMa = pathJtMa.substring(Math.max(pathJtMa.lastIndexOf('\\'), pathJtMa.lastIndexOf('/')) + 1)
                    .toLowerCase(Locale.ROOT);
            if (nameJtMa.endsWith(".exe")) {
                nameJtMa = nameJtMa.substring(0, nameJtMa.length() - 4);
            }
            if (RuleServiceJt.PROTECTED_JTMA.contains(nameJtMa)) {
                return;
            }
            mapJtMa.computeIfAbsent(nameJtMa, kJtMa -> new ArrayList<>()).add(handleJtMa);
        });
        return mapJtMa;
    }

    /** Cierre educado (taskkill sin /F); si pasan 10 s y sigue vivo, se fuerza. */
    /** Devuelve true cuando acaba de pedir el cierre (para registrarlo una sola vez en el historial). */
    private boolean closeJtMa(ProcessHandle handleJtMa) {
        if (!handleJtMa.isAlive() || handleJtMa.pid() == ProcessHandle.current().pid()) {
            return false;
        }
        Instant askedAtJtMa = askedJtMa.get(handleJtMa.pid());
        if (askedAtJtMa == null) {
            askedJtMa.put(handleJtMa.pid(), Instant.now());
            try {
                new ProcessBuilder("taskkill", "/PID", String.valueOf(handleJtMa.pid()))
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            } catch (Exception exJtMa) {
                handleJtMa.destroy();
            }
            return true;
        } else if (Instant.now().toEpochMilli() - askedAtJtMa.toEpochMilli() > forceAfterMsJtMa) {
            handleJtMa.destroyForcibly();
        }
        return false;
    }
}
