package com.rbac.rbac1ddd.interfaces.rest.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import java.io.IOException;

/** 登录账号兼容数字形式的学号，转换仅用于 LoginDTO 的 username 字段。 */
public final class LoginUsernameDeserializer extends StdScalarDeserializer<String> {

  /** 使用字符串承接请求中的账号值。 */
  public LoginUsernameDeserializer() {
    super(String.class);
  }

  /** 接受字符串或 JSON 整数，之后由 DTO 校验账号长度。 */
  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.hasToken(JsonToken.VALUE_STRING) || parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
      return parser.getText();
    }
    return (String) context.handleUnexpectedToken(String.class, parser);
  }
}
