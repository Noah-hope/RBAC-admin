package com.adgn.backend.bootstrap;

import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.audit.AuditRecord;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.identity.security.PasswordPolicy;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.sql.*;
import java.time.Clock;
import java.util.*;

/** Explicit deployment command. Never registered with Spring or invoked during web startup. */
public final class BootstrapAdminApplication {
    private BootstrapAdminApplication() { }

    public static void main(String[] args) {
        // This short-lived process reports only safe categories, never driver SQL/configuration or causes.
        if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext log) {
            log.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
        }
        int exit=0;
        try {
            if(args.length!=0)throw new Refused("INVALID_CONFIGURATION",2);
            initialize(new Settings(System.getenv()));
            System.out.println("BOOTSTRAP_CREATED: initial administrator must change password on first login.");
        } catch(Refused failure) {
            System.err.println("BOOTSTRAP_REFUSED: "+failure.category);
            exit=failure.exit;
        } catch(Exception failure) {
            System.err.println("BOOTSTRAP_REFUSED: DATABASE_OR_SCHEMA_FAILURE; verify database state before retry.");
            exit=4;
        }
        if(exit!=0)System.exit(exit);
    }

    private static void initialize(Settings settings) throws Exception {
        var source=new BootstrapDataSource(settings);
        var jdbc=new JdbcTemplate(source);
        if(!settings.database.equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))
            throw new Refused("TARGET_DATABASE_MISMATCH",2);
        // Validation is read-only; migration remains a separate deployment operation.
        var flyway=Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).load();
        flyway.validate();
        if(flyway.info().pending().length!=0 || flyway.info().current()==null)
            throw new Refused("MIGRATION_REQUIRED",4);
        var hash=new PasswordPolicy().encode(settings.adminPassword);
        var boundary=new CommandBoundary(new JdbcTransactionManager(source),source,Clock.systemUTC());
        var audit=new AuditAppender(source,JsonMapper.builder().build());
        boundary.execute(()->{
            if(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE is_super_admin=1",Long.class)>0)
                throw new Refused("ADMIN_ALREADY_EXISTS",3);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE login_name=?",Long.class,settings.login)>0)
                throw new Refused("LOGIN_ALREADY_EXISTS",3);
            return new CommandBoundary.Identity<>("bootstrap-admin",
                    new AuditRecord.Actor(AuditRecord.ActorKind.DEPLOYMENT,"bootstrap-admin"));
        }, context->{
            context.requireActive(source);
            var id=context.newId().toString();
            jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,enabled,is_super_admin,"
                            +"must_change_password,version,credential_version,session_epoch,row_version,created_at) "
                            +"VALUES(?,NULL,?,?,1,1,1,1,1,1,0,?)", statement->{
                statement.setString(1,id);statement.setString(2,settings.login);statement.setString(3,hash);
                statement.setTimestamp(4,Timestamp.from(context.now()),Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            });
            audit.append(context,new AuditRecord("ADMIN_BOOTSTRAPPED",id,null,null,
                    new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",
                    context.newId().toString(),"Explicit deployment initialization","{\"schemaVersion\":1}",
                    List.of(new AuditRecord.Subject("ACCOUNT",id))));
            return id;
        });
    }

    /** Deliberately not a record: generated toString must never expose secrets. */
    private static final class Settings {
        final String url,user,password,database,login,adminPassword;
        Settings(Map<String,String> env) {
            try {
                url=required(env,"URL");user=required(env,"USER");password=required(env,"PASSWORD");
                database=required(env,"CONFIRM_DATABASE");login=required(env,"ADMIN_LOGIN");
                adminPassword=required(env,"ADMIN_PASSWORD");
                if(!adminPassword.equals(required(env,"ADMIN_CONFIRM_PASSWORD")))throw new IllegalArgumentException();
                PasswordPolicy.length(adminPassword,12,128);
                if(!login.matches("[A-Za-z0-9][A-Za-z0-9_.@-]{0,99}")
                        || !database.matches("[A-Za-z0-9_]{1,64}")
                        || Set.of("mysql","sys","information_schema","performance_schema").contains(database.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException();
                if(!url.startsWith("jdbc:mysql://"))throw new IllegalArgumentException();
                var uri=URI.create(url.substring(5));
                if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null
                        || uri.getPort()==0 || uri.getPort()>65535 || !uri.getRawPath().equals("/"+database))
                    throw new IllegalArgumentException();
                // Do not accept URL credentials, alternate catalogs, multi-query or driver behavior overrides.
                if(uri.getRawQuery()!=null && !uri.getRawQuery().matches("sslMode=(DISABLED|PREFERRED|REQUIRED|VERIFY_CA|VERIFY_IDENTITY)"))
                    throw new IllegalArgumentException();
            } catch(Exception invalid) { throw new Refused("INVALID_CONFIGURATION",2); }
        }
        private static String required(Map<String,String> env,String suffix) {
            var value=env.get("RBAC_BOOTSTRAP_"+suffix);
            if(value==null || value.isBlank())throw new IllegalArgumentException();
            return value;
        }
    }

    private static final class BootstrapDataSource extends AbstractDataSource {
        private final Settings settings;
        BootstrapDataSource(Settings settings){this.settings=settings;}
        @Override public Connection getConnection() throws SQLException {
            var properties=new Properties();
            properties.setProperty("user",settings.user);properties.setProperty("password",settings.password);
            properties.setProperty("characterEncoding","UTF-8");properties.setProperty("connectionCollation","utf8mb4_0900_bin");
            properties.setProperty("connectionTimeZone","UTC");properties.setProperty("forceConnectionTimeZoneToSession","true");
            properties.setProperty("connectTimeout","5000");properties.setProperty("socketTimeout","15000");
            var connection=DriverManager.getConnection(settings.url,properties);
            try(var statement=connection.createStatement()) {
                statement.execute("SET time_zone='+00:00', innodb_lock_wait_timeout=5");
                return connection;
            } catch(SQLException failure) {connection.close();throw failure;}
        }
        @Override public Connection getConnection(String username,String password) throws SQLException {
            throw new SQLFeatureNotSupportedException("Explicit bootstrap credentials only");
        }
    }
    private static final class Refused extends RuntimeException {
        final String category;final int exit;
        Refused(String category,int exit){super(category);this.category=category;this.exit=exit;}
    }
}
