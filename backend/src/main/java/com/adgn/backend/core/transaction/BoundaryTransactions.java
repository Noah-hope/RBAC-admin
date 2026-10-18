package com.adgn.backend.core.transaction;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

final class BoundaryTransactions {
    private BoundaryTransactions() { }

    static <R> R execute(TransactionTemplate template,
                         CommandBoundary.CheckedFunction<TransactionStatus, R> work) throws Exception {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("An outer transaction cannot call a new command/read boundary");
        }
        try {
            return template.execute(status -> {
                try { return work.apply(status); }
                catch (RuntimeException failure) { throw failure; }
                catch (Exception failure) { throw new CheckedFailure(failure); }
            });
        } catch (CheckedFailure failure) {
            throw (Exception) failure.getCause();
        } catch (RuntimeException failure) {
            throw PersistenceFailure.translate(failure);
        }
    }

    private static final class CheckedFailure extends RuntimeException {
        CheckedFailure(Exception cause) { super(cause); }
    }
}
