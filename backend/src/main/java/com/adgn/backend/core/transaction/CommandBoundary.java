package com.adgn.backend.core.transaction;

import com.adgn.backend.core.audit.AuditRecord;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import javax.sql.DataSource;
import java.time.Clock;
import java.util.Objects;

/** Outermost local write boundary. No network calls or per-item subtransactions inside the callback. */
public final class CommandBoundary {
    private final Clock clock;
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public CommandBoundary(PlatformTransactionManager manager, DataSource source, Clock clock) {
        this.clock = clock;
        this.source = source;
        this.jdbc = new JdbcTemplate(source);
        this.transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public record Identity<A>(A authority, AuditRecord.Actor actor) { }
    @FunctionalInterface public interface CheckedSupplier<T> { T get() throws Exception; }
    @FunctionalInterface public interface CheckedFunction<T, R> { R apply(T value) throws Exception; }
    public <A, R> R execute(CheckedSupplier<Identity<A>> qualify, CheckedFunction<WriteContext<A>, R> work) throws Exception {
        return BoundaryTransactions.execute(transactions, status -> {
            var rows = jdbc.queryForList("SELECT revision FROM mutation_guard WHERE id=1 FOR UPDATE", Long.class);
            if (rows.size() != 1 || rows.getFirst() == null || rows.getFirst() < 0 || rows.getFirst() >= 9007199254740991L) {
                throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR, null);
            }
            var identity = Objects.requireNonNull(qualify.get(), "Current authorization must supply an identity");
            Objects.requireNonNull(identity.actor(), "Current identity must supply an audit actor");
            var context = new WriteContext<>(identity, clock, source, DataSourceUtils.getConnection(source), status);
            TransactionSynchronizationManager.bindResource(WriteContext.class, context);
            try {
                R result = work.apply(context);
                context.checkCommittable();
                if (jdbc.update("UPDATE mutation_guard SET revision=revision+1 WHERE id=1 AND revision=?", rows.getFirst()) != 1) {
                    throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR, null);
                }
                return result;
            } finally {
                context.close();
                TransactionSynchronizationManager.unbindResource(WriteContext.class);
            }
        });
    }
}
