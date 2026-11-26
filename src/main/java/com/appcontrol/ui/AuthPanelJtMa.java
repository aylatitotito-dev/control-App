package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.AccountServiceMa.ResultJt;
import com.formdev.flatlaf.FlatClientProperties;

/**
 * Pantalla de entrada, dividida en dos: a la izquierda el panel azul con el logo y el mensaje; a la derecha el
 * formulario para crear la cuenta (primera vez), iniciar sesion o recuperar la contrasena con el codigo que se
 * mostro al crearla. Las reglas se siguen aplicando en segundo plano aunque nadie haya entrado.
 */
class AuthPanelJtMa extends JPanel {

    private static final String BLUE_JT = "#0B5CFF";
    private static final String PRIMARY_STYLE_JT = "background: " + BLUE_JT + "; foreground: #FFFFFF; font: bold +1; "
            + "arc: 12; margin: 10,16,10,16; borderWidth: 0; focusWidth: 0";

    private final AccountServiceMa accountJt;
    private final Runnable onSuccessJt;
    private final CardLayout cardsJt = new CardLayout();
    private final JPanel formsJt = new JPanel(cardsJt);

    // crear cuenta
    private final JTextField regUserJt = new JTextField(22);
    private final JPasswordField regPassJt = new JPasswordField(22);
    private final JPasswordField regPass2Jt = new JPasswordField(22);
    private final JCheckBox regRememberJt = new JCheckBox("Recordar sesión en esta PC", true);
    private final JLabel regErrorJt = errorLabel();

    // iniciar sesion
    private final JTextField loginUserJt = new JTextField(22);
    private final JPasswordField loginPassJt = new JPasswordField(22);
    private final JCheckBox loginRememberJt = new JCheckBox("Recordar sesión en esta PC", true);
    private final JLabel loginErrorJt = errorLabel();
    private final JButton loginBtnJt = new JButton("Iniciar sesión");

    // recuperar
    private final JTextField recCodeJt = new JTextField(22);
    private final JPasswordField recPassJt = new JPasswordField(22);
    private final JPasswordField recPass2Jt = new JPasswordField(22);
    private final JLabel recErrorJt = errorLabel();

    private Timer lockTimerJt;

    AuthPanelJtMa(AccountServiceMa accountJt, Runnable onSuccessJt) {
        super(new BorderLayout());
        this.accountJt = accountJt;
        this.onSuccessJt = onSuccessJt;

        formsJt.setOpaque(false);
        formsJt.add(registerForm(), "register");
        formsJt.add(loginForm(), "login");
        formsJt.add(recoverForm(), "recover");

        JLabel markJt = new JLabel(new ImageIcon(LogoMa.scaledMa(LogoMa.colorMa(), 56)));
        markJt.setAlignmentX(Component.LEFT_ALIGNMENT);
        formsJt.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel cardJt = UiKitJtMa.roundedPanel(36);
        cardJt.setLayout(new BoxLayout(cardJt, BoxLayout.Y_AXIS));
        cardJt.add(markJt);
        cardJt.add(Box.createVerticalStrut(14));
        cardJt.add(formsJt);
        cardJt.setPreferredSize(new Dimension(440, cardJt.getPreferredSize().height));

        JPanel rightJt = new JPanel(new GridBagLayout());
        rightJt.add(cardJt, new GridBagConstraints());

        JPanel splitJt = new JPanel(new GridLayout(1, 2));
        splitJt.add(new BrandPanelMa());
        splitJt.add(rightJt);
        add(splitJt, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);

        showStartJt();
    }

    /** Muestra "crear cuenta" si todavia no hay cuenta, o "iniciar sesion". */
    void showStartJt() {
        if (accountJt.hasAccount()) {
            loginUserJt.setText(accountJt.usernameJt());
            loginPassJt.setText("");
            loginErrorJt.setText(" ");
            cardsJt.show(formsJt, "login");
            applyLockStateJt();
        } else {
            cardsJt.show(formsJt, "register");
        }
    }

    private static JPanel statusBar() {
        JPanel barJt = new JPanel(new BorderLayout());
        barJt.setBorder(new EmptyBorder(6, 16, 6, 16));
        barJt.add(UiKitJtMa.subtitle("●  Modo local habilitado   ·   Sin conexión a Azure (modo local)"),
                BorderLayout.WEST);
        barJt.add(UiKitJtMa.subtitle("Versión 0.0.1"), BorderLayout.EAST);
        return barJt;
    }

