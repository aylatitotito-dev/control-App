package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.appcontrol.cloud.CloudClientMa.CloudExceptionMa;
import com.appcontrol.cloud.CloudDtoJt.ControlInMa;
import com.appcontrol.cloud.CloudDtoJt.GroupOutMa;
import com.appcontrol.cloud.CloudDtoJt.MemberOutJt;
import com.appcontrol.cloud.CloudLinkJt;
import com.appcontrol.cloud.CloudServiceMa;
import com.appcontrol.cloud.GroupRuleJtMa;
import com.appcontrol.service.AccountServiceMa;
import com.formdev.flatlaf.FlatClientProperties;

/**
 * Grupos de la nube. Lista de grupos (crear / unirme con codigo) → dentro de un grupo, cada persona es un cuadrito
 * (su equipo) → al abrir un equipo controlado solo hay tres pestañas: Aplicaciones, Sitios y URLs e Historial.
 * Todo funciona sin bloquear la ventana; sin internet se muestra lo ultimo guardado.
 */
class GroupsPageMa extends PageMa {

    private static final String LIST_MA = "list";
    private static final String GROUP_MA = "group";
    private static final String MEMBER_MA = "member";
    private static final DateTimeFormatter WHEN_MA = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final long LIST_EVERY_MS_MA = 10_000;
    private static final long GROUP_EVERY_MS_MA = 10_000;

    private final CloudServiceMa cloudMa;
    private final AccountServiceMa accountMa;
    private final CardLayout cardsMa = new CardLayout();
    private final JPanel rootMa = new JPanel(cardsMa);
    private final JPanel listBodyMa = new JPanel();
    private final JPanel groupHolderMa = new JPanel(new BorderLayout());
    private final JPanel memberHolderMa = new JPanel(new BorderLayout());
    private final JLabel noticeMa = UiKitJtMa.subtitle(" ");

    private List<GroupOutMa> groupsMa = new ArrayList<>();
    private GroupOutMa openGroupMa;
    private String viewMa = LIST_MA;
    private long lastListMsMa;
    private long lastGroupMsMa;
    private boolean loadingMa;
    /** Miembros marcados para pedir control en lote; se conservan al refrescar la vista. */
    private final Set<Long> pickedMa = new LinkedHashSet<>();
    private String filterMa = "";
    private List<MemberOutJt> activeMembersMa = List.of();
    private GroupOutMa tilesGroupMa;
    private JPanel tilesMa;
    private JLabel pickCountMa;
    private JButton bulkButtonMa;
    private JCheckBox allBoxMa;
    private boolean syncingAllMa;
    /** Equipo elegido: su detalle se ve a la derecha de las pantallas, hasta que se cierra con la X. */
    private MemberOutJt selectedMa;
    private MemberViewJtMa memberViewMa;
    private GroupOutMa lastGroupMa;
    private List<MemberOutJt> lastMembersMa = List.of();
    /** Pantallas en vivo (miniaturas) de los equipos controlados, por id de miembro. */
    private final java.util.Map<Long, javax.swing.ImageIcon> thumbsMa = new java.util.HashMap<>();
    private final java.util.Map<Long, Long> thumbNumbersMa = new java.util.HashMap<>();
    private final java.util.Map<Long, JLabel> thumbLabelsMa = new java.util.HashMap<>();
    private final java.util.Set<Long> thumbBusyMa = new java.util.HashSet<>();
    /** Tamaños de los cuadritos de equipos: nombre, ancho y alto del cuadrito. La miniatura ocupa casi todo el ancho. */
    private record TileSizeMa(String label, int width, int height) {
        int thumbW() {
            return width - 28;
        }

        int thumbH() {
            return (int) Math.round(thumbW() * 9.0 / 16.0);
        }
    }

    private static final TileSizeMa[] TILE_SIZES_MA = {
        new TileSizeMa("Iconos muy grandes", 440, 310), new TileSizeMa("Iconos grandes", 320, 240),
        new TileSizeMa("Iconos medianos", 230, 180), new TileSizeMa("Iconos pequeños", 160, 140)};
    private static final java.util.prefs.Preferences PREFS_MA =
            java.util.prefs.Preferences.userNodeForPackage(GroupsPageMa.class);
    private TileSizeMa tileSizeMa = TILE_SIZES_MA[Math.max(0, Math.min(TILE_SIZES_MA.length - 1,
            PREFS_MA.getInt("tile_size", 2)))];

    GroupsPageMa(CloudServiceMa cloudMa, AccountServiceMa accountMa) {
        super("Grupos y Sesiones");
        this.cloudMa = cloudMa;
        this.accountMa = accountMa;

        rootMa.setOpaque(false);
        groupHolderMa.setOpaque(false);
        memberHolderMa.setOpaque(false);
        rootMa.add(buildListCardMa(), LIST_MA);
        rootMa.add(groupHolderMa, GROUP_MA);
        rootMa.add(memberHolderMa, MEMBER_MA);
        add(rootMa, BorderLayout.CENTER);
        javax.swing.Timer thumbTimerMa = new javax.swing.Timer(2000, evMa -> tickThumbsMa());
        thumbTimerMa.start();
    }

    // ------------------------------------------------------------------ pantallas en vivo en los cuadritos

    /** Cada 2 s, solo si esta pantalla se está viendo: pide una miniatura nueva de cada equipo controlado. */
    private void tickThumbsMa() {
        GroupOutMa groupMa = tilesGroupMa;
        if (!GROUP_MA.equals(viewMa) || !isShowing() || groupMa == null || openGroupMa == null
                || !"OWNER".equals(openGroupMa.role())) {
            return;
        }
        for (MemberOutJt memberMa : activeMembersMa) {
            Long idMa = memberMa.id();
            if (!memberMa.controlActive() || !thumbBusyMa.add(idMa)) {
                continue;
            }
            long sinceMa = thumbNumbersMa.getOrDefault(idMa, 0L);
            UiAsyncJt.runJt(() -> {
                com.appcontrol.cloud.CloudClientMa.ScreenFrameMa frameMa = cloudMa.client().watchScreen(groupMa.id(),
                        idMa, sinceMa, false, 0);
                if (frameMa.jpeg() == null) {
                    return null;
                }
                java.awt.image.BufferedImage imageMa = javax.imageio.ImageIO.read(
                        new java.io.ByteArrayInputStream(frameMa.jpeg()));
                if (imageMa == null) {
                    return null;
                }
                TileSizeMa sizeMa = tileSizeMa;
                double scaleMa = Math.min(sizeMa.thumbW() / (double) imageMa.getWidth(),
                        sizeMa.thumbH() / (double) imageMa.getHeight());
                java.awt.Image smallMa = imageMa.getScaledInstance(Math.max(1, (int) (imageMa.getWidth() * scaleMa)),
                        Math.max(1, (int) (imageMa.getHeight() * scaleMa)), java.awt.Image.SCALE_SMOOTH);
                return new Object[] {new javax.swing.ImageIcon(smallMa), frameMa.number()};
            }, resultMa -> {
                thumbBusyMa.remove(idMa);
                if (resultMa != null) {
                    thumbsMa.put(idMa, (javax.swing.ImageIcon) resultMa[0]);
                    thumbNumbersMa.put(idMa, (Long) resultMa[1]);
                    JLabel labelMa = thumbLabelsMa.get(idMa);
                    if (labelMa != null) {
                        labelMa.setIcon((javax.swing.ImageIcon) resultMa[0]);
                    }
                }
            }, errorMa -> thumbBusyMa.remove(idMa));
        }
    }

    // ------------------------------------------------------------------ pantalla 1: lista de grupos

    private JPanel buildListCardMa() {
        JButton createMa = new JButton("+  Crear grupo");
        createMa.addActionListener(evMa -> createGroupMa());
        JButton joinMa = new JButton("Unirme a un grupo");
        joinMa.addActionListener(evMa -> joinGroupMa());
        JPanel buttonsMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonsMa.setOpaque(false);
        buttonsMa.add(refreshButtonMa(() -> {
            noticeMa.setText("Actualizando…");
            lastListMsMa = 0;
            loadGroupsMa();
        }));
        buttonsMa.add(joinMa);
        buttonsMa.add(createMa);

        JPanel headMa = new JPanel(new BorderLayout());
        headMa.setOpaque(false);
        JPanel titlesMa = new JPanel(new GridLayout(2, 1, 0, 2));
        titlesMa.setOpaque(false);
        titlesMa.add(UiKitJtMa.title("Grupos y Sesiones"));
        titlesMa.add(UiKitJtMa.subtitle("Crea un grupo y comparte su código, o únete con el código que te dieron."));
        headMa.add(titlesMa, BorderLayout.WEST);
        headMa.add(buttonsMa, BorderLayout.EAST);

        listBodyMa.setOpaque(false);
        listBodyMa.setLayout(new BoxLayout(listBodyMa, BoxLayout.Y_AXIS));

        JPanel topMa = new JPanel(new BorderLayout(0, 6));
        topMa.setOpaque(false);
        topMa.add(headMa, BorderLayout.NORTH);
        topMa.add(noticeMa, BorderLayout.SOUTH);

        JPanel cardMa = new JPanel(new BorderLayout(0, 14));
        cardMa.setOpaque(false);
        cardMa.add(topMa, BorderLayout.NORTH);
        cardMa.add(UiKitJtMa.vscroll(listBodyMa), BorderLayout.CENTER);
        return cardMa;
    }

