/*
 * PlotPanel.java
 *
 * Created on September 8, 2005, 1:06 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.pmiapp.apcs;
import java.awt.*;
import javax.swing.JPanel;

/**
 * Panel for showing scrolling plot of pressure versus time
 * @author Ron V. Webber
 */
public class PlotPanel extends JPanel implements java.awt.print.Printable {
    
    /**
     * Creates a new instance of PlotPanel.  This should be placed into a form at runtime.
     */
    public PlotPanel() {
        super();
        setOpaque(false);
        setLayout(new BorderLayout());
        //setBorder(BorderFactory.createLineBorder(Color.black));
        setDoubleBuffered(false);
        startTime=0;
        numberOfDataPoints=0;
        dataArray=new double[1000];
        dataOffset=0;
        dashed=new BasicStroke[6];
        for (int i=0; i<=5; i++)
            dashed[i]=new BasicStroke(0.25f, 
                                      BasicStroke.CAP_BUTT,
                                      BasicStroke.JOIN_MITER,
                                      10.0f, dash1, i);
    }
    
    private double maxPressure;
    private int startTime;
    private int dataOffset;
    private double[] dataArray;
    private int numberOfDataPoints;
    private final static BasicStroke solid = new BasicStroke(0.5f);
    private final static float dash1[] = {3.0f};
    private final static Color darkGreen = new Color(0,155,0);
    private BasicStroke[] dashed;
    
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
        int numYAxis=(size.height/20);
        double deltaP=1; // just in case
        if (numberOfDataPoints==0) {
            dataOffset=0;
            maxPressure=10;
        }
        else {
            maxPressure=10;
            for (int i=0; i<numberOfDataPoints; i++) {
                if (dataArray[i]>maxPressure) maxPressure=dataArray[i];
            }
            if (numberOfDataPoints>(size.width>>1))
                dataOffset=numberOfDataPoints - (size.width>>1);
            else
                dataOffset=0;
        }
        int y;
        int x = ((int)((startTime + dataOffset) % 3))<<1;
        if (numYAxis>0) {
            if (numYAxis>1)
                deltaP=Math.floor(maxPressure/(numYAxis-1))+1;
            if (deltaP<=0) deltaP=1;
            else if (deltaP>=10000) deltaP=Math.floor(deltaP/10000+1)*10000;
            else if (deltaP>=1000) deltaP=Math.floor(deltaP/1000+1)*1000;
            else if (deltaP>=100) deltaP=Math.floor(deltaP/100+1)*100;
            else if (deltaP>=10) deltaP=Math.floor(deltaP/10+1)*10;
            y=size.height-20;
            g2.setColor(darkGreen);
            g2.setStroke(solid);
            for (int i=1; i<=numYAxis; i++) {
                g2.drawLine(0, y, size.width-1, y);
                y-=20;
                if (g2.getStroke()==solid)
                    g2.setStroke(dashed[x]);
                else g2.setStroke(solid);
            }
            if (numYAxis>1)
                maxPressure=(size.height-20)*deltaP/20;
        }
        y = 5-(((size.height) % 6));
        x = (int)((startTime+dataOffset) % 20);
        if (x==0) x=20;
        if (x<=10) g2.setStroke(dashed[y]);
        else {
            x-=10;
            g2.setStroke(solid);
        }
        x=20-(x*2);
        while (x<size.width) {
            g2.drawLine(x,0,x,size.height-1);
            x+=20;
                if (g2.getStroke()==solid)
                    g2.setStroke(dashed[y]);
                else g2.setStroke(solid);            
        }
        y=size.height-21;
        int p=0;
        g2.setColor(Color.green);
        g2.setStroke(solid);
        while (numYAxis>0) {
            g2.drawString(""+p,1,y);
            y-=20;
            p+=(int)deltaP;
            numYAxis--;
        }
        String s = "0"+((startTime+dataOffset) % 60);
        g2.drawString(""+((startTime+dataOffset)/60)+":"+s.substring(s.length()-2)+"+",1,size.height-1);
        if (numberOfDataPoints>0) {
            x=0;
            int lastX=0;
            int lastY=size.height-20-(int)(dataArray[dataOffset]*(size.height-20)/maxPressure);
            g2.setColor(Color.red);
            for (int i=dataOffset; i<numberOfDataPoints; i++) {
                y=size.height-20-(int)(dataArray[i]*(size.height-20)/maxPressure);
                g2.drawLine(lastX,lastY, x,y);
                lastX=x;
                lastY=y;
                x+=2;
            }
        }
    }
    
    /**
     * From Printable interface.  Called to print the graph.  You don't normally call this
     * directly.  This is called when you create a print object, set the printable of this
     * object to the PlotPanel component, and then call the print method of the print object.
     * The print object will then call this method as many times as it needs to.
     * @param g graphics context of the printer
     * @param pf PageFormat of the printer (this only works on portrait)
     * @param pi Page number (starts at 0, which is the only page that actually gets printed)
     * @throws java.awt.print.PrinterException thrown when the print job is terminated
     * @return NO_SUCH_PAGE if pi>=1 or no data points to print, PAGE_EXISTS otherwise.
     */
    public int print(java.awt.Graphics g, java.awt.print.PageFormat pf, int pi) throws java.awt.print.PrinterException {
        if ((pi>=1) || (numberOfDataPoints<2))
            return java.awt.print.Printable.NO_SUCH_PAGE;
        // draw everything to g using page format pf
        // recast to Graphics2D
        Graphics2D g2 = (Graphics2D) g;
        // move (0,0) to printable space
        g2.translate(pf.getImageableX(),pf.getImageableY());
        // use the default font, but in 10 point size
        java.awt.Font f = g2.getFont().deriveFont(10.0f);
        g2.setFont(f);
//        if (pi==1) {
//            // for debugging purposes, dump a table of the seconds and data points
//            int y=10;
//            for (int i=0; i<numberOfDataPoints; i++) {
//                g2.drawString(""+i+" : "+dataArray[i],0,y);
//                y+=10;
//            }
//            return java.awt.print.Printable.PAGE_EXISTS;
//        }
        // print "PSI" as the Y-axis title, centered on X at Y=10
        g2.drawString("PSI",(float)((pf.getImageableWidth()-f.getStringBounds("PSI",g2.getFontRenderContext()).getWidth())/2), 10);
        // do Y-axis pressure values with 0 centered around x=30 and maximum X centered around x=maxX-30
        // yd is the differential between each Y-axis value
        // axisMaxY is the maximum Y-axis value
        double factor = Math.pow(10.,Math.floor(Math.log10(maxPressure*1.0001)));
        double yd = factor/5.;
        // calculate our own maxPressure - the outside one is for the screen graph
        double maxPressureLocal=5; // minimum value
        for (int i=0; i<numberOfDataPoints; i++) if (dataArray[i]>maxPressureLocal) maxPressureLocal=dataArray[i];
        if (maxPressureLocal==factor) yd=factor/10.;
        if (maxPressureLocal>factor*2.) yd=factor/2.;
        if (maxPressureLocal>factor*5.) yd=factor;
        double axisMaxY=Math.floor(maxPressureLocal/yd)*yd;
        if ((maxPressureLocal/yd)-Math.floor(maxPressureLocal/yd)<0.000001) axisMaxY+=yd;
        double d=0.; // starting x-axis value
        // remember original stroke
        java.awt.Stroke originalStroke = g2.getStroke();
        while (d<=axisMaxY*1.001) {
            float y=(float)(d*(pf.getImageableWidth()-60)/maxPressureLocal+30);
            String s=String.format("%1.0f",d);
            g2.drawString(s,(float)(y-f.getStringBounds(s,g2.getFontRenderContext()).getWidth()/2),20);
            // set line to dashed
            g2.setStroke(dashed[0]);
            g2.drawLine((int)y, 30,(int)(y),(int)(pf.getImageableHeight()));
            // set line to solid
            g2.setStroke(solid);
            g2.drawLine((int)y, 25,(int)(y),30);
            d+=yd;
        }
        // remember original transform
        java.awt.geom.AffineTransform saveAT = g2.getTransform();
        // rotate positive 90 degrees
        g2.transform(new java.awt.geom.AffineTransform(0,1,-1,0,0,0));
        // x now goes from 0 to ImageableHeight
        // y now goes from 0 to -ImageableWidth
        // if "Seconds" had any descenders, we would have to make room for them
        g2.drawString("Seconds",(float)((pf.getImageableHeight()-30-f.getStringBounds("Seconds",g2.getFontRenderContext()).getWidth())/2), 0);
        // now for the X axis
        factor = Math.pow(10., Math.floor(Math.log10((numberOfDataPoints-1)*1.0001)));
        double xd= factor / 5;
        if ((numberOfDataPoints-1)==factor) xd=factor/10.;
        if ((numberOfDataPoints-1)>factor*2.) xd=factor/2.;
        if ((numberOfDataPoints-1)>factor*5.) xd=factor;
        double axisMinX=Math.floor(startTime/xd) * xd;
        if (axisMinX<startTime) axisMinX+=xd;
        d=(numberOfDataPoints-1+startTime)/xd;
        double axisMaxX=Math.floor(d)*xd;
        if (d-Math.floor(d)<0.000001) axisMaxX+=xd;
        d=axisMinX;
        String formatString="%1.0f";
        if (xd<0.9) formatString="%2.1f";
        while (d<=axisMaxX*1.001) {
            // percentage along entire x-axis to place tick mark
            float x=(float)((d-startTime)/(numberOfDataPoints-1));
            String s=String.format(formatString,d);
            // width of string to put at tick mark
            float w=(float)(f.getStringBounds(s,g2.getFontRenderContext()).getWidth());
            // only use x percent of the width, so the left most string is left-justified and the
            // right-most string is right-justified (and the middle string is centered)
            w=w*x;
            // convert x into actual x-axis value
            x=x*((float)pf.getImageableHeight()-30)+30;
            g2.drawString(s,x-w,-10);
            // set line to dashed
            g2.setStroke(dashed[0]);
            g2.drawLine((int)x,-30,(int)x,-((int)pf.getImageableWidth()-30));
            // set line to solid
            g2.setStroke(solid);
            g2.drawLine((int)x,-25,(int)x,-30);
            d+=xd;
        }
        // draw graph title, which for us is just the current time and date
        java.util.Date date=new java.util.Date();
        String s=java.text.DateFormat.getTimeInstance().format(date)+"  "+java.text.DateFormat.getDateInstance().format(date);
        g2.drawString(s,(float)((pf.getImageableHeight()-30-f.getStringBounds(s,g2.getFontRenderContext()).getWidth())/2),-((float)pf.getImageableWidth()-10));
        // draw axis legend
        int i=-((int)pf.getImageableWidth()-20);
        g2.drawLine(30, i, 60, i);
        g2.drawString("Pressure vs. Time",61,i);
        // now plot all of the data points
        // use a slightly thicker line
        g2.setStroke(new BasicStroke(0.75f));
        int lastX=0,lastY=0,currentX,currentY;
        for (i=0; i<numberOfDataPoints; i++) {
            currentX=(int)(i*(pf.getImageableHeight()-30)/(numberOfDataPoints-1)+30);
            currentY=-(int)(dataArray[i]*(pf.getImageableWidth()-60)/maxPressureLocal+30);
            if (i>0) g2.drawLine(lastX,lastY,currentX,currentY);
            lastX=currentX;
            lastY=currentY;
        }
        // restore previous transform
        g2.setTransform(saveAT);
        // draw main axis using slightly thinner line
        g2.setStroke(solid);
        g2.drawLine(30,30, 30, (int)pf.getImageableHeight());
        g2.drawLine(30, 30, (int)pf.getImageableWidth()-30, 30);
        // restore original stroke
        g2.setStroke(originalStroke);
        // all done
        return java.awt.print.Printable.PAGE_EXISTS;
    }
    
    /**
     * Clear the panel of all data, and resets time to 0.
     */
    public void clear() {
        startTime=0;
        numberOfDataPoints=0;
        repaint();
    }
    /**
     * Add the next pressure value at the next time interval.  This should be called once per
     * second with a new pressure value.  If the graph can be updated without scrolling, this
     * will draw the next line segment.  If not, this will cause a repaint event.
     * @param d Pressure value, in PSI
     */
    public void addDataPoint(double d) {
        if (numberOfDataPoints==dataArray.length) {
            for (int i=0; i<numberOfDataPoints-1; i++)
                dataArray[i]=dataArray[i+1];
            dataArray[numberOfDataPoints-1]=d;
            startTime++;
            repaint();
            return;
        }
        dataArray[numberOfDataPoints]=d;
        numberOfDataPoints++;
        if ((dataOffset>0) || (d > maxPressure)) {
            repaint();
            return;
        }
        Dimension size = getSize();
        if (numberOfDataPoints>(size.width>>1)) {
            repaint();
            return;
        }
        Graphics2D g2 = (Graphics2D) this.getGraphics();
        g2.setColor(Color.red);
        int x;
        int y=size.height-20-(int)(d*(size.height-20)/maxPressure);
        int lastX;
        int lastY;
        if (numberOfDataPoints==1) {
            x=0;
            lastX=0;
            lastY=y;
        }
        else {
            x=(numberOfDataPoints<<1);
            lastX=x-2;
            lastY=size.height-20-(int)(dataArray[numberOfDataPoints-2]*(size.height-20)/maxPressure);
        }
        g2.drawLine(lastX,lastY,x,y);
        g2.dispose();        
    }
}
