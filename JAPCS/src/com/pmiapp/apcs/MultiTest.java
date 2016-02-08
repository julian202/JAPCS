/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * MultiTest.java
 *
 * Created on Feb 25, 2009, 8:17:05 PM
 */

package com.pmiapp.apcs;
import com.pmiapp.common.AbortQueriable;
import com.pmiapp.common.GaugeReadListener;
import com.pmiapp.common.Notifiable;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Date;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * Form that shows while multi test is running
 * Also includes runtime thread that runs the actual test
 * @author Ron V. Webber
 */
public class MultiTest extends javax.swing.JFrame implements GaugeReadListener, AbortQueriable {

    /**
     * Creates new form MultiTest
     * @param commSystem Already open communication system
     * @param callingForm form that will be Notified when this form is closing
     * @param userPath path to user directory
     */
    public MultiTest(APCSCommunication commSystem, Notifiable callingForm, String userPath) {
        setIconImage(new ImageIcon(getClass().getResource("/com/pmiapp/apcs/gifs/apcs16.gif")).getImage());
        initComponents();
        pPanel1 = new PlotPanel();
        plotPanelSubHolder.add(pPanel1, java.awt.BorderLayout.CENTER);
        this.callingForm=callingForm;
        this.commSystem=commSystem;
        this.userPath=userPath;
        configSettings=commSystem.getConfigSettings();
        demoLabel.setVisible(commSystem.isDemoMode());
        if (configSettings.getFastPumpAnalogOutput()!=-1) {
            fastPumpCheckBox.setSelected(configSettings.areUsingFastPump());            
        } else {
            fastPumpPanel.setVisible(false);
        }
        stopButton.setEnabled(false);
        targetPressureDisplay.setPreferredSize(targetPressureDisplay.getSize());
        targetPressureDisplay.setText("");
        holdingTimeDisplay.setPreferredSize(holdingTimeDisplay.getSize());
        holdingTimeDisplay.setText("");
        systemPressureDisplay.setPreferredSize(systemPressureDisplay.getSize());
        systemPressureDisplay.setText("");
        motorSpeedDisplay.setPreferredSize(motorSpeedDisplay.getSize());
        motorSpeedDisplay.setText("");
        speedFactorDisplay.setPreferredSize(speedFactorDisplay.getSize());
        speedFactor=1;
        speedFactorDisplay.setText("1");
        motorSpeedDisplay.setText("0");
        // don't support a second pressure gauge yet
        // In vb6 code this is for dual pressure gauge systems (for calibration)
        // or systems with a Ruska in it - neither of which are supported here yet
        secondPressureFrame.setVisible(false);
        closing=false;
        currentPressure=0;
        simulatedPressure=0;
        currentMotorSpeed=0;
        pressureList=null;
        timeList=null;
        dataSavingInterval=0;
        fileName="";
        testID="";
        aborted=false;

        txtChooser = new JFileChooser();
        txtChooser.addChoosableFileFilter(new com.pmiapp.common.TxtFilter());
        txtChooser.setCurrentDirectory(new java.io.File(userPath,"data"));

        pack();
        // center the form on the screen, so we can get the default location values
        configSettings.setWindowPosition((java.awt.Window)this);
        secondsNumberFormat = NumberFormat.getNumberInstance();
        secondsNumberFormat.setMaximumFractionDigits(1);
        secondsNumberFormat.setMinimumFractionDigits(1);
    }
    
