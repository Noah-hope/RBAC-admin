package com.adgn.backend.core.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;

/** Opt in on DATETIME(6) fields; UTC and truncation are independent of the JVM zone. */
@Converter
public class UtcInstantConverter implements AttributeConverter<Instant, Timestamp> {
    @Override
    public Timestamp convertToDatabaseColumn(Instant value) {
        return value == null ? null : Timestamp.from(value.truncatedTo(ChronoUnit.MICROS));
    }

    @Override
    public Instant convertToEntityAttribute(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
