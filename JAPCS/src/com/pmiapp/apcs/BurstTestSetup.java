/*
 * BurstTestSetup.java
 *
 * Created on September 12, 2005, 1:06 PM
 */

package com.pmiapp.apcs;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Scanner;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Burst test setup form - always shown modally
 * @author Ron V. Webber
 */
public class BurstTestSetup extends javax.swing.JDialog {
    private String loadFile;
    
    /**
     * Creates new form BurstTestSetup, which is always modal
     * @param parent Calling form
     * @param userPath path to user directory
     * @param configSettings APCSConfig object with system configuration settings
     * @param testSettings empty properties object that will be filled with the test settings when this form finishes properly
     */
    public BurstTestSetup(java.awt.Frame parent, String userPath, APCSConfig configSettings, 
            java.util.Properties testSettings) {
        super(parent, true);
        initComponents();
        this.parent = parent;
        // remember the userPath for our own purposes
        this.userPath = userPath;
        // for now, destDir, which will hold the auto-generated output file, stays empty
        destDir="";
        // lastTestSettings is what we use for the properties locally until they click OK
        // and everything checks out.  Then we copy these properties over to testSettings
        // so they can be passed back to the caller.
        lastTestSettings = new java.util.Properties();
        testSettingsFile=new java.io.File(userPath, "bursttest.config");
        try {
            java.io.FileInputStream tempFIS = new java.io.FileInputStream(testSettingsFile);
            lastTestSettings.load(tempFIS);
            tempFIS.close();
        } catch (java.io.IOException e) {}
        this.configSettings=configSettings;
        this.testSettings=testSettings;
        txtChooser = new JFileChooser();
        txtChooser.addChoosableFileFilter(new com.pmiapp.common.TxtFilter());
        txtChooser.setCurrentDirectory(new File(userPath,"data"));
        uecArray = new com.pmiapp.common.UserEnabledComponent[13];
        uecArray[0]=new com.pmiapp.common.UserEnabledComponent(jTextField1, jCheckBox1, "SID", lastTestSettings, configSettings.isSupervisorMode(),"ID");
        uecArray[1]=new com.pmiapp.common.UserEnabledComponent(jTextField2, jCheckBox2, "ST", lastTestSettings, configSettings.isSupervisorMode(),"NA");
        uecArray[2]=new com.pmiapp.common.UserEnabledComponent(jTextField3, jCheckBox3, "EPT", lastTestSettings, configSettings.isSupervisorMode(),"3000");
        uecArray[3]=new com.pmiapp.common.UserEnabledComponent(jTextField4, jCheckBox4, "HP", lastTestSettings, configSettings.isSupervisorMode(),"1000");
        uecArray[4]=new com.pmiapp.common.UserEnabledComponent(jTextField5, jCheckBox5, "HT", lastTestSettings, configSettings.isSupervisorMode(),"100");
        uecArray[5]=new com.pmiapp.common.UserEnabledComponent(jTextField6, jCheckBox6, "PIPMS", lastTestSettings, configSettings.isSupervisorMode(),"1000");
        uecArray[6]=new com.pmiapp.common.UserEnabledComponent(jTextField7, jCheckBox7, "PIIPR", lastTestSettings, configSettings.isSupervisorMode(),"10");
        uecArray[7]=new com.pmiapp.common.UserEnabledComponent(jTextField8, jCheckBox8, "DSI", lastTestSettings, configSettings.isSupervisorMode(),"2");
        uecArray[8]=new com.pmiapp.common.UserEnabledComponent(jTextField9, jCheckBox9, "MBP", lastTestSettings, configSettings.isSupervisorMode(),"9900");
        uecArray[9]=new com.pmiapp.common.UserEnabledComponent(jTextField10, jCheckBox10, "BurstDropset", lastTestSettings, configSettings.isSupervisorMode(),"50");
        uecArray[10]=new com.pmiapp.common.UserEnabledComponent(jTextField11, jCheckBox11, "Liquid", lastTestSettings, configSettings.isSupervisorMode(),"IPA");
        uecArray[11]=new com.pmiapp.common.UserEnabledComponent(jTextField12, jCheckBox12, "datafile", lastTestSettings, configSettings.isSupervisorMode(), "");
        uecArray[12]=new com.pmiapp.common.UserEnabledComponent(jButton1, jCheckBox13, "LoadFromFile", lastTestSettings, configSettings.isSupervisorMode());
        // datafile does not get saved, but is cleared every time to avoid overwriting the data file
        jTextField12.setText("");
        doFileNameUpdate();
    }
    
