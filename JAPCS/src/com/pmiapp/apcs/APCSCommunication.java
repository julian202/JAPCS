/*
 * APCSCommunication.java
 * Main routines for communicating with APCS
 * 
 * based on DMPCCommunication 8-31-05
 * Still to be implemented:  solenoid valve pulsing, feature number,
 *  and writable parameters.
 */

package com.pmiapp.apcs;
//import java.util.*;
import gnu.io.CommPortIdentifier;
import java.io.File;

/** All communication routines are found here.
 * Tries to create an instance of RawCommunication, which uses the
 * RXTX.org package.  If the package is not present, will set itself
 * to demo mode.
 *
 * @author  Ron V. Webber
 */
public class APCSCommunication {
    
    /**
     * Creates a new instance of APCSCommunication.
     *  Initializes all gauge calibration based on configuration settings
     * @param configSettings APCSConfig object that contains system configuration settings
     */
    protected APCSCommunication(APCSConfig configSettings) {
        // rawCommunication holds all the stuff that can't be instanced without
        // the RXTX communication package.  It was moved there so you could still
        // call APCSCommunication in demo mode without RXTX being installed
        // call with version set to 6 so we use the older protocol and speed
        rawC = null; // just in case
        try { rawC = new com.pmiapp.common.RawCommunication(6); }
        catch (NoClassDefFoundError e) {}
        this.configSettings=configSettings;
        initializeEverythingElse();
    }
    
    /**
     * Creates a new instance of APCSCommunication.
     *  Initializes all gauge calibration based on configuration settings
     * @param configSettings APCSConfig object that contains system configuration settings
     * @param statusFile file where status information should be written
     */
    protected APCSCommunication(APCSConfig configSettings, File statusFile) {
        rawC = null; // just in case
        try { rawC = new com.pmiapp.common.RawCommunication(6, statusFile); }
        catch (NoClassDefFoundError e) {}
        this.configSettings=configSettings;
        initializeEverythingElse();
    }
    
    private void initializeEverythingElse() {
        apos = new int[8];
        vpos = new int[3];
        demo=true; // default to demo mode unless they open a port
        numReadingsSoFar=0;
    }
    
    /** close the currently open communication port.
     *  This puts us into demo mode
     */
    protected void closePort() {
        if ((demo==false) && (rawC!=null))
        {
            rawC.closePort();
        }
        demo=true; // back to demo mode when port is closed
    }
    
    /** get the current configuration settings
     * @return the current configuration settings
     */
    protected APCSConfig getConfigSettings() {
        return configSettings;
    }
    /** find out of we are in demo mode or not
     * @return true if we are in demo mode
     */
    protected boolean isDemoMode() {
        return demo;
    }
    /**
     * Try to open a communication port.
     *  Will close an open port, if one is open, before trying to open
     * @param cpi CommPortIdeitifier object of the port you want to open
     * @return true if the port opened<br>
     * false if the port not opened (now in demo mode)
     */
    protected boolean openPort(CommPortIdentifier cpi) {
        if ((demo==false) && (rawC!=null))
        {
            // close the port first
            rawC.closePort();
        }
        boolean b;
        if (rawC!=null)
        {
            b=rawC.openPort(cpi,"apcs");
        }
        else
        {
            b=false;
        }
        demo= !b;
        return b;
    }
    /** get the last known status of a solenoid valve.
     *  Does not actually read anything from the instrument
     *
     * @param i valve number 0 through 2
     * @return 0 if valve is closed<br>
     * 1 if valve is open<br>
     * -1 if i out of bounds
     */
    protected int getVpos(int i){
        if ((i<0) || (i>2))
        {
            return -1;
        }
        else
        {
            return vpos[i];
        }
        
    }
    /** get the last known analog position.  Does not actually read anything
     *  from the instrument
     *
     * @param i analog output number 0 through 7
     * @return current position value 0 through 4000<br>
     * -1 on i out of bounds
     */
    protected int getApos(int i){
        if ((i<0) || (i>7)) 
        {
            return -1;
        }
        else
        {
            return apos[i];
        }
    }
    /** decrement analog output.
     *  Will generate multiple serial commands if necessary
     *
     * @param i analog output number 0 through 7
     * @param x any positive count value
     * @return current count value of analog output channel, 0 to 4000<br>
     * 0 if i out of bounds
     */
    protected int decAout(int i, int x) {
        String s;
        if ((i<0) || (i>7))
        {
            return 0;
        }
        s = "B" + (char)('1' + i);
        while (x>255) {
            serialMain(s,0,1,255);
            x-=255;
            apos[i]-=255;
        }
        serialMain(s,0,1,x);
        apos[i]-=x;
        if (apos[i]<0) 
        {
            apos[i]=0;
        }
        return apos[i];
    }
    /** increment analog output.
     *  Will generate multiple serial commands if necessary
     *
     * @param i analog output number 0 through 7
     * @param x any positive count value
     * @return current count value of analog output channel, 0 to 4000<br>
     * 0 if i out of bounds
     */
    protected int incAout(int i, int x) {
        String s;
        if ((i<0) || (i>7))
        {
            return 0;
        }
        s = "U" + (char)('1' + i);
        while (x>255) {
            serialMain(s,0,1,255);
            x-=255;
            apos[i]+=255;
        }
        serialMain(s,0,1,x);
        apos[i]+=x;
        if (apos[i]>4000) {
            apos[i]=4000;
        }
        return apos[i];
    }
    /** set analog output to 0 counts
     *
     * @param i analog output number 0 through 7
     */
    protected void zeroAout(int i) {
        if ((i<0) || (i>7)) {
            return;
        }
        serialMain("Z" + (char)('1'+i),0,0,0);
        apos[i]=0;
    }
    /** set analog output to any count value.
     * Will increment, decrement, or zero as needed
     * @param i analog output number 0 through 7
     * @param x set point, 0 to 4000
     */
    protected void setAout(int i, int x) {
        if ((i<0) || (i>7)) {
            return;
        }
        if (x==0) {
            zeroAout(i);
            return;
        }
        if (x==apos[i]) {
            return;
        }
        if (x<apos[i]) {
            decAout(i,apos[i]-x);
        } else {
            incAout(i,x-apos[i]);
        }
    }

