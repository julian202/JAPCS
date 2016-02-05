/*
 * ResetSystem.java
 *
 * Created on October 7, 2005, 10:55 AM
 */

package com.pmiapp.apcs;
import com.pmiapp.common.AbortQueriable;
import com.pmiapp.common.GaugeReadListener;

/**
 * Modal form to reset system
 * @author  Ron V. Webber
 */
public class ResetSystem extends javax.swing.JDialog {
    
    /**
     * Modal form for resetting the system.  The caller must have an active GaugeReadThread that
     * is sending pressure readings to the GaugeReadListener.  This form reads the current pressure
     * from the GaugeReadListener but never sends it any values.
     * @param parent Parent form, used for centering and modality
     * @param gaugeReadListener a GaugeReadListener that can be queried for the current pressure.  Some other
     * thread must be sending this GaugeReadListener pressure readings.
     * @param commSystem open communication system
     * @param generatorIndex 0 for main system, 1 for secondary system, -1 for both
     */
    public ResetSystem(java.awt.Frame parent, GaugeReadListener gaugeReadListener,
            APCSCommunication commSystem, int generatorIndex) {
        super(parent, true);
        this.gaugeReadListener=gaugeReadListener;
        this.commSystem=commSystem;
        this.safe=true; // defaults to being safe
        this.generatorIndex=generatorIndex;
        abort=false;
        initComponents();
        // center form on parent
        setLocationRelativeTo(parent);
    }
    
    /**
     * Start the main thread
     */
    public void initialize() {
        if (commSystem.isDemoMode())
            thread = new WaitThread(this);
        else
            thread = new RunResetThread(this);
        thread.start();
    }
    
    /**
     * Modal form for resetting the system.  The caller must have an active GaugeReadThread that
     * is sending pressure readings to the GaugeReadListener.  This form reads the current pressure
     * from the GaugeReadListener but never sends it any values.
     * @param parent Parent form, used for centering and modality
     * @param gaugeReadListener a GaugeReadListener that can be queried for the current pressure.  Some other
     * thread must be sending this GaugeReadListener pressure readings.
     * @param commSystem open communication system
     * @param generatorIndex 0 for main system, 1 for secondary system
     * @param safe true if we don't need to open valve 2 to vent the sample
     */
    public ResetSystem(java.awt.Frame parent, GaugeReadListener gaugeReadListener,
            APCSCommunication commSystem, int generatorIndex, boolean safe) {
        super(parent, true);
        this.gaugeReadListener=gaugeReadListener;
        this.commSystem=commSystem;
        this.safe=safe;
        this.generatorIndex=generatorIndex;
        abort=false;
        initComponents();
        // center form on parent
        setLocationRelativeTo(parent);
    }
    
    private Boolean abort, safe;
    private GaugeReadListener gaugeReadListener;
    private APCSCommunication commSystem;
    private java.lang.Thread thread;
    private int generatorIndex;
    
    private class WaitThread extends java.lang.Thread {
        public WaitThread(javax.swing.JDialog parent) {
            this.parent = parent;
        }
        
        private javax.swing.JDialog parent;
        
        @Override
        public void run() {
            // wait 10 seconds, checking for abort every 0.1 seconds
            for (int i=1; i<100; i++) {
                try {WaitThread.sleep(100);}
                catch (InterruptedException e) {}
                // if we get past 2 seconds, mark the system safe
                if (i==20) safe=true;
                if (abort) break;
            }
            // when done, invoke this back in the Swing thread
            java.awt.EventQueue.invokeLater(new Runnable() {
                public void run() {
                    parent.setVisible(false);
                }});
        }
    }
    
    private class RunResetThread extends java.lang.Thread implements AbortQueriable {

        public RunResetThread(javax.swing.JDialog parent) {
            this.parent = parent; 
        }
        
        private javax.swing.JDialog parent;
        
        public boolean shouldStop() {
            //yield();
            return abort;
        }
        
