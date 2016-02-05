/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.pmiapp.apcs;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Aaron
 */
public class BurstTestData {
    protected String testDate;
    protected String testTime;
    protected String sampleId;
    protected String testInfo;
    protected String estimatedBurstPressure;
    protected String holdingPressure;
    protected String holdingTime;
    protected String phaseIMotorSpeed;
    protected String phaseIIRate;
    protected String dataInterval;
    protected String maximumBurstPressure;
    protected String testFluid;
    protected String dataFileLocation;
    protected String burstPressure;
    protected ArrayList<TimePressure> data;
    private BufferedReader br;
    private FileReader fr;

    BurstTestData() {
        
    }

    BurstTestData(String fileName) {
        data = new ArrayList<TimePressure>();
        loadFile(fileName);
    }

    private String getIndex(String readLine, int i) {
        readLine = readLine.substring(readLine.indexOf(":") + 1);
        readLine.trim();
        return readLine;
    }

    private String parseLine(String readLine) {
        String info;
        info = readLine.substring(32, readLine.length());
        info = info.trim();
        return info;
    }

    private void loadFile(String fileName) {
        String line;


        try {
            br = new BufferedReader(new FileReader(fileName));

            br.readLine();
            br.readLine();

            setTestDate(getIndex(br.readLine(), 1));
            setTestTime(getIndex(br.readLine(), 1));

            br.readLine();
            br.readLine();
            br.readLine();

            setSampleId(parseLine(br.readLine()));
            setTestInfo(parseLine(br.readLine()));
            this.setEstimatedBurstPressure(parseLine(br.readLine()));
            this.setHoldingPressure(parseLine(br.readLine()));
            this.setHoldingTime(parseLine(br.readLine()));
            this.setPhaseIMotorSpeed(parseLine(br.readLine()));
            this.setPhaseIIRate(parseLine(br.readLine()));
            this.setDataInterval(parseLine(br.readLine()));
            this.setMaximumBurstPressure(parseLine(br.readLine()));
            this.setTestFluid(parseLine(br.readLine()));
            this.setDataFileLocation(parseLine(br.readLine()));

            br.readLine();
            br.readLine();
            br.readLine();

            while((line = br.readLine()).length() > 1){
                data.add(new TimePressure(line));
            }
            this.setBurstPressure(readBurstPressure(br.readLine()));
            br.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(BurstTestData.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ioe){

        } catch (NullPointerException nPe){
            //System.out.println("Line: " + line);
        }
    }

    private String readBurstPressure(String readLine) {
        String bp;
        bp = readLine.substring(readLine.indexOf("AT ") + 2);
        //System.out.println("BP: " + bp);
        bp = bp.substring(0, bp.indexOf(" PSI"));
        System.out.println("BP: " + bp);

        return bp;
    }



    protected class TimePressure{
        protected double time;
        protected double pressure;

        TimePressure(String line){
            String t = line.substring(0, line.indexOf(" "));
            String p = line.substring(line.indexOf(" "));

            try {
                time = Double.parseDouble(t);
                pressure = Double.parseDouble(p);
            } catch (NumberFormatException numberFormatException) {
                time = -1;
                pressure = -1;
            }
            //System.out.println("Time: " + time + "\tPressure: " + pressure);
        }
    }

    public String getBurstPressure() {
        return burstPressure;
    }

    public void setBurstPressure(String burstPressure) {
        this.burstPressure = burstPressure;
    }

    public ArrayList<TimePressure> getData() {
        return data;
    }

    public void setData(ArrayList<TimePressure> data) {
        this.data = data;
    }

    public String getDataFileLocation() {
        return dataFileLocation;
    }

    public void setDataFileLocation(String dataFileLocation) {
        this.dataFileLocation = dataFileLocation;
    }

    public String getDataInterval() {
        return dataInterval;
    }

    public void setDataInterval(String dataInterval) {
        this.dataInterval = dataInterval;
    }

    public String getEstimatedBurstPressure() {
        return estimatedBurstPressure;
    }

    public void setEstimatedBurstPressure(String estimatedBurstPressure) {
        this.estimatedBurstPressure = estimatedBurstPressure;
    }

    public String getHoldingPressure() {
        return holdingPressure;
    }

    public void setHoldingPressure(String holdingPressure) {
        this.holdingPressure = holdingPressure;
    }

    public String getHoldingTime() {
        return holdingTime;
    }

    public void setHoldingTime(String holdingTime) {
        this.holdingTime = holdingTime;
    }

    public String getMaximumBurstPressure() {
        return maximumBurstPressure;
    }

    public void setMaximumBurstPressure(String maximumBurstPressure) {
        this.maximumBurstPressure = maximumBurstPressure;
    }

    public String getPhaseIIRate() {
        return phaseIIRate;
    }

    public void setPhaseIIRate(String phaseIIRate) {
        this.phaseIIRate = phaseIIRate;
    }

    public String getPhaseIMotorSpeed() {
        return phaseIMotorSpeed;
    }

    public void setPhaseIMotorSpeed(String phaseIMotorSpeed) {
        this.phaseIMotorSpeed = phaseIMotorSpeed;
    }

    public String getSampleId() {
        return sampleId;
    }

    public void setSampleId(String sampleId) {
        this.sampleId = sampleId;
    }

    public String getTestDate() {
        return testDate;
    }

    public void setTestDate(String testDate) {
        this.testDate = testDate;
    }

    public String getTestFluid() {
        return testFluid;
    }

    public void setTestFluid(String testFluid) {
        this.testFluid = testFluid;
    }

    public String getTestInfo() {
        return testInfo;
    }

    public void setTestInfo(String testInfo) {
        this.testInfo = testInfo;
    }

    public String getTestTime() {
        return testTime;
    }

    public void setTestTime(String testTime) {
        this.testTime = testTime;
    }
}
