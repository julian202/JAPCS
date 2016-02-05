/*
 * CalibratePressureGauge.java
 *
 * Created on October 19, 2005, 12:51 PM
 */

package com.pmiapp.apcs;

/**
 * Modal form to calibrate pressure gauge
 * @author  Ron V. Webber
 */
public class CalibratePressureGauge extends javax.swing.JDialog {
    
    /**
     * Modal form for calibrating the pressure gauge.  It doesn't matter if there is a gauge
     * read listener running in the parent form or not.  If there is, this may run slower.
     * This form reads the pressure gauge, but it does not send readings back to the caller.
     * @param parent Parent form, used for centering and modality
     * @param commSystem open communication system
     * @param askForConfirmation true if we need to ask the user to confirm the new calibration values
     * @param gaugeIndex 0 for main gauge, 1 for secondary
     */
    public CalibratePressureGauge(java.awt.Frame parent, APCSCommunication commSystem,
            int gaugeIndex) {
        super(parent, true);
        this.commSystem=commSystem;
        this.gaugeIndex=gaugeIndex;
        initComponents();
        // center form on parent
        setLocationRelativeTo(parent);
    }
    
    /**
     * Start the gauge reading thread
     * @param askForConfirmation true if the thread should ask for confirmation
     */
    public void initialize(boolean askForConfirmation) {
        if (commSystem.isDemoMode())
            thread = new WaitThread(this);
        else
            thread = new RunCalibrationThread(this,askForConfirmation);
        thread.start();
    }
    
    private APCSCommunication commSystem;
    private java.lang.Thread thread;
    private int gaugeIndex;

    private class WaitThread extends java.lang.Thread {
        public WaitThread(javax.swing.JDialog parent) {
            this.parent = parent;
        }
        
        private javax.swing.JDialog parent;
        
        @Override
        public void run() {
            // wait 4 seconds.  This can't be aborted
            try {WaitThread.sleep(4000);}
            catch (InterruptedException e) {}
            // when done, invoke this back in the Swing thread
            java.awt.EventQueue.invokeLater(new Runnable() {
                public void run() {
                    parent.setVisible(false);
                }});
        }
    }
    
    private class RunCalibrationThread extends java.lang.Thread {

        public RunCalibrationThread(javax.swing.JDialog parent, boolean askForConfirmation) {
            this.parent = parent; 
            this.askForConfirmation=askForConfirmation;
        }
        
        private javax.swing.JDialog parent;
        private boolean askForConfirmation;
        
        @Override
        public void run() {
            // deactivate any relay that may be on
            commSystem.setRelay(false);
            // wait 5 seconds
            try {RunCalibrationThread.sleep(5000);}
            catch (InterruptedException e) {}
            int sum=5; // initial value so we round to nearest int when dividing by 10
            int i;
            for (i=1; i<=10; i++)
                sum+=commSystem.rawReading(2+gaugeIndex);
            int base=sum/10;
            // activate relay
            commSystem.setRelay(true);
            // wait 5 seconds
            try {RunCalibrationThread.sleep(5000);}
            catch (InterruptedException e) {}
            sum=5;
            for (i=1; i<=10; i++)
                sum+=commSystem.rawReading(2+gaugeIndex);
            int top=(sum-base*10)/8+base;
            // deactivate relay
            commSystem.setRelay(false);
            // wait 5 seconds
            try {RunCalibrationThread.sleep(5000);}
            catch (InterruptedException e) {}
            // if askForConfirmation is true, reset askForConfirmation to false
            // (meaning that we should store the results without confirmation)
            // only if they don't answer "NO" to the confiration question
            String mainOrSecondary="Main";
            if (gaugeIndex==1) mainOrSecondary="Secondary";
            if (askForConfirmation)
                askForConfirmation=(javax.swing.JOptionPane.showConfirmDialog(parent,
                        mainOrSecondary+" Pressure Gauge Low Value (2000 counts)="+base+
                        "\n"+mainOrSecondary+" Pressure Gauge High Value (62000 counts)="+top,
                        "Accept These Values?",
                        javax.swing.JOptionPane.YES_NO_OPTION)==javax.swing.JOptionPane.NO_OPTION);
            if (askForConfirmation==false) {
                commSystem.getConfigSettings().getGaugeChannel(2+gaugeIndex).setZeroCount(base);
                commSystem.getConfigSettings().getGaugeChannel(2+gaugeIndex).setFullCount(top);
            }
            // when done, invoke this back in the Swing thread
            java.awt.EventQueue.invokeLater(new Runnable() {
                public void run() {
                    parent.setVisible(false);
                }});
        }        
    }

    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        jLabel1 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("System Busy");
        setResizable(false);
        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 24));
        jLabel1.setText("Calibrating - Please Wait");
        getContentPane().add(jLabel1, java.awt.BorderLayout.CENTER);

        pack();
    }
    // </editor-fold>//GEN-END:initComponents
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    // End of variables declaration//GEN-END:variables
    
}
