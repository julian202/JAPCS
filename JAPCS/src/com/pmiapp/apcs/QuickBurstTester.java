/*
 * QuickBurstTester.java
 *
 * Created on 8/10/12 based on BurstTester.java
 */

package com.pmiapp.apcs;
import com.pmiapp.common.AbortQueriable;
import com.pmiapp.common.GaugeReadListener;
import com.pmiapp.common.Notifiable;
import com.pmiapp.common.TestLogging;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * Form that shows while quick burst test is running
 * Also includes runtime thread that runs the actual test
 * @author Ron V. Webber
 */
public class QuickBurstTester extends javax.swing.JFrame implements GaugeReadListener, AbortQueriable {
    
    /**
     * Creates new form QuickBurstTester
     * @param commSystem Already open communication system
     * @param callingForm form that will be Notified when this form is closing
     * @param userPath path to user directory
     */
    public QuickBurstTester(APCSCommunication commSystem, Notifiable callingForm, String userPath) {
        setIconImage(new ImageIcon(getClass().getResource("/com/pmiapp/apcs/gifs/apcs16.gif")).getImage());
        initComponents();
        pPanel1 = new PlotPanel();
        //jPanel6.add(pPanel1, java.awt.BorderLayout.CENTER);
        this.callingForm=callingForm;
        this.commSystem=commSystem;
        this.userPath=userPath;
        configSettings=commSystem.getConfigSettings();
        demoLabel.setVisible(commSystem.isDemoMode());
        unlockDoorButton.setVisible(configSettings.doesDoorLockExist());
        samplePressureGaugeChannel = configSettings.getSamplePressureGauge();
        pack();
        // center the form on the screen, so we can get the default location values
        configSettings.setWindowPosition((java.awt.Window) this);
        // set display labels so they don't shrink
        targetPressureDisplay.setPreferredSize(targetPressureDisplay.getSize());
        targetPressureDisplay.setText("");
        estimatedBurstPressureDisplay.setPreferredSize(estimatedBurstPressureDisplay.getSize());
        estimatedBurstPressureDisplay.setText("");
        systemPressureDisplay.setPreferredSize(systemPressureDisplay.getSize());
        systemPressureDisplay.setText("");
        samplePressureDisplay.setPreferredSize(samplePressureDisplay.getSize());
        samplePressureDisplay.setText("");
        motorSpeedDisplay.setPreferredSize(motorSpeedDisplay.getSize());
        motorSpeedDisplay.setText("");
        statusLabel1.setPreferredSize(statusLabel1.getSize());
        statusLabel1.setText("Welcome !");
        statusLabel2.setPreferredSize(statusLabel2.getSize());
        statusLabel2.setText("");
        statusLabel3.setPreferredSize(statusLabel3.getSize());
        statusLabel3.setText("");
        statusLabel4.setPreferredSize(statusLabel4.getSize());
        statusLabel4.setText("");
        statusLabel5.setPreferredSize(statusLabel5.getSize());
        statusLabel5.setText("");
        statusLabel6.setPreferredSize(statusLabel6.getSize());
        statusLabel6.setText("");
        statusLabel7.setPreferredSize(statusLabel7.getSize());
        statusLabel7.setText("");
        currentPressure=0;
        currentSamplePressure=0;
        simulatedPressure=0;
        currentMotorSpeed=0;
        aborted=false;
        closing=false;
        hasBurst=false;
        safeToOpenDoor=false; // default to not safe
        testSettings=new Properties();
        loggingEnabled=configSettings.isBurstTestLogging();
        autoName=configSettings.isBurstTestLoggingAutoName();
        logFile=configSettings.getBurstTestLogfile();
        loggingCheckBoxMenuItem.setSelected(loggingEnabled);
        txtChooser = new JFileChooser();
        txtChooser.addChoosableFileFilter(new com.pmiapp.common.TxtFilter());
        txtChooser.setCurrentDirectory(new java.io.File(userPath,"data"));
    }

