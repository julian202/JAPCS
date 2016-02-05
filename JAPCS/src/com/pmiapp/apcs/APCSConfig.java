/*
 * APCSConfig.java
 *
 * Created on October 10, 2005, 3:12 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.pmiapp.apcs;
import com.pmiapp.common.GaugeChannel;

/**
 * Object that holds all system configuration settings.
 * @author Ron V. Webber
 */
public class APCSConfig {

    /**
     * Creates a new instance of APCSConfig.  The caller must have already created
     * and filled the Properties objects.  Caller is also responsible for storing these
     * properties objects to files before the program finishes.  Both properties objects
     * can point to the same object if you are only using one properties file.
     * Global properties are those that remain the same no matter what group is current.
     * Group properties are those that can be different for each group.
     * @param globalProperties Properties object where global configuration settings are retrieved and updated
     * @param groupProperties Properties object where group configuration settings are retrieved and updated
     */
    protected APCSConfig(java.util.Properties globalProperties, java.util.Properties groupProperties) {
        this.globalProperties=globalProperties;
        this.groupProperties=groupProperties;
        gaugeChannelArray = new GaugeChannel[58];
        
        // voltage inputs are 0 to 2 volts
        // and these don't require calibration information from the properties file
        gaugeChannelArray[0]=new GaugeChannel(2.,"volts"); // 0 is the 2 volt reference
        gaugeChannelArray[1]=new GaugeChannel(2.,"volts"); // 1 is the ground reference
        
        // Main Pressure Gauge defaults to 0 to 60000 PSI, which is the largest we will probably ever use
        gaugeChannelArray[2]=new GaugeChannel(0., 60000.,"PSI","P1",globalProperties);
        // 5 secondary pressure gauges (P2 through P6) also default to 60000
        gaugeChannelArray[3]=new GaugeChannel(0., 60000.,"PSI","P2",globalProperties);
        gaugeChannelArray[4]=new GaugeChannel(0., 60000.,"PSI","P3",globalProperties);
        gaugeChannelArray[5]=new GaugeChannel(0., 60000.,"PSI","P4",globalProperties);
        gaugeChannelArray[6]=new GaugeChannel(0., 60000.,"PSI","P5",globalProperties);
        gaugeChannelArray[7]=new GaugeChannel(0., 60000.,"PSI","P6",globalProperties);
        
        // vacuum gauge doesn't require calibration information, it is just "%" - we don't use this normally
        gaugeChannelArray[8]=new GaugeChannel(100.,"%");
        
        // low pressure gauge defaults to 20 PSI - we don't use this normally
        gaugeChannelArray[9]=new GaugeChannel(20.,"PSI");
        
        // motor valve positions and limits do not use real values, so we set these to percentage
        // and we don't need to get any calibration information from the properties file
        // since the actual limits are read in hardware
        for (int i=10; i<=57; i++) {
            gaugeChannelArray[i]=new GaugeChannel(100.,"%");
        }
        
        // global running parameters
        athena=(GaugeChannel.getConfigValue("Athena",globalProperties,1)==1);
        qcGauge=(GaugeChannel.getConfigValue("QCGauge",globalProperties,1)==0);
        qcGaugePort=(GaugeChannel.getConfigValue("QCGaugePort",globalProperties,2));
        maxSpeedLP=GaugeChannel.getConfigValue("MaxSpeedLP",groupProperties,1000);
        maxSpeedHP=GaugeChannel.getConfigValue("MaxSpeedHP",groupProperties,600);
        pressureRange=GaugeChannel.getConfigValue("PressureRange",globalProperties,10000.);
        defaultSpeed=GaugeChannel.getConfigValue("DefaultSpeed",groupProperties,50);
        pressureAccurate=GaugeChannel.getConfigValue("PressureAccurate",groupProperties,3.);
        fastPumpMinPressure=GaugeChannel.getConfigValue("FastPumpMinPressure",globalProperties,1000.);
        fastPumpMinSpeed=GaugeChannel.getConfigValue("FastPumpMinSpeed",globalProperties,500);
        fastPumpMaxPressure=GaugeChannel.getConfigValue("FastPumpMaxPressure",globalProperties,30000.);
        fastPumpMaxSpeed=GaugeChannel.getConfigValue("FastPumpMaxSpeed",globalProperties,4000);
        fastPumpIncrementTime=GaugeChannel.getConfigValue("FastPumpIncrementTime",groupProperties,10.);
        ramUpSpeed=GaugeChannel.getConfigValue("RamUpSpeed",groupProperties,150);
        ramDownSpeed=GaugeChannel.getConfigValue("RamDownSpeed",groupProperties,-150);
        slowDownPressure=GaugeChannel.getConfigValue("SlowDownPressure",groupProperties,200.);
        microControlStartingSpeed=GaugeChannel.getConfigValue("MicroControlStartingSpeed",groupProperties,1);
        pulseHeight=GaugeChannel.getConfigValue("PulseHeight",groupProperties,50);
        generatorRange=GaugeChannel.getConfigValue("GeneratorRange",globalProperties,10000.);
        pressureTolerance=GaugeChannel.getConfigValue("PressureTolerance",groupProperties,20.);
        supervisorMode=(GaugeChannel.getConfigValue("SupervisorMode",globalProperties,1)==1);
        doorLockExists=(GaugeChannel.getConfigValue("DoorLockExists",globalProperties,0)==1);
        encoderExists=(GaugeChannel.getConfigValue("EncoderExists",globalProperties,0)==1);
        // default encoder factor is for 30k high capacity generator with 18cc volume for
        // 5.75 inches * 14 revolutions per inch * 1000 encoder counts
        if (encoderExists) {
            encoderFactor=GaugeChannel.getConfigValue("EncoderFactor", globalProperties, 0.0002236);
        }
        // default is for 1 generator and 2 valves and 1 chamber
        numberOfGenerators=GaugeChannel.getConfigValue("NumberOfGenerators",globalProperties,1);
        numberOfMotorValves=GaugeChannel.getConfigValue("NumberOfMotorValves",globalProperties,2);
        numberOfChambers=GaugeChannel.getConfigValue("NumberOfChambers",globalProperties,1);
        numberOfRanges=GaugeChannel.getConfigValue("NumberOfRanges",globalProperties,1);
        currentChamberNumber=GaugeChannel.getConfigValue("CurrentChamberNumber",globalProperties,1);
        currentRangeNumber=GaugeChannel.getConfigValue("CurrentRangeNumber",globalProperties,1);
        if (numberOfRanges>1) {
            pressureRange2=GaugeChannel.getConfigValue("PressureRange2",globalProperties,10000.);
        }
        else {
            pressureRange2=0;
        }
        // if you have a fast pump, there is an analog output for the fast pump
        // and maybe a motor valve to isolate the fast pump
        fastPumpAnalogOutput=GaugeChannel.getConfigValue("FastPumpAnalogOutput",globalProperties,-1);
        fastPumpMotorValve=GaugeChannel.getConfigValue("FastPumpMotorValve",globalProperties,-1);
        // if you have a vacuum pump, there is a solenoid valve that can turn on the pump
        // and a motor valve to isolate the pump
        vacuumSolenoidValve=GaugeChannel.getConfigValue("VacuumSolenoidValve",globalProperties,-1);
        vacuumMotorValve=GaugeChannel.getConfigValue("VacuumMotorValve",globalProperties,-1);
        sampleIsolationValve=GaugeChannel.getConfigValue("SampleIsolationValve",globalProperties,-1);
        samplePressureGauge=GaugeChannel.getConfigValue("SamplePressureGauge",globalProperties,-1);
        
        // first use of door lock was on motor index 4, so this is the default
        if (doorLockExists) {
            doorLockMotorIndex=(GaugeChannel.getConfigValue("DoorLockMotorIndex",globalProperties,4));
        }
        supervisorPassword=globalProperties.getProperty("SupervisorPassword", "");
        
        burstTester=(GaugeChannel.getConfigValue("BurstTester",globalProperties,0)==1);

        cycleTestEnabled=(GaugeChannel.getConfigValue("CycleTestEnabled",globalProperties,1)==1);
        
        // burst tester specific parameters
        burstTestLogging=(GaugeChannel.getConfigValue("BurstTestLogging",groupProperties,0)==1);
        burstTestLoggingAutoName=(GaugeChannel.getConfigValue("BurstTestLoggingAutoName",groupProperties,0)==1);
        burstTestLogfile=groupProperties.getProperty("BurstTestLogfile","");

        usingFastPump=(GaugeChannel.getConfigValue("UsingFastPump",groupProperties,0)==1);
        dataPath=groupProperties.getProperty("DataPath","");
    }
    
