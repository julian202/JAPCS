/*
 * Main.java
 *
 * Created on August 31, 2005, 10:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.pmiapp.apcs;
import java.io.File;

/**
 * Main entry point to APCS program.  Puts up splash screen, initializes things, and
 * then calls TitleScrn.
 * @author Ron V. Webber
 */
public class Main {
    
    /**
     * Main object
     */
    public Main() {
    }
    
    /** main entry point to APCS program
     * @param args the command line arguments, first argument is option run path
     */
    public static void main(String[] args) {
        String arg1;
        Boolean autoStart = false;

        SplashScreen s = new SplashScreen();
        s.setVisible(true);
        String ep;

        if (args.length>0){
            if (args[0].toUpperCase().contains("AUTOSTART")){
                autoStart = true;
                ep = System.getProperty("user.dir");
            }else{
                ep=args[0];
            }
        }else{
            ep = System.getProperty("user.dir");
        }
        //System.out.println(System.getProperty("java.library.path"));
        com.pmiapp.common.UserPathSupport ups=null;
        try { ups = new com.pmiapp.common.UserPathSupport(s.getRootPane(),ep,"apcs"); }
        catch (IllegalArgumentException e) {
            javax.swing.JOptionPane.showMessageDialog(s.getRootPane(),e.getMessage());
            System.exit(0);
        }
        if (ups.isFirstTime()) {
            // do first-time initialization stuff
            ups.copyToUser("apcs.config");
            File f=new File(ups.getUserPath(),"data");
            f.mkdir();
            f=new File(ups.getUserPath(),"parms");
            f.mkdir();
            ups.copyDirToUser("data"+File.separator+"examples");
            ups.copyDirToUser("parms"+File.separator+"examples");
        }

        //autoStart = true;
        TitleScrn t = new TitleScrn(ep, ups.getUserPath(), autoStart);

        // if we need anything else from ups, we will have to get it here
        s.dispose();
    }
    
    /**
     * Support function that can be used by anyone.  Takes valve position, open limit, and close
     * limit and returns a string with the percent open.  Will not return less than 0% or
     * greater than 100%.  If the open limit is less than or equal to the close limit, will return
     * plain string with the position integer and no percent sign on the end.
     * @param position Position of valve
     * @param openLimit Open limit of valve
     * @param closeLimit Close limit of valve
     * @return String with percent open
     */
    public static String convertValvePosition(int position, int openLimit, int closeLimit) {
        if (openLimit<=closeLimit) return ""+position;
        double f=(position - closeLimit) * 100. / (openLimit-closeLimit);
        if (f<0) f=0;
        else if (f>100) f=100;
        return String.format("%5.1f%%", f);
    }
    
}
