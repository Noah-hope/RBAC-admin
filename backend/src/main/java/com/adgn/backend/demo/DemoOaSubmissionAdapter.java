package com.adgn.backend.demo;
import com.adgn.backend.change.*;
import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.oa.OaSubmissionPort;
import com.adgn.backend.identity.security.IdentityFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
/** 显式demo模拟受理；稳定回执可由持久固定申请重建，绝不自动批准。 */
public final class DemoOaSubmissionAdapter implements OaSubmissionPort {
    private final JdbcTemplate jdbc;private final ReadBoundary reads;private final FixedRequestStore fixed;private final JsonMapper json;
    public DemoOaSubmissionAdapter(DataSource ds,ReadBoundary reads,FixedRequestStore fixed,JsonMapper json){jdbc=new JdbcTemplate(ds);this.reads=reads;this.fixed=fixed;this.json=json;}
    @Override public Receipt send(Submission input)throws Exception{return reads.read(()->"DEMO",a->{
        var f=fixed.load(input.requestId());if(!f.digest().equals(input.contentDigest())||f.submittedVersion()!=input.submittedVersion()||jdbc.queryForObject("SELECT COUNT(*) FROM oa_outbox o JOIN service_identity s ON s.id=o.oa_instance_id AND s.kind='OA' AND s.enabled=1 WHERE o.request_id=? AND o.oa_instance_id=?",Long.class,f.id(),input.oaInstanceId())!=1)throw new IdentityFailure(409,"CALLBACK_CONFLICT");
        var expected=(tools.jackson.databind.node.ObjectNode)f.payload().deepCopy();expected.put("contentDigest",f.digest());if(!Arrays.equals(CanonicalPayloadCodec.canonical(json.writeValueAsString(expected)),CanonicalPayloadCodec.canonical(json.writeValueAsString(input.payload()))))throw new IdentityFailure(409,"CALLBACK_CONFLICT");
        return new Receipt(f.id(),f.submittedVersion(),f.digest(),"DEMO-"+f.id(),Instant.parse(f.payload().path("submittedAt").stringValue()));
    });}
}
