package com.appcontrol.server.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.server.api.ApiExceptionMa;
import com.appcontrol.server.model.DeviceJtMa;
import com.appcontrol.server.model.MemberJt;
import com.appcontrol.server.repo.ReposJtMa.MemberRepoJt;

/**
 * Pantalla en vivo de un equipo controlado. Solo existe MIENTRAS el dueño la está mirando: cada imagen vive únicamente
 * en memoria (nunca en la base de datos ni en disco), se reemplaza por la siguiente y se descarta a los pocos segundos
 * de que el dueño deja de mirar o termina el control.
 */
@Service
public class ScreenServiceJt {

    /** Si el dueño no pregunta en este tiempo, deja de contar como "mirando". */
    private static final long WATCH_SECONDS_JT = 12;
    private static final int MAX_BYTES_JT = 900_000;

    private static final class SlotJt {
        private volatile Instant wantedUntilJt = Instant.EPOCH;
        private volatile String viewerJt = "";
        private volatile byte[] frameJt;
        private volatile long frameNoJt;
        private volatile boolean inputRequestedJt;
        private volatile boolean inputAllowedJt;
        /** La persona cortó el mouse y teclado: no se vuelve a dar mientras sigan mirándola en esta sesión. */
        private volatile boolean inputBlockedJt;
        /** Última vez que el equipo controlado recogió gestos (para saber si de verdad los está aplicando). */
        private volatile Instant lastPullJt = Instant.EPOCH;
        private final java.util.concurrent.atomic.AtomicReference<Map<String, String>> statusJt =
                new java.util.concurrent.atomic.AtomicReference<>(Map.of());
        /** Hasta cuándo alguien mira en grande (ventana de pantalla); si no, solo miniaturas. */
        private volatile Instant fullUntilJt = Instant.EPOCH;
        private final ConcurrentLinkedQueue<InputEvMa> queueJt = new ConcurrentLinkedQueue<>();
    }

    /** Un gesto del dueño: mover, botón, rueda o tecla. x e y van de 0 a 1 (fracción de la pantalla). */
    public record InputEvMa(String type, double x, double y, int button, int code, int delta) {
    }

    /** Estado que ve el equipo controlado. */
    public record StateMa(boolean wanted, boolean inputRequested, boolean inputAllowed, boolean full) {
    }

    private static final Set<String> TYPES_JT = Set.of("move", "down", "up", "wheel", "keydown", "keyup");
    private static final int MAX_BATCH_JT = 100;
    private static final int MAX_QUEUE_JT = 300;

    /** Lo que recibe el dueño: la imagen y su número (para no repetir la misma). */
    public record FrameMa(byte[] jpeg, long number) {
    }

    private final Map<Long, SlotJt> slotsJt = new ConcurrentHashMap<>();
    private final GroupServiceJt groupsJt;
    private final MemberRepoJt membersJt;
    private final Clock clockJt;

    public ScreenServiceJt(GroupServiceJt groupsJt, MemberRepoJt membersJt, Clock clockJt) {
        this.groupsJt = groupsJt;
        this.membersJt = membersJt;
        this.clockJt = clockJt;
    }

    // ------------------------------------------------------------------ dueño

    /** El dueño mira: renueva el permiso y devuelve la imagen más nueva si es distinta a la que ya tiene. */
    @Transactional
    public FrameMa watch(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt, long lastNumberJt, boolean fullJt) {
        SlotJt slotJt = touchJt(ownerJt, groupIdJt, memberIdJt, fullJt);
        byte[] frameJt = slotJt.frameJt;
        if (frameJt == null || slotJt.frameNoJt == lastNumberJt) {
            return null;
        }
        return new FrameMa(frameJt, slotJt.frameNoJt);
    }

    /** Renueva el permiso de mirar y devuelve el espacio de esa pantalla. */
    private SlotJt touchJt(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt, boolean fullJt) {
        MemberJt memberJt = visibleJt(ownerJt, groupIdJt, memberIdJt);
        SlotJt slotJt = slotsJt.computeIfAbsent(memberJt.getDeviceId(), keyJt -> new SlotJt());
        Instant untilJt = Instant.now(clockJt).plusSeconds(WATCH_SECONDS_JT);
        slotJt.wantedUntilJt = untilJt;
        slotJt.viewerJt = ownerJt.getName();
        if (fullJt) {
            slotJt.fullUntilJt = untilJt;
        }
        return slotJt;
    }

