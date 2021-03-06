/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.graphic.AdvancedShape.ScaleInvariantShape;
import org.weasis.core.ui.util.MouseEventDouble;

public class AnnotationGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(AnnotationGraphic.class.getResource("/icon/22x22/draw-text.png")); //$NON-NLS-1$

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected Point2D ptBox, ptAnchor; // Let AB be a simple a line segment
    protected boolean lineABvalid; // estimate if line segment is valid or not

    protected String[] labelStringArray;
    protected Rectangle2D labelBounds;
    protected double labelWidth;
    protected double labelHeight;

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public AnnotationGraphic(Point2D ptAnchoir, Point2D ptBox, float lineThickness, Color paintColor,
        boolean labelVisible) throws InvalidShapeException {
        super(2, paintColor, lineThickness, labelVisible, false);
        if (ptBox == null) {
            throw new InvalidShapeException("ptBox cannot be null!");
        }
        setHandlePointList(ptAnchoir, ptBox);
        if (!isShapeValid()) {
            throw new InvalidShapeException("This shape cannot be drawn");
        }
    }

    public AnnotationGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(2, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("Tools.Anno"); //$NON-NLS-1$
    }

    @Override
    protected void buildShape(MouseEventDouble mouseEvent) {
        updateTool();
        AdvancedShape newShape = null;

        if (ptBox != null) {
            DefaultView2d view = getDefaultView2d(mouseEvent);
            if (labelStringArray == null) {
                if (view != null) {
                    setLabel(new String[] { "Text box" }, view, ptBox);
                    // call buildShape
                    return;
                }
                if (labelStringArray == null || labelHeight == 0 || labelWidth == 0) {
                    // This graphic cannot be displayed, remove it.
                    fireRemoveAction();
                    return;
                }
            }
            newShape = new AdvancedShape(this, 2);
            Line2D line = null;
            if (lineABvalid) {
                line = new Line2D.Double(ptBox, ptAnchor);
            }
            labelBounds = new Rectangle.Double();
            labelBounds.setFrameFromCenter(ptBox.getX(), ptBox.getY(), ptBox.getX() + labelWidth / 2
                + GraphicLabel.GROWING_BOUND, ptBox.getY() + labelHeight * labelStringArray.length / 2
                + GraphicLabel.GROWING_BOUND);
            GeomUtil.growRectangle(labelBounds, GraphicLabel.GROWING_BOUND);
            if (line != null) {
                newShape.addLinkSegmentToInvariantShape(line, ptBox, labelBounds, getDashStroke(lineThickness), true);

                ScaleInvariantShape arrow =
                    newShape.addScaleInvShape(GeomUtil.getArrowShape(ptAnchor, ptBox, 15, 8), ptAnchor,
                        getStroke(lineThickness), true);
                arrow.setFilled(true);
            }
            newShape.addAllInvShape(labelBounds, ptBox, getStroke(lineThickness), true);

        }

        setShape(newShape, mouseEvent);
    }

    @Override
    public void paintLabel(Graphics2D g2d, AffineTransform transform) {
        if (labelVisible && labelStringArray != null && labelBounds != null) {
            Paint oldPaint = g2d.getPaint();

            Rectangle2D rect = labelBounds;
            Point2D pt = new Point2D.Double(rect.getCenterX(), rect.getCenterY());
            if (transform != null) {
                transform.transform(pt, pt);
            }

            float px = (float) (pt.getX() - rect.getWidth() / 2 + GraphicLabel.GROWING_BOUND);
            float py = (float) (pt.getY() - rect.getHeight() / 2 + GraphicLabel.GROWING_BOUND);

            for (String label : labelStringArray) {
                if (label.length() > 0) {
                    py += labelHeight;
                    GraphicLabel.paintColorFontOutline(g2d, label, px, py, Color.WHITE);
                }
            }
            g2d.setPaint(oldPaint);
        }
    }

    protected void setHandlePointList(Point2D ptAnchor, Point2D ptBox) {
        if (ptBox == null && ptAnchor != null) {
            ptBox = ptAnchor;
        }
        if (ptBox != null && ptBox.equals(ptAnchor)) {
            ptAnchor = null;
        }
        setHandlePoint(0, ptAnchor == null ? null : (Point2D) ptAnchor.clone());
        setHandlePoint(1, ptBox == null ? null : (Point2D) ptBox.clone());
        buildShape(null);
    }

    @Override
    public List<MeasureItem> computeMeasurements(ImageLayer layer, boolean releaseEvent) {
        return null;
    }

    protected void updateTool() {
        ptAnchor = getHandlePoint(0);
        ptBox = getHandlePoint(1);

        lineABvalid = ptAnchor != null && !ptAnchor.equals(ptBox);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public Point2D getAnchorPoint() {
        updateTool();
        return ptAnchor == null ? null : (Point2D) ptAnchor.clone();
    }

    public Point2D getBoxPoint() {
        updateTool();
        return ptBox == null ? null : (Point2D) ptBox.clone();
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return null;
    }

    protected void reset() {
        labelStringArray = null;
        labelBounds = null;
        labelHeight = labelWidth = 0;
    }

    @Override
    public void setLabel(String[] labels, DefaultView2d view2d) {
        Point2D pt = getBoxPoint();
        if (pt == null) {
            pt = getAnchorPoint();
        }
        if (pt != null) {
            this.setLabel(labels, view2d, pt);
        }
    }

    @Override
    public void setLabel(String[] labels, DefaultView2d view2d, Point2D pos) {
        if (view2d == null || labels == null || labels.length == 0 || pos == null) {
            reset();
        } else {
            labelStringArray = labels;
            Graphics2D g2d = (Graphics2D) view2d.getGraphics();
            Font defaultFont = g2d.getFont();
            FontRenderContext fontRenderContext = ((Graphics2D) view2d.getGraphics()).getFontRenderContext();

            updateBoundsSize(defaultFont, fontRenderContext);

            labelBounds = new Rectangle.Double();
            labelBounds.setFrameFromCenter(pos.getX(), pos.getY(), (labelWidth + GraphicLabel.GROWING_BOUND) / 2,
                ((labelHeight * labels.length) + GraphicLabel.GROWING_BOUND) * 2);
            labelBounds.setFrameFromCenter(pos.getX(), pos.getY(), ptBox.getX() + labelWidth / 2
                + GraphicLabel.GROWING_BOUND, ptBox.getY() + labelHeight * labelStringArray.length / 2
                + GraphicLabel.GROWING_BOUND);
            GeomUtil.growRectangle(labelBounds, GraphicLabel.GROWING_BOUND);
        }
        buildShape(null);
    }

    protected void updateBoundsSize(Font defaultFont, FontRenderContext fontRenderContext) {
        if (defaultFont == null) {
            throw new RuntimeException("Font should not be null"); //$NON-NLS-1$
        }
        if (fontRenderContext == null) {
            throw new RuntimeException("FontRenderContext should not be null"); //$NON-NLS-1$
        }

        if (labelStringArray == null || labelStringArray.length == 0) {
            reset();
        } else {
            double maxWidth = 0;
            for (String label : labelStringArray) {
                if (label.length() > 0) {
                    TextLayout layout = new TextLayout(label, defaultFont, fontRenderContext);
                    maxWidth = Math.max(layout.getBounds().getWidth(), maxWidth);
                }
            }
            labelHeight = new TextLayout("Tg", defaultFont, fontRenderContext).getBounds().getHeight() + 2; //$NON-NLS-1$
            labelWidth = maxWidth;
        }
    }

    @Override
    public BasicGraphic clone() {
        AnnotationGraphic newGraphic = (AnnotationGraphic) super.clone();
        newGraphic.labelBounds = labelBounds == null ? null : labelBounds.getBounds2D();
        newGraphic.labelWidth = labelWidth;
        newGraphic.labelHeight = labelHeight;
        newGraphic.labelStringArray = labelStringArray;
        return newGraphic;
    }
}
