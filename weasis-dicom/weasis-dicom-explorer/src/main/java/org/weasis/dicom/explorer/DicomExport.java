/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;

import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.dicom.explorer.internal.Activator;

public class DicomExport extends AbstractWizardDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomExport.class);

    private final ServiceTracker prefs_tracker;
    private final DicomModel dicomModel;
    private final ExportTree exportTree;

    public DicomExport(Frame parent, final DicomModel dicomModel) {
        super(parent,
            Messages.getString("DicomExport.exp_dicom"), ModalityType.APPLICATION_MODAL, new Dimension(640, 480)); //$NON-NLS-1$
        this.dicomModel = dicomModel;
        this.exportTree = new ExportTree(dicomModel);
        prefs_tracker = new ServiceTracker(Activator.getBundleContext(), DicomExportFactory.class.getName(), null);
        jPanelButtom.removeAll();
        final GridBagLayout gridBagLayout = new GridBagLayout();
        jPanelButtom.setLayout(gridBagLayout);

        final JButton exportandClose = new JButton(Messages.getString("DicomExport.exp_close")); //$NON-NLS-1$
        exportandClose.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                exportSelection(true);

            }
        });
        final GridBagConstraints gridBagConstraints_0 = new GridBagConstraints();
        gridBagConstraints_0.insets = new Insets(10, 15, 10, 0);
        gridBagConstraints_0.anchor = GridBagConstraints.EAST;
        gridBagConstraints_0.gridy = 0;
        gridBagConstraints_0.gridx = 0;
        gridBagConstraints_0.weightx = 1.0;
        jPanelButtom.add(exportandClose, gridBagConstraints_0);

        final JButton exportButton = new JButton();
        exportButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                exportSelection(false);
            }
        });
        exportButton.setText(Messages.getString("DicomExport.exp")); //$NON-NLS-1$
        final GridBagConstraints gridBagConstraints_1 = new GridBagConstraints();
        gridBagConstraints_1.insets = new Insets(10, 15, 10, 0);
        gridBagConstraints_1.anchor = GridBagConstraints.EAST;
        gridBagConstraints_1.gridy = 0;
        gridBagConstraints_1.gridx = 1;
        // gridBagConstraints_1.weightx = 1.0;
        jPanelButtom.add(exportButton, gridBagConstraints_1);

        jButtonClose.setText(Messages.getString("DicomExport.close")); //$NON-NLS-1$
        final GridBagConstraints gridBagConstraints_2 = new GridBagConstraints();
        gridBagConstraints_2.insets = new Insets(10, 15, 10, 15);
        gridBagConstraints_2.gridy = 0;
        gridBagConstraints_2.gridx = 2;
        jPanelButtom.add(jButtonClose, gridBagConstraints_2);

        initializePages();
        pack();
        showPageFirstPage();
    }

    @Override
    protected void initializePages() {
        pagesRoot.add(new DefaultMutableTreeNode(new LocalExport(dicomModel, exportTree)));

        try {
            prefs_tracker.open();

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        final Object[] servicesPref = prefs_tracker.getServices();
        for (int i = 0; (servicesPref != null) && (i < servicesPref.length); i++) {
            if (servicesPref[i] instanceof DicomExportFactory) {
                ExportDicom page = ((DicomExportFactory) servicesPref[i]).createDicomExportPage(null);
                if (page != null) {
                    pagesRoot.add(new DefaultMutableTreeNode(page));
                }
            }
        }
        iniTree();
    }

    private void exportSelection(boolean closeWin) {
        Object object = null;
        try {
            object = jScrollPanePage.getViewport().getComponent(0);
        } catch (Exception ex) {
        }
        if (object instanceof ExportDicom) {
            final ExportDicom selectedPage = (ExportDicom) object;
            if (closeWin) {
                cancel();
            }
            try {
                selectedPage.exportDICOM(exportTree, null);
            } catch (IOException e1) {
                LOGGER.error("DICOM export failed", e1.getMessage()); //$NON-NLS-1$
            }
        }
    }

    @Override
    public void cancel() {
        dispose();
    }

    @Override
    public void dispose() {
        closeAllPages();
        super.dispose();
    }

}
