package com.appcontrol.cloud;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.appcontrol.cloud.CloudClientMa.CloudExceptionMa;
import com.appcontrol.cloud.CloudDtoJt.AppDtoMa;
import com.appcontrol.cloud.CloudDtoJt.EventDtoJt;
import com.appcontrol.cloud.CloudDtoJt.RuleDtoJt;
import com.appcontrol.cloud.CloudDtoJt.SyncMembershipMa;
import com.appcontrol.cloud.CloudDtoJt.SyncOutJt;
import com.appcontrol.cloud.CloudDtoJt.UsageDtoMa;
import com.appcontrol.model.ActivityEventJt;
import com.appcontrol.model.AppUsageJtMa;
import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.repository.AppUsageRepositoryJtMa;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.InstalledAppsServiceJtMa;
import com.appcontrol.service.InstalledAppsServiceJtMa.InstalledAppJt;

/**
 * Une esta PC con la nube. Cada ~45 s pregunta que hay de nuevo (solicitudes de control y reglas), lo guarda en la
 * base local y, si alguien nos controla con nuestro permiso, le sube el uso y el historial. Sin internet no pasa
 * nada: las reglas ya guardadas se siguen aplicando y los avisos de fin se calculan con el reloj local corregido.
 */
@Service
public class CloudServiceMa implements GroupRulesSourceMa {

    private static final Logger logMa = LoggerFactory.getLogger(CloudServiceMa.class);
    private static final int UPLOAD_DAYS_MA = 7;
    private static final int MAX_EVENTS_MA = 900;
    private static final Duration APPS_EVERY_MA = Duration.ofMinutes(30);

    private final CloudClientMa clientMa;
    private final GroupRuleRepositoryMa rulesMa;
    private final CloudLinkRepositoryJtMa linksMa;
    private final AccountServiceMa accountMa;
    private final AppUsageRepositoryJtMa usageMa;
    private final ActivityServiceMa activityMa;
    private final InstalledAppsServiceJtMa installedMa;

    /** Diferencia (ms) entre la hora del servidor y la de esta PC, para que adelantar el reloj no sirva de nada. */
    private volatile long offsetMsMa;
    private volatile String lastErrorMa = "";
    private volatile boolean onlineMa;
    private final java.util.Map<String, String> localStatusMa = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Instant lastAppsUploadMa = Instant.EPOCH;

    public CloudServiceMa(CloudClientMa clientMa, GroupRuleRepositoryMa rulesMa, CloudLinkRepositoryJtMa linksMa,
            AccountServiceMa accountMa, AppUsageRepositoryJtMa usageMa, ActivityServiceMa activityMa,
            InstalledAppsServiceJtMa installedMa) {
        this.clientMa = clientMa;
        this.rulesMa = rulesMa;
        this.linksMa = linksMa;
        this.accountMa = accountMa;
        this.usageMa = usageMa;
        this.activityMa = activityMa;
        this.installedMa = installedMa;
    }

    public CloudClientMa client() {
        return clientMa;
    }

    /** "Ahora" segun el servidor (la hora de esta PC corregida con la ultima diferencia conocida). */
    public Instant nowMa() {
        return Instant.now().plusMillis(offsetMsMa);
    }

    public boolean isOnlineMa() {
        return onlineMa;
    }

    public String lastErrorMa() {
        return lastErrorMa;
    }

    // ------------------------------------------------------------------ registro

    /** Registra esta PC en la nube la primera vez que se usan los grupos. */
    public synchronized void ensureRegisteredMa() {
        if (clientMa.hasToken()) {
            return;
        }
        String ownerMa = accountMa.hasAccount() ? accountMa.usernameJt() : System.getProperty("user.name", "Usuario");
        clientMa.saveToken(clientMa.register("PC de " + ownerMa).token());
    }

    // ------------------------------------------------------------------ sincronizacion

