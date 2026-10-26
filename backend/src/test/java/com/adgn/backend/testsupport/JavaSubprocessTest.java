package com.adgn.backend.testsupport;

import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

class JavaSubprocessTest {
    private Set<Path> executionFiles() throws Exception {
        Path directory = Path.of("target/jacoco-subprocess");
        if (!Files.exists(directory)) return Set.of();
        try (var files = Files.list(directory)) {
            return files.filter(p -> p.toString().endsWith(".exec")).collect(Collectors.toSet());
        }
    }

    @Test void childActuallyRunsAndCollectsCoverageOnlyWhenParentIsInstrumented() throws Exception {
        boolean instrumented = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(a -> a.startsWith("-javaagent:") && a.contains("org.jacoco.agent"));
        var before = executionFiles();
        var process = JavaSubprocess.command("-cp", System.getProperty("java.class.path"),
                Probe.class.getName()).redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("child-executed");
            var created = executionFiles().stream().filter(p -> !before.contains(p)).toList();
            if (instrumented) {
                assertThat(created).as("启用 coverage 时子 JVM 必须产生独立执行记录").hasSize(1);
                assertThat(Files.size(created.getFirst())).isGreaterThan(0);
            } else {
                assertThat(created).isEmpty();
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor();
        }
    }

    public static final class Probe {
        public static void main(String[] args) { System.out.println("child-executed"); }
    }
}
