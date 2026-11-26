package com.appcontrol.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

import com.appcontrol.cloud.CloudClientMa;
import com.appcontrol.cloud.CloudDtoJt.AppDtoMa;
import com.appcontrol.cloud.CloudDtoJt.EventDtoJt;
import com.appcontrol.cloud.CloudDtoJt.GroupOutMa;
import com.appcontrol.cloud.CloudDtoJt.MemberOutJt;
import com.appcontrol.cloud.CloudDtoJt.RuleDtoJt;
import com.appcontrol.cloud.CloudDtoJt.UsageDtoMa;
import com.appcontrol.cloud.CloudServiceMa;
import com.appcontrol.service.AccountServiceMa;

/** Dibuja el grupo con un cuadrito por equipo y las tres pestañas de un equipo controlado (target/ui/grupo-*.png). */
class GroupsPageRenderMaTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static void layoutAll(Container cMa) {
        cMa.doLayout();
        for (Component childMa : cMa.getComponents()) {
            if (childMa instanceof Container ccMa) {
                layoutAll(ccMa);
            }
        }
    }

    private static void save(Container panelMa, String nameMa) throws Exception {
        panelMa.setSize(1100, 700);
        layoutAll(panelMa);
        layoutAll(panelMa);
        BufferedImage imgMa = new BufferedImage(1100, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D gMa = imgMa.createGraphics();
        gMa.setColor(UIManager.getColor("Panel.background"));
        gMa.fillRect(0, 0, 1100, 700);
        panelMa.printAll(gMa);
        gMa.dispose();
        File fileMa = new File("target/ui/" + nameMa + ".png");
        fileMa.getParentFile().mkdirs();
        ImageIO.write(imgMa, "png", fileMa);
        assertTrue(fileMa.length() > 1000);
    }

    private static MemberOutJt member(long idMa, String nameMa, String statusMa, String controlMa, String kindMa,
            Instant untilMa, boolean activeMa) {
        return new MemberOutJt(idMa, nameMa, "MEMBER", statusMa, controlMa, kindMa, Instant.now().minusSeconds(600),
                untilMa, false, activeMa, Instant.now().minusSeconds(120), "NONE".equals(controlMa) && !activeMa);
    }

    @Test
    void renderizaElGrupoYElEquipoControlado() throws Exception {
        CloudClientMa clientMa = mock(CloudClientMa.class);
        Instant nowMa = Instant.now();
        LocalDate todayMa = LocalDate.now();
        when(clientMa.usage(anyLong(), anyLong(), any(), any())).thenReturn(List.of(
                new UsageDtoMa("chrome", "Google Chrome", todayMa, 5400),
                new UsageDtoMa("roblox", "Roblox", todayMa, 2400),
                new UsageDtoMa("code", "Visual Studio Code", todayMa, 900)));
        when(clientMa.apps(anyLong(), anyLong())).thenReturn(List.of(new AppDtoMa("steam", "Steam"),
                new AppDtoMa("discord", "Discord")));
        when(clientMa.rules(anyLong(), anyLong())).thenReturn(List.of(
                new RuleDtoJt("APP", "roblox", "Roblox", "SCHEDULE", "ACTIVE", 127, 22 * 60, 6 * 60, 0),
                new RuleDtoJt("SITE", "tiktok.com", "tiktok.com", "ALWAYS", "ACTIVE", 127, 0, 0, 0)));
        when(clientMa.events(anyLong(), anyLong(), any(), any())).thenReturn(List.of(
                new EventDtoJt(1, "SITE", "youtube.com", "youtube.com", nowMa.minusSeconds(3000),
                        nowMa.minusSeconds(2000), 4),
                new EventDtoJt(2, "APP", "roblox", "Roblox", nowMa.minusSeconds(5000), nowMa.minusSeconds(2600), 1),
                new EventDtoJt(3, "BLOCK_SITE", "tiktok.com", "tiktok.com", nowMa.minusSeconds(900),
                        nowMa.minusSeconds(880), 2)));
        CloudServiceMa cloudMa = mock(CloudServiceMa.class);
        when(cloudMa.client()).thenReturn(clientMa);

        GroupOutMa groupMa = new GroupOutMa(1L, "Casa", "K7PM-92XA", "OWNER", 10L, "ACTIVE");
        List<MemberOutJt> membersMa = List.of(
                member(11, "Luis", "ACTIVE", "ACCEPTED", "PERMANENT", null, true),
                member(12, "Ana", "ACTIVE", "ACCEPTED", "TIMED", nowMa.plusSeconds(3000), true),
                member(13, "Pedro", "ACTIVE", "REQUESTED", "PERMANENT", null, false),
                member(14, "Sofía", "ACTIVE", "NONE", null, null, false),
                member(15, "Mateo", "PENDING", "NONE", null, null, false));

        for (boolean darkMa : new boolean[] {false, true}) {
            final GroupsPageMa[] splitPageMa = new GroupsPageMa[1];
            SwingUtilities.invokeAndWait(() -> {
                new ThemeManagerMa().applyJt(darkMa);
                splitPageMa[0] = new GroupsPageMa(cloudMa, mock(AccountServiceMa.class));
                splitPageMa[0].previewSelectedMa(groupMa, membersMa, darkMa ? 14L : 11L);
            });
            Thread.sleep(1500);
            // primera pasada: calcula el ancho real; las filas se reacomodan y luego se dibuja de verdad
            SwingUtilities.invokeAndWait(() -> {
                try {
                    save(splitPageMa[0], "grupo-detalle-previo");
                } catch (Exception exMa) {
                    throw new RuntimeException(exMa);
                }
            });
            Thread.sleep(800);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    save(splitPageMa[0], "grupo-detalle-" + (darkMa ? "oscuro" : "claro"));
                } catch (Exception exMa) {
                    throw new RuntimeException(exMa);
                }
            });
            SwingUtilities.invokeAndWait(() -> {
                try {
                    new ThemeManagerMa().applyJt(darkMa);
                    String suffixMa = darkMa ? "oscuro" : "claro";
                    GroupsPageMa pageMa = new GroupsPageMa(cloudMa, mock(AccountServiceMa.class));
                    pageMa.previewOwnerMa(groupMa, membersMa);
                    save(pageMa, "grupo-cuadritos-" + suffixMa);

                    MemberViewJtMa viewMa = new MemberViewJtMa(cloudMa, mock(AccountServiceMa.class), 1L,
                            membersMa.get(0), "prueba", false, () -> { });
                    javax.swing.JPanel holderMa = new javax.swing.JPanel(new java.awt.BorderLayout());
                    holderMa.setBorder(javax.swing.BorderFactory.createEmptyBorder(24, 28, 24, 28));
                    holderMa.add(viewMa, java.awt.BorderLayout.CENTER);
                    Thread.sleep(1500);
                    SwingUtilities.invokeLater(() -> { });
                    pending = new Object[] {holderMa, suffixMa};
                } catch (Exception exMa) {
                    throw new RuntimeException(exMa);
                }
            });
            // los datos del equipo llegan en segundo plano: se espera y se dibujan las tres pestañas
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> {
                try {
                    javax.swing.JPanel holderMa = (javax.swing.JPanel) pending[0];
                    String suffixMa = (String) pending[1];
                    JTabbedPane tabsMa = findTabs(holderMa);
                    String[] namesMa = {"aplicaciones", "sitios", "historial"};
                    for (int tabMa = 0; tabMa < namesMa.length; tabMa++) {
                        tabsMa.setSelectedIndex(tabMa);
                        save(holderMa, "grupo-equipo-" + namesMa[tabMa] + "-" + suffixMa);
                    }
                } catch (Exception exMa) {
                    throw new RuntimeException(exMa);
                }
            });
        }
    }

    private Object[] pending;

    private static JTabbedPane findTabs(Container rootMa) {
        for (Component childMa : rootMa.getComponents()) {
            if (childMa instanceof JTabbedPane tabsMa) {
                return tabsMa;
            }
            if (childMa instanceof Container ccMa) {
                JTabbedPane foundMa = findTabs(ccMa);
                if (foundMa != null) {
                    return foundMa;
                }
            }
        }
        return null;
    }

    @Test
    void renderizaLaPantallaDeQuienEsControladoYLaSolicitud() throws Exception {
        Instant nowMa = Instant.now();
        com.appcontrol.cloud.CloudDtoJt.SyncMembershipMa dtoMa = new com.appcontrol.cloud.CloudDtoJt.SyncMembershipMa(
                5L, 1L, "prueba", "PC de Juana", "MEMBER", "ACTIVE", "ACCEPTED", "TIMED", nowMa.minusSeconds(480),
                nowMa.plusSeconds(1320), false, nowMa.minusSeconds(480), List.of());
        com.appcontrol.cloud.CloudLinkJt linkMa = new com.appcontrol.cloud.CloudLinkJt(dtoMa);
        CloudServiceMa cloudMa = mock(CloudServiceMa.class);
        when(cloudMa.linksMa()).thenReturn(List.of(linkMa));
        when(cloudMa.nowMa()).thenReturn(nowMa);
        when(cloudMa.isOnlineMa()).thenReturn(true);
        GroupOutMa groupMa = new GroupOutMa(1L, "prueba", null, "MEMBER", 5L, "ACTIVE");
        for (boolean darkMa : new boolean[] {false, true}) {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    new ThemeManagerMa().applyJt(darkMa);
                    String suffixMa = darkMa ? "oscuro" : "claro";
                    GroupsPageMa pageMa = new GroupsPageMa(cloudMa, mock(AccountServiceMa.class));
                    pageMa.previewMemberMa(groupMa);
                    save(pageMa, "grupo-controlado-" + suffixMa);
                    javax.swing.JPanel promptMa = ControlPromptJt.bodyJt(linkMa, nowMa, pickedMa -> { });
                    promptMa.setSize(720, 520);
                    save(promptMa, "solicitud-control-" + suffixMa);
                } catch (Exception exMa) {
                    throw new RuntimeException(exMa);
                }
            });
        }
    }
}
