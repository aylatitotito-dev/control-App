package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;

import com.formdev.flatlaf.FlatClientProperties;

/** Piezas visuales reutilizables. Los colores salen del tema (claro/oscuro), nunca fijos. */
final class UiKitJtMa {

    private UiKitJtMa() {
    }

    static JLabel title(String textMa) {
        JLabel labelMa = new JLabel(textMa);
        labelMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");
        return labelMa;
    }

    static JLabel subtitle(String textMa) {
        JLabel labelMa = new JLabel(textMa);
        labelMa.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        return labelMa;
    }

    static JLabel sectionTitle(String textMa) {
        JLabel labelMa = new JLabel(textMa);
        labelMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        return labelMa;
    }

    static JLabel bigValue() {
        JLabel labelMa = new JLabel("—");
        labelMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +10");
        return labelMa;
    }

    /** Tarjeta redondeada con titulo pequeno arriba y un valor grande. */
    static JPanel card(String titleMa, JLabel valueMa) {
        JPanel panelMa = new JPanel(new BorderLayout(0, 6));
        panelMa.setOpaque(false);
        panelMa.putClientProperty(FlatClientProperties.STYLE,
                "border: 14,16,14,16,$Component.borderColor,1,14");
        panelMa.add(subtitle(titleMa), BorderLayout.NORTH);
        panelMa.add(valueMa, BorderLayout.CENTER);
        return panelMa;
    }

    /** Tarjeta con titulo pequeno, valor grande y una linea de detalle abajo. */
    static JPanel card(String titleMa, JLabel valueMa, JLabel noteMa) {
        JPanel panelMa = new JPanel(new BorderLayout(0, 6));
        panelMa.setOpaque(false);
        panelMa.putClientProperty(FlatClientProperties.STYLE, "border: 14,16,14,16,$Component.borderColor,1,14");
        panelMa.add(subtitle(titleMa), BorderLayout.NORTH);
        panelMa.add(valueMa, BorderLayout.CENTER);
        panelMa.add(noteMa, BorderLayout.SOUTH);
        return panelMa;
    }

    static JScrollPane scroll(Component viewMa) {
        JScrollPane scrollMa = new JScrollPane(viewMa);
        scrollMa.setBorder(BorderFactory.createEmptyBorder());
        scrollMa.getViewport().setOpaque(false);
        scrollMa.setOpaque(false);
        return scrollMa;
    }

    /** Nota informativa que salta de linea segun el ancho disponible. */
    static JTextArea note(String textMa) {
        JTextArea areaMa = new JTextArea(textMa);
        areaMa.setEditable(false);
        areaMa.setFocusable(false);
        areaMa.setLineWrap(true);
        areaMa.setWrapStyleWord(true);
        areaMa.setOpaque(false);
        areaMa.setBorder(null);
        areaMa.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        return areaMa;
    }

    /** Panel que ocupa todo el ancho visible y solo hace scroll vertical. */
    private static final class WidthTrackingPanelJt extends JPanel implements javax.swing.Scrollable {
        WidthTrackingPanelJt() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle rMa, int oMa, int dMa) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle rMa, int oMa, int dMa) {
            return 120;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    static JScrollPane vscroll(Component bodyMa) {
        WidthTrackingPanelJt holderMa = new WidthTrackingPanelJt();
        holderMa.add(bodyMa, BorderLayout.NORTH);
        JScrollPane scrollMa = scroll(holderMa);
        scrollMa.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollMa.getVerticalScrollBar().setUnitIncrement(24);
        return scrollMa;
    }

    /** Panel con borde redondeado (tarjeta). */
    static JPanel roundedPanel(int padMa) {
        JPanel panelMa = new JPanel();
        panelMa.setOpaque(false);
        panelMa.putClientProperty(FlatClientProperties.STYLE,
                "border: " + padMa + "," + padMa + "," + padMa + "," + padMa + ",$Component.borderColor,1,14");
        return panelMa;
    }

    /** Ejecuta una accion cada vez que cambia el texto de un campo. */
    static void onChange(javax.swing.text.JTextComponent fieldMa, Runnable actionMa) {
        fieldMa.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent evMa) {
                actionMa.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent evMa) {
                actionMa.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent evMa) {
                actionMa.run();
            }
        });
    }

    static JScrollPane scroll(JTable tableMa) {
        JScrollPane scrollMa = scroll((Component) tableMa);
        scrollMa.setColumnHeaderView(tableMa.getTableHeader());
        return scrollMa;
    }
}
