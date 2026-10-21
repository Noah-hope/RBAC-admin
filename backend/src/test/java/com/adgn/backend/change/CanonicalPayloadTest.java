package com.adgn.backend.change;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
class CanonicalPayloadTest {
    byte[] encode(String input)throws Exception{
        try{return (byte[])Class.forName("com.adgn.backend.change.CanonicalPayloadCodec").getMethod("canonical",String.class).invoke(null,input);}
        catch(ClassNotFoundException missing){throw new AssertionError("固定申请必须提供真实JCS编码器",missing);}
    }
    @Test void canonicalizesNumbersEscapesAndUtf16PropertyOrder()throws Exception{
        assertThat(new String(encode("{\"z\":[-0.0,1e30,4.50,0.002,1e-27],\"a\":\"\\u20ac\\u000f\\n\"}"),StandardCharsets.UTF_8)).isEqualTo("{\"a\":\"€\\u000f\\n\",\"z\":[0,1e+30,4.5,0.002,1e-27]}");
        assertThat(new String(encode("{\"דּ\":1,\"😀\":2,\"€\":3,\"1\":4,\"\\r\":5}"),StandardCharsets.UTF_8)).isEqualTo("{\"\\r\":5,\"1\":4,\"€\":3,\"😀\":2,\"דּ\":1}");
    }
    @Test void rejectsDuplicateKeysLoneSurrogatesAndNonFiniteNumbers(){
        for(String invalid:new String[]{"{\"a\":1,\"a\":2}","{\"a\":\"\\ud800\"}","{\"a\":1e999}"})assertThatThrownBy(()->encode(invalid)).isNotInstanceOf(AssertionError.class);
    }
}
