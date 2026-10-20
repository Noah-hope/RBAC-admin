package com.adgn.backend.web.preview.controller;

import com.adgn.backend.query.preview.PreviewService;
import com.adgn.backend.web.management.ManagementHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin/previews")
public class PreviewController {
    private final PreviewService previews;
    public PreviewController(PreviewService previews){this.previews=previews;}
    @GetMapping("/{previewId}/employees") public ResponseEntity<PreviewService.Page> employees(@AuthenticationPrincipal Jwt token,@PathVariable String previewId,
            @RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,HttpServletRequest request)throws Exception{
        return response(previews.page(token,ManagementHeaders.context(request),previewId,true,page,pageSize));
    }
    @GetMapping("/{previewId}/targets") public ResponseEntity<PreviewService.Page> targets(@AuthenticationPrincipal Jwt token,@PathVariable String previewId,
            @RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,HttpServletRequest request)throws Exception{
        return response(previews.page(token,ManagementHeaders.context(request),previewId,false,page,pageSize));
    }
    private static ResponseEntity<PreviewService.Page> response(PreviewService.Page page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);}
}
