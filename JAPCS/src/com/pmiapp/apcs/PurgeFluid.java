/*
 * PurgeFluid.java
 *
 * Created on April 19, 2006, 1:25 PM
 */

package com.pmiapp.apcs;
import com.pmiapp.common.AbortQueriable;
import com.pmiapp.common.GaugeReadListener;
import com.pmiapp.common.Notifiable;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

/**
 * Form that shows when we are purging old fluid from a system (and replacing it with new fluid)
 * @author  Ron V. Webber
 */
public class PurgeFluid extends javax.swing.JFrame implements GaugeReadListener {
    
    /** Creates new form PurgeFluid
     * @param commSystem Already open communication system
     * @param callingForm form that will be Notified when this form is closing
     */
    public PurgeFluid(APCSCommunication commSystem, Notifiable callingForm) {
        setIconImage(new ImageIcon(getClass().getResource("/com/pmiapp/apcs/gifs/apcs16.gif")).getImage());
        initComponents();
        pack();
        this.callingForm=callingForm;
        this.commSystem=commSystem;
        configSettings=commSystem.getConfigSettings();
        // center the form on the screen, so we can get the default location values
        configSettings.setWindowPosition((java.awt.Window)this);
        // set display labels so they don't shrink
        systemPressureDisplay.setPreferredSize(systemPressureDisplay.getSize());
        systemPressureDisplay.setText("");
        numCyclesText.setPreferredSize(numCyclesText.getSize());
        numCyclesText.setText("5");
        maxPressureText.setPreferredSize(maxPressureText.getSize());
        maxPressureText.setText("100");
        gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
        gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
        simulatedPressure=0;
        currentPressure=0;
        runPurgeThread=null;
        demoLabel.setVisible(commSystem.isDemoMode());
    }
    
    /**
     * Start the gauge reading thread
     */
    public void initialize() {
        gaugeReadThread.start();
    }
    
    private com.pmiapp.common.Notifiable callingForm;
    private APCSCommunication commSystem;
    private APCSConfig configSettings;
    private GaugeReadThread gaugeReadThread;
    private double currentPressure, simulatedPressure;
    private int numCycles;
    private double maxPressure;
    private boolean aborted;
    private java.lang.Thread runPurgeThread;

    /**
     * Part of GaugeReadListener interface - called by GaugeReadThread whenever it has a new gauge
     * reading.
     * @param channel gauge channel that was read
     * @param countValue count value of the reading
     * @param tag tag value of the gauge read thread, for future expansion to distinguish between
     * multiple gauge reading threads
     */
    public void gaugeReadData(int channel, int countValue, int tag) {
        switch (channel) {
            //case -1: // limit switch
            //    break;
            case 2: // main pressure gauge
                if (commSystem.isDemoMode()) currentPressure=simulatedPressure;
                else currentPressure=configSettings.getGaugeChannel(2).getUser(configSettings.getGaugeChannel(2).getReal(countValue));
                systemPressureDisplay.setText(configSettings.getGaugeChannel(2).getUserFormattedString(currentPressure));
                break;
        }
    }

    /**
     * Part of GaugeReadListener interface - called by worker threads that also need
     * to know the current pressures to know the current pressure
     * @return last known pressure
     * @param i currently ignored - always returns current pressure of main
     */
    public double getCurrentPressure(int i) {
        return currentPressure;
    }
        
    private class RunPurgeThread extends java.lang.Thread implements AbortQueriable {
        public RunPurgeThread() {        }
        
        /**
        * find out of the testing has been stopped
        * @return true if we should stop
        */
        public boolean shouldStop() {
            return aborted;
        }
        
