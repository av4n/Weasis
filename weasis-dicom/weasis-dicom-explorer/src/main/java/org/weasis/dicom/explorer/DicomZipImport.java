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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JProgressBar;
import javax.swing.border.TitledBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class DicomZipImport extends AbstractItemDialogPage implements ImportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomZipImport.class);

    private static final String lastDICOMDIR = "lastDicomZip";//$NON-NLS-1$

    private File selectedFile;
    private JButton btnOpen;

    public DicomZipImport() {
        super("DICOM Zip");
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        setBorder(new TitledBorder(null, "DICOM Zip", TitledBorder.LEADING, TitledBorder.TOP, null, null));

        btnOpen = new JButton("Open");
        btnOpen.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browseImgFile();
            }
        });
        add(btnOpen);

    }

    public void browseImgFile() {
        String directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(lastDICOMDIR, "");//$NON-NLS-1$

        JFileChooser fileChooser = new JFileChooser(directory);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileFilter(new FileFormatFilter("zip", "ZIP"));
        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION
            || (selectedFile = fileChooser.getSelectedFile()) == null) {
            return;
        } else {
            Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(lastDICOMDIR, selectedFile.getParent());
        }
    }

    protected void initialize(boolean afirst) {
        if (afirst) {

        }
    }

    public void resetSettingsToDefault() {
        initialize(false);
    }

    public void applyChange() {

    }

    protected void updateChanges() {
    }

    @Override
    public void closeAdditionalWindow() {
        applyChange();

    }

    @Override
    public void resetoDefaultValues() {
    }

    @Override
    public void importDICOM(DicomModel dicomModel, JProgressBar info) {
        loadDicomZip(selectedFile, dicomModel);
    }

    public static void loadDicomZip(File file, DicomModel dicomModel) {
        if (file != null) {
            ArrayList<LoadSeries> loadSeries = null;
            if (file.canRead()) {
                File dir = FileUtil.createTempDir("unzip");
                try {
                    FileUtil.unzip(file, dir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                DicomDirLoader dirImport = new DicomDirLoader(new File(dir, "DICOMDIR"), dicomModel, true);
                loadSeries = dirImport.readDicomDir();
            }
            if (loadSeries != null && loadSeries.size() > 0) {
                DicomModel.loadingExecutor.execute(new LoadDicomDir(loadSeries, dicomModel));
            } else {
                LOGGER.error("Cannot import DICOM from {}", file); //$NON-NLS-1$
            }
        }
    }

}