    // ------------------------------------------------------------------ formularios

    private static JLabel errorLabel() {
        JLabel labelJt = new JLabel(" ");
        labelJt.putClientProperty(FlatClientProperties.STYLE, "foreground: #D13438");
        return labelJt;
    }

    private static JPanel fieldBlock(String captionJt, JComponent fieldJt) {
        JPanel blockJt = new JPanel(new BorderLayout(0, 4));
        blockJt.setOpaque(false);
        JLabel capJt = new JLabel(captionJt);
        capJt.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        blockJt.add(capJt, BorderLayout.NORTH);
        blockJt.add(fieldJt, BorderLayout.CENTER);
        blockJt.setAlignmentX(Component.LEFT_ALIGNMENT);
        blockJt.setMaximumSize(new Dimension(Integer.MAX_VALUE, blockJt.getPreferredSize().height + 6));
        return blockJt;
    }

    private static JPanel column(Component... partsJt) {
        JPanel colJt = new JPanel();
        colJt.setOpaque(false);
        colJt.setLayout(new BoxLayout(colJt, BoxLayout.Y_AXIS));
        for (Component partJt : partsJt) {
            if (partJt instanceof JComponent cJt) {
                cJt.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            colJt.add(partJt);
        }
        return colJt;
    }

    private static JButton primary(JButton btnJt) {
        btnJt.putClientProperty(FlatClientProperties.STYLE, PRIMARY_STYLE_JT);
        btnJt.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnJt.setMaximumSize(new Dimension(Integer.MAX_VALUE, btnJt.getPreferredSize().height));
        return btnJt;
    }

    private static JButton linkButton(String textJt) {
        JButton btnJt = new JButton(textJt);
        btnJt.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        btnJt.putClientProperty(FlatClientProperties.STYLE, "foreground: " + BLUE_JT);
        return btnJt;
    }

    private static void styleField(JTextField fieldJt, String hintJt) {
        fieldJt.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, hintJt);
        fieldJt.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 6,10,6,10");
        if (fieldJt instanceof JPasswordField) {
            fieldJt.putClientProperty(FlatClientProperties.STYLE,
                    "arc: 10; margin: 6,10,6,10; showRevealButton: true");
        }
    }

    private static JLabel subHead(String textJt) {
        JLabel labelJt = UiKitJtMa.subtitle("<html><body style='width:340px'>" + textJt + "</body></html>");
        labelJt.setAlignmentX(Component.LEFT_ALIGNMENT);
        return labelJt;
    }

    private JPanel registerForm() {
        JLabel headJt = UiKitJtMa.title("Crea tu cuenta");
        styleField(regUserJt, "Tu nombre de usuario");
        styleField(regPassJt, "Mínimo " + AccountServiceMa.MIN_PASSWORD_JT + " caracteres");
        styleField(regPass2Jt, "Repite la contraseña");
        JButton createJt = primary(new JButton("Crear cuenta"));
        createJt.addActionListener(evJt -> doRegisterJt());
        regPass2Jt.addActionListener(evJt -> doRegisterJt());
        return column(headJt, Box.createVerticalStrut(2),
                subHead("Esta contraseña protege tus reglas en este equipo."),
                Box.createVerticalStrut(16),
                fieldBlock("Usuario", regUserJt), Box.createVerticalStrut(8),
                fieldBlock("Contraseña", regPassJt), Box.createVerticalStrut(8),
                fieldBlock("Repite la contraseña", regPass2Jt), Box.createVerticalStrut(8),
                regRememberJt, Box.createVerticalStrut(4), regErrorJt, Box.createVerticalStrut(8), createJt);
    }