    /**
     * Creates a new instance of APCSConfig.  The caller must have already created
     * and filled a Properties object.  Caller is also responsible for storing this
     * properties object to a file before the program finishes.
     * This is a convenience method for when you are only using one properties file.
     * @param p Properties object where all configuration settings are retrieved and updated
     */
    protected APCSConfig(java.util.Properties p) {
        this(p,p);
    }
    
    private double pressureRange, fastPumpMinPressure, fastPumpMaxPressure, fastPumpIncrementTime, pressureAccurate;
    private double slowDownPressure, generatorRange, pressureTolerance;
    private double pressureRange2;
    private int maxSpeedLP, maxSpeedHP, defaultSpeed, fastPumpMinSpeed, doorLockMotorIndex;
    private int fastPumpMaxSpeed;
    private int numberOfGenerators, numberOfMotorValves, numberOfChambers;
    private int currentChamberNumber;
    private int numberOfRanges, currentRangeNumber;
    private int fastPumpAnalogOutput, fastPumpMotorValve;
    private int vacuumSolenoidValve, vacuumMotorValve;
    private int sampleIsolationValve, samplePressureGauge;
    private int ramUpSpeed, ramDownSpeed, microControlStartingSpeed, pulseHeight;
    private boolean supervisorMode, burstTestLogging, doorLockExists, burstTester;
    private boolean burstTestLoggingAutoName;
    private boolean cycleTestEnabled;
    private boolean usingFastPump;
    private boolean encoderExists;
    private double encoderFactor;
    private boolean athena;
    private boolean qcGauge;
    private int qcGaugePort;
    private String supervisorPassword, burstTestLogfile;
    private String dataPath;
    private java.util.Properties globalProperties, groupProperties;
    /** Calibration information for all analog input channels */
    private GaugeChannel gaugeChannelArray[];

