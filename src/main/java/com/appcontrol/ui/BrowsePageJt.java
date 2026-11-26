package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * Pantalla de lista al estilo de Configuracion de Windows: titulo, buscador, tres vistas,
 * contador, "Filtrar por" y "Ordenar por", y tarjetas con menu de acciones.
 * Cada pantalla concreta solo dice como cargar sus elementos.
 */
abstract class BrowsePageJt extends PageMa {

    enum ViewMa { LIST, GRID4, GRID9 }

    /** Elementos ya filtrados y ordenados, y el total sin filtrar (para el contador). */
    record ResultJt(List<BrowseItemMa> items, int total) {
    }

    private final String headingJt;
    private final JTextField searchJt = new JTextField();
    private final JComboBox<String> filterBoxJt = new JComboBox<>();
    private final JComboBox<String> sortBoxJt = new JComboBox<>();
    private final JLabel countJt = new JLabel(" ");
    private final JPanel itemsJt = new JPanel();
    private ViewMa viewJt = ViewMa.LIST;
    private String lastSigJt = "";
    private int pageIdxJt;
    private final PagerJt pagerJt = new PagerJt(newPageJt -> {
        pageIdxJt = newPageJt;
        rebuildMa(true);
    });
    private final JButton refreshBtnJt = new JButton();
    private boolean busyJt;
    private JPanel countRowBoxJt;
    private JPanel optionsBoxJt;
    private boolean narrowJt;

    BrowsePageJt(String navTitleJt, String headingJt) {
        super(navTitleJt);
        this.headingJt = headingJt;
    }

    /** Carga los elementos. Los parametros vienen del buscador y de los dos desplegables. */
    abstract ResultJt loadMa(String queryJt, String filterNameJt, String sortNameJt);

    /** Opciones de "Filtrar por" (vacio = no se muestra). */
    abstract String[] filtersMa();

    /** Opciones de "Ordenar por" (vacio = no se muestra). */
    abstract String[] sortsMa();

    abstract String placeholderMa();

    /** "sitio" / "sitios" segun la cantidad. */
    abstract String nounMa(int amountJt);

    /** Si la pantalla muestra el boton de actualizar (las dos flechas en circulo). */
    boolean hasRefreshMa() {
        return true;
    }

    /**
     * Trabajo de actualizar de verdad (leer historiales, iconos, etc.). Se ejecuta en segundo plano, no en el hilo
     * de la interfaz, porque puede tardar unos segundos. Al terminar la lista se vuelve a dibujar.
     */
    void refreshNowMa() {
    }

    /** Vuelve a la primera pagina (al cambiar la busqueda, el filtro, el orden, la vista o el dia). */
    final void resetPageMa() {
        pageIdxJt = 0;
    }

    /** Boton extra junto a las vistas (por ejemplo "Agregar sitio"), o null. */
    JComponent extraActionMa() {
        return null;
    }

    /** Nota pequena al pie, o null. */
    String noteMa() {
        return null;
    }

    /** Texto extra que, si cambia, obliga a redibujar (por ejemplo, cuando aparece un icono nuevo). */
    String signatureExtraMa() {
        return "";
    }

    String emptyTextMa() {
        return "No hay elementos que coincidan con la búsqueda o el filtro.";
    }