    @Override
    void refreshMa() {
        long nowMa = System.currentTimeMillis();
        if (LIST_MA.equals(viewMa) && nowMa - lastListMsMa > LIST_EVERY_MS_MA) {
            loadGroupsMa();
        } else if (GROUP_MA.equals(viewMa) && openGroupMa != null && nowMa - lastGroupMsMa > GROUP_EVERY_MS_MA) {
            refreshOpenGroupMa();
        }
    }

    /** Botón "Actualizar" con el icono de las dos flechas. */
    private static JButton refreshButtonMa(Runnable actionMa) {
        JButton buttonMa = new JButton("Actualizar", IconsJtMa.of("refresh", 16));
        buttonMa.setToolTipText("Vuelve a preguntar a la nube ahora mismo");
        buttonMa.addActionListener(evMa -> actionMa.run());
        return buttonMa;
    }

    /** Selector con todos tus grupos para saltar de uno a otro sin volver a la lista. */
    private JComboBox<GroupOutMa> switcherMa(GroupOutMa currentMa) {
        List<GroupOutMa> optionsMa = new ArrayList<>(groupsMa);
        boolean presentMa = false;
        for (GroupOutMa groupMa : optionsMa) {
            presentMa |= groupMa.id().equals(currentMa.id());
        }
        if (!presentMa) {
            optionsMa.add(currentMa);
        }
        JComboBox<GroupOutMa> boxMa = new JComboBox<>(optionsMa.toArray(new GroupOutMa[0]));
        for (GroupOutMa groupMa : optionsMa) {
            if (groupMa.id().equals(currentMa.id())) {
                boxMa.setSelectedItem(groupMa);
            }
        }
        boxMa.setToolTipText("Tus grupos");
        boxMa.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> listMa, Object valueMa, int indexMa,
                    boolean selectedMa, boolean focusMa) {
                GroupOutMa groupMa = (GroupOutMa) valueMa;
                String textMa = groupMa == null ? "" : groupMa.name();
                return super.getListCellRendererComponent(listMa, textMa, indexMa, selectedMa, focusMa);
            }
        });
        boxMa.addActionListener(evMa -> {
            GroupOutMa pickedMa = (GroupOutMa) boxMa.getSelectedItem();
            if (pickedMa != null && !pickedMa.id().equals(currentMa.id())) {
                openGroupMa(pickedMa);
            }
        });
        return boxMa;
    }

    /** Vuelve a pedir tus grupos (y, si eres miembro, sincroniza ya) y reabre el grupo con el estado nuevo. */
    private void refreshOpenGroupMa() {
        GroupOutMa currentMa = openGroupMa;
        if (currentMa == null) {
            return;
        }
        lastGroupMsMa = System.currentTimeMillis();
        UiAsyncJt.runJt(() -> {
            List<GroupOutMa> freshMa = cloudMa.client().groups();
            if (!"OWNER".equals(currentMa.role())) {
                try {
                    cloudMa.syncNowMa();
                } catch (CloudExceptionMa exMa) {
                    // sin conexión: se muestra lo último guardado
                }
            }
            return freshMa;
        }, freshMa -> {
            groupsMa = freshMa;
            GroupOutMa updatedMa = currentMa;
            for (GroupOutMa groupMa : freshMa) {
                if (groupMa.id().equals(currentMa.id())) {
                    updatedMa = groupMa;
                }
            }
            if (openGroupMa != null && openGroupMa.id().equals(currentMa.id())) {
                openGroupMa(updatedMa);
            }
        }, errorMa -> {
            if (openGroupMa != null) {
                openGroupMa(openGroupMa);
            }
        });
    }

    private void showViewMa(String viewMa) {
        this.viewMa = viewMa;
        cardsMa.show(rootMa, viewMa);
    }

    private void loadGroupsMa() {
        if (loadingMa) {
            return;
        }
        loadingMa = true;
        lastListMsMa = System.currentTimeMillis();
        UiAsyncJt.runJt(() -> {
            cloudMa.ensureRegisteredMa();
            return cloudMa.client().groups();
        }, listMa -> {
            loadingMa = false;
            groupsMa = listMa;
            noticeMa.setText(" ");
            rebuildListMa();
        }, errorMa -> {
            loadingMa = false;
            noticeMa.setText(errorMa);
            if (groupsMa.isEmpty()) {
                for (CloudLinkJt linkMa : cloudMa.linksMa()) {
                    groupsMa.add(new GroupOutMa(linkMa.getGroupId(), linkMa.getGroupName(), null, linkMa.getRole(),
                            linkMa.getMemberId(), linkMa.getStatus()));
                }
            }
            rebuildListMa();
        });
    }

    private void rebuildListMa() {
        listBodyMa.removeAll();
        if (groupsMa.isEmpty()) {
            listBodyMa.add(UiKitJtMa.note("Aún no estás en ningún grupo. Crea uno con «Crear grupo» para obtener su "
                    + "código, o pulsa «Unirme a un grupo» y escribe el código que te dieron."));
        }
        for (GroupOutMa groupMa : groupsMa) {
            listBodyMa.add(groupRowMa(groupMa));
            listBodyMa.add(Box.createVerticalStrut(10));
        }
        listBodyMa.revalidate();
        listBodyMa.repaint();
    }

    private JPanel groupRowMa(GroupOutMa groupMa) {
        boolean ownerMa = "OWNER".equals(groupMa.role());
        JPanel rowMa = new JPanel(new BorderLayout(14, 0));
        rowMa.setOpaque(false);
        rowMa.putClientProperty(FlatClientProperties.STYLE, "border: 14,16,14,16,$Component.borderColor,1,14");
        rowMa.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel iconMa = new JLabel(IconsJtMa.colored("users", 30, "#2EB872"));
        JPanel textMa = new JPanel(new GridLayout(2, 1, 0, 2));
        textMa.setOpaque(false);
        JLabel nameMa = new JLabel(groupMa.name());
        nameMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        textMa.add(nameMa);
        textMa.add(UiKitJtMa.subtitle(ownerMa ? "Tú creaste este grupo" : "Eres miembro"));
        JPanel chipsMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        chipsMa.setOpaque(false);
        chipsMa.add(new ChipMa(ownerMa ? "Dueño" : "Miembro", ChipMa.KindJtMa.NEUTRAL));
        if ("PENDING".equals(groupMa.status())) {
            chipsMa.add(new ChipMa("Esperando aceptación", ChipMa.KindJtMa.WARN));
        }
        JButton openMa = new JButton("Abrir");
        openMa.addActionListener(evMa -> openGroupMa(groupMa));
        chipsMa.add(openMa);
        if (ownerMa) {
            JButton deleteMa = new JButton("Eliminar");
            deleteMa.setToolTipText("Eliminar este grupo (pide tu contraseña)");
            deleteMa.putClientProperty(FlatClientProperties.STYLE,
                    "foreground: #C62828; borderColor: #F5B5B5; focusedBorderColor: #C62828");
            deleteMa.addActionListener(evMa -> deleteGroupMa(groupMa));
            chipsMa.add(deleteMa);
        }
        rowMa.add(iconMa, BorderLayout.WEST);
        rowMa.add(textMa, BorderLayout.CENTER);
        rowMa.add(chipsMa, BorderLayout.EAST);
        rowMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowMa.getPreferredSize().height + 8));
        return rowMa;
    }

    /** Eliminar un grupo: se explica qué se pierde y se pide la contraseña, para que no pase por un toque sin querer. */
    private void deleteGroupMa(GroupOutMa groupMa) {
        String textMa = "¿Eliminar el grupo «" + groupMa.name() + "»?\n\nSe quitan todas las personas, sus reglas, el control "
                + "y el historial de este grupo. Esto no se puede deshacer.";
        if (JOptionPane.showConfirmDialog(this, textMa, "Eliminar grupo", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION || !RuleActionsMa.unlockMa(this, accountMa)) {
            return;
        }
        UiAsyncJt.runJt(() -> {
            cloudMa.client().deleteGroup(groupMa.id());
            return Boolean.TRUE;
        }, okMa -> {
            if (openGroupMa != null && openGroupMa.id().equals(groupMa.id())) {
                openGroupMa = null;
                selectedMa = null;
                memberViewMa = null;
                showViewMa(LIST_MA);
            }
            lastListMsMa = 0;
            loadGroupsMa();
        }, errorMa -> JOptionPane.showMessageDialog(this, errorMa, "AppControl", JOptionPane.WARNING_MESSAGE));
    }

    // ------------------------------------------------------------------ crear y unirse

    private void createGroupMa() {
        String nameMa = JOptionPane.showInputDialog(this, "Nombre del grupo (por ejemplo: Casa)", "Crear grupo",
                JOptionPane.PLAIN_MESSAGE);
        if (nameMa == null) {
            return;
        }
        UiAsyncJt.runJt(() -> {
            cloudMa.ensureRegisteredMa();
            return cloudMa.client().createGroup(nameMa);
        }, groupMa -> {
            showCodeMa(groupMa);
            lastListMsMa = 0;
            loadGroupsMa();
        }, errorMa -> JOptionPane.showMessageDialog(this, errorMa, "AppControl", JOptionPane.WARNING_MESSAGE));
    }

    private void showCodeMa(GroupOutMa groupMa) {
        JTextField codeMa = new JTextField(groupMa.code(), 14);
        codeMa.setEditable(false);
        codeMa.setHorizontalAlignment(JTextField.CENTER);
        codeMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");
        JPanel panelMa = new JPanel(new BorderLayout(0, 10));
        panelMa.add(new JLabel("<html><body style='width:330px'>Grupo <b>" + groupMa.name() + "</b> creado. Pasa este "
                + "código a las personas que quieras en el grupo: lo escriben en «Unirme a un grupo» y tú las "
                + "aceptas.</body></html>"), BorderLayout.NORTH);
        panelMa.add(codeMa, BorderLayout.CENTER);
        Object[] optionsMa = {"Copiar código", "Cerrar"};
        int choiceMa = JOptionPane.showOptionDialog(this, panelMa, "Tu código de grupo", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, optionsMa, optionsMa[1]);
        if (choiceMa == 0) {
            copyMa(groupMa.code());
        }
    }

    private static void copyMa(String textMa) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(textMa), null);
    }

    private void joinGroupMa() {
        JTextField codeMa = new JTextField(12);
        codeMa.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "XXXX-XXXX");
        JTextField nameMa = new JTextField(accountMa.hasAccount() ? accountMa.usernameJt() : "", 16);
        JPanel panelMa = new JPanel(new GridLayout(4, 1, 0, 4));
        panelMa.add(new JLabel("Código del grupo"));
        panelMa.add(codeMa);
        panelMa.add(new JLabel("Tu nombre en el grupo"));
        panelMa.add(nameMa);
        if (JOptionPane.showConfirmDialog(this, panelMa, "Unirme a un grupo", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String codeTextMa = codeMa.getText();
        String nameTextMa = nameMa.getText();
        UiAsyncJt.runJt(() -> {
            cloudMa.ensureRegisteredMa();
            return cloudMa.client().join(codeTextMa, nameTextMa);
        }, groupMa -> {
            JOptionPane.showMessageDialog(this, "Enviaste tu solicitud a «" + groupMa.name() + "». Cuando quien creó "
                    + "el grupo te acepte, aparecerá activo aquí.", "Solicitud enviada",
                    JOptionPane.INFORMATION_MESSAGE);
            lastListMsMa = 0;
            loadGroupsMa();
        }, errorMa -> JOptionPane.showMessageDialog(this, errorMa, "AppControl", JOptionPane.WARNING_MESSAGE));
    }

    // ------------------------------------------------------------------ pantalla 2: un grupo

    private void openGroupMa(GroupOutMa groupMa) {
        if (lastGroupMa != null && !lastGroupMa.id().equals(groupMa.id())) {
            selectedMa = null;
            memberViewMa = null;
        }
        openGroupMa = groupMa;
        lastGroupMsMa = System.currentTimeMillis();
        if ("OWNER".equals(groupMa.role())) {
            UiAsyncJt.runJt(() -> cloudMa.client().members(groupMa.id()), membersMa -> {
                buildOwnerViewMa(groupMa, membersMa, null);
                showViewMa(GROUP_MA);
            }, errorMa -> {
                buildOwnerViewMa(groupMa, List.of(), errorMa);
                showViewMa(GROUP_MA);
            });
        } else {
            buildMemberGroupViewMa(groupMa);
            showViewMa(GROUP_MA);
        }
    }

    private JButton backToListMa() {
        JButton backMa = new JButton("←  Grupos");
        backMa.addActionListener(evMa -> {
            selectedMa = null;
            memberViewMa = null;
            openGroupMa = null;
            showViewMa(LIST_MA);
            lastListMsMa = 0;
            loadGroupsMa();
        });
        return backMa;
    }

    /** Vista de quien creo el grupo: codigo, solicitudes para entrar y un cuadrito por equipo. */
    private void buildOwnerViewMa(GroupOutMa groupMa, List<MemberOutJt> membersMa, String errorMa) {
        JPanel bodyMa = new JPanel();
        bodyMa.setOpaque(false);
        bodyMa.setLayout(new BoxLayout(bodyMa, BoxLayout.Y_AXIS));

        int membersTotalMa = 0;
        int controlledMa = 0;
        for (MemberOutJt countedMa : membersMa) {
            if (!"PENDING".equals(countedMa.status()) && "MEMBER".equals(countedMa.role())) {
                membersTotalMa++;
                if (countedMa.controlActive()) {
                    controlledMa++;
                }
            }
        }

        JPanel topRowMa = new JPanel(new BorderLayout(12, 0));
        topRowMa.setOpaque(false);
        JPanel backWrapMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        backWrapMa.setOpaque(false);
        backWrapMa.add(backToListMa());
        JPanel switchWrapMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        switchWrapMa.setOpaque(false);
        switchWrapMa.add(switcherMa(groupMa));
        switchWrapMa.add(refreshButtonMa(this::refreshOpenGroupMa));
        topRowMa.add(backWrapMa, BorderLayout.WEST);
        topRowMa.add(switchWrapMa, BorderLayout.EAST);

        JPanel titleBlockMa = new JPanel();
        titleBlockMa.setOpaque(false);
        titleBlockMa.setLayout(new BoxLayout(titleBlockMa, BoxLayout.Y_AXIS));
        titleBlockMa.add(left(UiKitJtMa.title("Grupo: " + groupMa.name())));
        titleBlockMa.add(Box.createVerticalStrut(8));
        JPanel chipWrapMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        chipWrapMa.setOpaque(false);
        chipWrapMa.add(new ChipMa("Creado por ti (Propietario)", ChipMa.KindJtMa.NEUTRAL));
        titleBlockMa.add(left(chipWrapMa));
        titleBlockMa.add(Box.createVerticalStrut(10));
        titleBlockMa.add(left(UiKitJtMa.subtitle(membersTotalMa + (membersTotalMa == 1 ? " equipo" : " equipos")
                + " en este grupo · " + controlledMa + " bajo control · " + (membersTotalMa - controlledMa)
                + " sin control")));

        JPanel codeCardMa = UiKitJtMa.roundedPanel(14);
        codeCardMa.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        codeCardMa.add(new JLabel("Código de invitación:"));
        JLabel codeLabelMa = new JLabel(groupMa.code() == null ? "—" : groupMa.code());
        codeLabelMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        codeCardMa.add(codeLabelMa);
        JButton copyMa = new JButton("Copiar código");
        copyMa.addActionListener(evMa -> copyMa(groupMa.code()));
        codeCardMa.add(copyMa);
        JPanel codeWrapMa = new JPanel(new java.awt.GridBagLayout());
        codeWrapMa.setOpaque(false);
        codeWrapMa.add(codeCardMa);

        JPanel headMa = new JPanel(new BorderLayout(12, 12));
        headMa.setOpaque(false);
        headMa.add(topRowMa, BorderLayout.NORTH);
        headMa.add(titleBlockMa, BorderLayout.CENTER);
        headMa.add(codeWrapMa, BorderLayout.EAST);

        JPanel northMa = new JPanel(new BorderLayout(0, 6));
        northMa.setOpaque(false);
        northMa.add(headMa, BorderLayout.NORTH);
        if (errorMa != null) {
            northMa.add(UiKitJtMa.subtitle(errorMa), BorderLayout.SOUTH);
        }

        List<MemberOutJt> pendingMa = new ArrayList<>();
        List<MemberOutJt> activeMa = new ArrayList<>();
        for (MemberOutJt memberMa : membersMa) {
            if ("PENDING".equals(memberMa.status())) {
                pendingMa.add(memberMa);
            } else if ("MEMBER".equals(memberMa.role())) {
                activeMa.add(memberMa);
            }
        }
        lastGroupMa = groupMa;
        lastMembersMa = membersMa;
        MemberOutJt chosenMa = null;
        if (selectedMa != null) {
            for (MemberOutJt candidateMa : activeMa) {
                if (candidateMa.id().equals(selectedMa.id())) {
                    chosenMa = candidateMa;
                }
            }
        }
        if (chosenMa == null) {
            selectedMa = null;
            memberViewMa = null;
        } else {
            selectedMa = chosenMa;
            if (memberViewMa == null || memberViewMa.memberIdJt() != chosenMa.id()
                    || memberViewMa.historyOnlyJt() == chosenMa.controlActive()) {
                memberViewMa = new MemberViewJtMa(cloudMa, accountMa, groupMa.id(), chosenMa, groupMa.name(),
                        !chosenMa.controlActive(), () -> {
                    clearSelectionMa();
                    refreshOpenGroupMa();
                });
            }
        }
        boolean detailMa = chosenMa != null;

        if (!pendingMa.isEmpty() && !detailMa) {
            bodyMa.add(left(UiKitJtMa.sectionTitle("Quieren unirse (" + pendingMa.size() + ")")));
            bodyMa.add(Box.createVerticalStrut(8));
            if (pendingMa.size() > 1) {
                JButton acceptAllMa = new JButton("Aceptar a los " + pendingMa.size());
                acceptAllMa.addActionListener(evMa -> runOwnerActionMa(groupMa, () -> {
                    for (MemberOutJt pendingMemberMa : pendingMa) {
                        cloudMa.client().approve(groupMa.id(), pendingMemberMa.id());
                    }
                }));
                bodyMa.add(left(acceptAllMa));
                bodyMa.add(Box.createVerticalStrut(8));
            }
            for (MemberOutJt memberMa : pendingMa) {
                bodyMa.add(requestRowMa(groupMa, memberMa));
                bodyMa.add(Box.createVerticalStrut(8));
            }
            bodyMa.add(Box.createVerticalStrut(10));
        }
        JPanel gridMa = null;
        if (activeMa.isEmpty()) {
            bodyMa.add(left(UiKitJtMa.sectionTitle("Equipos del grupo")));
            bodyMa.add(Box.createVerticalStrut(10));
            bodyMa.add(left(UiKitJtMa.note("Todavía no hay equipos. Comparte el código arriba; cuando alguien se una "
                    + "y lo aceptes, su equipo aparecerá aquí como un cuadrito.")));
        } else {
            activeMembersMa = activeMa;
            tilesGroupMa = groupMa;
            pickedMa.retainAll(selectableIdsMa(activeMa));
            JPanel barMa = bulkBarMa();
            tilesMa = new JPanel(new WrapLayoutMa(FlowLayout.LEFT, 14, 14));
            tilesMa.setOpaque(false);
            tilesMa.setAlignmentX(Component.LEFT_ALIGNMENT);
            fillTilesMa();

            gridMa = UiKitJtMa.roundedPanel(16);
            gridMa.setLayout(new BorderLayout(0, 10));
            gridMa.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel gridHeadMa = new JPanel();
            gridHeadMa.setOpaque(false);
            gridHeadMa.setLayout(new BoxLayout(gridHeadMa, BoxLayout.Y_AXIS));
            gridHeadMa.add(left(UiKitJtMa.sectionTitle("Pantallas de Equipos en Tiempo Real")));
            gridHeadMa.add(Box.createVerticalStrut(2));
            gridHeadMa.add(left(UiKitJtMa.subtitle("Haz clic en una pantalla para ver su detalle, aplicaciones, "
                    + "sitios e historial.")));
            if (!detailMa) {
                gridHeadMa.add(Box.createVerticalStrut(10));
                gridHeadMa.add(left(barMa));
            }
            gridMa.add(gridHeadMa, BorderLayout.NORTH);
            gridMa.add(detailMa ? UiKitJtMa.vscroll(tilesMa) : tilesMa, BorderLayout.CENTER);
        }

        groupHolderMa.removeAll();
        if (detailMa) {
            groupHolderMa.add(memberViewMa.headerJt(), BorderLayout.NORTH);
            javax.swing.JSplitPane splitMa = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT,
                    gridMa, memberViewMa.detailJt());
            splitMa.setBorder(null);
            splitMa.setOpaque(false);
            splitMa.setDividerSize(12);
            splitMa.setResizeWeight(0.52);
            splitMa.setContinuousLayout(true);
            gridMa.setMinimumSize(new Dimension(300, 200));
            memberViewMa.detailJt().setMinimumSize(new Dimension(360, 200));
            javax.swing.SwingUtilities.invokeLater(() -> splitMa.setDividerLocation(0.52));
            JPanel splitWrapMa = new JPanel(new BorderLayout());
            splitWrapMa.setOpaque(false);
            splitWrapMa.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 0, 0, 0));
            splitWrapMa.add(splitMa, BorderLayout.CENTER);
            groupHolderMa.add(splitWrapMa, BorderLayout.CENTER);
        } else {
            if (gridMa != null) {
                bodyMa.add(gridMa);
            }
            groupHolderMa.add(northMa, BorderLayout.NORTH);
            groupHolderMa.add(UiKitJtMa.vscroll(bodyMa), BorderLayout.CENTER);
        }
        groupHolderMa.revalidate();
        groupHolderMa.repaint();
    }

    /** Cierra el detalle: vuelven a verse todas las pantallas completas. */
    private void clearSelectionMa() {
        selectedMa = null;
        memberViewMa = null;
        if (lastGroupMa != null) {
            buildOwnerViewMa(lastGroupMa, lastMembersMa, null);
        }
    }

    /** Abre el detalle de un equipo al lado de las pantallas. */
    private void selectMemberMa(MemberOutJt memberMa) {
        selectedMa = memberMa;
        memberViewMa = null;
        buildOwnerViewMa(lastGroupMa, lastMembersMa, null);
    }

    // ------------------------------------------------------------------ seleccion multiple

    private static boolean selectableMa(MemberOutJt memberMa) {
        return !memberMa.controlActive() && "NONE".equals(memberMa.controlStatus());
    }

    private static Set<Long> selectableIdsMa(List<MemberOutJt> membersMa) {
        Set<Long> idsMa = new LinkedHashSet<>();
        for (MemberOutJt memberMa : membersMa) {
            if (selectableMa(memberMa)) {
                idsMa.add(memberMa.id());
            }
        }
        return idsMa;
    }

    private List<MemberOutJt> visibleMembersMa() {
        String needleMa = filterMa.trim().toLowerCase();
        List<MemberOutJt> visibleMa = new ArrayList<>();
        for (MemberOutJt memberMa : activeMembersMa) {
            if (needleMa.isEmpty() || memberMa.displayName().toLowerCase().contains(needleMa)) {
                visibleMa.add(memberMa);
            }
        }
        return visibleMa;
    }

    /** Barra: buscar por nombre, marcar todos los visibles y pedir control a los marcados con una sola solicitud. */
    private JPanel bulkBarMa() {
        JTextField searchMa = new JTextField(filterMa, 16);
        searchMa.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Buscar persona…");
        searchMa.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, IconsJtMa.of("search", 16));
        searchMa.putClientProperty(FlatClientProperties.STYLE, "arc: 999; margin: 4,12,4,12");
        searchMa.setPreferredSize(new Dimension(280, 38));
        searchMa.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent evMa) {
                changedMa();
            }

            @Override
            public void removeUpdate(DocumentEvent evMa) {
                changedMa();
            }

            @Override
            public void changedUpdate(DocumentEvent evMa) {
                changedMa();
            }

            private void changedMa() {
                filterMa = searchMa.getText();
                fillTilesMa();
            }
        });
        allBoxMa = new JCheckBox("Marcar todos");
        allBoxMa.setToolTipText("Marca a todas las personas que se ven ahora (según la búsqueda)");
        allBoxMa.addActionListener(evMa -> {
            if (syncingAllMa) {
                return;
            }
            for (MemberOutJt memberMa : visibleMembersMa()) {
                if (selectableMa(memberMa)) {
                    if (allBoxMa.isSelected()) {
                        pickedMa.add(memberMa.id());
                    } else {
                        pickedMa.remove(memberMa.id());
                    }
                }
            }
            fillTilesMa();
        });
        pickCountMa = new JLabel(" ");
        pickCountMa.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Component.accentColor");
        bulkButtonMa = new JButton("Solicitar control");
        bulkButtonMa.setToolTipText("Envía la solicitud de control a las personas marcadas");
        bulkButtonMa.putClientProperty(FlatClientProperties.STYLE,
                "background: $Component.accentColor; foreground: #FFFFFF; borderWidth: 0; "
                        + "focusedBackground: darken($Component.accentColor,10%); font: bold; margin: 7,18,7,18");
        bulkButtonMa.addActionListener(evMa -> requestControlBulkMa());
        JPanel leftBarMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        leftBarMa.setOpaque(false);
        leftBarMa.add(searchMa);
        leftBarMa.add(allBoxMa);
        JPanel rightBarMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightBarMa.setOpaque(false);
        rightBarMa.add(pickCountMa);
        rightBarMa.add(bulkButtonMa);
        rightBarMa.add(viewButtonMa());
        JPanel barMa = new JPanel(new BorderLayout(12, 0));
        barMa.setOpaque(false);
        barMa.add(leftBarMa, BorderLayout.WEST);
        barMa.add(rightBarMa, BorderLayout.EAST);
        return barMa;
    }

    /** "Ver": cuadritos muy grandes, grandes, medianos o pequeños (se recuerda la elección). */
    private JButton viewButtonMa() {
        JButton viewMa = new JButton("Ver  ▾");
        viewMa.setToolTipText("Tamaño de los cuadritos");
        viewMa.addActionListener(evMa -> {
            javax.swing.JPopupMenu menuMa = new javax.swing.JPopupMenu();
            for (TileSizeMa sizeMa : TILE_SIZES_MA) {
                javax.swing.JRadioButtonMenuItem itemMa = new javax.swing.JRadioButtonMenuItem(sizeMa.label(),
                        sizeMa == tileSizeMa);
                itemMa.addActionListener(pickMa -> {
                    tileSizeMa = sizeMa;
                    PREFS_MA.putInt("tile_size", java.util.Arrays.asList(TILE_SIZES_MA).indexOf(sizeMa));
                    thumbNumbersMa.clear();
                    fillTilesMa();
                });
                menuMa.add(itemMa);
            }
            menuMa.show(viewMa, 0, viewMa.getHeight());
        });
        return viewMa;
    }

    private void fillTilesMa() {
        tilesMa.removeAll();
        thumbLabelsMa.clear();
        List<MemberOutJt> visibleMa = visibleMembersMa();
        for (MemberOutJt memberMa : visibleMa) {
            tilesMa.add(tileMa(tilesGroupMa, memberMa));
        }
        if (visibleMa.isEmpty()) {
            tilesMa.add(UiKitJtMa.note("Nadie coincide con la búsqueda."));
        }
        tilesMa.revalidate();
        tilesMa.repaint();
        refreshPickStateMa();
    }

    private void refreshPickStateMa() {
        int countMa = pickedMa.size();
        pickCountMa.setText(countMa == 0 ? "Toca una pantalla para marcarla" : countMa + (countMa == 1 ? " marcado" : " marcados"));
        bulkButtonMa.setEnabled(countMa > 0);
        boolean allMa = true;
        boolean anyMa = false;
        for (MemberOutJt memberMa : visibleMembersMa()) {
            if (selectableMa(memberMa)) {
                anyMa = true;
                allMa &= pickedMa.contains(memberMa.id());
            }
        }
        syncingAllMa = true;
        allBoxMa.setSelected(anyMa && allMa);
        allBoxMa.setEnabled(anyMa);
        syncingAllMa = false;
    }

    private void requestControlBulkMa() {
        if (pickedMa.isEmpty() || !RuleActionsMa.unlockMa(this, accountMa)) {
            return;
        }
        GroupOutMa groupMa = tilesGroupMa;
        List<Long> idsMa = new ArrayList<>(pickedMa);
        ControlInMa controlMa = askControlMa("<html>Control sobre <b>" + idsMa.size() + " persona(s)</b>. Cada una "
                + "recibirá la solicitud y tendrá que aceptarla en su propio equipo.</html>");
        if (controlMa == null) {
            return;
        }
        UiAsyncJt.runJt(() -> {
            int failedMa = 0;
            for (Long idMa : idsMa) {
                try {
                    cloudMa.client().requestControl(groupMa.id(), idMa, controlMa);
                } catch (RuntimeException exMa) {
                    failedMa++;
                }
            }
            return failedMa;
        }, failedMa -> {
            pickedMa.clear();
            if (failedMa > 0) {
                JOptionPane.showMessageDialog(this, "Se enviaron " + (idsMa.size() - failedMa) + " solicitudes; "
                        + failedMa + " no se pudieron enviar. Inténtalo de nuevo.", "AppControl",
                        JOptionPane.WARNING_MESSAGE);
            }
            openGroupMa(groupMa);
        }, errorMa -> JOptionPane.showMessageDialog(this, errorMa, "AppControl", JOptionPane.WARNING_MESSAGE));
    }

    private static JComponent left(JComponent compMa) {
        compMa.setAlignmentX(Component.LEFT_ALIGNMENT);
        return compMa;
    }

    private JPanel requestRowMa(GroupOutMa groupMa, MemberOutJt memberMa) {
        JPanel rowMa = new JPanel(new BorderLayout(12, 0));
        rowMa.setOpaque(false);
        rowMa.putClientProperty(FlatClientProperties.STYLE, "border: 10,14,10,14,$Component.borderColor,1,12");
        rowMa.setAlignmentX(Component.LEFT_ALIGNMENT);
        rowMa.add(new JLabel("<html><b>" + memberMa.displayName() + "</b> quiere unirse al grupo</html>"),
                BorderLayout.CENTER);
        JButton okMa = new JButton("Aceptar");
        okMa.addActionListener(evMa -> runOwnerActionMa(groupMa, () -> cloudMa.client().approve(groupMa.id(),
                memberMa.id())));
        JButton noMa = new JButton("Rechazar");
        noMa.addActionListener(evMa -> runOwnerActionMa(groupMa, () -> cloudMa.client().remove(groupMa.id(),
                memberMa.id())));
        JPanel btnsMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnsMa.setOpaque(false);
        btnsMa.add(okMa);
        btnsMa.add(noMa);
        rowMa.add(btnsMa, BorderLayout.EAST);
        rowMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowMa.getPreferredSize().height + 6));
        return rowMa;
    }

    private void runOwnerActionMa(GroupOutMa groupMa, Runnable actionMa) {
        UiAsyncJt.runJt(() -> {
            actionMa.run();
            return Boolean.TRUE;
        }, okMa -> openGroupMa(groupMa), errorMa -> JOptionPane.showMessageDialog(this, errorMa, "AppControl",
                JOptionPane.WARNING_MESSAGE));
    }

    // ------------------------------------------------------------------ el cuadrito de un equipo

    private JPanel tileMa(GroupOutMa groupMa, MemberOutJt memberMa) {
        JPanel tileMa = new JPanel(new BorderLayout(0, 6));
        tileMa.setOpaque(false);
        boolean chosenTileMa = selectedMa != null && selectedMa.id().equals(memberMa.id());
        tileMa.putClientProperty(FlatClientProperties.STYLE, chosenTileMa
                ? "border: 14,14,14,14,$Component.accentColor,2,14"
                : "border: 14,14,14,14,$Component.borderColor,1,14");
        tileMa.setPreferredSize(new Dimension(tileSizeMa.width(), tileSizeMa.height()));
        tileMa.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel iconMa = new JLabel(new MonitorIconMa(), JLabel.CENTER);
        if (memberMa.controlActive()) {
            javax.swing.ImageIcon liveMa = thumbsMa.get(memberMa.id());
            if (liveMa != null) {
                iconMa.setIcon(liveMa);
            }
            iconMa.setToolTipText("Pantalla en vivo · haz clic para abrir el equipo");
            thumbLabelsMa.put(memberMa.id(), iconMa);
        }
        boolean recentMa = memberMa.lastSeen() != null
                && java.time.Duration.between(memberMa.lastSeen(), java.time.Instant.now()).toMinutes() < 3;
        String suffixMa = memberMa.controlActive() ? (recentMa ? " (Activo)" : " (Inactivo)") : "";
        JLabel nameMa = new JLabel(memberMa.displayName() + suffixMa, JLabel.CENTER);
        nameMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        JPanel chipMa = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        chipMa.setOpaque(false);
        chipMa.add(statusChipMa(memberMa));
        JPanel bottomMa = new JPanel(new GridLayout(2, 1, 0, 4));
        bottomMa.setOpaque(false);
        bottomMa.add(nameMa);
        bottomMa.add(chipMa);
        tileMa.add(iconMa, BorderLayout.CENTER);
        tileMa.add(bottomMa, BorderLayout.SOUTH);
        if (memberMa.lastSeen() != null) {
            tileMa.setToolTipText("Visto " + FmtJt.agoJtMa(memberMa.lastSeen()));
        }
        MouseAdapter clickMa = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evMa) {
                onTileClickMa(groupMa, memberMa);
            }
        };
        addClickMa(tileMa, clickMa);
        if (!memberMa.controlActive() && "NONE".equals(memberMa.controlStatus())) {
            // Botón propio (sin el clic de marcar): abre el historial de lo que se hizo mientras hubo control.
            JButton historyBtnMa = new JButton("Ver historial");
            historyBtnMa.setToolTipText("Lo que se usó en ese equipo mientras estuvo bajo tu control");
            historyBtnMa.putClientProperty(FlatClientProperties.STYLE, "margin: 3,10,3,10");
            historyBtnMa.setFocusable(false);
            historyBtnMa.addActionListener(evMa -> selectMemberMa(memberMa));
            JPanel historyWrapMa = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            historyWrapMa.setOpaque(false);
            historyWrapMa.add(historyBtnMa);
            bottomMa.setLayout(new GridLayout(3, 1, 0, 4));
            bottomMa.add(historyWrapMa);
        }
        if (selectableMa(memberMa)) {
            JCheckBox pickMa = new JCheckBox("Marcar", pickedMa.contains(memberMa.id()));
            pickMa.setOpaque(false);
            pickMa.addActionListener(evMa -> {
                if (pickMa.isSelected()) {
                    pickedMa.add(memberMa.id());
                } else {
                    pickedMa.remove(memberMa.id());
                }
                refreshPickStateMa();
            });
            JPanel pickRowMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            pickRowMa.setOpaque(false);
            pickRowMa.add(pickMa);
            tileMa.add(pickRowMa, BorderLayout.NORTH);
        } else if (memberMa.controlActive()) {
            JPanel liveRowMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            liveRowMa.setOpaque(false);
            liveRowMa.add(recentMa ? new ChipMa("● En vivo", ChipMa.KindJtMa.OK)
                    : new ChipMa("Inactivo · " + (memberMa.lastSeen() == null ? "sin datos"
                            : FmtJt.agoJtMa(memberMa.lastSeen())), ChipMa.KindJtMa.NEUTRAL));
            addClickMa(liveRowMa, clickMa);
            tileMa.add(liveRowMa, BorderLayout.NORTH);
        }
        // «Quitar» siempre a la vista: el dueño puede sacar a cualquier persona del grupo (pide su contraseña).
        JButton removeBtnMa = new JButton("Quitar");
        removeBtnMa.setToolTipText("Quitar a " + memberMa.displayName() + " del grupo (pide tu contraseña)");
        removeBtnMa.putClientProperty(FlatClientProperties.STYLE,
                "margin: 2,8,2,8; foreground: #C62828; borderColor: #F5B5B5");
        removeBtnMa.setFocusable(false);
        removeBtnMa.addActionListener(evMa -> removeMemberMa(groupMa, memberMa));
        JPanel northMa = new JPanel(new BorderLayout());
        northMa.setOpaque(false);
        Component oldNorthMa = ((BorderLayout) tileMa.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        if (oldNorthMa != null) {
            northMa.add(oldNorthMa, BorderLayout.WEST);
        }
        northMa.add(removeBtnMa, BorderLayout.EAST);
        tileMa.add(northMa, BorderLayout.NORTH);
        return tileMa;
    }

    /** Sacar a una persona del grupo: se explica qué pasa y se pide la contraseña. */
    private void removeMemberMa(GroupOutMa groupMa, MemberOutJt memberMa) {
        String textMa = "¿Quitar a «" + memberMa.displayName() + "» del grupo «" + groupMa.name() + "»?" + "\n\n"
                + "Se termina el control, se borran sus reglas del grupo y su historial de control. "
                + "La persona puede volver a unirse con el código.";
        if (JOptionPane.showConfirmDialog(this, textMa, "Quitar del grupo", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION || !RuleActionsMa.unlockMa(this, accountMa)) {
            return;
        }
        pickedMa.remove(memberMa.id());
        if (selectedMa != null && selectedMa.id().equals(memberMa.id())) {
            selectedMa = null;
            memberViewMa = null;
        }
        runOwnerActionMa(groupMa, () -> cloudMa.client().remove(groupMa.id(), memberMa.id()));
    }

    private static void addClickMa(Component compMa, MouseAdapter clickMa) {
        compMa.addMouseListener(clickMa);
        if (compMa instanceof java.awt.Container contMa) {
            for (Component childMa : contMa.getComponents()) {
                addClickMa(childMa, clickMa);
            }
        }
    }

    private static ChipMa statusChipMa(MemberOutJt memberMa) {
        if (memberMa.controlActive()) {
            return new ChipMa("TIMED".equals(memberMa.controlKind()) && memberMa.controlUntil() != null
                    ? "Controlado hasta " + WHEN_MA.format(memberMa.controlUntil()) : "Controlado · permanente",
                    ChipMa.KindJtMa.OK);
        }
        return switch (memberMa.controlStatus()) {
            case "REQUESTED" -> new ChipMa("Esperando que acepte", ChipMa.KindJtMa.WARN);
            case "ACCEPTED" -> new ChipMa("Empieza " + WHEN_MA.format(memberMa.controlFrom()), ChipMa.KindJtMa.WARN);
            default -> new ChipMa(memberMa.hasHistory() ? "Control terminado" : "Sin control",
                    ChipMa.KindJtMa.NEUTRAL);
        };
    }

    private void onTileClickMa(GroupOutMa groupMa, MemberOutJt memberMa) {
        if (memberMa.controlActive()) {
            selectMemberMa(memberMa);
        } else if ("REQUESTED".equals(memberMa.controlStatus())) {
            JOptionPane.showMessageDialog(this, memberMa.displayName() + " todavía no aceptó la solicitud. Tiene que "
                    + "aceptarla en su propio equipo.", "Solicitud enviada", JOptionPane.INFORMATION_MESSAGE);
        } else if ("ACCEPTED".equals(memberMa.controlStatus())) {
            JOptionPane.showMessageDialog(this, "El control empieza a las " + WHEN_MA.format(memberMa.controlFrom())
                    + ".", "Control programado", JOptionPane.INFORMATION_MESSAGE);
        } else {
            // Un toque solo marca (o desmarca) a la persona; el control se pide con el botón de arriba.
            togglePickMa(memberMa);
        }
    }

    private void togglePickMa(MemberOutJt memberMa) {
        if (!pickedMa.remove(memberMa.id())) {
            pickedMa.add(memberMa.id());
        }
        fillTilesMa();
    }

    // ------------------------------------------------------------------ solicitar control

    private void requestControlMa(GroupOutMa groupMa, MemberOutJt memberMa) {
        if (!RuleActionsMa.unlockMa(this, accountMa)) {
            return;
        }
        ControlInMa sendMa = askControlMa("<html>Control sobre <b>" + memberMa.displayName() + "</b>. Tendrá que "
                + "aceptarlo en su equipo.</html>");
        if (sendMa != null) {
            runOwnerActionMa(groupMa, () -> cloudMa.client().requestControl(groupMa.id(), memberMa.id(), sendMa));
        }
    }

    /** Pregunta tipo de control (permanente u horario) y devuelve la solicitud, o null si se cancela. */
    private ControlInMa askControlMa(String headerHtmlMa) {
        JRadioButton permanentMa = new JRadioButton("Permanente (hasta que se termine)", true);
        JRadioButton timedMa = new JRadioButton("Solo por un horario de hoy");
        ButtonGroup groupBtnMa = new ButtonGroup();
        groupBtnMa.add(permanentMa);
        groupBtnMa.add(timedMa);
        JSpinner fromMa = timeSpinnerMa(0);
        JSpinner toMa = timeSpinnerMa(60);
        JPanel timeRowMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        timeRowMa.add(new JLabel("De"));
        timeRowMa.add(fromMa);
        timeRowMa.add(new JLabel("a"));
        timeRowMa.add(toMa);
        JCheckBox leaveMa = new JCheckBox("La persona puede terminar el control o salir del grupo");
        JPanel panelMa = new JPanel(new GridLayout(0, 1, 0, 4));
        panelMa.add(new JLabel(headerHtmlMa));
        panelMa.add(permanentMa);
        panelMa.add(timedMa);
        panelMa.add(timeRowMa);
        panelMa.add(leaveMa);
        panelMa.add(UiKitJtMa.subtitle("Con horario, al terminar se quitan solas todas las reglas."));
        if (JOptionPane.showConfirmDialog(this, panelMa, "Solicitar control", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        if (permanentMa.isSelected()) {
            return new ControlInMa("PERMANENT", null, null, leaveMa.isSelected());
        }
        Instant fromInstantMa = todayAtMa(fromMa);
        Instant toInstantMa = todayAtMa(toMa);
        if (!toInstantMa.isAfter(fromInstantMa)) {
            toInstantMa = toInstantMa.plusSeconds(24 * 3600L);
        }
        return new ControlInMa("TIMED", fromInstantMa, toInstantMa, leaveMa.isSelected());
    }

    private static JSpinner timeSpinnerMa(int plusMinutesMa) {
        java.util.Calendar calMa = java.util.Calendar.getInstance();
        calMa.add(java.util.Calendar.MINUTE, plusMinutesMa);
        JSpinner spinnerMa = new JSpinner(new SpinnerDateModel(calMa.getTime(), null, null,
                java.util.Calendar.MINUTE));
        spinnerMa.setEditor(new JSpinner.DateEditor(spinnerMa, "HH:mm"));
        return spinnerMa;
    }

    private static Instant todayAtMa(JSpinner spinnerMa) {
        java.util.Calendar timeMa = java.util.Calendar.getInstance();
        timeMa.setTime((Date) spinnerMa.getValue());
        java.util.Calendar dayMa = java.util.Calendar.getInstance();
        dayMa.set(java.util.Calendar.HOUR_OF_DAY, timeMa.get(java.util.Calendar.HOUR_OF_DAY));
        dayMa.set(java.util.Calendar.MINUTE, timeMa.get(java.util.Calendar.MINUTE));
        dayMa.set(java.util.Calendar.SECOND, 0);
        dayMa.set(java.util.Calendar.MILLISECOND, 0);
        return dayMa.toInstant();
    }

    // ------------------------------------------------------------------ vista de quien es controlado

    private static final DateTimeFormatter HOUR_MA = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY_HOUR_MA = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());

    /** "hoy 22:15" si es de hoy; si no, "24/11 22:15". */
    private static String whenTextMa(Instant whenMa) {
        boolean todayMa = whenMa.atZone(ZoneId.systemDefault()).toLocalDate()
                .equals(java.time.LocalDate.now(ZoneId.systemDefault()));
        return todayMa ? "hoy " + HOUR_MA.format(whenMa) : DAY_HOUR_MA.format(whenMa);
    }

    /** Un cuadro con título, para agrupar información (Permisos concedidos, Información de la sesión…). */
    private static JPanel infoCardMa(String titleMa, JComponent... rowsMa) {
        JPanel cardMa = UiKitJtMa.roundedPanel(18);
        cardMa.setLayout(new BoxLayout(cardMa, BoxLayout.Y_AXIS));
        JLabel headMa = new JLabel(titleMa);
        headMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        headMa.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardMa.add(headMa);
        cardMa.add(Box.createVerticalStrut(10));
        for (JComponent rowMa : rowsMa) {
            rowMa.setAlignmentX(Component.LEFT_ALIGNMENT);
            cardMa.add(rowMa);
            cardMa.add(Box.createVerticalStrut(6));
        }
        cardMa.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, cardMa.getPreferredSize().height + 8));
        return cardMa;
    }

    private void buildMemberGroupViewMa(GroupOutMa groupMa) {
        CloudLinkJt linkMa = null;
        for (CloudLinkJt candidateMa : cloudMa.linksMa()) {
            if (candidateMa.getMemberId().equals(groupMa.memberId())) {
                linkMa = candidateMa;
            }
        }
        JPanel bodyMa = new JPanel();
        bodyMa.setOpaque(false);
        bodyMa.setLayout(new BoxLayout(bodyMa, BoxLayout.Y_AXIS));

        // ---- fila de arriba: volver, y el aviso de quién está viendo esta pantalla
        JPanel topRowMa = new JPanel(new BorderLayout(12, 0));
        topRowMa.setOpaque(false);
        JPanel backWrapMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        backWrapMa.setOpaque(false);
        JButton backMa = backToListMa();
        backMa.setText("←  Volver a Grupos");
        backWrapMa.add(backMa);
        topRowMa.add(backWrapMa, BorderLayout.WEST);
        String viewerMa = com.appcontrol.cloud.ScreenShareJt.viewerNowJt();
        if (!viewerMa.isEmpty() && linkMa != null) {
            JPanel bannerMa = UiKitJtMa.roundedPanel(12);
            bannerMa.setLayout(new BorderLayout(16, 0));
            JPanel bannerTextMa = new JPanel(new GridLayout(2, 1, 0, 2));
            bannerTextMa.setOpaque(false);
            JLabel bannerTitleMa = new JLabel("<html><span style='color:#C2410C'>●</span>  <b>" + viewerMa
                    + " está viendo tu pantalla</b></html>");
            bannerTextMa.add(bannerTitleMa);
            Instant untilBannerMa = linkMa.getControlUntil();
            long leftBannerMa = untilBannerMa == null ? -1
                    : Math.max(0, Duration.between(cloudMa.nowMa(), untilBannerMa).toMinutes());
            bannerTextMa.add(UiKitJtMa.subtitle(untilBannerMa == null ? "Control activo, sin fecha de fin"
                    : "Control activo hasta las " + HOUR_MA.format(untilBannerMa) + " · Quedan " + leftBannerMa + " min"));
            JButton stopMa = new JButton("Detener");
            stopMa.setToolTipText("Corta al instante el mouse y teclado remotos");
            stopMa.setEnabled(com.appcontrol.cloud.ScreenShareJt.controllingNowJt());
            stopMa.addActionListener(evMa -> {
                com.appcontrol.cloud.ScreenShareJt.cutInputNowJt();
                stopMa.setEnabled(false);
            });
            bannerMa.add(bannerTextMa, BorderLayout.CENTER);
            bannerMa.add(stopMa, BorderLayout.EAST);
            topRowMa.add(bannerMa, BorderLayout.EAST);
        }
        bodyMa.add(left(topRowMa));
        bodyMa.add(Box.createVerticalStrut(18));

        // ---- título del grupo
        JPanel titleRowMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleRowMa.setOpaque(false);
        titleRowMa.add(UiKitJtMa.title("Grupo: " + groupMa.name()));
        if (linkMa != null) {
            titleRowMa.add(new ChipMa("Propietario: " + linkMa.getOwnerName(), ChipMa.KindJtMa.NEUTRAL));
        }
        titleRowMa.add(switcherMa(groupMa));
        titleRowMa.add(refreshButtonMa(this::refreshOpenGroupMa));
        bodyMa.add(left(titleRowMa));
        bodyMa.add(Box.createVerticalStrut(14));

        if ("PENDING".equals(groupMa.status())) {
            bodyMa.add(left(UiKitJtMa.note("Tu solicitud para unirte está enviada. Cuando quien creó el grupo te "
                    + "acepte, verás aquí el estado.")));
        } else if (linkMa == null) {
            bodyMa.add(left(UiKitJtMa.note("Sin datos guardados todavía. Conecta a internet para actualizar.")));
        } else {
            boolean acceptedMa = "ACCEPTED".equals(linkMa.getControlStatus());
            String stateMa = acceptedMa ? "Bajo control" : "REQUESTED".equals(linkMa.getControlStatus())
                    ? "Solicitud de control pendiente" : "Sin control";
            JPanel bandMa = UiKitJtMa.roundedPanel(12);
            bandMa.setLayout(new BorderLayout());
            JLabel bandLabelMa = new JLabel("◷   Estado de este equipo: " + stateMa);
            bandLabelMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
            bandMa.add(bandLabelMa, BorderLayout.CENTER);
            bandMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, bandMa.getPreferredSize().height + 6));
            bodyMa.add(left(bandMa));
            bodyMa.add(Box.createVerticalStrut(14));

            Instant nowMa = cloudMa.nowMa();
            Instant untilMa = linkMa.getControlUntil();
            boolean timedMa = acceptedMa && "TIMED".equals(linkMa.getControlKind()) && untilMa != null;
            Instant startedMa = linkMa.getControlStarted() != null ? linkMa.getControlStarted()
                    : linkMa.getControlFrom();

            // ---- tarjeta grande: quién controla y hasta cuándo
            JPanel bigMa = UiKitJtMa.roundedPanel(22);
            bigMa.setLayout(new BoxLayout(bigMa, BoxLayout.Y_AXIS));
            JLabel bigTitleMa;
            if (acceptedMa) {
                bigTitleMa = new JLabel(timedMa
                        ? "Controlado por " + linkMa.getOwnerName() + " hasta las " + HOUR_MA.format(untilMa)
                        : "Controlado por " + linkMa.getOwnerName() + " · sin fecha de fin");
            } else if ("REQUESTED".equals(linkMa.getControlStatus())) {
                bigTitleMa = new JLabel(linkMa.getOwnerName() + " solicita controlar este equipo");
            } else {
                bigTitleMa = new JLabel("Nadie controla este equipo");
            }
            bigTitleMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +10");
            bigTitleMa.setAlignmentX(Component.LEFT_ALIGNMENT);
            bigMa.add(bigTitleMa);
            if (timedMa) {
                long leftMa = Math.max(0, Duration.between(nowMa, untilMa).toMinutes());
                JPanel chipRowMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                chipRowMa.setOpaque(false);
                chipRowMa.add(new ChipMa("◷  Quedan " + leftMa + " minutos (finaliza a las " + HOUR_MA.format(untilMa)
                        + ")", ChipMa.KindJtMa.OK));
                chipRowMa.setAlignmentX(Component.LEFT_ALIGNMENT);
                bigMa.add(Box.createVerticalStrut(10));
                bigMa.add(chipRowMa);
            }
            if (acceptedMa) {
                String sessionMa = (startedMa == null ? "" : "Sesión iniciada " + whenTextMa(startedMa) + " · ")
                        + (timedMa && startedMa != null ? "Duración máxima: "
                                + Math.max(1, Duration.between(startedMa, untilMa).toMinutes()) + " minutos"
                                : "Sin fecha de fin");
                JLabel sessionLabelMa = UiKitJtMa.subtitle(sessionMa);
                sessionLabelMa.setAlignmentX(Component.LEFT_ALIGNMENT);
                bigMa.add(Box.createVerticalStrut(10));
                bigMa.add(sessionLabelMa);
            }
            bigMa.add(Box.createVerticalStrut(14));
            JPanel buttonsMa = controlButtonsMa(groupMa, linkMa);
            buttonsMa.setAlignmentX(Component.LEFT_ALIGNMENT);
            bigMa.add(buttonsMa);
            bigMa.setAlignmentX(Component.LEFT_ALIGNMENT);
            bigMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, bigMa.getPreferredSize().height + 8));
            bodyMa.add(bigMa);
            bodyMa.add(Box.createVerticalStrut(14));

            if (acceptedMa) {
                bodyMa.add(infoCardMa("Permisos concedidos",
                        new JLabel("✔   Compartiendo pantalla (miniatura en tiempo real)"),
                        new JLabel("✔   Permiso de mouse y teclado (control remoto)"),
                        new JLabel("✔   Registro de aplicaciones usadas y tiempo"),
                        new JLabel("✔   Registro de sitios web visitados (solo dominio)"),
                        UiKitJtMa.subtitle(linkMa.isMemberCanLeave()
                                ? "ⓘ  Puedes pulsar «Terminar control» en cualquier momento."
                                : "ⓘ  Solo " + linkMa.getOwnerName() + " puede terminar este control.")));
                bodyMa.add(Box.createVerticalStrut(14));
                bodyMa.add(infoCardMa("Información de la sesión",
                        new JLabel("•  Inicio: " + (startedMa == null ? "—" : cap(whenTextMa(startedMa)))),
                        new JLabel("•  Límite: " + (timedMa ? cap(whenTextMa(untilMa)) : "sin fecha de fin")),
                        new JLabel("•  Conexión: " + (cloudMa.isOnlineMa() ? "Activa (nube de Azure)"
                                : "Sin conexión (se usa lo último guardado)"))));
                bodyMa.add(Box.createVerticalStrut(14));
            }

            List<GroupRuleJtMa> rulesMa = new ArrayList<>();
            for (GroupRuleJtMa ruleMa : cloudMa.groupRulesMa()) {
                if (ruleMa.memberIdMa().equals(linkMa.getMemberId())) {
                    rulesMa.add(ruleMa);
                }
            }
            if (!rulesMa.isEmpty()) {
                JLabel[] ruleLabelsMa = new JLabel[rulesMa.size()];
                for (int posMa = 0; posMa < rulesMa.size(); posMa++) {
                    ruleLabelsMa[posMa] = new JLabel("•  " + rulesMa.get(posMa).getDisplayName() + " — "
                            + rulesMa.get(posMa).summaryMa());
                }
                bodyMa.add(infoCardMa("Reglas puestas por " + linkMa.getOwnerName(), ruleLabelsMa));
            }
        }
        groupHolderMa.removeAll();
        groupHolderMa.add(UiKitJtMa.vscroll(bodyMa), BorderLayout.CENTER);
        groupHolderMa.revalidate();
        groupHolderMa.repaint();
    }

    private static String cap(String textMa) {
        return textMa.isEmpty() ? textMa : Character.toUpperCase(textMa.charAt(0)) + textMa.substring(1);
    }

    private ChipMa controlChipMa(CloudLinkJt linkMa) {
        return switch (linkMa.getControlStatus()) {
            case "ACCEPTED" -> new ChipMa("Controlado por " + linkMa.getOwnerName() + " · "
                    + ControlPromptJt.describeJt(linkMa), ChipMa.KindJtMa.OK);
            case "REQUESTED" -> new ChipMa(linkMa.getOwnerName() + " solicita controlar este equipo",
                    ChipMa.KindJtMa.WARN);
            default -> new ChipMa("Nadie controla este equipo", ChipMa.KindJtMa.NEUTRAL);
        };
    }

    private JPanel controlButtonsMa(GroupOutMa groupMa, CloudLinkJt linkMa) {
        JPanel btnsMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnsMa.setOpaque(false);
        if ("REQUESTED".equals(linkMa.getControlStatus())) {
            JButton answerMa = new JButton("Responder solicitud…");
            answerMa.addActionListener(evMa -> ControlPromptJt.askJt(this, cloudMa, accountMa, linkMa,
                    () -> openGroupMa(groupMa)));
            btnsMa.add(answerMa);
        }
        boolean lockedMa = "ACCEPTED".equals(linkMa.getControlStatus()) && !linkMa.isMemberCanLeave();
        if (lockedMa) {
            btnsMa.add(UiKitJtMa.subtitle("<html>" + linkMa.getOwnerName() + " no permite terminar este control ni "
                    + "salir del grupo desde aquí.<br>Solo " + linkMa.getOwnerName() + " puede quitarlo.</html>"));
            return btnsMa;
        }
        if ("ACCEPTED".equals(linkMa.getControlStatus())) {
            JButton endMa = new JButton("⊘  Terminar control");
            endMa.putClientProperty(FlatClientProperties.STYLE,
                    "foreground: #C62828; borderColor: #F5B5B5; focusedBorderColor: #C62828");
            endMa.addActionListener(evMa -> memberAction(groupMa, linkMa, true));
            btnsMa.add(endMa);
        }
        JButton leaveMa = new JButton("Salir del grupo");
        leaveMa.addActionListener(evMa -> memberAction(groupMa, linkMa, false));
        btnsMa.add(leaveMa);
        return btnsMa;
    }

    /** Terminar el control o salir del grupo: pide la contraseña y la nube decide si esta permitido. */
    private void memberAction(GroupOutMa groupMa, CloudLinkJt linkMa, boolean endOnlyMa) {
        String questionMa = endOnlyMa ? "¿Terminar el control de " + linkMa.getOwnerName() + " sobre este equipo?"
                : "¿Salir del grupo «" + groupMa.name() + "»?";
        if (JOptionPane.showConfirmDialog(this, questionMa, "Confirmar", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION || !RuleActionsMa.unlockMa(this, accountMa)) {
            return;
        }
        UiAsyncJt.runJt(() -> {
            if (endOnlyMa) {
                cloudMa.client().endControl(linkMa.getMemberId());
            } else {
                cloudMa.client().leave(linkMa.getMemberId());
            }
            cloudMa.syncNowMa();
            return Boolean.TRUE;
        }, okMa -> {
            if (endOnlyMa) {
                openGroupMa(groupMa);
            } else {
                openGroupMa = null;
                showViewMa(LIST_MA);
                lastListMsMa = 0;
                loadGroupsMa();
            }
        }, errorMa -> JOptionPane.showMessageDialog(this, errorMa, "AppControl", JOptionPane.WARNING_MESSAGE));
    }

    // ------------------------------------------------------------------ icono de computadora

    /** Un monitor sencillo dibujado con el color de acento del tema. */
    private static final class MonitorIconMa implements Icon {
        @Override
        public void paintIcon(Component cMa, Graphics gMa, int xMa, int yMa) {
            Graphics2D g2Ma = (Graphics2D) gMa.create();
            g2Ma.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color accentMa = UIManager.getColor("Component.accentColor");
            g2Ma.setColor(accentMa != null ? accentMa : new Color(0, 103, 192));
            g2Ma.fillRoundRect(xMa + 4, yMa + 2, 52, 34, 8, 8);
            g2Ma.setColor(new Color(255, 255, 255, 70));
            g2Ma.fillRoundRect(xMa + 8, yMa + 6, 44, 26, 5, 5);
            g2Ma.setColor(accentMa != null ? accentMa : new Color(0, 103, 192));
            g2Ma.fillRect(xMa + 26, yMa + 36, 8, 7);
            g2Ma.fillRoundRect(xMa + 16, yMa + 42, 28, 5, 4, 4);
            g2Ma.dispose();
        }

        @Override
        public int getIconWidth() {
            return 60;
        }

        @Override
        public int getIconHeight() {
            return 50;
        }
    }

    /** Para pruebas y capturas: muestra la vista del dueño con estos miembros, sin llamar a la nube. */
    void previewOwnerMa(GroupOutMa groupMa, List<MemberOutJt> membersMa) {
        openGroupMa = groupMa;
        buildOwnerViewMa(groupMa, membersMa, null);
        showViewMa(GROUP_MA);
    }

    /** Para pruebas y capturas: la vista del dueño con el detalle de un equipo abierto al lado de las pantallas. */
    void previewSelectedMa(GroupOutMa groupMa, List<MemberOutJt> membersMa, long memberIdMa) {
        openGroupMa = groupMa;
        for (MemberOutJt memberMa : membersMa) {
            if (memberMa.id() == memberIdMa) {
                selectedMa = memberMa;
            }
        }
        memberViewMa = null;
        buildOwnerViewMa(groupMa, membersMa, null);
        showViewMa(GROUP_MA);
    }

    /** Para pruebas y capturas: la pantalla de quien está siendo controlado. */
    void previewMemberMa(GroupOutMa groupMa) {
        openGroupMa = groupMa;
        buildMemberGroupViewMa(groupMa);
        showViewMa(GROUP_MA);
    }

    /** Para pruebas: ¿el texto del error indica que no hay conexion? */
    static boolean isOfflineMa(Throwable exMa) {
        return exMa instanceof CloudExceptionMa cloudMa && cloudMa.offline();
    }
}