        @Override
        public void run() {
            // runs the actual purge routine
            // initialize some things
            commSystem.setPGenSpeed(0,0); // just in case
            boolean motorsOn=false;
            // convenience variable
            boolean dualGenerators=(configSettings.getNumberOfGenerators()==2);
            if (dualGenerators) commSystem.setPGenSpeed(1, 0);
            // main loop
            for (int cycleNumber=1; cycleNumber<=numCycles; cycleNumber++) {
                currentCycleLabel.setText("Cycles: "+cycleNumber);
                redDot1.setEnabled(true);
                // close valves 1 and maybe 3
                if (aborted==false) commSystem.moveMotorValveAndWait(0,'C', this);
                if ((dualGenerators) && (aborted==false)) commSystem.moveMotorValveAndWait(2,'C',this);
                redDot1.setEnabled(false);
                if (aborted) break;
                redDot2.setEnabled(true);
                // open valves 2 and maybe 4
                if (aborted==false) commSystem.moveMotorValveAndWait(1,'O',this);
                if ((dualGenerators) && (aborted==false)) commSystem.moveMotorValveAndWait(3,'O',this);
                redDot2.setEnabled(false);
                if (aborted) break;
                redDot3.setEnabled(true);
                // all generators forward
                while (aborted==false) {
                    // check generators
                    if ((motorsOn==true) && (currentPressure > maxPressure)) {
                        motorsOn=false;
                        commSystem.setPGenSpeed(0,0);
                        if (dualGenerators) commSystem.setPGenSpeed(1,0);
                    }
                    if ((motorsOn==false) && (currentPressure < maxPressure)) {
                        motorsOn=true;
                        commSystem.setPGenSpeed(0,1023);
                        if (dualGenerators) commSystem.setPGenSpeed(1,1023);
                    }
                    if (commSystem.getPGenLimit(0)==1) {
                        if (dualGenerators) {
                            if (commSystem.getPGenLimit(1)==1) break;
                        }
                        else break;
                    }
                }
                // shut off the generators, just in case
                if (motorsOn) {
                    motorsOn=false;
                    commSystem.setPGenSpeed(0,0);
                    if (dualGenerators) commSystem.setPGenSpeed(1,0);
                }
                redDot3.setEnabled(false);
                if (aborted) break;
                redDot4.setEnabled(true);
                // close valves 2 and maybe 4
                if (aborted==false) commSystem.moveMotorValveAndWait(1,'C', this);
                if ((dualGenerators) && (aborted==false)) commSystem.moveMotorValveAndWait(3,'C',this);
                redDot4.setEnabled(false);
                if (aborted) break;
                redDot5.setEnabled(true);
                // open valves 1 and maybe 3
                if (aborted==false) commSystem.moveMotorValveAndWait(0,'O',this);
                if ((dualGenerators) && (aborted==false)) commSystem.moveMotorValveAndWait(2,'O',this);
                redDot5.setEnabled(false);
                if (aborted) break;
                redDot6.setEnabled(true);
                // all generators reverse
                commSystem.setPGenSpeed(0,-1023);
                if (dualGenerators) commSystem.setPGenSpeed(1,-1023);
                while (aborted==false) {
                    if (commSystem.getPGenLimit(0)==-1) {
                        if (dualGenerators) {
                            if (commSystem.getPGenLimit(1)==-1) break;
                        }
                        else break;
                    }
                }
                // shut off the generators
                commSystem.setPGenSpeed(0,0);
                if (dualGenerators) commSystem.setPGenSpeed(1,0);
                redDot6.setEnabled(false);
            }
            // all done
            stopButton.setEnabled(false);
            startButton.setEnabled(true);
            maxPressureText.setEnabled(true);
            numCyclesText.setEnabled(true);
        }
    }
    
    private class FakePurgeThread extends java.lang.Thread {
        public FakePurgeThread() { }
        
        /**
        * find out if the testing has been stopped
        * @return true if we should stop
        */
        public boolean shouldStop() {
            return aborted;
        }

        private void delay() {
            // 10 second delay, unless aborted
            // (Things that set aborted=true should also interrupt this thread)
            if (aborted==false) {
                try {
                    sleep(10000);
                } catch (InterruptedException ex) {}
            }
        }
        