    /**
     * Zero the encoder attached to a generator
     * @param i generator number (0 or 1)
     */
    protected void zeroEncoder(int i) {
        if ((i<0) || (i>1)) {
            return;
        }
        serialMain("Z" + (char)('A'+i),0,0,0);
    }

    /** Read the encoder value attached to a generator
     * @param i generator number (0 or 1)
     * @return encoder value (any integer, plus or minus)
     */
    protected int readEncoder(int i) {
        if ((i<0) || (i>1)) {
            return 0;
        }
        return serialMain("R"+(char)('D'+i),3,0,0);
    }

// this is from the dmpc communication protocol, but is not supported in the APCS protocol (yet)
//    /** read the actual current analog output.
//     * Communicates with machine to do this.
//     * Updates internal current value of analog output
//     * which is available through getApos.
//     * If you haven't called this before, and you 
//     * haven't zeroed the channel, getApos may not return
//     * the actual right number.
//     *
//     * @param i analog output number 0 through 7
//     * @return current count value 0 through 4000<br>
//     * 0 if i out of bounds
//     */
//    protected int readAout(int i) {
//        if ((i<0) || (i>7)) return 0;
//        int j=serialMain("R"+(char)('1'+i),2,0,0);
//        if (j<0) j=0;
//        if (j>4000) j=4000;
//        apos[i]=j;
//        return j;
//    }
    /** Move solenoid valve
     *
     * @param i solenoid valve number 0 through 2
     * @param c 'O' for Open, 'C' for Close
     */
    protected void moveValve(int i, char c) {
        if ((i<0) || (i>2)) {
            return;
        }
        if ((c!='O') && (c!='C')) {
            return;
        }
        serialMain(""+c+(char)('x'+i),0,0,0);
        if (c=='O') {
            vpos[i]=1;
        } else {
            vpos[i]=0;
        }
    }
    /** Control motorized valve.
     * valve number 1 may be remapped on multi chamber machines
     * valve number 0 may be remapped on multi range machines
     *
     * @param i motorized valve number 0 through 15
     * @param c 'O' for start Opening, 'C' for start Closing,
     *          'S' for Stop (increment and decrement are not supported)
     */
    protected void controlMotor(int i, char c) {
        int offset=0;
        if ((i<0) || (i>15)) {
            return;
        }
        if ((c!='O') && (c!='C') && (c!='S')) {
            return;
        }
        if ((i==1) && (configSettings.getCurrentChamberNumber()==2)) {
            offset=1;
        }
        if ((i==0) && (configSettings.getCurrentRangeNumber()==2)) {
            offset=3;
        }
        serialMain(""+c+(char)('1'+i+offset),0,0,0);
    }
//    /** Make the motor valve go to a specific target position
//     * 
//     * @param i motorized valve number 0 through 1
//     * @param o 1 if you can only move in the open direction,
//     *          -1 if you can only move in the close direction,
//     *          0 if you can move in whichever direction necessary
//     * @param target target count value
//     */
//    protected void motorValveGoto(int i, int o, int target) {
//        String s;
//        if ((i<0) || (i>1)) return;
//        if (o==1) s="G+";
//        else if (o==-1) s="G-";
//        else s="G";
//        if (target<0) target=0;
//        if (target>65535) target=65535;
//        serialMain(s+(char)('1'+i),0,2,target);
//    }
    /** get status of motor
     * valve number 1 may be remapped on multi chamber machines
     * valve number 0 may be remapped on multi range machines
     *
     * @param i motorized valve number 0 through 15
     * @return -1 if valve is currently moving in close direction<br>
     * 1 if valve is currently moving in open direction<br>
     * 0 if valve is currently stopped<br>
     * 0 if i is out of bounds
     */
    protected int getMotorStatus(int i) {
        int offset=0;
        if ((i<0) || (i>15)) {
            return 0;
        }
        if ((i==1) && (configSettings.getCurrentChamberNumber()==2)) {
            offset=1;
        }
        if ((i==0) && (configSettings.getCurrentRangeNumber()==2)) {
            offset=3;
        }
        int j=serialMain("V"+(char)('1'+i+offset),1,0,0);
        if (j=='C') {
            return -1;
        }
        if (j=='O') {
            return 1;
        }
        return 0;
    }
//    /** get status of a solenoid valve
//     *
//     * @param i solenoid valve number 0 through 15
//     * @return -1 if valve is open<br>
//     * 1 if valve is cloased<br>
//     * 0 if valve status is unknown<br>
//     * 0 if i is out of bounds
//     */
//    protected int getSolenoidStatus(int i) {
//        if ((i<0) || (i>15)) return 0;
//        int j=serialMain("V"+(char)('A'+i),1,0,0);
//        if (j=='C') { vpos[i]=0; return -1; }
//        if (j=='O') { vpos[i]=1; return 1; }
//        return 0;
//    }
//    /** set misc byte values used in reading and valve movement
//     *
//     * @param c 'i' to set "ignore" or "delay" value for reading,
//     *          'm' to set "multiple" or "average" value for reading,
//     *          'J' to set "Jiffy" timer values for valve movement
//     * @param i byte value to send (0 through 255),<br> 
//     *          For c=='m', an i value of 0 really means 256
//     */
//    protected void setMiscValue(char c, int i) {
//        if ((c!='i') && (c!='m') && (c!='J')) return;
//        if (c=='i') iValue=i;
//        if (c=='m') mValue=i;
//        serialMain(""+c,0,1,i);
//    }
    

