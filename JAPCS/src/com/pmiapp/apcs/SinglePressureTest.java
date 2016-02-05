/*
 * SinglePressureTest.java
 *
 * Created on 2-21-06 based on BurstTester
 * 6-13-09 Added support for remote interface
 */

package com.pmiapp.apcs;
import com.pmiapp.common.AbortQueriable;
import com.pmiapp.common.GaugeReadListener;
import com.pmiapp.common.Notifiable;
import java.awt.Window;
import java.util.Properties;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

/**
 * Form that shows while single pressure test is running
 * Also includes runtime thread that runs the actual test
 * though this will be exported eventually to run other tests
 * @author Ron V. Webber
 */
public class SinglePressureTest extends javax.swing.JFrame
        implements GaugeReadListener, AbortQueriable, RemoteControllable {
    
    /**
     * Creates new form SinglePressureTest
     * @param commSystem Already open communication system
     * @param callingForm form that will be Notified when this form is closing
     * @param userPath path to user directory
     */
    public SinglePressureTest(APCSCommunication commSystem, Notifiable callingForm, String userPath) {
        setIconImage(new ImageIcon(getClass().getResource("/com/pmiapp/apcs/gifs/apcs16.gif")).getImage());
        initComponents();
        pPanel1 = new PlotPanel();
        jPanel6.add(pPanel1, java.awt.BorderLayout.CENTER);
        this.callingForm=callingForm;
        this.commSystem=commSystem;
        this.userPath=userPath;
        remoteControlFrame=null;
        configSettings=commSystem.getConfigSettings();
        demoLabel.setVisible(commSystem.isDemoMode());
        unlockDoorButton.setVisible(configSettings.doesDoorLockExist());
        jPanel9.setVisible(configSettings.getNumberOfGenerators()>1);
        pack();
        // center the form on the screen, so we can get the default location values
        configSettings.setWindowPosition((java.awt.Window)this);
        // set display labels so they don't shrink
        targetPressureDisplay.setPreferredSize(targetPressureDisplay.getSize());
        targetPressureDisplay.setText("");
        systemPressureDisplay.setPreferredSize(systemPressureDisplay.getSize());
        systemPressureDisplay.setText("");
        motorSpeedDisplay.setPreferredSize(motorSpeedDisplay.getSize());
        motorSpeedDisplay.setText("");
        systemPressureDisplay1.setPreferredSize(systemPressureDisplay1.getSize());
        systemPressureDisplay1.setText("");
        motorSpeedDisplay1.setPreferredSize(motorSpeedDisplay1.getSize());
        motorSpeedDisplay1.setText("");
        speedFactorDisplay.setPreferredSize(speedFactorDisplay.getSize());
        speedFactorDisplay.setText("1.0");
        speedFactor=1;
        currentPressure=0;
        simulatedPressure=0;
        currentMotorSpeed=0;
        aborted=false;
        currentUnit=0;
        if (configSettings.getNumberOfGenerators()>1) {
            jPanel3.setBorder(new javax.swing.border.TitledBorder("Main System 1"));
            jPanel9.setBorder(new javax.swing.border.TitledBorder("Secondary System 2"));
        }
        setKeyboardInputAllow(false);
        safeToOpenDoor=false; // default to not safe
        testSettings=new Properties();
    }
    
    /**
     * Start gauge reading thread and timer
     */
    public void initialize() {
        gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
        gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
        if (configSettings.getNumberOfGenerators()>1)
            gaugeReadThread.setGaugeChannel(3,true); // secondary pressure gauge
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
    
    private double pressureAccurate;
    private int currentMotorSpeed, currentUnit;
    private GaugeReadThread gaugeReadThread;
    private com.pmiapp.common.Notifiable callingForm;
    private APCSCommunication commSystem;
    private APCSConfig configSettings;
    private PlotPanel pPanel1;
    private javax.swing.Timer swingTimer;
    private double currentPressure, targetPressure, alternatePressure;
    private double simulatedPressure;
    private Properties testSettings;
    private String userPath;
    private RunSingleTestThread runSingleTestThread;
    private boolean aborted, safeToOpenDoor, allowingKeyboardInput;
    private java.io.PrintWriter outputFileWriter;
    private double speedFactor;
    private TelnetRemoteControlFrame remoteControlFrame;
    private boolean autoStart = false;

    public void notifyTaskFinished(Window finishedTaskWindow, int tag) {
        if (finishedTaskWindow==remoteControlFrame) {
            remoteControlFrame.dispose();
            remoteControlFrame=null;
            remoteInterfaceMenuItem.setEnabled(true);
        }
    }

    public String processCommand(String line) {
        double d;
        int i;
        if (line.equals("GetCurrentPressure"))
            return ("CurrentPressure="+currentPressure);
        if (line.equals("GetTargetPressure"))
            return ("TargetPressure="+targetPressure);
        if (line.equals("GetSpeedFactor"))
            return ("SpeedFactor="+speedFactor);
        if (line.equals("GetMotorSpeed"))
            return ("MotorSpeed="+currentMotorSpeed);
        if (line.equals("GetMode")) {
            if (startButton.isEnabled())
                return ("Mode=0");
            else
                return ("Mode=1");
        }
        if (line.startsWith("SetSpeedFactor=")) {
            try { d = Double.parseDouble(line.substring(15)); }
            catch (NumberFormatException e) {
                return ("Number Format Error");
            }
            if (d<.25) d=.25;
            if (d>4.0) d=4.0;
            speedFactor=d;
            speedFactorDisplay.setText(""+speedFactor);
            return ("SpeedFactor="+speedFactor);
        }
        if (line.startsWith("SetTargetPressure=")) {
            try { d = Double.parseDouble(line.substring(18)); }
            catch (NumberFormatException e) {
                return ("Number Format Error");
            }
            if (d>configSettings.getPressureRange()) d=configSettings.getPressureRange();
            targetPressureDisplay.setText(""+d);
            targetPressure=d;
            return ("TargetPressure="+targetPressure);
        }
        if (line.startsWith("SetMode=")) {
            try { i = Integer.parseInt(line.substring(8)); }
            catch (NumberFormatException e) {
                return ("Number Format Error");
            }
            if (i==0) {
                if (stopButton.isEnabled()) {
                    stopButton.setEnabled(false);
                    aborted=true;
                    return ("Mode=0");
                }
                if (startButton.isEnabled()) {
                    // already stopped
                    return ("Mode=0");
                }
                return ("Busy");
            }
            if (i==1) {
                if (startButton.isEnabled()) {
                    i=tryStart();
                    if (i>0)
                        return ("Start Error "+i);
                }
                return ("Mode=1");
            }
            return ("Undefined Mode");
        }
        return ("Unknown Command");
    }
    
    private class RunSingleTestThread extends java.lang.Thread implements GaugeReadListener, AbortQueriable {
        public RunSingleTestThread(GaugeReadListener parent) {
            this.parent=parent;
        }
        
        private GaugeReadListener parent;
        private long lastTestTime;
        private boolean stable, firstTimeThrough, useInitialSpeed;
        private int noiseCount, slowdownFactor;
        private double nextDesiredMotorSpeed, currentDesiredMotorSpeed, lastPressure, dp, dp0, maxSpeed;
        private double maxSpeedLP, maxSpeedHP, pressureRange, ramUpSpeed, ramDownSpeed, defaultSpeed;
        private double slowDownPressure, microControlStartingSpeed, generatorRange, pressureTolerance;
        private double speedScale, PSL;
        
        // called by any routine that we may call that needs to keep reading the pressure gauge 
        // this will update the display, and maybe save the results to the output file
        public void gaugeReadData(int channel, int countValue, int tag) {
            if ((channel==2) || (channel==3)) {
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

        // read pressure, update display, and maybe save the results to the output file
        // saving results moved to this thread's own gaugeReadData routine
        private void readPressure() {
            gaugeReadData(2+currentUnit, commSystem.rawReading(2+currentUnit),0);
            if (configSettings.getNumberOfGenerators()>1)
                parent.gaugeReadData(3-currentUnit, commSystem.rawReading(3-currentUnit),0);
        }
        
        private void maintainPressure() {
            // maintain the pressure on the current unit
            readPressure();  // this sets currentPressure
            if (aborted) {
                if (currentMotorSpeed!=0) {
                    commSystem.setPGenSpeed(currentUnit,0);
                    currentMotorSpeed=0;
                    motorSpeedDisplay.setText("0");
                }
                return;
            }
            // only do this every 0.1 seconds
            if (System.nanoTime()-lastTestTime<100000000) return;
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
            commSystem.setPGenSpeed(currentUnit, currentMotorSpeed);
            motorSpeedDisplay.setText(""+currentMotorSpeed);            
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
        
        @Override
        public void run() {
            // runs the actual test
            // background reading of pressure is still going on
            // make sure that valve 1 is closed
            commSystem.moveMotorValveAndWait(0,'C',this);
            if (configSettings.getNumberOfGenerators()>1)
                commSystem.moveMotorValveAndWait(2,'C',this);
            // make sure that valve 2 is open
            commSystem.moveMotorValveAndWait(1,'O',this);
            if (configSettings.getNumberOfGenerators()>1)
                commSystem.moveMotorValveAndWait(3,'O',this);
            if (aborted) {
                testFinished(false);
                return;
            }
            // at this point, we want to shut down the background gauge reader - we will be doing our own
            // pressure gauge reads from now on (until the end of the test)
            if (gaugeReadThread.isAlive()) gaugeReadThread.pleaseStop();
            // don't worry about waiting for the gaugeReadThread to actually stop - it will stop on its own soon enough
            // initialize some things
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
            lastTestTime=System.nanoTime();
            while (aborted==false) {
                maintainPressure();
                if (commSystem.getPGenLimit(currentUnit)==1) {
                    if (configSettings.getNumberOfGenerators()==1) {
                        // can't maintain pressure here, so we just need to seal things off
                        // remember the current pressure
                        double stallingPressure=currentPressure;
                        // stop generator (it should already be stopped by the limit switch)
                        commSystem.setPGenSpeed(0,0);
                        motorSpeedDisplay.setText("0");
                        currentMotorSpeed=0;
                        // start valve 2 closing
                        commSystem.controlMotor(1, 'C');
                        // wait for it to stop, while reading the current pressure
                        // for now, you can't abort this
                        while (commSystem.getMotorStatus(1)!=0) readPressure();
                        // start generator moving in full reverse
                        commSystem.setPGenSpeed(0,-1023);
                        motorSpeedDisplay.setText("-1023");
                        currentMotorSpeed=-1023;
                        // wait for reverse limit switch or pressure less than 100
                        while (commSystem.getPGenLimit(0)!=-1) {
                            readPressure();
                            if (currentPressure<100) break;
                        }
                        // start valve 1 opening
                        commSystem.controlMotor(0, 'O');
                        // wait for reverse limit switch
                        while (commSystem.getPGenLimit(0)!=-1) readPressure();
                        // wait for valve 1 to stop
                        while (commSystem.getMotorStatus(0)!=0) readPressure();
                        // start valve 1 closing
                        commSystem.controlMotor(0, 'C');
                        // wait for valve 1 to stop
                        while (commSystem.getMotorStatus(0)!=0) readPressure();
                        commSystem.setPGenSpeed(0,0);  // stop first so it goes to full speed faster
                        // start generator running at full speed
                        commSystem.setPGenSpeed(0,1023);
                        motorSpeedDisplay.setText("1023");
                        currentMotorSpeed=1023;
                        // wait for pressure to be >= stalling pressure
                        do {
                            readPressure();
                        } while ((currentPressure<stallingPressure) && (commSystem.getPGenLimit(0)!=1));
                        // stop generator
                        commSystem.setPGenSpeed(0,0);
                        motorSpeedDisplay.setText("0");
                        currentMotorSpeed=0;
                        // start valve 2 opening
                        commSystem.controlMotor(1, 'O');
                        // wait for valve 2 to stop
                        while (commSystem.getMotorStatus(1)!=0) readPressure();
                        // all done recycling pressure generator
                        // we now return to normal loop
                    }
                    else {
                        // multiple generator recycle
                        currentUnit=(1-currentUnit); // switch units
                        jPanel3.setBorder(new javax.swing.border.TitledBorder("Main System "+(currentUnit+1)));
                        jPanel9.setBorder(new javax.swing.border.TitledBorder("Secondary System "+(2-currentUnit)));
                        maintainPressure(); // start second generator moving as soon as possible
                        // stop non-current generator (it should already be stopped by the limit switch)
                        commSystem.setPGenSpeed(1-currentUnit,0);
                        motorSpeedDisplay1.setText("0");
                        // if currentUnit=0, we have a 2 offset for all non-currentUnit valves
                        // if currentUnit=1, we have a 0 offset for all non-currentUnit valves
                        int valveOffset=2-currentUnit-currentUnit;
                        // start valve 2 on the now-non-current unit closing
                        commSystem.controlMotor(1+valveOffset, 'C');
                        // wait for it to stop, maintaining pressure in current unit
                        // for now, you can't abort this
                        while (commSystem.getMotorStatus(1+valveOffset)!=0) maintainPressure();
                        // start non-current generator moving in full reverse
                        commSystem.setPGenSpeed(1-currentUnit,-1023);
                        motorSpeedDisplay1.setText("-1023");
                        // wait for reverse limit switch on non-current unit or pressure less than
                        // 100 on non-current pressure gauge
                        while (commSystem.getPGenLimit(1-currentUnit)!=-1) {
                            maintainPressure();
                            if (alternatePressure<100) break;
                        }
                        // start valve 1 on the now-non-current unit opening
                        commSystem.controlMotor(valveOffset, 'O');
                        // wait for reverse limit switch on non-current unit
                        while (commSystem.getPGenLimit(1-currentUnit)!=-1) maintainPressure();
                        // wait for valve 1 to stop
                        while (commSystem.getMotorStatus(valveOffset)!=0) maintainPressure();
                        // start valve 1 closing
                        commSystem.controlMotor(valveOffset, 'C');
                        // wait for valve 1 to stop
                        while (commSystem.getMotorStatus(valveOffset)!=0) maintainPressure();
                        commSystem.setPGenSpeed(1-currentUnit, 0); // stop first so it starts faster
                        // start alternate generator running at full speed
                        commSystem.setPGenSpeed(1-currentUnit,1023);
                        motorSpeedDisplay1.setText("1023");
                        // wait for alternate pressure to be >= current pressure
                        do {
                            maintainPressure();
                        } while ((alternatePressure<currentPressure) && (commSystem.getPGenLimit(1-currentUnit)!=1));
                        // stop generator
                        commSystem.setPGenSpeed(1-currentUnit,0);
                        motorSpeedDisplay1.setText("0");
                        // start valve 2 opening
                        commSystem.controlMotor(1+valveOffset, 'O');
                        // wait for valve 2 to stop
                        while (commSystem.getMotorStatus(1+valveOffset)!=0) maintainPressure();
                        // all done recycling non-current pressure generator
                        // we now return to normal loop
                    }
                }
            }
            commSystem.setPGenSpeed(0, 0);
            commSystem.setPGenSpeed(1,0);
            motorSpeedDisplay.setText("0");
            currentMotorSpeed=0;
            testFinished(true);
        }
    }
    
    // do stuff when the test thread is finished
    private void testFinished(boolean testWasActuallyStarted) {
        stopButton.setEnabled(false);
        // restart the gauge read background thread, unless
        // the test was not actually started (in which case the background thread
        // would still be running)
        // Note that if we are closing down the form, the reset system may still need
        // the background read thread so we should still start it up even if it is
        // just going to get shut down again
        if (testWasActuallyStarted) {
            gaugeReadThread=new GaugeReadThread(this, commSystem, 0);
            gaugeReadThread.setGaugeChannel(2,true); // main pressure gauge
            if (configSettings.getNumberOfGenerators()>1)
                gaugeReadThread.setGaugeChannel(3,true); // secondary pressure gauge
            gaugeReadThread.start();
        }
        motorSpeedDisplay.setText("0");
        commSystem.setPGenSpeed(0,0); // just in case
        currentMotorSpeed=0;
        resetMenuItem.setEnabled(true);
        unlockDoorButton.setEnabled(true);        
        jButton1.setEnabled(true);
        startButton.setEnabled(true);
    }

    private void setKeyboardInputAllow(boolean b) {
        allowingKeyboardInput=b;
        jButton2.setEnabled(b);
        jButton3.setEnabled(b);
        jButton4.setEnabled(b);
        jButton5.setEnabled(b);
        jButton6.setEnabled(b);
        jButton7.setEnabled(b);
        jButton8.setEnabled(b);
        jButton9.setEnabled(b);
        jButton10.setEnabled(b);
        jButton11.setEnabled(b);
        jButton12.setEnabled(b);
        jButton13.setEnabled(b);
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
        spacerLabel = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        jPanel6 = new javax.swing.JPanel();
        spacerLabel1 = new javax.swing.JLabel();
        unlockDoorButton = new javax.swing.JButton();
        jPanel7 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        targetPressureDisplay = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        systemPressureDisplay = new javax.swing.JLabel();
        motorSpeedDisplay = new javax.swing.JLabel();
        jPanel4 = new javax.swing.JPanel();
        speedFactorDisplay = new javax.swing.JLabel();
        jScrollBar1 = new javax.swing.JScrollBar();
        jPanel8 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        jButton6 = new javax.swing.JButton();
        jButton7 = new javax.swing.JButton();
        jButton8 = new javax.swing.JButton();
        jButton9 = new javax.swing.JButton();
        jButton10 = new javax.swing.JButton();
        jButton11 = new javax.swing.JButton();
        jButton12 = new javax.swing.JButton();
        jButton13 = new javax.swing.JButton();
        jPanel9 = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        systemPressureDisplay1 = new javax.swing.JLabel();
        motorSpeedDisplay1 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        optionsMenu = new javax.swing.JMenu();
        resetMenuItem = new javax.swing.JMenuItem();
        printGraphMenuItem = new javax.swing.JMenuItem();
        remoteInterfaceMenuItem = new javax.swing.JMenuItem();
        exitMenu = new javax.swing.JMenu();
        exitMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Pressure Control");
        setName("SinglePressureTest"); // NOI18N
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
        jLabel2.setText("Mode 1: Single Target");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 0, 5);
        getContentPane().add(jLabel2, gridBagConstraints);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Pressure Auto Control"));

        startButton.setText("START");
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

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
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

        jPanel5.setLayout(new java.awt.BorderLayout());

        jPanel6.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        jPanel6.setLayout(new java.awt.BorderLayout());
        jPanel5.add(jPanel6, java.awt.BorderLayout.CENTER);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
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
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        getContentPane().add(unlockDoorButton, gridBagConstraints);

        jPanel7.setLayout(new java.awt.GridBagLayout());

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(""));
        jPanel2.setLayout(new java.awt.GridBagLayout());

        jLabel3.setText("Target Pressure (PSI)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel2.add(jLabel3, gridBagConstraints);

        targetPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        targetPressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12));
        targetPressureDisplay.setForeground(new java.awt.Color(0, 255, 0));
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

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHEAST;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        jPanel7.add(jPanel2, gridBagConstraints);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Main System"));
        jPanel3.setLayout(new java.awt.GridBagLayout());

        jLabel7.setText("System Pressure (PSI)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel3.add(jLabel7, gridBagConstraints);

        jLabel8.setText("Analog Motor Speed");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel3.add(jLabel8, gridBagConstraints);

        systemPressureDisplay.setBackground(new java.awt.Color(0, 0, 0));
        systemPressureDisplay.setFont(new java.awt.Font("Monospaced", 1, 12));
        systemPressureDisplay.setForeground(new java.awt.Color(0, 255, 255));
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
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel3.add(motorSpeedDisplay, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        jPanel7.add(jPanel3, gridBagConstraints);

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("Speed Factor"));
        jPanel4.setLayout(new java.awt.GridBagLayout());

        speedFactorDisplay.setBackground(new java.awt.Color(0, 0, 0));
        speedFactorDisplay.setFont(new java.awt.Font("Monospaced", 1, 12));
        speedFactorDisplay.setForeground(new java.awt.Color(0, 255, 255));
        speedFactorDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        speedFactorDisplay.setText("60,000.00");
        speedFactorDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        speedFactorDisplay.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(1, 8, 1, 8);
        jPanel4.add(speedFactorDisplay, gridBagConstraints);

        jScrollBar1.setMaximum(410);
        jScrollBar1.setMinimum(25);
        jScrollBar1.setOrientation(javax.swing.JScrollBar.HORIZONTAL);
        jScrollBar1.setValue(100);
        jScrollBar1.addAdjustmentListener(new java.awt.event.AdjustmentListener() {
            public void adjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {
                jScrollBar1AdjustmentValueChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 6);
        jPanel4.add(jScrollBar1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        jPanel7.add(jPanel4, gridBagConstraints);

        jPanel8.setBorder(javax.swing.BorderFactory.createTitledBorder(""));
        jPanel8.setLayout(new java.awt.GridBagLayout());

        jButton1.setText("Set Pressure");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jButton1.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                jButton1KeyTyped(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel8.add(jButton1, gridBagConstraints);

        jButton2.setText("7");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel8.add(jButton2, gridBagConstraints);

        jButton3.setText("8");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel8.add(jButton3, gridBagConstraints);

        jButton4.setText("9");
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel8.add(jButton4, gridBagConstraints);

        jButton5.setText("4");
        jButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel8.add(jButton5, gridBagConstraints);

        jButton6.setText("5");
        jButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton6ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel8.add(jButton6, gridBagConstraints);

        jButton7.setText("6");
        jButton7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton7ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel8.add(jButton7, gridBagConstraints);

        jButton8.setText("1");
        jButton8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton8ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel8.add(jButton8, gridBagConstraints);

        jButton9.setText("2");
        jButton9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton9ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel8.add(jButton9, gridBagConstraints);

        jButton10.setText("3");
        jButton10.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton10ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel8.add(jButton10, gridBagConstraints);

        jButton11.setText("0");
        jButton11.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton11ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 4, 0);
        jPanel8.add(jButton11, gridBagConstraints);

        jButton12.setText(".");
        jButton12.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton12ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 4, 0);
        jPanel8.add(jButton12, gridBagConstraints);

        jButton13.setText("CE");
        jButton13.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton13ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 4, 0);
        jPanel8.add(jButton13, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        jPanel7.add(jPanel8, gridBagConstraints);

        jPanel9.setBorder(javax.swing.BorderFactory.createTitledBorder("Secondary System"));
        jPanel9.setLayout(new java.awt.GridBagLayout());

        jLabel10.setText("System Pressure (PSI)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel9.add(jLabel10, gridBagConstraints);

        jLabel11.setText("Analog Motor Speed");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel9.add(jLabel11, gridBagConstraints);

        systemPressureDisplay1.setBackground(new java.awt.Color(0, 0, 0));
        systemPressureDisplay1.setFont(new java.awt.Font("Monospaced", 1, 12));
        systemPressureDisplay1.setForeground(new java.awt.Color(0, 255, 255));
        systemPressureDisplay1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        systemPressureDisplay1.setText("60,000.00");
        systemPressureDisplay1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        systemPressureDisplay1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel9.add(systemPressureDisplay1, gridBagConstraints);

        motorSpeedDisplay1.setBackground(new java.awt.Color(0, 0, 0));
        motorSpeedDisplay1.setFont(new java.awt.Font("Monospaced", 1, 12));
        motorSpeedDisplay1.setForeground(new java.awt.Color(255, 255, 0));
        motorSpeedDisplay1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        motorSpeedDisplay1.setText("60,000.00");
        motorSpeedDisplay1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        motorSpeedDisplay1.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 2);
        jPanel9.add(motorSpeedDisplay1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        jPanel7.add(jPanel9, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridheight = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        getContentPane().add(jPanel7, gridBagConstraints);

        jLabel9.setFont(new java.awt.Font("Serif", 1, 18));
        jLabel9.setForeground(java.awt.Color.red);
        jLabel9.setText("Pressure Control");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 2, 0);
        getContentPane().add(jLabel9, gridBagConstraints);

        optionsMenu.setText("Options");

        resetMenuItem.setText("Reset System");
        resetMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(resetMenuItem);

        printGraphMenuItem.setText("Print Pressure versus Time Graph");
        printGraphMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printGraphMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(printGraphMenuItem);

        remoteInterfaceMenuItem.setText("Remote Control Interface");
        remoteInterfaceMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                remoteInterfaceMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(remoteInterfaceMenuItem);

        jMenuBar1.add(optionsMenu);

        exitMenu.setText("Exit");

        exitMenuItem.setText("Exit Single Target Pressure Control");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        exitMenu.add(exitMenuItem);

        jMenuBar1.add(exitMenu);

        setJMenuBar(jMenuBar1);

        getAccessibleContext().setAccessibleName("Single Target Pressure Control");
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1KeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jButton1KeyTyped
        if (allowingKeyboardInput==false) return;
        char c=evt.getKeyChar();
        if (((c>='0') && (c<='9')) || (c=='.'))
            targetPressureDisplay.setText(targetPressureDisplay.getText()+c);
        else if (c=='\b') {
            String s=targetPressureDisplay.getText();
            if (s.length()<=1)
                targetPressureDisplay.setText("");
            else
                targetPressureDisplay.setText(s.substring(0, s.length()-1));        
        }
        else if (c=='\n') setKeyboardInputAllow(false);
    }//GEN-LAST:event_jButton1KeyTyped

    private void jButton13ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton13ActionPerformed
        String s=targetPressureDisplay.getText();
        if (s.length()<=1)
            targetPressureDisplay.setText("");
        else
            targetPressureDisplay.setText(s.substring(0, s.length()-1));
    }//GEN-LAST:event_jButton13ActionPerformed

    private void jButton12ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton12ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+".");
    }//GEN-LAST:event_jButton12ActionPerformed

    private void jButton11ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton11ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+"0");
    }//GEN-LAST:event_jButton11ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+"9");
    }//GEN-LAST:event_jButton4ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+"8");
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+"7");
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton7ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+"6");
    }//GEN-LAST:event_jButton7ActionPerformed

    private void jButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+"5");
    }//GEN-LAST:event_jButton6ActionPerformed

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+"4");
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jButton10ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton10ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+"3");
    }//GEN-LAST:event_jButton10ActionPerformed

    private void jButton9ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton9ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+"2");
    }//GEN-LAST:event_jButton9ActionPerformed

    private void jButton8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton8ActionPerformed
        targetPressureDisplay.setText(targetPressureDisplay.getText()+"1");
    }//GEN-LAST:event_jButton8ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        targetPressureDisplay.setText("");
        setKeyboardInputAllow(true);
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jScrollBar1AdjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {//GEN-FIRST:event_jScrollBar1AdjustmentValueChanged
        speedFactor=jScrollBar1.getValue()/100.;
        speedFactorDisplay.setText(""+speedFactor);
    }//GEN-LAST:event_jScrollBar1AdjustmentValueChanged

    private void printGraphMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_printGraphMenuItemActionPerformed
        java.awt.print.PrinterJob printJob = java.awt.print.PrinterJob.getPrinterJob();
        printJob.setPrintable(pPanel1);
        if (printJob.printDialog()==false) return;
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
            else
                JOptionPane.showMessageDialog(this,"May not be safe to open door.  Reset generator first.");
        }
    }//GEN-LAST:event_unlockDoorButtonActionPerformed

    private void resetMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMenuItemActionPerformed
        ResetSystem rs = new ResetSystem(this, this, commSystem, -1, safeToOpenDoor);
        rs.initialize();
        rs.setVisible(true);
        safeToOpenDoor=rs.isItSafe();
    }//GEN-LAST:event_resetMenuItemActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
        stopButton.setEnabled(false);
        aborted=true;
    }//GEN-LAST:event_stopButtonActionPerformed

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        int retcode;
        // endless loop to allow door checking routine to try again
        while (true) {
            retcode = tryStart();
            if (retcode==1) {
                JOptionPane.showMessageDialog(this, "Input Target Pressure!");
                return;
            }
            if (retcode==2) {
                JOptionPane.showMessageDialog(this, "Error in target pressure");
                return;
            }
            if (retcode==3) {
                JOptionPane.showMessageDialog(this, "Pressure Target Exceeds Pressure Range.");
                return;
            }
            if (retcode==4) {
                JOptionPane.showMessageDialog(this,"Close door before starting test");
                // maybe they closed the door before clicking on OK?
                if ((commSystem.getDigitalInput(0) & 1)==1) return;
                continue;
            }
            return;
        }
    }

    private int tryStart() {
        // check target pressure display
        setKeyboardInputAllow(false);
        if (targetPressureDisplay.getText().length()==0) {
            return (1);
        }
        try { targetPressure=Double.parseDouble(targetPressureDisplay.getText()); }
        catch (NumberFormatException e) {
            return (2);
        }
        if (targetPressure>configSettings.getPressureRange()) {
            return (3);
        }
        // load variables from testSettings
        pressureAccurate=configSettings.getPressureAccurate();
        // check door switch
        if (commSystem.isDemoMode()==false)
            if ((commSystem.getDigitalInput(0) & 1)==1)
                return (4);
        unlockDoorButton.setEnabled(false);
        // everything should be set to run start an auto test running
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        jButton1.setEnabled(false);
        aborted=false;
        resetMenuItem.setEnabled(false);
        if (targetPressure<=0) {
            ResetSystem rs = new ResetSystem(this,this,commSystem,-1);
            rs.initialize();
            currentMotorSpeed=-1023;
            rs.setVisible(true);
            testFinished(false);
            return (0);
        }
        // stop all motors, just in case
        motorSpeedDisplay.setText("0");
        commSystem.setPGenSpeed(0,0);
        currentMotorSpeed=0;
        if (configSettings.getNumberOfGenerators()>1) {
            commSystem.setPGenSpeed(1, 0);
        }
        // start a second thread to actually control what happens during testing
        runSingleTestThread = new RunSingleTestThread(this);
        runSingleTestThread.start();
        return (0);
    }//GEN-LAST:event_startButtonActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        dispatchEvent(new java.awt.event.WindowEvent(this,java.awt.event.WindowEvent.WINDOW_CLOSING));
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
        // only call this if the background task is finished
        if (runSingleTestThread!=null) if (runSingleTestThread.isAlive()) {
            aborted=true;
            try { runSingleTestThread.join(); }
            catch (InterruptedException e) {}
        }
        if (remoteControlFrame!=null) {
            remoteControlFrame.close();
        }
        int rv=JOptionPane.showConfirmDialog(this,"Do you want to reset the system before exiting?",
                "APCS Single Pressure Control",JOptionPane.YES_NO_CANCEL_OPTION);
        if (rv==JOptionPane.CANCEL_OPTION) {
            // tell the calling form to forget that it had told us to exit
            callingForm.notifyTaskFinished(null,0);
            return;
        }
        else if (rv==JOptionPane.YES_OPTION) {
            ResetSystem rs = new ResetSystem(this, this, commSystem, -1, safeToOpenDoor);
            rs.initialize();
            rs.setVisible(true);
            safeToOpenDoor=rs.isItSafe();
            if (rs.wasAborted()) {
                callingForm.notifyTaskFinished(null,0);
                return;
            }
        }
        swingTimer.stop();
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

    private void startRemoteInterface(){
        remoteControlFrame = new TelnetRemoteControlFrame(this,autoStart);
        remoteInterfaceMenuItem.setEnabled(false);
        remoteControlFrame.setVisible(true);
    }

    public void autoStartRemoteInterface(){
        autoStart = true;
        startRemoteInterface();
    }

    private void remoteInterfaceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_remoteInterfaceMenuItemActionPerformed
        startRemoteInterface();
    }//GEN-LAST:event_remoteInterfaceMenuItemActionPerformed
    
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
                if (currentUnit==1) {
                    alternatePressure=configSettings.getGaugeChannel(2).getUser(configSettings.getGaugeChannel(2).getReal(countValue));
                    systemPressureDisplay1.setText(configSettings.getGaugeChannel(2).getUserFormattedString(alternatePressure));
                    break;
                }
                if (commSystem.isDemoMode()) currentPressure=simulatedPressure;
                else currentPressure=configSettings.getGaugeChannel(2).getUser(configSettings.getGaugeChannel(2).getReal(countValue));
                systemPressureDisplay.setText(configSettings.getGaugeChannel(2).getUserFormattedString(currentPressure));
                break;
            case 3: // secondary pressure gauge
                if (currentUnit==0) {
                    alternatePressure=configSettings.getGaugeChannel(3).getUser(configSettings.getGaugeChannel(3).getReal(countValue));
                    systemPressureDisplay1.setText(configSettings.getGaugeChannel(3).getUserFormattedString(alternatePressure));
                    break;
                }
                if (commSystem.isDemoMode()) currentPressure=simulatedPressure;
                else currentPressure=configSettings.getGaugeChannel(3).getUser(configSettings.getGaugeChannel(3).getReal(countValue));
                systemPressureDisplay.setText(configSettings.getGaugeChannel(3).getUserFormattedString(currentPressure));
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
    private javax.swing.JLabel demoLabel;
    private javax.swing.JMenu exitMenu;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton10;
    private javax.swing.JButton jButton11;
    private javax.swing.JButton jButton12;
    private javax.swing.JButton jButton13;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JButton jButton8;
    private javax.swing.JButton jButton9;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
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
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollBar jScrollBar1;
    private javax.swing.JLabel motorSpeedDisplay;
    private javax.swing.JLabel motorSpeedDisplay1;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JMenuItem printGraphMenuItem;
    private javax.swing.JMenuItem remoteInterfaceMenuItem;
    private javax.swing.JMenuItem resetMenuItem;
    private javax.swing.JLabel spacerLabel;
    private javax.swing.JLabel spacerLabel1;
    private javax.swing.JLabel speedFactorDisplay;
    private javax.swing.JButton startButton;
    private javax.swing.JButton stopButton;
    private javax.swing.JLabel systemPressureDisplay;
    private javax.swing.JLabel systemPressureDisplay1;
    private javax.swing.JLabel targetPressureDisplay;
    private javax.swing.JButton unlockDoorButton;
    // End of variables declaration//GEN-END:variables
    
}
