package com.adgn.peoplefixture;

import com.adgn.backend.people.OrganizationReader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class OrganizationReaderTest {
    @Test void refusesUnboundReadsBeforeOpeningAnyConnection() {
        var reader = new OrganizationReader(new DriverManagerDataSource("jdbc:unreachable:must-not-connect"));
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> reader.company(id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reader.department(id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reader.position(id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reader.employee(id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reader.sourceIdentity("hr", "001")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reader.organizationMapping("hr", "a/b")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reader.applicableEmployments(id, id, id)).isInstanceOf(IllegalStateException.class);
    }
}
