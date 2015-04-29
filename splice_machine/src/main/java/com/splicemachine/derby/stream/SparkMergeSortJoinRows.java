package com.splicemachine.derby.stream;

import com.splicemachine.derby.iapi.sql.execute.SpliceRuntimeContext;
import com.splicemachine.derby.stream.spark.RDDUtils;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.derby.impl.sql.execute.operations.IJoinRowsIterator;
import com.splicemachine.derby.impl.sql.execute.operations.LocatedRow;
import org.apache.hadoop.hbase.util.Pair;
import scala.Tuple2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * IJoinRowsIterator for MergeSortJoin run in Spark.
 *
 */
public class SparkMergeSortJoinRows implements IJoinRowsIterator<ExecRow> {

	private final Iterator<LocatedRow> lefts;
	private final Iterable<LocatedRow> rights;
    List<ExecRow> currentRights = new ArrayList<ExecRow>();
    byte[] currentRightHash;
    Pair<ExecRow,Iterator<ExecRow>> nextBatch;
    private int leftRowsSeen;
    private int rightRowsSeen;


    public SparkMergeSortJoinRows(Tuple2<Iterable<LocatedRow>, Iterable<LocatedRow>> source){
        this.lefts = source._1().iterator();
		this.rights = source._2();
    }

    Pair<ExecRow,Iterator<ExecRow>> nextLeftAndRights(){
		if (lefts.hasNext()) {
			leftRowsSeen++;
//			rightRowsSeen += rights.size();   TODO fix statistics
			return new Pair<ExecRow,Iterator<ExecRow>>(lefts.next().getRow(), RDDUtils.toExecRowsIterator(rights.iterator()));
		}
        return null;
    }

    @Override
    public Pair<ExecRow,Iterator<ExecRow>> next(SpliceRuntimeContext spliceRuntimeContext) {
        Pair<ExecRow,Iterator<ExecRow>> value = nextLeftAndRights();
        nextBatch = null;
        return value;
    }

    @Override
    public long getLeftRowsSeen() {
        return leftRowsSeen;
    }

    @Override
    public long getRightRowsSeen() {
        return rightRowsSeen;
    }

    @Override
    public void open() throws StandardException, IOException {

    }

    @Override
    public void close() throws StandardException, IOException {

    }
}