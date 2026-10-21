package com.adgn.backend.web.oa.controller;
import com.adgn.backend.oa.OaResultService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class OaResultController {
    private final OaResultService service;
    public OaResultController(OaResultService service){this.service=service;}
    @PostMapping("/api/v1/oa/final-results") public ResponseEntity<Object> receive(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode input)throws Exception{var result=service.receive(jwt,input);return ResponseEntity.status(result.duplicate()?200:202).header("Cache-Control","no-store").body(result.receipt());}
}
