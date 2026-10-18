package com.adgn.backend.core.transaction;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.TransactionException;
import java.sql.SQLException;

public final class PersistenceFailure extends RuntimeException {
    public enum Kind { DEPENDENCY_UNAVAILABLE, DATA_INTEGRITY_ERROR }
    private final Kind kind;
    public PersistenceFailure(Kind kind, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
    }
    public Kind kind() { return kind; }
    public static RuntimeException translate(RuntimeException cause) {
        if (cause instanceof PersistenceFailure) return cause;
        if (!(cause instanceof DataAccessException) && !(cause instanceof TransactionException)) return cause;
        for (Throwable nested = cause; nested != null; nested = nested.getCause()) {
            if (nested instanceof SQLException sql && (sql.getErrorCode() == 1205 || sql.getErrorCode() == 1213
                    || (sql.getSQLState() != null && sql.getSQLState().startsWith("08")))) {
                return new PersistenceFailure(Kind.DEPENDENCY_UNAVAILABLE, cause);
            }
        }
        return new PersistenceFailure(cause instanceof TransientDataAccessException || cause instanceof TransactionException
                ? Kind.DEPENDENCY_UNAVAILABLE : Kind.DATA_INTEGRITY_ERROR, cause);
    }
}
