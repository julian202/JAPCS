/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * CycleTest.java
 *
 * Created on Feb 10, 2009, 8:17:05 PM
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
 * Form that shows while cycle test is running
 * Also includes runtime thread that runs the actual test
 * @author Ron V. Webber
 */
public class CycleTest extends javax.swing.JFrame implements GaugeReadListener, AbortQueriable {

    /**
     * Creates new form CycleTest
     * @param commSystem Already open communication system
     * @param callingForm form that will be Notified when this form is closing
     * @param userPath path to user directory
     */
    public CycleTest(APCSCommunication commSystem, Notifiable callingForm, String userPath) {
        setIconImage(new ImageIcon(getClass().getResource("/com/pmiapp/apcs/gifs/apcs16.gif")).getImage());
        initComponents();
        pPanel1 = new PlotPanel();
        plotPanelSubHolder.add(pPanel1, java.awt.BorderLayout.CENTER);
        this.callingForm=callingForm;
        this.commSystem=commSystem;
        this.userPath=userPath;
        configSettings=commSystem.getConfigSettings();
        demoLabel.setVisible(commSystem.isDemoMode());
        closing=false;
        currentPressure=0;
        simulatedPressure=0;
        currentMotorSpeed=0;
        aborted=false;
        outputFileHasBeenSet=false;
        testSettings=new Properties();
        testSettingsFile=new java.io.File(userPath, "cycletest.config");
        try {
            java.io.FileInputStream tempFIS = new java.io.FileInputStream(testSettingsFile);
            testSettings.load(tempFIS);
            tempFIS.close();
        } catch (java.io.IOException e) {}

        uecArray = new com.pmiapp.common.UserEnabledComponent[7];
        uecArray[0]=new com.pmiapp.common.UserEnabledComponent(maxPressureText, maxPressureCheckBox, "MaxP", testSettings, configSettings.isSupervisorMode(),""+configSettings.getPressureRange());
        uecArray[1]=new com.pmiapp.common.UserEnabledComponent(minPressureText, minPressureCheckBox, "MinP", testSettings, configSettings.isSupervisorMode(),"0");
        uecArray[2]=new com.pmiapp.common.UserEnabledComponent(cyclesText, cyclesCheckBox, "CYC", testSettings, configSettings.isSupervisorMode(),"5");
        uecArray[3]=new com.pmiapp.common.UserEnabledComponent(rampUpSpeedText, rampUpSpeedCheckBox, "RU", testSettings, configSettings.isSupervisorMode(),"400");
        uecArray[4]=new com.pmiapp.common.UserEnabledComponent(rampDownSpeedText, rampDownSpeedCheckBox, "RD", testSettings, configSettings.isSupervisorMode(),"1000");
        uecArray[5]=new com.pmiapp.common.UserEnabledComponent(highDwellTimeText, highDwellTimeCheckBox, "DH", testSettings, configSettings.isSupervisorMode(),"10");
        uecArray[6]=new com.pmiapp.common.UserEnabledComponent(lowDwellTimeText, lowDwellTimeCheckBox, "DL", testSettings, configSettings.isSupervisorMode(),"10");

        txtChooser = new JFileChooser();
        txtChooser.addChoosableFileFilter(new com.pmiapp.common.TxtFilter());
        txtChooser.setCurrentDirectory(new java.io.File(userPath,"data"));

        pack();
        // center the form on the screen, so we can get the default location values
        configSettings.setWindowPosition((java.awt.Window)this);
    }
    
