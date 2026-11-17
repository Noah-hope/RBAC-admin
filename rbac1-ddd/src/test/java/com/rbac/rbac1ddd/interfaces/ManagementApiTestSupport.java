package com.rbac.rbac1ddd.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.rbac.rbac1ddd.infrastructure.security.TokenHash;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 管理接口集成测试的公共基类：H2 内存库 + 与结构化版本相同的初始权限数据。
 *
 * <p>令牌由测试用私钥直接签发并写入会话，省去登录步骤；应用配置同一对密钥，因此能校验这些令牌。
 * 子类共用同一个 Spring 上下文和数据库，测试数据用 {@link #uniq} 生成不重复的名称。
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ddd;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "spring.jpa.defer-datasource-initialization=true",
      "spring.sql.init.mode=always",
      "spring.sql.init.data-locations=classpath:test-bootstrap.sql",
      "rbac.bootstrap.username=admin",
      "rbac.bootstrap.password=demo1234"
    })
@AutoConfigureMockMvc
abstract class ManagementApiTestSupport {

  /** 测试令牌的签发者，与应用配置一致。 */
  private static final String ISSUER = "http://test-issuer";

  /** 全局递增序号，生成不重复的名称和编码。 */
  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  /** 测试用 RSA 密钥对，应用用其公钥校验令牌。 */
  private static KeyPair keys;

  @Autowired
  protected MockMvc mvc;

  @Autowired
  protected ObjectMapper json;

  @Autowired
  protected JdbcTemplate jdbc;

  /** 生成测试用 RSA 密钥对，私钥和公钥分别写入临时 PEM 文件，配置为应用签发和校验 JWT 的密钥。 */
  @DynamicPropertySource
  static void signingKey(DynamicPropertyRegistry registry) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keys = generator.generateKeyPair();
    Path privateFile = Files.createTempFile("rbac-ddd-private", ".pem");
    Path publicFile = Files.createTempFile("rbac-ddd-public", ".pem");
    Files.writeString(
        privateFile,
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {10})
                .encodeToString(keys.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----");
    Files.writeString(
        publicFile,
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(keys.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----");
    registry.add("rbac.jwt.private-key", () -> privateFile.toUri().toString());
    registry.add("rbac.jwt.public-key", () -> publicFile.toUri().toString());
    registry.add("rbac.jwt.issuer", () -> ISSUER);
  }

  /** 带上 Bearer 令牌执行请求。 */
  protected ResultActions call(MockHttpServletRequestBuilder request, String token)
      throws Exception {
    return mvc.perform(request.header("Authorization", "Bearer " + token));
  }

  /** 把对象序列化为 JSON 请求体。 */
  protected String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }

  /** 插入部门，parent 为 null 时是顶级部门。 */
  protected long department(String name, Long parent) {
    Map<String, Object> values = new HashMap<>(Map.of("name", name));
    if (parent != null) {
      values.put("parent_id", parent);
    }
    return insert("sys_department", values);
  }

  /** 插入启用状态的用户并直接关联给定角色，departmentId 为 null 时表示没有所属部门。 */
  protected long user(String name, Long departmentId, long... roleIds) {
    Map<String, Object> values =
        new HashMap<>(
            Map.of(
                "username", uniq("u"), "name", name, "password_hash", "hash", "status", "ACTIVE"));
    if (departmentId != null) {
      values.put("department_id", departmentId);
    }
    long id = insert("sys_user", values);
    for (long roleId : roleIds) {
      jdbc.update("insert into sys_user_role(user_id, role_id) values (?, ?)", id, roleId);
    }
    return id;
  }

  /** 模拟登录：签发面向权限中心的令牌并写入有效会话，返回令牌。 */
  protected String login(long userId) {
    String token = token(userId, List.of("permission-center"));
    session(token, userId);
    return token;
  }

  /** 写入一小时后过期的会话，库中只保存令牌的 SHA-256 摘要。 */
  protected void session(String token, long userId) {
    jdbc.update(
        "insert into sys_session(token_hash, user_id, expires_at) values (?, ?, ?)",
        TokenHash.sha256(token),
        userId,
        LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
  }

  /** 用测试私钥签发 RS256 JWT，audience 决定令牌可以调用哪个应用的接口。 */
  protected static String token(long userId, List<String> audience) {
    RSAKey key =
        new RSAKey.Builder((RSAPublicKey) keys.getPublic())
            .privateKey((RSAPrivateKey) keys.getPrivate())
            .keyID("test")
            .build();
    NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(Long.toString(userId))
            .audience(audience)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .id(UUID.randomUUID().toString())
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /** 向指定表插入一行并返回自增主键。 */
  protected long insert(String table, Map<String, ?> values) {
    return new SimpleJdbcInsert(jdbc)
        .withTableName(table)
        .usingGeneratedKeyColumns("id")
        .executeAndReturnKey(new HashMap<>(values))
        .longValue();
  }

  /** 查询单个 ID。 */
  protected long id(String sql) {
    return jdbc.queryForObject(sql, Long.class);
  }

  /** 执行 count 查询。 */
  protected long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  /** 在前缀后追加全局递增序号，避免测试之间名称或编码冲突。 */
  protected static String uniq(String prefix) {
    return prefix + SEQUENCE.incrementAndGet();
  }
}
