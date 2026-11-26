package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.appcontrol.cloud.CloudLinkJt;
import com.appcontrol.cloud.CloudServiceMa;
import com.appcontrol.service.AccountServiceMa;
import com.formdev.flatlaf.FlatClientProperties;

/**
 * Pregunta a la persona si acepta que otra controle este equipo. Dice con claridad qué se permite, qué se protege y
 * cuándo termina; aceptar pide la contraseña de esta cuenta. Sin «Aceptar y Permitir» no se aplica nada.
 */
final class ControlPromptJt {

    private static final DateTimeFormatter WHEN_JT = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter HOUR_JT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private ControlPromptJt() {
    }

    static String describeJt(CloudLinkJt linkJt) {
        return "TIMED".equals(linkJt.getControlKind()) && linkJt.getControlUntil() != null
                ? "hasta el " + WHEN_JT.format(linkJt.getControlUntil()) : "sin fecha de fin";
    }

    private static JPanel bulletsJt(String titleJt, String markJt, String... linesJt) {
        JPanel boxJt = new JPanel();
        boxJt.setOpaque(false);
        boxJt.setLayout(new BoxLayout(boxJt, BoxLayout.Y_AXIS));
        JLabel headJt = new JLabel(markJt + "  " + titleJt);
        headJt.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        headJt.setAlignmentX(Component.LEFT_ALIGNMENT);
        boxJt.add(headJt);
        boxJt.add(Box.createVerticalStrut(4));
        for (String lineJt : linesJt) {
            JLabel itemJt = new JLabel("<html><body style='width:430px'>•  " + lineJt + "</body></html>");
            itemJt.setBorder(BorderFactory.createEmptyBorder(1, 22, 1, 0));
            itemJt.setAlignmentX(Component.LEFT_ALIGNMENT);
            boxJt.add(itemJt);
        }
        return boxJt;
    }