    /** move motorized valve and wait for it to stop.  May be aborted.
     * valve number 1 may be remapped on multi chamber machines
     * valve number 0 may be remapped on multi range machines
     * @param valveNum motor valve number 0 to 15
     * @param direction 'O' for open, 'C' for close
     * @param abortCaller calling routine that can be queried to see if we should abort
     */
    protected void moveMotorValveAndWait(int valveNum, char direction,
            com.pmiapp.common.AbortQueriable abortCaller) {
        if (abortCaller.shouldStop()==false) {
                    controlMotor(valveNum,direction);
                }
        // wait for it to stop
        while ((getMotorStatus(valveNum)!=0) && (abortCaller.shouldStop()==false))
        { // intentional empty loop
        }
        controlMotor(valveNum,'S');
    }
        
    /** move motorized valve and wait for it to stop.  May be aborted.
     * valve number 1 may be remapped on multi chamber machines
     * valve number 0 may be remapped on multi range machines
     * Keeps reading pressure channel (2) and returning the value while it is waiting.
     * @param valveNum motor valve number 0 to 15
     * @param direction 'O' for open, 'C' for close
     * @param abortCaller calling routine that can be queried to see if we should abort
     * @param gaugeReadListener calling routine that can be send gauge readings while we wait
     * @param channel the damn channel....THANKS RON!  LONG LIVE V9 CONTROL!!
     * @param temp the damn temperature....THANKS RON! C# 4 LYF
     */
    
   
    protected void setAthenaTemp(int channel, String temp)
    {
        if(!demo)
        {
            System.out.println("Sending: TS"  + temp);
            sendRawCharacters("TS" + temp);
        }
    }
    protected void readAthena2()
    {
        if(!demo) System.out.println ("Result: " + serialMainString("TT",6));
    }
    protected double readAthenaTemp()
    {
        //read athena temperature controller channel
        if(!demo)
        {
            System.out.println("Sending: TT");
            int raw = serialMain("TT",2,0,0);
            return raw;
        }
        else
        {
            return 0.0;
        }
    }
    