    /**
     * Start the gauge reading thread and the graphics updater
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

    private RunMultiTestThread runMultiTestThread;
    private JFileChooser txtChooser;
    //private File testSettingsFile;
    //private Properties testSettings;
    private javax.swing.Timer swingTimer;
    private boolean aborted;
    private double currentPressure;
    private double currentTemperature;
    private double simulatedPressure;
    private int currentMotorSpeed;
    private GaugeReadThread gaugeReadThread;
    private com.pmiapp.common.Notifiable callingForm;
    private APCSCommunication commSystem;
    private APCSConfig configSettings;
    private PlotPanel pPanel1;
    private String userPath;
    private boolean closing;
    private double currentTemp;
    private long testStartTime;
    private java.io.PrintWriter outputFileWriter;
    private java.io.FileWriter fileWriter;
    private boolean outputFileHasBeenSet;
    private float[] pressureList, timeList, tempList;
    private float dataSavingInterval;
    private String fileName, testID;
    private double speedFactor;
    private NumberFormat secondsNumberFormat;
    private int encoderValue;
    private int volume;

    private class RunMultiTestThread extends java.lang.Thread implements GaugeReadListener, AbortQueriable {
        public RunMultiTestThread(GaugeReadListener parent) {
            this.parent=parent;
        }

        private GaugeReadListener parent;
        private long lastTestTime, lastDataStoreTime, dataStoreIntervalMillis;
        private long testTime0;
        private boolean stable, firstTimeThrough, useInitialSpeed;
        private int noiseCount, slowdownFactor;
        private double nextDesiredMotorSpeed, currentDesiredMotorSpeed, lastPressure, dp, dp0, maxSpeed;
        private double maxSpeedLP, maxSpeedHP, pressureRange, ramUpSpeed, ramDownSpeed, defaultSpeed;
        private double slowDownPressure, microControlStartingSpeed, generatorRange, pressureTolerance;
        private double speedScale, PSL;
        private double targetPressure, pressureAccurate, targetTemperature;
        private boolean useVolume = configSettings.hasVolume();
        private boolean firstWrite = true;
        
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
                    if (outputFileHasBeenSet){
                        if (useVolume){
                            if (firstWrite) {
                                outputFileWriter.printf("%n%-9s\t%4s\t%7s\t%9s%n","Time", "PSI", "Temp", "Volume(cc)");
                                firstWrite=false;
                            }
                            outputFileWriter.printf("%-9d\t%4.0f\t%7.2f\t%9d%n", (t-testTime0)/1000, currentPressure, currentTemp, volume);
                        }
                        else {
                            if (firstWrite) {
                                outputFileWriter.printf("%n%-9s\t%4s\t%7s%n","Time", "PSI", "Temp");
                                firstWrite=false;
                            }
                            outputFileWriter.printf("%-9d\t%4.0f\t%7.2f%n", (t-testTime0)/1000, currentPressure, currentTemp);
                        }
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

            
            gaugeReadData(2, commSystem.rawReading(2),0); //writes to a file: temp, pressure and volume.
            currentTemp = commSystem.readAthenaTemp() / 10;
            systemTempText.setText(""+currentTemp+" F");
            if (useVolume){
                //read volume from encoder and multiply it by an encoder factor:          
                encoderValue = commSystem.readEncoder(1);
                volume= encoderValue * (int)configSettings.getEncoderFactor();
                volumeText.setText(""+volume+"");

            }          
        }
       
        private void microControl() {
            dp=0;
            if ((Math.abs(currentPressure-targetPressure)>pressureTolerance) && (noiseCount<=7))
                noiseCount++;
            else noiseCount=0;
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
                dp=currentPressure-lastPressure;
                PSL_Update();
                if (firstTimeThrough) {
                    firstTimeThrough=false;
                    nextDesiredMotorSpeed=microControlStartingSpeed+(currentPressure/10000)*(currentPressure/10000);
                    if ((speedScale>6) || (speedScale<1)) speedScale=1;
                }
                if (currentDesiredMotorSpeed < PSL) 
                    nextDesiredMotorSpeed=nextDesiredMotorSpeed+0.1*speedFactor;
            }
            else {
                dp=lastPressure-currentPressure;
                PSL_Update();
                if (firstTimeThrough) {
                    firstTimeThrough=false;
                    nextDesiredMotorSpeed= -microControlStartingSpeed-(currentPressure/10000)*(currentPressure/10000);
                    if ((speedScale>6) || (speedScale<1)) speedScale=1;
                }
                if (currentDesiredMotorSpeed > -PSL) 
                    nextDesiredMotorSpeed=nextDesiredMotorSpeed-0.1*speedFactor;
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
                            if (currentDesiredMotorSpeed<PSL)
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed+0.5*speedFactor*(speedScale-dp)/speedScale;
                        }
                        else {
                            if (currentDesiredMotorSpeed<PSL)
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed+1*speedFactor*(speedScale-dp)/speedScale;
                        }
                    }
                    else if (currentDesiredMotorSpeed>-PSL)
                        nextDesiredMotorSpeed=nextDesiredMotorSpeed - 1*speedFactor*(dp-speedScale)/speedScale;
                }
                else {
                    if (Math.abs(speedScale-dp)<0.1*speedScale) {
                        // leave nextDesiredMotorSpeed along
                    }
                    else if (speedScale>dp) {
                        if (dp>dp0) {
                            if (currentDesiredMotorSpeed < maxSpeed)
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed+2*speedFactor;
                        }
                        else if (currentDesiredMotorSpeed < maxSpeed)
                            nextDesiredMotorSpeed=nextDesiredMotorSpeed+4*speedFactor;
                    }
                else if (currentDesiredMotorSpeed>-1*maxSpeed)
                    nextDesiredMotorSpeed=nextDesiredMotorSpeed-4*speedFactor;
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
                            if (currentDesiredMotorSpeed>(-PSL))
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed-0.5*speedFactor*(speedScale-dp)/speedScale;
                        }
                        else {
                            if (currentDesiredMotorSpeed>(-PSL))
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed-1*speedFactor*(speedScale-dp)/speedScale;
                        }
                    }
                    else if (currentDesiredMotorSpeed<PSL)
                        nextDesiredMotorSpeed=nextDesiredMotorSpeed + 1*speedFactor*(dp-speedScale)/speedScale;
                }
                else {
                    if (Math.abs(speedScale-dp)<0.1*speedScale) {
                        // leave nextDesiredMotorSpeed along
                    }
                    else if (speedScale>dp) {
                        if (dp>dp0) {
                            if (currentDesiredMotorSpeed > -maxSpeed)
                                nextDesiredMotorSpeed=nextDesiredMotorSpeed-4*speedFactor;
                        }
                        else if (currentDesiredMotorSpeed > -maxSpeed)
                            nextDesiredMotorSpeed=nextDesiredMotorSpeed-8*speedFactor;
                    }
                else if (currentDesiredMotorSpeed<maxSpeed)
                    nextDesiredMotorSpeed=nextDesiredMotorSpeed+8*speedFactor;
                }
            }
        }
        
        private void PSL_Update() {
            if (generatorRange<=10000) {
                if (Math.abs(dp-dp0)<1) PSL=PSL+0.5;
                else if (Math.abs(dp-dp0)>2)PSL=PSL-1;
            }
            else if (generatorRange<=30000) {
                if (Math.abs(dp-dp0)<3) PSL=PSL+0.5;
                else if (Math.abs(dp-dp0)>6) PSL=PSL-2;
            }
            else {
                if (Math.abs(dp-dp0)<5) PSL=PSL+0.5;
                else if (Math.abs(dp-dp0)>10) PSL=PSL-5;
            }
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
            // only do this every 0.1 seconds
            if (System.currentTimeMillis()-lastTestTime<1000) return;
            maxSpeed=maxSpeedLP - (currentPressure / pressureRange) * (maxSpeedLP - maxSpeedHP);
            if (stable) microControl();
            else coarseControl();
            lastPressure=currentPressure;
            dp0=dp;
            currentDesiredMotorSpeed=nextDesiredMotorSpeed;
            if (currentDesiredMotorSpeed>=maxSpeed)
                currentMotorSpeed=(int)maxSpeed;
            else if (currentDesiredMotorSpeed<=-maxSpeed)
                currentMotorSpeed=(int)-maxSpeed;
            else
                currentMotorSpeed=(int)currentDesiredMotorSpeed;
            commSystem.setPGenSpeed(0, currentMotorSpeed);
            motorSpeedDisplay.setText(""+currentMotorSpeed);
        }

       @Override
        public void run() {
            
            
            // runs the actual multi test
            // background reading of pressure is still going on
            testStartTime=System.currentTimeMillis();
            boolean usingFastPump = fastPumpCheckBox.isSelected();
            int fastPumpValve = configSettings.getFastPumpMotorValve();
            int fastPumpX1 = configSettings.getFastPumpMinSpeed();
            int fastPumpX2 = configSettings.getFastPumpMaxSpeed();
            int fastPumpAout = configSettings.getFastPumpAnalogOutput();
            double fastPumpY1 = configSettings.getFastPumpMinPressure();
            double fastPumpY2 = configSettings.getFastPumpMaxPressure();
            boolean fastPumpConnected=false;
            boolean athena = configSettings.hasAthena();
            
            
            fastPumpCheckBox.setEnabled(false);
            if (usingFastPump) {
                commSystem.zeroAout(fastPumpAout);
                // open 1
                commSystem.moveMotorValveAndWait(0,'O',this);
                if (aborted) {
                    testFinished(false);
                    return;
                }
                // generator full forward
                commSystem.setPGenSpeed(0,0); // stop it first, just in case it was going in reverse
                commSystem.setPGenSpeed(0,1023);
                motorSpeedDisplay.setText("1023");
                // wait for aborted or forward limit switch
                while ((!aborted) && (commSystem.getPGenLimit(0)!=1)) {}
                if (aborted) {
                    testFinished(false);
                    return;
                }
            }
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

            if (usingFastPump && (fastPumpValve>1)) {
                // open 3
                commSystem.moveMotorValveAndWait(fastPumpValve, 'O',this);

                if (aborted) {
                    testFinished(false);
                    return;
                }
                fastPumpConnected=true;
            }

            // at this point, we want to shut down the background gauge reader - we will be doing our own
            // pressure gauge reads from now on (until the end of the test)
            if (gaugeReadThread.isAlive()) gaugeReadThread.pleaseStop();
            // don't worry about waiting for the gaugeReadThread to actually stop - it will stop on its own soon enough
            int state=0;
            int listIndex=0;
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
            maxSpeedLP=configSettings.getMaxSpeedLP();
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
            pressureAccurate=configSettings.getPressureAccurate();
            lastTestTime=System.currentTimeMillis();
            long waitStartTime=lastTestTime;
            long stableStartTime=lastTestTime;
            dataStoreIntervalMillis=(long)(dataSavingInterval * 1000);
            lastDataStoreTime=lastTestTime-dataStoreIntervalMillis;
            testTime0=lastTestTime;
            while (aborted==false) {
                readPressure();
                if (state==0) {
                    // state 0: set everything up
                    targetPressure=pressureList[listIndex];
                    targetTemperature = tempList[listIndex];
                    targetPressureDisplay.setText(""+targetPressure);
                    holdingTimeDisplay.setText(secondsNumberFormat.format(0.0));
                    targetTempText.setText(""+targetTemperature + " F");
                    
                    state=1;
                    stableStartTime=System.currentTimeMillis();
                } else if (state==1) {
                    // state 1: Reach pressure
                    
                    if(athena)
                    {
                        currentTemp = commSystem.readAthenaTemp() / 10;

                        systemTempText.setText(""+currentTemp+" F");
                        int targetVal = (int)targetTemperature;
                        double targetVal2 = targetVal * 10;
                        commSystem.setAthenaTemp(1, String.format("%04d", (int)targetVal2));

                        while (currentTemp < targetTemperature)
                        {
                            currentTemp = commSystem.readAthenaTemp() / 10;
                            systemTempText.setText(""+currentTemp+" F");
                            maintainPressure();
                        }
                    }
                    
                    if ((!fastPumpConnected) && usingFastPump && (targetPressure<=fastPumpY2)) {
                        commSystem.setPGenSpeed(0,0);
                        motorSpeedDisplay.setText("0");
                        commSystem.controlMotor(fastPumpValve, 'O');
                        while ((!aborted) && (commSystem.getMotorStatus(fastPumpValve)!=0)) readPressure();
                        commSystem.controlMotor(fastPumpValve, 'S');
                        fastPumpConnected=true;
                    }
                    if (fastPumpConnected && usingFastPump && (targetPressure<=fastPumpY2)) {
                        if (targetPressure<=fastPumpY1)
                            commSystem.setAout(fastPumpAout, fastPumpX1);
                        else {
                            commSystem.setAout(fastPumpAout, (int)(
                                    (targetPressure - fastPumpY1) *
                                    (fastPumpX2 - fastPumpX1) /
                                    (fastPumpY2 - fastPumpY1) + fastPumpX1));
                        }
                        state=2;
                        waitStartTime=System.currentTimeMillis();
                    } else {
                        if (usingFastPump && fastPumpConnected) {
                            commSystem.setAout(fastPumpAout, fastPumpX2);
                            commSystem.setPGenSpeed(0,0); // stop it first just in case
                            commSystem.setPGenSpeed(0, -1023);
                            motorSpeedDisplay.setText("-1023");
                            while ((!aborted) && (commSystem.getPGenLimit(0)!= -1)) readPressure();
                            commSystem.controlMotor(fastPumpValve, 'C');
                            while ((!aborted) && (commSystem.getMotorStatus(fastPumpValve)!=0)) readPressure();
                            commSystem.setPGenSpeed(0,0);
                            motorSpeedDisplay.setText("0");
                            commSystem.controlMotor(fastPumpValve, 'S');
                            fastPumpConnected=false;
                            stableStartTime=System.currentTimeMillis();
                        }
                        maintainPressure();
                        int limit=commSystem.getPGenLimit(0);
                        if ((limit==-1) && (currentMotorSpeed<0)) {
                            // remember the current pressure
                            double stallingPressure=currentPressure;
                            // stop generator (it should already be stopped by the limit switch)
                            commSystem.setPGenSpeed(0,0);
                            motorSpeedDisplay.setText("0");
                            // start valve 2 closing
                            commSystem.controlMotor(1, 'C');
                            // wait for it to stop, while reading the current pressure
                            // for now, you can't abort this
                            while ((!aborted) && (commSystem.getMotorStatus(1)!=0)) readPressure();
                            commSystem.controlMotor(1,'S');
                            // start valve 1 or 3 opening
                            if (usingFastPump) {
                                if (!aborted) commSystem.controlMotor(fastPumpValve, 'O');
                                // wait for valve 3 to stop
                                while ((!aborted) && (commSystem.getMotorStatus(fastPumpValve)!=0)) readPressure();
                                commSystem.controlMotor(fastPumpValve, 'S');
                            } else {
                                if (!aborted) commSystem.controlMotor(0, 'O');
                                // wait for valve 1 to stop
                                while ((!aborted) && (commSystem.getMotorStatus(0)!=0)) readPressure();
                                commSystem.controlMotor(0,'S');
                            }
                            // start generator running at full speed
                            if (!aborted) {
                                commSystem.setPGenSpeed(0,1023);
                                motorSpeedDisplay.setText("1023");
                            }
                            long refillT0=System.currentTimeMillis();
                            // wait 90 seconds
                            while ((!aborted) && (System.currentTimeMillis()-refillT0<90000)) readPressure();
                            // stop motor
                            commSystem.setPGenSpeed(0,0);
                            motorSpeedDisplay.setText("0");
                            if (usingFastPump) {
                                // start valve 3 closing
                                if (!aborted) commSystem.controlMotor(fastPumpValve, 'C');
                                // wait for valve 3 to stop
                                while ((!aborted) && (commSystem.getMotorStatus(fastPumpValve)!=0)) readPressure();
                                commSystem.controlMotor(fastPumpValve, 'S');
                            } else {
                                // start valve 1 closing
                                if (!aborted) commSystem.controlMotor(0, 'C');
                                // wait for valve 1 to stop
                                while ((!aborted) && (commSystem.getMotorStatus(0)!=0)) readPressure();
                                commSystem.controlMotor(0,'S');
                            }
                            // start generator running at full speed
                            if (!aborted) {
                                commSystem.setPGenSpeed(0,1023);
                                motorSpeedDisplay.setText("1023");
                            }
                            // wait for pressure to be >= stalling pressure
                            do {
                                readPressure();
                            } while ((!aborted) && (currentPressure<stallingPressure) && (commSystem.getPGenLimit(0)!=1));
                            // stop generator
                            commSystem.setPGenSpeed(0,0);
                            motorSpeedDisplay.setText("0");
                            // start valve 2 opening
                            if (!aborted) commSystem.controlMotor(1, 'O');
                            // wait for valve 2 to stop
                            while ((!aborted) && (commSystem.getMotorStatus(1)!=0)) readPressure();
                            commSystem.controlMotor(1,'S');
                            // all done recycling pressure generator
                            // we now return to normal loop
                            stableStartTime=System.currentTimeMillis();
                        } else if ((limit==1) && (currentMotorSpeed>0)) {
                            // can't maintain pressure here, so we just need to seal things off
                            // remember the current pressure
                            double stallingPressure=currentPressure;
                            // stop generator (it should already be stopped by the limit switch)
                            commSystem.setPGenSpeed(0,0);
                            motorSpeedDisplay.setText("0");
                            // start valve 2 closing
                            if (!aborted) commSystem.controlMotor(1, 'C');
                            // wait for it to stop, while reading the current pressure
                            // for now, you can't abort this
                            while ((!aborted) && (commSystem.getMotorStatus(1)!=0)) readPressure();
                            commSystem.controlMotor(1,'S');
                            // start generator moving in full reverse
                            if (!aborted) {
                                commSystem.setPGenSpeed(0,-1023);
                                motorSpeedDisplay.setText("-1023");
                            }
                            if (usingFastPump) {
                                // wait for reverse limit switch or pressure less than fast pump max
                                while ((!aborted) && (commSystem.getPGenLimit(0)!=-1)) {
                                    readPressure();
                                    if (currentPressure<fastPumpY2) break;
                                }
                                // start valve 3 opening
                                if (!aborted) commSystem.controlMotor(fastPumpValve, 'O');
                            } else {
                                // wait for reverse limit switch or pressure less than 100
                                while ((!aborted) && (commSystem.getPGenLimit(0)!=-1)) {
                                    readPressure();
                                    if (currentPressure<100) break;
                                }
                                // start valve 1 opening
                                if (!aborted) commSystem.controlMotor(0, 'O');
                            }
                            // wait for reverse limit switch
                            while ((!aborted) && (commSystem.getPGenLimit(0)!=-1)) readPressure();
                            if (usingFastPump) {
                                // wait for valve 3 to stop
                                while ((!aborted) && (commSystem.getMotorStatus(fastPumpValve)!=0)) readPressure();
                                // start valve 3 closing
                                if (!aborted) commSystem.controlMotor(fastPumpValve, 'C');
                                // wait for valve 3 to stop
                                while ((!aborted) && (commSystem.getMotorStatus(fastPumpValve)!=0)) readPressure();
                                commSystem.controlMotor(fastPumpValve, 'S');
                            } else {
                                // wait for valve 1 to stop
                                while ((!aborted) && (commSystem.getMotorStatus(0)!=0)) readPressure();
                                // start valve 1 closing
                                if (!aborted) commSystem.controlMotor(0, 'C');
                                // wait for valve 1 to stop
                                while ((!aborted) && (commSystem.getMotorStatus(0)!=0)) readPressure();
                                commSystem.controlMotor(0,'S');
                            }
                            commSystem.setPGenSpeed(0,0);  // stop first so it goes to full speed faster
                            // start generator running at full speed
                            if (!aborted) {
                                commSystem.setPGenSpeed(0,1023);
                                motorSpeedDisplay.setText("1023");
                            }
                            // wait for pressure to be >= stalling pressure
                            do {
                                readPressure();
                            } while ((!aborted) && (currentPressure<stallingPressure) && (commSystem.getPGenLimit(0)!=1));
                            // stop generator
                            commSystem.setPGenSpeed(0,0);
                            motorSpeedDisplay.setText("0");
                            // start valve 2 opening
                            if (!aborted) commSystem.controlMotor(1, 'O');
                            // wait for valve 2 to stop
                            while ((!aborted) && (commSystem.getMotorStatus(1)!=0)) readPressure();
                            commSystem.controlMotor(1,'S');
                            // all done recycling pressure generator
                            // we now return to normal loop
                            stableStartTime=System.currentTimeMillis();
                        }
                        if (stable) {
                            if ((System.currentTimeMillis()-stableStartTime)>2000) {
                                state=2;
                                waitStartTime=System.currentTimeMillis();
                            }
                        } else {
                            stableStartTime=System.currentTimeMillis();
                        }
                    }
                } else if (state==2) {
                    // state 2: Hold pressure for time
                    if (!fastPumpConnected) maintainPressure();
                    long elapsed=System.currentTimeMillis()-waitStartTime;
                    double remaining=timeList[listIndex]-(elapsed / 1000.);
                    if (remaining>0)
                        holdingTimeDisplay.setText(secondsNumberFormat.format(remaining));
                    else
                        state=3;
                } else {
                    // state 3: go to next pressure or quit
                    holdingTimeDisplay.setText(secondsNumberFormat.format(0.0));
                    stable=false;
                    state=0;
                    listIndex++;
                    if (listIndex>=pressureList.length) break;
                }
                // the following calculations were never used.  They may be used
                // some day, so they are still here
                //long t=(System.currentTimeMillis()-testStartTime)/1000;
                //long a=t/3600;
                //t=t-a*3600;
                //long b=t/60;
                //t=t-b*60;
            }
            testFinished(true);
        }
    }

    // do stuff when the test thread is finished
    private void testFinished(boolean testWasActuallyStarted) {
        stopButton.setEnabled(false);
        commSystem.setPGenSpeed(0,0);
        motorSpeedDisplay.setText("0");
        currentMotorSpeed=0;
        startButton.setEnabled(true);
        setupButton.setEnabled(true);
        fastPumpCheckBox.setEnabled(true);

        if (outputFileHasBeenSet) {
            outputFileWriter.println();
            dataFileLabel.setText(" ");
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
        motorSpeedDisplay.setText("0");
        currentMotorSpeed=0;
        resetMenuItem.setEnabled(true);
        viewMenuItem.setEnabled(true);
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
        titlePanel = new javax.swing.JPanel();
        titleLabel1 = new javax.swing.JLabel();
        titleLabel2 = new javax.swing.JLabel();
        controlPanel = new javax.swing.JPanel();
        startButton = new javax.swing.JButton();
        stopButton = new javax.swing.JButton();
        setupButton = new javax.swing.JButton();
        spacerLabel = new javax.swing.JLabel();
        displayPanel = new javax.swing.JPanel();
        targetFrame = new javax.swing.JPanel();
        targetLabel = new javax.swing.JLabel();
        dataFileFrame = new javax.swing.JPanel();
        dataFileLabel = new javax.swing.JLabel();
        plotPanelHolder = new javax.swing.JPanel();
        plotPanelSubHolder = new javax.swing.JPanel();
        spacerLabel1 = new javax.swing.JLabel();
        secondPressureFrame = new javax.swing.JPanel();
        secondPressureCaption = new javax.swing.JLabel();
        secondPressureDisplay = new javax.swing.JTextField();
        speedFactorFrame = new javax.swing.JPanel();
        speedFactorDisplay = new javax.swing.JLabel();
        speedFactorScrollBar = new javax.swing.JScrollBar();
        fastPumpPanel = new javax.swing.JPanel();
        fastPumpCheckBox = new javax.swing.JCheckBox();
        displayPanel3 = new javax.swing.JPanel();
        systemPressureCaption1 = new javax.swing.JLabel();
        motorSpeedCaption1 = new javax.swing.JLabel();
        targetTempText = new javax.swing.JLabel();
        systemTempText = new javax.swing.JLabel();
        displayPanel1 = new javax.swing.JPanel();
        targetPressureCaption = new javax.swing.JLabel();
        holdingTimeCaption = new javax.swing.JLabel();
        targetPressureDisplay = new javax.swing.JLabel();
        holdingTimeDisplay = new javax.swing.JLabel();
        displayPanel2 = new javax.swing.JPanel();
        systemPressureCaption = new javax.swing.JLabel();
        motorSpeedCaption = new javax.swing.JLabel();
        systemPressureDisplay = new javax.swing.JLabel();
        motorSpeedDisplay = new javax.swing.JLabel();
        displayPanel4 = new javax.swing.JPanel();
        systemPressureCaption2 = new javax.swing.JLabel();
        volumeText = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        optionMenu = new javax.swing.JMenu();
        resetMenuItem = new javax.swing.JMenuItem();
        viewMenuItem = new javax.swing.JMenuItem();
        printMenuItem = new javax.swing.JMenuItem();
        exitMenu = new javax.swing.JMenu();
        exitMenuItem = new javax.swing.JMenuItem();

        setTitle("Multi-target Pressure Control");
        setMinimumSize(new java.awt.Dimension(585, 503));
        setName("M_Control"); // NOI18N
        setPreferredSize(new java.awt.Dimension(585, 503));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        demoLabel.setFont(new java.awt.Font("SansSerif", 0, 18)); // NOI18N
        demoLabel.setForeground(new java.awt.Color(192, 0, 0));
        demoLabel.setText("Demo Mode");
        getContentPane().add(demoLabel, new org.netbeans.lib.awtextra.AbsoluteConstraints(128, 11, -1, -1));

        titlePanel.setLayout(new java.awt.BorderLayout());

        titleLabel1.setFont(new java.awt.Font("Serif", 1, 18)); // NOI18N
        titleLabel1.setForeground(java.awt.Color.red);
        titleLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        titleLabel1.setText("Mode 2: Multi-Target");
        titlePanel.add(titleLabel1, java.awt.BorderLayout.NORTH);

        titleLabel2.setFont(new java.awt.Font("Serif", 1, 18)); // NOI18N
        titleLabel2.setForeground(java.awt.Color.red);
        titleLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        titleLabel2.setText("Pressure Control");
        titleLabel2.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        titlePanel.add(titleLabel2, java.awt.BorderLayout.SOUTH);

        getContentPane().add(titlePanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(92, 46, -1, -1));

        controlPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Auto Control"));

        startButton.setText("START");
        startButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startButtonActionPerformed(evt);
            }
        });
        controlPanel.add(startButton);

        stopButton.setText("STOP");
        stopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButtonActionPerformed(evt);
            }
        });
        controlPanel.add(stopButton);

        setupButton.setText("SETUP");
        setupButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setupButtonActionPerformed(evt);
            }
        });
        controlPanel.add(setupButton);

        getContentPane().add(controlPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(73, 94, -1, -1));

        spacerLabel.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
        spacerLabel.setForeground(new java.awt.Color(192, 0, 0));
        spacerLabel.setText(" ");
        getContentPane().add(spacerLabel, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 11, 73, -1));

        displayPanel.setLayout(new javax.swing.BoxLayout(displayPanel, javax.swing.BoxLayout.PAGE_AXIS));
        getContentPane().add(displayPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(353, 8, 206, -1));

        targetFrame.setBorder(javax.swing.BorderFactory.createTitledBorder("Target File Name Display"));
        targetFrame.setLayout(new java.awt.BorderLayout());

        targetLabel.setBackground(new java.awt.Color(0, 0, 0));
        targetLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        targetLabel.setForeground(new java.awt.Color(0, 255, 0));
        targetLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        targetLabel.setText(" ");
        targetLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        targetLabel.setOpaque(true);
        targetLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                targetLabelMouseClicked(evt);
            }
        });
        targetFrame.add(targetLabel, java.awt.BorderLayout.CENTER);

        getContentPane().add(targetFrame, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 210, 567, -1));

        dataFileFrame.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Saving File Name Input"));
        dataFileFrame.setLayout(new java.awt.BorderLayout());

        dataFileLabel.setBackground(new java.awt.Color(0, 0, 0));
        dataFileLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        dataFileLabel.setForeground(new java.awt.Color(0, 255, 0));
        dataFileLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        dataFileLabel.setText(" ");
        dataFileLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        dataFileLabel.setOpaque(true);
        dataFileLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                dataFileLabelMouseClicked(evt);
            }
        });
        dataFileFrame.add(dataFileLabel, java.awt.BorderLayout.CENTER);

        getContentPane().add(dataFileFrame, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 250, 567, -1));

        plotPanelHolder.setLayout(new java.awt.GridBagLayout());

        plotPanelSubHolder.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        plotPanelSubHolder.setLayout(new java.awt.BorderLayout());
        plotPanelHolder.add(plotPanelSubHolder, new java.awt.GridBagConstraints());

        getContentPane().add(plotPanelHolder, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 290, 345, 170));

        spacerLabel1.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
        spacerLabel1.setForeground(new java.awt.Color(192, 0, 0));
        spacerLabel1.setText(" ");
        getContentPane().add(spacerLabel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(280, 11, 73, -1));

        secondPressureFrame.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        secondPressureFrame.setLayout(new java.awt.GridBagLayout());

        secondPressureCaption.setText("Second Pressure (psi)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 2, 3, 0);
        secondPressureFrame.add(secondPressureCaption, gridBagConstraints);

        secondPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        secondPressureDisplay.setColumns(9);
        secondPressureDisplay.setForeground(new java.awt.Color(255, 255, 0));
        secondPressureDisplay.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        secondPressureDisplay.setText(" 0");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 1, 3, 1);
        secondPressureFrame.add(secondPressureDisplay, gridBagConstraints);

        getContentPane().add(secondPressureFrame, new org.netbeans.lib.awtextra.AbsoluteConstraints(350, 290, 214, -1));

        speedFactorFrame.setBorder(javax.swing.BorderFactory.createTitledBorder("Speed Factor"));
        speedFactorFrame.setLayout(new java.awt.GridBagLayout());

        speedFactorDisplay.setBackground(new java.awt.Color(0, 0, 0));
        speedFactorDisplay.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        speedFactorDisplay.setForeground(new java.awt.Color(0, 255, 0));
        speedFactorDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        speedFactorDisplay.setText("99999");
        speedFactorDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 18;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        speedFactorFrame.add(speedFactorDisplay, gridBagConstraints);

        speedFactorScrollBar.setMaximum(410);
        speedFactorScrollBar.setMinimum(10);
        speedFactorScrollBar.setOrientation(javax.swing.JScrollBar.HORIZONTAL);
        speedFactorScrollBar.setValue(100);
        speedFactorScrollBar.addAdjustmentListener(new java.awt.event.AdjustmentListener() {
            public void adjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {
                speedFactorScrollBarAdjustmentValueChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        speedFactorFrame.add(speedFactorScrollBar, gridBagConstraints);

        getContentPane().add(speedFactorFrame, new org.netbeans.lib.awtextra.AbsoluteConstraints(350, 320, 214, -1));

        fastPumpPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Air Operated Hydraulic Pump Control"));
        fastPumpPanel.setLayout(new java.awt.GridBagLayout());

        fastPumpCheckBox.setText("Use Air Operated Hydraulic Pump");
        fastPumpPanel.add(fastPumpCheckBox, new java.awt.GridBagConstraints());

        getContentPane().add(fastPumpPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(350, 370, 214, -1));

        displayPanel3.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        displayPanel3.setPreferredSize(new java.awt.Dimension(179, 46));
        displayPanel3.setLayout(new java.awt.GridBagLayout());

        systemPressureCaption1.setText("Target Temperature   ");
        displayPanel3.add(systemPressureCaption1, new java.awt.GridBagConstraints());

        motorSpeedCaption1.setText("System Temperature");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        displayPanel3.add(motorSpeedCaption1, gridBagConstraints);

        targetTempText.setBackground(new java.awt.Color(0, 0, 0));
        targetTempText.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        targetTempText.setForeground(new java.awt.Color(255, 51, 51));
        targetTempText.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        targetTempText.setText("0 F");
        targetTempText.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        targetTempText.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        displayPanel3.add(targetTempText, gridBagConstraints);

        systemTempText.setBackground(new java.awt.Color(0, 0, 0));
        systemTempText.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        systemTempText.setForeground(new java.awt.Color(255, 0, 0));
        systemTempText.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        systemTempText.setText("0 F");
        systemTempText.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        systemTempText.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        displayPanel3.add(systemTempText, gridBagConstraints);

        getContentPane().add(displayPanel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(350, 110, 210, 50));

        displayPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        displayPanel1.setLayout(new java.awt.GridBagLayout());

        targetPressureCaption.setText("Target Pressure (psi) ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        displayPanel1.add(targetPressureCaption, gridBagConstraints);

        holdingTimeCaption.setText("Holding Time (sec.)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        displayPanel1.add(holdingTimeCaption, gridBagConstraints);

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
        displayPanel1.add(targetPressureDisplay, gridBagConstraints);

        holdingTimeDisplay.setBackground(new java.awt.Color(0, 0, 0));
        holdingTimeDisplay.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        holdingTimeDisplay.setForeground(new java.awt.Color(0, 255, 255));
        holdingTimeDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        holdingTimeDisplay.setText("60,000.00");
        holdingTimeDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        holdingTimeDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        displayPanel1.add(holdingTimeDisplay, gridBagConstraints);

        getContentPane().add(displayPanel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(350, 10, 210, -1));

        displayPanel2.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        displayPanel2.setLayout(new java.awt.GridBagLayout());

        systemPressureCaption.setText("System Pressure (psi)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        displayPanel2.add(systemPressureCaption, gridBagConstraints);

        motorSpeedCaption.setText("Analog Motor Speed");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        displayPanel2.add(motorSpeedCaption, gridBagConstraints);

        systemPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        systemPressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        systemPressureDisplay.setForeground(new java.awt.Color(255, 255, 0));
        systemPressureDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        systemPressureDisplay.setText("60,000.00");
        systemPressureDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        systemPressureDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        displayPanel2.add(systemPressureDisplay, gridBagConstraints);

        motorSpeedDisplay.setBackground(new java.awt.Color(0, 0, 0));
        motorSpeedDisplay.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        motorSpeedDisplay.setForeground(new java.awt.Color(255, 255, 0));
        motorSpeedDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        motorSpeedDisplay.setText("60,000.00");
        motorSpeedDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        motorSpeedDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        displayPanel2.add(motorSpeedDisplay, gridBagConstraints);

        getContentPane().add(displayPanel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(350, 60, 210, -1));

        displayPanel4.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        displayPanel4.setPreferredSize(new java.awt.Dimension(179, 46));
        displayPanel4.setLayout(new java.awt.GridBagLayout());

        systemPressureCaption2.setText("          Volume (cc)       ");
        displayPanel4.add(systemPressureCaption2, new java.awt.GridBagConstraints());

        volumeText.setBackground(new java.awt.Color(0, 0, 0));
        volumeText.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        volumeText.setForeground(new java.awt.Color(0, 204, 0));
        volumeText.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        volumeText.setText("0");
        volumeText.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        volumeText.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        displayPanel4.add(volumeText, gridBagConstraints);

        getContentPane().add(displayPanel4, new org.netbeans.lib.awtextra.AbsoluteConstraints(350, 170, 210, 30));

        optionMenu.setText("Options");

        resetMenuItem.setText("Reset System");
        resetMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(resetMenuItem);

        viewMenuItem.setText("View a report");
        viewMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(viewMenuItem);

        printMenuItem.setText("Print pressure versus time graph");
        printMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(printMenuItem);

        jMenuBar1.add(optionMenu);

        exitMenu.setText("Exit");

        exitMenuItem.setText("Exit Multi-Target Pressure Control");
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
        if (runMultiTestThread!=null) if (runMultiTestThread.isAlive()) {
            aborted=true;
            closing=true;
            try { runMultiTestThread.join(); }
            catch (InterruptedException e) {}
        }
        if (gaugeReadThread.isAlive()) {
            //for (int i=-1; i<58; i++) gaugeReadThread.setGaugeChannel(i,false);
            gaugeReadThread.pleaseStop();
            try { gaugeReadThread.join(); }
            catch (InterruptedException e) {}
        }
        if (fastPumpPanel.isVisible())
            configSettings.setUsingFastPump(fastPumpCheckBox.isSelected());
        // store the current screen location
        configSettings.rememberWindowPosition(this);

        // this will close the communication system (or take control of it for some other purpose)
        // and dispose of this window
        callingForm.notifyTaskFinished(this, 0);

    }//GEN-LAST:event_formWindowClosing

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        //  check parameters

        
        if ((pressureList==null) || (timeList==null) ||
            (pressureList.length==0) || (timeList.length==0)) {
            JOptionPane.showMessageDialog(this,"Input Target Pressure File");
            return;
        }
        if (outputFileHasBeenSet) {
            try {
                fileWriter = new java.io.FileWriter(dataFileLabel.getText(),false);
            }
            catch (java.io.IOException e) {
                JOptionPane.showMessageDialog(this,"Error in output file name");
                return;
            }
            outputFileWriter = new java.io.PrintWriter(fileWriter);
            outputFileWriter.println("PRESSURE CONTROL REPORT");
            outputFileWriter.print("Test ID:\t");
            outputFileWriter.println(testID);
            outputFileWriter.print("Test Date:\t");
            Date date=new Date();
            outputFileWriter.println(java.text.DateFormat.getDateInstance().format(date));
            outputFileWriter.print("Test Time:\t");
            outputFileWriter.println(java.text.DateFormat.getTimeInstance().format(date));
            outputFileWriter.print("Data Saving Interval:\t");
            outputFileWriter.print(dataSavingInterval);
            outputFileWriter.println("\tSec.");
        }
        setupButton.setEnabled(false);
        startButton.setEnabled(false);
        viewMenuItem.setEnabled(false);
        exitMenuItem.setEnabled(false);
        stopButton.setEnabled(true);
        resetMenuItem.setEnabled(false);
        aborted=false;
        // start a second thread to actually control what happens during testing
        runMultiTestThread = new RunMultiTestThread(this);
        runMultiTestThread.start();
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

    private void viewMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewMenuItemActionPerformed
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
}//GEN-LAST:event_viewMenuItemActionPerformed

    private void printMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_printMenuItemActionPerformed
        java.awt.print.PrinterJob printJob = java.awt.print.PrinterJob.getPrinterJob();
        printJob.setPrintable(pPanel1);
        if (printJob.printDialog()==false) return;
        try {printJob.print();}
        catch (java.awt.print.PrinterException e) {
            JOptionPane.showMessageDialog(this,"Error trying to print graph");
        }
}//GEN-LAST:event_printMenuItemActionPerformed

    private void targetLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_targetLabelMouseClicked
        // Show target
        Target t = new Target(this,userPath);
        t.setVisible(true);
        if (t.isDataAvailable()) {
            pressureList=t.getPressureData();
            timeList=t.getTimeData();
            tempList=t.getTempData();
            targetLabel.setText(t.getTargetFile());
        }
}//GEN-LAST:event_targetLabelMouseClicked

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        formWindowClosing(null);
}//GEN-LAST:event_exitMenuItemActionPerformed

    private void dataFileLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dataFileLabelMouseClicked
        // Show result
        Result r = new Result(this,userPath);
        r.setVisible(true);
        if (r.isDataGood()) {
            dataSavingInterval=r.getDataSavingInterval();
            fileName=r.getFileName();
            testID=r.getTestID();
            dataFileLabel.setText(fileName);
            outputFileHasBeenSet=true;
        }
}//GEN-LAST:event_dataFileLabelMouseClicked

    private void setupButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setupButtonActionPerformed
        // Show target
        Target t = new Target(this,userPath);
        t.setVisible(true);
        if (t.isDataAvailable()) {
            pressureList=t.getPressureData();
            timeList=t.getTimeData();
            tempList=t.getTempData();
            targetLabel.setText(t.getTargetFile());
        }
        // Show result
        Result r = new Result(this,userPath);
        r.setVisible(true);
        if (r.isDataGood()) {
            dataSavingInterval=r.getDataSavingInterval();
            fileName=r.getFileName();
            testID=r.getTestID();
            dataFileLabel.setText(fileName);
            outputFileHasBeenSet=true;
        }
        // start valve 2 opening (this is what the VB6 program did)
        // but don't wait for it to finish because we don't want to lock up
        // the GUI
        commSystem.controlMotor(1,'O');
}//GEN-LAST:event_setupButtonActionPerformed

    private void speedFactorScrollBarAdjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {//GEN-FIRST:event_speedFactorScrollBarAdjustmentValueChanged
        speedFactor=speedFactorScrollBar.getValue()/100.;
        speedFactorDisplay.setText(""+speedFactor);
        //SpeedScale=speedFactor * Default_speed;
}//GEN-LAST:event_speedFactorScrollBarAdjustmentValueChanged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel controlPanel;
    private javax.swing.JPanel dataFileFrame;
    private javax.swing.JLabel dataFileLabel;
    private javax.swing.JLabel demoLabel;
    private javax.swing.JPanel displayPanel;
    private javax.swing.JPanel displayPanel1;
    private javax.swing.JPanel displayPanel2;
    private javax.swing.JPanel displayPanel3;
    private javax.swing.JPanel displayPanel4;
    private javax.swing.JMenu exitMenu;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JCheckBox fastPumpCheckBox;
    private javax.swing.JPanel fastPumpPanel;
    private javax.swing.JLabel holdingTimeCaption;
    private javax.swing.JLabel holdingTimeDisplay;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JLabel motorSpeedCaption;
    private javax.swing.JLabel motorSpeedCaption1;
    private javax.swing.JLabel motorSpeedDisplay;
    private javax.swing.JMenu optionMenu;
    private javax.swing.JPanel plotPanelHolder;
    private javax.swing.JPanel plotPanelSubHolder;
    private javax.swing.JMenuItem printMenuItem;
    private javax.swing.JMenuItem resetMenuItem;
    private javax.swing.JLabel secondPressureCaption;
    private javax.swing.JTextField secondPressureDisplay;
    private javax.swing.JPanel secondPressureFrame;
    private javax.swing.JButton setupButton;
    private javax.swing.JLabel spacerLabel;
    private javax.swing.JLabel spacerLabel1;
    private javax.swing.JLabel speedFactorDisplay;
    private javax.swing.JPanel speedFactorFrame;
    private javax.swing.JScrollBar speedFactorScrollBar;
    private javax.swing.JButton startButton;
    private javax.swing.JButton stopButton;
    private javax.swing.JLabel systemPressureCaption;
    private javax.swing.JLabel systemPressureCaption1;
    private javax.swing.JLabel systemPressureCaption2;
    private javax.swing.JLabel systemPressureDisplay;
    private javax.swing.JLabel systemTempText;
    private javax.swing.JPanel targetFrame;
    private javax.swing.JLabel targetLabel;
    private javax.swing.JLabel targetPressureCaption;
    private javax.swing.JLabel targetPressureDisplay;
    private javax.swing.JLabel targetTempText;
    private javax.swing.JLabel titleLabel1;
    private javax.swing.JLabel titleLabel2;
    private javax.swing.JPanel titlePanel;
    private javax.swing.JMenuItem viewMenuItem;
    private javax.swing.JLabel volumeText;
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

    /**
     * find out of the testing has been stopped
     * @return true if we should stop
     */
    public boolean shouldStop() {
        return aborted;
    }

}
