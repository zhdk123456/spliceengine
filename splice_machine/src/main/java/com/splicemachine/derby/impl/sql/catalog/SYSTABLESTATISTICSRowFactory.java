package com.splicemachine.derby.impl.sql.catalog;

import com.splicemachine.derby.iapi.catalog.TableStatisticsDescriptor;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.sanity.SanityManager;
import org.apache.derby.iapi.services.uuid.UUIDFactory;
import org.apache.derby.iapi.sql.dictionary.CatalogRowFactory;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.SystemColumn;
import org.apache.derby.iapi.sql.dictionary.TupleDescriptor;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.sql.execute.ExecutionFactory;
import org.apache.derby.iapi.types.*;
import org.apache.derby.impl.sql.catalog.SystemColumnImpl;

import java.sql.Timestamp;
import java.sql.Types;

/**
 * @author Scott Fines
 *         Date: 2/25/15
 */
public class SYSTABLESTATISTICSRowFactory extends CatalogRowFactory {
    private static final String TABLENAME_STRING = "SYSTABLESTATISTICS";
    private static final int SYSTABLESTATISTICS_COLUMN_COUNT = 9;
    private static final int CONGLOMID = 1;
    private static final int PARTITIONID = 2;
    private static final int TIMESTAMP = 3;
    private static final int STALENESS = 4;
    private static final int INPROGRESS = 5;
    private static final int ROWCOUNT = 6;
    private static final int PARTITION_SIZE = 7;
    private static final int MEANROWWIDTH= 8;
    private static final int QUERYCOUNT = 9;

    private String[] uuids = {
            "08264012-014b-c29b-a826-000003009390",
            "08264012-014b-c29b-a826-000003009390"
    };

    public SYSTABLESTATISTICSRowFactory(UUIDFactory uuidf, ExecutionFactory ef, DataValueFactory dvf) {
        super(uuidf, ef, dvf);
        initInfo(SYSTABLESTATISTICS_COLUMN_COUNT,TABLENAME_STRING,null,null,uuids);
    }

    @Override
    public ExecRow makeRow(TupleDescriptor td, TupleDescriptor parent) throws StandardException {
        long conglomId = 0;
        String partitionId = null;
        long timestamp = 0;
        boolean staleness = false;
        boolean inProgress = false;
        long rowCount = 0;
        long partitionSize = 0;
        int meanRowWidth=0;
        long queryCount = 0;

        if(td!=null){
            TableStatisticsDescriptor tsd = (TableStatisticsDescriptor)td;
            conglomId = tsd.getConglomerateId();
            partitionId = tsd.getPartitionId();
            timestamp  = tsd.getTimestamp();
            staleness = tsd.isStale();
            inProgress= tsd.isInProgress();
            rowCount = tsd.getRowCount();
            partitionSize = tsd.getPartitionSize();
            meanRowWidth = tsd.getMeanRowWidth();
            queryCount = tsd.getQueryCount();
        }

        ExecRow row = getExecutionFactory().getValueRow(SYSTABLESTATISTICS_COLUMN_COUNT);
        row.setColumn(CONGLOMID,new SQLLongint(conglomId));
        row.setColumn(PARTITIONID,new SQLVarchar(partitionId));
        row.setColumn(TIMESTAMP,new SQLTimestamp(new Timestamp(timestamp)));
        row.setColumn(STALENESS,new SQLBoolean(staleness));
        row.setColumn(INPROGRESS,new SQLBoolean(inProgress));
        row.setColumn(ROWCOUNT,new SQLLongint(rowCount));
        row.setColumn(PARTITION_SIZE,new SQLLongint(partitionSize));
        row.setColumn(MEANROWWIDTH,new SQLInteger(meanRowWidth));
        row.setColumn(QUERYCOUNT,new SQLLongint(queryCount));
        return row;
    }

    @Override
    public TupleDescriptor buildDescriptor(ExecRow row, TupleDescriptor parentTuple, DataDictionary dataDictionary) throws StandardException {
        if (SanityManager.DEBUG) {
            SanityManager.ASSERT( row.nColumns() == SYSTABLESTATISTICS_COLUMN_COUNT,
                    "Wrong number of columns for a STATEMENTHISTORY row");
        }

        DataValueDescriptor col = row.getColumn(CONGLOMID);
        long conglomId = col.getLong();
        col = row.getColumn(PARTITIONID);
        String partitionId = col.getString();
        col = row.getColumn(TIMESTAMP);
        Timestamp timestamp = col.getTimestamp(null);
        col = row.getColumn(STALENESS);
        boolean isStale = col.getBoolean();
        col = row.getColumn(INPROGRESS);
        boolean inProgress = col.getBoolean();
        col = row.getColumn(ROWCOUNT);
        long rowCount = col.getLong();
        col = row.getColumn(PARTITION_SIZE);
        long partitionSize = col.getLong();
        col = row.getColumn(MEANROWWIDTH);
        int rowWidth = col.getInt();
        col = row.getColumn(QUERYCOUNT);
        long queryCount = col.getLong();

        return new TableStatisticsDescriptor(conglomId,
                partitionId,
                timestamp.getTime(),
                isStale,
                inProgress,
                rowCount,
                partitionSize,
                rowWidth,
                queryCount);
    }

    @Override
    public SystemColumn[] buildColumnList() throws StandardException {
        return new SystemColumn[]{
                SystemColumnImpl.getColumn("CONGLOMERATEID", Types.BIGINT,false),
                SystemColumnImpl.getColumn("PARTITIONID",Types.VARCHAR,false),
                SystemColumnImpl.getColumn("LAST_UPDATED",Types.TIMESTAMP,false),
                SystemColumnImpl.getColumn("IS_STALE",Types.BOOLEAN,false),
                SystemColumnImpl.getColumn("IN_PROGRESS",Types.BOOLEAN,false),
                SystemColumnImpl.getColumn("ROWCOUNT",Types.BIGINT,true),
                SystemColumnImpl.getColumn("PARTITION_SIZE",Types.BIGINT,true),
                SystemColumnImpl.getColumn("MEANROWWIDTH",Types.INTEGER,true),
                SystemColumnImpl.getColumn("QUERYCOUNT",Types.BIGINT,true)
        };
    }
}