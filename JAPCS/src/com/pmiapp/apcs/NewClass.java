package com.pmiapp.apcs;
import gnu.io.CommPortIdentifier;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Andy
 */
public class NewClass {
    private com.pmiapp.common.RawCommunication rawC2;
    private boolean demo;
    
    protected NewClass()
    {
        rawC2 = null; // just in case
        try { rawC2 = new com.pmiapp.common.RawCommunication(6); }
        catch (NoClassDefFoundError e) {}
        
        
    }
    protected void closePort() {
        if ((demo==false) && (rawC2!=null))
        {
            rawC2.closePort();
        }
        demo=true; // back to demo mode when port is closed
    }
    
    protected boolean openPort(CommPortIdentifier cpi) {
        if ((demo==false) && (rawC2!=null))
        {
            // close the port first
            rawC2.closePort();
        }
        boolean b;
        if (rawC2!=null)
        {
            b=rawC2.openPort(cpi,"apcs");
        }
        else
        {
            b=false;
        }
        demo= !b;
        return b;
    }
    
    
    private String serialMainString(String cmd, int returnChars)
    {
        return rawC2.serialMainString(cmd, returnChars);
    }
}