    @Scheduled(initialDelay = 20_000, fixedDelay = 45_000)
    public void syncJt() {
        if (!clientMa.hasToken()) {
            return;
        }
        try {
            syncNowMa();
        } catch (CloudExceptionMa exMa) {
            onlineMa = !exMa.offline();
            lastErrorMa = exMa.getMessage();
            logMa.debug("Sincronizacion con la nube: {}", exMa.getMessage());
        } catch (RuntimeException exMa) {
            lastErrorMa = exMa.toString();
            logMa.warn("No se pudo sincronizar con la nube: {}", exMa.toString());
        }
    }

    /** Consulta la nube y actualiza las reglas y vinculos locales. Lanza error si no hay conexion. */
    public synchronized void syncNowMa() {
        SyncOutJt outMa = clientMa.sync();
        onlineMa = true;
        lastErrorMa = "";
        offsetMsMa = Duration.between(Instant.now(), outMa.serverTime()).toMillis();

        linksMa.deleteAllInBatch();
        Set<Long> keepMa = new HashSet<>();
        List<CloudLinkJt> newLinksMa = new ArrayList<>();
        for (SyncMembershipMa memberMa : outMa.memberships()) {
            newLinksMa.add(new CloudLinkJt(memberMa));
            if ("MEMBER".equals(memberMa.role()) && "ACCEPTED".equals(memberMa.controlStatus())) {
                keepMa.add(memberMa.memberId());
                List<GroupRuleJtMa> freshMa = new ArrayList<>();
                for (RuleDtoJt dtoMa : memberMa.rules()) {
                    freshMa.add(new GroupRuleJtMa(memberMa.memberId(), memberMa.groupName(), memberMa.ownerName(),
                            dtoMa, memberMa.controlUntil()));
                }
                // Solo se reemplaza si cambió algo: así las reglas nunca desaparecen un instante en cada consulta.
                List<GroupRuleJtMa> oldMa = new ArrayList<>();
                for (GroupRuleJtMa ruleMa : rulesMa.findAll()) {
                    if (memberMa.memberId().equals(memberOf(ruleMa))) {
                        oldMa.add(ruleMa);
                    }
                }
                if (!sameRulesMa(oldMa, freshMa)) {
                    rulesMa.deleteByMemberId(memberMa.memberId());
                    rulesMa.saveAll(freshMa);
                }
            }
        }
        linksMa.saveAll(newLinksMa);
        // Reglas de vinculos que ya no estan aceptados (terminaron o se rechazaron): se quitan.
        for (GroupRuleJtMa ruleMa : rulesMa.findAll()) {
            if (!keepMa.contains(memberOf(ruleMa))) {
                rulesMa.delete(ruleMa);
            }
        }
        if (anyAcceptedMa(newLinksMa)) {
            uploadMa();
        }
    }

    private static boolean sameRulesMa(List<GroupRuleJtMa> aMa, List<GroupRuleJtMa> bMa) {
        if (aMa.size() != bMa.size()) {
            return false;
        }
        for (GroupRuleJtMa ruleMa : bMa) {
            boolean foundMa = false;
            for (GroupRuleJtMa otherMa : aMa) {
                foundMa |= ruleMa.sameAsMa(otherMa);
            }
            if (!foundMa) {
                return false;
            }
        }
        return true;
    }

    private static boolean anyAcceptedMa(List<CloudLinkJt> linksMa) {
        for (CloudLinkJt linkMa : linksMa) {
            if ("MEMBER".equals(linkMa.getRole()) && "ACCEPTED".equals(linkMa.getControlStatus())) {
                return true;
            }
        }
        return false;
    }

    private static Long memberOf(GroupRuleJtMa ruleMa) {
        return ruleMa.memberIdMa();
    }

    // ------------------------------------------------------------------ subida de datos (solo con permiso)

    @Override
    public void reportStatusMa(String keyMa, String valueMa) {
        localStatusMa.put(keyMa, valueMa);
    }

