package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.RuleStateJt;
import com.appcontrol.service.AccountServiceMa;
import com.appcontrol.service.RuleServiceJt;
import com.formdev.flatlaf.FlatClientProperties;

/**
 * Editor de una regla: elige el modo (bloqueo total, por horario o cuota diaria) y, al editar,
 * el estado (activa, pausada o desactivada). Los tres modos valen para aplicaciones y para sitios web.
 */
final class RuleDialogJt {

    private static final String[] DAYS_JT = {"L", "M", "X", "J", "V", "S", "D"};
    private static final String[] DAY_NAMES_JT = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado",
        "Domingo"};
    private static final String[] STATES_JT = {"Activa", "Pausada", "Desactivada"};
    private static final String[] PAUSES_JT = {"30 minutos", "1 hora", "Hasta mañana"};

    private RuleDialogJt() {
    }

    /** El formulario ya armado (sin ventana) y su boton Guardar. Separado para poder probarlo sin abrir nada. */
    record FormJt(JPanel panel, JButton saveButton) {
    }

    /** Muestra el editor. keyJt null = regla nueva (se pide el nombre). Devuelve true si se guardo. */
    static boolean showJt(Component parentJt, RuleServiceJt rulesJt, AccountServiceMa accountJt, String kindJt,
            String keyJt, String displayJt, BlockRuleJtMa existingJt) {
        if (!RuleActionsMa.unlockMa(parentJt, accountJt)) {
            return false;
        }
        Window ownerJt = SwingUtilities.getWindowAncestor(parentJt);
        JDialog dialogJt = new JDialog(ownerJt, existingJt == null ? "Nueva regla" : "Editar regla",
                Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] savedJt = {false};
        FormJt formJt = buildJt(rulesJt, kindJt, keyJt, displayJt, existingJt, okJt -> {
            savedJt[0] = okJt;
            dialogJt.dispose();
        });
        dialogJt.setLayout(new BorderLayout());
        dialogJt.add(formJt.panel(), BorderLayout.CENTER);
        dialogJt.getRootPane().setDefaultButton(formJt.saveButton());
        // Dos pasadas: las notas largas solo saben cuántas líneas ocupan cuando ya tienen ancho. Con una sola, la
        // ventana quedaba baja y se cortaba la fila "Estado de la regla".
        dialogJt.pack();
        int widthJt = Math.max(560, dialogJt.getWidth());
        dialogJt.setSize(widthJt, dialogJt.getHeight());
        dialogJt.validate();
        dialogJt.pack();
        dialogJt.setSize(Math.max(widthJt, dialogJt.getWidth()), dialogJt.getHeight() + 8);
        dialogJt.setMinimumSize(dialogJt.getSize());
        dialogJt.setLocationRelativeTo(parentJt);
        dialogJt.setVisible(true);
        return savedJt[0];
    }

    /** Arma el formulario. closeJt recibe true al guardar y false al cancelar. */
    static FormJt buildJt(RuleServiceJt rulesJt, String kindJt, String keyJt, String displayJt,
            BlockRuleJtMa existingJt, java.util.function.Consumer<Boolean> closeJt) {
        boolean siteJt = BlockRuleJtMa.SITE.equals(kindJt);
        JPanel rootJt = new JPanel(new BorderLayout());

        JTextField targetJt = new JTextField(keyJt == null ? "" : displayJt, 22);
        targetJt.setEditable(keyJt == null);
        targetJt.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                siteJt ? "youtube.com" : "nombre del programa, por ejemplo: steam");

        JRadioButton alwaysJt = new JRadioButton("Bloqueo total");
        JRadioButton scheduleJt = new JRadioButton("Bloquear en ciertos horarios");
        JRadioButton quotaJt = new JRadioButton("Cuota diaria de tiempo");
        ButtonGroup modesJt = new ButtonGroup();
        modesJt.add(alwaysJt);
        modesJt.add(scheduleJt);
        modesJt.add(quotaJt);

        // --- tarjetas de cada modo
        JPanel cardsJt = new JPanel(new CardLayout());
        cardsJt.setOpaque(false);

        JPanel alwaysCardJt = new JPanel(new BorderLayout());
        alwaysCardJt.setOpaque(false);
        alwaysCardJt.add(UiKitJtMa.note(siteJt
                ? "El sitio queda restringido en todo momento."
                : "La aplicación no se podrá usar en ningún momento. Si se abre, AppControl la cierra y avisa."),
                BorderLayout.NORTH);

        JCheckBox[] dayBoxesJt = new JCheckBox[7];
        JPanel daysJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        daysJt.setOpaque(false);
        for (int posJt = 0; posJt < 7; posJt++) {
            dayBoxesJt[posJt] = new JCheckBox(DAYS_JT[posJt]);
            dayBoxesJt[posJt].setToolTipText(DAY_NAMES_JT[posJt]);
            dayBoxesJt[posJt].setOpaque(false);
            daysJt.add(dayBoxesJt[posJt]);
        }
        JSpinner startJt = timeSpinnerJt(8 * 60);
        JSpinner endJt = timeSpinnerJt(14 * 60);
        JButton schoolJt = new JButton("Horario escolar (L–V 08:00–14:00)");
        schoolJt.addActionListener(evJt -> applySchedule(dayBoxesJt, startJt, endJt, 0b0011111, 8 * 60, 14 * 60));
        JButton nightJt = new JButton("Descanso nocturno (22:00–06:00)");
        nightJt.addActionListener(evJt -> applySchedule(dayBoxesJt, startJt, endJt, 127, 22 * 60, 6 * 60));
        JPanel timesJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        timesJt.setOpaque(false);
        timesJt.add(new JLabel("Prohibido desde"));
        timesJt.add(startJt);
        timesJt.add(new JLabel("hasta"));
        timesJt.add(endJt);
        JPanel presetsJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        presetsJt.setOpaque(false);
        presetsJt.add(schoolJt);
        presetsJt.add(nightJt);
        javax.swing.JTextArea scheduleNoteJt = UiKitJtMa.note("Fuera de esas horas funciona con normalidad. Si la hora de "
                + "fin es menor que la de inicio, la franja cruza la medianoche.");
        scheduleNoteJt.setRows(2); // son dos líneas: sin esto la segunda quedaba cortada
        JPanel scheduleCardJt = new JPanel();
        scheduleCardJt.setOpaque(false);
        scheduleCardJt.setLayout(new BoxLayout(scheduleCardJt, BoxLayout.Y_AXIS));
        for (Component partJt : new Component[] {new JLabel("Días en que está prohibida:"), daysJt,
            Box.createVerticalStrut(8), timesJt, Box.createVerticalStrut(8), presetsJt,
            Box.createVerticalStrut(6), scheduleNoteJt}) {
            ((javax.swing.JComponent) partJt).setAlignmentX(Component.LEFT_ALIGNMENT);
            scheduleCardJt.add(partJt);
        }

        JSpinner minutesJt = new JSpinner(new SpinnerNumberModel(120, 1, 1440, 5));
        JPanel quotaRowJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quotaRowJt.setOpaque(false);
        quotaRowJt.add(new JLabel("Máximo al día (minutos):"));
        quotaRowJt.add(minutesJt);
        for (int[] presetJt : new int[][] {{30, 0}, {60, 0}, {120, 0}, {180, 0}}) {
            JButton presetBtnJt = new JButton(com.appcontrol.service.RuleLogicJtMa.minutesText(presetJt[0]));
            presetBtnJt.addActionListener(evJt -> minutesJt.setValue(presetJt[0]));
            quotaRowJt.add(presetBtnJt);
        }
        JPanel quotaCardJt = new JPanel();
        quotaCardJt.setOpaque(false);
        quotaCardJt.setLayout(new BoxLayout(quotaCardJt, BoxLayout.Y_AXIS));
        quotaRowJt.setAlignmentX(Component.LEFT_ALIGNMENT);
        quotaCardJt.add(quotaRowJt);
        quotaCardJt.add(Box.createVerticalStrut(6));
        javax.swing.JTextArea quotaNoteJt = UiKitJtMa.note(siteJt
                ? "AppControl cuenta, de forma aproximada, el tiempo en que el navegador está conectado a este sitio y, "
                        + "al llegar al límite, lo bloquea hasta el día siguiente."
                : "AppControl suma el tiempo de uso de hoy. Avisa cuando quedan 5 minutos y, al llegar al límite, "
                        + "la cierra hasta el día siguiente.");
        quotaNoteJt.setRows(2);
        quotaNoteJt.setAlignmentX(Component.LEFT_ALIGNMENT);
        quotaCardJt.add(quotaNoteJt);

        cardsJt.add(alwaysCardJt, "ALWAYS");
        cardsJt.add(scheduleCardJt, "SCHEDULE");
        cardsJt.add(quotaCardJt, "QUOTA");
        alwaysJt.addActionListener(evJt -> ((CardLayout) cardsJt.getLayout()).show(cardsJt, "ALWAYS"));
        scheduleJt.addActionListener(evJt -> ((CardLayout) cardsJt.getLayout()).show(cardsJt, "SCHEDULE"));
        quotaJt.addActionListener(evJt -> ((CardLayout) cardsJt.getLayout()).show(cardsJt, "QUOTA"));

        // --- estado (solo al editar)
        JComboBox<String> stateJt = new JComboBox<>(STATES_JT);
        JComboBox<String> pauseJt = new JComboBox<>(PAUSES_JT);
        pauseJt.setEnabled(false);
        stateJt.addActionListener(evJt -> pauseJt.setEnabled(stateJt.getSelectedIndex() == 1));

        // --- valores iniciales
        RuleModeMa modeJt = existingJt == null ? RuleModeMa.ALWAYS : existingJt.getMode();
        int maskJt = existingJt == null ? 0b0011111 : existingJt.getDaysMask();
        for (int posJt = 0; posJt < 7; posJt++) {
            dayBoxesJt[posJt].setSelected((maskJt & (1 << posJt)) != 0);
        }
        if (existingJt != null && modeJt == RuleModeMa.SCHEDULE) {
            startJt.setValue(dateAt(existingJt.getStartMinute()));
            endJt.setValue(dateAt(existingJt.getEndMinute()));
        }
        if (existingJt != null && existingJt.getQuotaMinutes() > 0) {
            minutesJt.setValue(existingJt.getQuotaMinutes());
        }
        switch (modeJt) {
            case SCHEDULE -> scheduleJt.setSelected(true);
            case QUOTA -> quotaJt.setSelected(true);
            default -> alwaysJt.setSelected(true);
        }
        ((CardLayout) cardsJt.getLayout()).show(cardsJt, modeJt.name());
        if (existingJt != null) {
            stateJt.setSelectedIndex(switch (existingJt.getState()) {
                case ACTIVE -> 0;
                case PAUSED -> 1;
                case INACTIVE -> 2;
            });
            pauseJt.setEnabled(existingJt.getState() == RuleStateJt.PAUSED);
        }

        // --- armado
        JPanel bodyJt = new JPanel();
        bodyJt.setLayout(new BoxLayout(bodyJt, BoxLayout.Y_AXIS));
        bodyJt.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        JLabel headJt = new JLabel(siteJt ? "Sitio web" : "Aplicación");
        headJt.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        JPanel targetRowJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        targetRowJt.setOpaque(false);
        targetRowJt.add(headJt);
        targetRowJt.add(targetJt);
        JPanel modeBoxJt = new JPanel();
        modeBoxJt.setOpaque(false);
        modeBoxJt.setLayout(new BoxLayout(modeBoxJt, BoxLayout.Y_AXIS));
        modeBoxJt.add(alwaysJt);
        modeBoxJt.add(scheduleJt);
        modeBoxJt.add(quotaJt);
        JLabel howJt = new JLabel("Cómo se restringe");
        howJt.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        for (Component partJt : new Component[] {targetRowJt, Box.createVerticalStrut(14), howJt, modeBoxJt,
            Box.createVerticalStrut(8), cardsJt}) {
            ((javax.swing.JComponent) partJt).setAlignmentX(Component.LEFT_ALIGNMENT);
            bodyJt.add(partJt);
        }
        if (existingJt != null) {
            JLabel stateLabelJt = new JLabel("Estado de la regla");
            stateLabelJt.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
            JPanel stateRowJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            stateRowJt.setOpaque(false);
            stateRowJt.add(stateJt);
            stateRowJt.add(new JLabel("Pausar por:"));
            stateRowJt.add(pauseJt);
            for (Component partJt : new Component[] {Box.createVerticalStrut(14), stateLabelJt, stateRowJt}) {
                ((javax.swing.JComponent) partJt).setAlignmentX(Component.LEFT_ALIGNMENT);
                bodyJt.add(partJt);
            }
        }

        JButton saveJt = new JButton("Guardar");
        saveJt.addActionListener(evJt -> {
            try {
                RuleModeMa chosenJt = quotaJt.isSelected() ? RuleModeMa.QUOTA
                        : scheduleJt.isSelected() ? RuleModeMa.SCHEDULE : RuleModeMa.ALWAYS;
                int daysMaskJt = 0;
                for (int posJt = 0; posJt < 7; posJt++) {
                    if (dayBoxesJt[posJt].isSelected()) {
                        daysMaskJt |= 1 << posJt;
                    }
                }
                BlockRuleJtMa ruleJt = rulesJt.saveJtMa(kindJt, keyJt == null ? targetJt.getText() : keyJt,
                        keyJt == null ? targetJt.getText() : displayJt, chosenJt, daysMaskJt, minuteOfJt(startJt),
                        minuteOfJt(endJt), (Integer) minutesJt.getValue());
                if (existingJt != null) {
                    applyState(rulesJt, ruleJt, stateJt.getSelectedIndex(), pauseJt.getSelectedIndex());
                }
                closeJt.accept(true);
            } catch (IllegalArgumentException exJt) {
                JOptionPane.showMessageDialog(rootJt, exJt.getMessage(), "AppControl", JOptionPane.WARNING_MESSAGE);
            }
        });
        JButton cancelJt = new JButton("Cancelar");
        cancelJt.addActionListener(evJt -> closeJt.accept(false));
        JPanel buttonsJt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonsJt.setBorder(BorderFactory.createEmptyBorder(0, 24, 16, 24));
        buttonsJt.add(cancelJt);
        buttonsJt.add(saveJt);

        rootJt.add(bodyJt, BorderLayout.CENTER);
        rootJt.add(buttonsJt, BorderLayout.SOUTH);
        return new FormJt(rootJt, saveJt);
    }

    private static void applyState(RuleServiceJt rulesJt, BlockRuleJtMa ruleJt, int stateIndexJt, int pauseIndexJt) {
        switch (stateIndexJt) {
            case 1 -> {
                Instant untilJt = switch (pauseIndexJt) {
                    case 0 -> Instant.now().plusSeconds(30 * 60);
                    case 1 -> Instant.now().plusSeconds(60 * 60);
                    default -> LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                };
                rulesJt.setStateJtMa(ruleJt.getId(), RuleStateJt.PAUSED, untilJt);
            }
            case 2 -> rulesJt.setStateJtMa(ruleJt.getId(), RuleStateJt.INACTIVE, null);
            default -> rulesJt.setStateJtMa(ruleJt.getId(), RuleStateJt.ACTIVE, null);
        }
    }

    private static void applySchedule(JCheckBox[] boxesJt, JSpinner startJt, JSpinner endJt, int maskJt,
            int startMinuteJt, int endMinuteJt) {
        for (int posJt = 0; posJt < 7; posJt++) {
            boxesJt[posJt].setSelected((maskJt & (1 << posJt)) != 0);
        }
        startJt.setValue(dateAt(startMinuteJt));
        endJt.setValue(dateAt(endMinuteJt));
    }

    private static Date dateAt(int minuteOfDayJt) {
        Calendar calJt = Calendar.getInstance();
        calJt.set(Calendar.HOUR_OF_DAY, minuteOfDayJt / 60);
        calJt.set(Calendar.MINUTE, minuteOfDayJt % 60);
        calJt.set(Calendar.SECOND, 0);
        calJt.set(Calendar.MILLISECOND, 0);
        return calJt.getTime();
    }

    private static JSpinner timeSpinnerJt(int minuteOfDayJt) {
        JSpinner spinnerJt = new JSpinner(new SpinnerDateModel(dateAt(minuteOfDayJt), null, null, Calendar.MINUTE));
        spinnerJt.setEditor(new JSpinner.DateEditor(spinnerJt, "HH:mm"));
        return spinnerJt;
    }

    private static int minuteOfJt(JSpinner spinnerJt) {
        Calendar calJt = Calendar.getInstance();
        calJt.setTime((Date) spinnerJt.getValue());
        return calJt.get(Calendar.HOUR_OF_DAY) * 60 + calJt.get(Calendar.MINUTE);
    }
}