    /** Las pantallas hijas lo llaman al final de su constructor (cuando ya tienen sus campos listos). */
    final void buildMa() {
        searchJt.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholderMa());
        searchJt.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_ICON, IconsJtMa.of("search", 16));
        searchJt.putClientProperty(FlatClientProperties.STYLE, "margin: 6,10,6,10");
        searchJt.setPreferredSize(new Dimension(340, 36));
        UiKitJtMa.onChange(searchJt, () -> {
            resetPageMa();
            rebuildMa(true);
        });

        JPanel searchWrapJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        searchWrapJt.setOpaque(false);
        searchWrapJt.add(searchJt);

        JPanel rightTopJt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightTopJt.setOpaque(false);
        JComponent extraJt = extraActionMa();
        if (extraJt != null) {
            rightTopJt.add(extraJt);
        }
        if (hasRefreshMa()) {
            refreshBtnJt.setIcon(IconsJtMa.of("refresh", 18));
            refreshBtnJt.setToolTipText("Actualizar ahora (puede tardar unos segundos)");
            refreshBtnJt.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                    FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            refreshBtnJt.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 7,10,7,10");
            refreshBtnJt.setFocusable(false);
            refreshBtnJt.addActionListener(evJt -> startRefreshJt());
            rightTopJt.add(refreshBtnJt);
        }
        rightTopJt.add(viewButtonsJt());

        JPanel searchRowJt = new JPanel(new BorderLayout());
        searchRowJt.setOpaque(false);
        searchRowJt.add(searchWrapJt, BorderLayout.WEST);
        searchRowJt.add(rightTopJt, BorderLayout.EAST);

        JPanel optionsJt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        optionsJt.setOpaque(false);
        String[] filtersJt = filtersMa();
        if (filtersJt.length > 0) {
            filterBoxJt.setModel(new DefaultComboBoxModel<>(filtersJt));
            filterBoxJt.addActionListener(evJt -> {
                resetPageMa();
                rebuildMa(true);
            });
            optionsJt.add(UiKitJtMa.subtitle("Filtrar por:"));
            optionsJt.add(filterBoxJt);
        }
        String[] sortsJt = sortsMa();
        if (sortsJt.length > 0) {
            sortBoxJt.setModel(new DefaultComboBoxModel<>(sortsJt));
            sortBoxJt.addActionListener(evJt -> {
                resetPageMa();
                rebuildMa(true);
            });
            if (filtersJt.length > 0) {
                optionsJt.add(javax.swing.Box.createHorizontalStrut(10));
            }
            optionsJt.add(UiKitJtMa.subtitle("Ordenar por:"));
            optionsJt.add(sortBoxJt);
        }
        countJt.putClientProperty(FlatClientProperties.STYLE, "font: +1");
        JPanel countRowJt = new JPanel(new BorderLayout());
        countRowJt.setOpaque(false);
        countRowJt.add(countJt, BorderLayout.WEST);
        countRowJt.add(optionsJt, BorderLayout.EAST);
        countRowBoxJt = countRowJt;
        optionsBoxJt = optionsJt;

        JPanel topJt = new JPanel(new BorderLayout(0, 16));
        topJt.setOpaque(false);
        if (headingJt != null) {
            topJt.add(UiKitJtMa.title(headingJt), BorderLayout.NORTH);
        }
        topJt.add(searchRowJt, BorderLayout.CENTER);
        topJt.add(countRowJt, BorderLayout.SOUTH);

        itemsJt.setOpaque(false);
        JPanel centerJt = new JPanel(new BorderLayout(0, 12));
        centerJt.setOpaque(false);
        centerJt.add(UiKitJtMa.vscroll(itemsJt), BorderLayout.CENTER);
        JPanel bottomJt = new JPanel(new BorderLayout(0, 8));
        bottomJt.setOpaque(false);
        bottomJt.add(pagerJt, BorderLayout.NORTH);
        String noteJt = noteMa();
        if (noteJt != null) {
            bottomJt.add(UiKitJtMa.note(noteJt), BorderLayout.SOUTH);
        }
        centerJt.add(bottomJt, BorderLayout.SOUTH);

        add(topJt, BorderLayout.NORTH);
        add(centerJt, BorderLayout.CENTER);
        rebuildMa(true);
    }

    private JPanel viewButtonsJt() {
        JPanel panelJt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        panelJt.setOpaque(false);
        ButtonGroup groupJt = new ButtonGroup();
        String[][] defsJt = {{"view-list", "Lista"}, {"view-grid4", "Iconos medianos"}, {"view-grid9", "Iconos pequeños"}};
        ViewMa[] viewsJt = ViewMa.values();
        for (int posJt = 0; posJt < defsJt.length; posJt++) {
            ViewMa vJt = viewsJt[posJt];
            JToggleButton btnJt = new JToggleButton(IconsJtMa.of(defsJt[posJt][0], 18));
            btnJt.setToolTipText(defsJt[posJt][1]);
            btnJt.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            btnJt.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 7,10,7,10");
            btnJt.setFocusable(false);
            btnJt.setSelected(vJt == viewJt);
            btnJt.addActionListener(evJt -> setViewMa(vJt));
            groupJt.add(btnJt);
            panelJt.add(btnJt);
        }
        return panelJt;
    }

    void setViewMa(ViewMa newJt) {
        viewJt = newJt;
        resetPageMa();
        rebuildMa(true);
    }

    @Override
    void refreshMa() {
        rebuildMa(false);
    }

    /**
     * En un panel angosto (por ejemplo, el detalle de un equipo) el buscador se achica y las opciones de filtrar y
     * ordenar bajan a su propia línea.
     */
    @Override
    public void doLayout() {
        int widthJt = getWidth();
        if (widthJt > 0 && countRowBoxJt != null) {
            Dimension wantedJt = new Dimension(Math.max(150, Math.min(340, widthJt / 3)), 36);
            if (!wantedJt.equals(searchJt.getPreferredSize())) {
                searchJt.setPreferredSize(wantedJt);
            }
            boolean nowNarrowJt = widthJt < 760;
            if (nowNarrowJt != narrowJt) {
                narrowJt = nowNarrowJt;
                countRowBoxJt.removeAll();
                if (nowNarrowJt) {
                    countRowBoxJt.setLayout(new BorderLayout(0, 6));
                    countRowBoxJt.add(countJt, BorderLayout.NORTH);
                    optionsBoxJt.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    countRowBoxJt.add(optionsBoxJt, BorderLayout.CENTER);
                } else {
                    countRowBoxJt.setLayout(new BorderLayout());
                    optionsBoxJt.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                    countRowBoxJt.add(countJt, BorderLayout.WEST);
                    countRowBoxJt.add(optionsBoxJt, BorderLayout.EAST);
                }
                countRowBoxJt.revalidate();
            }
        }
        super.doLayout();
    }

    /** Pulso del boton actualizar: trabaja en segundo plano, avisa que esta actualizando y luego redibuja. */
    private void startRefreshJt() {
        if (busyJt) {
            return;
        }
        busyJt = true;
        refreshBtnJt.setEnabled(false);
        countJt.setText("Actualizando…");
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    refreshNowMa();
                } catch (Exception exJt) {
                    // si algo falla, igual se vuelve a leer lo que ya hay guardado
                }
                return null;
            }

            @Override
            protected void done() {
                busyJt = false;
                refreshBtnJt.setEnabled(true);
                setCursor(java.awt.Cursor.getDefaultCursor());
                rebuildMa(true);
            }
        }.execute();
    }

    /** Vuelve a cargar y dibujar. Sin force y sin cambios, no toca la pantalla (conserva el scroll). */
    final void rebuildMa(boolean forceJt) {
        String queryJt = searchJt.getText().trim().toLowerCase(Locale.ROOT);
        String filterNameJt = (String) filterBoxJt.getSelectedItem();
        String sortNameJt = (String) sortBoxJt.getSelectedItem();
        ResultJt resultJt = loadMa(queryJt, filterNameJt, sortNameJt);
        pageIdxJt = PagerJt.clampJt(pageIdxJt, resultJt.items().size());
        List<BrowseItemMa> pageItemsJt = PagerJt.sliceJt(resultJt.items(), pageIdxJt);

        StringBuilder sigJt = new StringBuilder().append(viewJt).append('|').append(queryJt).append('|')
                .append(filterNameJt).append('|').append(sortNameJt).append('|').append(resultJt.total())
                .append('|').append(pageIdxJt).append('/').append(resultJt.items().size());
        for (BrowseItemMa itemJt : pageItemsJt) {
            sigJt.append(';').append(itemJt.title()).append('~').append(itemJt.subtitle()).append('~')
                    .append(itemJt.chipText()).append('~')
                    .append(itemJt.progress() == null ? -1 : Math.round(itemJt.progress() * 100));
        }
        sigJt.append('#').append(signatureExtraMa());
        if (!forceJt && sigJt.toString().equals(lastSigJt)) {
            return;
        }
        lastSigJt = sigJt.toString();

        itemsJt.removeAll();
        if (resultJt.items().isEmpty()) {
            itemsJt.setLayout(new BorderLayout());
            itemsJt.add(UiKitJtMa.note(resultJt.total() == 0 ? emptyAllJt() : emptyTextMa()), BorderLayout.NORTH);
        } else if (viewJt == ViewMa.LIST) {
            itemsJt.setLayout(new BoxLayout(itemsJt, BoxLayout.Y_AXIS));
            for (BrowseItemMa itemJt : pageItemsJt) {
                itemsJt.add(cardJt(itemJt));
                itemsJt.add(javax.swing.Box.createVerticalStrut(8));
            }
        } else {
            itemsJt.setLayout(new WrapLayoutMa(FlowLayout.LEFT, 12, 12));
            for (BrowseItemMa itemJt : pageItemsJt) {
                itemsJt.add(cardJt(itemJt));
            }
        }
        int shownJt = resultJt.items().size();
        countJt.setText(shownJt == resultJt.total()
                ? "Se encontraron " + shownJt + " " + nounMa(shownJt)
                : "Se encontraron " + shownJt + " de " + resultJt.total() + " " + nounMa(resultJt.total()));
        pagerJt.showJt(pageIdxJt, resultJt.items().size());
        itemsJt.revalidate();
        itemsJt.repaint();
    }

    private String emptyAllJt() {
        return "Todavía no hay elementos.";
    }

    private JPanel cardJt(BrowseItemMa itemJt) {
        int iconSizeJt = viewJt == ViewMa.GRID9 ? 24 : 32;
        Icon iconJt = itemJt.icon() != null ? itemJt.icon().apply(iconSizeJt)
                : AppIconsMa.placeholder(itemJt.title(), iconSizeJt);
        JLabel iconLabelJt = new JLabel(iconJt);
        iconLabelJt.setPreferredSize(new Dimension(iconSizeJt, iconSizeJt));
        JPanel iconWrapJt = new JPanel(new java.awt.GridBagLayout());
        iconWrapJt.setOpaque(false);
        iconWrapJt.add(iconLabelJt);

        JPanel cardJt = UiKitJtMa.roundedPanel(viewJt == ViewMa.GRID9 ? 8 : 12);
        cardJt.setLayout(new BorderLayout(12, 0));

        JButton moreJt = null;
        if (itemJt.menu() != null) {
            JButton btnJt = new JButton("···");
            btnJt.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            btnJt.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 4,10,4,10; font: bold +2");
            btnJt.setFocusable(false);
            btnJt.addActionListener(evJt -> itemJt.menu().get().show(btnJt, 0, btnJt.getHeight()));
            moreJt = btnJt;
        }
        ChipMa chipJt = itemJt.chipText() == null ? null : new ChipMa(itemJt.chipText(), itemJt.chipKind());

        JPanel nameJt = new JPanel(new GridLayout(viewJt == ViewMa.GRID9 ? 1 : 2, 1));
        nameJt.setOpaque(false);
        JLabel titleJt = new JLabel(itemJt.title());
        nameJt.add(titleJt);
        if (viewJt != ViewMa.GRID9) {
            nameJt.add(UiKitJtMa.subtitle(itemJt.subtitle() == null ? " " : itemJt.subtitle()));
        } else if (chipJt != null) {
            titleJt.setToolTipText(itemJt.chipText());
        }

        cardJt.add(iconWrapJt, BorderLayout.WEST);
        cardJt.add(nameJt, BorderLayout.CENTER);
        switch (viewJt) {
            case LIST -> {
                JPanel eastJt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
                eastJt.setOpaque(false);
                if (itemJt.extra() != null) {
                    BrowseItemMa.RowExtraMa extraJt = itemJt.extra();
                    eastJt.add(cellJt(new JLabel(extraJt.timeText() == null ? "" : extraJt.timeText()), 90));
                    eastJt.add(cellJt(extraJt.categoryText() == null ? new JLabel(" ")
                            : new ChipMa(extraJt.categoryText(), ChipMa.KindJtMa.NEUTRAL), 130));
                    eastJt.add(cellJt(extraJt.quotaRatio() == null ? new JLabel(" ")
                            : quotaJt(extraJt.quotaRatio(), extraJt.quotaLabel()), 190));
                    eastJt.add(cellJt(chipJt == null ? new JLabel(" ") : chipJt, 130));
                    if (extraJt.actionText() != null) {
                        JButton actionJt = new JButton(extraJt.actionText(), IconsJtMa.of("gear", 16));
                        actionJt.setFocusable(false);
                        actionJt.setPreferredSize(new Dimension(172, actionJt.getPreferredSize().height));
                        actionJt.addActionListener(evJt -> extraJt.action().run());
                        eastJt.add(actionJt);
                    }
                    if (moreJt != null) {
                        eastJt.add(moreJt);
                    }
                    cardJt.add(eastJt, BorderLayout.EAST);
                    cardJt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
                    break;
                }
                if (itemJt.progress() != null) {
                    eastJt.add(progressJt(itemJt.progress()));
                }
                if (chipJt != null) {
                    eastJt.add(chipJt);
                }
                if (moreJt != null) {
                    eastJt.add(moreJt);
                }
                cardJt.add(eastJt, BorderLayout.EAST);
                cardJt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
            }
            case GRID4 -> {
                JPanel southJt = new JPanel(new BorderLayout());
                southJt.setOpaque(false);
                JPanel chipWrapJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                chipWrapJt.setOpaque(false);
                if (chipJt != null) {
                    chipWrapJt.add(chipJt);
                }
                southJt.add(chipWrapJt, BorderLayout.CENTER);
                if (moreJt != null) {
                    southJt.add(moreJt, BorderLayout.EAST);
                }
                cardJt.add(southJt, BorderLayout.SOUTH);
                cardJt.setPreferredSize(new Dimension(270, 112));
            }
            case GRID9 -> {
                if (moreJt != null) {
                    cardJt.add(moreJt, BorderLayout.EAST);
                }
                cardJt.setPreferredSize(new Dimension(210, 52));
            }
        }
        return cardJt;
    }

    /** Un componente dentro de una celda de ancho fijo, para que las columnas queden alineadas entre filas. */
    private static JPanel cellJt(javax.swing.JComponent contentJt, int widthJt) {
        JPanel cellJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cellJt.setOpaque(false);
        cellJt.setPreferredSize(new Dimension(widthJt, 30));
        cellJt.add(contentJt);
        return cellJt;
    }

    /** Barra de uso contra el límite diario, con el límite escrito a la derecha. */
    private static JPanel quotaJt(double ratioJt, String labelJt) {
        JProgressBar barJt = new JProgressBar(0, 100);
        barJt.setValue((int) Math.min(100, Math.round(ratioJt * 100)));
        barJt.setPreferredSize(new Dimension(110, 8));
        if (ratioJt >= 1) {
            barJt.putClientProperty(FlatClientProperties.STYLE, "foreground: #D13438");
        } else if (ratioJt >= 0.85) {
            barJt.putClientProperty(FlatClientProperties.STYLE, "foreground: #E8A317");
        }
        JPanel rowJt = new JPanel(new BorderLayout(8, 0));
        rowJt.setOpaque(false);
        JPanel barWrapJt = new JPanel(new java.awt.GridBagLayout());
        barWrapJt.setOpaque(false);
        barWrapJt.add(barJt);
        rowJt.add(barWrapJt, BorderLayout.CENTER);
        rowJt.add(new JLabel(labelJt == null ? "" : labelJt), BorderLayout.EAST);
        return rowJt;
    }

    static JPanel progressJt(double ratioJt) {
        JProgressBar barJt = new JProgressBar(0, 100);
        barJt.setValue((int) Math.min(100, Math.round(ratioJt * 100)));
        barJt.setPreferredSize(new Dimension(110, 8));
        if (ratioJt >= 1) {
            barJt.putClientProperty(FlatClientProperties.STYLE, "foreground: #D13438");
        } else if (ratioJt >= 0.85) {
            barJt.putClientProperty(FlatClientProperties.STYLE, "foreground: #E8A317");
        }
        JLabel pctJt = new JLabel(Math.round(ratioJt * 100) + "%");
        pctJt.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        JPanel boxJt = new JPanel(new java.awt.GridBagLayout());
        boxJt.setOpaque(false);
        JPanel rowJt = new JPanel(new BorderLayout(8, 0));
        rowJt.setOpaque(false);
        rowJt.add(barJt, BorderLayout.CENTER);
        rowJt.add(pctJt, BorderLayout.EAST);
        boxJt.add(rowJt);
        return boxJt;
    }
}
