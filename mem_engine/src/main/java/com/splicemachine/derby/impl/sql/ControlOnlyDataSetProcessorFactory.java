package com.splicemachine.derby.impl.sql;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.Activation;
import com.splicemachine.derby.iapi.sql.execute.DataSetProcessorFactory;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.stream.control.ControlDataSetProcessor;
import com.splicemachine.derby.stream.iapi.DataSetProcessor;
import com.splicemachine.derby.stream.iapi.DistributedDataSetProcessor;
import com.splicemachine.derby.stream.utils.ForwardingDataSetProcessor;
import com.splicemachine.si.impl.driver.SIDriver;

import javax.annotation.Nullable;

/**
 * A DataSetProcessor Factory which only generates Control-Side DataSet processors. This is because memory
 * cannot support spark.
 *
 * @author Scott Fines
 *         Date: 1/12/16
 */
public class ControlOnlyDataSetProcessorFactory implements DataSetProcessorFactory{
    private final ControlDataSetProcessor cdsp;
    private final DistributedWrapper dist;

    public ControlOnlyDataSetProcessorFactory(){
        final SIDriver driver=SIDriver.driver();
        cdsp = new ControlDataSetProcessor(driver.getTxnSupplier(),
                driver.getIgnoreTxnSupplier(),
                driver.getTransactor(),
                driver.getOperationFactory());
        this.dist = new DistributedWrapper(cdsp);
    }

    @Override
    public DataSetProcessor chooseProcessor(@Nullable Activation activation,@Nullable SpliceOperation op){
        return cdsp;
    }

    @Override
    public DataSetProcessor localProcessor(@Nullable Activation activation,@Nullable SpliceOperation op){
        return cdsp;
    }

    @Override
    public DistributedDataSetProcessor distributedProcessor(){
        return dist;
    }

    private class DistributedWrapper extends ForwardingDataSetProcessor implements DistributedDataSetProcessor{
        public DistributedWrapper(ControlDataSetProcessor cdsp){
            super(cdsp);
        }

        @Override
        public void setup(Activation activation,String description,String schedulerPool) throws StandardException{
            //no-op
        }

        @Override
        public boolean allowsExecution(){
            return true;
        }
    }
}