    private void uploadMa() {
        java.util.Map<String, String> statusMa = new java.util.LinkedHashMap<>(localStatusMa);
        statusMa.put("version", com.appcontrol.BuildInfoMa.labelMa());
        try {
            clientMa.putStatus(statusMa);
        } catch (CloudExceptionMa exMa) {
            logMa.debug("No se pudo informar el estado: {}", exMa.getMessage());
        }
        LocalDate todayMa = LocalDate.now();
        List<UsageDtoMa> usageListMa = new ArrayList<>();
        for (AppUsageJtMa rowMa : usageMa.findByUsageDayBetween(todayMa.minusDays(UPLOAD_DAYS_MA - 1L), todayMa)) {
            usageListMa.add(new UsageDtoMa(rowMa.getAppName(), rowMa.getAppName(), rowMa.getUsageDay(),
                    rowMa.getSeconds()));
        }
        clientMa.putUsage(usageListMa);

        Instant nowMa = Instant.now();
        List<ActivityEventJt> eventsMa = activityMa.listJt(nowMa.minus(Duration.ofDays(UPLOAD_DAYS_MA)), nowMa);
        List<EventDtoJt> eventListMa = new ArrayList<>();
        for (ActivityEventJt eventMa : eventsMa) {
            if (eventListMa.size() >= MAX_EVENTS_MA) {
                break;
            }
            eventListMa.add(new EventDtoJt(eventMa.getId(), eventMa.getKind(), eventMa.getTargetKey(),
                    eventMa.getDisplayName(), eventMa.getStartedAt(), eventMa.getEndedAt(), eventMa.getHits()));
        }
        clientMa.putEvents(eventListMa);

        if (Duration.between(lastAppsUploadMa, nowMa).compareTo(APPS_EVERY_MA) > 0) {
            List<AppDtoMa> appsMa = new ArrayList<>();
            for (InstalledAppJt appMa : installedMa.cached()) {
                if (appMa.exe() != null && !appMa.exe().isBlank()) {
                    appsMa.add(new AppDtoMa(appMa.exe().toLowerCase(Locale.ROOT), appMa.name()));
                }
            }
            if (appsMa.size() > 900) {
                appsMa = appsMa.subList(0, 900);
            }
            clientMa.putApps(appsMa);
            lastAppsUploadMa = nowMa;
        }
    }

    // ------------------------------------------------------------------ lo que usan los motores y la pantalla

    @Override
    public List<BlockRuleJtMa> activeJt(String kindJt) {
        Instant nowMa = nowMa();
        List<BlockRuleJtMa> outMa = new ArrayList<>();
        for (GroupRuleJtMa ruleMa : rulesMa.findByKind(kindJt)) {
            if (ruleMa.getExpiresAt() == null || nowMa.isBefore(ruleMa.getExpiresAt())) {
                outMa.add(ruleMa.toRuleJt());
            }
        }
        return outMa;
    }

    /** Reglas de grupo guardadas en este equipo, vigentes (para mostrarlas, sin poder editarlas). */
    public List<GroupRuleJtMa> groupRulesMa() {
        Instant nowMa = nowMa();
        List<GroupRuleJtMa> outMa = new ArrayList<>();
        for (GroupRuleJtMa ruleMa : rulesMa.findAll()) {
            if (ruleMa.getExpiresAt() == null || nowMa.isBefore(ruleMa.getExpiresAt())) {
                outMa.add(ruleMa);
            }
        }
        return outMa;
    }

    public List<CloudLinkJt> linksMa() {
        return linksMa.findAll();
    }

    /** Solicitudes de control que esperan mi respuesta. */
    public List<CloudLinkJt> pendingRequestsMa() {
        List<CloudLinkJt> outMa = new ArrayList<>();
        for (CloudLinkJt linkMa : linksMa.findAll()) {
            if ("MEMBER".equals(linkMa.getRole()) && "REQUESTED".equals(linkMa.getControlStatus())) {
                outMa.add(linkMa);
            }
        }
        return outMa;
    }

    /** Quienes me controlan ahora mismo (para el aviso permanente en pantalla). */
    public List<CloudLinkJt> controlledByMa() {
        Instant nowMa = nowMa();
        List<CloudLinkJt> outMa = new ArrayList<>();
        for (CloudLinkJt linkMa : linksMa.findAll()) {
            if (linkMa.controlledByOther(nowMa)) {
                outMa.add(linkMa);
            }
        }
        return outMa;
    }
}
