package com.splicemachine.derby.utils.marshall;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.kvpair.KVPair;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Scott Fines
 *         Date: 11/15/13
 */
public class PairEncoder implements Closeable{
    protected final KeyEncoder keyEncoder;
    protected final DataHash<ExecRow> rowEncoder;
    protected final KVPair.Type pairType;

    public PairEncoder(KeyEncoder keyEncoder,DataHash<ExecRow> rowEncoder,KVPair.Type pairType){
        this.keyEncoder=keyEncoder;
        this.rowEncoder=rowEncoder;
        this.pairType=pairType;
    }

    public KVPair encode(ExecRow execRow) throws StandardException, IOException{
        byte[] key=keyEncoder.getKey(execRow);
        rowEncoder.setRow(execRow);
        byte[] row=rowEncoder.encode();

        return new KVPair(key,row,pairType);
    }

    public PairDecoder getDecoder(ExecRow template){
        return new PairDecoder(keyEncoder.getDecoder(),
                rowEncoder.getDecoder(),template);
    }

    @Override
    public void close() throws IOException{
        keyEncoder.close();
        rowEncoder.close();
//        Closeables.closeQuietly(keyEncoder);
//        Closeables.closeQuietly(rowEncoder);
    }
}
