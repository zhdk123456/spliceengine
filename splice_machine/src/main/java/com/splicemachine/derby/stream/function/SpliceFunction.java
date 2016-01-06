package com.splicemachine.derby.stream.function;

import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.stream.iapi.OperationContext;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * Created by dgomezferro on 4/4/14.
 */
public abstract class SpliceFunction<Op extends SpliceOperation, From, To>
    extends AbstractSpliceFunction<Op>
		implements ExternalizableFunction<From, To>, com.google.common.base.Function<From,To>, Serializable {

	public SpliceFunction() {
        super();
	}
	public SpliceFunction(OperationContext<Op> operationContext) {
        super(operationContext);
	}

    @Nullable
    @Override
    public To apply(@Nullable From from) {
        try {
            return call(from);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}