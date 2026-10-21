package com.adgn.backend.oa;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
/** 适配器在调用者事务之外执行；requestId是跨重试稳定业务键。 */
public interface OaSubmissionPort {
    record Submission(String oaInstanceId,String requestId,long submittedVersion,String contentDigest,JsonNode payload){}
    record Receipt(String requestId,long submittedVersion,String contentDigest,String oaApprovalId,Instant acceptedAt){}
    Receipt send(Submission submission)throws Exception;
}