    /**
     * returns the current data path
     * @return the path of the data
     */
    protected String getDataPath(){
        return dataPath;
    }

    /**
     * Sets the data path
     * @param newDataPath is the new value
     */
    protected void setDataPath(String newDataPath){
        dataPath = newDataPath;
        groupProperties.setProperty("DataPath", dataPath);
    }

    /**
     * returns the current supervisor mode
     * @return true if we are in supervisor mode
     */
    protected boolean isSupervisorMode() {
        return supervisorMode;
    }
    protected boolean hasAthena()
    {
        //y0 config file, you got an athena temperature controller?
        return athena;
    }
    protected boolean hasQCGauge()
    {
        //y0 config file, you got a QC Guge?
        return qcGauge;
    }
    protected int qcGaugePort()
    {
        //y0 config file, you got a QC Guge?
        return qcGaugePort;
    }
    /**
     * Sets supervisor password.  Note that this only works
     * if you are in supervisor mode
     * @param newPassword new supervisor password
     */
    protected void setSupervisorPassword(String newPassword) {
        if (supervisorMode==false) {
            return;
        }
        supervisorPassword=newPassword;
        globalProperties.setProperty("SupervisorPassword", newPassword);
    }
    
    /**
     * Go into supervisor mode.  You must supply the proper password.  If the
     * password does not match, but you are already in supervisor mode,
     * you will stay in supervisor mode even though this method returns
     * false.
     * @param checkPassword password
     * @return true if checkPassword matches supervisor password
     */
    protected boolean setSupervisorMode(String checkPassword) {
        if (supervisorPassword.equals(checkPassword)) {
            supervisorMode=true;
            globalProperties.setProperty("SupervisorMode", "1");
            return true;
        }
        return false;
    }
    
    /**
     * Go into user mode.  If you are already in user mode, this has no effect.
     */
    protected void setUserMode() {
        globalProperties.setProperty("SupervisorMode", "0");
        supervisorMode=false;
    }
    
    /**
     * returns the GaugeChannel object for a given channel,
     * which holds all calibration information for the channel.
     * May return a different object for channel 2 on multi-range machines
     * @param channel channel number of the object
     * @return GaugeChannel object, or null if channel number out of bounds
     */
    protected GaugeChannel getGaugeChannel(int channel) {
        if ((channel<0) || (channel>57)) {
            return null;
        }
        if ((channel==2) && (currentRangeNumber==2)) {
            return gaugeChannelArray[3];
        }
        return gaugeChannelArray[channel];
    }
    
