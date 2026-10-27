package com.adgn.backend;

import com.adgn.backend.testsupport.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import java.nio.file.*;
import java.sql.SQLFeatureNotSupportedException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** 在独立进程中注入迁移库状态，真实数据库验证、应用命令和凭据边界保持执行。 */
class MigrationStateMySqlCheck {
    @ParameterizedTest @ValueSource(strings={"pending","no-current"})
    void bootstrapRefusesInconsistentMigrationInfoBeforeAnyAdministrativeWrite(String state)throws Exception{
        try(var db=new IsolatedMySqlDatabase()){
            var command=command(db,"bootstrap",state);var result=run(command);
            assertThat(result.exit()).as(result.output()).isEqualTo(4);
            assertThat(result.output()).contains("MIGRATION_REQUIRED","CREDENTIAL_OVERRIDE_REFUSED").doesNotContain("private-override-password");
            assertThat(db.scalar("SELECT COUNT(*) FROM admin_account")).isEqualTo("0");
            assertThat(db.scalar("SELECT COUNT(*) FROM audit_event")).isEqualTo("0");
        }
    }

    @Test void demoDatasourceRefusesCallerCredentialsWhileExplicitConfiguredCredentialsStillWork()throws Exception{
        try(var db=new IsolatedMySqlDatabase()){
            var command=command(db,"demo","valid");var result=run(command);
            assertThat(result.exit()).as(result.output()).isZero();
            assertThat(result.output()).contains("CREDENTIAL_OVERRIDE_REFUSED","DEMO_COMMAND_SUCCEEDED").doesNotContain("private-override-password");
            assertThat(db.scalar("SELECT COUNT(*) FROM service_identity")).isEqualTo("1");
            assertThat(db.scalar("SELECT COUNT(*) FROM audit_event WHERE event_type='DEMO_SERVICE_CONFIGURED'")).isEqualTo("1");
        }
    }

    private ProcessBuilder command(IsolatedMySqlDatabase db,String entry,String state)throws Exception{
        String mockito=Path.of(Mockito.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var command=JavaSubprocess.command("-javaagent:"+mockito,"-cp",System.getProperty("java.class.path"),Probe.class.getName(),entry,state);
        var env=command.environment();env.keySet().removeIf(k->k.startsWith("RBAC_")||k.startsWith("SPRING_")||k.equals("JAVA_TOOL_OPTIONS")||k.equals("JDK_JAVA_OPTIONS"));
        db.configureBootstrap(command);
        if(entry.equals("bootstrap")){
            env.put("RBAC_BOOTSTRAP_ADMIN_LOGIN","migration.fixture");env.put("RBAC_BOOTSTRAP_ADMIN_PASSWORD","Migration-Fixture-123456");env.put("RBAC_BOOTSTRAP_ADMIN_CONFIRM_PASSWORD","Migration-Fixture-123456");
        }else{
            for(String suffix:List.of("URL","USER","PASSWORD","CONFIRM_DATABASE"))env.put("RBAC_DEMO_"+suffix,env.remove("RBAC_BOOTSTRAP_"+suffix));
            env.put("RBAC_DEMO_MODE","demo");env.put("RBAC_DEMO_SERVICE_ID",UUID.randomUUID().toString());env.put("RBAC_DEMO_SUBJECT","migration-fixture-"+UUID.randomUUID());env.put("RBAC_DEMO_KIND","OA");
        }
        return command.redirectErrorStream(true);
    }
    private record Result(int exit,String output){}
    private Result run(ProcessBuilder command)throws Exception{
        Path log=Files.createTempFile(Path.of(".tmp"),"migration-state-",".log");var process=command.redirectOutput(log.toFile()).start();
        try{assertThat(process.waitFor(35,TimeUnit.SECONDS)).isTrue();return new Result(process.exitValue(),Files.readString(log));}
        finally{if(process.isAlive())process.destroyForcibly().waitFor();Files.deleteIfExists(log);}
    }
    public static final class Probe {
        public static void main(String[] args)throws Exception{
            FluentConfiguration configuration=Mockito.spy(Flyway.configure());
            Mockito.doAnswer(call->{
                // 应用实际交给迁移库的数据源：调用者不能以另一组凭据覆盖显式配置。
                try{configuration.getDataSource().getConnection("unexpected-user","private-override-password");throw new AssertionError("credential override must be refused");}
                catch(SQLFeatureNotSupportedException refused){System.out.println("CREDENTIAL_OVERRIDE_REFUSED");}
                Flyway real=(Flyway)call.callRealMethod();
                if(args[1].equals("valid"))return real;
                var result=Mockito.spy(real);var info=Mockito.mock(MigrationInfoService.class);
                Mockito.when(info.pending()).thenReturn(args[1].equals("pending")?new MigrationInfo[]{Mockito.mock(MigrationInfo.class)}:new MigrationInfo[0]);
                Mockito.when(info.current()).thenReturn(null);Mockito.doReturn(info).when(result).info();
                return result;
            }).when(configuration).load();
            try(var factory=Mockito.mockStatic(Flyway.class,Mockito.CALLS_REAL_METHODS)){
                factory.when(Flyway::configure).thenReturn(configuration);
                if(args[0].equals("bootstrap"))com.adgn.backend.bootstrap.BootstrapAdminApplication.main(new String[0]);
                else com.adgn.backend.demo.DemoServiceApplication.main(new String[]{"configure-demo-service"});
            }
        }
    }
}
