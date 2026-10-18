package com.adgn.backend.core.transaction;

import com.adgn.backend.core.audit.AuditRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** An internal capability for exactly one thread and one active guarded transaction. */
public final class WriteContext<A> {
    private final CommandBoundary.Identity<A> identity;
    private final Clock clock;
    private final DataSource source;
    private final Connection connection;
    private final TransactionStatus status;
    private final Thread owner = Thread.currentThread();
    private boolean open = true;
    private RuntimeException failure;

    WriteContext(CommandBoundary.Identity<A> identity, Clock clock, DataSource source,
                 Connection connection, TransactionStatus status) {
        this.identity = identity;
        this.clock = clock;
        this.source = source;
        this.connection = connection;
        this.status = status;
    }
    public void requireActive() {
        if (Thread.currentThread() != owner || !open || !TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || TransactionSynchronizationManager.getResource(WriteContext.class) != this
                || !DataSourceUtils.isConnectionTransactional(connection, source)) {
            throw new IllegalStateException("WriteContext requires its original active guarded transaction");
        }
    }
    public void requireActive(DataSource expectedSource) {
        requireActive();
        if (expectedSource != source) throw new IllegalStateException("WriteContext belongs to a different datasource");
    }
    public A authority() { requireActive(); return identity.authority(); }
    public AuditRecord.Actor actor() { requireActive(); return identity.actor(); }
    public Instant now() { requireActive(); return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    public UUID newId() { requireActive(); return UUID.randomUUID(); }
    public void markFailed(RuntimeException cause) {
        requireActive();
        if (failure == null) failure = cause;
        status.setRollbackOnly();
    }
    void checkCommittable() {
        requireActive();
        if (failure != null) throw failure;
        if (status.isRollbackOnly()) throw new PersistenceFailure(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE, null);
    }
    void close() { open = false; }
}