        @Override
        public void run() {
            // runs the fake purge routine
            // main loop
            for (int cycleNumber=1; cycleNumber<=numCycles; cycleNumber++) {
                currentCycleLabel.setText("Cycles: "+cycleNumber);
                redDot1.setEnabled(true);
                delay();
                redDot1.setEnabled(false);
                if (aborted) break;
                redDot2.setEnabled(true);
                delay();
                redDot2.setEnabled(false);
                if (aborted) break;
                redDot3.setEnabled(true);
                delay();
                redDot3.setEnabled(false);
                if (aborted) break;
                redDot4.setEnabled(true);
                delay();
                redDot4.setEnabled(false);
                if (aborted) break;
                redDot5.setEnabled(true);
                delay();
                redDot5.setEnabled(false);
                if (aborted) break;
                redDot6.setEnabled(true);
                delay();
                redDot6.setEnabled(false);
            }
            // all done
            stopButton.setEnabled(false);
            startButton.setEnabled(true);
            maxPressureText.setEnabled(true);
            numCyclesText.setEnabled(true);
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

        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        systemPressureDisplay = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        numCyclesText = new javax.swing.JTextField();
        maxPressureText = new javax.swing.JTextField();
        startButton = new javax.swing.JButton();
        stopButton = new javax.swing.JButton();
        currentCycleLabel = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        redDot1 = new javax.swing.JLabel();
        redDot2 = new javax.swing.JLabel();
        redDot3 = new javax.swing.JLabel();
        redDot4 = new javax.swing.JLabel();
        redDot5 = new javax.swing.JLabel();
        redDot6 = new javax.swing.JLabel();
        demoLabel = new javax.swing.JLabel();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Purge Fluid");
        setName("PurgeFluid");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jPanel2.setLayout(new java.awt.GridBagLayout());

        jPanel2.setBorder(new javax.swing.border.TitledBorder(""));
        jLabel3.setText("System Pressure (PSI)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel2.add(jLabel3, gridBagConstraints);

        systemPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        systemPressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12));
        systemPressureDisplay.setForeground(new java.awt.Color(0, 255, 0));
        systemPressureDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        systemPressureDisplay.setText("60,000.00");
        systemPressureDisplay.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0)));
        systemPressureDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel2.add(systemPressureDisplay, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridheight = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.25;
        getContentPane().add(jPanel2, gridBagConstraints);

        jLabel1.setText("# of Cycles");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        getContentPane().add(jLabel1, gridBagConstraints);

        jLabel2.setText("Maximum Pressure (PSI)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        getContentPane().add(jLabel2, gridBagConstraints);

        numCyclesText.setText("99999");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        getContentPane().add(numCyclesText, gridBagConstraints);

        maxPressureText.setText("60000");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        getContentPane().add(maxPressureText, gridBagConstraints);

        startButton.setText("Start");
        startButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        getContentPane().add(startButton, gridBagConstraints);

        stopButton.setText("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        getContentPane().add(stopButton, gridBagConstraints);

        currentCycleLabel.setText("Cycles: 0");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        getContentPane().add(currentCycleLabel, gridBagConstraints);

        jLabel5.setText("1: Closing input valve(s)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        getContentPane().add(jLabel5, gridBagConstraints);

        jLabel6.setText("2: Opening output valve(s)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        getContentPane().add(jLabel6, gridBagConstraints);

        jLabel7.setText("3: Pushing old fluid out");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        getContentPane().add(jLabel7, gridBagConstraints);

        jLabel8.setText("4: Closing output valve(s)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        getContentPane().add(jLabel8, gridBagConstraints);

        jLabel9.setText("5: Opening input valve(s)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        getContentPane().add(jLabel9, gridBagConstraints);

        jLabel10.setText("6: Drawing new fluid in");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        getContentPane().add(jLabel10, gridBagConstraints);

        redDot1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/pmiapp/common/images/reddot.gif")));
        redDot1.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        getContentPane().add(redDot1, gridBagConstraints);

        redDot2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/pmiapp/common/images/reddot.gif")));
        redDot2.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        getContentPane().add(redDot2, gridBagConstraints);

        redDot3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/pmiapp/common/images/reddot.gif")));
        redDot3.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        getContentPane().add(redDot3, gridBagConstraints);

        redDot4.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/pmiapp/common/images/reddot.gif")));
        redDot4.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        getContentPane().add(redDot4, gridBagConstraints);

        redDot5.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/pmiapp/common/images/reddot.gif")));
        redDot5.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        getContentPane().add(redDot5, gridBagConstraints);

        redDot6.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/pmiapp/common/images/reddot.gif")));
        redDot6.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        getContentPane().add(redDot6, gridBagConstraints);

        demoLabel.setFont(new java.awt.Font("SansSerif", 0, 18));
        demoLabel.setForeground(new java.awt.Color(192, 0, 0));
        demoLabel.setText("Demo Mode");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        getContentPane().add(demoLabel, gridBagConstraints);

    }
    // </editor-fold>//GEN-END:initComponents

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
        aborted=true;
        stopButton.setEnabled(false);
        if (runPurgeThread!=null) if (runPurgeThread.isAlive()) {
            runPurgeThread.interrupt(); // stop any sleep that may be active
        }
    }//GEN-LAST:event_stopButtonActionPerformed

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        try {
            numCycles=Integer.parseInt(numCyclesText.getText());
        } catch (NumberFormatException e) { numCycles=-1; }
        if (numCycles<=0) return;
        try {
            maxPressure=Double.parseDouble(maxPressureText.getText());
        } catch (NumberFormatException e) {maxPressure=-1; }
        if (maxPressure < 1) return;
        aborted=false;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        maxPressureText.setEnabled(false);
        numCyclesText.setEnabled(false);
        // start a second thread to actually control what happens during testing
        if (commSystem.isDemoMode()) runPurgeThread = new FakePurgeThread();
        else runPurgeThread = new RunPurgeThread();
        runPurgeThread.start();        
    }//GEN-LAST:event_startButtonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // only call this if the background task is finished
        if (runPurgeThread!=null) if (runPurgeThread.isAlive()) {
            aborted=true;
            runPurgeThread.interrupt(); // stop any sleep that may be active
            try { runPurgeThread.join(); }
            catch (InterruptedException e) {}
        }
        int rv=JOptionPane.showConfirmDialog(this,"Do you want to reset the system before exiting?",
                "APCS Purge Fluid",JOptionPane.YES_NO_CANCEL_OPTION);
        if (rv==JOptionPane.CANCEL_OPTION) {
            // tell the calling form to forget that it had told us to exit
            callingForm.notifyTaskFinished(null,0);
            return;
        }
        else if (rv==JOptionPane.YES_OPTION) {
            ResetSystem rs = new ResetSystem(this, this, commSystem, -1);
            rs.initialize();
            rs.setVisible(true);
            if (rs.wasAborted()) {
                callingForm.notifyTaskFinished(null,0);
                return;
            }
        }
        if (gaugeReadThread.isAlive()) {
            gaugeReadThread.pleaseStop();
            try { gaugeReadThread.join(); }
            catch (InterruptedException e) {}
        }
        // store the current screen location
        configSettings.rememberWindowPosition(this);
        // this will close the communication system (or take control of it for some other purpose)
        // and dispose of this window
        callingForm.notifyTaskFinished(this, 0);
    }//GEN-LAST:event_formWindowClosing
   
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel currentCycleLabel;
    private javax.swing.JLabel demoLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JTextField maxPressureText;
    private javax.swing.JTextField numCyclesText;
    private javax.swing.JLabel redDot1;
    private javax.swing.JLabel redDot2;
    private javax.swing.JLabel redDot3;
    private javax.swing.JLabel redDot4;
    private javax.swing.JLabel redDot5;
    private javax.swing.JLabel redDot6;
    private javax.swing.JButton startButton;
    private javax.swing.JButton stopButton;
    private javax.swing.JLabel systemPressureDisplay;
    // End of variables declaration//GEN-END:variables
    
}