    /**
     * Igual que watch, pero si todavía no hay una imagen más nueva espera (hasta waitMsJt) a que llegue. Así el dueño
     * la recibe en cuanto el otro equipo la sube, sin ir preguntando cada rato. No mantiene ninguna transacción abierta.
     */
    public FrameMa watchWait(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt, long lastNumberJt, boolean fullJt,
            long waitMsJt) {
        SlotJt slotJt = touchJt(ownerJt, groupIdJt, memberIdJt, fullJt);
        long endJt = System.nanoTime() + Math.max(0, waitMsJt) * 1_000_000L;
        synchronized (slotJt) {
            while (slotJt.frameJt == null || slotJt.frameNoJt == lastNumberJt) {
                long leftMsJt = (endJt - System.nanoTime()) / 1_000_000L;
                if (leftMsJt <= 0) {
                    break;
                }
                try {
                    slotJt.wait(leftMsJt);
                } catch (InterruptedException exJt) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            byte[] frameJt = slotJt.frameJt;
            return frameJt == null || slotJt.frameNoJt == lastNumberJt ? null : new FrameMa(frameJt, slotJt.frameNoJt);
        }
    }

    @Transactional
    public void stop(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = groupsJt.memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        slotsJt.remove(memberJt.getDeviceId());
    }

    private MemberJt visibleJt(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = groupsJt.memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        Instant nowJt = Instant.now(clockJt);
        groupsJt.expireIfNeededJt(memberJt, nowJt);
        if (!memberJt.controlActive(nowJt)) {
            throw ApiExceptionMa.forbidden("Esa persona no ha aceptado el control (o ya terminó).");
        }
        return memberJt;
    }

    /** ¿El otro equipo está recogiendo los gestos ahora mismo? (los pide cada ~0,1 s mientras los aplica) */
    @Transactional
    public boolean inputApplying(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = visibleJt(ownerJt, groupIdJt, memberIdJt);
        SlotJt slotJt = slotsJt.get(memberJt.getDeviceId());
        return slotJt != null && slotJt.inputAllowedJt
                && Instant.now(clockJt).isBefore(slotJt.lastPullJt.plusSeconds(4));
    }

    /** "none", "requested" (esperando que la persona acepte) o "allowed". */
    @Transactional
    public String inputState(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = visibleJt(ownerJt, groupIdJt, memberIdJt);
        SlotJt slotJt = slotsJt.get(memberJt.getDeviceId());
        if (slotJt == null) {
            return "none";
        }
        return slotJt.inputAllowedJt ? "allowed" : slotJt.inputRequestedJt ? "requested" : "none";
    }

    /**
     * El dueño usa el mouse y el teclado. La persona ya lo aceptó al aceptar el control (la solicitud se lo explica),
     * así que no se vuelve a preguntar; solo puede cortarlo con «Detener» y entonces no se da de nuevo en esta sesión.
     */
    @Transactional
    public void requestInput(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = visibleJt(ownerJt, groupIdJt, memberIdJt);
        SlotJt slotJt = slotsJt.computeIfAbsent(memberJt.getDeviceId(), keyJt -> new SlotJt());
        slotJt.wantedUntilJt = Instant.now(clockJt).plusSeconds(WATCH_SECONDS_JT);
        slotJt.viewerJt = ownerJt.getName();
        slotJt.queueJt.clear();
        slotJt.inputRequestedJt = false;
        slotJt.inputAllowedJt = !slotJt.inputBlockedJt;
    }

    /** Gestos del dueño. Solo se aceptan si la persona permitió el control del mouse y teclado. Nada se guarda. */
    @Transactional
    public void sendInput(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt, List<InputEvMa> eventsJt) {
        MemberJt memberJt = visibleJt(ownerJt, groupIdJt, memberIdJt);
        SlotJt slotJt = slotsJt.get(memberJt.getDeviceId());
        if (slotJt == null || !slotJt.inputAllowedJt) {
            throw ApiExceptionMa.forbidden("La persona no ha permitido usar su mouse y teclado.");
        }
        if (eventsJt == null || eventsJt.size() > MAX_BATCH_JT) {
            throw ApiExceptionMa.badRequest("Envía hasta " + MAX_BATCH_JT + " gestos por vez.");
        }
        for (InputEvMa evJt : eventsJt) {
            if (evJt == null || evJt.type() == null || !TYPES_JT.contains(evJt.type())
                    || slotJt.queueJt.size() >= MAX_QUEUE_JT) {
                continue;
            }
            slotJt.queueJt.add(new InputEvMa(evJt.type(), Math.max(0, Math.min(1, evJt.x())),
                    Math.max(0, Math.min(1, evJt.y())), Math.max(0, Math.min(3, evJt.button())),
                    Math.max(0, Math.min(0x3000, evJt.code())), Math.max(-20, Math.min(20, evJt.delta()))));
        }
        synchronized (slotJt) {
            slotJt.notifyAll();
        }
    }

    /** El dueño deja de usar el mouse y el teclado (sigue viendo la pantalla). */
    @Transactional
    public void stopInput(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = groupsJt.memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        SlotJt slotJt = slotsJt.get(memberJt.getDeviceId());
        if (slotJt != null) {
            endInputJt(slotJt);
        }
    }

    private static void endInputJt(SlotJt slotJt) {
        slotJt.inputAllowedJt = false;
        slotJt.inputRequestedJt = false;
        slotJt.queueJt.clear();
    }

    // ------------------------------------------------------------------ equipo controlado

    /** ¿Alguien la está mirando ahora? Devuelve el nombre de quien mira, o vacío. */
    public String viewerOf(DeviceJtMa deviceJt) {
        SlotJt slotJt = slotsJt.get(deviceJt.getId());
        if (slotJt == null) {
            return "";
        }
        if (!Instant.now(clockJt).isBefore(slotJt.wantedUntilJt)) {
            slotsJt.remove(deviceJt.getId());
            return "";
        }
        return slotJt.viewerJt;
    }

    /** Estado actual para el equipo controlado (si lo miran y si hay pedido o permiso de mouse y teclado). */
    public StateMa stateOf(DeviceJtMa deviceJt) {
        boolean wantedJt = !viewerOf(deviceJt).isEmpty();
        SlotJt slotJt = slotsJt.get(deviceJt.getId());
        return new StateMa(wantedJt, wantedJt && slotJt != null && slotJt.inputRequestedJt,
                wantedJt && slotJt != null && slotJt.inputAllowedJt,
                wantedJt && slotJt != null && Instant.now(clockJt).isBefore(slotJt.fullUntilJt));
    }

    /** La persona acepta o corta el control del mouse y teclado. Cortar siempre funciona. */
    @Transactional
    public void answerInput(DeviceJtMa deviceJt, boolean allowJt) {
        SlotJt slotJt = slotsJt.get(deviceJt.getId());
        if (slotJt == null) {
            return;
        }
        if (!allowJt) {
            endInputJt(slotJt);
            slotJt.inputBlockedJt = true;
            return;
        }
        if (!viewerOf(deviceJt).isEmpty() && slotJt.inputRequestedJt && controlledJt(deviceJt)) {
            slotJt.inputRequestedJt = false;
            slotJt.inputAllowedJt = true;
        } else {
            throw ApiExceptionMa.forbidden("Nadie está pidiendo usar tu mouse y teclado.");
        }
    }

    /** Los gestos pendientes (y los borra). Vacío si no hay permiso, nadie mira o el control terminó. */
    @Transactional
    public List<InputEvMa> drainInput(DeviceJtMa deviceJt) {
        List<InputEvMa> outJt = new ArrayList<>();
        SlotJt slotJt = slotsJt.get(deviceJt.getId());
        if (slotJt == null || !slotJt.inputAllowedJt || viewerOf(deviceJt).isEmpty() || !controlledJt(deviceJt)) {
            return outJt;
        }
        slotJt.lastPullJt = Instant.now(clockJt);
        InputEvMa evJt;
        while (outJt.size() < 200 && (evJt = slotJt.queueJt.poll()) != null) {
            outJt.add(evJt);
        }
        return outJt;
    }

    /** Igual que drainInput, pero si no hay gestos espera un poco a que lleguen (así se aplican al instante). */
    public List<InputEvMa> drainInputWait(DeviceJtMa deviceJt, long waitMsJt) {
        List<InputEvMa> firstJt = drainInput(deviceJt);
        if (!firstJt.isEmpty() || waitMsJt <= 0) {
            return firstJt;
        }
        SlotJt slotJt = slotsJt.get(deviceJt.getId());
        if (slotJt == null || !slotJt.inputAllowedJt) {
            return firstJt;
        }
        synchronized (slotJt) {
            if (slotJt.queueJt.isEmpty()) {
                try {
                    slotJt.wait(waitMsJt);
                } catch (InterruptedException exJt) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        List<InputEvMa> outJt = new ArrayList<>();
        slotJt.lastPullJt = Instant.now(clockJt);
        InputEvMa evJt;
        while (slotJt.inputAllowedJt && outJt.size() < 200 && (evJt = slotJt.queueJt.poll()) != null) {
            outJt.add(evJt);
        }
        return outJt;
    }

    private boolean controlledJt(DeviceJtMa deviceJt) {
        Instant nowJt = Instant.now(clockJt);
        boolean controlledJt = false;
        for (MemberJt memberJt : membersJt.findByDeviceId(deviceJt.getId())) {
            groupsJt.expireIfNeededJt(memberJt, nowJt);
            controlledJt |= memberJt.controlActive(nowJt);
        }
        return controlledJt;
    }

    /** Guarda la imagen que sube el equipo; devuelve si todavía la piden. Sin control vigente no se acepta nada. */
    @Transactional
    public boolean put(DeviceJtMa deviceJt, byte[] jpegJt) {
        if (jpegJt == null || jpegJt.length == 0 || jpegJt.length > MAX_BYTES_JT) {
            throw ApiExceptionMa.badRequest("La imagen de pantalla no es válida.");
        }
        if (viewerOf(deviceJt).isEmpty()) {
            return false;
        }
        Instant nowJt = Instant.now(clockJt);
        boolean controlledJt = false;
        for (MemberJt memberJt : membersJt.findByDeviceId(deviceJt.getId())) {
            groupsJt.expireIfNeededJt(memberJt, nowJt);
            controlledJt |= memberJt.controlActive(nowJt);
        }
        SlotJt slotJt = slotsJt.get(deviceJt.getId());
        if (!controlledJt || slotJt == null) {
            slotsJt.remove(deviceJt.getId());
            return false;
        }
        synchronized (slotJt) {
            slotJt.frameJt = jpegJt;
            slotJt.frameNoJt++;
            slotJt.notifyAll();
        }
        return true;
    }
}
