package com.splicemachine.derby.impl.sql.execute.operations;

import com.splicemachine.EngineDriver;
import com.splicemachine.access.api.SConfiguration;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.loader.GeneratedMethod;
import com.splicemachine.db.iapi.sql.Activation;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.sql.execute.HasIncrement;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.db.iapi.types.RowLocation;
import com.splicemachine.db.impl.sql.compile.InsertNode;
import com.splicemachine.db.impl.sql.execute.BaseActivation;
import com.splicemachine.derby.iapi.sql.execute.DataSetProcessorFactory;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperationContext;
import com.splicemachine.derby.impl.sql.execute.actions.InsertConstantOperation;
import com.splicemachine.derby.impl.sql.execute.sequence.SequenceKey;
import com.splicemachine.derby.impl.sql.execute.sequence.SpliceSequence;
import com.splicemachine.derby.impl.store.access.hbase.HBaseRowLocation;
import com.splicemachine.derby.stream.function.InsertPairFunction;
import com.splicemachine.derby.stream.iapi.DataSet;
import com.splicemachine.derby.stream.iapi.DataSetProcessor;
import com.splicemachine.derby.stream.iapi.OperationContext;
import com.splicemachine.derby.stream.iapi.PairDataSet;
import com.splicemachine.derby.stream.output.DataSetWriter;
import com.splicemachine.derby.stream.output.WriteReadUtils;
import com.splicemachine.derby.stream.output.insert.InsertPipelineWriter;
import com.splicemachine.pipeline.ErrorState;
import com.splicemachine.pipeline.Exceptions;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.si.impl.driver.SIDriver;
import com.splicemachine.utils.Pair;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;


/**
 * Operation that handles inserts into Splice Machine.
 *
 * @author Scott Fines
 */
public class InsertOperation extends DMLWriteOperation implements HasIncrement{
    private static final long serialVersionUID=1l;
    private static final Logger LOG=Logger.getLogger(InsertOperation.class);
    private ExecRow rowTemplate;
    private int[] pkCols;
    private boolean singleRowResultSet=false;
    private long nextIncrement=-1;
    private RowLocation[] autoIncrementRowLocationArray;
    private SpliceSequence[] spliceSequences;
    protected static final String NAME=InsertOperation.class.getSimpleName().replaceAll("Operation","");
    public InsertPipelineWriter tableWriter;
    public Pair<Long, Long>[] defaultAutoIncrementValues;
    public InsertNode.InsertMode insertMode;
    public String statusDirectory;
    public int failBadRecordCount;


    @Override
    public String getName(){
        return NAME;
    }

    @SuppressWarnings("UnusedDeclaration")
    public InsertOperation(){
        super();
    }

    public InsertOperation(SpliceOperation source,
                           GeneratedMethod generationClauses,
                           GeneratedMethod checkGM,
                           String insertMode,
                           String statusDirectory,
                           int failBadRecordCount,
                           double optimizerEstimatedRowCount,
                           double optimizerEstimatedCost,
                           String tableVersion) throws StandardException{
        super(source,generationClauses,checkGM,source.getActivation(),optimizerEstimatedRowCount,optimizerEstimatedCost,tableVersion);
        this.insertMode=InsertNode.InsertMode.valueOf(insertMode);
        this.statusDirectory=statusDirectory;
        this.failBadRecordCount=failBadRecordCount;
        try{
            init(SpliceOperationContext.newContext(activation));
        }catch(IOException ioe){
            Exceptions.parseException(ioe);
        }
        recordConstructorTime();
    }

    @Override
    public void init(SpliceOperationContext context) throws StandardException, IOException{
        try{
            super.init(context);
            source.init(context);
            writeInfo.initialize(context);
            heapConglom=writeInfo.getConglomerateId();
            pkCols=writeInfo.getPkColumnMap();
            singleRowResultSet=isSingleRowResultSet();
            autoIncrementRowLocationArray=writeInfo.getConstantAction()!=null &&
                    ((InsertConstantOperation)writeInfo.getConstantAction()).getAutoincRowLocation()!=null?
                    ((InsertConstantOperation)writeInfo.getConstantAction()).getAutoincRowLocation():new RowLocation[0];
            defaultAutoIncrementValues=WriteReadUtils.getStartAndIncrementFromSystemTables(autoIncrementRowLocationArray,
                    activation.getLanguageConnectionContext().getDataDictionary(),
                    heapConglom);
            spliceSequences=new SpliceSequence[autoIncrementRowLocationArray.length];
            int length=autoIncrementRowLocationArray.length;
            for(int i=0;i<length;i++){
                HBaseRowLocation rl=(HBaseRowLocation)autoIncrementRowLocationArray[i];
                if(rl==null){
                    spliceSequences[i]=null;
                }else{
                    byte[] rlBytes=rl.getBytes();
                    SConfiguration config=context.getSystemConfiguration();
                    SequenceKey key=new SequenceKey(
                            rlBytes,
                            (isSingleRowResultSet())?1l:config.getInt(OperationConfiguration.SEQUENCE_BLOCK_SIZE),
                            defaultAutoIncrementValues[i].getFirst(),
                            defaultAutoIncrementValues[i].getSecond(),
                            SIDriver.driver().getTableFactory(),
                            SIDriver.driver().getOperationFactory());
                    spliceSequences[i]=EngineDriver.driver().sequencePool().get(key);
                }
            }
        }catch(Exception e){
            throw Exceptions.parseException(e);
        }

    }