    /**
     * Start threads and timers
     */
    public void initialize() {
        gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
        gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
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

    private RunCycleTestThread runCycleTestThread;
    private JFileChooser txtChooser;
    private com.pmiapp.common.UserEnabledComponent[] uecArray;
    private File testSettingsFile;
    private Properties testSettings;
    private javax.swing.Timer swingTimer;
    private boolean aborted;
    private double currentPressure;
    private double simulatedPressure;
    private int currentMotorSpeed;
    private GaugeReadThread gaugeReadThread;
    private com.pmiapp.common.Notifiable callingForm;
    private APCSCommunication commSystem;
    private APCSConfig configSettings;
    private PlotPanel pPanel1;
    private String userPath;
    private boolean closing;
    private int numberOfCycles;
    private int cyclesLeft;
    private int rampUpSpeed, rampDownSpeed;
    private double maximumPressureTarget, minimumPressureTarget;
    private double maximumPressureSeen;
    private double minimumPressureSeen;
    private int highDwellTime, lowDwellTime;

    private long testStartTime;
    private int substep;
    private double highPressureCorrectionFactor;
    private double lowPressureCorrectionFactor;
    private java.io.PrintWriter outputFileWriter;
    private java.io.FileWriter fileWriter;
    private boolean outputFileHasBeenSet;

    private class RunCycleTestThread extends java.lang.Thread implements GaugeReadListener, AbortQueriable {
        public RunCycleTestThread(GaugeReadListener parent) {
            this.parent=parent;
        }

        private GaugeReadListener parent;
        private long testTime0, lastDataStoreTime;

        // called by any routine that we may call that needs to keep reading the pressure gauge
        // this will update the display, and maybe save the results to the output file
        // only channel 2 is supported for this
        // Routines that use this callback can only be called after the outputFileWriter has
        // been initialized.
        public void gaugeReadData(int channel, int countValue, int tag) {
            if (channel==2) {
                parent.gaugeReadData(2, countValue, 0);
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
        
        private void delay1sec() {
            try { sleep(1000); }
            catch (InterruptedException e) {}
        }

        @Override
        public void run() {
            long dwellStart=0;
            // runs the actual cycle test
            // background reading of pressure is still going on

            int cycleMode = 0;
            cyclesLeft = numberOfCycles;
            double highestPressureSeenSoFar=0;
            double lowestPressureSeenSoFar=9999999;

            testStartTime=System.currentTimeMillis();
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

            //AJB 11-25-09
            double startTime = System.nanoTime(), currentTime, interval = .1;
            //DecimalFormat formatter = new DecimalFormat("0.#");
            if(outputFileHasBeenSet){
                //outputFileWriter.println("Time(Sec):" + "\t" + "Pressure:");
            }
            // at this point, we want to shut down the background gauge reader - we will be doing our own
            // pressure gauge reads from now on (until the end of the test)
            if (gaugeReadThread.isAlive()) gaugeReadThread.pleaseStop();
            // don't worry about waiting for the gaugeReadThread to actually stop - it will stop on its own soon enough

            while (aborted==false) {
                //AJB 11-25-09
                currentTime = (System.nanoTime() - startTime) / 1000000000.;

                readPressure();

                //AJB 11-25-09
                if((currentTime > interval) && outputFileHasBeenSet){
                    interval += currentTime;
                    if(commSystem.isDemoMode()){
                        //outputFileWriter.println(formatter.format(currentTime) + "\t" +  formatter.format(simulatedPressure));
                    } else{
                        //outputFileWriter.println(formatter.format(currentTime) + "\t" +  formatter.format(currentPressure));
                    }
                }

                if (currentPressure < lowestPressureSeenSoFar) lowestPressureSeenSoFar=currentPressure;
                if (currentPressure > highestPressureSeenSoFar) highestPressureSeenSoFar=currentPressure;

                if (cyclesLeft < numberOfCycles-1) {
                    if (maximumPressureSeen < currentPressure) maximumPressureSeen=currentPressure;
                    if (minimumPressureSeen > currentPressure) minimumPressureSeen=currentPressure;
                }

                int limit=commSystem.getPGenLimit(0);

                if (cycleMode==0) {
                    // ramp down
                    if (substep==0) {
                        if (aborted==false) delay1sec();
                        commSystem.setPGenSpeed(0,rampDownSpeed);
                        currentMotorSpeed=rampDownSpeed;
                        substep=1;
                        lowestPressureSeenSoFar = maximumPressureSeen + maximumPressureTarget;
                    } else if (substep==1) {
                        if ((limit==-1) || (currentPressure <= lowPressureCorrectionFactor * minimumPressureTarget)) {
                            commSystem.setPGenSpeed(0,0);
                            currentMotorSpeed=0;
                            dwellStart=System.currentTimeMillis();
                            substep=2;
                        }
                    } else if (substep==2) {
                        if (System.currentTimeMillis() - dwellStart > lowDwellTime * 1000) {
                            cycleMode=1;
                            cyclesLeft--;
                            substep=0;
                        }
                    }
                } else if (cycleMode==1) {
                    // ramp up
                    if (substep==0) {
                        commSystem.setPGenSpeed(0,rampUpSpeed);
                        currentMotorSpeed=rampUpSpeed;
                        substep=1;
                        highestPressureSeenSoFar=-10;
                    } else if (substep==1) {
                        if ((limit==1) || (currentPressure > highPressureCorrectionFactor * maximumPressureTarget)) {
                            commSystem.setPGenSpeed(0,0);
                            currentMotorSpeed=0;
                            dwellStart=System.currentTimeMillis();
                            substep=2;
                        }
                    } else if (substep==2) {
                        if (System.currentTimeMillis() - dwellStart > highDwellTime * 1000) {
                            cycleMode=2;
                        }
                    }
                } else if (cycleMode==2) {
                    // adjustment after finish cycle
                    if (outputFileHasBeenSet) {
                        outputFileWriter.println(String.format("%2d%28.1f%20.1f",
                                numberOfCycles-cyclesLeft,highestPressureSeenSoFar,lowestPressureSeenSoFar));
                    }
                    if (highestPressureSeenSoFar < maximumPressureTarget * 0.995)
                        highPressureCorrectionFactor = highPressureCorrectionFactor + 0.5 * Math.abs(maximumPressureTarget - highestPressureSeenSoFar) / maximumPressureTarget;
                    else if (highestPressureSeenSoFar > maximumPressureTarget * 1.005)
                        highPressureCorrectionFactor = highPressureCorrectionFactor - 0.5 * Math.abs(maximumPressureTarget - highestPressureSeenSoFar) / maximumPressureTarget;

                    if (lowestPressureSeenSoFar < minimumPressureTarget * 0.995)
                        lowPressureCorrectionFactor = lowPressureCorrectionFactor + 0.25 * Math.abs(minimumPressureTarget - lowestPressureSeenSoFar) / (minimumPressureTarget + 100);
                    else if (lowestPressureSeenSoFar > minimumPressureTarget * 1.005)
                        lowPressureCorrectionFactor = lowPressureCorrectionFactor - 0.25 * Math.abs(minimumPressureTarget - lowestPressureSeenSoFar) / (minimumPressureTarget + 100);

                    cycleMode = 0;
                    substep = 0;
                }
                cyclesLeftStatusLabel.setText(""+cyclesLeft);
                long t=(System.currentTimeMillis()-testStartTime)/1000;
                long a=t/3600;
                t=t-a*3600;
                long b=t/60;
                t=t-b*60;
                elapsedTimeLabel.setText(String.format("%4d:%02d:%02d",a,b,t));
                if (cyclesLeft==-1) break;
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
            outputFileWriter.println();
            if (cyclesLeft==-1) {
                outputFileWriter.println("[Records after Finished Cycles]");
            } else {
                outputFileWriter.println("[Finished Cycle Data]");
            }
            outputFileWriter.println("Actual performed Cycle:"+(numberOfCycles - cyclesLeft - 1));
            outputFileWriter.println("Total Time:"+elapsedTimeLabel.getText());
            outputFileWriter.println("Frequency: " + ((numberOfCycles - cyclesLeft - 1) / ((System.currentTimeMillis() - testStartTime) / 60000)) +" Cycle/Min");
            outputFileWriter.println("Actual Maximum Pressure : " + maximumPressureSeen + " PSI");
            outputFileWriter.println("Actual Minimum Pressure : " + minimumPressureSeen + " PSI");
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
        cyclesLeftStatusLabel = new javax.swing.JLabel();
        elapsedTimeLabel = new javax.swing.JLabel();
        parameterPanel = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        maxPressureText = new javax.swing.JTextField();
        maxPressureCheckBox = new javax.swing.JCheckBox();
        jLabel8 = new javax.swing.JLabel();
        minPressureText = new javax.swing.JTextField();
        minPressureCheckBox = new javax.swing.JCheckBox();
        jLabel9 = new javax.swing.JLabel();
        cyclesText = new javax.swing.JTextField();
        cyclesCheckBox = new javax.swing.JCheckBox();
        jLabel10 = new javax.swing.JLabel();
        rampUpSpeedText = new javax.swing.JTextField();
        rampUpSpeedCheckBox = new javax.swing.JCheckBox();
        jLabel11 = new javax.swing.JLabel();
        rampDownSpeedText = new javax.swing.JTextField();
        rampDownSpeedCheckBox = new javax.swing.JCheckBox();
        jLabel12 = new javax.swing.JLabel();
        highDwellTimeText = new javax.swing.JTextField();
        highDwellTimeCheckBox = new javax.swing.JCheckBox();
        jLabel13 = new javax.swing.JLabel();
        lowDwellTimeText = new javax.swing.JTextField();
        lowDwellTimeCheckBox = new javax.swing.JCheckBox();
        jMenuBar1 = new javax.swing.JMenuBar();
        optionsMenu = new javax.swing.JMenu();
        resetMenuItem = new javax.swing.JMenuItem();
        viewReportMenuItem = new javax.swing.JMenuItem();
        printPlotMenuItem = new javax.swing.JMenuItem();
        exitMenu = new javax.swing.JMenu();
        exitMenuItem = new javax.swing.JMenuItem();

        setTitle("Cyclic Pressure Testing");
        setName("cycle"); // NOI18N
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
        titleLabel.setText("Mode 3: Cyclic Pressure Testing");
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
        outputFileNameLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
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

        jLabel3.setText("System Pressure (PSI) ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        outputStatusPanel.add(jLabel3, gridBagConstraints);

        jLabel4.setText("Remaining Cycles:");
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

        cyclesLeftStatusLabel.setBackground(new java.awt.Color(0, 0, 0));
        cyclesLeftStatusLabel.setFont(new java.awt.Font("Monospaced", 1, 12));
        cyclesLeftStatusLabel.setForeground(new java.awt.Color(0, 255, 0));
        cyclesLeftStatusLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        cyclesLeftStatusLabel.setText(" ");
        cyclesLeftStatusLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        cyclesLeftStatusLabel.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        outputStatusPanel.add(cyclesLeftStatusLabel, gridBagConstraints);

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

        parameterPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Cyclic Parameter Input"));
        parameterPanel.setLayout(new java.awt.GridBagLayout());

        jLabel7.setText("High Pressure(PSI)  ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel7, gridBagConstraints);

        maxPressureText.setBackground(new java.awt.Color(0, 0, 0));
        maxPressureText.setColumns(6);
        maxPressureText.setFont(new java.awt.Font("Tahoma", 1, 11));
        maxPressureText.setForeground(new java.awt.Color(0, 255, 255));
        maxPressureText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        maxPressureText.setText(" ");
        maxPressureText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(maxPressureText, gridBagConstraints);

        maxPressureCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        parameterPanel.add(maxPressureCheckBox, gridBagConstraints);

        jLabel8.setText("Low Pressure(PSI)  ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel8, gridBagConstraints);

        minPressureText.setBackground(new java.awt.Color(0, 0, 0));
        minPressureText.setColumns(6);
        minPressureText.setFont(new java.awt.Font("Tahoma", 1, 11));
        minPressureText.setForeground(new java.awt.Color(0, 255, 255));
        minPressureText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        minPressureText.setText(" ");
        minPressureText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(minPressureText, gridBagConstraints);

        minPressureCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        parameterPanel.add(minPressureCheckBox, gridBagConstraints);

        jLabel9.setText("Cycle Number: ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel9, gridBagConstraints);

        cyclesText.setBackground(new java.awt.Color(0, 0, 0));
        cyclesText.setColumns(6);
        cyclesText.setFont(new java.awt.Font("Tahoma", 1, 11));
        cyclesText.setForeground(new java.awt.Color(0, 255, 255));
        cyclesText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        cyclesText.setText(" ");
        cyclesText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(cyclesText, gridBagConstraints);

        cyclesCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        parameterPanel.add(cyclesCheckBox, gridBagConstraints);

        jLabel10.setText("Ramp-Up Speed:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel10, gridBagConstraints);

        rampUpSpeedText.setBackground(new java.awt.Color(0, 0, 0));
        rampUpSpeedText.setColumns(6);
        rampUpSpeedText.setFont(new java.awt.Font("Tahoma", 1, 11));
        rampUpSpeedText.setForeground(new java.awt.Color(0, 255, 255));
        rampUpSpeedText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        rampUpSpeedText.setText(" ");
        rampUpSpeedText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(rampUpSpeedText, gridBagConstraints);

        rampUpSpeedCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        parameterPanel.add(rampUpSpeedCheckBox, gridBagConstraints);

        jLabel11.setText("Ramp-Down Speed:  ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel11, gridBagConstraints);

        rampDownSpeedText.setBackground(new java.awt.Color(0, 0, 0));
        rampDownSpeedText.setColumns(6);
        rampDownSpeedText.setFont(new java.awt.Font("Tahoma", 1, 11));
        rampDownSpeedText.setForeground(new java.awt.Color(0, 255, 255));
        rampDownSpeedText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        rampDownSpeedText.setText(" ");
        rampDownSpeedText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(rampDownSpeedText, gridBagConstraints);

        rampDownSpeedCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        parameterPanel.add(rampDownSpeedCheckBox, gridBagConstraints);

        jLabel12.setText("Dwell at H.P. (sec)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel12, gridBagConstraints);

        highDwellTimeText.setBackground(new java.awt.Color(0, 0, 0));
        highDwellTimeText.setColumns(6);
        highDwellTimeText.setFont(new java.awt.Font("Tahoma", 1, 11));
        highDwellTimeText.setForeground(new java.awt.Color(0, 255, 255));
        highDwellTimeText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        highDwellTimeText.setText(" ");
        highDwellTimeText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(highDwellTimeText, gridBagConstraints);

        highDwellTimeCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
        parameterPanel.add(highDwellTimeCheckBox, gridBagConstraints);

        jLabel13.setText("Dwell at L.P. (sec)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        parameterPanel.add(jLabel13, gridBagConstraints);

        lowDwellTimeText.setBackground(new java.awt.Color(0, 0, 0));
        lowDwellTimeText.setColumns(6);
        lowDwellTimeText.setFont(new java.awt.Font("Tahoma", 1, 11));
        lowDwellTimeText.setForeground(new java.awt.Color(0, 255, 255));
        lowDwellTimeText.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        lowDwellTimeText.setText(" ");
        lowDwellTimeText.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        parameterPanel.add(lowDwellTimeText, gridBagConstraints);

        lowDwellTimeCheckBox.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 8;
        parameterPanel.add(lowDwellTimeCheckBox, gridBagConstraints);

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

        exitMenuItem.setText("Exit Cyclic Pressure Testing");
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
        if (runCycleTestThread!=null) if (runCycleTestThread.isAlive()) {
            aborted=true;
            closing=true;
            runCycleTestThread.interrupt(); // stop any sleep the thread may be doing
            try { runCycleTestThread.join(); }
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
        for (int i=0; i<=6; i++)
            uecArray[i].storeFinalResults(testSettings);
        try { testSettings.store(new java.io.FileOutputStream(testSettingsFile),"Cycle Test Settings"); }
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

        try { f = Float.parseFloat(maxPressureText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Maximum Pressure");
            return;
        }
        if (f>configSettings.getPressureRange()) {
            JOptionPane.showMessageDialog(this,"Maximum Pressure exceeds pressure limit");
            return;            
        }
        if (f<=0) return;
        maximumPressureTarget=f;
        
        try { f = Float.parseFloat(minPressureText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Minimum Pressure");
            return;
        }
        if (f>maximumPressureTarget) {
            JOptionPane.showMessageDialog(this,"Low Pressure exceeds high pressure");
            return;            
        }
        if (f<0) f=0;
        minimumPressureTarget=f;
        
        try { i = Integer.parseInt(cyclesText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Number of Cycles");
            return;
        }
        if (i>9999) {
            JOptionPane.showMessageDialog(this,"Number of cycles exceeds 9999");
            return;            
        }
        if (i<1) {
            JOptionPane.showMessageDialog(this,"Number of cycles must be at least 1");
            return;            
        }
        numberOfCycles=i;
        
        try { i = Integer.parseInt(rampUpSpeedText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Ramp Up Speed");
            return;
        }
        if (i>configSettings.getMaxSpeedHP()) {
            JOptionPane.showMessageDialog(this,"Ramp Up Speed exceeds "+configSettings.getMaxSpeedHP());
            return;            
        }
        if (i<50) {
            JOptionPane.showMessageDialog(this,"Ramp Up Speed must be at least 50");
            return;            
        }
        rampUpSpeed=i;

        try { i = Integer.parseInt(rampDownSpeedText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Ramp Down Speed");
            return;
        }
        if (i>configSettings.getMaxSpeedHP()) {
            JOptionPane.showMessageDialog(this,"Ramp Down Speed exceeds "+configSettings.getMaxSpeedHP());
            return;
        }
        if (i<50) {
            JOptionPane.showMessageDialog(this,"Ramp Down Speed must be at least 50");
            return;
        }
        rampDownSpeed=0-i;

        try { i = Integer.parseInt(highDwellTimeText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for High Pressure Dwell Time");
            return;
        }
        if (i>9999) {
            JOptionPane.showMessageDialog(this,"High Pressure Dwell time can not exceeds 9999 sec");
            return;
        }
        highDwellTime=i;

        try { i = Integer.parseInt(lowDwellTimeText.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Low Pressure Dwell Time");
            return;
        }
        if (i>9999) {
            JOptionPane.showMessageDialog(this,"Low Pressure Dwell time can not exceeds 9999 sec");
            return;
        }
        lowDwellTime=i;
        if (outputFileHasBeenSet) {
            try {
                fileWriter = new java.io.FileWriter(outputFileNameLabel.getText(),false);
            }
            catch (java.io.IOException e) {
                JOptionPane.showMessageDialog(this,"Error in output file name");
                return;
            }
            outputFileWriter = new java.io.PrintWriter(fileWriter);
            outputFileWriter.println("Pressure Cycle Testing Data Record");
            outputFileWriter.println();
            outputFileWriter.println("[Setting]");
            outputFileWriter.println("Maximum Setting Pressure: "+maximumPressureTarget+" PSI");
            outputFileWriter.println("Minimum Setting Pressure: "+minimumPressureTarget+" PSI");
            outputFileWriter.println("Total Setting Cycle: "+numberOfCycles);
            outputFileWriter.println("Ramp_Up Speed: "+rampUpSpeed);
            outputFileWriter.println("Ramp_Down Speed: "+rampDownSpeed);
            outputFileWriter.println("Dwell at high pressure: "+highDwellTime+" sec");
            outputFileWriter.println("Dwell at low pressure: "+lowDwellTime+" sec");
            outputFileWriter.println("----------------------------------------------------------");
            outputFileWriter.println("Cycle Number            Peak High (PSI)     Peak Low (PSI)");
            outputFileWriter.println();
        }

        maximumPressureSeen=0;
        minimumPressureSeen=1000000;
        substep=0;
        highPressureCorrectionFactor= 1 - 0.25f * rampUpSpeed / 1023;
        lowPressureCorrectionFactor= 1 + 0.2f * (-rampDownSpeed) / 1023;

        startButton.setEnabled(false);
        viewReportMenuItem.setEnabled(false);
        exitMenuItem.setEnabled(false);
        stopButton.setEnabled(true);
        resetMenuItem.setEnabled(false);
        aborted=false;
        // start a second thread to actually control what happens during testing
        runCycleTestThread = new RunCycleTestThread(this);
        runCycleTestThread.start();

}//GEN-LAST:event_startButtonActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
        aborted=true;
        if (runCycleTestThread!=null) if (runCycleTestThread.isAlive()) {
            runCycleTestThread.interrupt(); // stop any sleep or wait
        }
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
    private javax.swing.JCheckBox cyclesCheckBox;
    private javax.swing.JLabel cyclesLeftStatusLabel;
    private javax.swing.JTextField cyclesText;
    private javax.swing.JPanel dataFilePanel;
    private javax.swing.JLabel demoLabel;
    private javax.swing.JLabel elapsedTimeLabel;
    private javax.swing.JMenu exitMenu;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JCheckBox highDwellTimeCheckBox;
    private javax.swing.JTextField highDwellTimeText;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JCheckBox lowDwellTimeCheckBox;
    private javax.swing.JTextField lowDwellTimeText;
    private javax.swing.JCheckBox maxPressureCheckBox;
    private javax.swing.JTextField maxPressureText;
    private javax.swing.JCheckBox minPressureCheckBox;
    private javax.swing.JTextField minPressureText;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JLabel outputFileNameLabel;
    private javax.swing.JPanel outputStatusPanel;
    private javax.swing.JPanel parameterPanel;
    private javax.swing.JPanel plotPanelHolder;
    private javax.swing.JPanel plotPanelSubHolder;
    private javax.swing.JMenuItem printPlotMenuItem;
    private javax.swing.JCheckBox rampDownSpeedCheckBox;
    private javax.swing.JTextField rampDownSpeedText;
    private javax.swing.JCheckBox rampUpSpeedCheckBox;
    private javax.swing.JTextField rampUpSpeedText;
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
            //case -1: // limit switch
            //    break;
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
