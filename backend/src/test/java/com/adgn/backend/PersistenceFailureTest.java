package com.adgn.backend;

import com.adgn.backend.core.transaction.PersistenceFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.UncategorizedDataAccessException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceFailureTest {
    @ParameterizedTest
    @CsvSource({"1205,HY000,DEPENDENCY_UNAVAILABLE", "1213,40001,DEPENDENCY_UNAVAILABLE",
            "0,08001,DEPENDENCY_UNAVAILABLE", "1062,23000,DATA_INTEGRITY_ERROR"})
    void classifiesMysqlStorageFailuresWithoutExposingSql(int vendor, String state, PersistenceFailure.Kind expected) {
        var original = new UncategorizedDataAccessException("private-storage-fixture",
                new SQLException("SELECT private-storage-fixture", state, vendor)) { };
        assertThat(PersistenceFailure.translate(original)).isInstanceOfSatisfying(PersistenceFailure.class, failure -> {
            assertThat(failure.kind()).isEqualTo(expected);
            assertThat(failure.getMessage()).doesNotContain("private-storage-fixture", "SELECT");
        });
    }

    @Test
    void databaseIntegrityFailureIsNotReportedAsSuccessOrBusinessConflict() {
        assertThat(PersistenceFailure.translate(new DataIntegrityViolationException("fixture constraint")))
                .isInstanceOfSatisfying(PersistenceFailure.class,
                        failure -> assertThat(failure.kind()).isEqualTo(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR));
    }

    @Test
    void preservesApplicationBusinessExceptionRatherThanReclassifyingItAsStorageFailure() {
        var failure = new IllegalArgumentException("domain validation fixture");
        assertThat(PersistenceFailure.translate(failure)).isSameAs(failure);
    }
}