    /**
     * Start gauge reading thread and timers for this form
     * Call this after you have instantiated the form and set it visible
     */
    public void initialize() {
        gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
        gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
        gaugeReadThread.setGaugeChannel(samplePressureGaugeChannel,true);
        // this turns on the gauge reader
        gaugeReadThread.start();

        QuickBurstTestSetup qbts = new QuickBurstTestSetup(this, userPath, configSettings, testSettings);
        qbts.autoRun(); // ask user for sample ID and initialize the test settings properties
        
        if (testSettings.getProperty("datafile","").length()>0) {
            // don't enable the start button just yet
            //startButton.setEnabled(true);
            dataFileDisplay.setText(testSettings.getProperty("datafile",""));
            estimatedBurstPressureDisplay.setText(testSettings.getProperty("EPT", "error"));
            targetPressureDisplay.setText(testSettings.getProperty("PIP", "error"));
        }
        
        // initialize graph updater
        if (commSystem.isDemoMode()) {
            swingTimer = new javax.swing.Timer(250, new java.awt.event.ActionListener() {
                private int i=0;
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    simulatedPressure+=(currentMotorSpeed/1024000.+Math.signum(Math.random()-0.5)/20000.)
                        *configSettings.getPressureRange();
                    if (simulatedPressure<0) {
                        simulatedPressure=0;
                    }
                    // simulate burst at 3000
                    if (simulatedPressure>3000) {
                        simulatedPressure=0;
                    }
                    i++;
                    if (i==4) {
                        pPanel1.addDataPoint(simulatedPressure);
                        i=0;
                    }
                }
            });
        }
        else {
            swingTimer = new javax.swing.Timer(1000, new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    pPanel1.addDataPoint(currentPressure);
                    
                }
            });
        }
        swingTimer.setInitialDelay(0);
        swingTimer.start();
        
        final QuickBurstTester me = this;
        // do all valve motion in another thread so the display keeps getting updated
        new Thread() {
            @Override
            public void run() {
                // stop air pump
                airPumpChannel = configSettings.getFastPumpAnalogOutput();
                if (airPumpChannel>=0) {
                    commSystem.zeroAout(airPumpChannel);
                }
                    
                // make sure generator is running full speed reverse
                commSystem.setPGenSpeed(0,-1023);
                    
                // make sure that valve 1 is open
                statusLabel2.setText("Opening valve 1...");
                commSystem.moveMotorValveAndWait(0, 'O', me);
                    
                // make sure that valve 2 is open
                statusLabel3.setText("Opening valve 2...");
                commSystem.moveMotorValveAndWait(1, 'O', me);
                    
                // make sure isolation valve is open
                moveIsolationValve('O');
                statusLabel4.setText("Done initializing.");
                // don't do any other valve thing for the next 0.1 seconds
                // because of the way the isolation valve works
        
                //NOW enable the start button
                if (testSettings.getProperty("datafile","").length()>0) {
                    startButton.setEnabled(true);
                }
            }
        }.start();
    }
    
    private int samplePressureGaugeChannel;
    private int airPumpChannel;
    private double phase1Pressure, phase2Pressure, airPumpPercent;
    private long dataStoreIntervalMillis;
    private int currentMotorSpeed;
    private GaugeReadThread gaugeReadThread;
    private com.pmiapp.common.Notifiable callingForm;
    private APCSCommunication commSystem;
    private APCSConfig configSettings;
    private PlotPanel pPanel1;
    private javax.swing.Timer swingTimer;
    private double currentPressure, currentSamplePressure;
    private double simulatedPressure;
    private Properties testSettings;
    private String userPath;
    private RunQuickBurstTestThread runQuickBurstTestThread;
    private boolean aborted, closing, hasBurst, safeToOpenDoor, loggingEnabled;
    private boolean autoName;
    private String logFile;
    private java.io.PrintWriter outputFileWriter;
    private java.io.FileWriter fileWriter;
    private double maxPressureSoFar;
    private JFileChooser txtChooser;
    private double maxPressureTime;
    private boolean readSamplePressure;
    private void moveIsolationValve(char direction) {
        int i=configSettings.getSampleIsolationValve();
        if (i>=0) {
            // isolation valve is latching solenoid valve attached, usually, to spot 4
            // which is indexed as 4 (normally spots 1 through 4 are indexed as 0 through 3)
            // because the Rabbit program M10N-AP thinks that spots 1 through 4 are always
            // motors, and spot 5 is a solenoid valve.  But the M10N-AP program does not
            // handle timing of latching valves, so we have to do that here
            commSystem.controlMotor(i,direction);
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
    }
    
    private class RunQuickBurstTestThread extends java.lang.Thread implements GaugeReadListener, AbortQueriable {

        public RunQuickBurstTestThread(GaugeReadListener parent) {
            this.parent=parent;
        }
        
        private GaugeReadListener parent;
        private long testTime0, lastDataStoreTime;
        
        // called by any routine that we may call that needs to keep reading the pressure gauge 
        // this will update the display, and maybe save the results to the output file
        // channel 2 is the main pressure gauge
        // any other channel is for the sample pressure gauge
        // Routines that use this callback can only be called after the outputFileWriter has
        // been initialized.
        public void gaugeReadData(int channel, int countValue, int tag) {
            if (channel==2) {
                parent.gaugeReadData(2, countValue, 0);
                long t = System.currentTimeMillis();
                if (t-lastDataStoreTime>=.001) {
                    lastDataStoreTime=t;
                    outputFileWriter.printf("%-9.2f %4.0f %4.0f%n", (t-testTime0)/1000., currentPressure, currentSamplePressure);
                }
            } else {
                parent.gaugeReadData(channel, countValue, 0);
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

        // read pressures (system and sample), update display, and maybe save the results to the output file
        // saving results moved to this thread's own gaugeReadData routine
        private void readPressure() {
            if (readSamplePressure==true){
            gaugeReadData(samplePressureGaugeChannel, commSystem.rawReading(samplePressureGaugeChannel),0);
            }
            gaugeReadData(2, commSystem.rawReading(2),0);
            //AW TEST
        }
        
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
        
        @Override
        public void run() {
            long t0;
            // runs the actual quick burst test
            // background reading of pressure is still going on
            statusLabel4.setText("Initializing...");
            statusLabel5.setText("Opening valve 1...");
            commSystem.moveMotorValveAndWait(0,'O',this);
            if (!aborted) {
                statusLabel5.setText("Opening valve 2...");
                commSystem.moveMotorValveAndWait(1,'O',this);
            }
            // if we are going to use the air pump, open 3
            if ((airPumpPercent > 0.0) && (!aborted)) {
                statusLabel5.setText("Opening valve 3...");
                commSystem.moveMotorValveAndWait(2, 'O',this);
            }
            statusLabel5.setText("Generator to reverse limit...");
            // make sure we are at reverse limit
            while ((!aborted) && (commSystem.getPGenLimit(0)!= -1)) { }
            if (!aborted) {
                // close 1
                statusLabel5.setText("Closing valve 1...");
                commSystem.moveMotorValveAndWait(0,'C',this);
            }
            targetPressureDisplay.setText(""+phase1Pressure);
            statusLabel4.setText("Phase I:  Pressurize the sample");
            // set the air pump
            if ((airPumpPercent > 0.0) && (!aborted)) {
                int i;
                if (airPumpPercent >= 100) {
                    i = 4000;
                } else {
                    i = (int) (airPumpPercent * 40);
                }
                commSystem.setAout(airPumpChannel, i);
                // close valve 3
                statusLabel5.setText("Closing valve 3...");
                commSystem.moveMotorValveAndWait(2,'C',this);
                commSystem.zeroAout(airPumpChannel);
            }
            // at this point, we want to shut down the background gauge reader - we will be doing our own
            // pressure gauge reads from now on (until the end of the test)
            if (gaugeReadThread.isAlive()) {
                gaugeReadThread.pleaseStop();
            }
            testTime0=System.currentTimeMillis();
            lastDataStoreTime=testTime0-dataStoreIntervalMillis;
            // don't worry about waiting for the gaugeReadThread to actually stop - it will stop on its own soon enough
            if (aborted==false) {
                statusLabel5.setText("pressure at full speed......");
                // set it to 0 to start with so we don't have to wait for it to climb from -1023 to 0
                // before it starts moving
                commSystem.setPGenSpeed(0,0);
                commSystem.setPGenSpeed(0, 1023);
                motorSpeedDisplay.setText("1023");
                currentMotorSpeed=1023;
                safeToOpenDoor=false;
                // read pressure gauge and update screen (and set currentPressure) while pressure is below holding pressure
                do {
                    readPressure();
                } while ((currentPressure<phase1Pressure) && (aborted==false));
                // leave generator at full speed
                //commSystem.setPGenSpeed(0,0);
                //currentMotorSpeed=0;
                // close isolation valve
                readPressure();
                moveIsolationValve('C');
                readPressure();
            }
            readSamplePressure = true;
            maxPressureSoFar = currentSamplePressure;
            readSamplePressure = false;
            statusLabel4.setText("Phase II:   Pressurize the system");
            statusLabel5.setText("with the sample isolated");
            targetPressureDisplay.setText(""+phase2Pressure);
            do {
                readPressure();
                // check forward limit switch
                if (commSystem.getPGenLimit(0)==1) {
                    statusLabel7.setText("Aborting - forward limit switch");
                    aborted=true;
                }
                readPressure();
            } while ((currentPressure < phase2Pressure) && (aborted==false));
            readPressure();
            commSystem.setPGenSpeed(0, 0);
            motorSpeedDisplay.setText("0");
            currentMotorSpeed=0;
            readPressure();
            if (!aborted) {
                readPressure();
                // do special function
                // this opens the isolation valve and reads the sample pressure gauge as fast
                // as possible, then dumps the data
                // we don't have to worry about other commands messing up since we already
                // turned off the background gauge reader routine
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
                    readPressure();
                } else {
                    readPressure();
                    // send starting sequence
                    commSystem.sendRawCharacters("$1");
                    long time_interval;
                    // now wait for something to come back from the serial port.  This shouldn't take
                    // longer than about 20 seconds
                    t0 = System.currentTimeMillis();
                    int c1;
                    long l1;
                    double d1;
                    readPressure();
                    while ((c1=commSystem.getRawCharacter())== -1) {
                        readPressure();
                        if ((System.currentTimeMillis()-t0)>20000) {
                            readPressure();
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
                            readPressure();
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
                            readPressure();
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
                            //d1 = gc1.getUser(gc1.getReal((int)l1));
                            readSamplePressure = true;
                            d1=configSettings.getGaugeChannel(samplePressureGaugeChannel).getUser(configSettings.getGaugeChannel(samplePressureGaugeChannel).getReal((int)l1));
                            readSamplePressure = false;
                            if (d1 > maxPressureSoFar) {
                                maxPressureSoFar = d1;
                                maxPressureTime = (System.currentTimeMillis()-testTime0)/1000;
                                
                            }
                            
                            currentSamplePressure = d1;
                            //d1 = l1;
                            //raw_data[buffer_length] = d1;
                            buffer_length++;
                            sdf.addData(d1);
                            //////AW TEST
                            outputFileWriter.printf("%-9.2f %4.0f %4.0f%n", (lastDataStoreTime-testTime0)/1000., currentPressure, currentSamplePressure);
                            readPressure();
                        } while (buffer_length < 32767);
                        sdf.setTimeInterval(time_interval / 1000.);
                        readPressure();
                    }
                    readPressure();
                }
                readPressure();
                //Determine from currentSamplePressure if the sample has burst
                readSamplePressure = true;
                hasBurst = (currentSamplePressure < 100);
                readSamplePressure = false;
                if (hasBurst) {
                    statusLabel4.setText("Sample burst at "+String.format("%4.0f", maxPressureSoFar));
                    statusLabel5.setText("Test is done!");
                    statusLabel6.setText("");
                    statusLabel7.setText("");
                    outputFileWriter.printf("%-9.2f %4.0f%n", (System.currentTimeMillis()-testTime0)/1000., currentPressure);
                    outputFileWriter.println();
                    outputFileWriter.printf("BURST TIME: " + String.format("%4.2f", maxPressureTime) + " - SAMPLE BURST AT %4.0f PSI%n",maxPressureSoFar);
                    // wait for up to 5 seconds for the pressure to drop below 100 PSI
                    // if it does, this is a real full burst and we need to close valve 2
                    // if it doesn't, this is not enough of a burst that we want to close valve 2
                    t0=System.currentTimeMillis();
                    hasBurst=false; // in case it didn't really burst
                    while ((System.currentTimeMillis()-t0)<5000) {
                        // read current pressure without saving the results anywhere
                        readPressure();
                        parent.gaugeReadData(2, commSystem.rawReading(2),0);
                        if (currentPressure<100) {
                            hasBurst=true; // it is a real burst
                            break;
                        }
                    }
                }
            }
            if (aborted) {
                statusLabel4.setText("Test Aborted");
                statusLabel5.setText("");
                statusLabel6.setText("");
                statusLabel7.setText("");
            }
            testFinished(true);
        }
    }
    
    // do stuff when the test thread is finished
    private void testFinished(boolean testWasActuallyStarted) {
        stopButton.setEnabled(false);
        // restart the gauge read background thread, unless we are closing down
        // or the test was not actually started (in which case the background thread
        // would still be running)
        if ((closing==false) && (testWasActuallyStarted)) {
            gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
            gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
            gaugeReadThread.setGaugeChannel(samplePressureGaugeChannel, true);
            gaugeReadThread.start();
        }
        outputFileWriter.println("========================================");
        outputFileWriter.close();
        try {
            fileWriter.close();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,"Error closing output file");
        }
        // reset the system - unless the test didn't actually start
        if (testWasActuallyStarted) {
            if (hasBurst) {
                boolean fileError=false;
                // we can also output the log file here, if possible
                if (loggingEnabled && (logFile.length()>0)) {
                    try {
                        java.io.FileWriter fw;
                        if (autoName) {
                            //File f1 = new File(logFile);
                            // store the log file in the same directory as the data file
                            File f1 = new File(testSettings.getProperty("datafile"));
                            // use the name of the sample id, followed by log.txt
                            File f2 = new File(f1.getParentFile(),testSettings.getProperty("SID")+"-log.txt");
                            fw = new java.io.FileWriter(f2,true);
                        }
                        else {
                            fw = new java.io.FileWriter(logFile, true);
                        }
                        outputFileWriter=new java.io.PrintWriter(fw);
                        Date date=new Date();
                        outputFileWriter.print(java.text.DateFormat.getDateInstance().format(date));
                        outputFileWriter.print('\t');
                        outputFileWriter.print(java.text.DateFormat.getTimeInstance().format(date));
                        outputFileWriter.print('\t');
                        outputFileWriter.print(testSettings.getProperty("ST"));
                        outputFileWriter.print('\t');
                        outputFileWriter.printf("%4.0f", maxPressureSoFar);
                        outputFileWriter.println();
                        if (outputFileWriter.checkError()) {
                            fileError=true;
                        }
                        outputFileWriter.close();
                        fw.close();
                    }
                    catch (java.io.IOException e) { fileError=true; }                    
                }
                // don't really need to close valve 2 at this time?
                //commSystem.moveMotorValveAndWait(1,'C',this);
                safeToOpenDoor=true;
                if (fileError) {
                    JOptionPane.showMessageDialog(this,"Error writing to log file");
                }
            }
            currentMotorSpeed=-1023;
            motorSpeedDisplay.setText("-1023");
            if (closing==false) {
                ResetSystem rs = new ResetSystem(this, this, commSystem, 0, safeToOpenDoor);
                rs.initialize();
                rs.setVisible(true);
                safeToOpenDoor=rs.isItSafe();
            }
        }
        simulatedPressure=0;
        motorSpeedDisplay.setText("0");
        commSystem.setPGenSpeed(0,0); // just in case
        setupButton.setEnabled(true);
        resetMenuItem.setEnabled(true);
        statusLabel7.setText("Ready for next test.");
        unlockDoorButton.setEnabled(true);        
    }

/** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        demoLabel = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        startButton = new javax.swing.JButton();
        stopButton = new javax.swing.JButton();
        setupButton = new javax.swing.JButton();
        spacerLabel = new javax.swing.JLabel();
        jPanel4 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        dataFileDisplay = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        statusLabel1 = new javax.swing.JLabel();
        statusLabel2 = new javax.swing.JLabel();
        statusLabel3 = new javax.swing.JLabel();
        statusLabel4 = new javax.swing.JLabel();
        statusLabel5 = new javax.swing.JLabel();
        statusLabel6 = new javax.swing.JLabel();
        statusLabel7 = new javax.swing.JLabel();
        jPanel6 = new javax.swing.JPanel();
        spacerLabel1 = new javax.swing.JLabel();
        unlockDoorButton = new javax.swing.JButton();
        jPanel7 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        targetPressureDisplay = new javax.swing.JLabel();
        estimatedBurstPressureDisplay = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        systemPressureDisplay = new javax.swing.JLabel();
        motorSpeedDisplay = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        samplePressureDisplay = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        optionsMenu = new javax.swing.JMenu();
        resetMenuItem = new javax.swing.JMenuItem();
        reportMenuItem = new javax.swing.JMenuItem();
        printGraphMenuItem = new javax.swing.JMenuItem();
        loggingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        exitMenu = new javax.swing.JMenu();
        exitMenuItem = new javax.swing.JMenuItem();

        setTitle("Automated Pressure Burst Tester");
        setName("BurstTester"); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                exitForm(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        demoLabel.setFont(new java.awt.Font("SansSerif", 0, 18)); // NOI18N
        demoLabel.setForeground(new java.awt.Color(192, 0, 0));
        demoLabel.setText("Demo Mode");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        getContentPane().add(demoLabel, gridBagConstraints);

        jLabel2.setFont(new java.awt.Font("Serif", 1, 18)); // NOI18N
        jLabel2.setForeground(java.awt.Color.red);
        jLabel2.setText("Quick Burst Test");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        getContentPane().add(jLabel2, gridBagConstraints);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Pressure Auto Control"));

        startButton.setText("START");
        startButton.setEnabled(false);
        startButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startButtonActionPerformed(evt);
            }
        });
        jPanel1.add(startButton);

        stopButton.setText("STOP");
        stopButton.setEnabled(false);
        stopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButtonActionPerformed(evt);
            }
        });
        jPanel1.add(stopButton);

        setupButton.setText("SETUP");
        setupButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setupButtonActionPerformed(evt);
            }
        });
        jPanel1.add(setupButton);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        getContentPane().add(jPanel1, gridBagConstraints);

        spacerLabel.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
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

        jPanel4.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Data File:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        jPanel4.add(jLabel1, gridBagConstraints);

        dataFileDisplay.setBackground(new java.awt.Color(0, 0, 0));
        dataFileDisplay.setForeground(new java.awt.Color(255, 255, 255));
        dataFileDisplay.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        dataFileDisplay.setText(" ");
        dataFileDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        dataFileDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 4);
        jPanel4.add(dataFileDisplay, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(jPanel4, gridBagConstraints);

        jPanel5.setLayout(new java.awt.GridBagLayout());

        statusLabel1.setBackground(new java.awt.Color(0, 0, 0));
        statusLabel1.setForeground(new java.awt.Color(0, 255, 255));
        statusLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        statusLabel1.setText("Information Center");
        statusLabel1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel5.add(statusLabel1, gridBagConstraints);

        statusLabel2.setBackground(new java.awt.Color(0, 0, 0));
        statusLabel2.setForeground(new java.awt.Color(0, 255, 255));
        statusLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        statusLabel2.setText("Examples of long lines:");
        statusLabel2.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel5.add(statusLabel2, gridBagConstraints);

        statusLabel3.setBackground(new java.awt.Color(0, 0, 0));
        statusLabel3.setForeground(new java.awt.Color(0, 255, 255));
        statusLabel3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        statusLabel3.setText("Phase I: Pressurize the sample to the holding pressure");
        statusLabel3.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel5.add(statusLabel3, gridBagConstraints);

        statusLabel4.setBackground(new java.awt.Color(0, 0, 0));
        statusLabel4.setForeground(new java.awt.Color(0, 255, 255));
        statusLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        statusLabel4.setText("Exceeds Expected Burst Pressure, Continue?");
        statusLabel4.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel5.add(statusLabel4, gridBagConstraints);

        statusLabel5.setBackground(new java.awt.Color(0, 0, 0));
        statusLabel5.setForeground(new java.awt.Color(0, 255, 255));
        statusLabel5.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        statusLabel5.setText("System pressure exceeds the expected burst pressure");
        statusLabel5.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel5.add(statusLabel5, gridBagConstraints);

        statusLabel6.setBackground(new java.awt.Color(0, 0, 0));
        statusLabel6.setForeground(new java.awt.Color(0, 255, 255));
        statusLabel6.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        statusLabel6.setText("Closing V2 and Opening V1");
        statusLabel6.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel5.add(statusLabel6, gridBagConstraints);

        statusLabel7.setBackground(new java.awt.Color(0, 0, 0));
        statusLabel7.setForeground(new java.awt.Color(0, 255, 255));
        statusLabel7.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        statusLabel7.setText("Rechargind System - data not being saved.");
        statusLabel7.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        statusLabel7.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 1.0;
        jPanel5.add(statusLabel7, gridBagConstraints);

        jPanel6.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        jPanel6.setLayout(new java.awt.BorderLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 3);
        jPanel5.add(jPanel6, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jPanel5, gridBagConstraints);

        spacerLabel1.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
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

        unlockDoorButton.setText("Unlock Door");
        unlockDoorButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unlockDoorButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        getContentPane().add(unlockDoorButton, gridBagConstraints);

        jPanel7.setLayout(new java.awt.GridBagLayout());

        jPanel2.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jPanel2.setLayout(new java.awt.GridBagLayout());

        jLabel3.setText("Target Pressure (PSI)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel2.add(jLabel3, gridBagConstraints);

        jLabel6.setText("Estimated Burst Pressure (PSI)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel2.add(jLabel6, gridBagConstraints);

        targetPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        targetPressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        targetPressureDisplay.setForeground(new java.awt.Color(0, 255, 255));
        targetPressureDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        targetPressureDisplay.setText("60,000.00");
        targetPressureDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        targetPressureDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel2.add(targetPressureDisplay, gridBagConstraints);

        estimatedBurstPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        estimatedBurstPressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        estimatedBurstPressureDisplay.setForeground(new java.awt.Color(0, 255, 255));
        estimatedBurstPressureDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        estimatedBurstPressureDisplay.setText("60,000.00");
        estimatedBurstPressureDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        estimatedBurstPressureDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel2.add(estimatedBurstPressureDisplay, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHEAST;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 1);
        jPanel7.add(jPanel2, gridBagConstraints);

        jPanel3.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jPanel3.setLayout(new java.awt.GridBagLayout());

        jLabel7.setText("System Pressure (PSI)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel3.add(jLabel7, gridBagConstraints);

        jLabel8.setText("Motor Speed");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel3.add(jLabel8, gridBagConstraints);

        systemPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        systemPressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        systemPressureDisplay.setForeground(new java.awt.Color(0, 255, 0));
        systemPressureDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        systemPressureDisplay.setText("60,000.00");
        systemPressureDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        systemPressureDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel3.add(systemPressureDisplay, gridBagConstraints);

        motorSpeedDisplay.setBackground(new java.awt.Color(0, 0, 0));
        motorSpeedDisplay.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        motorSpeedDisplay.setForeground(new java.awt.Color(255, 255, 0));
        motorSpeedDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        motorSpeedDisplay.setText("60,000.00");
        motorSpeedDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        motorSpeedDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel3.add(motorSpeedDisplay, gridBagConstraints);

        jLabel9.setText("Sample Pressure (PSI)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel3.add(jLabel9, gridBagConstraints);

        samplePressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        samplePressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        samplePressureDisplay.setForeground(new java.awt.Color(0, 255, 0));
        samplePressureDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        samplePressureDisplay.setText("60,000.00");
        samplePressureDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        samplePressureDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel3.add(samplePressureDisplay, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 45, 0, 1);
        jPanel7.add(jPanel3, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridheight = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.25;
        getContentPane().add(jPanel7, gridBagConstraints);

        optionsMenu.setText("Options");

        resetMenuItem.setText("Reset System");
        resetMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(resetMenuItem);

        reportMenuItem.setText("View a Report");
        reportMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reportMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(reportMenuItem);

        printGraphMenuItem.setText("Print Pressure versus Time Graph");
        printGraphMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printGraphMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(printGraphMenuItem);

        loggingCheckBoxMenuItem.setText("Log Burst Test Data");
        loggingCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loggingCheckBoxMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(loggingCheckBoxMenuItem);

        jMenuBar1.add(optionsMenu);

        exitMenu.setText("Exit");

        exitMenuItem.setText("Exit Quick Burst Test");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        exitMenu.add(exitMenuItem);

        jMenuBar1.add(exitMenu);

        setJMenuBar(jMenuBar1);
    }// </editor-fold>//GEN-END:initComponents

    private void reportMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reportMenuItemActionPerformed
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
    }//GEN-LAST:event_reportMenuItemActionPerformed

    private void loggingCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loggingCheckBoxMenuItemActionPerformed
        TestLogging btl = new TestLogging(this, userPath, loggingEnabled, autoName, logFile );
        btl.setVisible(true);
        loggingEnabled=btl.isLoggingEnabled();
        autoName=btl.isAutoName();
        loggingCheckBoxMenuItem.setSelected(loggingEnabled);
        logFile=btl.getLogFile();
        configSettings.setBurstTestLogfile(logFile);
        configSettings.setBurstTestLogging(loggingEnabled);
        configSettings.setBurstTestLoggingAutoName(autoName);
    }//GEN-LAST:event_loggingCheckBoxMenuItemActionPerformed

    private void printGraphMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_printGraphMenuItemActionPerformed
        java.awt.print.PrinterJob printJob = java.awt.print.PrinterJob.getPrinterJob();
        //printJob.setPrintable(pPanel1);
        if (printJob.printDialog()==false) {
            return;
        }
        try {printJob.print();}
        catch (java.awt.print.PrinterException e) {
            JOptionPane.showMessageDialog(this,"Error trying to print graph");
        }
    }//GEN-LAST:event_printGraphMenuItemActionPerformed

    private void unlockDoorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unlockDoorButtonActionPerformed
        int i=configSettings.getDoorLockMotorIndex();
        if (i>=0) {
            if (safeToOpenDoor) {
                commSystem.controlMotor(i,'O');
                JOptionPane.showMessageDialog(this,"Open Door");
                commSystem.controlMotor(i,'S');
            }
            else {
                JOptionPane.showMessageDialog(this,"May not be safe to open door.  Reset generator first.");
            }
        }
    }//GEN-LAST:event_unlockDoorButtonActionPerformed

    private void resetMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMenuItemActionPerformed
        ResetSystem rs = new ResetSystem(this, this, commSystem, 0, safeToOpenDoor);
        rs.initialize();
        rs.setVisible(true);
        safeToOpenDoor=rs.isItSafe();
    }//GEN-LAST:event_resetMenuItemActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
        stopButton.setEnabled(false);
        aborted=true;
        // interrupt any delay loop that the running thread may be in
        if (runQuickBurstTestThread!=null) {
            if (runQuickBurstTestThread.isAlive()) {
                runQuickBurstTestThread.interrupt();
            }
        }
    }//GEN-LAST:event_stopButtonActionPerformed

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        // load variables from testSettings
        try {phase1Pressure = Double.parseDouble(testSettings.getProperty("PIP")); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Phase I Pressure");
            return;
        }
        try {phase2Pressure = Double.parseDouble(testSettings.getProperty("PIIP")); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Phase II Pressure");
            return;
        }
        try {airPumpPercent = Integer.parseInt(testSettings.getProperty("APP")); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Air Pump Percent");
            return;
        }
        double dsi;
        try {dsi = Double.parseDouble(testSettings.getProperty("DSI")); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Data Saving Interval");
            return;
        }
        dataStoreIntervalMillis=(long)(dsi * 1000);
        try {
            fileWriter = new java.io.FileWriter(testSettings.getProperty("datafile"),true);
        }
        catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this,"Error in output file name");
            return;
        }
        outputFileWriter = new java.io.PrintWriter(fileWriter);
        // check door switch
        if (commSystem.isDemoMode()==false) {
            if ((commSystem.getDigitalInput(0) & 1)==1) {
                JOptionPane.showMessageDialog(this,"Close door before starting test");
                // maybe they closed the door before clicking on OK?
                if ((commSystem.getDigitalInput(0) & 1)==1) {
                    return;
                }
            }
        }
        unlockDoorButton.setEnabled(false);
        outputFileWriter.println("QUICK BURST PRESSURE TEST");
        outputFileWriter.println();
        outputFileWriter.print("Test Date:     ");
        Date date=new Date();
        outputFileWriter.println(java.text.DateFormat.getDateInstance().format(date));
        outputFileWriter.print("Test Time:     ");
        outputFileWriter.println(java.text.DateFormat.getTimeInstance().format(date));
        outputFileWriter.println();
        outputFileWriter.println("Testing parameters setup:");
        outputFileWriter.println();
        outputFileWriter.print("                       Sample ID:      ");
        outputFileWriter.println(testSettings.getProperty("SID"));
        outputFileWriter.print("     Additional Test Information:      ");
        outputFileWriter.println(testSettings.getProperty("ST"));
        outputFileWriter.print("        Estimated Burst Pressure:      ");
        outputFileWriter.print(testSettings.getProperty("EPT"));
        outputFileWriter.println(" PSI");
        outputFileWriter.print("                Air Pump Percent:      ");
        outputFileWriter.print(testSettings.getProperty("APP"));
        outputFileWriter.println(" %");
        outputFileWriter.print("                Phase I Pressure:      ");
        outputFileWriter.print(testSettings.getProperty("PIP"));
        outputFileWriter.println(" PSI");
        outputFileWriter.print("               Phase II Pressure:      ");
        outputFileWriter.println(testSettings.getProperty("PIIP"));
        outputFileWriter.print("            Data saving interval:      ");
        outputFileWriter.print(testSettings.getProperty("DSI"));
        outputFileWriter.println(" seconds");
        outputFileWriter.print("  Hydraulic Medium used for test:      ");
        outputFileWriter.println(testSettings.getProperty("Liquid"));
        outputFileWriter.print("                  Data file name:      ");
        outputFileWriter.println(testSettings.getProperty("datafile"));
        outputFileWriter.println();
        outputFileWriter.println("Time     PSI");
        outputFileWriter.println();
        // everything should be set to run start an auto test running
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        setupButton.setEnabled(false);
        aborted=false;
        resetMenuItem.setEnabled(false);
        statusLabel1.setText("");
        statusLabel2.setText("INFORMATION CENTER");
        statusLabel3.setText("");
        statusLabel4.setText("Initializing System...");
        statusLabel5.setText("");
        statusLabel6.setText("");
        statusLabel7.setText("");
        motorSpeedDisplay.setText("-1023");
        // make sure the system is reset.  If they abort this, we can't start the test
        // if we don't have a vacuum valve, the very next thing we will be doing is open valve
        // 2, so we don't need to pre-open it just to close it again
        if (configSettings.getVacuumMotorValve()<0) {
            safeToOpenDoor=true;
        }
        //ResetSystem rs = new ResetSystem(this, this, commSystem, 0, safeToOpenDoor);
        //rs.initialize();
        //rs.setVisible(true);
        //if (rs.wasAborted()) {
        //    statusLabel4.setText("Initial Setup Aborted");
        //    testFinished(false);
        //}
        //else {
            // start a second thread to actually control what happens during testing
            runQuickBurstTestThread = new RunQuickBurstTestThread(this);
            runQuickBurstTestThread.start();
        //}
    }//GEN-LAST:event_startButtonActionPerformed

    private void setupButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setupButtonActionPerformed
        QuickBurstTestSetup qbts = new QuickBurstTestSetup(this, userPath, configSettings, testSettings);
        qbts.setVisible(true);
        if (testSettings.getProperty("datafile","").length()>0) {
            startButton.setEnabled(true);
            dataFileDisplay.setText(testSettings.getProperty("datafile",""));
            estimatedBurstPressureDisplay.setText(testSettings.getProperty("EPT", "error"));
            targetPressureDisplay.setText(testSettings.getProperty("PIP", "error"));
        }
    }//GEN-LAST:event_setupButtonActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        exitForm(null);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
        // only call this if the background task is finished
        swingTimer.stop();
        if (runQuickBurstTestThread!=null) {
            if (runQuickBurstTestThread.isAlive()) {
                aborted=true;
                closing=true;
                // interrupt any possible sleep condition in the thread
                runQuickBurstTestThread.interrupt();
                // join the thread until it finishes properly
                try { runQuickBurstTestThread.join(); }
                catch (InterruptedException e) {}
            }
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
    
    /**
     * find out of the testing has been stopped
     * @return true if we should stop
     */
    public boolean shouldStop() {
        return aborted;
    }

    /**
     * Part of GaugeReadListener interface - called by GaugeReadThread whenever it has a new gauge
     * reading.
     * @param channel gauge channel that was read
     * @param countValue count value of the reading
     * @param tag tag value of the gauge read thread, for future expansion to distinguish between
     * multiple gauge reading threads
     */
    public void gaugeReadData(int channel, int countValue, int tag) {
        if (channel==2) {
            // main pressure gauge
            if (commSystem.isDemoMode()) {
                currentPressure=simulatedPressure;
            } else {
                currentPressure=configSettings.getGaugeChannel(2).getUser(configSettings.getGaugeChannel(2).getReal(countValue));
            }
            systemPressureDisplay.setText(configSettings.getGaugeChannel(2).getUserFormattedString(currentPressure));
        } else if (channel == samplePressureGaugeChannel) {
            // main pressure gauge
            if (commSystem.isDemoMode()) {
                currentSamplePressure=simulatedPressure;
            } else {
                currentSamplePressure=configSettings.getGaugeChannel(samplePressureGaugeChannel).getUser(configSettings.getGaugeChannel(samplePressureGaugeChannel).getReal(countValue));
            }
            samplePressureDisplay.setText(configSettings.getGaugeChannel(samplePressureGaugeChannel).getUserFormattedString(currentSamplePressure));
        }
    }

    /**
     * Part of GaugeReadListener interface - called by worker threads that also need
     * to know the current pressures to know the current pressure
     * @return last known pressure
     * @param i = 2 for main pressure gauge, anything else for sample pressure gauge
     */
    public double getCurrentPressure(int i) {
        if (i!=2) {
            return currentSamplePressure;
        } 
        return currentPressure;
    }
        
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel dataFileDisplay;
    private javax.swing.JLabel demoLabel;
    private javax.swing.JLabel estimatedBurstPressureDisplay;
    private javax.swing.JMenu exitMenu;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JCheckBoxMenuItem loggingCheckBoxMenuItem;
    private javax.swing.JLabel motorSpeedDisplay;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JMenuItem printGraphMenuItem;
    private javax.swing.JMenuItem reportMenuItem;
    private javax.swing.JMenuItem resetMenuItem;
    private javax.swing.JLabel samplePressureDisplay;
    private javax.swing.JButton setupButton;
    private javax.swing.JLabel spacerLabel;
    private javax.swing.JLabel spacerLabel1;
    private javax.swing.JButton startButton;
    private javax.swing.JLabel statusLabel1;
    private javax.swing.JLabel statusLabel2;
    private javax.swing.JLabel statusLabel3;
    private javax.swing.JLabel statusLabel4;
    private javax.swing.JLabel statusLabel5;
    private javax.swing.JLabel statusLabel6;
    private javax.swing.JLabel statusLabel7;
    private javax.swing.JButton stopButton;
    private javax.swing.JLabel systemPressureDisplay;
    private javax.swing.JLabel targetPressureDisplay;
    private javax.swing.JButton unlockDoorButton;
    // End of variables declaration//GEN-END:variables
    
}
