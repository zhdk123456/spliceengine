package com.splicemachine.si.impl;

import com.splicemachine.si.api.ClientTransactor;
import com.splicemachine.si.api.FilterState;
import com.splicemachine.si.api.TimestampSource;
import com.splicemachine.si.api.TransactionId;
import com.splicemachine.si.api.Transactor;
import com.splicemachine.si.data.api.SDataLib;
import com.splicemachine.si.data.api.SGet;
import com.splicemachine.si.data.api.SRead;
import com.splicemachine.si.data.api.SRowLock;
import com.splicemachine.si.data.api.SScan;
import com.splicemachine.si.data.api.STable;
import com.splicemachine.si.data.api.STableWriter;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.splicemachine.constants.TransactionConstants.SUPPRESS_INDEXING_ATTRIBUTE_NAME;
import static com.splicemachine.constants.TransactionConstants.SUPPRESS_INDEXING_ATTRIBUTE_VALUE;
import static com.splicemachine.si.impl.TransactionStatus.ACTIVE;
import static com.splicemachine.si.impl.TransactionStatus.COMMITTED;
import static com.splicemachine.si.impl.TransactionStatus.COMMITTING;
import static com.splicemachine.si.impl.TransactionStatus.ERROR;
import static com.splicemachine.si.impl.TransactionStatus.LOCAL_COMMIT;
import static com.splicemachine.si.impl.TransactionStatus.ROLLED_BACK;

/**
 * Central point of implementation of the "snapshot isolation" MVCC algorithm that provides transactions across atomic
 * row updates in the underlying store.
 */
public class SiTransactor implements Transactor, ClientTransactor {
    static final Logger LOG = Logger.getLogger(SiTransactor.class);

    private final TimestampSource timestampSource;
    private final SDataLib dataLib;
    private final STableWriter dataWriter;
    private final DataStore dataStore;
    private final TransactionStore transactionStore;

    public SiTransactor(TimestampSource timestampSource, SDataLib dataLib, STableWriter dataWriter, DataStore dataStore,
                        TransactionStore transactionStore) {
        this.timestampSource = timestampSource;
        this.dataLib = dataLib;
        this.dataWriter = dataWriter;
        this.dataStore = dataStore;
        this.transactionStore = transactionStore;
    }

    /***********************************/
    // Transaction control

    @Override
    public TransactionId beginTransaction(boolean allowWrites, boolean readUncommitted, boolean readCommitted)
            throws IOException {
        final TransactionParams params = new TransactionParams(null, null, allowWrites, readUncommitted, readCommitted);
        return beginTransactionDirect(params, ACTIVE);
    }

    @Override
    public TransactionId beginChildTransaction(TransactionId parent, boolean dependent, boolean allowWrites,
                                               Boolean readUncommitted, Boolean readCommitted) throws IOException {
        if (dependent || allowWrites) {
            final TransactionParams params = new TransactionParams(parent, dependent, allowWrites, readUncommitted, readCommitted);
            return createHeavyChildTransaction(params);
        } else {
            return createLightweightChildTransaction(parent);
        }
    }

    /**
     * Create a "full-fledged" child transaction. This will get it's own entry in the transaction table.
     */
    private TransactionId createHeavyChildTransaction(TransactionParams params)
            throws IOException {
        final TransactionId childTransactionId = beginTransactionDirect(params, ACTIVE);
        transactionStore.addChildToTransaction(params.parent, childTransactionId);
        return childTransactionId;
    }

    /**
     * Start a transaction. Either a root-level transaction or a nested child transaction.
     */
    private TransactionId beginTransactionDirect(TransactionParams params, TransactionStatus status)
            throws IOException {
        final SiTransactionId transactionId = assignTransactionId();
        transactionStore.recordNewTransaction(transactionId, params, status);
        return transactionId;
    }

    /**
     * Generate the next sequential timestamp / transaction ID.
     *
     * @return the new transaction ID.
     */
    private SiTransactionId assignTransactionId() {
        return new SiTransactionId(timestampSource.nextTimestamp());
    }

