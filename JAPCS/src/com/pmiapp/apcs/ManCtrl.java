/*
 * ManCtrl.java
 *
 * Created on August 31, 2005, 3:43 PM
 */

package com.pmiapp.apcs;
import com.pmiapp.common.GaugeChannel;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

/**
 * Manual control form
 * @author Ron V. Webber
 */
public class ManCtrl extends javax.swing.JFrame implements com.pmiapp.common.GaugeReadListener {
    
    /**
     * Creates new instance of manual control
     * @param commSystem open communication system
     * @param callingForm form to be Notified when this form is closed
     */
    public ManCtrl(APCSCommunication commSystem, com.pmiapp.common.Notifiable callingForm) {
        setIconImage(new ImageIcon(getClass().getResource("/com/pmiapp/apcs/gifs/apcs16.gif")).getImage());
        initComponents();
        this.callingForm=callingForm;
        this.commSystem=commSystem;
        configSettings=commSystem.getConfigSettings();
        demoLabel.setVisible(commSystem.isDemoMode());
        encoderExists=configSettings.doesEncoderExist();
        encoderLabel1.setVisible(encoderExists);
        zeroEncoderButton1.setVisible(encoderExists);
        encoderFactor = configSettings.getEncoderFactor();
        athena = configSettings.hasAthena();
        unlockButton.setVisible(configSettings.doesDoorLockExist());
        doorSwitchCheckBox.setVisible(configSettings.doesDoorLockExist());
        motorPanel2.setVisible(configSettings.getNumberOfGenerators()>1);
        pumpPanel1.setVisible(configSettings.getFastPumpAnalogOutput()>=0);
        valvePanel3.setVisible(configSettings.getNumberOfMotorValves()>2);
        valvePanel4.setVisible(configSettings.getNumberOfMotorValves()>3);
        vacuumPanel.setVisible(configSettings.getVacuumMotorValve()>=0);
        isolationPanel.setVisible(configSettings.getSampleIsolationValve()>=0);
        gaugePanel2.setVisible(
                (configSettings.getNumberOfGenerators()>1) ||
                (configSettings.getSamplePressureGauge()>=0));
        
        if(configSettings.hasAthena())
        {
            
        }
        if (configSettings.getNumberOfChambers()>1) {
            if (configSettings.getCurrentChamberNumber()==1) {
                chamberRadioButton1.setSelected(true);
            }
            else {
                chamberRadioButton2.setSelected(true);
            }
        }
        else {
            chambersPanel.setVisible(false);
        }
        pack();
        // center the form on the screen, so we can get the default location values
        configSettings.setWindowPosition((java.awt.Window)this);
        motorRunning=false;
        pumpRunning=false;
        gaugeLabel1.setPreferredSize(gaugeLabel1.getSize());
        gaugeLabel1.setText("");
        gaugeLabel2.setPreferredSize(gaugeLabel2.getSize());
        gaugeLabel2.setText("");
        motorSpeedLabel1.setPreferredSize(motorSpeedLabel1.getSize());
        motorSpeedLabel1.setText("0");
        motorSpeedLabel2.setPreferredSize(motorSpeedLabel2.getSize());
        motorSpeedLabel2.setText("0");
        motorStatusLabel1.setPreferredSize(motorStatusLabel1.getSize());
        motorStatusLabel1.setText("");
        motorStatusLabel2.setPreferredSize(motorStatusLabel2.getSize());
        motorStatusLabel2.setText("");
        pumpSpeedLabel1.setPreferredSize(pumpSpeedLabel1.getSize());
        pumpSpeedLabel1.setText("0");
        valveLabel1.setPreferredSize(valveLabel1.getSize());
        valveLabel1.setText("");
        valveLabel2.setPreferredSize(valveLabel2.getSize());
        valveLabel2.setText("");
        valveLabel3.setPreferredSize(valveLabel3.getSize());
        valveLabel3.setText("");
        valveLabel4.setPreferredSize(valveLabel4.getSize());
        valveLabel4.setText("");
        setupGaugeReadThread();
        if (commSystem.isDemoMode()) {
            v1cl=2000;
            v2cl=2000;
            v3cl=2000;
            v4cl=2000;
            v1ol=62000;
            v2ol=62000;
            v3ol=62000;
            v4ol=62000;
        }
        else {
            v1cl=commSystem.rawReading(11);
            if (v1cl==-1) {
                JOptionPane.showMessageDialog((java.awt.Component)this,"Error in communication - all readings are disabled");
                return;
            }
            // we can fairly safely assume that if we got a valid reading for v1cl we can
            // get valid readings for the other channels
            v1ol=commSystem.rawReading(12);
            v2cl=commSystem.rawReading(14);
            v2ol=commSystem.rawReading(15);
            if (configSettings.getNumberOfMotorValves()>2) {
                v3cl=commSystem.rawReading(17);
                v3ol=commSystem.rawReading(18);
            }
            else {
                v3cl=2000;
                v3ol=62000; // just in case
            }
            if (configSettings.getNumberOfMotorValves()>3) {
                v4cl=commSystem.rawReading(20);
                v4ol=commSystem.rawReading(21);
            }
            else {
                v4cl=2000;
                v4ol=62000; // just in case
            }
        }
        commSystem.setPGenSpeed(0,0);
        currentSpeed=0;
        if (configSettings.getNumberOfGenerators()>1) {
            commSystem.setPGenSpeed(1,0);
        }
        currentSpeed2=0;
    }
    
