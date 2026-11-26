package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.appcontrol.cloud.CloudDtoJt.EventDtoJt;
import com.appcontrol.model.BlockRuleJtMa;
import com.formdev.flatlaf.FlatClientProperties;

/**
 * "Historial de Actividad" de otro equipo: buscador, filtro por tipo, ocultar registros muy cortos, exportar a un
 * archivo y una tabla paginada con la hora, el tipo, el elemento (con su icono), la duración y su estado.
 * Solo muestra programa o dominio y hora: nunca direcciones completas.
 */
final class RemoteHistoryPanelJtMa extends JPanel {

    private static final String[] TYPES_JT = {"Todos", "Aplicaciones", "Sitios"};
    private static final DateTimeFormatter HOUR_JT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY_HOUR_JT = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());

    private final MemberViewJtMa hostJt;
    private final JTextField searchJt = new JTextField();
    private final JComboBox<String> typeJt = new JComboBox<>(TYPES_JT);
    private final JCheckBox hideShortJt = new JCheckBox("Ocultar registros de menos de 10s");
    private final DefaultTableModel modelJt = new DefaultTableModel(
            new String[] {"Hora", "Tipo", "Elemento / Nombre", "Duración", "Estado / Acción", "Acción"}, 0) {
        @Override
        public boolean isCellEditable(int rowJt, int colJt) {
            return false;
        }
    };
    private final JTable tableJt = new JTable(modelJt);
    private final PagerJt pagerJt = new PagerJt(newPageJt -> {
        pageJt = newPageJt;
        rebuildJt();
    });
    private final List<EventDtoJt> shownJt = new ArrayList<>();
    private final List<EventDtoJt> pageItemsJt = new ArrayList<>();
    private int pageJt;

    RemoteHistoryPanelJtMa(MemberViewJtMa hostJt) {
        super(new BorderLayout(0, 12));
        this.hostJt = hostJt;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        searchJt.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Buscar en historial…");
        searchJt.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_ICON, IconsJtMa.of("search", 16));
        searchJt.setPreferredSize(new Dimension(340, 34));
        UiKitJtMa.onChange(searchJt, () -> {
            pageJt = 0;
            rebuildJt();
        });
        typeJt.addActionListener(evJt -> {
            pageJt = 0;
            rebuildJt();
        });
        hideShortJt.addActionListener(evJt -> {
            pageJt = 0;
            rebuildJt();
        });
        JButton exportJt = new JButton("Exportar reporte");
        exportJt.addActionListener(evJt -> exportJt());

        // Dos filas para que quepa también en el panel angosto del detalle: buscar y exportar / tipo y filtro.
        JPanel searchRowJt = new JPanel(new BorderLayout(10, 0));
        searchRowJt.setOpaque(false);
        searchJt.setPreferredSize(new Dimension(200, 34));
        searchRowJt.add(searchJt, BorderLayout.CENTER);
        searchRowJt.add(exportJt, BorderLayout.EAST);
        JPanel filterRowJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterRowJt.setOpaque(false);
        JButton dateJt = new JButton("📅  Elegir fecha");
        dateJt.setToolTipText("Abre el calendario para ver el historial de un día");
        dateJt.addActionListener(evJt -> hostJt.chooseDateJt());
        filterRowJt.add(dateJt);
        filterRowJt.add(UiKitJtMa.subtitle("Tipo:"));
        filterRowJt.add(typeJt);
        filterRowJt.add(hideShortJt);
        JPanel topJt = new JPanel(new BorderLayout(0, 8));
        topJt.setOpaque(false);
        topJt.add(searchRowJt, BorderLayout.NORTH);
        topJt.add(filterRowJt, BorderLayout.CENTER);

        tableJt.setRowHeight(40);
        tableJt.setShowVerticalLines(false);
        tableJt.setFillsViewportHeight(true);
        tableJt.getColumnModel().getColumn(0).setMinWidth(104); // cabe "25/11 09:41"
        tableJt.getColumnModel().getColumn(0).setPreferredWidth(110);
        tableJt.getColumnModel().getColumn(1).setPreferredWidth(120);
        tableJt.getColumnModel().getColumn(2).setPreferredWidth(320);
        tableJt.getColumnModel().getColumn(3).setPreferredWidth(90);
        tableJt.getColumnModel().getColumn(4).setPreferredWidth(140);
        tableJt.getColumnModel().getColumn(5).setPreferredWidth(70);
        tableJt.getColumnModel().getColumn(1).setCellRenderer(new TypeRendererJt());
        tableJt.getColumnModel().getColumn(2).setCellRenderer(new NameRendererJt());
        tableJt.getColumnModel().getColumn(4).setCellRenderer(new StateRendererJt());
        DefaultTableCellRenderer moreJt = new DefaultTableCellRenderer();
        moreJt.setHorizontalAlignment(SwingConstants.CENTER);
        tableJt.getColumnModel().getColumn(5).setCellRenderer(moreJt);
        tableJt.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evJt) {
                int rowJt = tableJt.rowAtPoint(evJt.getPoint());
                int colJt = tableJt.columnAtPoint(evJt.getPoint());
                if (rowJt >= 0 && rowJt < pageItemsJt.size() && colJt == 5) {
                    menuJt(pageItemsJt.get(rowJt)).show(tableJt, evJt.getX(), evJt.getY());
                }
            }
        });

        JPanel bottomJt = new JPanel(new BorderLayout());
        bottomJt.setOpaque(false);
        // El paginador ya dice "mostrando 1–10 de 12": ponerle otro texto al lado lo encimaba en el panel angosto.
        bottomJt.add(pagerJt, BorderLayout.EAST);

        add(topJt, BorderLayout.NORTH);
        add(UiKitJtMa.scroll(tableJt), BorderLayout.CENTER);
        add(bottomJt, BorderLayout.SOUTH);
    }

    /** Vuelve a dibujar con los datos más nuevos del equipo. */
    void reloadMa() {
        rebuildJt();
    }

    private static boolean isAppJt(EventDtoJt eventJt) {
        return eventJt.type().endsWith("APP");
    }

    private static long secondsJt(EventDtoJt eventJt) {
        return Math.max(0, Duration.between(eventJt.startedAt(), eventJt.endedAt()).toSeconds());
    }

    private void rebuildJt() {
        String queryJt = searchJt.getText().trim().toLowerCase(Locale.ROOT);
        String typeNameJt = (String) typeJt.getSelectedItem();
        shownJt.clear();
        for (EventDtoJt eventJt : hostJt.loadedNowJt().events()) {
            boolean typeOkJt = "Todos".equals(typeNameJt) || ("Aplicaciones".equals(typeNameJt) && isAppJt(eventJt))
                    || ("Sitios".equals(typeNameJt) && !isAppJt(eventJt));
            boolean textOkJt = queryJt.isEmpty() || eventJt.displayName().toLowerCase(Locale.ROOT).contains(queryJt)
                    || eventJt.targetKey().toLowerCase(Locale.ROOT).contains(queryJt);
            boolean longOkJt = !hideShortJt.isSelected() || secondsJt(eventJt) >= 10;
            if (typeOkJt && textOkJt && longOkJt) {
                shownJt.add(eventJt);
            }
        }
        pageJt = PagerJt.clampJt(pageJt, shownJt.size());
        pageItemsJt.clear();
        pageItemsJt.addAll(PagerJt.sliceJt(shownJt, pageJt));
        modelJt.setRowCount(0);
        LocalDate todayJt = LocalDate.now();
        for (EventDtoJt eventJt : pageItemsJt) {
            boolean todayEventJt = eventJt.startedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(todayJt);
            long secJt = secondsJt(eventJt);
            modelJt.addRow(new Object[] {(todayEventJt ? HOUR_JT : DAY_HOUR_JT).format(eventJt.startedAt()),
                eventJt.type(), eventJt.displayName(), secJt == 0 ? "—" : FmtJt.durationJtMa(secJt),
                eventJt.type().startsWith("BLOCK") ? "Bloqueado" : "Permitido", "···"});
        }
        pagerJt.showJt(pageJt, shownJt.size());
    }

    private JPopupMenu menuJt(EventDtoJt eventJt) {
        JPopupMenu menuJt = new JPopupMenu();
        String kindJt = isAppJt(eventJt) ? BlockRuleJtMa.APP : BlockRuleJtMa.SITE;
        JMenuItem ruleJt = new JMenuItem("Poner una regla a «" + eventJt.displayName() + "»…");
        ruleJt.addActionListener(evJt -> hostJt.configureKeyJt(kindJt, eventJt.targetKey().toLowerCase(Locale.ROOT),
                eventJt.displayName()));
        menuJt.add(ruleJt);
        return menuJt;
    }

    // ------------------------------------------------------------------ exportar

    private void exportJt() {
        JFileChooser chooserJt = new JFileChooser();
        chooserJt.setDialogTitle("Guardar reporte");
        chooserJt.setSelectedFile(new java.io.File("historial-" + hostJt.memberNameJt() + ".csv"));
        if (chooserJt.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path pathJt = chooserJt.getSelectedFile().toPath();
        try (Writer outJt = Files.newBufferedWriter(pathJt, StandardCharsets.UTF_8)) {
            outJt.write('﻿');
            outJt.write("Fecha;Hora;Tipo;Nombre;Duración (segundos);Estado\n");
            DateTimeFormatter dayJt = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());
            for (EventDtoJt eventJt : shownJt) {
                outJt.write(dayJt.format(eventJt.startedAt()) + ";" + HOUR_JT.format(eventJt.startedAt()) + ";"
                        + (isAppJt(eventJt) ? "Aplicación" : "Sitio") + ";" + csvJt(eventJt.displayName()) + ";"
                        + secondsJt(eventJt) + ";" + (eventJt.type().startsWith("BLOCK") ? "Bloqueado" : "Permitido")
                        + "\n");
            }
            JOptionPane.showMessageDialog(this, "Reporte guardado en:\n" + pathJt, "AppControl",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exJt) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar el archivo: " + exJt.getMessage(), "AppControl",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static String csvJt(String textJt) {
        String cleanJt = textJt == null ? "" : textJt.replace(';', ',').replace('\n', ' ');
        // evita que una hoja de cálculo ejecute un texto que empiece con = + - @
        return !cleanJt.isEmpty() && "=+-@".indexOf(cleanJt.charAt(0)) >= 0 ? "'" + cleanJt : cleanJt;
    }

    // ------------------------------------------------------------------ dibujo de las celdas

    /** "Sitio" / "Aplicación" como una etiqueta chica. */
    private final class TypeRendererJt extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tableJt, Object valueJt, boolean selectedJt,
                boolean focusJt, int rowJt, int colJt) {
            String typeJt = String.valueOf(valueJt);
            boolean appJt = typeJt.endsWith("APP");
            JLabel labelJt = (JLabel) super.getTableCellRendererComponent(tableJt, appJt ? "Aplicación" : "Sitio",
                    selectedJt, focusJt, rowJt, colJt);
            labelJt.setIcon(null);
            labelJt.setForeground(ThemeManagerMa.accentColorJt());
            return labelJt;
        }
    }

    /** El elemento con su icono (letra para aplicaciones, favicon si ya lo tiene este equipo para sitios). */
    private final class NameRendererJt extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tableJt, Object valueJt, boolean selectedJt,
                boolean focusJt, int rowJt, int colJt) {
            JLabel labelJt = (JLabel) super.getTableCellRendererComponent(tableJt, valueJt, selectedJt, focusJt, rowJt,
                    colJt);
            if (rowJt >= 0 && rowJt < pageItemsJt.size()) {
                EventDtoJt eventJt = pageItemsJt.get(rowJt);
                Icon iconJt = isAppJt(eventJt) ? AppIconsMa.placeholder(eventJt.displayName(), 24)
                        : SiteIconsJt.ofJt(eventJt.targetKey().toLowerCase(Locale.ROOT), 24);
                labelJt.setIcon(iconJt);
                labelJt.setIconTextGap(10);
            }
            return labelJt;
        }
    }

    /** "✓ Permitido" en verde; "Bloqueado" en rojo. */
    private final class StateRendererJt extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tableJt, Object valueJt, boolean selectedJt,
                boolean focusJt, int rowJt, int colJt) {
            boolean blockedJt = "Bloqueado".equals(valueJt);
            JLabel labelJt = (JLabel) super.getTableCellRendererComponent(tableJt,
                    (blockedJt ? "✕ " : "✓ ") + valueJt, selectedJt, focusJt, rowJt, colJt);
            labelJt.setIcon(null);
            labelJt.setForeground(blockedJt ? new Color(0xC62828) : new Color(0x2E7D32));
            return labelJt;
        }
    }
}
