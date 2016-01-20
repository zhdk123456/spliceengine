package com.splicemachine.pipeline;

import java.io.IOException;

import com.carrotsearch.hppc.BitSet;
import com.splicemachine.derby.impl.sql.execute.index.IndexTransformer;
import com.splicemachine.kvpair.KVPair;
import com.splicemachine.pipeline.callbuffer.CallBuffer;
import com.splicemachine.pipeline.context.WriteContext;
import com.splicemachine.pipeline.client.WriteResult;
import com.splicemachine.pipeline.writehandler.RoutingWriteHandler;
import com.splicemachine.primitives.Bytes;
import org.apache.log4j.Logger;
import com.splicemachine.utils.SpliceLogUtils;

/**
 * Intercepts UPDATE/UPSERT/INSERT/DELETE mutations to a base table and sends corresponding mutations to the index table.
 *
 * @author Scott Fines
 *         Created on: 5/1/13
 */
public class IndexWriteHandler extends RoutingWriteHandler{
    private static final Logger LOG = Logger.getLogger(IndexWriteHandler.class);
    private final IndexTransformer transformer;
    private CallBuffer<KVPair> indexBuffer;
    private final int expectedWrites;
    private BitSet indexedColumns;

    public IndexWriteHandler(boolean keepState,
                             int expectedWrites,
                             IndexTransformer transformer){
        super(transformer.getIndexConglomBytes(),keepState);
        this.expectedWrites = expectedWrites;
        this.transformer = transformer;
        this.indexedColumns = transformer.gitIndexedCols();
    }

    @Override
    protected void doFlush(WriteContext ctx) throws Exception {
        if (indexBuffer != null && ! ctx.skipIndexWrites())
            indexBuffer.flushBuffer();
    }

    @Override
    public void flush(WriteContext ctx) throws IOException {
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "flush");
        super.flush(ctx);
    }

    @Override
    public void close(WriteContext ctx) throws IOException {
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "close");
        super.close(ctx);
    }

    @Override
    public void doClose(WriteContext ctx) throws Exception {
        if (indexBuffer != null)
            indexBuffer.close(); // Blocks
    }

    @Override
    protected boolean isHandledMutationType(KVPair.Type type) {
        return type == KVPair.Type.DELETE || type == KVPair.Type.CANCEL ||
            type == KVPair.Type.UPDATE || type == KVPair.Type.INSERT ||
            type == KVPair.Type.UPSERT;
    }

    @Override
    public boolean route(KVPair mutation,WriteContext ctx) {
        if (ctx.skipIndexWrites()) {
            return true;
        }
        if (!ensureBufferReader(mutation, ctx))
            return false;

        switch(mutation.getType()) {
            case INSERT:
                return createIndexRecord(mutation, ctx);
            case UPDATE:
                if (transformer.areIndexKeysModified(mutation, indexedColumns)) { // Do I need to update?
                    deleteIndexRecord(mutation, ctx);
                    return createIndexRecord(mutation, ctx);
                }
                return true; // No index columns modifies ignore...
            case UPSERT:
                deleteIndexRecord(mutation, ctx);
                return createIndexRecord(mutation, ctx);
            case DELETE:
                return deleteIndexRecord(mutation, ctx);
            case CANCEL:
                if (transformer.isUniqueIndex())
                    return true;
                throw new RuntimeException("Not Valid Execution Path");
            case EMPTY_COLUMN:
            case FOREIGN_KEY_PARENT_EXISTENCE_CHECK:
            case FOREIGN_KEY_CHILDREN_EXISTENCE_CHECK:
            default:
                throw new RuntimeException("Not Valid Execution Path");
        }
    }

    private boolean createIndexRecord(KVPair mutation, WriteContext ctx) {
        try {
            KVPair newIndex = transformer.translate(mutation);
            newIndex.setType(KVPair.Type.INSERT);
            if(keepState) {
                this.routedToBaseMutationMap.put(newIndex, mutation);
            }
            indexBuffer.add(newIndex);
        } catch (Exception e) {
            ctx.failed(mutation, WriteResult.failed(e.getClass().getSimpleName()+":"+e.getMessage()));
            return false;
        }
        return true;
    }

    private boolean deleteIndexRecord(KVPair mutation, WriteContext ctx) {
        if (LOG.isTraceEnabled())
            SpliceLogUtils.trace(LOG, "index delete with %s", mutation);

    	/*
         * To delete the correct index row, we do the following:
         *
         * 1. do a Get() on all the indexed columns of the main table
         * 2. transform the results into an index row (as if we were inserting it)
         * 3. issue a delete against the index table
         */
        try {
            KVPair indexDelete = transformer.createIndexDelete(mutation, ctx, indexedColumns);
            if (indexDelete == null) {
                // we can't find the old row, it may have been deleted already, but we'll have to update the
                // index anyway in the calling method
//                ctx.success(mutation);
                return false;
            }
            if(keepState)
                this.routedToBaseMutationMap.put(indexDelete,mutation);
            if (LOG.isTraceEnabled())
                SpliceLogUtils.trace(LOG, "performing index delete on row %s", Bytes.toHex(indexDelete.getRowKey()));
            ensureBufferReader(indexDelete, ctx);
            indexBuffer.add(indexDelete);
        } catch (Exception e) {
            ctx.failed(mutation, WriteResult.failed(e.getClass().getSimpleName()+":"+e.getMessage()));
            return false;
        }
        return true;
    }

    private boolean ensureBufferReader(KVPair mutation, WriteContext ctx) {
        if (indexBuffer == null) {
            try {
                indexBuffer = getRoutedWriteBuffer(ctx,expectedWrites);
            } catch (Exception e) {
                ctx.failed(mutation, WriteResult.failed(e.getClass().getSimpleName() + ":" + e.getMessage()));
                return false;
            }
        }
        return true;
    }
}