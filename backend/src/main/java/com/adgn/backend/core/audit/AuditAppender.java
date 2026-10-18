package com.adgn.backend.core.audit;

import com.adgn.backend.core.transaction.WriteContext;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.UUID;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;
import org.springframework.jdbc.core.JdbcTemplate;

public final class AuditAppender {
    private final DataSource source;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    public AuditAppender(DataSource source, JsonMapper json) {
        this.source = source;
        this.json = json;
        this.jdbc = new JdbcTemplate(source);
    }
    public UUID append(WriteContext<?> context, AuditRecord event) {
        context.requireActive(source);
        try {
            var snapshot = json.readTree(event.snapshotJson());
            if (snapshot == null || !snapshot.isObject() || !snapshot.path("schemaVersion").isIntegralNumber()
                    || snapshot.path("schemaVersion").intValue() != 1) {
                throw new IllegalArgumentException("Audit snapshot requires schemaVersion=1 object");
            }
            var actor = context.actor();
            bounded(actor.id(), 64, false);
            bounded(event.eventType(), 64, false);
            bounded(event.targetId(), 64, false);
            bounded(event.result(), 64, false);
            bounded(event.correlationId(), 64, false);
            bounded(event.reason(), 500, true);
            var scope = event.scope();
            UUID id = context.newId();
            String[] fields = {event.eventType(), actor.kind().name(), actor.id(), text(event.originalSubmitterId()),
                    event.targetId(), text(event.changeRequestId()), text(scope.companyId()), text(scope.departmentId()),
                    text(scope.systemId()), scope.visibility().name(), event.result(), event.correlationId(), event.reason(),
                    json.writeValueAsString(snapshot)};
            jdbc.update("INSERT INTO audit_event(id,occurred_at,event_type,actor_kind,actor_id,original_submitter_id,"
                            + "target_id,change_request_id,company_id,source_department_id,system_id,visibility,result,"
                            + "correlation_id,reason,event_snapshot) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    statement -> {
                        statement.setString(1, id.toString());
                        statement.setTimestamp(2, Timestamp.from(context.now()), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                        for (int index = 0; index < fields.length; index++) statement.setString(index + 3, fields[index]);
                    });
            for (var subject : event.subjects()) {
                bounded(subject.type(), 16, false);
                bounded(subject.id(), 64, false);
                jdbc.update("INSERT INTO audit_subject(audit_event_id,subject_type,subject_id) VALUES(?,?,?)",
                        id.toString(), subject.type(), subject.id());
            }
            return id;
        } catch (RuntimeException failure) {
            context.markFailed(failure);
            throw failure;
        }
    }
    private static String text(UUID id) { return id == null ? null : id.toString(); }
    private static void bounded(String value, int maximum, boolean allowEmpty) {
        if (value == null || (!allowEmpty && value.isBlank()) || value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException("Invalid audit field");
        }
    }
}
