/*
 * TpfFilter.java
 *
 * Based on TxtFilter in common
 */

package com.pmiapp.apcs;

import java.io.File;
import javax.swing.filechooser.FileFilter;

/**
 * Filter for JFileChooser - allows filtering of tpf files
 * @author Ron V. Webber
 */
public class TpfFilter extends FileFilter {
        /**
         * Accept all directories and all tpf files.
         * @param f File to test to see if it should be accepted or not
         * @return True if this file is a directory or ends in .tpf
         */
        public boolean accept(File f) {
            if (f.isDirectory()) return true;
            // note that getExtension always returns lower case
            return getExtension(f).equals("tpf");
        }

        /**
         * The description of this filter
         * @return description "Target Pressure Files"
         */
        public String getDescription() {
            return "Target Pressure Files";
        }    

    /**
     * Support function that can be used by anyone.  Returns the lowercase extension of a file.
     *@param f the file
     *@return lowercase String with extension of file
     */  
    public static String getExtension(File f) {
        String ext;
        String s = f.getName();
        int i = s.lastIndexOf('.');
        if ((i > 0) &&  (i < s.length() - 1)) 
            ext = s.substring(i+1).toLowerCase();
        else
            ext="";
        return ext;
    }
}
