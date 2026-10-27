package com.adgn.backend;

import com.adgn.backend.testsupport.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

class PackagedEntrypointMySqlCheck {
    private ProcessBuilder command(String... args){
        var process=JavaSubprocess.command(args);
        process.environment().keySet().removeIf(k->k.startsWith("RBAC_")||k.startsWith("SPRING_")||k.equals("JAVA_TOOL_OPTIONS")||k.equals("JDK_JAVA_OPTIONS"));
        return process.redirectErrorStream(true);
    }

    @Test void migrationJarExitsAfterIdempotentMigrateWithoutStartingWebOrCreatingAdministrator() throws Exception {
        try(var db=new IsolatedMySqlDatabase()){
            var command=command("-Dloader.main=com.adgn.backend.migration.MigrationApplication","-cp",
                    "target/backend-0.0.1-SNAPSHOT.jar","org.springframework.boot.loader.launch.PropertiesLauncher");
            db.configureMigration(command);Path log=Files.createTempFile(Path.of(".tmp"),"migration-entry-",".log");
            var process=command.redirectOutput(log.toFile()).start();
            try{
                assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue();
                String output=Files.readString(log);assertThat(process.exitValue()).as(output).isZero();
                assertThat(output).doesNotContain(command.environment().get("RBAC_MIGRATION_PASSWORD"),"Tomcat started");
                assertThat(db.scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1")).isEqualTo("3");
                assertThat(db.scalar("SELECT COUNT(*) FROM admin_account")).isEqualTo("0");
            }finally{if(process.isAlive())process.destroyForcibly().waitFor();Files.deleteIfExists(log);}
        }
    }

    @Test void webJarServesRealHealthAndShutsDownWithoutImplicitAdministratorInitialization() throws Exception {
        try(var db=new IsolatedMySqlDatabase()){
            var command=command("-jar","target/backend-0.0.1-SNAPSHOT.jar","--spring.profiles.active=dev",
                    "--server.port=0","--rbac.workflow.scheduling-enabled=false");
            db.configureBootstrap(command);var env=command.environment();
            env.put("SPRING_DATASOURCE_URL",env.remove("RBAC_BOOTSTRAP_URL"));
            env.put("SPRING_DATASOURCE_USERNAME",env.remove("RBAC_BOOTSTRAP_USER"));
            env.put("SPRING_DATASOURCE_PASSWORD",env.remove("RBAC_BOOTSTRAP_PASSWORD"));
            env.remove("RBAC_BOOTSTRAP_CONFIRM_DATABASE");
            Path log=Files.createTempFile(Path.of(".tmp"),"web-entry-",".log");var process=command.redirectOutput(log.toFile()).start();
            try{
                var pattern=Pattern.compile("Tomcat started on port (\\d+)");int port=0;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
                while(System.nanoTime()<until&&process.isAlive()){
                    var matcher=pattern.matcher(Files.readString(log));if(matcher.find()){port=Integer.parseInt(matcher.group(1));break;}
                    Thread.sleep(50);
                }
                assertThat(port).as(Files.readString(log)).isPositive();
                var http=ContractHttpClient.create();var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health"))
                        .timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("\"status\":\"UP\"");
                assertThat(db.scalar("SELECT COUNT(*) FROM admin_account")).isEqualTo("0");
                assertThat(db.scalar("SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo("0");
                process.destroy();assertThat(process.waitFor(20,TimeUnit.SECONDS)).isTrue();
                assertThat(Files.readString(log)).doesNotContain(env.get("SPRING_DATASOURCE_PASSWORD"));
            }finally{if(process.isAlive())process.destroyForcibly().waitFor();Files.deleteIfExists(log);}
        }
    }
}