        @Override
        public void run() {
            // if generatorIndex=-1 then we do both units
            boolean doUnit1=true;
            if (generatorIndex==1) doUnit1=false;
            boolean doUnit2=true;
            if (generatorIndex==0) doUnit2=false;
            // just to be safe...
            APCSConfig config=commSystem.getConfigSettings();
            if (config.getNumberOfGenerators()==1) {
                doUnit2=false;
                doUnit1=true;
            }
            // generator full reverse speed
            if (doUnit1) commSystem.setPGenSpeed(0,0);
            if (doUnit2) commSystem.setPGenSpeed(1,0);
            if (abort==false) {
                if (doUnit1) commSystem.setPGenSpeed(0,-1023);
                if (doUnit2) commSystem.setPGenSpeed(1,-1023);
            }
            // for systems with one generator and more than 2 valves, we need to close
            // the additional valves
            if (config.getNumberOfGenerators()==1) {
                // make sure valve 4 is closed (if it exists)
                if (config.getNumberOfMotorValves()>3)
                    commSystem.moveMotorValveAndWait(3,'C',this);
                // make sure valve 3 is closed (if it exists)
                if (config.getNumberOfMotorValves()>2)
                    commSystem.moveMotorValveAndWait(2,'C',this);
            }
            // wait for reverse limit or pressure below 1000 PSI
            while (abort==false) {
                boolean atLimit=true;
                if (doUnit1)
                    if ((gaugeReadListener.getCurrentPressure(0)>1000) &&  
                        (commSystem.getPGenLimit(0)>=0)) atLimit=false;
                if (doUnit2)
                    if ((gaugeReadListener.getCurrentPressure(1)>1000) &&
                        (commSystem.getPGenLimit(1)>=0)) atLimit=false;
                if (atLimit) break;
                //yield();
            }
            // open valve 1 or 3 (if generator 2)
            if (doUnit1) commSystem.moveMotorValveAndWait(0, 'O',this);
            if (doUnit2) commSystem.moveMotorValveAndWait(2, 'O',this);
            // if system is not safe, open valve 2 to make sure sample chamber is vented
            if (safe==false) {
                if (doUnit1) commSystem.moveMotorValveAndWait(1,'O',this);
                if (doUnit2) commSystem.moveMotorValveAndWait(3,'O',this);
            }
            // if we haven't aborted yet, then sample chamber must be safe
            if (abort==false) safe=true;
            // close valve 2
            if (doUnit1) commSystem.moveMotorValveAndWait(1, 'C',this);
            if (doUnit2) commSystem.moveMotorValveAndWait(3, 'C',this);
            // wait for reverse limit
            while (abort==false) {
                boolean atLimit=true;
                if (doUnit1)
                    if (commSystem.getPGenLimit(0)>=0)
                        atLimit=false;
                if (doUnit2)
                    if (commSystem.getPGenLimit(1)>=0)
                        atLimit=false;
                if (atLimit) break;
                //yield();
            }
            // shut off generator motor, just to be safe
            if (doUnit1) commSystem.setPGenSpeed(0,0);
            if (doUnit2) commSystem.setPGenSpeed(1,0);
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
        java.awt.GridBagConstraints gridBagConstraints;

        jLabel1 = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("System Busy");
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                exitForm(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 24));
        jLabel1.setText("Resetting Piston - Please Wait");
        getContentPane().add(jLabel1, new java.awt.GridBagConstraints());

        jButton1.setText("Abort");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        getContentPane().add(jButton1, gridBagConstraints);

        pack();
    }
    // </editor-fold>//GEN-END:initComponents

    private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
        if (thread.isAlive()) {
            abort=true;
            try { thread.join(); }
            catch (InterruptedException e) {}
        }
    }//GEN-LAST:event_exitForm

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        abort=true;
    }//GEN-LAST:event_jButton1ActionPerformed
    
    /**
     * Find out if the reset was aborted before it fully finished
     * @return true if the reset was aborted without fully finishing
     */
    public boolean wasAborted() {
        return abort;
    }
    
    /**
     * Find out if the reset got far enough to vent the sample area (or was safe from the beginning)
     * @return true if it is safe to open the door
     */
    public boolean isItSafe() {
        return safe;
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    // End of variables declaration//GEN-END:variables
    
}