    protected void moveMotorValveAndWait(int valveNum, char direction,
            com.pmiapp.common.AbortQueriable abortCaller,
            com.pmiapp.common.GaugeReadListener gaugeReadListener) {
        if (abortCaller.shouldStop()==false) {
                    controlMotor(valveNum,direction);
                }
        // wait for it to stop
        while ((getMotorStatus(valveNum)!=0) && (abortCaller.shouldStop()==false)) {
                    gaugeReadListener.gaugeReadData(2, rawReading(2),0);
                }
        controlMotor(valveNum,'S');
    }
        
    /** set the generator speed
     * @param i generator number (0 or 1)
     * @param speed -1023 to 1023
     */
    protected void setPGenSpeed(int i, int speed) {
        if ((i<0) || (i>1)) {
            return;
        }
        if (speed<-1023) {
            speed=-1023;
        }
        if (speed>1023) {
            speed=1023;
        }
        serialMain("P"+(char)('A'+i),0,2,speed);
    }
    /** turn the calibration relay on or off
     * @param b true if relay is to be turned on
     */
    protected void setRelay(boolean b) {
        if (b) {
            serialMain("+", 0,0,0);
        }
        else {
            serialMain("-",0,0,0);
        }
    }
    /** read the limit switch of a pressure generator
     * @param i generator number (0 or 1)
     * @return -1 for reverse limit, 1 for forward limit, 0 for neither
     */
    protected int getPGenLimit(int i) {
        if ((i<0) || (i>1)) {
            return 0;
        }
        int j=serialMain("L"+(char)('A'+i),1,0,0);
        if (j=='F') {
            return 1;
        }
        if (j=='R') {
            return -1;
        }
        return 0;
    }
    /** read the digital input
     * this is usually used for the door switch
     * @param i digital input number (0)
     * @return 0 to 255
     */
    protected int getDigitalInput(int i) {
        if (i!=0) {
            return 0;
        }
        return serialMain("R@", 1, 0, 0);
    }
    /** read the count value of an analog input channel
     * channels 13, 14, and 15 may be remapped on multi chamber machines
     * channels 2, 10, 11, and 12 may be remapped on multi range machines
     *
     * @param i analog input channel number (0-57)
     * @return count value (0 to 65535)<br>
     * 0 if i out of bounds
     */
    protected int rawReading(int i) {
        if ((i<0) || (i>57)) {
            return 0;
        }
        int offset=0;
        if ((i>=13) && (i<=15) && (configSettings.getCurrentChamberNumber()==2)) {
            offset=3;
        }
        if ((i>=10) && (i<=12) && (configSettings.getCurrentRangeNumber()==2)) {
            offset=9;
        }
        if ((i==2) && (configSettings.getCurrentRangeNumber()==2)) {
            offset=1;
        }
        numReadingsSoFar++;
        return serialMain("R"+(char)('A'+i+offset),3,0,0);
    }
    
    /**
     * Send the character string to the instrument, one character at a time,
     * waiting for echo, ignore any errors
     * @param s the character string to send
     */
    protected void sendRawCharacters(String s) {
        serialMain(s,0,0,0);
    }
    
    /**
     * Get a character from the serial port.  Will not block or timeout
     * @return character, -1 for buffer empty, -2 for error.  In demo mode, always returns -1.
     */
    protected int getRawCharacter() {
        if (demo) {
            return -1;
        }
        return rawC.serialGetRaw();
    }
    
