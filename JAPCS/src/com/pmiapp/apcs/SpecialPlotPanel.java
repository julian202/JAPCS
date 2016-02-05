/*
 * SpecialPlotPanel.java
 *
 * from PlotPanel.  Just for showing raw data curve in special burst test
 */

package com.pmiapp.apcs;
import java.awt.*;
import javax.swing.JPanel;

/**
 * Panel for showing plot of raw data
 * @author Ron V. Webber
 */
public class SpecialPlotPanel extends JPanel {
    
    /**
     * Creates a new instance of SpecialPlotPanel.  This should be placed into a form at runtime.
     */
    public SpecialPlotPanel() {
        super();
        setOpaque(false);
        setLayout(new BorderLayout());
        setDoubleBuffered(false);
        numberOfDataPoints=0;
        dataArray=new double[32767];
    }
    
    public void setData(int length, double[] array) {
        if (length > 32767) {
            numberOfDataPoints = 32767;
        } else {
            numberOfDataPoints = length;
        }
        System.arraycopy(array, 0, dataArray, 0, numberOfDataPoints);
        repaint();
    }
    
    public void addData(double value) {
        if (numberOfDataPoints < 32767) {
            dataArray[numberOfDataPoints] = value;
            numberOfDataPoints++;
        }
        repaint();
    }
    
    private double maxPressure;
    private double[] dataArray;
    private int numberOfDataPoints;
    private final static BasicStroke solid = new BasicStroke(0.5f);
    
    /**
     * Tells system how big this panel wants to be (which may not be how big it actually gets to be).
     * The normal size is 320x240
     * @return dimensions 320x240
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(320, 240);
    }
    
    /**
     * Called by system when this object needs to be repainted
     * @param g Graphics object that this panel is to use in repainting itself
     */
    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);
        g.fillRect(0,0,size.width,size.height);
        double xFactor, yFactor;
        if (numberOfDataPoints==0) {
            xFactor = 1;
            yFactor = 1;
        } else {
            xFactor = size.width;
            if (numberOfDataPoints>=2) xFactor = xFactor / (numberOfDataPoints - 1);
            maxPressure=10;
            for (int i=0; i<numberOfDataPoints; i++) {
                if (dataArray[i]>maxPressure) maxPressure=dataArray[i];
            }
            yFactor = size.height - 1;
            yFactor = yFactor / maxPressure;
        }
        int y;
        int x;
        int p=0;
        g2.setStroke(solid);
        if (numberOfDataPoints>0) {
            int lastX=0;
            int lastY=size.height - (int)(dataArray[0]*yFactor);
            g2.setColor(Color.red);
            for (int i=1; i<numberOfDataPoints; i++) {
                y=size.height - (int)(dataArray[i]*yFactor);
                x=(int)(i * xFactor);
                g2.drawLine(lastX,lastY, x,y);
                lastX=x;
                lastY=y;
            }
        }
    }

    protected double[] getDataArray() {
        double[] da = new double[numberOfDataPoints];
        System.arraycopy(dataArray, 0, da, 0, numberOfDataPoints);
        return da;
    }
}