    private void setupGaugeReadThread() {
        gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
        gaugeReadThread.setGaugeChannel(-1,true); // limit switch 1
        gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
        
        if (configSettings.getNumberOfGenerators()>1) {
            gaugeReadThread.setGaugeChannel(3,true); // pressure gauge 2
            gaugeReadThread.setGaugeChannel(-3,true); // limit switch 2
        }
        if (configSettings.getSamplePressureGauge()>=0) {
            gaugeReadThread.setGaugeChannel(configSettings.getSamplePressureGauge(),true);
        }
        gaugeReadThread.setGaugeChannel(10,true); // valve 1 position
        gaugeReadThread.setGaugeChannel(13,true); // valve 2 position
        if (configSettings.getNumberOfMotorValves()>2) {
            gaugeReadThread.setGaugeChannel(16,true);
        } // valve 3 position
        if (configSettings.getNumberOfMotorValves()>3) {
            gaugeReadThread.setGaugeChannel(19,true);
        } // valve 4 position
        if (encoderExists) {
            gaugeReadThread.setGaugeChannel(-4,true);
            //gaugeReadThread.setGaugeChannel(3,true);
        } // encoder        
//        if(athena)
//        {
//            gaugeReadThread.setGaugeChannel(20,true);
//        }
    }
    
    /**
     * Start timer and gauge readings
     */
    public void initialize() {
        // this turns on the communication status display
        SwingTask swingTask1 = new SwingTask();
        swingTimer = new javax.swing.Timer(1000, swingTask1);
        swingTimer.setInitialDelay(0);
        swingTimer.start();
        // this turns on the gauge reader
        gaugeReadThread.start();
    }
    
