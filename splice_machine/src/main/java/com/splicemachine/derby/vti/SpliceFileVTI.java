package com.splicemachine.derby.vti;

import com.splicemachine.access.api.FileInfo;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.vti.VTICosting;
import com.splicemachine.db.vti.VTIEnvironment;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.impl.load.ImportUtils;
import com.splicemachine.derby.impl.sql.execute.operations.LocatedRow;
import com.splicemachine.derby.stream.function.FileFunction;
import com.splicemachine.derby.stream.function.StreamFileFunction;
import com.splicemachine.derby.stream.iapi.DataSet;
import com.splicemachine.derby.stream.iapi.DataSetProcessor;
import com.splicemachine.derby.stream.iapi.OperationContext;
import com.splicemachine.derby.stream.iapi.PairDataSet;
import com.splicemachine.derby.vti.iapi.DatasetProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * Created by jleach on 10/7/15.
 */
public class SpliceFileVTI implements DatasetProvider, VTICosting {
    private String fileName;
    private String characterDelimiter;
    private String columnDelimiter;
    private String timeFormat;
    private String dateTimeFormat;
    private String timestampFormat;
    private int[] columnIndex;
    private OperationContext operationContext;
    private boolean oneLineRecords;
    private String charset;

    public SpliceFileVTI() {}

    public SpliceFileVTI(String fileName) {
        this.fileName = fileName;
        oneLineRecords = true;
        charset = StandardCharsets.UTF_8.name();
    }

    public SpliceFileVTI(String fileName,String characterDelimiter, String columnDelimiter) {
        this(fileName);
        this.characterDelimiter = characterDelimiter;
        this.columnDelimiter = columnDelimiter;
    }

    public SpliceFileVTI(String fileName,String characterDelimiter, String columnDelimiter, boolean oneLineRecords) {
        this(fileName);
        this.characterDelimiter = characterDelimiter;
        this.columnDelimiter = columnDelimiter;
        this.oneLineRecords = oneLineRecords;
    }

    @SuppressFBWarnings(value="EI_EXPOSE_REP2", justification="Intentional")
    public SpliceFileVTI(String fileName,String characterDelimiter, String columnDelimiter, int[] columnIndex) {
        this(fileName, characterDelimiter, columnDelimiter);
        this.columnIndex = columnIndex;
    }

    public SpliceFileVTI(String fileName,String characterDelimiter, String columnDelimiter, int[] columnIndex, String timeFormat, String dateTimeFormat, String timestampFormat) {
        this(fileName, characterDelimiter, columnDelimiter,columnIndex);
        this.timeFormat = timeFormat;
        this.dateTimeFormat = dateTimeFormat;
        this.timestampFormat = timestampFormat;
    }

    public SpliceFileVTI(String fileName,String characterDelimiter, String columnDelimiter, int[] columnIndex, String timeFormat, String dateTimeFormat, String timestampFormat, String oneLineRecords, String charset) {
        this(fileName, characterDelimiter, columnDelimiter,columnIndex,timeFormat,dateTimeFormat,timestampFormat);
        this.oneLineRecords = Boolean.parseBoolean(oneLineRecords);
        this.charset = charset;
    }

    public static DatasetProvider getSpliceFileVTI(String fileName) {
        return new SpliceFileVTI(fileName);
    }

    public static DatasetProvider getSpliceFileVTI(String fileName, String characterDelimiter, String columnDelimiter) {
        return new SpliceFileVTI(fileName,characterDelimiter,columnDelimiter);
    }

    public static DatasetProvider getSpliceFileVTI(String fileName, String characterDelimiter, String columnDelimiter, int[] columnIndex) {
        return new SpliceFileVTI(fileName,characterDelimiter,columnDelimiter, columnIndex);
    }

    public static DatasetProvider getSpliceFileVTI(String fileName, String characterDelimiter, String columnDelimiter, int[] columnIndex,
                                                   String timeFormat, String dateTimeFormat, String timestampFormat) {
        return new SpliceFileVTI(fileName,characterDelimiter,columnDelimiter, columnIndex,timeFormat,dateTimeFormat,timestampFormat);
    }

    @Override
    public DataSet<LocatedRow> getDataSet(SpliceOperation op, DataSetProcessor dsp, ExecRow execRow) throws StandardException {
        operationContext = dsp.createOperationContext(op);
        try {
            ImportUtils.validateReadable(fileName, false);
            if (oneLineRecords && (charset==null || charset.toLowerCase().equals("utf-8"))) {
                DataSet<String> textSet = dsp.readTextFile(fileName, op);
                operationContext.pushScopeForOp("Parse File");
                return textSet.flatMap(new FileFunction(characterDelimiter, columnDelimiter, execRow, columnIndex, timeFormat, dateTimeFormat, timestampFormat, operationContext));
            } else {
                PairDataSet<String,InputStream> streamSet = dsp.readWholeTextFile(fileName, op);
                operationContext.pushScopeForOp("Parse File");
                return streamSet.values().flatMap(new StreamFileFunction(characterDelimiter, columnDelimiter, execRow, columnIndex, timeFormat, dateTimeFormat, timestampFormat, charset, operationContext));
            }
        } finally {
            operationContext.popScope();
        }
    }

    private static final int defaultBytesPerRow = 100;
    protected int getBytesPerRow() {
        // Imprecise assumption of a fixed number of bytes per row,
        // but better than assuming fixed default row count.
        // Future improvements might include:
        // 1) fetch first few rows and compute real bytes per row and extrapolate
        // 2) count number of columns and assume bytes per column
        // In the end, though, this will *always* be just an approximation,
        // because we can't actually count the rows before importing.
        return defaultBytesPerRow;
    }

    @Override
    public double getEstimatedRowCount(VTIEnvironment vtiEnvironment) throws SQLException {
        if (fileName != null) {
            try {
                FileInfo fileInfo = ImportUtils.getImportFileInfo(fileName);
                return fileInfo.spaceConsumed() / getBytesPerRow();
            } catch (IOException e){
                throw new SQLException(e);
            }
        }

        return VTICosting.defaultEstimatedRowCount;
    }

    @Override
    public double getEstimatedCostPerInstantiation(VTIEnvironment vtiEnvironment) throws SQLException {
        return VTICosting.defaultEstimatedCost;
    }

    @Override
    public boolean supportsMultipleInstantiations(VTIEnvironment vtiEnvironment) throws SQLException {
        return false;
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        throw new SQLException("not supported");
    }

    @Override
    public OperationContext getOperationContext() {
        return operationContext;
    }

    public String getFileName() {
        return fileName;
    }
}