    /**
     * sets the location of the window to either the center of the screen or the last
     * known location of this window.  The Name of the window should be set to something
     * unique, as that is what is used to remember where this window was last located
     * @param w the window
     */
    protected void setWindowPosition(java.awt.Window w) {
        w.setLocationRelativeTo(null);
        java.awt.Point loc = w.getLocation();
        // now use the current configuration settings to set the real location
        // using the current location (in the center of the screen) as the
        // default values.
        w.setLocation(GaugeChannel.getConfigValue(w.getName()+".x",globalProperties,loc.x),
                    GaugeChannel.getConfigValue(w.getName()+".y",globalProperties,loc.y));
    }
    
    /**
     * Stores the current window position to the system configuration so that it can be
     * retrieved later by the setWindowPosition method.  The window must have a unique
     * name.
     * @param w the window
     */
    protected void rememberWindowPosition(java.awt.Window w) {
        java.awt.Point loc = w.getLocation();
        globalProperties.setProperty(w.getName()+".x",""+loc.x);
        globalProperties.setProperty(w.getName()+".y",""+loc.y);
    }
    
    /**
     * Get system parameter fastPumpMinPressure, which is the pressure that is produced
     * by the fast pump when the fast pump is set to fastPumpMinSpeed.
     * @return fastPumpMinPressure in PSI
     */
    protected double getFastPumpMinPressure() {
        return fastPumpMinPressure;
    }
    
    /**
     * Get system parameter pressureAccurate, which is the tolerance used to determine
     * if we have reached a target pressure.
     * @return pressureAccurate in PSI
     */
    protected double getPressureAccurate() {
        return pressureAccurate;
    }
    
    /**
     * Set system parameter pressureAccurate, which is the tolerance used to determine
     * if we have reached a target pressure.  Should be greater than 0, or the previous value
     * will be retained.
     * @param p new pressureAccurate value, in PSI
     */
    protected void setPressureAccurate(double p) {
        if (p>0) {
            pressureAccurate=p;
            groupProperties.setProperty("PressureAccurate",""+pressureAccurate);                
        }
    }
    
    /**
     * Get system parameter pressureTolerance, which is the tolerance of the micro pressure
     * control to determine if it should go back to coarse control
     * @return pressureTolerance in PSI
     */
    protected double getPressureTolerance() {
        return pressureTolerance;
    }
    
    /**
     * Set system parameter pressureTolerance, which is the tolerance of the micro pressure
     * control to determine if it should go back to coarse control.  This should be greater
     * than the parameter pressureAccurate.
     * @param p new pressureTolerance value, in PSI
     */
    protected void setPressureTolerance(double p) {
        if (p>0) {
            pressureTolerance=p;
            groupProperties.setProperty("PressureTolerance",""+pressureTolerance);                
        }
    }
    
    /**
     * Get system parameter fastPumpMinSpeed, which is the speed that produces
     * the pressure fastPumpMinPressure.
     * @return fastPumpMinSpeed in counts (0 to 4000)
     */
    protected int getFastPumpMinSpeed() {
        return fastPumpMinSpeed;
    }
    
    /**
     * Get system parameter fastPumpMaxPressure, which is the pressure that is produced
     * by the fast pump when the fast pump is set to fastPumpMaxSpeed.
     * @return fastPumpMaxPressure in PSI
     */
    protected double getFastPumpMaxPressure() {
        return fastPumpMaxPressure;
    }
    
    /**
     * Get system parameter fastPumpMaxSpeed, which is the speed that produces
     * the pressure fastPumpMaxPressure.
     * @return fastPumpMaxSpeed in counts (0 to 4000)
     */
    protected int getFastPumpMaxSpeed() {
        return fastPumpMaxSpeed;
    }

    /**
     * Get system parameter fastPumpIncrementTime,  which is the amount of time, in seconds,
     * between increments of the fast pump while the system is trying to raise the pressure to
     * some user specified value.  The smaller you make this, the faster the pump will increase
     * the pressure and the more likely you will overshoot.
     * @return fastPumpIncrementTime in seconds
     */
    protected double getFastPumpIncrementTime() {
        return fastPumpIncrementTime;
    }
    
    /**
     * Set system parameter fastPumpIncrementTime,  which is the amount of time, in seconds,
     * between increments of the fast pump while the system is trying to raise the pressure to
     * some user specified value.  The smaller you make this, the faster the pump will increase
     * the pressure and the more likely you will overshoot.  Should be greater than 0 or the
     * previous value will be retained.
     * @param f new fastPumpIncrementTime in seconds
     */
     protected void setFastPumpIncrementTime(double f) {
         if (f>0) {
             fastPumpIncrementTime=f;
         }
        groupProperties.setProperty("FastPumpIncrementTime",""+fastPumpIncrementTime);        
     }
    