    private JPanel loginForm() {
        JLabel headJt = UiKitJtMa.title("Bienvenido de nuevo");
        styleField(loginUserJt, "Tu nombre de usuario");
        styleField(loginPassJt, "Tu contraseña");
        primary(loginBtnJt);
        loginBtnJt.addActionListener(evJt -> doLoginJt());
        loginPassJt.addActionListener(evJt -> doLoginJt());
        JButton forgotJt = linkButton("¿Olvidaste tu contraseña?");
        forgotJt.addActionListener(evJt -> {
            recErrorJt.setText(" ");
            cardsJt.show(formsJt, "recover");
        });
        JPanel optionsJt = new JPanel(new BorderLayout());
        optionsJt.setOpaque(false);
        optionsJt.add(loginRememberJt, BorderLayout.WEST);
        optionsJt.add(forgotJt, BorderLayout.EAST);
        optionsJt.setMaximumSize(new Dimension(Integer.MAX_VALUE, optionsJt.getPreferredSize().height));
        return column(headJt, Box.createVerticalStrut(2),
                subHead("Ingresa tus credenciales para acceder a AppControl."), Box.createVerticalStrut(16),
                fieldBlock("Usuario", loginUserJt), Box.createVerticalStrut(8),
                fieldBlock("Contraseña", loginPassJt), Box.createVerticalStrut(8),
                optionsJt, Box.createVerticalStrut(4), loginErrorJt, Box.createVerticalStrut(8), loginBtnJt);
    }

    private JPanel recoverForm() {
        JLabel headJt = UiKitJtMa.title("Recuperar la contraseña");
        styleField(recCodeJt, "XXXX-XXXX-XXXX-XXXX");
        styleField(recPassJt, "Contraseña nueva");
        styleField(recPass2Jt, "Repite la contraseña nueva");
        JButton resetJt = primary(new JButton("Restablecer contraseña"));
        resetJt.addActionListener(evJt -> doRecoverJt());
        recPass2Jt.addActionListener(evJt -> doRecoverJt());
        JButton backJt = linkButton("Volver");
        backJt.setAlignmentX(Component.LEFT_ALIGNMENT);
        backJt.addActionListener(evJt -> showStartJt());
        return column(headJt, Box.createVerticalStrut(2),
                subHead("Escribe el código que te mostramos al crear la cuenta y elige una contraseña nueva."),
                Box.createVerticalStrut(16),
                fieldBlock("Código de recuperación", recCodeJt), Box.createVerticalStrut(8),
                fieldBlock("Contraseña nueva", recPassJt), Box.createVerticalStrut(8),
                fieldBlock("Repite la contraseña nueva", recPass2Jt), Box.createVerticalStrut(4), recErrorJt,
                Box.createVerticalStrut(8), resetJt, Box.createVerticalStrut(4), backJt);
    }

    // ------------------------------------------------------------------ panel azul

    /** Panel izquierdo: degradado azul con el logo, el mensaje y tres tarjetas. */
    private static final class BrandPanelMa extends JPanel {

