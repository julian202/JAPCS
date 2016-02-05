/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * FractureTest.java
 *
 * Created on July 8, 2010 based on CycleTest.java
 */

package com.pmiapp.apcs;
import com.pmiapp.common.AbortQueriable;
import com.pmiapp.common.GaugeReadListener;
import com.pmiapp.common.Notifiable;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * Form that shows while fracture test is running
 * Also includes runtime thread that runs the actual test
 * @author Ron V. Webber
 */
public class FractureTest extends javax.swing.JFrame implements GaugeReadListener, AbortQueriable {

    /**
     * Creates new form FractureTest
     * @param commSystem Already open communication system
     * @param callingForm form that will be Notified when this form is closing
     * @param userPath path to user directory
     */
    public FractureTest(APCSCommunication commSystem, Notifiable callingForm, String userPath) {
        setIconImage(new ImageIcon(getClass().getResource("/com/pmiapp/apcs/gifs/apcs16.gif")).getImage());
        initComponents();
        pPanel1 = new PlotPanel();
        plotPanelSubHolder.add(pPanel1, java.awt.BorderLayout.CENTER);
        this.callingForm=callingForm;
        this.commSystem=commSystem;
        configSettings=commSystem.getConfigSettings();
        demoLabel.setVisible(commSystem.isDemoMode());
        closing=false;
        currentPressure=0;
        simulatedPressure=0;
        simulatedEncoder=0;
        encoderFactor=configSettings.getEncoderFactor();
        currentMotorSpeed=0;
        aborted=false;
        outputFileHasBeenSet=false;
        testSettings=new Properties();
        testSettingsFile=new java.io.File(userPath, "fracturetest.config");
        try {
            java.io.FileInputStream tempFIS = new java.io.FileInputStream(testSettingsFile);
            testSettings.load(tempFIS);
            tempFIS.close();
        } catch (java.io.IOException e) {}

        uecArray = new com.pmiapp.common.UserEnabledComponent[5];
        uecArray[0]=new com.pmiapp.common.UserEnabledComponent(highPressureText, highPressureCheckBox, "HighP", testSettings, configSettings.isSupervisorMode(),""+configSettings.getPressureRange());
        uecArray[1]=new com.pmiapp.common.UserEnabledComponent(lowPressureText, lowPressureCheckBox, "LowP", testSettings, configSettings.isSupervisorMode(),"0");
        uecArray[2]=new com.pmiapp.common.UserEnabledComponent(highPressureSpeedText, highPressureSpeedCheckBox, "HPSpeed", testSettings, configSettings.isSupervisorMode(),"500");
        uecArray[3]=new com.pmiapp.common.UserEnabledComponent(lowPressureSpeedText, lowPressureSpeedCheckBox, "LPSpeed", testSettings, configSettings.isSupervisorMode(),"1000");
        uecArray[4]=new com.pmiapp.common.UserEnabledComponent(dataIntervalText, dataIntervalCheckBox, "DataInterval", testSettings, configSettings.isSupervisorMode(),"1");

        txtChooser = new JFileChooser();
        txtChooser.addChoosableFileFilter(new com.pmiapp.common.TxtFilter());
        txtChooser.setCurrentDirectory(new java.io.File(userPath,"data"));

        pack();
        // center the form on the screen, so we can get the default location values
        configSettings.setWindowPosition((java.awt.Window)this);
    }
    