    /** El contenido de la solicitud. chooseJt recibe 0 = aceptar, 1 = rechazar, 2 = decidir después. */
    static JPanel bodyJt(CloudLinkJt linkJt, Instant nowJt, java.util.function.IntConsumer chooseJt) {
        boolean timedJt = "TIMED".equals(linkJt.getControlKind()) && linkJt.getControlUntil() != null;
        String untilTextJt = timedJt ? HOUR_JT.format(linkJt.getControlUntil()) : "";
        String subtitleJt;
        if (timedJt) {
            Instant fromJt = linkJt.getControlFrom() == null || linkJt.getControlFrom().isAfter(nowJt) ? nowJt
                    : linkJt.getControlFrom();
            long totalJt = Math.max(1, Duration.between(fromJt, linkJt.getControlUntil()).toMinutes());
            long leftJt = Math.max(0, Duration.between(nowJt, linkJt.getControlUntil()).toMinutes());
            subtitleJt = "Hasta las " + untilTextJt + " (" + totalJt + " minutos) · Quedan " + leftJt + " min";
        } else {
            subtitleJt = "Sin fecha de fin · hasta que se termine el control";
        }
        String canLeaveJt = linkJt.isMemberCanLeave()
                ? "Podrás terminar el control o salir del grupo cuando quieras."
                : "Solo quien creó el grupo podrá terminar este control.";


        JLabel iconJt = new JLabel("i", JLabel.CENTER);
        iconJt.setOpaque(true);
        iconJt.setBackground(ThemeManagerMa.accentColorJt());
        iconJt.setForeground(java.awt.Color.WHITE);
        iconJt.putClientProperty(FlatClientProperties.STYLE, "font: bold +6");
        iconJt.setPreferredSize(new Dimension(44, 44));
        JPanel iconWrapJt = new JPanel(new java.awt.GridBagLayout());
        iconWrapJt.setOpaque(false);
        iconWrapJt.add(iconJt);
        iconJt.putClientProperty(FlatClientProperties.STYLE, "font: bold +6; arc: 999");

        JLabel titleJt = new JLabel(linkJt.getOwnerName() + " solicita controlar este equipo");
        titleJt.putClientProperty(FlatClientProperties.STYLE, "font: bold +5");
        JPanel headTextJt = new JPanel(new BorderLayout(0, 4));
        headTextJt.setOpaque(false);
        headTextJt.add(titleJt, BorderLayout.NORTH);
        headTextJt.add(UiKitJtMa.subtitle(subtitleJt), BorderLayout.SOUTH);
        JPanel headJt = new JPanel(new BorderLayout(14, 0));
        headJt.setOpaque(false);
        headJt.add(iconWrapJt, BorderLayout.WEST);
        headJt.add(headTextJt, BorderLayout.CENTER);

        JPanel infoJt = UiKitJtMa.roundedPanel(16);
        infoJt.setLayout(new BoxLayout(infoJt, BoxLayout.Y_AXIS));
        infoJt.add(bulletsJt("¿Qué se permitirá?", "✔",
                "Poner reglas de bloqueo y ver tus aplicaciones usadas y sitios web (solo el dominio).",
                "Ver tu pantalla en vivo: una miniatura mientras tenga abierto el grupo y en grande si abre tu equipo.",
                "Usar tu mouse y tu teclado, sin volver a preguntarte. Verás un aviso con el botón «Detener»."));
        infoJt.add(Box.createVerticalStrut(10));
        infoJt.add(bulletsJt("¿Qué está protegido?", "◆",
                "No se guardan imágenes de tu pantalla ni lo que se escribe: solo se ve en vivo mientras lo miran.",
                "De tu actividad solo se guarda el programa o el dominio (nunca la dirección completa) y la hora.",
                canLeaveJt));
        if (timedJt) {
            infoJt.add(Box.createVerticalStrut(10));
            JLabel endsJt = new JLabel("El control finalizará automáticamente a las " + untilTextJt + ".");
            endsJt.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            endsJt.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
            endsJt.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoJt.add(endsJt);
        }

        JLabel questionJt = new JLabel(timedJt ? "¿Aceptas otorgar el control temporal hasta las " + untilTextJt + "?"
                : "¿Aceptas otorgar el control a " + linkJt.getOwnerName() + "?");
        questionJt.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");

        JButton laterJt = new JButton("Decidir después");
        laterJt.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        laterJt.addActionListener(evJt -> chooseJt.accept(2));
        JButton declineJt = new JButton("Rechazar");
        declineJt.addActionListener(evJt -> chooseJt.accept(1));
        JButton acceptJt = new JButton("Aceptar y Permitir");
        acceptJt.putClientProperty(FlatClientProperties.STYLE,
                "background: $Component.accentColor; foreground: #FFFFFF; borderWidth: 0; focusedBackground: darken($Component.accentColor,10%); font: bold");
        acceptJt.addActionListener(evJt -> chooseJt.accept(0));
        JPanel buttonsJt = new JPanel(new BorderLayout());
        buttonsJt.setOpaque(false);
        buttonsJt.add(laterJt, BorderLayout.WEST);
        JPanel rightJt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightJt.setOpaque(false);
        rightJt.add(declineJt);
        rightJt.add(acceptJt);
        buttonsJt.add(rightJt, BorderLayout.EAST);

        JPanel bodyJt = new JPanel(new BorderLayout(0, 14));
        bodyJt.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));
        JPanel centerJt = new JPanel(new BorderLayout(0, 14));
        centerJt.setOpaque(false);
        centerJt.add(infoJt, BorderLayout.CENTER);
        centerJt.add(questionJt, BorderLayout.SOUTH);
        bodyJt.add(headJt, BorderLayout.NORTH);
        bodyJt.add(centerJt, BorderLayout.CENTER);
        bodyJt.add(buttonsJt, BorderLayout.SOUTH);

        return bodyJt;
    }

    /** Muestra la solicitud. "afterJt" se ejecuta al terminar (aceptar, rechazar) para refrescar la pantalla. */
    static void askJt(Component parentJt, CloudServiceMa cloudJt, AccountServiceMa accountJt, CloudLinkJt linkJt,
            Runnable afterJt) {
        Window ownerJt = SwingUtilities.getWindowAncestor(parentJt);
        JDialog dialogJt = new JDialog(ownerJt, "Solicitud de Control", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        int[] choiceJt = {2};
        dialogJt.setContentPane(bodyJt(linkJt, cloudJt.nowMa(), pickedJt -> {
            choiceJt[0] = pickedJt;
            dialogJt.dispose();
        }));
        dialogJt.getRootPane().setDefaultButton(null);
        dialogJt.pack();
        dialogJt.setLocationRelativeTo(parentJt);
        dialogJt.setVisible(true);

        if (choiceJt[0] == 0) {
            if (!RuleActionsMa.unlockMa(parentJt, accountJt)) {
                return;
            }
            answerJt(parentJt, cloudJt, linkJt, true, afterJt);
        } else if (choiceJt[0] == 1) {
            answerJt(parentJt, cloudJt, linkJt, false, afterJt);
        }
    }

    private static void answerJt(Component parentJt, CloudServiceMa cloudJt, CloudLinkJt linkJt, boolean acceptJt,
            Runnable afterJt) {
        UiAsyncJt.runJt(() -> {
            if (acceptJt) {
                cloudJt.client().accept(linkJt.getMemberId());
            } else {
                cloudJt.client().decline(linkJt.getMemberId());
            }
            cloudJt.syncNowMa();
            return Boolean.TRUE;
        }, okJt -> {
            if (afterJt != null) {
                afterJt.run();
            }
        }, errorJt -> JOptionPane.showMessageDialog(parentJt, errorJt, "AppControl", JOptionPane.WARNING_MESSAGE));
    }
}
