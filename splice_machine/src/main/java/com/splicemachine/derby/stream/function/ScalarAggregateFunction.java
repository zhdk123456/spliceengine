package com.splicemachine.derby.stream.function;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.derby.impl.sql.execute.operations.LocatedRow;
import com.splicemachine.derby.impl.sql.execute.operations.ScalarAggregateOperation;
import com.splicemachine.derby.impl.sql.execute.operations.framework.SpliceGenericAggregator;
import com.splicemachine.derby.stream.iapi.OperationContext;

/**
 * Created by jleach on 5/1/15.
 */
public class ScalarAggregateFunction extends SpliceFunction2<ScalarAggregateOperation, LocatedRow, LocatedRow, LocatedRow> {
    private static final long serialVersionUID = -4150499166764796082L;
    protected boolean initialized;
    protected ScalarAggregateOperation op;
    public ScalarAggregateFunction() {
    }

    public ScalarAggregateFunction(OperationContext<ScalarAggregateOperation> operationContext) {
        super(operationContext);
    }

    @Override
    public LocatedRow call(LocatedRow t1, LocatedRow t2) throws Exception {
        if (!initialized) {
            op = getOperation();
            initialized = true;
        }
        operationContext.recordRead();
        if (t2 == null) return t1;
        if (t1 == null) return t2.getClone();
//        if (RDDUtils.LOG.isDebugEnabled())
//            RDDUtils.LOG.debug(String.format("Reducing %s and %s", t1, t2));

        ExecRow r1 = t1.getRow();
        ExecRow r2 = t2.getRow();
        if (!op.isInitialized(r1)) {
            op.initializeVectorAggregation(r1);
        }
        if (!op.isInitialized(r2)) {
            accumulate(t2.getRow(), r1);
        } else {
            merge(t2.getRow(), r1);
        }
        return new LocatedRow(r1);
    }

    private void accumulate(ExecRow next, ExecRow agg) throws StandardException {
        ScalarAggregateOperation op = (ScalarAggregateOperation) getOperation();
//        if (RDDUtils.LOG.isDebugEnabled()) {
//            RDDUtils.LOG.debug(String.format("Accumulating %s to %s", next, agg));
//        }
        for (SpliceGenericAggregator aggregate : op.aggregates)
            aggregate.accumulate(next, agg);
    }

    private void merge(ExecRow next, ExecRow agg) throws StandardException {
        ScalarAggregateOperation op = (ScalarAggregateOperation) getOperation();
//        if (RDDUtils.LOG.isDebugEnabled()) {
//            RDDUtils.LOG.debug(String.format("Merging %s to %s", next, agg));
//        }
        for (SpliceGenericAggregator aggregate : op.aggregates)
            aggregate.merge(next, agg);
    }

}

