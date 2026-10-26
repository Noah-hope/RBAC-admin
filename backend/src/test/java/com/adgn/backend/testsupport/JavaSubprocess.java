package com.adgn.backend.testsupport;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 测试专用的显式 Java 子进程入口。 */
public final class JavaSubprocess {
    private JavaSubprocess() { }

    public static ProcessBuilder command(String... arguments) {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> a.startsWith("-javaagent:") && a.contains("org.jacoco.agent"))
                .findFirst().ifPresent(agent -> {
                    try {
                        Path directory = Path.of("target/jacoco-subprocess").toAbsolutePath();
                        Files.createDirectories(directory);
                        int separator = agent.indexOf('=');
                        String prefix = separator < 0 ? agent + "=" : agent.substring(0, separator + 1);
                        var options = new ArrayList<String>();
                        if (separator >= 0) {
                            for (String option : agent.substring(separator + 1).split(",")) {
                                if (!option.startsWith("destfile=") && !option.startsWith("append=")
                                        && !option.startsWith("output=") && !option.startsWith("dumponexit=")) {
                                    options.add(option);
                                }
                            }
                        }
                        // 独立文件避免并发短进程争用同一个 exec；只转交 JaCoCo，不转交凭据或 Mockito。
                        options.add("destfile=" + directory.resolve(UUID.randomUUID() + ".exec"));
                        options.addAll(List.of("append=false", "output=file", "dumponexit=true"));
                        command.add(prefix + String.join(",", options));
                    } catch (IOException failure) {
                        throw new UncheckedIOException("无法建立子进程覆盖率目录", failure);
                    }
                });
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command);
    }
}
