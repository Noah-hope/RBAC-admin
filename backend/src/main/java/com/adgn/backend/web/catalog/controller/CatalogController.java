package com.adgn.backend.web.catalog.controller;

import com.adgn.backend.catalog.service.CatalogService;
import com.adgn.backend.query.preview.PreviewService;
import com.adgn.backend.catalog.repository.CatalogRepository;
import com.adgn.backend.web.IdempotencyHttp;
import com.adgn.backend.web.management.ManagementHeaders;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin/systems/{systemId}")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service){this.service=service;}
    @PostMapping("/state-previews") public ResponseEntity<PreviewService.View> statePreview(@AuthenticationPrincipal Jwt token,@PathVariable String systemId,@RequestBody CatalogService.Edit input)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.systemPreview(token,systemId,input));
    }
    @PutMapping public ResponseEntity<Object> saveSystem(@AuthenticationPrincipal Jwt token,@PathVariable String systemId,@RequestHeader("Idempotency-Key")String key,@RequestBody CatalogService.Save input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.saveSystem(token,systemId,key,input,(String)request.getAttribute("rbac.requestId")),request);
    }
    @PostMapping("/catalog-previews") public ResponseEntity<PreviewService.View> preview(@AuthenticationPrincipal Jwt token,@PathVariable String systemId,@RequestBody JsonNode input)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.catalogPreview(token,systemId,input));
    }
    @PostMapping("/catalog-changes") public ResponseEntity<Object> save(@AuthenticationPrincipal Jwt token,@PathVariable String systemId,@RequestHeader("Idempotency-Key")String key,@RequestBody JsonNode input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.saveCatalog(token,systemId,key,input,(String)request.getAttribute("rbac.requestId")),request);
    }
    @GetMapping("/directory-nodes") public ResponseEntity<CatalogRepository.Page<CatalogRepository.Node>> nodes(@AuthenticationPrincipal Jwt token,@PathVariable String systemId,HttpServletRequest request,
            @RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,@RequestParam(required=false)String parentId,@RequestParam(required=false)String nodeType,@RequestParam(required=false)String enabled,@RequestParam(required=false)String rootOnly)throws Exception{
        var result=service.nodes(token,ManagementHeaders.context(request),systemId,page,pageSize,parentId,nodeType,enabled,rootOnly);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Catalog-Version",Long.toString(result.catalogVersion())).body(result.page());
    }
    @GetMapping("/permissions") public ResponseEntity<CatalogRepository.Page<CatalogRepository.Permission>> permissions(@AuthenticationPrincipal Jwt token,@PathVariable String systemId,HttpServletRequest request,
            @RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,@RequestParam(required=false)String functionId,@RequestParam(required=false)String permissionType,@RequestParam(required=false)String enabled,@RequestParam(required=false)String q)throws Exception{
        var result=service.permissions(token,ManagementHeaders.context(request),systemId,page,pageSize,functionId,permissionType,enabled,q);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Catalog-Version",Long.toString(result.catalogVersion())).body(result.page());
    }
}
