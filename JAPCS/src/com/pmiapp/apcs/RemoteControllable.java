/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.pmiapp.apcs;

/**
 *
 * @author Ron
 */
public interface RemoteControllable extends com.pmiapp.common.Notifiable {

    public String processCommand(String line);
    
}