    private int v1cl, v2cl, v3cl, v4cl;
    private int v1ol, v2ol, v3ol, v4ol;
    private GaugeReadThread gaugeReadThread;
    private com.pmiapp.common.Notifiable callingForm;
    private APCSCommunication commSystem;
    private APCSConfig configSettings;
    private boolean motorRunning, pumpRunning, motorRunning2;
    private boolean encoderExists;
    private double encoderFactor;
    private boolean athena;
    //private SwingTask swingTask1;
    private javax.swing.Timer swingTimer;
    private double currentPressure, currentPressure2;
    private int currentSpeed, currentSpeed2;
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        buttonGroup1 = new javax.swing.ButtonGroup();
        valvePanel1 = new javax.swing.JPanel();
        valveLabel1 = new javax.swing.JLabel();
        closeButton1 = new javax.swing.JButton();
        stopButton1 = new javax.swing.JButton();
        openButton1 = new javax.swing.JButton();
        valvePanel2 = new javax.swing.JPanel();
        valveLabel2 = new javax.swing.JLabel();
        closeButton2 = new javax.swing.JButton();
        stopButton2 = new javax.swing.JButton();
        openButton2 = new javax.swing.JButton();
        valvePanel3 = new javax.swing.JPanel();
        valveLabel3 = new javax.swing.JLabel();
        closeButton3 = new javax.swing.JButton();
        stopButton3 = new javax.swing.JButton();
        openButton3 = new javax.swing.JButton();
        valvePanel4 = new javax.swing.JPanel();
        valveLabel4 = new javax.swing.JLabel();
        closeButton4 = new javax.swing.JButton();
        stopButton4 = new javax.swing.JButton();
        openButton4 = new javax.swing.JButton();
        titleLabel = new javax.swing.JLabel();
        demoLabel = new javax.swing.JLabel();
        statusLabel = new javax.swing.JLabel();
        isolationPanel = new javax.swing.JPanel();
        isolationOpenButton = new javax.swing.JButton();
        isolationCloseButton = new javax.swing.JButton();
        specialButton = new javax.swing.JButton();
        vacuumPanel = new javax.swing.JPanel();
        vacOnButton = new javax.swing.JButton();
        vacOffButton = new javax.swing.JButton();
        unlockButton = new javax.swing.JButton();
        doorSwitchCheckBox = new javax.swing.JCheckBox();
        gaugesPanel = new javax.swing.JPanel();
        gaugePanel1 = new javax.swing.JPanel();
        gaugeLabel1 = new javax.swing.JLabel();
        gaugePanel2 = new javax.swing.JPanel();
        gaugeLabel2 = new javax.swing.JLabel();
        generatorsPanel = new javax.swing.JPanel();
        motorPanel1 = new javax.swing.JPanel();
        motorSpeedLabel1 = new javax.swing.JLabel();
        motorScroll1 = new javax.swing.JScrollBar();
        motorStatusLabel1 = new javax.swing.JLabel();
        motorRunButton1 = new javax.swing.JButton();
        motorStopButton1 = new javax.swing.JButton();
        encoderLabel1 = new javax.swing.JLabel();
        zeroEncoderButton1 = new javax.swing.JButton();
        motorPanel2 = new javax.swing.JPanel();
        motorSpeedLabel2 = new javax.swing.JLabel();
        motorScroll2 = new javax.swing.JScrollBar();
        motorStatusLabel2 = new javax.swing.JLabel();
        motorRunButton2 = new javax.swing.JButton();
        motorStopButton2 = new javax.swing.JButton();
        pumpPanel1 = new javax.swing.JPanel();
        pumpSpeedLabel1 = new javax.swing.JLabel();
        pumpBoostLabel1 = new javax.swing.JLabel();
        pumpScroll1 = new javax.swing.JScrollBar();
        pump0Label1 = new javax.swing.JLabel();
        pump4000label1 = new javax.swing.JLabel();
        pumpRunButton1 = new javax.swing.JButton();
        pumpStopButton1 = new javax.swing.JButton();
        chambersPanel = new javax.swing.JPanel();
        chamberRadioButton1 = new javax.swing.JRadioButton();
        chamberRadioButton2 = new javax.swing.JRadioButton();
        jPanel1 = new javax.swing.JPanel();
        valveLabel5 = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jTextField1 = new javax.swing.JTextField();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();
        jMenuItem4 = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();
        jMenuItem3 = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("APCS Manual Control");
        setName("ManCtrl"); // NOI18N
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                exitForm(evt);
            }
        });
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        valvePanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Valve 1"));
        valvePanel1.setLayout(new java.awt.GridBagLayout());

        valveLabel1.setBackground(new java.awt.Color(0, 0, 0));
        valveLabel1.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        valveLabel1.setForeground(new java.awt.Color(0, 255, 0));
        valveLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        valveLabel1.setText("100.0%");
        valveLabel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        valveLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 7, 7, 7);
        valvePanel1.add(valveLabel1, gridBagConstraints);

        closeButton1.setText("Close");
        closeButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButton1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 2, 2);
        valvePanel1.add(closeButton1, gridBagConstraints);

        stopButton1.setText("Stop");
        stopButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButton1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        valvePanel1.add(stopButton1, gridBagConstraints);

        openButton1.setText("Open");
        openButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButton1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 2);
        valvePanel1.add(openButton1, gridBagConstraints);

        getContentPane().add(valvePanel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(260, 90, -1, -1));

        valvePanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Valve 2"));
        valvePanel2.setLayout(new java.awt.GridBagLayout());

        valveLabel2.setBackground(new java.awt.Color(0, 0, 0));
        valveLabel2.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        valveLabel2.setForeground(new java.awt.Color(0, 255, 0));
        valveLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        valveLabel2.setText("100.0%");
        valveLabel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        valveLabel2.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 7, 7, 7);
        valvePanel2.add(valveLabel2, gridBagConstraints);

        closeButton2.setText("Close");
        closeButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButton2ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 2, 2);
        valvePanel2.add(closeButton2, gridBagConstraints);

        stopButton2.setText("Stop");
        stopButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButton2ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        valvePanel2.add(stopButton2, gridBagConstraints);

        openButton2.setText("Open");
        openButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButton2ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 2);
        valvePanel2.add(openButton2, gridBagConstraints);

        getContentPane().add(valvePanel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(330, 90, -1, -1));

        valvePanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Valve 3"));
        valvePanel3.setLayout(new java.awt.GridBagLayout());

        valveLabel3.setBackground(new java.awt.Color(0, 0, 0));
        valveLabel3.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        valveLabel3.setForeground(new java.awt.Color(0, 255, 0));
        valveLabel3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        valveLabel3.setText("100.0%");
        valveLabel3.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        valveLabel3.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 7, 7, 7);
        valvePanel3.add(valveLabel3, gridBagConstraints);

        closeButton3.setText("Close");
        closeButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButton3ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 2, 2);
        valvePanel3.add(closeButton3, gridBagConstraints);

        stopButton3.setText("Stop");
        stopButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButton3ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        valvePanel3.add(stopButton3, gridBagConstraints);

        openButton3.setText("Open");
        openButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButton3ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 2);
        valvePanel3.add(openButton3, gridBagConstraints);

        getContentPane().add(valvePanel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(260, 210, -1, -1));

        valvePanel4.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Valve 4"));
        valvePanel4.setLayout(new java.awt.GridBagLayout());

        valveLabel4.setBackground(new java.awt.Color(0, 0, 0));
        valveLabel4.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        valveLabel4.setForeground(new java.awt.Color(0, 255, 0));
        valveLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        valveLabel4.setText("100.0%");
        valveLabel4.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        valveLabel4.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 7, 7, 7);
        valvePanel4.add(valveLabel4, gridBagConstraints);

        closeButton4.setText("Close");
        closeButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButton4ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 2, 2);
        valvePanel4.add(closeButton4, gridBagConstraints);

        stopButton4.setText("Stop");
        stopButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButton4ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        valvePanel4.add(stopButton4, gridBagConstraints);

        openButton4.setText("Open");
        openButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButton4ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 2);
        valvePanel4.add(openButton4, gridBagConstraints);

        getContentPane().add(valvePanel4, new org.netbeans.lib.awtextra.AbsoluteConstraints(330, 210, -1, -1));

        titleLabel.setFont(new java.awt.Font("Serif", 1, 18)); // NOI18N
        titleLabel.setText("   Manual Control Mode");
        getContentPane().add(titleLabel, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 10, -1, -1));

        demoLabel.setFont(new java.awt.Font("Serif", 1, 12)); // NOI18N
        demoLabel.setForeground(java.awt.Color.red);
        demoLabel.setText("Demo Mode");
        getContentPane().add(demoLabel, new org.netbeans.lib.awtextra.AbsoluteConstraints(300, 10, -1, -1));

        statusLabel.setText("0:0");
        getContentPane().add(statusLabel, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 430, -1, -1));

        isolationPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Isolation"));

        isolationOpenButton.setText("Open");
        isolationOpenButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isolationOpenButtonActionPerformed(evt);
            }
        });
        isolationPanel.add(isolationOpenButton);

        isolationCloseButton.setText("Close");
        isolationCloseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isolationCloseButtonActionPerformed(evt);
            }
        });
        isolationPanel.add(isolationCloseButton);

        specialButton.setText("Special");
        specialButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                specialButtonActionPerformed(evt);
            }
        });
        isolationPanel.add(specialButton);

        getContentPane().add(isolationPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 370, -1, -1));

        vacuumPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Vacuum"));

        vacOnButton.setText("On");
        vacOnButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                vacOnButtonActionPerformed(evt);
            }
        });
        vacuumPanel.add(vacOnButton);

        vacOffButton.setText("Off");
        vacOffButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                vacOffButtonActionPerformed(evt);
            }
        });
        vacuumPanel.add(vacOffButton);

        getContentPane().add(vacuumPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 430, -1, -1));

        unlockButton.setText("Unlock Door");
        unlockButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unlockButtonActionPerformed(evt);
            }
        });
        getContentPane().add(unlockButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 430, -1, -1));

        doorSwitchCheckBox.setText(" ");
        doorSwitchCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doorSwitchCheckBoxActionPerformed(evt);
            }
        });
        getContentPane().add(doorSwitchCheckBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(160, 430, -1, -1));

        gaugesPanel.setLayout(new java.awt.GridBagLayout());

        gaugePanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "P Gauge 1 (PSI)"));
        gaugePanel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 20, 5));

        gaugeLabel1.setBackground(new java.awt.Color(0, 0, 0));
        gaugeLabel1.setFont(new java.awt.Font("Monospaced", 1, 14)); // NOI18N
        gaugeLabel1.setForeground(new java.awt.Color(0, 255, 0));
        gaugeLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        gaugeLabel1.setText("60,000.000");
        gaugeLabel1.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        gaugeLabel1.setOpaque(true);
        gaugePanel1.add(gaugeLabel1);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gaugesPanel.add(gaugePanel1, gridBagConstraints);

        gaugePanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "P Gauge 2 (PSI)"));
        gaugePanel2.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 20, 5));

        gaugeLabel2.setBackground(new java.awt.Color(0, 0, 0));
        gaugeLabel2.setFont(new java.awt.Font("Monospaced", 1, 14)); // NOI18N
        gaugeLabel2.setForeground(new java.awt.Color(0, 255, 0));
        gaugeLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        gaugeLabel2.setText("60,000.000");
        gaugeLabel2.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        gaugeLabel2.setOpaque(true);
        gaugePanel2.add(gaugeLabel2);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gaugesPanel.add(gaugePanel2, gridBagConstraints);

        getContentPane().add(gaugesPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 30, -1, -1));

        generatorsPanel.setLayout(new java.awt.GridBagLayout());

        motorPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Generator Motor 1"));
        motorPanel1.setLayout(new java.awt.GridBagLayout());

        motorSpeedLabel1.setBackground(new java.awt.Color(0, 0, 0));
        motorSpeedLabel1.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        motorSpeedLabel1.setForeground(new java.awt.Color(0, 255, 0));
        motorSpeedLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        motorSpeedLabel1.setText("1023.00");
        motorSpeedLabel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        motorSpeedLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(7, 7, 2, 7);
        motorPanel1.add(motorSpeedLabel1, gridBagConstraints);

        motorScroll1.setMaximum(1033);
        motorScroll1.setMinimum(-1023);
        motorScroll1.setOrientation(javax.swing.JScrollBar.HORIZONTAL);
        motorScroll1.addAdjustmentListener(new java.awt.event.AdjustmentListener() {
            public void adjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {
                motorScroll1AdjustmentValueChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        motorPanel1.add(motorScroll1, gridBagConstraints);

        motorStatusLabel1.setBackground(new java.awt.Color(0, 0, 0));
        motorStatusLabel1.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        motorStatusLabel1.setForeground(new java.awt.Color(0, 255, 0));
        motorStatusLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        motorStatusLabel1.setText("1023.00");
        motorStatusLabel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        motorStatusLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(2, 7, 7, 7);
        motorPanel1.add(motorStatusLabel1, gridBagConstraints);

        motorRunButton1.setText("Run");
        motorRunButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                motorRunButton1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 25);
        motorPanel1.add(motorRunButton1, gridBagConstraints);

        motorStopButton1.setText("Stop");
        motorStopButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                motorStopButton1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 25, 0, 5);
        motorPanel1.add(motorStopButton1, gridBagConstraints);

        encoderLabel1.setText("Encoder:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 0);
        motorPanel1.add(encoderLabel1, gridBagConstraints);

        zeroEncoderButton1.setText("Zero");
        zeroEncoderButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zeroEncoderButton1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        motorPanel1.add(zeroEncoderButton1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        generatorsPanel.add(motorPanel1, gridBagConstraints);

        motorPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Generator Motor 2"));
        motorPanel2.setLayout(new java.awt.GridBagLayout());

        motorSpeedLabel2.setBackground(new java.awt.Color(0, 0, 0));
        motorSpeedLabel2.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        motorSpeedLabel2.setForeground(new java.awt.Color(0, 255, 0));
        motorSpeedLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        motorSpeedLabel2.setText("1023.00");
        motorSpeedLabel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        motorSpeedLabel2.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(7, 7, 2, 7);
        motorPanel2.add(motorSpeedLabel2, gridBagConstraints);

        motorScroll2.setMaximum(1033);
        motorScroll2.setMinimum(-1023);
        motorScroll2.setOrientation(javax.swing.JScrollBar.HORIZONTAL);
        motorScroll2.addAdjustmentListener(new java.awt.event.AdjustmentListener() {
            public void adjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {
                motorScroll2AdjustmentValueChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        motorPanel2.add(motorScroll2, gridBagConstraints);

        motorStatusLabel2.setBackground(new java.awt.Color(0, 0, 0));
        motorStatusLabel2.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        motorStatusLabel2.setForeground(new java.awt.Color(0, 255, 0));
        motorStatusLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        motorStatusLabel2.setText("1023.00");
        motorStatusLabel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        motorStatusLabel2.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(2, 7, 7, 7);
        motorPanel2.add(motorStatusLabel2, gridBagConstraints);

        motorRunButton2.setText("Run");
        motorRunButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                motorRunButton2ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 25);
        motorPanel2.add(motorRunButton2, gridBagConstraints);

        motorStopButton2.setText("Stop");
        motorStopButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                motorStopButton2ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 25, 0, 5);
        motorPanel2.add(motorStopButton2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        generatorsPanel.add(motorPanel2, gridBagConstraints);

        pumpPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Air Operated Pump"));
        pumpPanel1.setLayout(new java.awt.GridBagLayout());

        pumpSpeedLabel1.setBackground(new java.awt.Color(0, 0, 0));
        pumpSpeedLabel1.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        pumpSpeedLabel1.setForeground(new java.awt.Color(0, 255, 0));
        pumpSpeedLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pumpSpeedLabel1.setText("1023.00");
        pumpSpeedLabel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        pumpSpeedLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 7, 0, 7);
        pumpPanel1.add(pumpSpeedLabel1, gridBagConstraints);

        pumpBoostLabel1.setText("Boost Level");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        pumpPanel1.add(pumpBoostLabel1, gridBagConstraints);

        pumpScroll1.setBlockIncrement(100);
        pumpScroll1.setMaximum(4010);
        pumpScroll1.setOrientation(javax.swing.JScrollBar.HORIZONTAL);
        pumpScroll1.addAdjustmentListener(new java.awt.event.AdjustmentListener() {
            public void adjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {
                pumpScroll1AdjustmentValueChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        pumpPanel1.add(pumpScroll1, gridBagConstraints);

        pump0Label1.setText("0");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHWEST;
        pumpPanel1.add(pump0Label1, gridBagConstraints);

        pump4000label1.setText("4000");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHEAST;
        pumpPanel1.add(pump4000label1, gridBagConstraints);

        pumpRunButton1.setText("Run");
        pumpRunButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pumpRunButton1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 20);
        pumpPanel1.add(pumpRunButton1, gridBagConstraints);

        pumpStopButton1.setText("Stop");
        pumpStopButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pumpStopButton1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 0);
        pumpPanel1.add(pumpStopButton1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHWEST;
        generatorsPanel.add(pumpPanel1, gridBagConstraints);

        getContentPane().add(generatorsPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 90, -1, -1));

        chambersPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Chamber Select"));
        chambersPanel.setLayout(new javax.swing.BoxLayout(chambersPanel, javax.swing.BoxLayout.Y_AXIS));

        buttonGroup1.add(chamberRadioButton1);
        chamberRadioButton1.setText("Chamber 1");
        chamberRadioButton1.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chamberRadioButton1.setMargin(new java.awt.Insets(0, 0, 0, 0));
        chamberRadioButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chamberRadioButton1ActionPerformed(evt);
            }
        });
        chambersPanel.add(chamberRadioButton1);

        buttonGroup1.add(chamberRadioButton2);
        chamberRadioButton2.setText("Chamber 2");
        chamberRadioButton2.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chamberRadioButton2.setMargin(new java.awt.Insets(0, 0, 0, 0));
        chamberRadioButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chamberRadioButton2ActionPerformed(evt);
            }
        });
        chambersPanel.add(chamberRadioButton2);

        getContentPane().add(chambersPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(300, 430, 91, 56));

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Temperature Control"));
        jPanel1.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        valveLabel5.setBackground(new java.awt.Color(0, 0, 0));
        valveLabel5.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        valveLabel5.setForeground(new java.awt.Color(0, 255, 0));
        valveLabel5.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        valveLabel5.setText("0 F");
        valveLabel5.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        valveLabel5.setName("jTempLabel"); // NOI18N
        valveLabel5.setOpaque(true);
        jPanel1.add(valveLabel5, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 20, 40, -1));

        jButton1.setText("Read");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton1, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 20, 70, -1));

        jButton2.setText("Set");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton2, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 50, 70, -1));

        jTextField1.setText("050");
        jPanel1.add(jTextField1, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 50, 40, -1));

        getContentPane().add(jPanel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(260, 340, 140, 80));

        jMenu1.setText("Options");

        jMenuItem1.setText("Calibrate Piston");
        jMenuItem1.setEnabled(false);
        jMenu1.add(jMenuItem1);

        jMenuItem2.setText("Valve Test");
        jMenuItem2.setEnabled(false);
        jMenu1.add(jMenuItem2);

        jMenuItem4.setText("Reset System");
        jMenuItem4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem4ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem4);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("Exit");

        jMenuItem3.setText("Exit Manual Control");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem3ActionPerformed(evt);
            }
        });
        jMenu2.add(jMenuItem3);

        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);
    }// </editor-fold>//GEN-END:initComponents

    private void chamberRadioButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chamberRadioButton2ActionPerformed
        if (configSettings.getCurrentChamberNumber()==2) {
            return;
        }
        commSystem.controlMotor(1,'C');
        while (commSystem.getMotorStatus(1)!=0) {}
        configSettings.setCurrentChamberNumber(2);
        v2cl=commSystem.rawReading(14);
        v2ol=commSystem.rawReading(15);
    }//GEN-LAST:event_chamberRadioButton2ActionPerformed

    private void chamberRadioButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chamberRadioButton1ActionPerformed
        if (configSettings.getCurrentChamberNumber()==1) {
            return;
        }
        commSystem.controlMotor(1,'C');
        while (commSystem.getMotorStatus(1)!=0) {}
        configSettings.setCurrentChamberNumber(1);
        v2cl=commSystem.rawReading(14);
        v2ol=commSystem.rawReading(15);
    }//GEN-LAST:event_chamberRadioButton1ActionPerformed

    private void motorStopButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_motorStopButton2ActionPerformed
        commSystem.setPGenSpeed(1, 0);
        currentSpeed2=0;
        motorSpeedLabel2.setForeground(new java.awt.Color(0,255,0));
        motorRunning2=false;
    }//GEN-LAST:event_motorStopButton2ActionPerformed

    private void motorRunButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_motorRunButton2ActionPerformed
        currentSpeed2=motorScroll2.getValue();
        commSystem.setPGenSpeed(1, currentSpeed2);
        motorSpeedLabel2.setForeground(new java.awt.Color(255, 0, 0));
        motorRunning2=true;
    }//GEN-LAST:event_motorRunButton2ActionPerformed

    private void motorScroll2AdjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {//GEN-FIRST:event_motorScroll2AdjustmentValueChanged
        motorSpeedLabel2.setText(""+motorScroll2.getValue());
        if (motorRunning2 && motorScroll2.getValueIsAdjusting()==false) {
            currentSpeed2=motorScroll2.getValue();
            commSystem.setPGenSpeed(1,currentSpeed2);            
        }
    }//GEN-LAST:event_motorScroll2AdjustmentValueChanged

    private void jMenuItem4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem4ActionPerformed
        ResetSystem rs = new ResetSystem(this, this, commSystem, -1);
        rs.initialize();
        rs.setVisible(true);
    }//GEN-LAST:event_jMenuItem4ActionPerformed

    private void doorSwitchCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doorSwitchCheckBoxActionPerformed
        gaugeReadThread.setGaugeChannel(-2,doorSwitchCheckBox.isSelected());
        if (doorSwitchCheckBox.isSelected()==false) {
            doorSwitchCheckBox.setText(" ");
        }
    }//GEN-LAST:event_doorSwitchCheckBoxActionPerformed

    private void unlockButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unlockButtonActionPerformed
        int i=configSettings.getDoorLockMotorIndex();
        if (i>=0) {
            commSystem.controlMotor(i,'O');
            JOptionPane.showMessageDialog(this,"Open Door");
            commSystem.controlMotor(i,'S');
        }
    }//GEN-LAST:event_unlockButtonActionPerformed

    private void vacOffButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_vacOffButtonActionPerformed
        int i=configSettings.getVacuumSolenoidValve();
        if (i>=0) {
            commSystem.moveValve(i,'C');
        }
    }//GEN-LAST:event_vacOffButtonActionPerformed

    private void vacOnButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_vacOnButtonActionPerformed
        int i=configSettings.getVacuumSolenoidValve();
        if (i>=0) {
            commSystem.moveValve(i, 'O');
        }
    }//GEN-LAST:event_vacOnButtonActionPerformed

    private void pumpStopButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pumpStopButton1ActionPerformed
        int i=configSettings.getFastPumpAnalogOutput();
        if (i>=0) {
            commSystem.zeroAout(i);
        }
        pumpSpeedLabel1.setForeground(new java.awt.Color(0,255,0));
        pumpRunning=false;
    }//GEN-LAST:event_pumpStopButton1ActionPerformed

    private void pumpRunButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pumpRunButton1ActionPerformed
        int i=configSettings.getFastPumpAnalogOutput();
        if (i>=0) {
            commSystem.setAout(i, pumpScroll1.getValue());
        }
        pumpSpeedLabel1.setForeground(new java.awt.Color(255,0,0));
        pumpRunning=true;
    }//GEN-LAST:event_pumpRunButton1ActionPerformed

    private void motorStopButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_motorStopButton1ActionPerformed
        commSystem.setPGenSpeed(0, 0);
        currentSpeed=0;
        motorSpeedLabel1.setForeground(new java.awt.Color(0,255,0));
        motorRunning=false;
    }//GEN-LAST:event_motorStopButton1ActionPerformed

    private void motorRunButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_motorRunButton1ActionPerformed
        currentSpeed=motorScroll1.getValue();
        commSystem.setPGenSpeed(0, currentSpeed);
        motorSpeedLabel1.setForeground(new java.awt.Color(255, 0, 0));
        motorRunning=true;
    }//GEN-LAST:event_motorRunButton1ActionPerformed

    private void openButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButton4ActionPerformed
        commSystem.controlMotor(3,'O');
    }//GEN-LAST:event_openButton4ActionPerformed

    private void openButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButton3ActionPerformed
        commSystem.controlMotor(2,'O');
    }//GEN-LAST:event_openButton3ActionPerformed

    private void openButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButton2ActionPerformed
        commSystem.controlMotor(1,'O');
    }//GEN-LAST:event_openButton2ActionPerformed

    private void openButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButton1ActionPerformed
        commSystem.controlMotor(0,'O');
    }//GEN-LAST:event_openButton1ActionPerformed

    private void stopButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButton4ActionPerformed
        commSystem.controlMotor(3,'S');
    }//GEN-LAST:event_stopButton4ActionPerformed

    private void stopButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButton3ActionPerformed
        commSystem.controlMotor(2,'S');
    }//GEN-LAST:event_stopButton3ActionPerformed

    private void stopButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButton2ActionPerformed
        commSystem.controlMotor(1,'S');
    }//GEN-LAST:event_stopButton2ActionPerformed

    private void stopButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButton1ActionPerformed
        commSystem.controlMotor(0,'S');
    }//GEN-LAST:event_stopButton1ActionPerformed

    private void closeButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButton4ActionPerformed
        commSystem.controlMotor(3,'C');
    }//GEN-LAST:event_closeButton4ActionPerformed

    private void closeButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButton3ActionPerformed
        commSystem.controlMotor(2,'C');
    }//GEN-LAST:event_closeButton3ActionPerformed

    private void closeButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButton2ActionPerformed
        commSystem.controlMotor(1,'C');
    }//GEN-LAST:event_closeButton2ActionPerformed

    private void closeButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButton1ActionPerformed
        commSystem.controlMotor(0, 'C');
    }//GEN-LAST:event_closeButton1ActionPerformed

    private void pumpScroll1AdjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {//GEN-FIRST:event_pumpScroll1AdjustmentValueChanged
        int i=configSettings.getFastPumpAnalogOutput();
        pumpSpeedLabel1.setText(""+pumpScroll1.getValue());
        if (pumpRunning && pumpScroll1.getValueIsAdjusting()==false && i>=0) {
            commSystem.setAout(i, pumpScroll1.getValue());
        }
    }//GEN-LAST:event_pumpScroll1AdjustmentValueChanged

    private void motorScroll1AdjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {//GEN-FIRST:event_motorScroll1AdjustmentValueChanged
        motorSpeedLabel1.setText(""+motorScroll1.getValue());
        if (motorRunning && motorScroll1.getValueIsAdjusting()==false) {
            currentSpeed=motorScroll1.getValue();
            commSystem.setPGenSpeed(0,currentSpeed);            
        }
    }//GEN-LAST:event_motorScroll1AdjustmentValueChanged

    private void jMenuItem3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem3ActionPerformed
        dispatchEvent(new java.awt.event.WindowEvent(this,java.awt.event.WindowEvent.WINDOW_CLOSING));
    }//GEN-LAST:event_jMenuItem3ActionPerformed

    private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
        commSystem.setPGenSpeed(0,0); // just in case
        if (configSettings.getNumberOfGenerators()>1) {
            commSystem.setPGenSpeed(1,0);
        }
        int rv=JOptionPane.showConfirmDialog(this,"Do you want to reset the system before exiting?",
                "APCS Manual Control",JOptionPane.YES_NO_CANCEL_OPTION);
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
        // only call this if the background task is finished
        if (swingTimer!=null) {
            swingTimer.stop();
        }
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

    private void zeroEncoderButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zeroEncoderButton1ActionPerformed
        commSystem.zeroEncoder(0);
    }//GEN-LAST:event_zeroEncoderButton1ActionPerformed

    private void isolationOpenButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isolationOpenButtonActionPerformed
        int i=configSettings.getSampleIsolationValve();
        if (i>=0) {
            // isolation valve is latching solenoid valve attached, usually, to spot 4
            // which is indexed as 4 (normally spots 1 through 4 are indexed as 0 through 3)
            // because the Rabbit program M10N-AP thinks that spots 1 through 4 are always
            // motors, and spot 5 is a solenoid valve.  But the M10N-AP program does not
            // handle timing of latching valves, so we have to do that here
            commSystem.controlMotor(i,'O');
            final int ii = i;
            // delay by 0.1 seconds and then shut off the motor power
            new Thread() {
                @Override
                public void run() {
                    try {
                        sleep(100);
                    } catch (InterruptedException ex) {}
                    commSystem.controlMotor(ii,'S');
                }
            }.start();
        }
    }//GEN-LAST:event_isolationOpenButtonActionPerformed

    private void isolationCloseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isolationCloseButtonActionPerformed
        int i=configSettings.getSampleIsolationValve();
        if (i>=0) {
            commSystem.controlMotor(i,'C');
            final int ii = i;
            // delay by 0.1 seconds and then shut off the motor power
            new Thread() {
                @Override
                public void run() {
                    try {
                        sleep(100);
                    } catch (InterruptedException ex) {}
                    commSystem.controlMotor(ii,'S');
                }
            }.start();
        }
    }//GEN-LAST:event_isolationCloseButtonActionPerformed

    private int getRawCharacter() {
        long t0 = System.currentTimeMillis();
        int i;
        while ((i=commSystem.getRawCharacter())== -1) {
            if ((System.currentTimeMillis() - t0) > 1000) {
                break;
            }
        }
        return i;
    }
    
    private void specialButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specialButtonActionPerformed
        // place to put special test code for burst tester
        int buffer_length;
        double raw_data[] = new double[32767];
        if (commSystem.isDemoMode()) {
            buffer_length = 5;
            raw_data[0] = 100.;
            raw_data[1] = 20000.;
            raw_data[2] = 25000.;
            raw_data[3] = 200.;
            raw_data[4] = 0.;
            SpecialDataFrame sdf = new SpecialDataFrame(buffer_length, raw_data);
            sdf.setVisible(true);
            sdf.setTimeInterval(1.234);
            return;
        }
        // first, shut down the gauge listener so it doesn't send anything out
        // or take any data we are going to get
        if (gaugeReadThread.isAlive()) {
            gaugeReadThread.pleaseStop();
            try { gaugeReadThread.join(); }
            catch (InterruptedException e) {}
        }
        // send starting sequence
        commSystem.sendRawCharacters("$1");
        Thread thread;
        thread = new Thread() {
            @Override
            public void run() {
                int buffer_length;
                //double raw_data[] = new double[32767];
                long time_interval;
                // now wait for something to come back from the serial port.  This shouldn't take
                // longer than about 20 seconds
                long t0 = System.currentTimeMillis();
                int c1;
                long l1;
                double d1;
                GaugeChannel gc1=configSettings.getGaugeChannel(3);
                while ((c1=commSystem.getRawCharacter())== -1) {
                    if ((System.currentTimeMillis()-t0)>20000) {
                        break;
                    }
                }
                if (c1>=0) {
                    // read entire sequence
                    time_interval = c1;
                    time_interval <<= 8;
                    c1 = getRawCharacter();
                    time_interval += c1;
                    time_interval <<= 8;
                    c1 = getRawCharacter();
                    time_interval += c1;
                    time_interval <<= 8;
                    c1 = getRawCharacter();
                    time_interval += c1;
                    buffer_length = 0;
                    SpecialDataFrame sdf = new SpecialDataFrame();
                    sdf.setVisible(true);
                    do {
                        c1 = getRawCharacter();
                        if (c1<0) {
                            break;
                        }
                        l1 = c1;
                        l1 <<= 8;
                        c1 = getRawCharacter();
                        if (c1<0) {
                            break;
                        }
                        l1 += c1;
                        // these are really raw count values, direct from the ADC
                        // so we need to modify them in the same way as the Rabbit
                        // modifies normal averaged ADC readings in order to get
                        // them to the same scale as normal pressure gauge readings.
                        // scale by 5 since this is a raw 2 volt reading
                        // and the board can actually handle 10 volt readings
                        l1 *= 5;
                        // now remove *4 of bias of 2000
                        l1 -= 8000;
                        // now use this count value to get the actual pressure value
                        d1 = gc1.getUser(gc1.getReal((int)l1));
                        //d1 = l1;
                        //raw_data[buffer_length] = d1;
                        buffer_length++;
                        sdf.addData(d1);
                    } while (buffer_length < 32767);
                    sdf.setTimeInterval(time_interval / 1000.);
                }
                // finally, setup the gauge read thread again, and start it running
                setupGaugeReadThread();
                gaugeReadThread.start();                        
            }
        };            
        thread.start();
    }//GEN-LAST:event_specialButtonActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        // TODO add your handling code here:
        //double crapshoot = commSystem.readAthenaTemp();
        //jLabel1.setText(Double.toString(crapshoot));

        double temp = commSystem.readAthenaTemp() / 10;
        valveLabel5.setText(Double.toString(temp));
     
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        // TODO add your handling code here:

        double digi = Double.parseDouble(jTextField1.getText()) * 10;
        int digi2 = (int)digi;
        commSystem.setAthenaTemp(1, String.format("%04d", digi2));

    }//GEN-LAST:event_jButton2ActionPerformed
    
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
            case -1: // limit switch
                if (countValue==-1) {
            motorStatusLabel1.setText("R Limit");
        }
                else if (countValue==1) {
            motorStatusLabel1.setText("F Limit");
        }
                else {
            motorStatusLabel1.setText("");
        }
                break;
            case -2: // door switch
                if (doorSwitchCheckBox.isSelected()) {
            doorSwitchCheckBox.setText(""+countValue);
        }
                break;
            case -3: // limit switch
                if (countValue==-1) {
            motorStatusLabel2.setText("R Limit");
        }
                else if (countValue==1) {
            motorStatusLabel2.setText("F Limit");
        }
                else {
            motorStatusLabel2.setText("");
        }
                break;
            case -4: // encoder
                encoderLabel1.setText("Encoder: "+String.format("%6.2f",(encoderFactor * countValue)) + " cc (" + countValue + ")");
                break;
            case 2: // main pressure gauge
                currentPressure=configSettings.getGaugeChannel(2).getUser(configSettings.getGaugeChannel(2).getReal(countValue));
                gaugeLabel1.setText(configSettings.getGaugeChannel(2).getUserFormattedString(currentPressure));
                if ((currentSpeed>0) && (currentPressure>=configSettings.getPressureRange())) {
                    commSystem.setPGenSpeed(0,0);
                    currentSpeed=0;
                    motorRunning=false;
                    motorSpeedLabel1.setForeground(new java.awt.Color(0,255,0));
                }
                break;
            case 3: // second pressure gauge
                currentPressure2=configSettings.getGaugeChannel(3).getUser(configSettings.getGaugeChannel(3).getReal(countValue));
                gaugeLabel2.setText(configSettings.getGaugeChannel(3).getUserFormattedString(currentPressure2));
                // if the second pressure gauge is from the sample, and not from a second pressure generator
                // then currentSpeed2 will always be 0, so we don't have to worry about the next line
                if ((currentSpeed2>0) && (currentPressure2>=configSettings.getPressureRange())) {
                    commSystem.setPGenSpeed(1,0);
                    currentSpeed2=0;
                    motorRunning2=false;
                    motorSpeedLabel2.setForeground(new java.awt.Color(0,255,0));
                }
                break;
            case 10: // valve 1 position
                valveLabel1.setText(Main.convertValvePosition(countValue,v1ol,v1cl));
                break;
            case 13: // valve 2 position
                valveLabel2.setText(Main.convertValvePosition(countValue,v2ol,v2cl));
                break;
            case 16: // valve 3 position
                valveLabel3.setText(Main.convertValvePosition(countValue,v3ol,v3cl));
                break;
            case 19: // valve 4 position
                valveLabel4.setText(Main.convertValvePosition(countValue,v4ol,v4cl));
                break;
        }
    }
    
    /**
     * Part of GaugeReadListener interface - called by worker threads that also need
     * to know the current pressures
     * @return last known pressure
     */
    public double getCurrentPressure(int i) {
        return currentPressure;
    }
    
    private class SwingTask implements java.awt.event.ActionListener {
        SwingTask() {
            i1=0;
            i2=0;
            i3=0;
        }
        int i1, i2, i3;
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            // stuff that is run on each task
            i1=i2;
            i2=i3;
            i3=commSystem.getNumReadingsSoFar();
            commSystem.clearNumReadingsSoFar();
            float r=(i1+i2+i3)/3;
            statusLabel.setText(""+commSystem.getNumhangs()+":"+r);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JRadioButton chamberRadioButton1;
    private javax.swing.JRadioButton chamberRadioButton2;
    private javax.swing.JPanel chambersPanel;
    private javax.swing.JButton closeButton1;
    private javax.swing.JButton closeButton2;
    private javax.swing.JButton closeButton3;
    private javax.swing.JButton closeButton4;
    private javax.swing.JLabel demoLabel;
    private javax.swing.JCheckBox doorSwitchCheckBox;
    private javax.swing.JLabel encoderLabel1;
    private javax.swing.JLabel gaugeLabel1;
    private javax.swing.JLabel gaugeLabel2;
    private javax.swing.JPanel gaugePanel1;
    private javax.swing.JPanel gaugePanel2;
    private javax.swing.JPanel gaugesPanel;
    private javax.swing.JPanel generatorsPanel;
    private javax.swing.JButton isolationCloseButton;
    private javax.swing.JButton isolationOpenButton;
    private javax.swing.JPanel isolationPanel;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JMenuItem jMenuItem4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JPanel motorPanel1;
    private javax.swing.JPanel motorPanel2;
    private javax.swing.JButton motorRunButton1;
    private javax.swing.JButton motorRunButton2;
    private javax.swing.JScrollBar motorScroll1;
    private javax.swing.JScrollBar motorScroll2;
    private javax.swing.JLabel motorSpeedLabel1;
    private javax.swing.JLabel motorSpeedLabel2;
    private javax.swing.JLabel motorStatusLabel1;
    private javax.swing.JLabel motorStatusLabel2;
    private javax.swing.JButton motorStopButton1;
    private javax.swing.JButton motorStopButton2;
    private javax.swing.JButton openButton1;
    private javax.swing.JButton openButton2;
    private javax.swing.JButton openButton3;
    private javax.swing.JButton openButton4;
    private javax.swing.JLabel pump0Label1;
    private javax.swing.JLabel pump4000label1;
    private javax.swing.JLabel pumpBoostLabel1;
    private javax.swing.JPanel pumpPanel1;
    private javax.swing.JButton pumpRunButton1;
    private javax.swing.JScrollBar pumpScroll1;
    private javax.swing.JLabel pumpSpeedLabel1;
    private javax.swing.JButton pumpStopButton1;
    private javax.swing.JButton specialButton;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JButton stopButton1;
    private javax.swing.JButton stopButton2;
    private javax.swing.JButton stopButton3;
    private javax.swing.JButton stopButton4;
    private javax.swing.JLabel titleLabel;
    private javax.swing.JButton unlockButton;
    private javax.swing.JButton vacOffButton;
    private javax.swing.JButton vacOnButton;
    private javax.swing.JPanel vacuumPanel;
    private javax.swing.JLabel valveLabel1;
    private javax.swing.JLabel valveLabel2;
    private javax.swing.JLabel valveLabel3;
    private javax.swing.JLabel valveLabel4;
    private javax.swing.JLabel valveLabel5;
    private javax.swing.JPanel valvePanel1;
    private javax.swing.JPanel valvePanel2;
    private javax.swing.JPanel valvePanel3;
    private javax.swing.JPanel valvePanel4;
    private javax.swing.JButton zeroEncoderButton1;
    // End of variables declaration//GEN-END:variables
    
}
