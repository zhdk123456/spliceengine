package com.splicemachine.derby.impl.load;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.metrics.IOStats;
import org.apache.hadoop.fs.FileSystem;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.IOException;

/**
 * @author Scott Fines
 * Created on: 9/30/13
 */
public interface ImportReader extends Closeable,Externalizable {

    void setup(FileSystem fileSystem,ImportContext ctx) throws IOException;

    String[] nextRow() throws IOException, StandardException;

	String[][] nextRowBatch() throws IOException, StandardException;

	IOStats getStats();

	boolean shouldParallelize(FileSystem fs, ImportContext ctx) throws IOException;
}