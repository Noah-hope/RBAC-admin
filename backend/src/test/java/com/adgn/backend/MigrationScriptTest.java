package com.adgn.backend;

import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.parser.Parser;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline Flyway/MySQL parser characterization; never executes SQL or proves engine validity. */
class MigrationScriptTest {
    private List<String> parse(String filename) throws Exception {
        var resource = new ClassPathResource("db/migration/" + filename);
        assertThat(resource.exists()).as("packaged migration %s", filename).isTrue();
        String script = resource.getContentAsString(StandardCharsets.UTF_8);
        var parserClass = Class.forName("org.flywaydb.database.mysql.MySQLParser");
        Parser parser = (Parser) parserClass.getConstructor(Configuration.class, ParsingContext.class)
                .newInstance(new ClassicConfiguration(), new ParsingContext());
        List<String> statements = new ArrayList<>();
        try (var iterator = parser.parse(new StringResource(script))) {
            while (iterator.hasNext()) statements.add(iterator.next().getSql());
        }
        return statements;
    }

    @Test
    void tablesAreCreatedBeforeForeignKeysAreAdded() throws Exception {
        var statements = parse("V1__create_tables_and_constraints.sql");
        assertThat(statements).hasSize(124); // 2 session SETs + 43 CREATE TABLEs + 79 ALTER TABLEs
        assertThat(statements.subList(2, 45)).allSatisfy(sql -> assertThat(sql).contains("CREATE TABLE"));
        assertThat(statements.subList(45, 124)).allSatisfy(sql ->
                assertThat(sql).contains("ALTER TABLE", "FOREIGN KEY"));
    }

    @Test
    void triggerBodiesRemainWholeStatementsDespiteInternalSemicolons() throws Exception {
        var statements = parse("V2__create_immutability_triggers.sql");
        assertThat(statements).hasSize(41);
        assertThat(statements).allSatisfy(sql ->
                assertThat(sql.strip()).contains("CREATE TRIGGER", "BEGIN", "SIGNAL SQLSTATE").endsWith("END"));
    }

    @Test
    void guardBootstrapIsASingleStatement() throws Exception {
        assertThat(parse("V3__initialize_mutation_guard.sql")).hasSize(1);
    }
}
