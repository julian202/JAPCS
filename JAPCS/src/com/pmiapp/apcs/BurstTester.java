/*
 * BurstTester.java
 *
 * Created on September 8, 2005, 9:44 AM
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
 * Form that shows while burst test is running
 * Also includes runtime thread that runs the actual test
 * @author Ron V. Webber
 */
public class BurstTester extends javax.swing.JFrame implements GaugeReadListener, AbortQueriable {
    
    /**
     * Creates new form BurstTester
     * @param commSystem Already open communication system
     * @param callingForm form that will be Notified when this form is closing
     * @param userPath path to user directory
     */
    //
        private com.pmiapp.apcs.NewClass newClass;

    
    public BurstTester(APCSCommunication commSystem, Notifiable callingForm, String userPath) {        setIconImage(new ImageIcon(getClass().getResource("/com/pmiapp/apcs/gifs/apcs16.gif")).getImage());

        setIconImage(new ImageIcon(getClass().getResource("/com/pmiapp/apcs/gifs/apcs16.gif")).getImage());
        initComponents();
        pPanel1 = new PlotPanel();
        
        ////////
        /////wtf is this?
        jPanel6.add(pPanel1, java.awt.BorderLayout.CENTER);
        this.callingForm=callingForm;
        this.commSystem=commSystem;
        this.userPath=userPath;
        configSettings=commSystem.getConfigSettings();
        demoLabel.setVisible(commSystem.isDemoMode());
        unlockDoorButton.setVisible(configSettings.doesDoorLockExist());
        pack();
        // center the form on the screen, so we can get the default location values
        configSettings.setWindowPosition((java.awt.Window) this);
        // set display labels so they don't shrink
        targetPressureDisplay.setPreferredSize(targetPressureDisplay.getSize());
        targetPressureDisplay.setText("");
        holdTimeDisplay.setPreferredSize(holdTimeDisplay.getSize());
        holdTimeDisplay.setText("");
        rateDisplay.setPreferredSize(rateDisplay.getSize());
        rateDisplay.setText("");
        estimatedBurstPressureDisplay.setPreferredSize(estimatedBurstPressureDisplay.getSize());
        estimatedBurstPressureDisplay.setText("");
        systemPressureDisplay.setPreferredSize(systemPressureDisplay.getSize());
        systemPressureDisplay.setText("");
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
     */
    public void initialize() {
        gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
        gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
        // this turns on the gauge reader
        gaugeReadThread.start();
        BurstTestSetup bts = new BurstTestSetup(this, userPath, configSettings, testSettings);
        bts.autoRun(); // ask user for sample ID and initialize the test settings properties
        
        if (testSettings.getProperty("datafile","").length()>0) {
            startButton.setEnabled(true);
            dataFileDisplay.setText(testSettings.getProperty("datafile",""));
            estimatedBurstPressureDisplay.setText(testSettings.getProperty("EPT", "error"));
            targetPressureDisplay.setText(testSettings.getProperty("HP", "error"));
            holdTimeDisplay.setText(testSettings.getProperty("HT","error"));
            rateDisplay.setText(testSettings.getProperty("PIIPR", "error"));
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
    }
    
    private double holdingPressure, pressureAccurate;
    private double holdingTime, phase2Rate, burstDropSet;
    private double maximumBurstPressure;
    private long dataStoreIntervalMillis;
    private int phase1MotorSpeed, currentMotorSpeed;
    private GaugeReadThread gaugeReadThread;
    private com.pmiapp.common.Notifiable callingForm;
    private APCSCommunication commSystem;
    private APCSConfig configSettings;
    private PlotPanel pPanel1;
    private javax.swing.Timer swingTimer;
    private double currentPressure;
    private double simulatedPressure;
    private Properties testSettings;
    private String userPath;
    private RunBurstTestThread runBurstTestThread;
    private boolean aborted, closing, hasBurst, safeToOpenDoor, loggingEnabled;
    private boolean autoName;
    private String logFile;
    private java.io.PrintWriter outputFileWriter;
    private java.io.FileWriter fileWriter;
    private double maxPressureSoFar;
    private JFileChooser txtChooser;

    //Added varaible to maintain pressure.
    private long lastTestTime;
    private int maxSpeedLP;
    private boolean stable;
    private double lastPressure;
    private double pressureRange;
    private int maxSpeedHP;
    private double maxSpeed;
    private double currentDesiredMotorSpeed;
    private double nextDesiredMotorSpeed;
    private double dp;
    private double dp0;
    private double targetPressure;
    private double pressureTolerance;
    private int noiseCount;
    private boolean firstTimeThrough;
    private boolean useInitialSpeed;
    private double microControlStartingSpeed;
    private double speedScale;
    private double PSL;
    private double speedFactor;
    private double ramUpSpeed;
    private int slowdownFactor;
    private double defaultSpeed;
    private double slowDownPressure;
    private double ramDownSpeed;
    private double generatorRange;
    private boolean climbing;
    private double maxPressureTime;

    private class RunBurstTestThread extends java.lang.Thread implements GaugeReadListener, AbortQueriable {

        public RunBurstTestThread(GaugeReadListener parent) {
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
                long t = System.currentTimeMillis();
                if (t-lastDataStoreTime>=dataStoreIntervalMillis) {
                    lastDataStoreTime+=dataStoreIntervalMillis;
                    //changed time format to display
                    if (climbing==true)
                    {
                        outputFileWriter.printf("%-9.2f %4.0f%n", (t-testTime0)/1000., currentPressure);
                    }
                    
                }
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

        private void maintainPressure() {
            // maintain the pressure on the current unit
            readPressure();  // this sets currentPressure
            if (aborted) {
                if (currentMotorSpeed!=0) {
                    commSystem.setPGenSpeed(0,0);
                    currentMotorSpeed=0;
                    motorSpeedDisplay.setText("0");
                }
                return;
            }
            // only do this every 0.05 seconds
            //
            if (System.nanoTime()-lastTestTime<50000000) {
                return;
            }
            
            maxSpeed=maxSpeedLP - (currentPressure / pressureRange) * (maxSpeedLP - maxSpeedHP);
            
            if (stable) {
                microControl();
            }
            else {
                coarseControl();
            }
            
            lastPressure=currentPressure;
            dp0=dp;
            currentDesiredMotorSpeed=nextDesiredMotorSpeed;
            if (currentDesiredMotorSpeed>=maxSpeed) {
                currentMotorSpeed=(int)maxSpeed;
            }
            else if (currentDesiredMotorSpeed<=-maxSpeed) {
                currentMotorSpeed=(int)-maxSpeed;
            }
            else {
                currentMotorSpeed=(int)currentDesiredMotorSpeed;
            }
            commSystem.setPGenSpeed(0, currentMotorSpeed);
            motorSpeedDisplay.setText(""+currentMotorSpeed);            
        }
        
        private void microControl() {
            dp=0;
            if ((Math.abs(currentPressure-targetPressure)>pressureTolerance) && (noiseCount<=7)) {
                noiseCount++;
            }
            else {
                noiseCount=0;
            }
            if (noiseCount>5) {
               stable=false;
               firstTimeThrough=true;
               useInitialSpeed=true;
               noiseCount=0;
               return;
            }
            if (Math.abs(currentPressure-targetPressure)<=pressureAccurate) {
                firstTimeThrough=true;
                nextDesiredMotorSpeed=0;
                return;
            }
            if (targetPressure>currentPressure) {
                dp=(currentPressure - lastPressure);
                PSL_Update();
                if (firstTimeThrough) {
                    firstTimeThrough=false;
                    nextDesiredMotorSpeed=microControlStartingSpeed+(currentPressure/10000)*(currentPressure/10000);
                    if ((speedScale>6) || (speedScale<1)) {
                        speedScale=1;
                    }
                }
                if (currentDesiredMotorSpeed < PSL) {
                    nextDesiredMotorSpeed=nextDesiredMotorSpeed+0.1*speedFactor;
                }
            }
            else {
                dp=lastPressure-currentPressure;
                PSL_Update();
                if (firstTimeThrough) {
                    firstTimeThrough=false;
                    nextDesiredMotorSpeed= -microControlStartingSpeed-(currentPressure/10000)*(currentPressure/10000);
                    if ((speedScale>6) || (speedScale<1)) {
                        speedScale=1;
                    }
                }
                if (currentDesiredMotorSpeed > -PSL) {
                    nextDesiredMotorSpeed=nextDesiredMotorSpeed-0.1*speedFactor;
                }                
            }
        }
        
        private void coarseControl() {
            double dt;
            if (targetPressure>currentPressure) {
                dp=currentPressure-lastPressure;
                dt=targetPressure-currentPressure;
                if (useInitialSpeed) {
                    useInitialSpeed=false;
                    nextDesiredMotorSpeed=ramUpSpeed;
                }
                if ((dt<=(3*dp)) && (slowdownFactor==1)) {
                    slowdownFactor=2;
                    nextDesiredMotorSpeed=0.5*nextDesiredMotorSpeed;
                }
                if ((dt<=(2*dp)) && (slowdownFactor==2)) {
                    slowdownFactor=4;
                    nextDesiredMotorSpeed=0.5*nextDesiredMotorSpeed;
                }
                if ((dt<=(1.5*dp)) && (slowdownFactor==4)) {
                    slowdownFactor=8;
                    nextDesiredMotorSpeed=0.5*nextDesiredMotorSpeed;
                }
                speedScale=speedFactor*defaultSpeed/slowdownFactor;
                if ((Math.abs(dt) <= pressureAccurate) || (dt <= 0.9 * dp)) {
                    stable=true;
                    firstTimeThrough=true;
                    nextDesiredMotorSpeed=0;
                    return;
                }
                if (currentPressure>= (1 - 0.05*currentDesiredMotorSpeed/1023)*(targetPressure-slowDownPressure)) {
                    if (firstTimeThrough) {
                        firstTimeThrough=false;
                        nextDesiredMotorSpeed=microControlStartingSpeed+(currentPressure/10000)*(currentPressure/10000); // from old program - don't know what it does
                    }
                    PSL_Update();
                    if (Math.abs(speedScale-dp)<(0.1*speedScale)) {
                        // leave nextDesiredMotorSpeed alone
                    }
                    else if (speedScale>dp) {
                        if (dp>dp0) {
                            if (currentDesiredMotorSpeed<PSL) {
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed+0.5*speedFactor*(speedScale-dp)/speedScale;
                            }
                        }
                        else {
                            if (currentDesiredMotorSpeed<PSL) {
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed+1*speedFactor*(speedScale-dp)/speedScale;
                            }
                        }
                    }
                    else if (currentDesiredMotorSpeed>-PSL) {
                        nextDesiredMotorSpeed=nextDesiredMotorSpeed - 1*speedFactor*(dp-speedScale)/speedScale;
                    }
                }
                else {
                    if (Math.abs(speedScale-dp)<0.1*speedScale) {
                        // leave nextDesiredMotorSpeed along
                    }
                    else if (speedScale>dp) {
                        if (dp>dp0) {
                            if (currentDesiredMotorSpeed < maxSpeed) {
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed+2*speedFactor;
                            }
                        }
                        else if (currentDesiredMotorSpeed < maxSpeed) {
                            nextDesiredMotorSpeed=nextDesiredMotorSpeed+4*speedFactor;
                        }
                    }
                else if (currentDesiredMotorSpeed>-1*maxSpeed) {
                        nextDesiredMotorSpeed=nextDesiredMotorSpeed-4*speedFactor;
                    }
                }
            }
            else { // currentPressure>targetPressure
                dp=lastPressure-currentPressure;
                dt=currentPressure-targetPressure;
                if (useInitialSpeed) {
                    useInitialSpeed=false;
                    nextDesiredMotorSpeed=ramDownSpeed;
                }
                if ((dt<=(3*dp)) && (slowdownFactor==1)) {
                    slowdownFactor=2;
                    nextDesiredMotorSpeed=0.5*nextDesiredMotorSpeed;
                }
                if ((dt<=(2*dp)) && (slowdownFactor==2)) {
                    slowdownFactor=4;
                    nextDesiredMotorSpeed=0.5*nextDesiredMotorSpeed;
                }
                if ((dt<=(1.5*dp)) && (slowdownFactor==4)) {
                    slowdownFactor=8;
                    nextDesiredMotorSpeed=0.5*nextDesiredMotorSpeed;
                }
                speedScale=speedFactor*defaultSpeed/slowdownFactor;
                if ((Math.abs(dt) <= pressureAccurate) || (Math.abs(dt)<=targetPressure/1000) || (dt <= 0.9 * dp)) {
                    stable=true;
                    firstTimeThrough=true;
                    nextDesiredMotorSpeed=0;
                    return;
                }
                if (currentPressure<= (1 + 0.05*currentDesiredMotorSpeed/1023)*(targetPressure+slowDownPressure)) {
                    if (firstTimeThrough) {
                        firstTimeThrough=false;
                        nextDesiredMotorSpeed=(-microControlStartingSpeed)-(currentPressure/10000)*(currentPressure/10000); // from old program - don't know what it does
                    }
                    PSL_Update();
                    if (Math.abs(speedScale-dp)<(0.1*speedScale)) {
                        // leave nextDesiredMotorSpeed alone
                    }
                    else if (speedScale>dp) {
                        if (dp>dp0) {
                            if (currentDesiredMotorSpeed>(-PSL)) {
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed-0.5*speedFactor*(speedScale-dp)/speedScale;
                            }
                        }
                        else {
                            if (currentDesiredMotorSpeed>(-PSL)) {
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed-1*speedFactor*(speedScale-dp)/speedScale;
                            }
                        }
                    }
                    else if (currentDesiredMotorSpeed<PSL) {
                        nextDesiredMotorSpeed=nextDesiredMotorSpeed + 1*speedFactor*(dp-speedScale)/speedScale;
                    }
                }
                else {
                    if (Math.abs(speedScale-dp)<0.1*speedScale) {
                        // leave nextDesiredMotorSpeed along
                    }
                    else if (speedScale>dp) {
                        if (dp>dp0) {
                            if (currentDesiredMotorSpeed > -maxSpeed) {
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed-4*speedFactor;
                            }
                        }
                        else if (currentDesiredMotorSpeed > -maxSpeed) {
                            nextDesiredMotorSpeed=nextDesiredMotorSpeed-8*speedFactor;
                        }
                    }
                else if (currentDesiredMotorSpeed<maxSpeed) {
                        nextDesiredMotorSpeed=nextDesiredMotorSpeed+8*speedFactor;
                    }
                }
                
            }
        }
        
        private void PSL_Update() {
            if (generatorRange<=10000) {
                if (Math.abs(dp-dp0)<1) {
                    PSL=PSL+0.5;
                }
                else if (Math.abs(dp-dp0)>2) {
                    PSL=PSL-1;
                }
            }
            else if (generatorRange<=30000) {
                if (Math.abs(dp-dp0)<3) {
                    PSL=PSL+0.5;
                }
                else if (Math.abs(dp-dp0)>6) {
                    PSL=PSL-2;
                }
            }
            else {
                if (Math.abs(dp-dp0)<5) {
                    PSL=PSL+0.5;
                }
                else if (Math.abs(dp-dp0)>10) {
                    PSL=PSL-5;
                }
            }
        }
        
        @Override
        public void run() {
            long t0;
            speedFactor = 1;
            stable=false;
            useInitialSpeed=true;
            firstTimeThrough=true;
            noiseCount=0;
            nextDesiredMotorSpeed=0;
            currentDesiredMotorSpeed=0;
            slowdownFactor=1;
            lastPressure=currentPressure;
            dp=0;
            dp0=0;
            pressureRange=configSettings.getPressureRange();
            ramUpSpeed=configSettings.getRamUpSpeed();
            ramDownSpeed=configSettings.getRamDownSpeed();
            defaultSpeed=configSettings.getDefaultSpeed();
            slowDownPressure=configSettings.getSlowDownPressure();
            microControlStartingSpeed=configSettings.getMicroControlStartingSpeed();
            PSL=configSettings.getPulseHeight();
            generatorRange=configSettings.getGeneratorRange();
            pressureTolerance=configSettings.getPressureTolerance();
            lastTestTime=System.nanoTime();

            targetPressure = holdingPressure;maxSpeedLP=configSettings.getMaxSpeedLP();
            maxSpeedHP=configSettings.getMaxSpeedHP();
            pressureRange=configSettings.getPressureRange();
            ramUpSpeed=configSettings.getRamUpSpeed();
            ramDownSpeed=configSettings.getRamDownSpeed();
            defaultSpeed=configSettings.getDefaultSpeed();
            slowDownPressure=configSettings.getSlowDownPressure();
            microControlStartingSpeed=configSettings.getMicroControlStartingSpeed();
            PSL=configSettings.getPulseHeight();
            generatorRange=configSettings.getGeneratorRange();
            pressureTolerance=configSettings.getPressureTolerance();
            lastTestTime=System.nanoTime();

            targetPressure = holdingPressure;

            // runs the actual burst test
            // background reading of pressure is still going on
            
            climbing = false;
            
            if (configSettings.getVacuumMotorValve()>=0) {
                statusLabel4.setText("Evacuating Sample...");
                // vacuum on
                commSystem.moveValve(0, 'O');
                // open 4
                commSystem.moveMotorValveAndWait(3, 'O',this);
                // wait 30 seconds - this may be an option later
                if (aborted==false) {
                    try {
                        sleep(30000);
                    } catch(InterruptedException ex) {
                    }
                }
                // close 4
                commSystem.moveMotorValveAndWait(3, 'C',this);
                // vacuum off
                commSystem.moveValve(0, 'C');
            }
            statusLabel4.setText("Filling sample...");
            // open 2
            commSystem.moveMotorValveAndWait(1, 'O',this);
            // if we don't have a vacuum valve, we don't need to wait for filling either
            if (configSettings.getVacuumMotorValve()>=0) {            
                // wait fill time (15 seconds - may be an option later)
                if (aborted==false) {
                    try {
                        sleep(15000);
                    } catch (InterruptedException ex) {
                        
                    }
                }
            }
            // close 1
            statusLabel4.setText("Closing Valve 1...");
            commSystem.moveMotorValveAndWait(0,'C',this);
            targetPressureDisplay.setText(""+holdingPressure);
            statusLabel4.setText("Phase I:  Pressurize the sample to the holding");
            // at this point, we want to shut down the background gauge reader - we will be doing our own
            // pressure gauge reads from now on (until the end of the test)
//            if (gaugeReadThread.isAlive()) {
//                gaugeReadThread.pleaseStop();
//            }
            testTime0=System.currentTimeMillis();
            lastDataStoreTime=testTime0-dataStoreIntervalMillis;
            // don't worry about waiting for the gaugeReadThread to actually stop - it will stop on its own soon enough
            if (aborted==false) {
                //if (phase1MotorSpeed>=1023) {
                if (phase1MotorSpeed!=1023) {
                    statusLabel5.setText("pressure with constant motor speed of "+phase1MotorSpeed+ "......");
                    // set it to 0 to start with so we don't have to wait for it to climb from -1023 to 0
                    // before it starts moving
                    commSystem.setPGenSpeed(0,0);
                    commSystem.setPGenSpeed(0, phase1MotorSpeed);
                    motorSpeedDisplay.setText(""+phase1MotorSpeed);
                    currentMotorSpeed=phase1MotorSpeed;
                    // read pressure gauge and update screen (and set currentPressure) while pressure is below holding pressure
                    do {
                        readPressure();
                    } while ((currentPressure<holdingPressure-pressureAccurate) && (aborted==false));
                    readPressure();  //added 1/23/13                 
                }
                else {
                    statusLabel5.setText("pressure using fast pump......");
                    // start at speed 0 just in case it has some speed already in it
                    commSystem.zeroAout(0);
                    if ((currentPressure < holdingPressure) && (aborted==false)) {
                        double pTarget=holdingPressure;
                        int fastPumpSpeed;
                        if (pTarget<=configSettings.getFastPumpMinPressure()) {
                            fastPumpSpeed=configSettings.getFastPumpMinSpeed();
                        }
                        else if (pTarget>=configSettings.getFastPumpMaxPressure()) {
                            pTarget=configSettings.getFastPumpMaxPressure();
                            fastPumpSpeed=4000;
                        }
                        else {
                            fastPumpSpeed=(int)((pTarget-configSettings.getFastPumpMinPressure())*
                               (4000-configSettings.getFastPumpMinSpeed())/
                               (configSettings.getFastPumpMaxPressure()-configSettings.getFastPumpMinPressure())
                               +configSettings.getFastPumpMinSpeed());
                        }
                        commSystem.moveMotorValveAndWait(2, 'O', this, this); // open valve 3 while still reading pressure
                        commSystem.incAout(0, fastPumpSpeed);
                        t0=System.currentTimeMillis();
                        while ((currentPressure < pTarget) && (aborted==false)) {
                            if ((System.currentTimeMillis()-t0)/1000.>=configSettings.getFastPumpIncrementTime()) {
                                // if we are already at the maximum fast pump speed,
                                // no sense in increasing it.
                                if (fastPumpSpeed>=4000) {
                                    readPressure();  //added 1/23/13 
                                    break;
                                }
                                t0=System.currentTimeMillis();
                                commSystem.incAout(0, 40);
                                fastPumpSpeed+=40;
                            }
                            readPressure();
                        }
                        commSystem.moveMotorValveAndWait(2,'C',this,this); // close valve 3 while still reading pressure
                        commSystem.zeroAout(0); // stop pump
                        readPressure();  //added 1/23/13 
                    }
                    readPressure();  //added 1/23/13 
                    //following if statements, this could also be a point where
                    //the holding pressure has been met, meaning holding time
                    //would also have to appear here conditionally.
                }
                commSystem.setPGenSpeed(0,0);
                currentMotorSpeed=0;
                if (currentPressure > 100) {
                    safeToOpenDoor=false;
                    readPressure();  //added 1/23/13 
                }
            }
            t0=System.currentTimeMillis();
            // read pressure gauge and update screen while waiting for holding time
            double dt;

            do {
                maintainPressure();
                statusLabel4.setText("Keep the sample pressure at "+String.format("%4.0f",currentPressure)+"  PSI");
                dt=(System.currentTimeMillis()-t0)/1000.;
                holdTimeDisplay.setText(String.format("%3.1f", dt));
                statusLabel5.setText("and hold for   "+((int)(holdingTime-dt))+ "  Seconds  ......");
            } while ((holdingTime>dt) && (aborted==false));
            climbing = true;
            statusLabel4.setText("Phase II:   Pressurize the sample with ");
            statusLabel5.setText("constant rate of "+phase2Rate + "  PSI/Sec");
            statusLabel6.setText("and look for the burst pressure,");
            statusLabel7.setText("pressure data is saved on this stage.");
            targetPressureDisplay.setText(""+maximumBurstPressure);
            rateDisplay.setText(""+phase2Rate);
            int currentSpeed=configSettings.getDefaultSpeed();
            t0=System.currentTimeMillis();
            long lastTime=t0;
            double lastPressure=currentPressure;
            double pressureRateCal=0;
            hasBurst=false;
            readPressure();  //added 1/23/13 
            maxPressureSoFar=0;
            if (aborted==false) {
                currentMotorSpeed=configSettings.getDefaultSpeed();
                commSystem.setPGenSpeed(0, currentMotorSpeed);
                motorSpeedDisplay.setText(""+currentMotorSpeed);
            }
            do {
                readPressure();
                if (currentPressure>100) {
                    safeToOpenDoor=false;
                }
                // check forward limit switch
                if (commSystem.getPGenLimit(0)==1) {
                    double startP=currentPressure;
                    long recycleT0=System.currentTimeMillis();
                    int lastMotorSpeed=currentMotorSpeed;
                    statusLabel7.setText("Recharging System - data not being saved");
                    // close valve 2 while reading gauge and updating display
                    // we do this the long way because we are treating the closing of valve 2 as a possible
                    // pressurizing mechanism and therefore need to keep track of the maximum pressure seen so far
                    // and check for bursting
                    if (aborted==false) {
                        commSystem.controlMotor(1,'C');
                    }
                    // wait for it to stop, monitoring pressure
                    while ((commSystem.getMotorStatus(1)!=0) && (aborted==false)) {
                        parent.gaugeReadData(2, commSystem.rawReading(2),0);
                        if (currentPressure > maxPressureSoFar) {
                            maxPressureSoFar=currentPressure;
                            readPressure();  //added 1/23/13 
                        }                
                    }
                    commSystem.controlMotor(1,'S');
                    // compare startP to currentPressure to see if we have burst while closing valve 2
                    // for now, we use a criteria of 1/2 of starting pressure since an expanding or slightly
                    // leaking sample may drop in pressure slightly while we close valve 2 and we don't want
                    // to give a false burst pressure reading
                    if (currentPressure<(startP/2)) {
                        readPressure();  //added 1/23/13 
                        hasBurst=true;
                        break;
                    }
                    commSystem.setPGenSpeed(0,0);
                    commSystem.setPGenSpeed(0,-1023);
                    motorSpeedDisplay.setText("-1023");
                    currentMotorSpeed=-1023;
                    // wait for reverse limit or below 1000 PSI
                    while ((commSystem.getPGenLimit(0)>=0) && (currentPressure>1000) && (aborted==false)) {
                        parent.gaugeReadData(2, commSystem.rawReading(2),0);
                    }
                    // open valve 1
                    commSystem.moveMotorValveAndWait(0,'O',this,parent);
                    // wait for reverse limit
                    while ((commSystem.getPGenLimit(0)>=0) && (aborted==false)) {
                        parent.gaugeReadData(2,commSystem.rawReading(2),0);
                    }
                    // close 1
                    commSystem.moveMotorValveAndWait(0,'C',this,parent);
                    // pressurize back up
                    commSystem.setPGenSpeed(0,0);
                    currentMotorSpeed=0;
                    if (aborted==false) {
                        commSystem.setPGenSpeed(0, 1023);
                    }
                    motorSpeedDisplay.setText("1023");                    
                    while ((aborted==false) && (currentPressure<startP)) {
                        parent.gaugeReadData(2,commSystem.rawReading(2),0);
                    }
                    commSystem.setPGenSpeed(0,0);
                    motorSpeedDisplay.setText("0");
                    // open valve 2
                    commSystem.moveMotorValveAndWait(1,'O',this,parent);
                    // calculate time lost during this recycle
                    recycleT0=System.currentTimeMillis()-recycleT0;
                    // correct all other t0 values-
                    lastDataStoreTime=lastDataStoreTime+recycleT0;
                    lastTime=lastTime+recycleT0;
                    // compare startP to currentPressure to see if we have burst while closing valve 2
                    // for now, we use a criteria of 1/2 of starting pressure since an expanding or slightly
                    // leaking sample may drop in pressure slightly while we close valve 2 and we don't want
                    // to give a false burst pressure reading
                    if (currentPressure<(startP/2)) {
                        hasBurst=true;
                        break;
                    }
                    // if it didn't burst, don't let this pressure cause false burst readings
                    else if (maxPressureSoFar>currentPressure) {
                        maxPressureSoFar=currentPressure;
                    }
                    // put generator back to what it was when we stopped
                    if ((aborted==false) && (hasBurst==false)) {
                        currentMotorSpeed=lastMotorSpeed;
                        commSystem.setPGenSpeed(0, currentMotorSpeed);
                        motorSpeedDisplay.setText(""+currentMotorSpeed);
                    }
                    statusLabel7.setText("pressure data is saved on this stage.");
                }
                // this rate control routine may be exported to a subroutine later
                int maxSpeed=(int)(configSettings.getMaxSpeedLP() 
                    - (currentPressure/configSettings.getPressureRange())
                    *(configSettings.getMaxSpeedLP()-configSettings.getMaxSpeedHP()));
                int minSpeed=configSettings.getDefaultSpeed();
                // only do stuff every 0.1 second or slower
                long currentTime=System.currentTimeMillis();
                dt=(currentTime-lastTime)/1000.;
                //set minimum interval from .1 to .05
                if (dt>=0.05){
                    pressureRateCal=(pressureRateCal + (currentPressure - lastPressure) / dt) / 2.;
                    //changed format string to allow two decimal places.
                    if(climbing = false){
                    holdTimeDisplay.setText(String.format("%4.1f", pressureRateCal));
                    }
                    if(climbing = true){
                    holdTimeDisplay.setText(String.format("%4.1f", 0.00));
                    }
                    
                    lastTime=currentTime;
                    lastPressure=currentPressure;
                    currentSpeed=Math.abs(currentSpeed);
                    if (pressureRateCal < phase2Rate * 0.6) {
                        currentSpeed+=5;
                    }
                    else if (pressureRateCal < phase2Rate * 0.9) {
                        currentSpeed++;
                    }
                    else if (pressureRateCal > 1.5 * phase2Rate) {
                        currentSpeed-=5;
                    }
                    else if (pressureRateCal > phase2Rate * 0.95) {
                        currentSpeed--;
                    }
                    if (currentSpeed > maxSpeed) {
                        currentSpeed=maxSpeed;
                    }
                    if (currentSpeed < minSpeed) {
                        currentSpeed=minSpeed;
                    }
                    // this next line shouldn't happen during burst testing
                    if (currentPressure > maximumBurstPressure) {
                        currentSpeed=0-currentSpeed;
                    }
                    motorSpeedDisplay.setText(""+currentSpeed);
                    commSystem.setPGenSpeed(0, currentSpeed);
                    currentMotorSpeed=currentSpeed;
                }
                // look for burst
                if (currentPressure > maxPressureSoFar) {
                    maxPressureSoFar=currentPressure;
                    maxPressureTime=(System.currentTimeMillis()-testTime0)/1000.;
                    outputFileWriter.printf("%-9.2f %4.0f%n", maxPressureTime, currentPressure);
                }
                if ((maxPressureSoFar - currentPressure) > burstDropSet) {
                    
                    readPressure();
                    hasBurst=true;
                    commSystem.moveValve(1, 'C');
                    break;
                }
            } while ((currentPressure < maximumBurstPressure) && (aborted==false));
            commSystem.setPGenSpeed(0, 0);
            motorSpeedDisplay.setText("0");
            currentMotorSpeed=0;
            if (hasBurst) {
                statusLabel4.setText("Sample burst at "+String.format("%4.0f", maxPressureSoFar));
                statusLabel5.setText("Test is done!");
                statusLabel6.setText("");
                statusLabel7.setText("");
                outputFileWriter.printf("%-9.1f %4.0f%n", (System.currentTimeMillis()-testTime0)/1000., currentPressure);
                outputFileWriter.println();
                outputFileWriter.printf( "BURST TIME:" + String.format("%4.2f", maxPressureTime) + " - SAMPLE BURST AT %4.0f PSI%n", maxPressureSoFar);
                
                // if it does, this is a real full burst and we need to close valve 2
                // if it doesn't, this is not enough of a burst that we want to close valve 2// wait for up to 5 seconds for the pressure to drop below 100 PSI
                // if it does, this is a real full burst and we need to close valve 2
                // if it doesn't, this is not enough of a burst that we want to close valve 2
                t0=System.currentTimeMillis();
                hasBurst=false; // in case it didn't really burst
                while ((System.currentTimeMillis()-t0)<5000) {
                    // read current pressure without saving the results anywhere
                    parent.gaugeReadData(2, commSystem.rawReading(2),0);
                    if (currentPressure<100) {
                        hasBurst=true; // it is a real burst
                        climbing = false;
                        break;
                    }
                }
            }
            else if (aborted) {
                statusLabel4.setText("Test Aborted");
                statusLabel5.setText("");
                statusLabel6.setText("");
                statusLabel7.setText("");
            }
            else {
                statusLabel4.setText("System Pressure exceeds the maximum pressure");
                statusLabel5.setText("setting.  Test is teminated!");
                statusLabel6.setText("");
                statusLabel7.setText("");
                //JOptionPane.showMessageDialog(rootPane,"Maximum pressure is reached and test is stopped");
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
            // if sample really burst, close valve 2 first
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
                commSystem.moveMotorValveAndWait(1,'C',this);
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
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        targetPressureDisplay = new javax.swing.JLabel();
        holdTimeDisplay = new javax.swing.JLabel();
        rateDisplay = new javax.swing.JLabel();
        estimatedBurstPressureDisplay = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        systemPressureDisplay = new javax.swing.JLabel();
        motorSpeedDisplay = new javax.swing.JLabel();
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

        demoLabel.setFont(new java.awt.Font("SansSerif", 0, 18));
        demoLabel.setForeground(new java.awt.Color(192, 0, 0));
        demoLabel.setText("Demo Mode");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        getContentPane().add(demoLabel, gridBagConstraints);

        jLabel2.setFont(new java.awt.Font("Serif", 1, 18));
        jLabel2.setForeground(java.awt.Color.red);
        jLabel2.setText("Mode 4: Pressure Burst Test");
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

        jLabel4.setText("Hold Time (Seconds)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel2.add(jLabel4, gridBagConstraints);

        jLabel5.setText("Rate (PSI/Second)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel2.add(jLabel5, gridBagConstraints);

        jLabel6.setText("Estimated Burst Pressure (PSI)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel2.add(jLabel6, gridBagConstraints);

        targetPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        targetPressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12));
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

        holdTimeDisplay.setBackground(new java.awt.Color(0, 0, 0));
        holdTimeDisplay.setFont(new java.awt.Font("Monospaced", 1, 12));
        holdTimeDisplay.setForeground(new java.awt.Color(0, 255, 255));
        holdTimeDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        holdTimeDisplay.setText("60,000.00");
        holdTimeDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        holdTimeDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel2.add(holdTimeDisplay, gridBagConstraints);

        rateDisplay.setBackground(new java.awt.Color(0, 0, 0));
        rateDisplay.setFont(new java.awt.Font("Monospaced", 1, 12));
        rateDisplay.setForeground(new java.awt.Color(0, 255, 255));
        rateDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        rateDisplay.setText("60,000.00");
        rateDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        rateDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel2.add(rateDisplay, gridBagConstraints);

        estimatedBurstPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        estimatedBurstPressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12));
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
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel3.add(jLabel8, gridBagConstraints);

        systemPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        systemPressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12));
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
        motorSpeedDisplay.setFont(new java.awt.Font("Monospaced", 1, 12));
        motorSpeedDisplay.setForeground(new java.awt.Color(255, 255, 0));
        motorSpeedDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        motorSpeedDisplay.setText("60,000.00");
        motorSpeedDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        motorSpeedDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel3.add(motorSpeedDisplay, gridBagConstraints);

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

        exitMenuItem.setText("Exit Pressure Burst Test");
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
        if (runBurstTestThread!=null) {
            if (runBurstTestThread.isAlive()) {
                runBurstTestThread.interrupt();
            }
        }

    }//GEN-LAST:event_stopButtonActionPerformed

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        // load variables from testSettings
        try {holdingPressure = Double.parseDouble(testSettings.getProperty("HP")); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Holding Pressure");
            return;
        }
        try {phase1MotorSpeed = Integer.parseInt(testSettings.getProperty("PIPMS")); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Phase I Motor Speed");
            return;
        }
        try {holdingTime = Double.parseDouble(testSettings.getProperty("HT")); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Holding Time");
            return;
        }
        try {phase2Rate = Double.parseDouble(testSettings.getProperty("PIIPR")); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Phase II Pressurizing Rate");
            return;
        }
        try {burstDropSet = Double.parseDouble(testSettings.getProperty("BurstDropset")); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Burst P.Drop Judgement");
            return;
        }
        try {maximumBurstPressure = Double.parseDouble(testSettings.getProperty("MBP")); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Maximum Burst Pressure");
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
        // check door switch AW TEST
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
        outputFileWriter.println("BURST PRESSURE TEST");
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
        outputFileWriter.print("                Holding Pressure:      ");
        outputFileWriter.print(testSettings.getProperty("HP"));
        outputFileWriter.println(" PSI");
        outputFileWriter.print("                    Holding Time:      ");
        outputFileWriter.print(testSettings.getProperty("HT"));
        outputFileWriter.println(" seconds");
        outputFileWriter.print("Phase I pressurizing motor speed:      ");
        outputFileWriter.println(testSettings.getProperty("PIPMS"));
        outputFileWriter.print("      Phase II pressuziring rate:      ");
        outputFileWriter.print(testSettings.getProperty("PIIPR"));
        outputFileWriter.println(" PSI/Sec");
        outputFileWriter.print("            Data saving interval:      ");
        outputFileWriter.print(testSettings.getProperty("DSI"));
        outputFileWriter.println(" seconds");
        outputFileWriter.print("          Maximum burst pressure:      ");
        outputFileWriter.print(testSettings.getProperty("MBP"));
        outputFileWriter.println(" PSI");
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
        ResetSystem rs = new ResetSystem(this, this, commSystem, 0, safeToOpenDoor);
        rs.initialize();
        rs.setVisible(true);
        if (rs.wasAborted()) {
            statusLabel4.setText("Initial Setup Aborted");
            testFinished(false);
        }
        else {
            // start a second thread to actually control what happens during testing
            runBurstTestThread = new RunBurstTestThread(this);
            runBurstTestThread.start();
        }
    }//GEN-LAST:event_startButtonActionPerformed

    private void setupButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setupButtonActionPerformed
        BurstTestSetup bts = new BurstTestSetup(this, userPath, configSettings, testSettings);
        bts.setVisible(true);
        if (testSettings.getProperty("datafile","").length()>0) {
            startButton.setEnabled(true);
            dataFileDisplay.setText(testSettings.getProperty("datafile",""));
            estimatedBurstPressureDisplay.setText(testSettings.getProperty("EPT", "error"));
            targetPressureDisplay.setText(testSettings.getProperty("HP", "error"));
            holdTimeDisplay.setText(testSettings.getProperty("HT","error"));
            rateDisplay.setText(testSettings.getProperty("PIIPR", "error"));
        }
    }//GEN-LAST:event_setupButtonActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        exitForm(null);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
        // only call this if the background task is finished
        swingTimer.stop();
        if (runBurstTestThread!=null) {
            if (runBurstTestThread.isAlive()) {
                aborted=true;
                closing=true;
                // interrupt any possible sleep condition in the thread
                runBurstTestThread.interrupt();
                // join the thread until it finishes properly
                try { runBurstTestThread.join(); }
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
        switch (channel) {
            //case -1: // limit switch
            //    break;
            case 2: // main pressure gauge
                if (commSystem.isDemoMode()) {
                    currentPressure=simulatedPressure;
                } else {
                    currentPressure=configSettings.getGaugeChannel(2).getUser(configSettings.getGaugeChannel(2).getReal(countValue));
                }
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
        
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel dataFileDisplay;
    private javax.swing.JLabel demoLabel;
    private javax.swing.JLabel estimatedBurstPressureDisplay;
    private javax.swing.JMenu exitMenu;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JLabel holdTimeDisplay;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
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
    private javax.swing.JLabel rateDisplay;
    private javax.swing.JMenuItem reportMenuItem;
    private javax.swing.JMenuItem resetMenuItem;
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