    /**
     * Create a non-resource intensive child. This avoids hitting the transaction table. The same transaction ID is
     * given to many callers, and calls to commit, rollback, etc are ignored.
     */
    private TransactionId createLightweightChildTransaction(TransactionId parent) {
        return new SiTransactionId(parent.getId(), true);
    }

    @Override
    public void commit(TransactionId transactionId) throws IOException {
        if (!isIndependentReadOnly(transactionId)) {
            commitDirect(transactionId);
        }
    }

    private void commitDirect(TransactionId transactionId) throws IOException {
        final Transaction transaction = transactionStore.getTransaction(transactionId);
        ensureTransactionActive(transaction);
        if (transaction.isNestedDependent()) {
            performLocalCommit(transactionId);
        } else {
            performCommit(transaction);
        }
    }

    /**
     * Nested, dependent children commit locally only. They will finally commit when the root parent transaction commits.
     */
    private void performLocalCommit(TransactionId transactionId) throws IOException {
        // perform "local" commit only within the parent transaction
        transactionStore.recordTransactionStatusChange(transactionId, LOCAL_COMMIT);
    }

    /**
     * Update the transaction table to show this transaction is committed.
     */
    private void performCommit(Transaction transaction) throws IOException {
        final SiTransactionId transactionId = transaction.getTransactionId();
        final List<Long> childrenToCommit = findChildrenToCommit(transaction);
        transactionStore.recordTransactionStatusChange(transactionId, COMMITTING);
        // TODO: need to sort out how to take child transactions through COMMITTING state, alternatively don't commit
        // TODO: children directly, rather let them inherit their commit status from their parent
        final long endId = timestampSource.nextTimestamp();
        transactionStore.recordTransactionEnd(transactionId, endId, COMMITTED);
        commitAll(childrenToCommit, endId);
    }

    /**
     * Filter the immediate children of the transaction to find the ones that can be committed.
     */
    private List<Long> findChildrenToCommit(Transaction transaction) throws IOException {
        final List<Long> childrenToCommit = new ArrayList<Long>();
        for (Long childId : transaction.getChildren()) {
            if (transactionStore.getTransaction(childId).isEffectivelyActive()) {
                childrenToCommit.add(childId);
            }
        }
        return childrenToCommit;
    }

    /**
     * Update the transaction table to record all of the transactionIds as committed as of the timestamp.
     */
    private void commitAll(List<Long> transactionIds, long timestamp) throws IOException {
        for (Long transactionId : transactionIds) {
            transactionStore.recordTransactionEnd(transactionId, timestamp, COMMITTED);
        }
    }

    @Override
    public void rollback(TransactionId transactionId) throws IOException {
        if (!isIndependentReadOnly(transactionId)) {
            rollbackDirect(transactionId);
        }
    }

    private void rollbackDirect(TransactionId transactionId) throws IOException {
        Transaction transaction = transactionStore.getTransaction(transactionId);
        // currently the application above us tries to rollback already committed transactions.
        // This is poor form, but if it happens just silently ignore it.
        if (transaction.isActive() && !transaction.isLocallyCommitted()) {
            transactionStore.recordTransactionStatusChange(transactionId, ROLLED_BACK);
        }
    }

    @Override
    public void fail(TransactionId transactionId) throws IOException {
        if (!isIndependentReadOnly(transactionId)) {
            failDirect(transactionId);
        }
    }

    private void failDirect(TransactionId transactionId) throws IOException {
        Transaction transaction = transactionStore.getTransaction(transactionId);
        ensureTransactionActive(transaction);
        transactionStore.recordTransactionStatusChange(transactionId, ERROR);
    }

    private boolean isIndependentReadOnly(TransactionId transactionId) {
        return ((SiTransactionId) transactionId).independentReadOnly;
    }

    /***********************************/
    // Transaction ID manipulation

    @Override
    public TransactionId transactionIdFromString(String transactionId) {
        return new SiTransactionId(transactionId);
    }

