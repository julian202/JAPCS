/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.pmiapp.apcs;

import java.util.ArrayList;

/**
 *
 * @author Aaron
 */
public class StandardDeviation {

    private int numElements;
    private double sumX;
    private ArrayList<Double> xValues;
    private ArrayList<Double> deviation;
    private double mean;
    private double sumDeviation;

    public StandardDeviation() {
        numElements = 0;
        sumX = 0;
        mean = 0;
        xValues = new ArrayList<Double>();
        deviation = new ArrayList<Double>();
        sumDeviation = 0;
    }

    public void addXvalue(double x){
        xValues.add(x);
        numElements++;
    }

    private void calcSum(){
        for(int i = 0; i < numElements; i++){
            sumX += xValues.get(i);
        }
    }
    private void calcMean(){
        calcSum();
        mean = sumX / numElements;
    }

    private void calcDeviation(){
        for(int i = 0; i < numElements; i++){
            deviation.add(Math.pow(xValues.get(i) - mean, 2.0));
            sumDeviation += Math.pow(xValues.get(i) - mean, 2.0);
        }
    }

    public double calcStdDev(){
        double sd;
        calcMean();
        calcDeviation();
        sd = Math.sqrt(sumDeviation) / Math.sqrt(numElements - 1);

        return sd;
    }

    public static void main(String[] args){
        double[] arr = {1,2,3,4,5};
        StandardDeviation sd = new StandardDeviation();

        for(int i = 0; i < arr.length; i++){
            sd.addXvalue(arr[i]);
        }

        System.out.println("SD: " + sd.calcStdDev());
    }
}
