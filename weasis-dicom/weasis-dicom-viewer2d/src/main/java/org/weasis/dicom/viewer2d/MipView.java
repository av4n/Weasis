package org.weasis.dicom.viewer2d;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JProgressBar;

import org.dcm4che2.data.UID;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.op.MaxCollectionZprojection;
import org.weasis.core.api.image.op.MeanCollectionZprojection;
import org.weasis.core.api.image.op.MinCollectionZprojection;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.RawImageIO;
import org.weasis.dicom.viewer2d.mpr.SeriesBuilder;

public class MipView extends View2d {
    public static final ImageIcon MIP_ICON_SETTING = new ImageIcon(
        MipView.class.getResource("/icon/22x22/mip-setting.png"));
    public static final ActionW MIP = new ActionW("MIP", "mip", 0, 0, null);
    public static final ActionW MIP_MIN_SLICE = new ActionW("Min Slice: ", "mip_min", 0, 0, null);
    public static final ActionW MIP_MAX_SLICE = new ActionW("Max Slice: ", "mip_max", 0, 0, null);

    public enum Type {
        MIN("min-MIP"), MEAN("mean-MIP"), MAX("MIP");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    };

    private final ViewButton mip_button;

    private final JProgressBar progressBar;
    private volatile Thread process;

    public MipView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        this.mip_button = new ViewButton(new MipPopup(), MIP_ICON_SETTING);
        viewButtons.add(mip_button);
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        // Force to extend VOI LUT to pixel allocated
        actionsInView.put(DicomImageElement.FILL_OUTSIDE_LUT, true);
        actionsInView.put(MIP_MIN_SLICE.cmd(), 1);
        actionsInView.put(MIP_MAX_SLICE.cmd(), 15);
    }

    @Override
    protected Rectangle drawExtendedAtions(Graphics2D g2d) {
        // Does not allow to use PR or KO
        Icon icon = mip_button.getIcon();
        int x = getWidth() - icon.getIconWidth() - 5;
        int y = (int) ((getHeight() - 1) * 0.5);
        mip_button.x = x;
        mip_button.y = y;
        icon.paintIcon(this, g2d, x, y);

        if (progressBar.isVisible()) {
            int shiftx = getWidth() / 2 - progressBar.getWidth() / 2;
            int shifty = getHeight() / 2 - progressBar.getHeight() / 2;
            g2d.translate(shiftx, shifty);
            progressBar.paint(g2d);
            g2d.translate(-shiftx, -shifty);
        }
        return mip_button.getBounds();
    }

    public void setMIPSeries(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
        super.setSeries(series, selectedDicom);
    }

    @Override
    public void setSeries(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
        // If series is updates by other actions than MIP, the view is reseted
        exitMipMode(series, selectedDicom);
    }

    public void exitMipMode(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
        // reset current process
        this.setActionsInView(MipView.MIP.cmd(), null);
        this.setActionsInView(MipView.MIP_MIN_SLICE.cmd(), null);
        this.setActionsInView(MipView.MIP_MAX_SLICE.cmd(), null);
        this.applyMipParameters();

        ImageViewerPlugin<DicomImageElement> container = this.getEventManager().getSelectedView2dContainer();
        container.setSelectedAndGetFocus();
        View2d newView2d = new View2d(this.getEventManager());
        newView2d.registerDefaultListeners();
        newView2d.setSeries(series, selectedDicom);
        container.replaceView(this, newView2d);
    }

    public void applyMipParameters() {
        if (process != null) {
            final Thread t = process;
            process = null;
            t.interrupt();
        }

        final Integer min = (Integer) getActionValue(MIP_MIN_SLICE.cmd());
        final Integer max = (Integer) getActionValue(MIP_MAX_SLICE.cmd());
        if (series == null || min == null || max == null) {
            return;
        }
        GuiExecutor.instance().invokeAndWait(new Runnable() {

            @Override
            public void run() {
                progressBar.setVisible(true);
                progressBar.setMinimum(0);
                progressBar.setMaximum(max - min + 1);
                Dimension dim = new Dimension(getWidth() / 2, 30);
                progressBar.setSize(dim);
                progressBar.setPreferredSize(dim);
                progressBar.setMaximumSize(dim);
                progressBar.setValue(0);
                progressBar.setStringPainted(true);
                // Required for Substance l&f
                progressBar.updateUI();
                repaint();
            }
        });

        process = new Thread("Building MIP view") {
            @Override
            public void run() {
                try {
                    MipView imageOperation = MipView.this;
                    Type mipType = (Type) imageOperation.getActionValue(MIP.cmd());
                    PlanarImage curImage = null;
                    MediaSeries<DicomImageElement> series = imageOperation.getSeries();
                    if (series != null) {
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                progressBar.setValue(0);
                                MipView.this.repaint();
                            }
                        });

                        SeriesComparator sort =
                            (SeriesComparator) imageOperation.getActionValue(ActionW.SORTSTACK.cmd());
                        Boolean reverse = (Boolean) imageOperation.getActionValue(ActionW.INVERSESTACK.cmd());
                        Comparator sortFilter = (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
                        Filter filter = (Filter) imageOperation.getActionValue(ActionW.FILTERED_SERIES.cmd());
                        Iterable<DicomImageElement> medias = series.copyOfMedias(filter, sortFilter);
                        DicomImageElement firstDcm = null;
                        // synchronized (series) {
                        Iterator<DicomImageElement> iter = medias.iterator();
                        int startIndex = min - 1;
                        int k = 0;
                        if (startIndex > 0) {
                            while (iter.hasNext()) {
                                DicomImageElement dcm = iter.next();
                                if (k >= startIndex) {
                                    firstDcm = dcm;
                                    break;
                                }
                                k++;
                            }
                        } else {
                            if (iter.hasNext()) {
                                firstDcm = iter.next();
                            }
                        }

                        int stopIndex = max - 1;
                        if (firstDcm != null) {
                            List<ImageElement> sources = new ArrayList<ImageElement>();
                            sources.add(firstDcm);
                            while (iter.hasNext()) {
                                if (this.isInterrupted()) {
                                    return;
                                }
                                DicomImageElement dcm = iter.next();
                                sources.add(dcm);

                                if (k >= stopIndex) {
                                    break;
                                }
                                k++;
                            }
                            if (sources.size() > 1) {
                                curImage = addCollectionOperation(mipType, sources, MipView.this, progressBar);
                            } else {
                                curImage = null;
                            }
                        }
                        // }

                        if (curImage != null && firstDcm != null) {
                            RawImage raw = null;
                            try {
                                raw = new RawImage(File.createTempFile("mip_", ".raw", SeriesBuilder.MPR_CACHE_DIR));//$NON-NLS-1$ //$NON-NLS-2$);
                                writeRasterInRaw(curImage.getAsBufferedImage(), raw.getOutputStream());
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                if (raw != null) {
                                    raw.disposeOutputStream();
                                }
                            }
                            if (raw == null) {
                                return;
                            }
                            RawImageIO rawIO = new RawImageIO(raw.getFile().toURI(), null);
                            int bitsAllocated = firstDcm.getBitsAllocated();
                            int bitsStored = firstDcm.getBitsStored();
                            String photometricInterpretation = firstDcm.getPhotometricInterpretation();
                            // Tags with same values for all the Series
                            rawIO.setTag(TagW.TransferSyntaxUID, UID.ImplicitVRLittleEndian);
                            rawIO.setTag(TagW.Columns, curImage.getWidth());
                            rawIO.setTag(TagW.Rows, curImage.getHeight());
                            // rawIO.setTag(TagW.SliceThickness, origPixSize);
                            double origPixSize = firstDcm.getPixelSize();
                            rawIO.setTag(TagW.PixelSpacing, new double[] { origPixSize, origPixSize });
                            rawIO.setTag(TagW.SeriesInstanceUID,
                                "mip." + (String) series.getTagValue(TagW.SubseriesInstanceUID));
                            rawIO.setTag(TagW.ImageOrientationPatient,
                                firstDcm.getTagValue(TagW.ImageOrientationPatient));

                            rawIO.setTag(TagW.BitsAllocated, bitsAllocated);
                            rawIO.setTag(TagW.BitsStored, bitsStored);
                            rawIO.setTag(TagW.PixelRepresentation, firstDcm.getTagValue(TagW.PixelRepresentation));
                            rawIO.setTag(TagW.Units, firstDcm.getTagValue(TagW.Units));
                            rawIO.setTag(TagW.ImageType, firstDcm.getTagValue(TagW.ImageType));
                            rawIO.setTag(TagW.SamplesPerPixel, firstDcm.getTagValue(TagW.SamplesPerPixel));
                            rawIO.setTag(TagW.PhotometricInterpretation, photometricInterpretation);
                            rawIO.setTag(TagW.MonoChrome, firstDcm.getTagValue(TagW.MonoChrome));
                            rawIO.setTag(TagW.Modality, firstDcm.getTagValue(TagW.Modality));

                            // TODO take dicom tags from middle image? what to do when values are not constant in the
                            // series?
                            rawIO.setTagNoNull(TagW.PixelSpacingCalibrationDescription,
                                firstDcm.getTagValue(TagW.PixelSpacingCalibrationDescription));
                            rawIO
                                .setTagNoNull(TagW.ModalityLUTSequence, firstDcm.getTagValue(TagW.ModalityLUTSequence));
                            rawIO.setTagNoNull(TagW.RescaleSlope, firstDcm.getTagValue(TagW.RescaleSlope));
                            rawIO.setTagNoNull(TagW.RescaleIntercept, firstDcm.getTagValue(TagW.RescaleIntercept));
                            rawIO.setTagNoNull(TagW.RescaleType, firstDcm.getTagValue(TagW.RescaleType));
                            // rawIO.setTagNoNull(TagW.SmallestImagePixelValue,
                            // img.getTagValue(TagW.SmallestImagePixelValue));
                            // rawIO.setTagNoNull(TagW.LargestImagePixelValue,
                            // img.getTagValue(TagW.LargestImagePixelValue));
                            rawIO.setTagNoNull(TagW.PixelPaddingValue, firstDcm.getTagValue(TagW.PixelPaddingValue));
                            rawIO.setTagNoNull(TagW.PixelPaddingRangeLimit,
                                firstDcm.getTagValue(TagW.PixelPaddingRangeLimit));

                            rawIO.setTagNoNull(TagW.VOILUTSequence, firstDcm.getTagValue(TagW.VOILUTSequence));
                            // rawIO.setTagNoNull(TagW.WindowWidth, img.getTagValue(TagW.WindowWidth));
                            // rawIO.setTagNoNull(TagW.WinArrayListdowCenter, img.getTagValue(TagW.WindowCenter));
                            // rawIO.setTagNoNull(TagW.WindowCenterWidthExplanation,
                            // img.getTagValue(TagW.WindowCenterWidthExplanation));
                            rawIO.setTagNoNull(TagW.VOILutFunction, firstDcm.getTagValue(TagW.VOILutFunction));

                            // Image specific tags
                            rawIO.setTag(TagW.SOPInstanceUID, "mip.1");
                            rawIO.setTag(TagW.InstanceNumber, 1);

                            DicomImageElement dicom = new DicomImageElement(rawIO, 0);
                            DicomImageElement oldImage = getImage();
                            // Use graphics of the previous image when they belongs to a MIP image
                            if (oldImage != null && "mip.1".equals(oldImage.getTagValue(TagW.SOPInstanceUID))) {
                                dicom.setTag(TagW.MeasurementGraphics, oldImage.getTagValue(TagW.MeasurementGraphics));
                            }

                            setImage(dicom, false);
                        }
                        // TODO check images have similar modality and VOI LUT, W/L, LUT shape...
                        // imageLayer.updateAllImageOperations();

                        // actionsInView.put(ActionW.PREPROCESSING.cmd(), manager);
                        // imageLayer.setPreprocessing(manager);

                        // Following actions need to be executed in EDT thread
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                progressBar.setVisible(false);
                                DicomImageElement image = imageLayer.getSourceImage();
                                if (image != null) {
                                    // Update statistics
                                    List<Graphic> list = (List<Graphic>) image.getTagValue(TagW.MeasurementGraphics);
                                    if (list != null) {
                                        for (Graphic graphic : list) {
                                            graphic.updateLabel(true, MipView.this);
                                        }
                                    }
                                }
                            }
                        });

                    }
                } finally {
                    progressBar.setVisible(false);
                }
            }
        };
        process.start();
    }

    public static PlanarImage arithmeticOperation(String operation, PlanarImage img1, PlanarImage img2) {
        ParameterBlockJAI pb2 = new ParameterBlockJAI(operation);
        pb2.addSource(img1);
        pb2.addSource(img2);
        return JAI.create(operation, pb2);
    }

    public static PlanarImage addCollectionOperation(Type mipType, List<ImageElement> sources, MipView mipView,
        JProgressBar progressBar) {
        if (Type.MIN.equals(mipType)) {
            MinCollectionZprojection op = new MinCollectionZprojection(sources, mipView, progressBar);
            return op.computeMinCollectionOpImage();
        }
        if (Type.MEAN.equals(mipType)) {
            MeanCollectionZprojection op = new MeanCollectionZprojection(sources, mipView, progressBar);
            return op.computeMeanCollectionOpImage();
        }
        MaxCollectionZprojection op = new MaxCollectionZprojection(sources, mipView, progressBar);
        return op.computeMaxCollectionOpImage();
    }

    private static void writeRasterInRaw(BufferedImage image, OutputStream out) throws IOException {
        if (out != null && image != null) {
            DataBuffer dataBuffer = image.getRaster().getDataBuffer();
            byte[] bytesOut = null;
            if (dataBuffer instanceof DataBufferByte) {
                bytesOut = ((DataBufferByte) dataBuffer).getData();
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data =
                    dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                        : ((DataBufferUShort) dataBuffer).getData();
                bytesOut = new byte[data.length * 2];
                for (int i = 0; i < data.length; i++) {
                    bytesOut[i * 2] = (byte) (data[i] & 0xFF);
                    bytesOut[i * 2 + 1] = (byte) ((data[i] >>> 8) & 0xFF);
                }
            }
            out.write(bytesOut);
        }
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }
}