    /**
     * Start the gauge reading thread and any timers.
     */
    public void initialize() {
        gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
        gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
        gaugeReadThread.setGaugeChannel(-4,true); // encoder
        // this turns on the gauge reader
        gaugeReadThread.start();

        // initialize graph updater
        if (commSystem.isDemoMode())
            swingTimer = new javax.swing.Timer(250, new java.awt.event.ActionListener() {
                private int i=0;
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    simulatedPressure+=(currentMotorSpeed/1024000.+Math.signum(Math.random()-0.5)/20000.)
                        *configSettings.getPressureRange();
                    if (simulatedPressure<0) simulatedPressure=0;
                    i++;
                    if (i==4) {
                        pPanel1.addDataPoint(simulatedPressure);
                        i=0;
                    }
                    simulatedEncoder+=(currentMotorSpeed/100);
                }
            });
        else
            swingTimer = new javax.swing.Timer(1000, new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    pPanel1.addDataPoint(currentPressure);
                }
            });
        swingTimer.setInitialDelay(0);
        swingTimer.start();
    }

    private RunFractureTestThread runFractureTestThread;
    private JFileChooser txtChooser;
    private com.pmiapp.common.UserEnabledComponent[] uecArray;
    private File testSettingsFile;
    private Properties testSettings;
    private javax.swing.Timer swingTimer;
    private boolean aborted;
    private double currentPressure;
    private double simulatedPressure;
    private int simulatedEncoder;
    private int currentMotorSpeed;
    private GaugeReadThread gaugeReadThread;
    private com.pmiapp.common.Notifiable callingForm;
    private APCSCommunication commSystem;
    private APCSConfig configSettings;
    private PlotPanel pPanel1;
    private boolean closing;
    private int highPressureSpeed, lowPressureSpeed;
    private double highPressureTarget, lowPressureTarget;
    private double dataInterval;
    private double encoderFactor;
    private int currentEncoderCount;
    private double currentEncoderVolume;
    private java.io.PrintWriter outputFileWriter;
    private java.io.FileWriter fileWriter;
    private boolean outputFileHasBeenSet;

    private class RunFractureTestThread extends java.lang.Thread implements GaugeReadListener, AbortQueriable {
        public RunFractureTestThread(GaugeReadListener parent) {
            this.parent=parent;
        }

        private GaugeReadListener parent;

        // called by any routine that we may call that needs to keep reading the pressure gauge
        // this will update the display, and maybe save the results to the output file
        // only channel 2 (and -4 for encoder) is supported for this
        // Routines that use this callback can only be called after the outputFileWriter has
        // been initialized.
        public void gaugeReadData(int channel, int countValue, int tag) {
            if (channel==2) {
                parent.gaugeReadData(2, countValue, 0);
            } else if (channel==-4) {
                parent.gaugeReadData(-4, countValue, 0);
            }
        }

        /**
        * Part of GaugeReadListener interface - called by worker threads that also need
        * to know the current pressures to know the current pressure
        * @return last known pressure
        */
        public double getCurrentPressure(int i) {
            return currentPressure;
        }

        /**
        * find out of the testing has been stopped
        * @return true if we should stop
        */
        public boolean shouldStop() {
            return aborted;
        }

        // read pressure, update display, and maybe save the results to the output file
        // saving results moved to this thread's own gaugeReadData routine
        private void readPressure() {
            gaugeReadData(2, commSystem.rawReading(2),0);
        }

        private void readEncoder() {
            gaugeReadData(-4, commSystem.readEncoder(0),0);
        }

        @Override
        public void run() {
            // runs the actual fracture test
            // background reading of pressure is still going on

            long testStartTime=System.currentTimeMillis();
            // close 1
            commSystem.moveMotorValveAndWait(0,'C',this);

            if (aborted) {
                testFinished(false);
                return;
            }

            // open 2
            commSystem.moveMotorValveAndWait(1, 'O',this);

            if (aborted) {
                testFinished(false);
                return;
            }

            commSystem.zeroEncoder(0);
            simulatedEncoder=0;

            double startTime = System.nanoTime();
            double currentTime;
            double nextDataStorageTime = 0;

            // at this point, we want to shut down the background gauge reader - we will be doing our own
            // pressure gauge reads from now on (until the end of the test)
            if (gaugeReadThread.isAlive()) gaugeReadThread.pleaseStop();
            // don't worry about waiting for the gaugeReadThread to actually stop - it will stop on its own soon enough

            commSystem.setPGenSpeed(0, lowPressureSpeed);
            currentMotorSpeed=lowPressureSpeed;
            int testMode=0;

            while (aborted==false) {
                currentTime = (System.nanoTime() - startTime) / 1000000000.;

                readPressure();
                readEncoder();

                if((currentTime >= nextDataStorageTime) && outputFileHasBeenSet){
                    nextDataStorageTime += dataInterval;
                    outputFileWriter.println(String.format("%10.1f          %10.3f          %7.3f",
                            currentTime, currentPressure, currentEncoderVolume));
                }

                int limit=commSystem.getPGenLimit(0);
                // if we are at the forward limit, we need to push the test to the end
                // because we can't get any higher pressure

                if (((currentPressure>=lowPressureTarget) || (limit==1)) && (testMode==0)) {
                    testMode=1;
                    commSystem.setPGenSpeed(0, highPressureSpeed);
                    currentMotorSpeed=highPressureSpeed;
                }

                if (((currentPressure>=highPressureTarget) || (limit==1)) && (testMode==1)) {
                    testMode=2;
                    commSystem.setPGenSpeed(0, 0);
                    currentMotorSpeed=0;
                    commSystem.controlMotor(0, 'O');
                    simulatedPressure=0;
                }

                if (testMode==2) {
                    if (commSystem.getMotorStatus(0)==0)
                        break;
                }
                long t=(System.currentTimeMillis()-testStartTime)/1000;
                long a=t/3600;
                t=t-a*3600;
                long b=t/60;
                t=t-b*60;
                elapsedTimeLabel.setText(String.format("%4d:%02d:%02d",a,b,t));

            }
            testFinished(true);
        }
    }

    // do stuff when the test thread is finished
    private void testFinished(boolean testWasActuallyStarted) {
        stopButton.setEnabled(false);
        commSystem.setPGenSpeed(0,0);
        currentMotorSpeed=0;
        startButton.setEnabled(true);

        if (outputFileHasBeenSet) {
            outputFileNameLabel.setText(" ");
            outputFileHasBeenSet=false;
            outputFileWriter.close();
            try {
                fileWriter.close();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,"Error closing output file");
            }
        }

        // restart the gauge read background thread, unless we are closing down
        // or the test was not actually started (in which case the background thread
        // would still be running)
        if ((closing==false) && (testWasActuallyStarted)) {
            gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
            gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
            gaugeReadThread.setGaugeChannel(-4,true);
            gaugeReadThread.start();
        }
        // reset the system - unless the test didn't actually start
        if (testWasActuallyStarted) {
            currentMotorSpeed=-1023;
            if (closing==false) {
                ResetSystem rs = new ResetSystem(this, this, commSystem, 0);
                rs.initialize();
                rs.setVisible(true);
            }
        }
        simulatedPressure=0;
        simulatedEncoder=0;
        commSystem.setPGenSpeed(0,0); // just in case
        currentMotorSpeed=0;
        resetMenuItem.setEnabled(true);
        viewReportMenuItem.setEnabled(true);
        exitMenuItem.setEnabled(true);
        aborted=false;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        demoLabel = new javax.swing.JLabel();
        titleLabel = new javax.swing.JLabel();
        controlPanel = new javax.swing.JPanel();
        startButton = new javax.swing.JButton();
        stopButton = new javax.swing.JButton();
        spacerLabel = new javax.swing.JLabel();
        dataFilePanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        outputFileNameLabel = new javax.swing.JLabel();
        plotPanelHolder = new javax.swing.JPanel();
        plotPanelSubHolder = new javax.swing.JPanel();
        spacerLabel1 = new javax.swing.JLabel();
        outputStatusPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        currentPressureStatusLabel = new javax.swing.JLabel();
        encoderStatusLabel = new javax.swing.JLabel();
        elapsedTimeLabel = new javax.swing.JLabel();
        parameterPanel = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        highPressureText = new javax.swing.JTextField();
        highPressureCheckBox = new javax.swing.JCheckBox();
        jLabel8 = new javax.swing.JLabel();
        lowPressureText = new javax.swing.JTextField();
        lowPressureCheckBox = new javax.swing.JCheckBox();
        jLabel12 = new javax.swing.JLabel();
        highPressureSpeedText = new javax.swing.JTextField();
        highPressureSpeedCheckBox = new javax.swing.JCheckBox();
        jLabel13 = new javax.swing.JLabel();
        lowPressureSpeedText = new javax.swing.JTextField();
        lowPressureSpeedCheckBox = new javax.swing.JCheckBox();
        jLabel9 = new javax.swing.JLabel();
        dataIntervalText = new javax.swing.JTextField();
        dataIntervalCheckBox = new javax.swing.JCheckBox();
        jMenuBar1 = new javax.swing.JMenuBar();
        optionsMenu = new javax.swing.JMenu();
        resetMenuItem = new javax.swing.JMenuItem();
        viewReportMenuItem = new javax.swing.JMenuItem();
        printPlotMenuItem = new javax.swing.JMenuItem();
        exitMenu = new javax.swing.JMenu();
        exitMenuItem = new javax.swing.JMenuItem();

        setTitle("Fracture Testing");
        setName("fracture"); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        demoLabel.setFont(new java.awt.Font("SansSerif", 0, 18));
        demoLabel.setForeground(new java.awt.Color(192, 0, 0));
        demoLabel.setText("Demo Mode");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        getContentPane().add(demoLabel, gridBagConstraints);

        titleLabel.setFont(new java.awt.Font("Serif", 1, 18));
        titleLabel.setForeground(java.awt.Color.red);
        titleLabel.setText("Fracture Testing");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        getContentPane().add(titleLabel, gridBagConstraints);

        controlPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Auto Control"));

        startButton.setText("Start");
        startButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startButtonActionPerformed(evt);
            }
        });
        controlPanel.add(startButton);

        stopButton.setText("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButtonActionPerformed(evt);
            }
        });
        controlPanel.add(stopButton);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 17, 0);
        getContentPane().add(controlPanel, gridBagConstraints);

        spacerLabel.setFont(new java.awt.Font("SansSerif", 1, 18));
        spacerLabel.setForeground(new java.awt.Color(192, 0, 0));
        spacerLabel.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        getContentPane().add(spacerLabel, gridBagConstraints);

        dataFilePanel.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Data File:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        dataFilePanel.add(jLabel1, gridBagConstraints);

        outputFileNameLabel.setBackground(new java.awt.Color(0, 0, 0));
        outputFileNameLabel.setFont(new java.awt.Font("Tahoma", 1, 11));
        outputFileNameLabel.setForeground(new java.awt.Color(0, 255, 255));
        outputFileNameLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        outputFileNameLabel.setText("Input the file name here for data saving");
        outputFileNameLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        outputFileNameLabel.setOpaque(true);
        outputFileNameLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                outputFileNameLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        dataFilePanel.add(outputFileNameLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(dataFilePanel, gridBagConstraints);

        plotPanelHolder.setLayout(new java.awt.GridBagLayout());

        plotPanelSubHolder.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        plotPanelSubHolder.setLayout(new java.awt.BorderLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 3);
        plotPanelHolder.add(plotPanelSubHolder, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(plotPanelHolder, gridBagConstraints);

        spacerLabel1.setFont(new java.awt.Font("SansSerif", 1, 18));
        spacerLabel1.setForeground(new java.awt.Color(192, 0, 0));
        spacerLabel1.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        getContentPane().add(spacerLabel1, gridBagConstraints);

        outputStatusPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("System Monitor"));
        outputStatusPanel.setLayout(new java.awt.GridBagLayout());

        jLabel3.setText("System Pressure (PSI):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        outputStatusPanel.add(jLabel3, gridBagConstraints);

        jLabel4.setText("Encoder Volume (cc):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        outputStatusPanel.add(jLabel4, gridBagConstraints);

        jLabel5.setText("Elapsed Time (H:M:S): ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        outputStatusPanel.add(jLabel5, gridBagConstraints);

        currentPressureStatusLabel.setBackground(new java.awt.Color(0, 0, 0));
        currentPressureStatusLabel.setFont(new java.awt.Font("Monospaced", 1, 12));
        currentPressureStatusLabel.setForeground(new java.awt.Color(0, 255, 0));
        currentPressureStatusLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        currentPressureStatusLabel.setText(" ");
        currentPressureStatusLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        currentPressureStatusLabel.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        outputStatusPanel.add(currentPressureStatusLabel, gridBagConstraints);

        encoderStatusLabel.setBackground(new java.awt.Color(0, 0, 0));
        encoderStatusLabel.setFont(new java.awt.Font("Monospaced", 1, 12));
        encoderStatusLabel.setForeground(new java.awt.Color(0, 255, 0));
        encoderStatusLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        encoderStatusLabel.setText(" ");
        encoderStatusLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        encoderStatusLabel.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        outputStatusPanel.add(encoderStatusLabel, gridBagConstraints);

        elapsedTimeLabel.setBackground(new java.awt.Color(0, 0, 0));
        elapsedTimeLabel.setFont(new java.awt.Font("Monospaced", 1, 12));
        elapsedTimeLabel.setForeground(new java.awt.Color(0, 255, 0));
        elapsedTimeLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        elapsedTimeLabel.setText(" ");
        elapsedTimeLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        elapsedTimeLabel.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        outputStatusPanel.add(elapsedTimeLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(13, 5, 0, 1);
        getContentPane().add(outputStatusPanel, gridBagConstraints);

        parameterPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Parameter Input"));
        parameterPanel.setLayout(new java.awt.GridBagLayout());

        jLabel7.setText("High Pressure(PSI)  ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel7, gridBagConstraints);

        highPressureText.setBackground(new java.awt.Color(0, 0, 0));
        highPressureText.setColumns(6);
        highPressureText.setFont(new java.awt.Font("Tahoma", 1, 11));
        highPressureText.setForeground(new java.awt.Color(0, 255, 255));
        highPressureText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        highPressureText.setText(" ");
        highPressureText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(highPressureText, gridBagConstraints);

        highPressureCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        parameterPanel.add(highPressureCheckBox, gridBagConstraints);

        jLabel8.setText("Low Pressure(PSI)  ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel8, gridBagConstraints);

        lowPressureText.setBackground(new java.awt.Color(0, 0, 0));
        lowPressureText.setColumns(6);
        lowPressureText.setFont(new java.awt.Font("Tahoma", 1, 11));
        lowPressureText.setForeground(new java.awt.Color(0, 255, 255));
        lowPressureText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        lowPressureText.setText(" ");
        lowPressureText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(lowPressureText, gridBagConstraints);

        lowPressureCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        parameterPanel.add(lowPressureCheckBox, gridBagConstraints);

        jLabel12.setText("Speed to H.P.");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel12, gridBagConstraints);

        highPressureSpeedText.setBackground(new java.awt.Color(0, 0, 0));
        highPressureSpeedText.setColumns(6);
        highPressureSpeedText.setFont(new java.awt.Font("Tahoma", 1, 11));
        highPressureSpeedText.setForeground(new java.awt.Color(0, 255, 255));
        highPressureSpeedText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        highPressureSpeedText.setText(" ");
        highPressureSpeedText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(highPressureSpeedText, gridBagConstraints);

        highPressureSpeedCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        parameterPanel.add(highPressureSpeedCheckBox, gridBagConstraints);

        jLabel13.setText("Speed to L.P.");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel13, gridBagConstraints);

        lowPressureSpeedText.setBackground(new java.awt.Color(0, 0, 0));
        lowPressureSpeedText.setColumns(6);
        lowPressureSpeedText.setFont(new java.awt.Font("Tahoma", 1, 11));
        lowPressureSpeedText.setForeground(new java.awt.Color(0, 255, 255));
        lowPressureSpeedText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        lowPressureSpeedText.setText(" ");
        lowPressureSpeedText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(lowPressureSpeedText, gridBagConstraints);

        lowPressureSpeedCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        parameterPanel.add(lowPressureSpeedCheckBox, gridBagConstraints);

        jLabel9.setText("Recording Interval (sec)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel9, gridBagConstraints);

        dataIntervalText.setBackground(new java.awt.Color(0, 0, 0));
        dataIntervalText.setColumns(6);
        dataIntervalText.setFont(new java.awt.Font("Tahoma", 1, 11));
        dataIntervalText.setForeground(new java.awt.Color(0, 255, 255));
        dataIntervalText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        dataIntervalText.setText(" ");
        dataIntervalText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(dataIntervalText, gridBagConstraints);

        dataIntervalCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        parameterPanel.add(dataIntervalCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 1);
        getContentPane().add(parameterPanel, gridBagConstraints);

        optionsMenu.setText("Options");

        resetMenuItem.setText("Reset System");
        resetMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(resetMenuItem);

        viewReportMenuItem.setText("View a report");
        viewReportMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewReportMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(viewReportMenuItem);

        printPlotMenuItem.setText("Print pressure versus time graph");
        printPlotMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printPlotMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(printPlotMenuItem);

        jMenuBar1.add(optionsMenu);

        exitMenu.setText("Exit");

        exitMenuItem.setText("Exit Fracture Testing");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        exitMenu.add(exitMenuItem);

        jMenuBar1.add(exitMenu);

        setJMenuBar(jMenuBar1);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        //wantToClose=true;
        //if (!allowedToClose) return;
        //autoRunning=false;
        swingTimer.stop();
        if (runFractureTestThread!=null) if (runFractureTestThread.isAlive()) {
            aborted=true;
            closing=true;
            try { runFractureTestThread.join(); }
            catch (InterruptedException e) {}
        }
        if (gaugeReadThread.isAlive()) {
            //for (int i=-1; i<58; i++) gaugeReadThread.setGaugeChannel(i,false);
            gaugeReadThread.pleaseStop();
            try { gaugeReadThread.join(); }
            catch (InterruptedException e) {}
        }
        // store the current screen location
        configSettings.rememberWindowPosition(this);
        // store current test settings
        for (int i=0; i<=4; i++)
            uecArray[i].storeFinalResults(testSettings);
        try { testSettings.store(new java.io.FileOutputStream(testSettingsFile),"Fracture Test Settings"); }
        catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this,"Error writing test settings to file - your test settings may not be remembered for the next test");
        }

        // this will close the communication system (or take control of it for some other purpose)
        // and dispose of this window
        callingForm.notifyTaskFinished(this, 0);

    }//GEN-LAST:event_formWindowClosing

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        //  check parameters
        float f;
        int i;

        try { f = Float.parseFloat(highPressureText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for High Pressure");
            return;
        }
        if (f>configSettings.getPressureRange()) {
            JOptionPane.showMessageDialog(this,"High Pressure exceeds pressure limit");
            return;            
        }
        if (f<=0) return;
        highPressureTarget=f;
        
        try { f = Float.parseFloat(lowPressureText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Low Pressure");
            return;
        }
        if (f>highPressureTarget) {
            JOptionPane.showMessageDialog(this,"Low Pressure exceeds high pressure");
            return;            
        }
        if (f<0) f=0;
        lowPressureTarget=f;
        
        try { f = Float.parseFloat(dataIntervalText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Recording Interval");
            return;
        }
        if (f<=0) {
            JOptionPane.showMessageDialog(this,"Recording Interval must be above 0");
            return;
        }
        dataInterval=f;

        try { i = Integer.parseInt(lowPressureSpeedText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Low Pressure Speed");
            return;
        }
        if (i>configSettings.getMaxSpeedLP()) {
            JOptionPane.showMessageDialog(this,"Low Pressure Speed exceeds "+configSettings.getMaxSpeedLP());
            return;
        }
        if (i<50) {
            JOptionPane.showMessageDialog(this,"Low Pressure Speed must be at least 50");
            return;            
        }
        lowPressureSpeed=i;

        try { i = Integer.parseInt(highPressureSpeedText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for High Pressure Speed");
            return;
        }
        if (i>configSettings.getMaxSpeedHP()) {
            JOptionPane.showMessageDialog(this,"High Pressure Speed exceeds "+configSettings.getMaxSpeedHP());
            return;
        }
        if (i<50) {
            JOptionPane.showMessageDialog(this,"High Pressure Speed must be at least 50");
            return;
        }
        highPressureSpeed=i;

        if (outputFileHasBeenSet) {
            try {
                fileWriter = new java.io.FileWriter(outputFileNameLabel.getText(),false);
            }
            catch (java.io.IOException e) {
                JOptionPane.showMessageDialog(this,"Error in output file name");
                return;
            }
            outputFileWriter = new java.io.PrintWriter(fileWriter);
            outputFileWriter.println("Fracture Testing Data Record");
            outputFileWriter.println();
            outputFileWriter.println("High Pressure: "+highPressureTarget+" PSI");
            outputFileWriter.println("Low Pressure: "+lowPressureTarget+" PSI");
            outputFileWriter.println("High Pressure Speed: "+highPressureSpeed);
            outputFileWriter.println("Low Pressure Speed: "+lowPressureSpeed);
            outputFileWriter.println("----------------------------------------------------------");
            outputFileWriter.println("Time                Pressure (PSI)      Volume (cc)");
            outputFileWriter.println();
        }

        commSystem.controlMotor(1, 'S'); // just in case
        commSystem.controlMotor(1, 'O'); // start valve 2 opening so they can load their sample
        commSystem.setPGenSpeed(0,-1023); // also reset the piston while we have the time
        if (JOptionPane.showConfirmDialog(this, "Load sample and seal chamber",
                "Fracture Test",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.CANCEL_OPTION) {
            commSystem.controlMotor(1, 'S'); // just in case
            commSystem.setPGenSpeed(0,0);
            commSystem.controlMotor(1, 'C'); // start valve closing since they canceled
            return;
        }
        commSystem.setPGenSpeed(0,0);
        commSystem.controlMotor(1, 'S'); // just in case

        startButton.setEnabled(false);
        viewReportMenuItem.setEnabled(false);
        exitMenuItem.setEnabled(false);
        stopButton.setEnabled(true);
        resetMenuItem.setEnabled(false);
        aborted=false;
        // start a second thread to actually control what happens during testing
        runFractureTestThread = new RunFractureTestThread(this);
        runFractureTestThread.start();
}//GEN-LAST:event_startButtonActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
        aborted=true;
}//GEN-LAST:event_stopButtonActionPerformed

    private void resetMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMenuItemActionPerformed
        currentMotorSpeed=-1023;
        ResetSystem rs = new ResetSystem(this, this, commSystem, 0);
        rs.initialize();
        rs.setVisible(true);
}//GEN-LAST:event_resetMenuItemActionPerformed

    private void viewReportMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewReportMenuItemActionPerformed
        if (txtChooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) {
            com.pmiapp.common.TextFileViewer tfv;
            try {
                tfv=new com.pmiapp.common.TextFileViewer(txtChooser.getSelectedFile());
                tfv.setVisible(true);
            }
            catch (java.io.FileNotFoundException e) {
                JOptionPane.showMessageDialog(this,"File Not Found");
            }
        }
}//GEN-LAST:event_viewReportMenuItemActionPerformed

    private void printPlotMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_printPlotMenuItemActionPerformed
        java.awt.print.PrinterJob printJob = java.awt.print.PrinterJob.getPrinterJob();
        printJob.setPrintable(pPanel1);
        if (printJob.printDialog()==false) return;
        try {printJob.print();}
        catch (java.awt.print.PrinterException e) {
            JOptionPane.showMessageDialog(this,"Error trying to print graph");
        }
}//GEN-LAST:event_printPlotMenuItemActionPerformed

    private void outputFileNameLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_outputFileNameLabelMouseClicked
        txtChooser.setCurrentDirectory(new File(configSettings.getDataPath()));
        if (txtChooser.showSaveDialog(this)==JFileChooser.APPROVE_OPTION) {
            File f = txtChooser.getSelectedFile();
            // make sure extension is ".txt"
            // note that getExtension always returns a lower case string
            if (!com.pmiapp.common.TxtFilter.getExtension(f).equals("txt")) {
                String s=f.getName();
                if (s.endsWith(".")==false)
                    s=s+".";
                f=new File(f.getParentFile(),s+"txt");
            }
            // check for overwrite
            if (f.exists())
                if (JOptionPane.showConfirmDialog(this,
                        "File already exists\nDo you want to overwrite?",
                        "Data File", JOptionPane.YES_NO_OPTION)==JOptionPane.NO_OPTION) return;
            outputFileNameLabel.setText(f.getAbsolutePath());
            configSettings.setDataPath(f.getParent());
            outputFileHasBeenSet=true;
        }
}//GEN-LAST:event_outputFileNameLabelMouseClicked

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        formWindowClosing(null);
}//GEN-LAST:event_exitMenuItemActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel controlPanel;
    private javax.swing.JLabel currentPressureStatusLabel;
    private javax.swing.JPanel dataFilePanel;
    private javax.swing.JCheckBox dataIntervalCheckBox;
    private javax.swing.JTextField dataIntervalText;
    private javax.swing.JLabel demoLabel;
    private javax.swing.JLabel elapsedTimeLabel;
    private javax.swing.JLabel encoderStatusLabel;
    private javax.swing.JMenu exitMenu;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JCheckBox highPressureCheckBox;
    private javax.swing.JCheckBox highPressureSpeedCheckBox;
    private javax.swing.JTextField highPressureSpeedText;
    private javax.swing.JTextField highPressureText;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JCheckBox lowPressureCheckBox;
    private javax.swing.JCheckBox lowPressureSpeedCheckBox;
    private javax.swing.JTextField lowPressureSpeedText;
    private javax.swing.JTextField lowPressureText;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JLabel outputFileNameLabel;
    private javax.swing.JPanel outputStatusPanel;
    private javax.swing.JPanel parameterPanel;
    private javax.swing.JPanel plotPanelHolder;
    private javax.swing.JPanel plotPanelSubHolder;
    private javax.swing.JMenuItem printPlotMenuItem;
    private javax.swing.JMenuItem resetMenuItem;
    private javax.swing.JLabel spacerLabel;
    private javax.swing.JLabel spacerLabel1;
    private javax.swing.JButton startButton;
    private javax.swing.JButton stopButton;
    private javax.swing.JLabel titleLabel;
    private javax.swing.JMenuItem viewReportMenuItem;
    // End of variables declaration//GEN-END:variables

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
            case -4: // encoder
                if (commSystem.isDemoMode()) currentEncoderCount=simulatedEncoder;
                else currentEncoderCount=countValue;
                currentEncoderVolume=currentEncoderCount*encoderFactor;
                encoderStatusLabel.setText(String.format("%7.3f",currentEncoderVolume));
                break;
            case 2: // main pressure gauge
                if (commSystem.isDemoMode()) currentPressure=simulatedPressure;
                else currentPressure=configSettings.getGaugeChannel(2).getUser(configSettings.getGaugeChannel(2).getReal(countValue));
                currentPressureStatusLabel.setText(configSettings.getGaugeChannel(2).getUserFormattedString(currentPressure));
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

    /**
     * find out of the testing has been stopped
     * @return true if we should stop
     */
    public boolean shouldStop() {
        return aborted;
    }

}
