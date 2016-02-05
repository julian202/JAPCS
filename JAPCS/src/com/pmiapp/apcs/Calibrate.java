/*
 * Calibrate.java
 *
 * Created on September 2, 2005, 9:37 AM
 */

package com.pmiapp.apcs;
import javax.swing.ImageIcon;

/**
 * Shows values while you are calibrating the system
 * @author Ron V. Webber
 */
public class Calibrate extends javax.swing.JFrame implements com.pmiapp.common.GaugeReadListener {
    
    /**
     * Creates new form Calibrate
     * @param commSystem Open communication system to use to talk to machine
     * @param callingForm form to Notify when this form has closed
     */
    public Calibrate(APCSCommunication commSystem, com.pmiapp.common.Notifiable callingForm) {
        setIconImage(new ImageIcon(getClass().getResource("/com/pmiapp/apcs/gifs/apcs16.gif")).getImage());
        initComponents();
        this.callingForm=callingForm;
        this.commSystem=commSystem;
        configSettings=commSystem.getConfigSettings();
        valve3RadioButton1.setVisible(configSettings.getNumberOfMotorValves()>2);
        valve4RadioButton1.setVisible(configSettings.getNumberOfMotorValves()>3);
        if ((configSettings.getNumberOfGenerators()==1) &&
            (configSettings.getSamplePressureGauge()<0)) {
            gauge1RadioButton.setVisible(false);
            gauge2RadioButton.setVisible(false);
        }
        pack();
        // center the form on the screen, so we can get the default location values
        configSettings.setWindowPosition((java.awt.Window)this);
        plus2DisplayLabel1.setPreferredSize(plus2DisplayLabel1.getSize());
        plus2DisplayLabel1.setText("");
        gndDisplayLabel1.setPreferredSize(gndDisplayLabel1.getSize());
        gndDisplayLabel1.setText("");
        gaugePSIDisplayLabel1.setPreferredSize(gaugePSIDisplayLabel1.getSize());
        gaugePSIDisplayLabel1.setText("");
        gaugeCountDisplayLabel1.setPreferredSize(gaugeCountDisplayLabel1.getSize());
        gaugeCountDisplayLabel1.setText("");
        olimitCountLabel1.setPreferredSize(olimitCountLabel1.getSize());
        olimitCountLabel1.setText("");
        climitCountLabel1.setPreferredSize(climitCountLabel1.getSize());
        climitCountLabel1.setText("");
        positionCountLabel1.setPreferredSize(positionCountLabel1.getSize());
        positionCountLabel1.setText("");
    }
    
    /**
     * Start the gauge read thread
     */
    public void initialize() {
        gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
        gaugeReadThread.setGaugeChannel(0,true); // +2 volt
        gaugeReadThread.setGaugeChannel(1,true); // ground
        gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
        gaugeReadThread.setGaugeChannel(10,true); // valve 1 position
        gaugeReadThread.setGaugeChannel(11,true); // valve 1 close limit
        gaugeReadThread.setGaugeChannel(12,true); // valve 1 open limit
        currentValve=0; // valve 1
        currentGauge=0; // main pressure gauge
        gaugeReadThread.start();
    }
    
