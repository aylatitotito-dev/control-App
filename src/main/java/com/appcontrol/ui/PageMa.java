package com.appcontrol.ui;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** Una pantalla de la aplicacion. */
abstract class PageMa extends JPanel {

    private final String navTitleJt;

    PageMa(String navTitleJt) {
        super(new BorderLayout(0, 16));
        this.navTitleJt = navTitleJt;
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
    }

    String navTitle() {
        return navTitleJt;
    }

    /** Vuelve a leer los datos y repinta. Se llama al mostrar la pantalla y cada pocos segundos. */
    abstract void refreshMa();
}