    private JFileChooser txtChooser;
    private java.awt.Frame parent;
    private File testSettingsFile;
    private APCSConfig configSettings;
    private java.util.Properties testSettings, lastTestSettings;
    private com.pmiapp.common.UserEnabledComponent[] uecArray;
    private String userPath, destDir;

    /** Force the user to enter the sample ID and then automatically act as if they
     * clicked the OK button.  This is used for initializing the test settings the first
     * time the burst tester form is loaded.
     */
    public void autoRun() {
        String sampleID = JOptionPane.showInputDialog(parent, "Enter Sample Id:");
        jTextField1.setText(sampleID);
        doFileNameUpdate();
        this.jButton2.doClick();
    }

    private void doFileNameUpdate() {
        //Added By Cody Banas
        //Allows for the auto-incrementation of Test Files.
        //Also allows the creation of a Directory for each Test.

        //We shall update the DataFile directory based on what they type here
        //Setting the destination directory to our sample id's folder
        if (jTextField1.getText().length()>0)
            destDir = configSettings.getDataPath() + "\\"  + jTextField1.getText() + "\\";
        else
            destDir = configSettings.getDataPath()+ "\\";

        System.out.println("destDir: " + destDir);
        //Begin Counting Files
        File Temp;//This is a temporary file to allow auto incrementing numbers
        boolean Finding = true; //Make a loop control
        int FileCount = 1; //A file counter
        int CurFileIndex = 1; //The index of the Text File

        //Start counting files :)
        while (Finding){
            Temp = new File(destDir + jTextField1.getText() + "_" + FileCount + ".txt");
            if (Temp.exists()){
                FileCount = FileCount + 1;
            }
            else{
                CurFileIndex = FileCount;
                FileCount = FileCount - 1;
                Finding = false;
            }
        }
        jTextField12.setText(destDir + jTextField1.getText() + "_" + CurFileIndex + ".txt");
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jButton4 = new javax.swing.JButton();
        jButton1 = new javax.swing.JButton();
        jCheckBox13 = new javax.swing.JCheckBox();
        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jTextField2 = new javax.swing.JTextField();
        jTextField3 = new javax.swing.JTextField();
        jTextField4 = new javax.swing.JTextField();
        jTextField5 = new javax.swing.JTextField();
        jTextField6 = new javax.swing.JTextField();
        jTextField7 = new javax.swing.JTextField();
        jTextField8 = new javax.swing.JTextField();
        jTextField9 = new javax.swing.JTextField();
        jTextField10 = new javax.swing.JTextField();
        jTextField11 = new javax.swing.JTextField();
        jTextField12 = new javax.swing.JTextField();
        jCheckBox1 = new javax.swing.JCheckBox();
        jCheckBox2 = new javax.swing.JCheckBox();
        jCheckBox3 = new javax.swing.JCheckBox();
        jCheckBox4 = new javax.swing.JCheckBox();
        jCheckBox5 = new javax.swing.JCheckBox();
        jCheckBox6 = new javax.swing.JCheckBox();
        jCheckBox7 = new javax.swing.JCheckBox();
        jCheckBox8 = new javax.swing.JCheckBox();
        jCheckBox9 = new javax.swing.JCheckBox();
        jCheckBox10 = new javax.swing.JCheckBox();
        jCheckBox11 = new javax.swing.JCheckBox();
        jCheckBox12 = new javax.swing.JCheckBox();
        jPanel3 = new javax.swing.JPanel();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jButton6 = new javax.swing.JButton();
        jButton7 = new javax.swing.JButton();

        jButton4.setText("jButton4");

        jButton1.setText("Load From File");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jCheckBox13.setFocusable(false);

        setTitle("Burst Test Setup");

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(128, 64, 64), 8));
        jPanel1.setLayout(new java.awt.GridBagLayout());

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(""));
        jPanel2.setLayout(new java.awt.GridBagLayout());

        jLabel2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(128, 64, 64));
        jLabel2.setText("Sample ID:");
        jPanel2.add(jLabel2, new java.awt.GridBagConstraints());

        jLabel3.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(128, 64, 64));
        jLabel3.setText("Additional Test Information:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        jPanel2.add(jLabel3, gridBagConstraints);

        jLabel4.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(128, 64, 64));
        jLabel4.setText("Estimated Burst Pressure (PSI):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        jPanel2.add(jLabel4, gridBagConstraints);

        jLabel5.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(128, 64, 64));
        jLabel5.setText("Holding Pressure(PSI):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        jPanel2.add(jLabel5, gridBagConstraints);

        jLabel6.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel6.setForeground(new java.awt.Color(128, 64, 64));
        jLabel6.setText("Holding Time(Sec):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        jPanel2.add(jLabel6, gridBagConstraints);

        jLabel7.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel7.setForeground(new java.awt.Color(128, 64, 64));
        jLabel7.setText("Phase I Pressurizing Motor Speed:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        jPanel2.add(jLabel7, gridBagConstraints);

        jLabel8.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel8.setForeground(new java.awt.Color(128, 64, 64));
        jLabel8.setText("Phase II Pressurizing Rate(PSI/Sec):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.insets = new java.awt.Insets(0, 7, 0, 7);
        jPanel2.add(jLabel8, gridBagConstraints);

        jLabel9.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(128, 64, 64));
        jLabel9.setText("Data Saving Interval(Sec):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        jPanel2.add(jLabel9, gridBagConstraints);

        jLabel10.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(128, 64, 64));
        jLabel10.setText("Maximum Burst Pressure (PSI):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        jPanel2.add(jLabel10, gridBagConstraints);

        jLabel11.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel11.setForeground(new java.awt.Color(128, 64, 64));
        jLabel11.setText("Burst  P. Drop Judgement  (PSI):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        jPanel2.add(jLabel11, gridBagConstraints);

        jLabel12.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel12.setForeground(new java.awt.Color(128, 64, 64));
        jLabel12.setText("Hydraulic Medium used for Test:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        jPanel2.add(jLabel12, gridBagConstraints);

        jLabel13.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel13.setForeground(new java.awt.Color(0, 0, 255));
        jLabel13.setText("Data File Name:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel2.add(jLabel13, gridBagConstraints);

        jTextField1.setBackground(new java.awt.Color(0, 0, 0));
        jTextField1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField1.setForeground(new java.awt.Color(0, 255, 255));
        jTextField1.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField1.setText("Something to make this box wide");
        jTextField1.setCaretColor(new java.awt.Color(0, 255, 255));
        jTextField1.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField1FocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField1, gridBagConstraints);

        jTextField2.setBackground(new java.awt.Color(0, 0, 0));
        jTextField2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField2.setForeground(new java.awt.Color(0, 255, 255));
        jTextField2.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField2.setText("jTextField1");
        jTextField2.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField2, gridBagConstraints);

        jTextField3.setBackground(new java.awt.Color(0, 0, 0));
        jTextField3.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField3.setForeground(new java.awt.Color(0, 255, 255));
        jTextField3.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField3.setText("jTextField1");
        jTextField3.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField3, gridBagConstraints);

        jTextField4.setBackground(new java.awt.Color(0, 0, 0));
        jTextField4.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField4.setForeground(new java.awt.Color(0, 255, 255));
        jTextField4.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField4.setText("jTextField1");
        jTextField4.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField4, gridBagConstraints);

        jTextField5.setBackground(new java.awt.Color(0, 0, 0));
        jTextField5.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField5.setForeground(new java.awt.Color(0, 255, 255));
        jTextField5.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField5.setText("jTextField1");
        jTextField5.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField5, gridBagConstraints);

        jTextField6.setBackground(new java.awt.Color(0, 0, 0));
        jTextField6.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField6.setForeground(new java.awt.Color(0, 255, 255));
        jTextField6.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField6.setText("jTextField1");
        jTextField6.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField6, gridBagConstraints);

        jTextField7.setBackground(new java.awt.Color(0, 0, 0));
        jTextField7.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField7.setForeground(new java.awt.Color(0, 255, 255));
        jTextField7.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField7.setText("jTextField1");
        jTextField7.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField7, gridBagConstraints);

        jTextField8.setBackground(new java.awt.Color(0, 0, 0));
        jTextField8.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField8.setForeground(new java.awt.Color(0, 255, 255));
        jTextField8.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField8.setText("jTextField1");
        jTextField8.setCaretColor(new java.awt.Color(0, 255, 255));
        jTextField8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField8ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField8, gridBagConstraints);

        jTextField9.setBackground(new java.awt.Color(0, 0, 0));
        jTextField9.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField9.setForeground(new java.awt.Color(0, 255, 255));
        jTextField9.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField9.setText("jTextField1");
        jTextField9.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField9, gridBagConstraints);

        jTextField10.setBackground(new java.awt.Color(0, 0, 0));
        jTextField10.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField10.setForeground(new java.awt.Color(0, 255, 255));
        jTextField10.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField10.setText("jTextField1");
        jTextField10.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField10, gridBagConstraints);

        jTextField11.setBackground(new java.awt.Color(0, 0, 0));
        jTextField11.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField11.setForeground(new java.awt.Color(0, 255, 255));
        jTextField11.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField11.setText("jTextField1");
        jTextField11.setCaretColor(new java.awt.Color(0, 255, 255));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField11, gridBagConstraints);

        jTextField12.setEditable(false);
        jTextField12.setBackground(new java.awt.Color(0, 0, 0));
        jTextField12.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jTextField12.setForeground(new java.awt.Color(0, 255, 255));
        jTextField12.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField12.setText("really long file name so that this box starts out long enough for most purposes");
        jTextField12.setCaretColor(new java.awt.Color(0, 255, 255));
        jTextField12.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jTextField12MouseClicked(evt);
            }
        });
        jTextField12.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField12ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jTextField12, gridBagConstraints);

        jCheckBox1.setFocusable(false);
        jPanel2.add(jCheckBox1, new java.awt.GridBagConstraints());

        jCheckBox2.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        jPanel2.add(jCheckBox2, gridBagConstraints);

        jCheckBox3.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        jPanel2.add(jCheckBox3, gridBagConstraints);

        jCheckBox4.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        jPanel2.add(jCheckBox4, gridBagConstraints);

        jCheckBox5.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        jPanel2.add(jCheckBox5, gridBagConstraints);

        jCheckBox6.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        jPanel2.add(jCheckBox6, gridBagConstraints);

        jCheckBox7.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        jPanel2.add(jCheckBox7, gridBagConstraints);

        jCheckBox8.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
        jPanel2.add(jCheckBox8, gridBagConstraints);

        jCheckBox9.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 8;
        jPanel2.add(jCheckBox9, gridBagConstraints);

        jCheckBox10.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 9;
        jPanel2.add(jCheckBox10, gridBagConstraints);

        jCheckBox11.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 10;
        jPanel2.add(jCheckBox11, gridBagConstraints);

        jCheckBox12.setFocusable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 12;
        jPanel2.add(jCheckBox12, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        jPanel1.add(jPanel2, gridBagConstraints);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(""));
        jPanel3.setLayout(new java.awt.GridBagLayout());

        jButton2.setText("OK");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.ipadx = 10;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        jPanel3.add(jButton2, gridBagConstraints);

        jButton3.setText("Cancel");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        jPanel3.add(jButton3, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 10, 10, 10);
        jPanel1.add(jPanel3, gridBagConstraints);

        jButton5.setText("jButton5");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        jPanel1.add(jButton5, gridBagConstraints);

        jPanel5.setLayout(new java.awt.GridBagLayout());

        jPanel4.setLayout(new java.awt.GridBagLayout());

        jLabel1.setFont(new java.awt.Font("SansSerif", 1, 24)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(0, 0, 255));
        jLabel1.setText("Burst Test Setup");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 0, 10);
        jPanel4.add(jLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel5.add(jPanel4, gridBagConstraints);

        jButton6.setText("Load Settings");
        jButton6.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton6MouseClicked(evt);
            }
        });
        jButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton6ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTH;
        jPanel5.add(jButton6, gridBagConstraints);

        jButton7.setText("Save Settings");
        jButton7.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton7MouseClicked(evt);
            }
        });
        jButton7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton7ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTH;
        jPanel5.add(jButton7, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        jPanel1.add(jPanel5, gridBagConstraints);

        getContentPane().add(jPanel1, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jTextField1FocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField1FocusLost
        //Added By Cody Banas
        //Allows for the auto-incrementation of Test Files.
        //Also allows the creation of a Directory for each Test.

        //We shall update the DataFile directory based on what they type here
        System.out.println("DataPath: " + configSettings.getDataPath() +"\\");
        //Setting the destination directory to our sample id's folder
        if (jTextField1.getText().length()>0)
            destDir = configSettings.getDataPath() + "\\" + jTextField1.getText() + "\\";
        else
            destDir = configSettings.getDataPath() + "\\";
//
//        //Setting the destination directory to our sample id's folder
//        if (jTextField1.getText().length()>0)
//            destDir = userPath + "\\Data\\" + jTextField1.getText() + "\\";
//        else
//            destDir = userPath + "\\Data\\";
       
        //Begin Counting Files
        File Temp;//This is a temporary file to allow auto incrementing numbers
        boolean Finding = true; //Make a loop control
        int FileCount = 1; //A file counter
        int CurFileIndex = 1; //The index of the Text File

        //Start counting files :)
        while (Finding){
            Temp = new File(destDir + jTextField1.getText() + "_" + FileCount + ".txt");
            if (Temp.exists()){
                FileCount = FileCount + 1;
            }
            else{
                CurFileIndex = FileCount;
                FileCount = FileCount - 1;
                Finding = false;
            }
        }
        jTextField12.setText(destDir + jTextField1.getText() + "_" + CurFileIndex + ".txt");
    }//GEN-LAST:event_jTextField1FocusLost

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        if (txtChooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) {
            java.io.BufferedReader br;
            try { br = new java.io.BufferedReader(new java.io.FileReader(txtChooser.getSelectedFile())); }
            catch (java.io.FileNotFoundException e) {
                JOptionPane.showMessageDialog(this, "Error trying to read file");
                return;
            }
            String[] sa=new String[10];
            try {
                String s=br.readLine();
                if (s.equals("BURST PRESSURE TEST")) {
                    // ignore next 6 lines
                    br.readLine();
                    br.readLine();
                    br.readLine();
                    br.readLine();
                    br.readLine();
                    br.readLine();
                    // process next 10 lines
                    int j,k;
                    for (int i=0; i<10; i++) {
                        s=br.readLine();
                        // location of first character after first ':'
                        j=s.indexOf(':')+1;
                        // skip over any spaces
                        while ((s.charAt(j)==' ') && (j<s.length()-1)) j++;
                        if ((i<=1) || (i>=9)) {
                            // for first two and last one, use entire remaining string
                            sa[i]=s.substring(j);
                        }
                        else {
                            // find the next space character
                            k=s.indexOf(' ', j+1);
                            if (k==-1)
                                sa[i]=s.substring(j);
                            else
                                sa[i]=s.substring(j, k);
                        }
                    }
                    jTextField1.setText(sa[0]);
                    jTextField2.setText(sa[1]);
                    jTextField3.setText(sa[2]);
                    jTextField4.setText(sa[3]);
                    jTextField5.setText(sa[4]);
                    jTextField6.setText(sa[5]);
                    jTextField7.setText(sa[6]);
                    jTextField8.setText(sa[7]);
                    jTextField9.setText(sa[8]);
                    jTextField11.setText(sa[9]);
                }
                else {
                    JOptionPane.showMessageDialog(this, "Error:  File is not a burst pressure test file");
                }
            }
            catch (java.io.IOException e) {
                JOptionPane.showMessageDialog(this, "Error reading file");
            }
            try { br.close(); }
            catch (java.io.IOException e) {
                JOptionPane.showMessageDialog(this, "Error trying to close file");
            }
        }
        
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        double d;
        // see if the parameters are ok so we can finish the test setup
        // jTextField1 is SID - anything is acceptable
        // jTextField2 is ST - anything is acceptable
        // jTextField3 is EPT - this must be >=10 and <=maximum pressure of system
        try { d = Double.parseDouble(jTextField3.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Estimated Burst Pressure");
            return;
        }
        if (d<10) {
            JOptionPane.showMessageDialog(this,"Estimated Burst Pressure must be >= 10");
            return;
        }
        if (d>configSettings.getPressureRange()) {
            JOptionPane.showMessageDialog(this,"Estimated Burst Pressure exceeds pressure range of machine");
            return;
        }
        // jTextField4 is HP - this must be < EPT
        // d still = EPT
        double d2;
        try {d2 = Double.parseDouble(jTextField4.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Holding Pressure");
            return;
        }
        if (d2>=d) {
            JOptionPane.showMessageDialog(this,"Holding pressure must be less than Estimated Burst Pressure");
            return;
        }
        // jTextField5 is HT - this can be any value, but must be interpretable as a number
        try {Double.parseDouble(jTextField5.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Holding Time");
            return;
        }
        // jTextField6 is PIPMS, which must be >= 10.  It is also an integer.
        int i;
        try {i = Integer.parseInt(jTextField6.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Phase I Pressurizing Motor Speed");
            return;
        }
        if (i<10) {
            JOptionPane.showMessageDialog(this,"Phase I Pressurizing Motor Speed must be 10 or higher");
            return;
        }
        // jTextField7 is PIIPR, which must not be 0.
        try {d = Double.parseDouble(jTextField7.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Phase II Pressurizing Rate");
            return;
        }
        if (d==0) {
            JOptionPane.showMessageDialog(this,"Phase II Pressurizing Rate must not be 0");
            return;
        }
        // jTextField8 is DSO, which must be >= 0.1
        try {d = Double.parseDouble(jTextField8.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Data Saving Interval");
            return;
        }
        if (d<0.04){
            JOptionPane.showMessageDialog(this,"Data Saving Interval must be 0.1 or higher");
            return;
        }
        // jTextField9 is MBP, which must be >=10 and <=Pressure Range
        try {d = Double.parseDouble(jTextField9.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Maximum Burst Pressure");
            return;
        }
        if (d<10) {
            JOptionPane.showMessageDialog(this,"Maximum Burst Pressure must be 10 or higher");
            return;
        }
        if (d>configSettings.getPressureRange()) {
            JOptionPane.showMessageDialog(this,"Maximum Burst Pressure exceeds pressure range of machine");
            return;
        }
        // jTextField10 is BurstDropset which can currently be anything, but must be a number
        try {Double.parseDouble(jTextField10.getText()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,"Error in number format for Burst P.Drop Judgement");
            return;
        }
        // jTextField11 is Liquid, which can be anything
        // jTextField12 is file name, which can not be empty, and must be a valid, writable file
        if (jTextField12.getText().length()==0) {
            JOptionPane.showMessageDialog(this,"Select Data File Name");
            return;
        }
        
        File testDir = new File(destDir); //A File variable to test for the directory
        //Verify that the destination directory exists
        if (!testDir.exists()) {
            //We do not need to create it because it is there,
            //proceed to counting
            testDir.mkdir();
        }
        File f = new File(jTextField12.getText());
        if ((f.getParentFile().isDirectory()==false) || (f.getParentFile().canWrite()==false)) {
            JOptionPane.showMessageDialog(this,"Error in data file name");
            return;
        }
        // everything looks ok, so load everything into testSettings
        // and save this out to the last test settings file
        for (i=0; i<=12; i++)
            uecArray[i].storeFinalResults(testSettings);
        try { lastTestSettings.store(new java.io.FileOutputStream(testSettingsFile),"Burst Test Settings"); }
        catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this,"Error writing test settings to file - your test settings may not be remembered for the next test");
        }

    
        this.setVisible(false);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        // cancel is same as just closing the form
        this.setVisible(false);
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jTextField12MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTextField12MouseClicked
        txtChooser.setCurrentDirectory(new File(configSettings.getDataPath()));
        if (txtChooser.showSaveDialog(this)==JFileChooser.APPROVE_OPTION) {
            File f = txtChooser.getSelectedFile();
            configSettings.setDataPath(f.getParent());
            // make sure extension is ".txt"
            // note that getExtension always returns a lower case string
            if (!com.pmiapp.common.TxtFilter.getExtension(f).equals("txt")) {
                String s=f.getName();
                if (s.endsWith(".")==false)
                    s=s+".";
                f=new File(f.getParentFile(),s+"txt");
            }
            // check for overwrite
            if (f.exists())
                if (JOptionPane.showConfirmDialog(this, 
                        "File already exists\nDo you want to overwrite?", 
                        "Data File", JOptionPane.YES_NO_OPTION)==JOptionPane.NO_OPTION) return;
            jTextField12.setText(f.getAbsolutePath());

        }
    }//GEN-LAST:event_jTextField12MouseClicked

    private void jTextField12ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField12ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField12ActionPerformed
      private String verifyExtension(String file, String ext){
        //MAKE SURE IT HAS THE RIGHT EXTENSION
        int t = file.indexOf('.');
        if (t >= 0){
            file = file.substring(0,t);
        }
        file+= ext;
        return file;
    }
    
    
    private void loadSettingsButtonMouseClicked(java.awt.event.MouseEvent evt) {
  
    }

    private void saveSettingsButtonMouseClicked(java.awt.event.MouseEvent evt) {
        // TODO add your handling code here:

        
       
        
        
        
        
        
    }
    private void jTextField8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField8ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField8ActionPerformed

    private void jButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
        // TODO add your handling code here:      // TODO add your handling code here:

        
    }//GEN-LAST:event_jButton6ActionPerformed

    private void jButton6MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton6MouseClicked
        // TODO add your        String loadFile;
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
            ".setting Files", "setting");
        chooser.setFileFilter(filter);
        int returnVal = chooser.showOpenDialog(getParent());
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            loadFile = verifyExtension(
                    chooser.getSelectedFile().getPath(), ".setting");
            File file = new File(loadFile);
            try{
                Scanner in = new Scanner(file);
            
                ArrayList<String> values = new ArrayList<String>();
                for(int i = 0; in.hasNext(); i++){
                    values.add(in.nextLine());
                }
                jTextField1.setText(values.get(0));
                jTextField2.setText(values.get(1));
                jTextField3.setText(values.get(2));
                jTextField4.setText(values.get(3));
                jTextField5.setText(values.get(4));
                jTextField6.setText(values.get(5));
                jTextField7.setText(values.get(6));
                jTextField8.setText(values.get(7));
                jTextField9.setText(values.get(8));
                jTextField10.setText(values.get(9));
                jTextField11.setText(values.get(10));
                jTextField12.setText(values.get(11)); 
            }catch(Exception e){
                e.printStackTrace();
            }
    }//GEN-LAST:event_jButton6MouseClicked
    }
    private void jButton7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton7ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jButton7ActionPerformed

    private void jButton7MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton7MouseClicked
        String saveFile;
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
            ".setting Files", "setting");
        chooser.setFileFilter(filter);
        int returnVal = chooser.showOpenDialog(getParent());
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            saveFile = verifyExtension(
                    chooser.getSelectedFile().getPath(), ".setting");
            
            
             //scrape all the jTextField texts
            String [] fields = new String [12];
            fields[0] = jTextField1.getText();
            fields[1] = jTextField2.getText();
            fields[2] = jTextField3.getText();
            fields[3] = jTextField4.getText();
            fields[4] = jTextField5.getText();
            fields[5] = jTextField6.getText();
            fields[6] = jTextField7.getText();
            fields[7] = jTextField8.getText();
            fields[8] = jTextField9.getText();
            fields[9] = jTextField10.getText();
            fields[10] = jTextField11.getText();
            fields[11] = jTextField12.getText();
           
                
            
            try{
                FileWriter fstream = 
                        new FileWriter(verifyExtension(saveFile, ".setting"));
                BufferedWriter out = 
                        new BufferedWriter(fstream);
                for(int i = 0; i < fields.length; i++){
                    System.err.print("line "+i+": "+fields[i]+"\n");
                    out.write(fields[i]+(char)13+(char)10);
                }
                out.close();
                
            }catch(Exception e){
                e.printStackTrace();

            }
        }
    }//GEN-LAST:event_jButton7MouseClicked
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JCheckBox jCheckBox10;
    private javax.swing.JCheckBox jCheckBox11;
    private javax.swing.JCheckBox jCheckBox12;
    private javax.swing.JCheckBox jCheckBox13;
    private javax.swing.JCheckBox jCheckBox2;
    private javax.swing.JCheckBox jCheckBox3;
    private javax.swing.JCheckBox jCheckBox4;
    private javax.swing.JCheckBox jCheckBox5;
    private javax.swing.JCheckBox jCheckBox6;
    private javax.swing.JCheckBox jCheckBox7;
    private javax.swing.JCheckBox jCheckBox8;
    private javax.swing.JCheckBox jCheckBox9;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField10;
    private javax.swing.JTextField jTextField11;
    private javax.swing.JTextField jTextField12;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField3;
    private javax.swing.JTextField jTextField4;
    private javax.swing.JTextField jTextField5;
    private javax.swing.JTextField jTextField6;
    private javax.swing.JTextField jTextField7;
    private javax.swing.JTextField jTextField8;
    private javax.swing.JTextField jTextField9;
    // End of variables declaration//GEN-END:variables
    
}
