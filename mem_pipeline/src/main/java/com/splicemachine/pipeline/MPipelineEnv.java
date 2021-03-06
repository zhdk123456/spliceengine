/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.pipeline;

import com.splicemachine.access.api.DistributedFileSystem;
import com.splicemachine.access.api.PartitionFactory;
import com.splicemachine.access.api.SConfiguration;
import com.splicemachine.access.api.SnowflakeFactory;
import com.splicemachine.concurrent.Clock;
import com.splicemachine.pipeline.api.BulkWriterFactory;
import com.splicemachine.pipeline.api.PipelineExceptionFactory;
import com.splicemachine.pipeline.api.PipelineMeter;
import com.splicemachine.pipeline.api.WritePipelineFactory;
import com.splicemachine.pipeline.context.NoOpPipelineMeter;
import com.splicemachine.pipeline.contextfactory.ContextFactoryDriver;
import com.splicemachine.pipeline.mem.DirectBulkWriterFactory;
import com.splicemachine.pipeline.mem.DirectPipelineExceptionFactory;
import com.splicemachine.pipeline.traffic.AtomicSpliceWriteControl;
import com.splicemachine.pipeline.utils.PipelineCompressor;
import com.splicemachine.si.api.data.ExceptionFactory;
import com.splicemachine.si.api.data.OperationFactory;
import com.splicemachine.si.api.data.OperationStatusFactory;
import com.splicemachine.si.api.data.TxnOperationFactory;
import com.splicemachine.si.api.readresolve.KeyedReadResolver;
import com.splicemachine.si.api.readresolve.RollForward;
import com.splicemachine.si.api.txn.KeepAliveScheduler;
import com.splicemachine.si.api.txn.TxnStore;
import com.splicemachine.si.api.txn.TxnSupplier;
import com.splicemachine.si.impl.driver.SIDriver;
import com.splicemachine.si.impl.driver.SIEnvironment;
import com.splicemachine.storage.DataFilterFactory;
import com.splicemachine.storage.PartitionInfoCache;
import com.splicemachine.timestamp.api.TimestampSource;

import java.io.IOException;

/**
 * @author Scott Fines
 *         Date: 1/11/16
 */
public class MPipelineEnv  implements PipelineEnvironment{
    private SIEnvironment siEnv;
    private BulkWriterFactory writerFactory;
    private ContextFactoryDriver ctxFactoryDriver;
    private final WritePipelineFactory pipelineFactory= new MappedPipelineFactory();

    public MPipelineEnv(SIEnvironment siEnv) throws IOException{
        super();
        this.siEnv=siEnv;
        this.writerFactory = new DirectBulkWriterFactory(new MappedPipelineFactory(),
                new AtomicSpliceWriteControl(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE),
                pipelineExceptionFactory(),pipelineMeter());
        this.ctxFactoryDriver = ContextFactoryDriverService.loadDriver();
    }

    @Override
    public OperationFactory baseOperationFactory(){
        return siEnv.baseOperationFactory();
    }

    @Override
    public PartitionFactory tableFactory(){
        return siEnv.tableFactory();
    }

    @Override
    public ExceptionFactory exceptionFactory(){
        return siEnv.exceptionFactory();
    }

    @Override
    public SConfiguration configuration(){
        return siEnv.configuration();
    }

    @Override
    public TxnStore txnStore(){
        return siEnv.txnStore();
    }

    @Override
    public OperationStatusFactory statusFactory(){
        return siEnv.statusFactory();
    }

    @Override
    public TimestampSource timestampSource(){
        return siEnv.timestampSource();
    }

    @Override
    public TxnSupplier txnSupplier(){
        return siEnv.txnSupplier();
    }

    @Override
    public RollForward rollForward(){
        return siEnv.rollForward();
    }

    @Override
    public TxnOperationFactory operationFactory(){
        return siEnv.operationFactory();
    }

    @Override
    public SIDriver getSIDriver(){
        return siEnv.getSIDriver();
    }

    @Override
    public PartitionInfoCache partitionInfoCache(){
        return siEnv.partitionInfoCache();
    }

    @Override
    public KeepAliveScheduler keepAliveScheduler(){
        return siEnv.keepAliveScheduler();
    }

    @Override
    public DataFilterFactory filterFactory(){
        return siEnv.filterFactory();
    }

    @Override
    public Clock systemClock(){
        return siEnv.systemClock();
    }

    @Override
    public KeyedReadResolver keyedReadResolver(){
        return siEnv.keyedReadResolver();
    }

    @Override
    public PipelineExceptionFactory pipelineExceptionFactory(){
        return DirectPipelineExceptionFactory.INSTANCE;
    }

    @Override
    public PipelineDriver getPipelineDriver(){
        return PipelineDriver.driver();
    }

    @Override
    public ContextFactoryDriver contextFactoryDriver(){
        return ctxFactoryDriver;
    }

    @Override
    public PipelineCompressor pipelineCompressor(){
        return null;
    }

    @Override
    public BulkWriterFactory writerFactory(){
        return writerFactory;
    }

    @Override
    public PipelineMeter pipelineMeter(){
        return NoOpPipelineMeter.INSTANCE;
    }

    @Override
    public WritePipelineFactory pipelineFactory(){
        return pipelineFactory;
    }

    @Override
    public DistributedFileSystem fileSystem(){
        return siEnv.fileSystem();
    }

    @Override
    public SnowflakeFactory snowflakeFactory() {
        return siEnv.snowflakeFactory();
    }
}
