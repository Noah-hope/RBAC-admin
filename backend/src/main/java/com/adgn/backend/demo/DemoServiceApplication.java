package com.adgn.backend.demo;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.net.URI;
import java.sql.*;
import java.time.*;
import java.util.*;

/** 显式短进程初始化/签发工具；不注册为Web组件，不从普通应用启动。 */
public final class DemoServiceApplication {
    private DemoServiceApplication(){}
    public static void main(String[] args){
        if(org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext log)log.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
        int exit=0;try{if(args.length!=1||!Set.of("configure-demo-service","issue-demo-service-token").contains(args[0]))throw new Refused();run(args[0],System.getenv());System.out.println("DEMO_COMMAND_SUCCEEDED");}
        catch(Exception refused){System.err.println("DEMO_COMMAND_REFUSED: verify explicit demo settings, current service, schema and destination.");exit=2;}if(exit!=0)System.exit(exit);
    }
    private static void run(String command,Map<String,String> env)throws Exception{
        var settings=new Settings(env);var ds=new DemoDataSource(settings);var jdbc=new JdbcTemplate(ds);
        if(!settings.database.equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))throw new Refused();
        var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).load();flyway.validate();if(flyway.info().current()==null||flyway.info().pending().length!=0)throw new Refused();
        var writes=new CommandBoundary(new JdbcTransactionManager(ds),ds,Clock.systemUTC());var audit=new AuditAppender(ds,JsonMapper.builder().build());
        // 签发所需密码运算与文件读取在guard外；当前服务资格仍在guard内复核。
        RSAKey signer=command.equals("issue-demo-service-token")?signer(env):null;Path output=signer==null?null:Path.of(required(env,"OUTPUT")).toAbsolutePath();
        if(output!=null&&(Files.exists(output,LinkOption.NOFOLLOW_LINKS)||!Files.isDirectory(output.getParent())))throw new Refused();
        String raw=writes.execute(()->new CommandBoundary.Identity<>("demo-command",new AuditRecord.Actor(AuditRecord.ActorKind.DEPLOYMENT,"demo-service-command")),c->{
            if(command.equals("configure-demo-service")){
                if(!jdbc.queryForList("SELECT 1 FROM service_identity WHERE id=? OR subject=?",settings.id,settings.subject).isEmpty())throw new Refused();
                if(settings.kind.equals("BUSINESS")&&jdbc.queryForObject("SELECT COUNT(*) FROM business_system WHERE id=?",Long.class,settings.system)!=1)throw new Refused();
                jdbc.update("INSERT INTO service_identity(id,subject,kind,system_id,enabled) VALUES(?,?,?,?,1)",settings.id,settings.subject,settings.kind,settings.system);
            }else if(jdbc.queryForObject("SELECT COUNT(*) FROM service_identity WHERE id=? AND subject=? AND kind=? AND enabled=1 AND (system_id<=>?)",Long.class,settings.id,settings.subject,settings.kind,settings.system)!=1)throw new Refused();
            audit.append(c,new AuditRecord(command.equals("configure-demo-service")?"DEMO_SERVICE_CONFIGURED":"DEMO_SERVICE_TOKEN_ISSUED",settings.id,null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",c.newId().toString(),"Explicit demo service command","{\"schemaVersion\":1}",List.of(new AuditRecord.Subject("SERVICE_IDENTITY",settings.id))));
            if(signer==null)return null;var now=c.now();var claims=new JWTClaimsSet.Builder().issuer(env.getOrDefault("RBAC_DEMO_ISSUER",settings.kind.equals("OA")?"urn:rbac:oa":"urn:rbac:business")).audience(settings.kind.equals("OA")?"rbac-oa-callback":"rbac-business").subject(settings.subject).jwtID(c.newId().toString()).issueTime(java.util.Date.from(now)).expirationTime(java.util.Date.from(now.plusSeconds(300))).claim(settings.kind.equals("OA")?"oaInstanceId":"systemId",settings.kind.equals("OA")?settings.id:settings.system).build();
            var token=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signer.getKeyID()).type(JOSEObjectType.JWT).build(),claims);token.sign(new RSASSASigner(signer));return token.serialize();
        });
        if(raw!=null){var attrs=Files.getFileStore(output.getParent()).supportsFileAttributeView("posix")?new java.nio.file.attribute.FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}:new java.nio.file.attribute.FileAttribute<?>[0];
            try(var file=Files.newByteChannel(output,Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),attrs)){file.write(java.nio.ByteBuffer.wrap((raw+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        }
    }
    private static RSAKey signer(Map<String,String> env)throws Exception{
        Path privateFile=Path.of(required(env,"SIGNING_JWK")),verifyFile=Path.of(required(env,"VERIFY_JWK_SET"));if(Files.size(privateFile)>131072||Files.size(verifyFile)>131072)throw new Refused();
        String privateJson=Files.readString(privateFile);var document=JsonMapper.builder().build().readTree(privateJson);
        var candidates=document.has("keys")?JWKSet.parse(privateJson).getKeys():List.of(RSAKey.parse(privateJson));String selected=env.get("RBAC_DEMO_SIGNING_KEY_ID");if(selected!=null&&!selected.isBlank())candidates=candidates.stream().filter(k->selected.equals(k.getKeyID())).toList();
        if(candidates.size()!=1||!(candidates.getFirst() instanceof RSAKey))throw new Refused();var key=(RSAKey)candidates.getFirst();if(key.getKeyUse()!=null&&!KeyUse.SIGNATURE.equals(key.getKeyUse())||!key.isPrivate()||key.getKeyID()==null||!key.getKeyID().matches("[A-Za-z0-9_.-]{1,64}")||key.toRSAPublicKey().getModulus().bitLength()<2048||key.getAlgorithm()!=null&&!key.getAlgorithm().equals(JWSAlgorithm.RS256))throw new Refused();
        JWK trusted=JWKSet.parse(Files.readString(verifyFile)).getKeyByKeyId(key.getKeyID());if(!(trusted instanceof RSAKey rsa)||rsa.isPrivate()||!key.toPublicJWK().computeThumbprint().equals(rsa.computeThumbprint()))throw new Refused();return key;
    }
    private static String required(Map<String,String> env,String key){String value=env.get("RBAC_DEMO_"+key);if(value==null||value.isBlank())throw new Refused();return value;}
    private static final class Settings {
        final String url,user,password,database,id,subject,kind,system;
        Settings(Map<String,String> env){
            if(!required(env,"MODE").equals("demo"))throw new Refused();url=required(env,"URL");user=required(env,"USER");password=required(env,"PASSWORD");database=required(env,"CONFIRM_DATABASE");id=required(env,"SERVICE_ID");subject=required(env,"SUBJECT");kind=required(env,"KIND");system=kind.equals("BUSINESS")?required(env,"SYSTEM_ID"):null;
            if(!UUID.fromString(id).toString().equals(id)||subject.length()>100||!Set.of("OA","BUSINESS").contains(kind)||system!=null&&!UUID.fromString(system).toString().equals(system)||!database.matches("[A-Za-z0-9_]{1,64}")||Set.of("mysql","sys","information_schema","performance_schema").contains(database.toLowerCase(Locale.ROOT))||!url.startsWith("jdbc:mysql://"))throw new Refused();
            var uri=URI.create(url.substring(5));if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||!uri.getRawPath().equals("/"+database)||uri.getPort()==0||uri.getPort()>65535||uri.getRawQuery()!=null&&!uri.getRawQuery().matches("sslMode=(DISABLED|PREFERRED|REQUIRED|VERIFY_CA|VERIFY_IDENTITY)"))throw new Refused();
        }
    }
    private static final class DemoDataSource extends AbstractDataSource {
        private final Settings settings;DemoDataSource(Settings s){settings=s;}
        @Override public Connection getConnection()throws SQLException{var p=new Properties();p.setProperty("user",settings.user);p.setProperty("password",settings.password);p.setProperty("characterEncoding","UTF-8");p.setProperty("connectionTimeZone","UTC");p.setProperty("forceConnectionTimeZoneToSession","true");p.setProperty("connectTimeout","5000");p.setProperty("socketTimeout","15000");var c=DriverManager.getConnection(settings.url,p);try(var s=c.createStatement()){s.execute("SET time_zone='+00:00', innodb_lock_wait_timeout=5");return c;}catch(SQLException e){c.close();throw e;}}
        @Override public Connection getConnection(String user,String password)throws SQLException{throw new SQLFeatureNotSupportedException();}
    }
    private static final class Refused extends RuntimeException{}
}