    private GaugeReadThread gaugeReadThread;
    private com.pmiapp.common.Notifiable callingForm;
    private APCSCommunication commSystem;
    private APCSConfig configSettings;
    private int currentValve, currentGauge;
    private double currentPressure, currentPressure2;

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
            case 0: // +2 volt reference
                plus2DisplayLabel1.setText(""+countValue);
                break;
            case 1: // ground reference
                gndDisplayLabel1.setText(""+countValue);
                break;
            case 2: // main pressure gauge
                currentPressure=configSettings.getGaugeChannel(2).getUser(configSettings.getGaugeChannel(2).getReal(countValue));
                // this allows us to receive pressure readings from both gauges while only displaying one of them
                if (currentGauge==0) {
                    gaugeCountDisplayLabel1.setText(""+countValue);
                    gaugePSIDisplayLabel1.setText(configSettings.getGaugeChannel(2).getUserFormattedString(currentPressure));
                }
                break;
            case 3: // second pressure gauge - uses same display
                currentPressure2=configSettings.getGaugeChannel(3).getUser(configSettings.getGaugeChannel(3).getReal(countValue));
                // this allows us to receive pressure readings from both gauges while only displaying one of them
                if (currentGauge==1) {
                    gaugeCountDisplayLabel1.setText(""+countValue);
                    gaugePSIDisplayLabel1.setText(configSettings.getGaugeChannel(3).getUserFormattedString(currentPressure2));
                }
                break;
            case 10: // valve 1 position
            case 13: // valve 2 position
            case 16: // valve 3 position
            case 19: // valve 4 position
                positionCountLabel1.setText(""+countValue);
                break;
            case 11: // valve 1 close limit
            case 14: // valve 2 close limit
            case 17: // valve 3 close limit
            case 20: // valve 4 close limit
                climitCountLabel1.setText(""+countValue);
                break;
            case 12: // valve 1 open limit
            case 15: // valve 2 open limit
            case 18: // valve 3 open limit
            case 21: // valve 4 open limit
                olimitCountLabel1.setText(""+countValue);
                break;
        }
    }
    
    /**
     * Part of GaugeReadListener interface - called by worker threads that also need
     * to know the current pressures to know the current pressure
     * @return last known pressure
     * @param i 0 for current pressure of main section, 1 for current pressure
     * of secondary section.
     */
    public double getCurrentPressure(int i) {
        if (i==1) return currentPressure2;
        return currentPressure;
    }
    
    private void handleValveSwitch(int newValve) {
        if (newValve==currentValve) return;
        // stop the current valve, just in case
        commSystem.controlMotor(currentValve, 'S');
        // clear out all previous valve selections
        gaugeReadThread.setGaugeChannel(currentValve*3+10,false); // position
        gaugeReadThread.setGaugeChannel(currentValve*3+11,false); // close limit
        gaugeReadThread.setGaugeChannel(currentValve*3+12,false); // open limit
        // blank the lines on the screen to get rid of old values
        positionCountLabel1.setText("");
        olimitCountLabel1.setText("");
        climitCountLabel1.setText("");
        // add back in the ones for this valve
        gaugeReadThread.setGaugeChannel(newValve*3+10,true);
        gaugeReadThread.setGaugeChannel(newValve*3+11,true);
        gaugeReadThread.setGaugeChannel(newValve*3+12,true);
        currentValve=newValve;
    }
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        valveButtonGroup1 = new javax.swing.ButtonGroup();
        gaugeButtonGroup = new javax.swing.ButtonGroup();
        jPanel5 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        gndLabel1 = new javax.swing.JLabel();
        plus2Label1 = new javax.swing.JLabel();
        gndDisplayLabel1 = new javax.swing.JLabel();
        plus2DisplayLabel1 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        psiLabel1 = new javax.swing.JLabel();
        countsLabel1 = new javax.swing.JLabel();
        gaugePSIDisplayLabel1 = new javax.swing.JLabel();
        gaugeCountDisplayLabel1 = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        gauge1RadioButton = new javax.swing.JRadioButton();
        gauge2RadioButton = new javax.swing.JRadioButton();
        jPanel3 = new javax.swing.JPanel();
        olimitLabel1 = new javax.swing.JLabel();
        positionLabel1 = new javax.swing.JLabel();
        olimitCountLabel1 = new javax.swing.JLabel();
        positionCountLabel1 = new javax.swing.JLabel();
        climitLabel1 = new javax.swing.JLabel();
        climitCountLabel1 = new javax.swing.JLabel();
        valve1RadioButton1 = new javax.swing.JRadioButton();
        valve2RadioButton1 = new javax.swing.JRadioButton();
        valve3RadioButton1 = new javax.swing.JRadioButton();
        valve4RadioButton1 = new javax.swing.JRadioButton();
        jPanel4 = new javax.swing.JPanel();
        openValveButton1 = new javax.swing.JButton();
        stopValveButton1 = new javax.swing.JButton();
        closeValveButton1 = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        calibMenuItem = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();
        jMenuItem3 = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("System Calibration");
        setName("Calibrate");
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                exitForm(evt);
            }
        });

        jPanel5.setLayout(new java.awt.GridBagLayout());

        jPanel5.setBackground(new java.awt.Color(0, 128, 128));
        jPanel1.setLayout(new java.awt.GridBagLayout());

        jPanel1.setBorder(new javax.swing.border.TitledBorder("Reference"));
        gndLabel1.setText("Ground");
        jPanel1.add(gndLabel1, new java.awt.GridBagConstraints());

        plus2Label1.setText("2V");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        jPanel1.add(plus2Label1, gridBagConstraints);

        gndDisplayLabel1.setBackground(new java.awt.Color(0, 0, 0));
        gndDisplayLabel1.setFont(new java.awt.Font("Monospaced", 1, 12));
        gndDisplayLabel1.setForeground(new java.awt.Color(0, 255, 0));
        gndDisplayLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        gndDisplayLabel1.setText("1023.00");
        gndDisplayLabel1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 2));
        gndDisplayLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(7, 7, 2, 7);
        jPanel1.add(gndDisplayLabel1, gridBagConstraints);

        plus2DisplayLabel1.setBackground(new java.awt.Color(0, 0, 0));
        plus2DisplayLabel1.setFont(new java.awt.Font("Monospaced", 1, 12));
        plus2DisplayLabel1.setForeground(new java.awt.Color(0, 255, 0));
        plus2DisplayLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        plus2DisplayLabel1.setText("1023.00");
        plus2DisplayLabel1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 2));
        plus2DisplayLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(7, 7, 2, 7);
        jPanel1.add(plus2DisplayLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 0, 4);
        jPanel5.add(jPanel1, gridBagConstraints);

        jPanel2.setLayout(new java.awt.GridBagLayout());

        jPanel2.setBorder(new javax.swing.border.TitledBorder("Gauges"));
        psiLabel1.setText("PSI");
        jPanel2.add(psiLabel1, new java.awt.GridBagConstraints());

        countsLabel1.setText("Counts");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        jPanel2.add(countsLabel1, gridBagConstraints);

        gaugePSIDisplayLabel1.setBackground(new java.awt.Color(0, 0, 0));
        gaugePSIDisplayLabel1.setFont(new java.awt.Font("Monospaced", 1, 12));
        gaugePSIDisplayLabel1.setForeground(new java.awt.Color(0, 255, 0));
        gaugePSIDisplayLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        gaugePSIDisplayLabel1.setText("60,000.000");
        gaugePSIDisplayLabel1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 2));
        gaugePSIDisplayLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(7, 0, 2, 7);
        jPanel2.add(gaugePSIDisplayLabel1, gridBagConstraints);

        gaugeCountDisplayLabel1.setBackground(new java.awt.Color(0, 0, 0));
        gaugeCountDisplayLabel1.setFont(new java.awt.Font("Monospaced", 1, 12));
        gaugeCountDisplayLabel1.setForeground(new java.awt.Color(0, 255, 0));
        gaugeCountDisplayLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        gaugeCountDisplayLabel1.setText("1023.00");
        gaugeCountDisplayLabel1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 2));
        gaugeCountDisplayLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(7, 7, 2, 7);
        jPanel2.add(gaugeCountDisplayLabel1, gridBagConstraints);

        jButton1.setText("+");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        jPanel2.add(jButton1, gridBagConstraints);

        jButton2.setText("-");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        jPanel2.add(jButton2, gridBagConstraints);

        gaugeButtonGroup.add(gauge1RadioButton);
        gauge1RadioButton.setSelected(true);
        gauge1RadioButton.setText("Gauge1");
        gauge1RadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gauge1RadioButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        jPanel2.add(gauge1RadioButton, gridBagConstraints);

        gaugeButtonGroup.add(gauge2RadioButton);
        gauge2RadioButton.setText("Gauge2");
        gauge2RadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gauge2RadioButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        jPanel2.add(gauge2RadioButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        jPanel5.add(jPanel2, gridBagConstraints);

        jPanel3.setLayout(new java.awt.GridBagLayout());

        jPanel3.setBorder(new javax.swing.border.TitledBorder("Valves"));
        olimitLabel1.setText("Open Limit");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        jPanel3.add(olimitLabel1, gridBagConstraints);

        positionLabel1.setText("Position");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        jPanel3.add(positionLabel1, gridBagConstraints);

        olimitCountLabel1.setBackground(new java.awt.Color(0, 0, 0));
        olimitCountLabel1.setFont(new java.awt.Font("Monospaced", 1, 12));
        olimitCountLabel1.setForeground(new java.awt.Color(0, 255, 0));
        olimitCountLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        olimitCountLabel1.setText("1023.00");
        olimitCountLabel1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 2));
        olimitCountLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(7, 7, 2, 7);
        jPanel3.add(olimitCountLabel1, gridBagConstraints);

        positionCountLabel1.setBackground(new java.awt.Color(0, 0, 0));
        positionCountLabel1.setFont(new java.awt.Font("Monospaced", 1, 12));
        positionCountLabel1.setForeground(new java.awt.Color(0, 255, 0));
        positionCountLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        positionCountLabel1.setText("1023.00");
        positionCountLabel1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 2));
        positionCountLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(7, 7, 2, 7);
        jPanel3.add(positionCountLabel1, gridBagConstraints);

        climitLabel1.setText("Close Limit");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        jPanel3.add(climitLabel1, gridBagConstraints);

        climitCountLabel1.setBackground(new java.awt.Color(0, 0, 0));
        climitCountLabel1.setFont(new java.awt.Font("Monospaced", 1, 12));
        climitCountLabel1.setForeground(new java.awt.Color(0, 255, 0));
        climitCountLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        climitCountLabel1.setText("1023.00");
        climitCountLabel1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 2));
        climitCountLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(7, 7, 2, 7);
        jPanel3.add(climitCountLabel1, gridBagConstraints);

        valveButtonGroup1.add(valve1RadioButton1);
        valve1RadioButton1.setSelected(true);
        valve1RadioButton1.setText("Valve 1");
        valve1RadioButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                valve1RadioButton1ActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        jPanel3.add(valve1RadioButton1, gridBagConstraints);

        valveButtonGroup1.add(valve2RadioButton1);
        valve2RadioButton1.setText("Valve 2");
        valve2RadioButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                valve2RadioButton1ActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        jPanel3.add(valve2RadioButton1, gridBagConstraints);

        valveButtonGroup1.add(valve3RadioButton1);
        valve3RadioButton1.setText("Valve 3");
        valve3RadioButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                valve3RadioButton1ActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        jPanel3.add(valve3RadioButton1, gridBagConstraints);

        valveButtonGroup1.add(valve4RadioButton1);
        valve4RadioButton1.setText("Valve 4");
        valve4RadioButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                valve4RadioButton1ActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        jPanel3.add(valve4RadioButton1, gridBagConstraints);

        openValveButton1.setText("Open");
        openValveButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openValveButton1ActionPerformed(evt);
            }
        });

        jPanel4.add(openValveButton1);

        stopValveButton1.setText("Stop");
        stopValveButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopValveButton1ActionPerformed(evt);
            }
        });

        jPanel4.add(stopValveButton1);

        closeValveButton1.setText("Close");
        closeValveButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeValveButton1ActionPerformed(evt);
            }
        });

        jPanel4.add(closeValveButton1);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        jPanel3.add(jPanel4, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 0, 0, 4);
        jPanel5.add(jPanel3, gridBagConstraints);

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 11));
        jLabel1.setText("Note: Auto Calibration of the Pressure Gauge should be done");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 0, 0, 0);
        jPanel5.add(jLabel1, gridBagConstraints);

        jLabel2.setFont(new java.awt.Font("Tahoma", 1, 11));
        jLabel2.setText("with the system vented to atmospheric pressure.");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 4, 0);
        jPanel5.add(jLabel2, gridBagConstraints);

        getContentPane().add(jPanel5, java.awt.BorderLayout.CENTER);

        jMenu1.setText("Main");
        jMenuItem1.setText("Leak Test");
        jMenuItem1.setEnabled(false);
        jMenu1.add(jMenuItem1);

        calibMenuItem.setText("Auto-Calibrate Pressure Gauges");
        calibMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                calibMenuItemActionPerformed(evt);
            }
        });

        jMenu1.add(calibMenuItem);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("Exit");
        jMenuItem3.setText("Exit Calibrate");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem3ActionPerformed(evt);
            }
        });

        jMenu2.add(jMenuItem3);

        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

    }
    // </editor-fold>//GEN-END:initComponents

    private void gauge2RadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gauge2RadioButtonActionPerformed
        gaugeReadThread.setGaugeChannel(2,false); // main pressure gauge
        gaugeCountDisplayLabel1.setText("");
        gaugePSIDisplayLabel1.setText("");
        currentGauge=1;
        gaugeReadThread.setGaugeChannel(3,true); // second pressure gauge       
    }//GEN-LAST:event_gauge2RadioButtonActionPerformed

    private void gauge1RadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gauge1RadioButtonActionPerformed
        gaugeReadThread.setGaugeChannel(3,false); // second pressure gauge
        gaugeCountDisplayLabel1.setText("");
        gaugePSIDisplayLabel1.setText("");
        currentGauge=0;
        gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge       
    }//GEN-LAST:event_gauge1RadioButtonActionPerformed

    private void calibMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_calibMenuItemActionPerformed
        // first, reset the piston
        // if they abort this, we can't do the calibration
        ResetSystem rs = new ResetSystem(this, this, commSystem, currentGauge);
        rs.initialize();
        rs.setVisible(true);
        if (rs.wasAborted()) return;
        CalibratePressureGauge cpg = new CalibratePressureGauge(this, commSystem, currentGauge);
        cpg.initialize(true);
        cpg.setVisible(true);
    }//GEN-LAST:event_calibMenuItemActionPerformed

    private void closeValveButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeValveButton1ActionPerformed
        commSystem.controlMotor(currentValve,'C');
    }//GEN-LAST:event_closeValveButton1ActionPerformed

    private void stopValveButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopValveButton1ActionPerformed
        commSystem.controlMotor(currentValve,'S');
    }//GEN-LAST:event_stopValveButton1ActionPerformed

    private void openValveButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openValveButton1ActionPerformed
        commSystem.controlMotor(currentValve,'O');
    }//GEN-LAST:event_openValveButton1ActionPerformed

    private void valve4RadioButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_valve4RadioButton1ActionPerformed
        handleValveSwitch(3);
    }//GEN-LAST:event_valve4RadioButton1ActionPerformed

    private void valve3RadioButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_valve3RadioButton1ActionPerformed
        handleValveSwitch(2);
    }//GEN-LAST:event_valve3RadioButton1ActionPerformed

    private void valve2RadioButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_valve2RadioButton1ActionPerformed
        handleValveSwitch(1);
    }//GEN-LAST:event_valve2RadioButton1ActionPerformed

    private void valve1RadioButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_valve1RadioButton1ActionPerformed
        handleValveSwitch(0);
    }//GEN-LAST:event_valve1RadioButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        // - command
        commSystem.setRelay(false);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        // + command
        commSystem.setRelay(true);
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jMenuItem3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem3ActionPerformed
        exitForm(null);
    }//GEN-LAST:event_jMenuItem3ActionPerformed

    private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
        // stop the current valve, just in case
        commSystem.controlMotor(currentValve, 'S');
        // - command, just in case
        commSystem.setRelay(false);
        // only call this if the background task is finished
        if (gaugeReadThread.isAlive()) {
            //for (int i=-1; i<58; i++) gaugeReadThread.setGaugeChannel(i,false);
            gaugeReadThread.pleaseStop();
            try { gaugeReadThread.join(); }
            catch (InterruptedException e) {}
        }
        // store the current screen location
        configSettings.rememberWindowPosition(this);
        // this will close the communication system (or take control of it for some other purpose)
        // and dispose of this window
        callingForm.notifyTaskFinished(this, 0);
    }//GEN-LAST:event_exitForm

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem calibMenuItem;
    private javax.swing.JLabel climitCountLabel1;
    private javax.swing.JLabel climitLabel1;
    private javax.swing.JButton closeValveButton1;
    private javax.swing.JLabel countsLabel1;
    private javax.swing.JRadioButton gauge1RadioButton;
    private javax.swing.JRadioButton gauge2RadioButton;
    private javax.swing.ButtonGroup gaugeButtonGroup;
    private javax.swing.JLabel gaugeCountDisplayLabel1;
    private javax.swing.JLabel gaugePSIDisplayLabel1;
    private javax.swing.JLabel gndDisplayLabel1;
    private javax.swing.JLabel gndLabel1;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JLabel olimitCountLabel1;
    private javax.swing.JLabel olimitLabel1;
    private javax.swing.JButton openValveButton1;
    private javax.swing.JLabel plus2DisplayLabel1;
    private javax.swing.JLabel plus2Label1;
    private javax.swing.JLabel positionCountLabel1;
    private javax.swing.JLabel positionLabel1;
    private javax.swing.JLabel psiLabel1;
    private javax.swing.JButton stopValveButton1;
    private javax.swing.JRadioButton valve1RadioButton1;
    private javax.swing.JRadioButton valve2RadioButton1;
    private javax.swing.JRadioButton valve3RadioButton1;
    private javax.swing.JRadioButton valve4RadioButton1;
    private javax.swing.ButtonGroup valveButtonGroup1;
    // End of variables declaration//GEN-END:variables
    
}
