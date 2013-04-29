package com.splicemachine.si.impl;

import com.splicemachine.si.data.api.SDataLib;

public class TransactionSchema {
    final String tableName;
    final Object siFamily;
    final Object siChildrenFamily;
    final Object siNull;

    final Object startQualifier;
    final Object parentQualifier;
    final Object dependentQualifier;
    final Object allowWritesQualifier;
    final Object readUncommittedQualifier;
    final Object readCommittedQualifier;
    final Object commitQualifier;
    final Object statusQualifier;
    final Object keepAliveQualifier;

    public TransactionSchema(String tableName, Object siFamily, Object siChildrenFamily, Object siNull,
                             Object startQualifier, Object parentQualifier,
                             Object dependentQualifier,
                             Object allowWritesQualifier, Object readUncommittedQualifier, Object readCommittedQualifier,
                             Object commitQualifier, Object statusQualifier, Object keepAliveQualifier) {
        this.tableName = tableName;
        this.siFamily = siFamily;
        this.siChildrenFamily = siChildrenFamily;
        this.siNull = siNull;

        this.startQualifier = startQualifier;
        this.parentQualifier = parentQualifier;
        this.dependentQualifier = dependentQualifier;
        this.allowWritesQualifier = allowWritesQualifier;
        this.readUncommittedQualifier = readUncommittedQualifier;
        this.readCommittedQualifier = readCommittedQualifier;
        this.commitQualifier = commitQualifier;
        this.statusQualifier = statusQualifier;
        this.keepAliveQualifier = keepAliveQualifier;
    }

    public TransactionSchema encodedSchema(SDataLib SDataLib) {
        return new TransactionSchema(tableName,
                SDataLib.encode(siFamily),
                SDataLib.encode(siChildrenFamily),
                SDataLib.encode(siNull),
                SDataLib.encode(startQualifier),
                SDataLib.encode(parentQualifier),
                SDataLib.encode(dependentQualifier),
                SDataLib.encode(allowWritesQualifier),
                SDataLib.encode(readUncommittedQualifier),
                SDataLib.encode(readCommittedQualifier),
                SDataLib.encode(commitQualifier),
                SDataLib.encode(statusQualifier),
                SDataLib.encode(keepAliveQualifier));
    }
}