package com.adgn.backend.core.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** An operational log identity, never an employee, account or authorization scope. */
@Validated
@ConfigurationProperties(prefix = "rbac.runtime", ignoreUnknownFields = false)
public record RuntimeProperties(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String instanceId) {
}
