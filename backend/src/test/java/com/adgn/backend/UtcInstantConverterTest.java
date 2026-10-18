package com.adgn.backend;

import com.adgn.backend.core.persistence.UtcInstantConverter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;

class UtcInstantConverterTest {
    private final UtcInstantConverter converter = new UtcInstantConverter();

    @Test
    void writesUtcWithMicrosecondPrecisionRatherThanLocalTimeOrRounding() {
        assertThat(converter.convertToDatabaseColumn(Instant.parse("2026-10-18T02:03:04.123456789Z")))
                .isEqualTo(Timestamp.from(Instant.parse("2026-10-18T02:03:04.123456Z")));
    }

    @Test
    void interpretsStoredDatetimeAsUtc() {
        assertThat(converter.convertToEntityAttribute(Timestamp.from(Instant.parse("2026-10-18T02:03:04.123456Z"))))
                .isEqualTo(Instant.parse("2026-10-18T02:03:04.123456Z"));
    }

    @Test
    void preservesNullableInstants() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
