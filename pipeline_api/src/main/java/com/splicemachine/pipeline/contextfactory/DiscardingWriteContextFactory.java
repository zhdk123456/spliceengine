package com.splicemachine.pipeline.contextfactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import com.splicemachine.access.api.ServerControl;
import com.splicemachine.ddl.DDLMessage;
import com.splicemachine.pipeline.context.WriteContext;
import com.splicemachine.pipeline.writehandler.SharedCallBufferFactory;
import com.splicemachine.si.api.txn.TxnView;

/**
 * Simply delegates to another TransactionalRegion, keeps a reference count, only invokes real close() when ref count
 * goes to zero, at which time also removes self from manger.
 */
class DiscardingWriteContextFactory<T> implements WriteContextFactory<T> {

    private final long conglomId;
    private final WriteContextFactory<T> delegate;
    private final AtomicInteger refCount = new AtomicInteger(0);
    private volatile boolean closed = false;

    public DiscardingWriteContextFactory(long conglomId, WriteContextFactory<T> delegate) {
        this.conglomId = conglomId;
        this.delegate = delegate;
    }

    @Override
    public void close() {
        int remaining = refCount.decrementAndGet();
        if (remaining <= 0) {
            closed = true;
            delegate.close();
            WriteContextFactoryManager.remove(conglomId, this); //remove us from the map
        }
    }

    public void incrementRefCount() {
        assert !closed : "Cannot register with a closed Factory!";
        refCount.incrementAndGet();
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // purely delegate methods
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Override
    public WriteContext create(SharedCallBufferFactory indexSharedCallBuffer, TxnView txn, T key, ServerControl env) throws IOException, InterruptedException {
        return delegate.create(indexSharedCallBuffer, txn, key, env);
    }

    @Override
    public WriteContext create(SharedCallBufferFactory indexSharedCallBuffer, TxnView txn, T key, int expectedWrites, boolean skipIndexWrites, ServerControl env) throws IOException, InterruptedException {
        return delegate.create(indexSharedCallBuffer, txn, key, expectedWrites, skipIndexWrites, env);
    }

    @Override
    public WriteContext createPassThrough(SharedCallBufferFactory indexSharedCallBuffer, TxnView txn, T key, int expectedWrites, ServerControl env) throws IOException, InterruptedException {
        return delegate.createPassThrough(indexSharedCallBuffer, txn, key, expectedWrites, env);
    }

    @Override
    public void addDDLChange(DDLMessage.DDLChange ddlChange) {
        delegate.addDDLChange(ddlChange);
    }

    @Override
    public void prepare() {
        delegate.prepare();
    }

    @Override
    public boolean hasDependentWrite(TxnView txn) throws IOException, InterruptedException {
        return delegate.hasDependentWrite(txn);
    }


}