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

package com.splicemachine.storage;

import com.splicemachine.metrics.Counter;
import com.splicemachine.metrics.MetricFactory;
import com.splicemachine.metrics.Metrics;
import com.splicemachine.metrics.TimeView;
import com.splicemachine.primitives.Bytes;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Scott Fines
 *         Date: 12/16/15
 */
class SetScanner implements DataScanner{
    private final long sequenceCutoffPoint;
    private final Iterator<DataCell> dataCells;
    private final long lowVersion;
    private final long highVersion;
    private final DataFilter filter;
    private final Partition partition;
    private final Counter rowCounter;
    private final Counter filterCounter;

    private byte[] currentKey = null;
    private int currentOffset = 0;
    private int currentLength = 0;
    private List<DataCell> currentRow;
    private DataCell last;

    public SetScanner(long sequenceCutoffPoint,
            Iterator<DataCell> dataCells,
                      long lowVersion,
                      long highVersion,
                      DataFilter filter,
                      Partition partition,
                      MetricFactory metricFactory){
        this.sequenceCutoffPoint=sequenceCutoffPoint;
        this.dataCells=dataCells;
        this.lowVersion=lowVersion;
        this.highVersion=highVersion;
        this.filter=filter;
        this.partition = partition;

        this.rowCounter = metricFactory.newCounter();
        this.filterCounter = metricFactory.newCounter();
    }

    @Override
    public Partition getPartition(){
        return partition;
    }

    @Override
    public @Nonnull List<DataCell> next(int limit) throws IOException{
        if(currentRow==null)
            currentRow = new ArrayList<>(limit>0?limit:10);
        if(limit<0)limit = Integer.MAX_VALUE;
        boolean shouldContinue;
        do{
            if(filter!=null)
                filter.reset();
            shouldContinue = fillNextRow(limit);
            if(filter instanceof MTxnFilterWrapper){
                ((MTxnFilterWrapper)filter).filterRow(currentRow);
            }
        }while(shouldContinue && currentRow.size()<=0);

        if(currentRow.size()>0)
            rowCounter.increment();
        return currentRow;
    }

    private boolean fillNextRow(int limit) throws IOException{
        currentRow.clear();
        DataCell n;
        if(last!=null){
            n = last;
            currentKey = last.keyArray();
            currentOffset = last.keyOffset();
            currentLength = last.keyLength();
            last=null;
        }else if(!dataCells.hasNext()){
            return false;
        }else{
            n = dataCells.next();
        }
        while(currentRow.size()<limit && n!=null){
            if(((MCell)n).getSequence()<=sequenceCutoffPoint){
                //skip data which was written after we started the scanner
                if(currentLength==0){
                    currentKey=n.keyArray();
                    currentOffset=n.keyOffset();
                    currentLength=n.keyLength();
                }else if(!Bytes.equals(currentKey,currentOffset,currentLength,n.keyArray(),n.keyOffset(),n.keyLength())){
                    if(currentRow.size()>0){
                        last=n;
                        return true;
                    }else{
                        filter.reset(); //we've moved to a new row
                        currentKey=n.keyArray();
                        currentOffset=n.keyOffset();
                        currentLength=n.keyLength();
                    }
                }
                switch(accept(n)){
                    case NEXT_ROW:
                        filterCounter.increment();
                        n=advanceRow();
                        continue;
                    case NEXT_COL:
                        n=advanceColumn(n);
                        continue;
                    case SKIP:
                        break;
                    case INCLUDE:
                        currentRow.add(n);
                        break;
                    case INCLUDE_AND_NEXT_COL:
                        currentRow.add(n);
                        n=advanceColumn(n);
                        continue;
                    case SEEK:
                        throw new UnsupportedOperationException("SEEK Not supported by in-memory store");
                }
            }

            if(dataCells.hasNext())
                n=dataCells.next();
            else n=null;
        }
        last = n;

        return n!=null;
    }

    private DataCell advanceColumn(DataCell n){
        while(dataCells.hasNext()){
            DataCell next = dataCells.next();
            if(!next.matchesQualifier(next.family(),n.qualifier()))
                return next;
        }
        return null;
    }

    private DataCell advanceRow(){
        while(dataCells.hasNext()){
            DataCell n = dataCells.next();
            if(!Bytes.equals(currentKey,currentOffset,currentLength,n.keyArray(),n.keyOffset(),n.keyLength())){
                return n;
            }
        }
        return null;
    }


    @Override
    public TimeView getReadTime(){
        return Metrics.noOpTimeView();
    }

    @Override
    public long getBytesOutput(){
        return 0;
    }

    @Override
    public long getRowsFiltered(){
        return filterCounter.getTotal();
    }

    @Override
    public long getRowsVisited(){
        return rowCounter.getTotal();
    }

    @Override
    public void close() throws IOException{

    }

    /* ****************************************************************************************************************/
    /*private helper methods*/
    private DataFilter.ReturnCode accept(DataCell n) throws IOException{
        long ts = n.version();
        if(ts<lowVersion) return DataFilter.ReturnCode.NEXT_COL;
        else if(ts>highVersion) return DataFilter.ReturnCode.SKIP;
        if(filter!=null){
            return filter.filterCell(n);
        }
        return DataFilter.ReturnCode.INCLUDE;
    }
}