    /** main serial port communication routine.  Calls RawCommunication object
     * if you are not in demo mode, otherwise returns demo values.
     * This is private because only this class should directly talk to the machine
     *
     * @param cmd command string, usually 1 or 2 characters
     * @param numReturnBytes number of bytes expected to be returned, 0, 1, 2, or 3 (or 4)
     * @param numExtraSendBytes number of extra bytes to send after the command string
     * @param extraBytes the extra bytes that are actually sent (usually for numeric values),
     *        if numExtraSendBytes==0, extraBytes is ignored,
     *        if numExtraSendBytes==1, only lower 8 bits of extraBytes is sent,
     *        if numExtraSendBytes==2, lower 16 bits of extraBytes is sent
     * @return passes back return count value from RawCommunication.serialMain
     * <br> 0 if in demo mode and numReturnBytes=0
     * <br> 1 if in demo mode and numReturnBytes=1
     * <br> 0 if in demo mode and numReturnBytes=2
     * <br> 2000 if in demo mode and numReturnBytes=3
     * <br> 0 if in demo mode and numReturnBytes=4
     */
    private int serialMain(String cmd, int numReturnBytes, int numExtraSendBytes, int extraBytes) {
        if (demo) {
            // delay for 2 ms so things don't just hang
            try { Thread.sleep(2); }
            catch (InterruptedException e) {}
            if (numReturnBytes==1) {
                return 1;
            }
            if (numReturnBytes==2) {
                return 0;
            }
            if (numReturnBytes==3) {
                return 2000;
            } // DAC_Zero may go here later
            return 0; // all other cases (which should be NumReturnBytes==0) (or 4)
        }
        return rawC.serialMain(cmd,numReturnBytes,numExtraSendBytes, extraBytes);
    }
    private String serialMainString(String cmd, int returnChars)
    {
        return rawC.serialMainString(cmd, returnChars);
    }
    /** returns number of serial port retries have happened since we opened the port
     *
     * @return number of times serial port has had an error
     */
    protected long getNumhangs() {
        if (rawC==null) {
            return 0;
        }
        else {
            return rawC.getNumhangs();
        }
    }
//    /** returns current ignore value setting
//     *
//     * @return current ignore value, 0-255
//     */
//    protected int getIgnoreSetting() {
//        return iValue;
//    }
//    /** returns current multiplier value setting
//     *
//     * @return current multiplier value, 0-255
//     */
//    protected int getMultSetting() {
//        return mValue;
//    }
//    /** returns number of readings so far, since we initialized or cleared
//     *
//     * @return number of calls made to rawReading since we initialized or cleared
//     */
    /**
     * returns number of readings since we started (or the last call to clearNumReadingsSoFar)
     * @return Number of readings since start or last clear
     */
    protected int getNumReadingsSoFar() {
        return numReadingsSoFar;
    }
    /** clear the number of readings so far */
    protected void clearNumReadingsSoFar() {
        numReadingsSoFar=0;
    }
    
    private boolean rtsDemo, dtrDemo;
    
    /** set RTS
     * @param b value to set to RTS
     */
    protected void setRTS(boolean b) {
        if ((rawC==null) || (demo)) {
            rtsDemo=b;
        }
        else {
            rawC.setRTS(b);
        }
    }
    
    /** set DTR
     * @param b value to set to DTR
     */
    protected void setDTR(boolean b) {
        if ((rawC==null) || (demo)) {
            dtrDemo=b;
        }
        else {
            rawC.setDTR(b);
        }
    }
    
        /**
     * get value of CTS
     * @return value of CTS
     */
    public boolean isCTS() {
        if ((rawC==null) || (demo)) {
            return rtsDemo;
        }
        else {
            return rawC.isCTS();
        }
    }
   
    /**
     * get value of DSR
     * @return value of DSR
     */
    public boolean isDSR() {
        if ((rawC==null) || (demo)) {
            return dtrDemo;
        }
        else {
            return rawC.isDSR();
        }
    }
   
    // this is taken from the VB6 dmpc_communication module
    private int vpos[]; // position of solenoid valves, either 0 or 1
    private int apos[]; // last known setting of analog outputs
    private boolean demo; // true if we are in demo mode
    private com.pmiapp.common.RawCommunication rawC; // is null if we can't open any comm port
//    private int iValue, mValue; // ignore and mult values
    private int numReadingsSoFar; // number of analog readings we have done since we started or were cleared
    private APCSConfig configSettings;

} // close of APCSCommunication