    /**
     * Get system parameter pressureRange, which is the maximum pressure of the system
     * @return pressureRange in PSI
     */
    protected double getPressureRange() {
        if ((numberOfRanges>1) && (currentRangeNumber>1)) {
            return pressureRange2;
        }
        return pressureRange;
    }
    
    /**
     * Get system parameter defaultSpeed, which is the starting speed of the generator
     * @return defaultSpeed in counts (0 to 1023)
     */
    protected int getDefaultSpeed() {
        return defaultSpeed;
    }
    
    /**
     * Set system parameter defaultSpeed, which is the starting speed of the generator
     * Constrained to be between 0 and 1023.
     * @param ds new defaultSpeed
     */
    protected void setDefaultSpeed(int ds) {
        if (ds<0) {
            defaultSpeed=0;
        }
        else if (ds>1023) {
            defaultSpeed=1023;
        }
        else {
            defaultSpeed=ds;
        }
        groupProperties.setProperty("DefaultSpeed",""+defaultSpeed);        
    }
    
    /**
     * Get system parameter maxSpeedLP, which is the maximum speed of the generator
     * when the system is at low pressure
     * @return maxSpeedLP in counts (0 to 1023)
     */
    protected int getMaxSpeedLP() {
        return maxSpeedLP;
    }
    
    /**
     * Set system parameter maxSpeedLP, which is the maximum speed of the generator
     * when the system is at low pressure.  Constrained to be between 0 and 1023.
     * @param mslp new maxSpeedLP
     */
    protected void setMaxSpeedLP(int mslp) {
        if (mslp<0) {
            maxSpeedLP=0;
        }
        else if (mslp>1023) {
            maxSpeedLP=1023;
        }
        else {
            maxSpeedLP=mslp;
        }
        groupProperties.setProperty("MaxSpeedLP",""+maxSpeedLP);        
    }

    /**
     * Get system parameter maxSpeedHP, which is the maximum speed of the generator
     * when the system is at high pressure
     * @return maxSpeedHP in counts (0 to 1023)
     */
    protected int getMaxSpeedHP() {
        return maxSpeedHP;
    }
    
    /**
     * Set system parameter maxSpeedHP, which is the maximum speed of the generator
     * when the system is at high pressure.  Constrained to be between 0 and 1023.
     * @param mshp new maxSpeedHP
     */
    protected void setMaxSpeedHP(int mshp) {
        if (mshp<0) {
            maxSpeedHP=0;
        }
        else if (mshp>1023) {
            maxSpeedHP=1023;
        }
        else {
            maxSpeedHP=mshp;
        }
        groupProperties.setProperty("MaxSpeedHP",""+maxSpeedHP);        
    }

    /**
     * Returns true if we should be logging the burst test results
     * @return true if logging is enabled
     */
    protected boolean isBurstTestLogging() {
        return burstTestLogging;
    }
    
    /**
     * Returns true if we should automatically generate the name of the log file
     * for the burst test based on the sample ID of the test
     * @return true if auto naming is enabled
     */
    protected boolean isBurstTestLoggingAutoName() {
        return burstTestLoggingAutoName;
    }
    
    /**
     * turn burst test logging on or off
     * @param b true if logging is on, false if logging is off
     */
    protected void setBurstTestLogging(boolean b) {
        burstTestLogging=b;
        if (b) {
            groupProperties.setProperty("BurstTestLogging", "1");
        }
        else {
            groupProperties.setProperty("BurstTestLogging", "0");
        }
    }
    
    /**
     * turn burst test logging auto naming on or off
     * @param b true if auto naming is on, false if auto naming is off
     */
    protected void setBurstTestLoggingAutoName(boolean b) {
        burstTestLoggingAutoName=b;
        if (b) {
            groupProperties.setProperty("BurstTestLoggingAutoName", "1");
        }
        else {
            groupProperties.setProperty("BurstTestLoggingAutoName", "0");
        }
    }
    
    /**
     * get full path and file name of burst test log file
     * @return String containing full path and name of burst test log file
     */
    protected String getBurstTestLogfile() {
        return burstTestLogfile;
    }
    
