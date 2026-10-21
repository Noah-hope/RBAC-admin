package com.adgn.backend;

import com.adgn.backend.testsupport.OpenApiResponses;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class OpenApiResponsesTest {
    private final JsonMapper json=JsonMapper.builder().build();
    private final OpenApiResponses validator=new OpenApiResponses(json.readTree("{\"paths\":{}}"));
    @Test void rejectsMissingUnexpectedAndWrongTypedFieldsWhileAllowingExplicitNull(){
        var schema=json.readTree("{\"type\":\"object\",\"required\":[\"id\"],\"properties\":{\"id\":{\"type\":\"integer\",\"minimum\":1},\"name\":{\"type\":[\"string\",\"null\"]}},\"additionalProperties\":false}");
        validator.schema(schema,json.readTree("{\"id\":1,\"name\":null}"),"$");
        for(String bad:new String[]{"{}","{\"id\":\"1\"}","{\"id\":0}","{\"id\":1,\"secret\":true}"})
            assertThatThrownBy(()->validator.schema(schema,json.readTree(bad),"$")).isInstanceOf(AssertionError.class);
    }
    @Test void enforcesUnionEnumArrayUniquenessAndDateTime(){
        var schema=json.readTree("{\"oneOf\":[{\"type\":\"null\"},{\"type\":\"array\",\"minItems\":1,\"uniqueItems\":true,\"items\":{\"type\":\"string\",\"enum\":[\"A\",\"B\"]}}]}");
        validator.schema(schema,json.readTree("[\"A\",\"B\"]"),"$");validator.schema(schema,json.nullNode(),"$");
        for(String bad:new String[]{"[]","[\"C\"]","[\"A\",\"A\"]","true"})assertThatThrownBy(()->validator.schema(schema,json.readTree(bad),"$")).isInstanceOf(AssertionError.class);
        assertThatThrownBy(()->validator.schema(json.readTree("{\"type\":\"string\",\"format\":\"date-time\"}"),json.valueToTree("yesterday"),"$")).isInstanceOf(AssertionError.class);
    }
    @Test void enforcesSiblingConstraintsAlongsideUnionBranches(){
        var schema=json.readTree("{\"type\":\"object\",\"oneOf\":[{\"required\":[\"ok\"]},{\"required\":[\"error\"]}],\"properties\":{\"ok\":{\"type\":\"boolean\"},\"error\":{\"type\":\"string\"}},\"additionalProperties\":false}");
        validator.schema(schema,json.readTree("{\"ok\":true}"),"$");
        assertThatThrownBy(()->validator.schema(schema,json.readTree("{\"ok\":true,\"secret\":true}"),"$")).isInstanceOf(AssertionError.class);
    }
    @Test void rejectsUndeclaredStatusMediaBodyAndMissingSensitiveCacheHeader(){
        var op=json.readTree("{\"responses\":{\"204\":{},\"200\":{\"headers\":{\"Cache-Control\":{}},\"content\":{\"application/json\":{\"schema\":{\"type\":\"boolean\"}}}}}}");
        validator.response(op,204,"","","");validator.response(op,200,"application/json;charset=utf-8","no-store","true");
        assertThatThrownBy(()->validator.response(op,201,"","","")).isInstanceOf(AssertionError.class);
        assertThatThrownBy(()->validator.response(op,204,"","","{}")).isInstanceOf(AssertionError.class);
        assertThatThrownBy(()->validator.response(op,200,"text/html","no-store","true")).isInstanceOf(AssertionError.class);
        assertThatThrownBy(()->validator.response(op,200,"application/json","","true")).isInstanceOf(AssertionError.class);
    }
}