    @Override
    public TransactionId transactionIdFromOperation(Object operation) {
        return dataStore.getTransactionIdFromOperation(operation);
    }

    // Operation initialization. These are expected to be called "client-side" when operations are created.

    @Override
    public void initializeGet(String transactionId, SGet get) throws IOException {
        initializeOperation(transactionId, get);
    }

    @Override
    public void initializeScan(String transactionId, SScan scan) {
        initializeOperation(transactionId, scan);
    }

    @Override
    public void initializePut(String transactionId, Object put) {
        initializeOperation(transactionId, put);
    }

    private void initializeOperation(String transactionId, Object operation) {
        flagForSiTreatment((SiTransactionId) transactionIdFromString(transactionId), operation);
    }

    @Override
    public Object createDeletePut(TransactionId transactionId, Object rowKey) {
        return createDeletePutDirect((SiTransactionId) transactionId, rowKey);
    }

    /**
     * Create a "put" operation that will effectively delete a given row.
     */
    private Object createDeletePutDirect(SiTransactionId transactionId, Object rowKey) {
        final Object deletePut = dataLib.newPut(rowKey);
        flagForSiTreatment(transactionId, deletePut);
        dataStore.setTombstoneOnPut(deletePut, transactionId);
        dataStore.setDeletePutAttribute(deletePut);
        return deletePut;
    }

    @Override
    public boolean isDeletePut(Object put) {
        final Boolean deleteAttribute = dataStore.getDeletePutAttribute(put);
        return (deleteAttribute != null && deleteAttribute);
    }

    /**
     * Set an attribute on the operation that identifies it as needing "snapshot isolation" treatment. This is so that
     * later when the operation comes through for processing we will know how to handle it.
     */
    private void flagForSiTreatment(SiTransactionId transactionId, Object operation) {
        dataStore.setSiNeededAttribute(operation);
        dataStore.setTransactionId(transactionId, operation);
    }

    // Operation pre-processing. These are to be called "server-side" when we are about to process an operation.

    @Override
    public void preProcessRead(SRead read) throws IOException {
        dataLib.setReadTimeRange(read, 0, Long.MAX_VALUE);
        dataLib.setReadMaxVersions(read);
        dataStore.addSiFamilyToReadIfNeeded(read);
    }

    /***********************************/
    // Process update operations

    @Override
    public boolean processPut(STable table, Object put) throws IOException {
        if (isFlaggedForSiTreatment(put)) {
            processPutDirect(table, put);
            return true;
        } else {
            return false;
        }
    }

    private void processPutDirect(STable table, Object put) throws IOException {
        final SiTransactionId transactionId = dataStore.getTransactionIdFromOperation(put);
        final ImmutableTransaction transaction = transactionStore.getImmutableTransaction(transactionId);
        ensureTransactionAllowsWrites(transaction);
        performPut(table, put, transaction);
    }

    private void performPut(STable table, Object put, ImmutableTransaction transaction) throws IOException {
        final Object rowKey = dataLib.getPutKey(put);
        final SRowLock lock = dataWriter.lockRow(table, rowKey);
        // This is the critical section that runs while the row is locked.
        try {
            ensureNoWriteConflict(transaction, table, rowKey);
            final Object newPut = createUltimatePut(transaction, lock, put);
            suppressIndexing(newPut);
            dataWriter.write(table, newPut, lock);
        } finally {
            dataWriter.unLockRow(table, lock);
        }
    }

    /**
     * While we hold the lock on the row, check to make sure that no transactions have updated the row since the
     * updating transaction started.
     */
    private void ensureNoWriteConflict(ImmutableTransaction updateTransaction, STable table, Object rowKey)
            throws IOException {
        final List dataCommitKeyValues = dataStore.getCommitTimestamp(table, rowKey);
        if (dataCommitKeyValues != null) {
            checkCommitTimestampsForConflicts(updateTransaction, dataCommitKeyValues);
        }
    }

