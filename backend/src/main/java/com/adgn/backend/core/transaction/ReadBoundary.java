package com.adgn.backend.core.transaction;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

/** One new primary-database RR snapshot for current qualification and all response projections. */
public final class ReadBoundary {
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;
    public ReadBoundary(PlatformTransactionManager manager, DataSource source) {
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transactions.setReadOnly(true);
    }
    public <A,R> R read(CommandBoundary.CheckedSupplier<A> qualify, CommandBoundary.CheckedFunction<A,R> work) throws Exception {
        return BoundaryTransactions.execute(transactions, status -> {
            // A nonlocking InnoDB read establishes the snapshot before qualification, without blocking writers.
            var rows = jdbc.queryForList("SELECT revision FROM mutation_guard WHERE id=1", Long.class);
            if (rows.size() != 1) throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR, null);
            return work.apply(qualify.get());
        });
    }
}
