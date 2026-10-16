package com.bbh.itss.beadle.support

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus

import static org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive
import static org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly

/**
 * Logs what happens to the transactions of a spec instead of touching a database: {@code begin read-only},
 * {@code begin read-write}, {@code suspend}, {@code resume}, {@code commit} and {@code rollback}. A transaction
 * started while another is open joins it or suspends it, as a real transaction manager does. With
 * {@link #commitsToRefuse} above zero, that many commits fail as a database refusing the flush would.
 */
class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    final List<String> log = []
    int commitsToRefuse

    private final Deque<Object> open = new ArrayDeque<>()

    static String transactionState() {
        if (!isActualTransactionActive()) {
            return 'without transaction'
        }
        isCurrentTransactionReadOnly() ? 'read-only' : 'read-write'
    }

    @Override
    protected Object doGetTransaction() {
        new Transaction(existing: !open.isEmpty())
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        (transaction as Transaction).existing
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        open.push(transaction)
        log << (definition.readOnly ? 'begin read-only' : 'begin read-write')
    }

    @Override
    protected Object doSuspend(Object transaction) {
        log << 'suspend'
        open.pop()
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        log << 'resume'
        open.push(suspendedResources)
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        if (commitsToRefuse > 0) {
            commitsToRefuse--
            log << 'commit refused'
            throw new TransactionSystemException('The database refused the commit')
        }
        log << 'commit'
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        log << 'rollback'
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        log << 'rollback-only'
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        open.remove(transaction)
    }

    static class Transaction {
        boolean existing
    }
}
