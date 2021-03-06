/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.stats.random;

/**
 * @author Scott Fines
 *         Date: 12/2/14
 */
public abstract class ZigguratGenerator implements RandomGenerator{
    protected final RandomGenerator baseRandom;

    protected double[] x;
    private double[] y;

    public ZigguratGenerator(RandomGenerator baseRandom) {
        this.baseRandom = baseRandom;

        buildTables();
    }

    private void buildTables() {
        int n = 256;
        this.x = new double[n];
        this.y = new double[n];

        this.x[0] = 0;
        this.y[0] = phi(0);
        this.x[n-1] = x0();
        this.y[n-1] = phi(this.x[n-1]);
        double area = area();
        for(int i=n-2;i>=0;i--){
            this.y[i] = phi(this.x[i+1])+area/this.x[i+1];
            this.x[i] = phiInverse(this.y[i]);
        }
    }



    @Override
    public double nextDouble() {
        double u0 = baseRandom.nextDouble();
        double u1 = baseRandom.nextDouble();
        return nextValue(u0, u1);
    }

    protected double nextValue(double u0, double u1) {
        while(true) {
            int i = baseRandom.nextInt() & 255; //generate a number  in the range [0,256)
            double x = u0 * this.x[i];
            if (x < this.x[i]) return x;
            else if (i == 0) {
                return tail(u0,u1);
            } else {
                double y = this.y[i]+u1*(this.y[i+1]-this.y[i]);
                double phi = phi(x);
                if(y < phi) return x;
            }
        }
    }


    @Override
    public int nextInt() {
        return (int)(nextDouble()*Integer.MAX_VALUE);
    }

    @Override
    public long nextLong() {
        return (long)(nextDouble()*Long.MAX_VALUE);
    }

    @Override public boolean nextBoolean() { return baseRandom.nextBoolean(); }

    protected abstract double tail(double u0, double u1);

    protected abstract double phiInverse(double v);

    protected abstract double phi(double x);

    /**
     * @return the value to use for the first x (x0)
     */
    protected abstract double x0();

    protected abstract double area();

}