    /**
     * Set the burst test log file
     * @param s String containing full path and file name of burst test log file
     */
    protected void setBurstTestLogfile(String s) {
        burstTestLogfile=s;
        groupProperties.setProperty("BurstTestLogfile",s);
    }
    
    /**
     * Find out if the door lock exists
     * @return True if the door lock exists
     */
    protected boolean doesDoorLockExist() {
        return doorLockExists;
    }

    /**
     * Find out if the encoder exists
     * @return True if the encoder exists
     */
    protected boolean doesEncoderExist() {
        return encoderExists;
    }
    
    /**
     * Get the encoder factor.  Multiply this value by the encoder counts to get volume in cc.
     * @return factor
     */
    protected double getEncoderFactor() {
        if (encoderExists) {
            return encoderFactor;
        }
        else {
            return 0;
        }
    }
    
    /**
     * Get the motor valve index number of the door lock
     * @return index value (0 based) of door lock solenoid or -1 if no door lock exists
     */
    protected int getDoorLockMotorIndex() {
        if (doorLockExists) {
            return doorLockMotorIndex;
        }
        else {
            return -1;
        }
    }

    /**
     * Get the solenoid valve index number of the sample isolation valve
     * @return index value (0 based) of sample isolation valve or -1 if no sample isolation valve exists
     */
    protected int getSampleIsolationValve() {
        return sampleIsolationValve;
    }

    /**
     * Get the gauge index number of the sample pressure gauge
     * @return index value (0 based) of the sample pressure gauge or -1 if no sample pressure gauge exists
     */
    protected int getSamplePressureGauge() {
        return samplePressureGauge;
    }
    
    /**
     * Get the solenoid valve index number of the vacuum pump
     * @return index value (0 based) of vacuum pump solenoid or -1 if no vacuum pump exists
     * (or if the vacuum pump can not be turned on or off)
     */
    protected int getVacuumSolenoidValve() {
        return vacuumSolenoidValve;
    }

    /**
     * Get the motor valve index number of the vacuum pump
     * @return index value (0 based) of vacuum pump motor valve or -1 if no vacuum pump exists
     * (or the vacuum pump does not have an isolation motor valve)
     */
    protected int getVacuumMotorValve() {
        return vacuumMotorValve;
    }

    /**
     * Get the motor valve index number of the fast pump
     * @return index value (0 based) of fast pump motor valve or -1 if no fast pump exists
     * (or the fast pump does not have an isolation motor valve)
     */
    protected int getFastPumpMotorValve() {
        return fastPumpMotorValve;
    }

    /**
     * Get the analog output index number of the fast pump
     * @return index value (0 based) of fast pump analog output or -1 if no fast pump exists
     */
    protected int getFastPumpAnalogOutput() {
        return fastPumpAnalogOutput;
    }

    /**
     * Get the number of generators on the system
     * @return currently either 1 or 2
     */
    protected int getNumberOfGenerators() {
        return numberOfGenerators;
    }

    /**
     * Get the number of chambers on the system
     * @return currently either 1 or 2
     */
    protected int getNumberOfChambers() {
        return numberOfChambers;
    }

    /**
     * Get the number of ranges on the system
     * @return currently either 1 or 2
     */
    protected int getNumberOfRanges() {
        return numberOfRanges;
    }

    /**
     * Get the number of motorized valves on the system
     * @return currently either 2 or 4
     */
    protected int getNumberOfMotorValves() {
        return numberOfMotorValves;
    }
    
    /**
     * Get the current chamber on the system
     * @return currently either 1 or 2
     */
    protected int getCurrentChamberNumber() {
        return currentChamberNumber;
    }

    /**
     * Get the current range on the system
     * @return currently either 1 or 2
     */
    protected int getCurrentRangeNumber() {
        return currentRangeNumber;
    }

    /**
     * Set the current chamber number for the system
     * @param i the new current chamber number (1 or 2)
     */
    protected void setCurrentChamberNumber(int i) {
        if (i<=1) {
            currentChamberNumber=1;
        }
        else if (i>=numberOfChambers) {
            currentChamberNumber=numberOfChambers;
        }
        else {
            currentChamberNumber=i;
        }
        globalProperties.setProperty("CurrentChamberNumber",""+currentChamberNumber);
    }