        BrandPanelMa() {
            setLayout(new GridBagLayout());
            setOpaque(false);
            JPanel colMa = new JPanel();
            colMa.setOpaque(false);
            colMa.setLayout(new BoxLayout(colMa, BoxLayout.Y_AXIS));
            colMa.setBorder(new EmptyBorder(24, 36, 24, 36));

            JLabel logoMa = new JLabel(new ImageIcon(LogoMa.scaledMa(LogoMa.whiteMa(), 64)));
            JPanel namesMa = new JPanel();
            namesMa.setOpaque(false);
            namesMa.setLayout(new BoxLayout(namesMa, BoxLayout.Y_AXIS));
            namesMa.add(white("AppControl", "font: bold +10"));
            namesMa.add(white("Control modular de aplicaciones y tiempo", "font: +0"));
            JPanel brandRowMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            brandRowMa.setOpaque(false);
            brandRowMa.add(logoMa);
            brandRowMa.add(namesMa);

            JLabel headMa = white("<html>Toma el control de tu<br>tiempo digital.</html>", "font: bold +14");
            JLabel subtextMa = white("<html>Reglas por aplicación y sitio, horarios y cuotas<br>"
                    + "diarias, todo en tu equipo y sin Internet.</html>", "font: +1");

            JPanel cardsMa = new JPanel();
            cardsMa.setOpaque(false);
            cardsMa.setLayout(new BoxLayout(cardsMa, BoxLayout.Y_AXIS));
            cardsMa.add(featureMa("gear", "Reglas Tipo Lego", "Combina bloqueo siempre, horarios y cuotas."));
            cardsMa.add(Box.createVerticalStrut(10));
            cardsMa.add(featureMa("block", "Cuotas y Bloqueos", "Límites diarios para aplicaciones y sitios web."));
            cardsMa.add(Box.createVerticalStrut(10));
            cardsMa.add(featureMa("globe", "Azure Sync",
                    "Tus datos están en este equipo; la sincronización opcional llegará pronto."));
            JLabel badgeMa = white("Optimizado para Windows 11", "font: +0");

            for (JComponent partMa : new JComponent[] {brandRowMa, headMa, subtextMa, cardsMa, badgeMa}) {
                partMa.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            colMa.add(brandRowMa);
            colMa.add(Box.createVerticalStrut(30));
            colMa.add(headMa);
            colMa.add(Box.createVerticalStrut(10));
            colMa.add(subtextMa);
            colMa.add(Box.createVerticalStrut(24));
            colMa.add(cardsMa);
            colMa.add(Box.createVerticalStrut(20));
            colMa.add(badgeMa);
            add(colMa, new GridBagConstraints());

            // En ventanas bajas se ocultan partes para que todo quepa.
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent evMa) {
                    cardsMa.setVisible(getHeight() >= 640);
                    badgeMa.setVisible(getHeight() >= 640);
                    subtextMa.setVisible(getHeight() >= 520);
                }
            });
        }

        private static JLabel white(String textMa, String styleMa) {
            JLabel labelMa = new JLabel(textMa);
            labelMa.setForeground(Color.WHITE);
            labelMa.putClientProperty(FlatClientProperties.STYLE, styleMa);
            return labelMa;
        }

        private static JPanel featureMa(String iconMa, String titleMa, String textMa) {
            JPanel cardMa = new JPanel(new BorderLayout(14, 0)) {
                @Override
                protected void paintComponent(Graphics gMa) {
                    Graphics2D g2Ma = (Graphics2D) gMa.create();
                    g2Ma.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2Ma.setColor(new Color(255, 255, 255, 38));
                    g2Ma.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                    g2Ma.setColor(new Color(255, 255, 255, 60));
                    g2Ma.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                    g2Ma.dispose();
                }
            };
            cardMa.setOpaque(false);
            cardMa.setBorder(new EmptyBorder(12, 14, 12, 14));
            JLabel iconLabelMa = new JLabel(IconsJtMa.colored(iconMa, 22, "#FFFFFF"), SwingConstants.CENTER);
            iconLabelMa.setPreferredSize(new Dimension(34, 34));
            JPanel textsMa = new JPanel();
            textsMa.setOpaque(false);
            textsMa.setLayout(new BoxLayout(textsMa, BoxLayout.Y_AXIS));
            textsMa.add(white(titleMa, "font: bold +1"));
            textsMa.add(white("<html><body style='width:250px'>" + textMa + "</body></html>", "font: -1"));
            cardMa.add(iconLabelMa, BorderLayout.WEST);
            cardMa.add(textsMa, BorderLayout.CENTER);
            cardMa.setMaximumSize(new Dimension(Integer.MAX_VALUE, cardMa.getPreferredSize().height));
            return cardMa;
        }

        @Override
        protected void paintComponent(Graphics gMa) {
            Graphics2D g2Ma = (Graphics2D) gMa.create();
            g2Ma.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int wMa = getWidth();
            int hMa = getHeight();
            g2Ma.setPaint(new GradientPaint(0, 0, new Color(0x0A3BC8), wMa, hMa, new Color(0x2E7BFF)));
            g2Ma.fillRect(0, 0, wMa, hMa);
            g2Ma.setColor(new Color(255, 255, 255, 18));
            g2Ma.fillOval(-wMa / 4, hMa * 3 / 5, wMa * 3 / 4, wMa * 3 / 4);
            g2Ma.fillOval(wMa / 2, -wMa / 5, wMa * 3 / 5, wMa * 3 / 5);
            g2Ma.dispose();
            super.paintComponent(gMa);
        }
    }

    // ------------------------------------------------------------------ acciones

    private void doRegisterJt() {
        regErrorJt.setText(" ");
        String passJt = new String(regPassJt.getPassword());
        if (!passJt.equals(new String(regPass2Jt.getPassword()))) {
            regErrorJt.setText("Las dos contraseñas no coinciden.");
            return;
        }
        try {
            String codeJt = accountJt.registerJt(regUserJt.getText(), passJt);
            if (!regRememberJt.isSelected()) {
                accountJt.logoutJt();
            }
            regPassJt.setText("");
            regPass2Jt.setText("");
            showRecoveryCodeJt(codeJt, "Guarda tu código de recuperación");
            onSuccessJt.run();
        } catch (IllegalArgumentException | IllegalStateException exJt) {
            regErrorJt.setText(exJt.getMessage());
        }
    }

    private void doLoginJt() {
        loginErrorJt.setText(" ");
        ResultJt resultJt = accountJt.loginJt(loginUserJt.getText(), new String(loginPassJt.getPassword()),
                loginRememberJt.isSelected());
        switch (resultJt) {
            case OK -> {
                loginPassJt.setText("");
                onSuccessJt.run();
            }
            case LOCKED -> applyLockStateJt();
            default -> loginErrorJt.setText("Usuario o contraseña incorrectos.");
        }
    }

    private void doRecoverJt() {
        recErrorJt.setText(" ");
        String passJt = new String(recPassJt.getPassword());
        if (!passJt.equals(new String(recPass2Jt.getPassword()))) {
            recErrorJt.setText("Las dos contraseñas no coinciden.");
            return;
        }
        try {
            String newCodeJt = accountJt.resetWithRecoveryJt(recCodeJt.getText(), passJt);
            if (newCodeJt == null) {
                recErrorJt.setText(accountJt.lockSecondsLeftJt() > 0
                        ? "Demasiados intentos. Espera " + accountJt.lockSecondsLeftJt() + " segundos."
                        : "El código de recuperación no es correcto.");
                return;
            }
            recCodeJt.setText("");
            recPassJt.setText("");
            recPass2Jt.setText("");
            showRecoveryCodeJt(newCodeJt, "Tu código de recuperación nuevo");
            onSuccessJt.run();
        } catch (IllegalArgumentException exJt) {
            recErrorJt.setText(exJt.getMessage());
        }
    }

    /** Si hay una espera por intentos fallidos, bloquea el boton y cuenta los segundos que faltan. */
    private void applyLockStateJt() {
        if (lockTimerJt != null) {
            lockTimerJt.stop();
        }
        if (accountJt.lockSecondsLeftJt() <= 0) {
            loginBtnJt.setEnabled(true);
            return;
        }
        loginBtnJt.setEnabled(false);
        lockTimerJt = new Timer(1000, evJt -> {
            long leftJt = accountJt.lockSecondsLeftJt();
            if (leftJt <= 0) {
                loginErrorJt.setText(" ");
                loginBtnJt.setEnabled(true);
                ((Timer) evJt.getSource()).stop();
            } else {
                loginErrorJt.setText("Demasiados intentos. Espera " + leftJt + " segundos.");
            }
        });
        loginErrorJt.setText("Demasiados intentos. Espera " + accountJt.lockSecondsLeftJt() + " segundos.");
        lockTimerJt.start();
    }

    /**
     * Muestra el codigo de recuperacion. No se puede cerrar sin pulsar "Ya lo guardé": es la unica vez que se ve
     * y, sin el, olvidar la contrasena dejaria las reglas sin poder cambiarse.
     */
    private void showRecoveryCodeJt(String codeJt, String titleJt) {
        JTextField codeFieldJt = new JTextField(codeJt, 22);
        codeFieldJt.setEditable(false);
        codeFieldJt.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        codeFieldJt.setHorizontalAlignment(JTextField.CENTER);
        JPanel panelJt = new JPanel(new BorderLayout(0, 12));
        panelJt.add(new JLabel("<html><body style='width:380px'>Este código te permite entrar si olvidas la "
                + "contraseña. <b>Solo se muestra ahora.</b> Anótalo en un lugar seguro, fuera de este equipo."
                + "</body></html>"), BorderLayout.NORTH);
        panelJt.add(codeFieldJt, BorderLayout.CENTER);
        Object[] optionsJt = {"Copiar código", "Ya lo guardé"};
        int choiceJt;
        do {
            choiceJt = JOptionPane.showOptionDialog(this, panelJt, titleJt, JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE, null, optionsJt, optionsJt[1]);
            if (choiceJt == 0) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(codeJt), null);
            }
        } while (choiceJt != 1);
    }
}