    @Override
    public String toString(){
        return "Insert{destTable="+heapConglom+",source="+source+"}";
    }

    @Override
    public String prettyPrint(int indentLevel){
        return "Insert"+super.prettyPrint(indentLevel);
    }

    @Override
    public DataValueDescriptor increment(int columnPosition,long increment) throws StandardException{
        assert activation!=null && spliceSequences!=null:"activation or sequences are null";
        nextIncrement=((BaseActivation)activation).ignoreSequence()?-1:spliceSequences[columnPosition-1].getNext();
        this.getActivation().getLanguageConnectionContext().setIdentityValue(nextIncrement);
        if(rowTemplate==null)
            rowTemplate=getExecRowDefinition();
        DataValueDescriptor dvd=rowTemplate.cloneColumn(columnPosition);
        dvd.setValue(nextIncrement);
        return dvd;
    }

    @Override
    public void close() throws StandardException{
        super.close();
        if(nextIncrement!=-1) // Do we do this twice?
            this.getActivation().getLanguageConnectionContext().setIdentityValue(nextIncrement);
    }

    private boolean isSingleRowResultSet(){
        boolean isRow=false;
        if(source instanceof RowOperation)
            isRow=true;
        else if(source instanceof NormalizeOperation)
            isRow=(((NormalizeOperation)source).source instanceof RowOperation);
        return isRow;
    }

    @Override
    public void setActivation(Activation activation) throws StandardException{
        super.setActivation(activation);
        SpliceOperationContext context=SpliceOperationContext.newContext(activation);
        try{
            init(context);
        }catch(IOException ioe){
            throw StandardException.plainWrapException(ioe);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{
        super.readExternal(in);
        autoIncrementRowLocationArray=new RowLocation[in.readInt()];
        for(int i=0;i<autoIncrementRowLocationArray.length;i++){
            autoIncrementRowLocationArray[i]=(HBaseRowLocation)in.readObject();
        }
        insertMode=InsertNode.InsertMode.valueOf(in.readUTF());
        if(in.readBoolean())
            statusDirectory=in.readUTF();
        failBadRecordCount=in.readInt();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException{
        super.writeExternal(out);
        int length=autoIncrementRowLocationArray.length;
        out.writeInt(length);
        for(int i=0;i<length;i++){
            out.writeObject(autoIncrementRowLocationArray[i]);
        }
        out.writeUTF(insertMode.toString());
        out.writeBoolean(statusDirectory!=null);
        if(statusDirectory!=null)
            out.writeUTF(statusDirectory);
        out.writeInt(failBadRecordCount);
    }

    public boolean isImport(){
        return failBadRecordCount!=-1;
    }

    @Override
    public DataSet<LocatedRow> getDataSet(DataSetProcessor dsp) throws StandardException{
        if(statusDirectory!=null){
            dsp.setPermissive();
            dsp.setFailBadRecordCount(this.failBadRecordCount);
        }
        DataSet set=source.getDataSet(dsp);
        OperationContext operationContext=dsp.createOperationContext(this);
        ExecRow execRow=getExecRowDefinition();
        int[] execRowTypeFormatIds=WriteReadUtils.getExecRowTypeFormatIds(execRow);
        if(insertMode.equals(InsertNode.InsertMode.UPSERT) && pkCols==null)
            throw ErrorState.UPSERT_NO_PRIMARY_KEYS.newException(""+heapConglom+"");
        TxnView txn=getCurrentTransaction();

        try{
            operationContext.pushScope();
            if(statusDirectory!=null)
                dsp.setSchedulerPool("import");
            PairDataSet dataSet=set.index(new InsertPairFunction(operationContext),true);
            DataSetWriter writer=dataSet.insertData(operationContext)
                    .autoIncrementRowLocationArray(autoIncrementRowLocationArray)
                    .execRowDefinition(getExecRowDefinition())
                    .execRowTypeFormatIds(execRowTypeFormatIds)
                    .sequences(spliceSequences)
                    .isUpsert(insertMode.equals(InsertNode.InsertMode.UPSERT))
                    .pkCols(pkCols)
                    .tableVersion(tableVersion)
                    .destConglomerate(heapConglom)
                    .operationContext(operationContext)
                    .txn(txn)
                    .build();
            return writer.write();
        }finally{
            operationContext.popScope();
        }

    }

    @Override
    public String getVTIFileName(){
        return getSubOperations().get(0).getVTIFileName();
    }


    @Override
    public void openCore() throws StandardException{
        DataSetProcessorFactory dspf=EngineDriver.driver().processorFactory();
        if(statusDirectory!=null) // Always go distributed...
            openCore(dspf.distributedProcessor());
        else
            openCore(dspf.chooseProcessor(activation,this));
    }

}