    /**
     * Set the current range number for the system
     * @param i the new current range number (1 or 2)
     */
    protected void setCurrentRangeNumber(int i) {
        if (i<=1) {
            currentRangeNumber=1;
        }
        else if (i>=numberOfRanges) {
            currentRangeNumber=numberOfRanges;
        }
        else {
            currentRangeNumber=i;
        }
        globalProperties.setProperty("CurrentRangeNumber",""+currentRangeNumber);
    }

    /**
     * Get the pressure range of the generator
     * @return range of pressure generator, in PSI
     */
    protected double getGeneratorRange() {
        return generatorRange;
    }
    
    /**
     * Get the Ram Up Speed - the initial speed to use
     * when starting a pressurize cycle
     * @return generator speed (1-1023)
     */
    protected int getRamUpSpeed() {
        return ramUpSpeed;
    }
    
    /**
     * Set the Ram Up Speed - the initial speed to use
     * when starting a pressurize cycle
     * @param i the new ram up speed (1-1023)
     */
    protected void setRamUpSpeed(int i) {
        if (i<0) {
            ramUpSpeed=0;
        }
        else if (i>=1023) {
            ramUpSpeed=1023;
        }
        else {
            ramUpSpeed=i;
        }
        groupProperties.setProperty("RamUpSpeed",""+ramUpSpeed);        
    }
    
    /**
     * Get the Ram Down Speed - the initial speed to use
     * when starting a depressurize cycle
     * @return generator speed (-1 to -1023)
     */
    protected int getRamDownSpeed() {
        return ramDownSpeed;
    }
    
    /**
     * Set the Ram Down Speed - the initial speed to use
     * when starting a depressurize cycle
     * @param i the new ram up speed (-1 to -1023)
     */
    protected void setRamDownSpeed(int i) {
        if (i>0) {
            ramDownSpeed=0;
        }
        else if (i<= -1023) {
            ramDownSpeed= -1023;
        }
        else {
            ramDownSpeed=i;
        }
        groupProperties.setProperty("RamDownSpeed",""+ramDownSpeed);        
    }
    
    /**
     * Get the Slow Down Pressure
     * @return slow down pressure in PSI
     */
    protected double getSlowDownPressure() {
        return slowDownPressure;
    }
    
    /**
     * Set the Slow Down Pressure
     * @param d the new slow down pressure (in PSI)
     */
    protected void setSlowDownPressure(double d) {
        slowDownPressure=d;
        groupProperties.setProperty("SlowDownPressure",""+slowDownPressure);
    }
    
    /**
     * Get the Micro Control Starting Speed - the initial speed to use
     * when starting a micro pressurize cycle
     * @return generator speed (1-1023)
     */
    protected int getMicroControlStartingSpeed() {
        return microControlStartingSpeed;
    }
    
    /**
     * Set the Micro Control Starting Speed - the initial speed to use
     * when starting a micro pressurize cycle
     * @param i the new micro control starting speed (1-1023)
     */
    protected void setMicroControlStartingSpeed(int i) {
        if (i<0) {
            microControlStartingSpeed=0;
        }
        else if (i>=1023) {
            microControlStartingSpeed=1023;
        }
        else {
            microControlStartingSpeed=i;
        }
        groupProperties.setProperty("MicroControlStartingSpeed",""+microControlStartingSpeed);        
    }
    
    /**
     * Get the pulse height - the maximum speed when pulsing the generator
     * @return generator speed (1-1023)
     */
    protected int getPulseHeight() {
        return pulseHeight;
    }
    
    /**
     * Set the pulse height - the maximum speed when pulsing the generator
     * @param i the new pulse height (1-1023)
     */
    protected void setPulseHeight(int i) {
        if (i<0) {
            pulseHeight=0;
        }
        else if (i>=1023) {
            pulseHeight=1023;
        }
        else {
            pulseHeight=i;
        }
        groupProperties.setProperty("PulseHeight",""+pulseHeight);        
    }
    
    /**
     * global configuration parameter to determine if this is a burst tester or not
     * @return true if this is a burst tester
     */
    protected boolean isBurstTester() {
        return burstTester;
    }

    protected boolean areUsingFastPump() {
        return usingFastPump;
    }

    protected void setUsingFastPump(boolean b) {
        usingFastPump=b;
        if (b) {
            groupProperties.setProperty("UsingFastPump","1");
        }
        else {
            groupProperties.setProperty("UsingFastPump","0");
        }
    }

    boolean isCycleTestEnabled() {
        return cycleTestEnabled;
    }

}