    /**
     * Look at all of the values in the "commitTimestamp" column to see if there are write collisions.
     */
    private void checkCommitTimestampsForConflicts(ImmutableTransaction updateTransaction, List dataCommitKeyValues)
            throws IOException {
        for (Object dataCommitKeyValue : dataCommitKeyValues) {
            final long dataTransactionId = dataLib.getKeyValueTimestamp(dataCommitKeyValue);
            final Transaction dataTransaction = transactionStore.getTransaction(dataTransactionId);
            checkTransactionConflict(updateTransaction, dataTransaction);
        }
    }

    /**
     * Determine if the dataTransaction conflicts with the updateTransaction.
     */
    private void checkTransactionConflict(ImmutableTransaction updateTransaction, Transaction dataTransaction)
            throws IOException {
        if (dataTransaction.committedAfter(updateTransaction)) {
            // if the row was updated after this update's transaction started then fail
            failOnWriteConflict(updateTransaction);
        } else if (dataTransaction.isEffectivelyActive() && !dataTransaction.isEffectivelyPartOfTransaction(updateTransaction)) {
            // if the row was written by an active transaction, that is not part of this update then fail
            failOnWriteConflict(updateTransaction);
        }
    }

    /**
     * A write conflict was discovered, throw an exception and kill the offending transaction.
     */
    private void failOnWriteConflict(ImmutableTransaction transaction) throws IOException {
        fail(transaction.getTransactionId());
        throw new WriteConflict("write/write conflict");
    }

    /**
     * Create a new operation, with the lock, that has all of the keyValues from the original operation.
     * This will also set the timestamp of the data being updated to reflect the transaction doing the update.
     */
    Object createUltimatePut(ImmutableTransaction transaction, SRowLock lock, Object put) {
        final Object rowKey = dataLib.getPutKey(put);
        final Object newPut = dataLib.newPut(rowKey, lock);
        final SiTransactionId transactionId = transaction.getTransactionId();
        final long timestamp = transactionId.getId();
        dataStore.copyPutKeyValues(put, newPut, timestamp);
        dataStore.addTransactionIdToPut(newPut, transactionId);
        return newPut;
    }

    /**
     * When this new operation goes through the co-processor stack it should not be indexed (because it already has been
     * when the original operation went through).
     */
    private void suppressIndexing(Object newPut) {
        dataLib.addAttribute(newPut, SUPPRESS_INDEXING_ATTRIBUTE_NAME, SUPPRESS_INDEXING_ATTRIBUTE_VALUE);
    }

    /***********************************/
    // Process read operations

    @Override
    public boolean isFilterNeeded(Object operation) {
        return isFlaggedForSiTreatment(operation);
    }

    @Override
    public FilterState newFilterState(STable table, TransactionId transactionId) throws IOException {
        return new SiFilterState(dataLib, dataStore, transactionStore, table,
                transactionStore.getImmutableTransaction(transactionId));
    }

    @Override
    public Filter.ReturnCode filterKeyValue(FilterState filterState, Object keyValue) throws IOException {
        return ((SiFilterState) filterState).filterKeyValue(keyValue);
    }

    /***********************************/
    // Helpers

    /**
     * Is this operation supposed to be handled by "snapshot isolation".
     */
    private boolean isFlaggedForSiTreatment(Object put) {
        return isTrue(dataStore.getSiNeededAttribute(put));
    }

    private boolean isTrue(Boolean b) {
        return b != null && b;
    }

    /**
     * Throw an exception if the transaction is not active.
     */
    private void ensureTransactionActive(Transaction transaction) throws IOException {
        if (!transaction.isEffectivelyActive()) {
            throw new DoNotRetryIOException("transaction is not ACTIVE: " +
                    transaction.getTransactionId().getTransactionIdString());
        }
    }

    /**
     * Throw an exception if this is a read-only transaction.
     */
    private void ensureTransactionAllowsWrites(ImmutableTransaction transaction) throws IOException {
        if (transaction.isReadOnly()) {
            throw new DoNotRetryIOException("transaction is read only");
        }
    }

}
