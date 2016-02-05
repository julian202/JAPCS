/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.pmiapp.apcs;

/**
 *
 * @author Aaron
 */
public class APCSData {
    protected BurstTestData burstData;
    protected int testType;
    public APCSData(int testType){
        this.testType = testType;
        this.burstData = new BurstTestData();
    }
    public APCSData(BurstTestData burstData) {
        this.burstData = burstData;
    }

    public BurstTestData getBurstData() {
        return burstData;
    }

    public void setBurstData(BurstTestData burstData) {
        this.burstData = burstData;
    }
}
