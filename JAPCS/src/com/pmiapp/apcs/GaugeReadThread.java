/*
 * GaugeReadThread.java
 *
 * Created on September 1, 2005, 3:10 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.pmiapp.apcs;

/**
 * Background thread that reads gauge channels and sends information back to caller.
 * Calling object must implement GaugeReadListener interface.
 * @author Ron V. Webber
 */
public class GaugeReadThread extends java.lang.Thread {
    
    /**
     * Creates a new instance of GaugeReadThread, but does not start it running.
     * Caller must turn on at least one gauge channel using setGaugeChannel before
     * starting this thread with run.  When there are no more channels to read
     * (all channels have been turned off using setGaugeChannel) this thread will
     * terminate.  You can also terminate by calling "pleaseStop" (which stops after the current
     * read is finished).
     * @param listener Object that implements GaugeReadListener.  The listener will receive gauge
     * readings as they become available.
     * @param commSystem Open communication system
     * @param tag tag for this thread, for future use with multiple gauge read threads active for
     * a single listener.  currently unused.
     */
    public GaugeReadThread(com.pmiapp.common.GaugeReadListener listener, APCSCommunication commSystem, int tag) {
        this.listener=listener;
        this.commSystem=commSystem;
        channelEnable=new boolean[58];
        for (int i=0; i<58; i++) {
            channelEnable[i]=false;
        }
        limitEnable=false;
        limitEnable2=false;
        wantToStop=false;
        doorSwitchEnable=false;
        encoderEnable=false;
    }

    /**
     * Enable or Disable a given channel.  At least one channel must be enabled before the
     * thread is run.  If no channels are enabled, this thread will terminate itself.
     * channel -1 is the limit switches, channnel -2 is the door switch,
     * channel -3 is the limit switches of generator 2.
     * channel -4 is the encoder of generator 1.
     * @param channel Channel number to enable or disable
     * @param b true for enable, false for disable
     */
    public void setGaugeChannel(int channel, boolean b) {
        if (channel==-1) {
            limitEnable=b;
        }
        else if (channel==-2) {
            doorSwitchEnable=b;
        }
        else if (channel==-3) {
            limitEnable2=b;
        }
        else if (channel==-4) {
            encoderEnable=b;
        }
        else if ((channel>=0) && (channel<=57)) {
            channelEnable[channel]=b;
        }
    }
    
    /**
     * Stop thread after current read is done
     */
    public void pleaseStop() {
        wantToStop=true;
    }
    
    /**
     * start the thread running.  Thread will continue to run, sending responses to the listener,
     * until all channels are disabled.
     */
    @Override
    public void run() {
        int numSkipped=0;
        int i=0;
        // if you go around twice without finding anything to do, stop trying
        while (wantToStop==false){//(numSkipped<120) && (wantToStop==false)) {
            if (i==-1) {
                // special case of limit switch
                if (limitEnable) {
                    listener.gaugeReadData(-1, commSystem.getPGenLimit(0), 0);
                    numSkipped=0;
                }
                else {
                    numSkipped++;
                }
            }
            else if (i==-2) {
                // special case of door switch
                if (doorSwitchEnable) {
                    listener.gaugeReadData(-2, commSystem.getDigitalInput(0), 0);
                    numSkipped=0;
                }
                else {
                    numSkipped++;
                }
            }
            else if (i==-3) {
                // special case of limit switch 2
                if (limitEnable2) {
                    listener.gaugeReadData(-3, commSystem.getPGenLimit(1), 0);
                    numSkipped=0;
                }
                else {
                    numSkipped++;
                }
            }
            else if (i==-4) {
                // special case of encoder of generator 1
                if (encoderEnable) {
                    listener.gaugeReadData(-4, commSystem.readEncoder(0),0);
                    numSkipped=0;
                }
                else {
                    numSkipped++;
                }
            }
            else if (channelEnable[i]) {
                listener.gaugeReadData(i,commSystem.rawReading(i), 0);
                numSkipped=0;
            }
            else {
                numSkipped++;
            }
            i++;
            if (i>=58) {
                i=-4;
            }
            // yield to any other waiting thread to allow button clicks that
            // cause commands to be sent to work better on really slow computers
            // This shouldn't be needed, and the current practice is to avoid it
            //yield();
        }
    }
    
    private boolean limitEnable, wantToStop, doorSwitchEnable, limitEnable2;
    private boolean[] channelEnable;
    private com.pmiapp.common.GaugeReadListener listener;
    private APCSCommunication commSystem;
    private boolean encoderEnable;
}
