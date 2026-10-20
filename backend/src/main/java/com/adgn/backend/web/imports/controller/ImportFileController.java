package com.adgn.backend.web.imports.controller;

import com.adgn.backend.imports.ImportFileService;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.web.IdempotencyHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/import-files")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class ImportFileController {
    private final ImportFileService service;
    public ImportFileController(ImportFileService service){this.service=service;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> upload(@AuthenticationPrincipal Jwt token,@RequestHeader("Idempotency-Key")String key,
            @RequestPart("file")MultipartFile file,HttpServletRequest request)throws Exception{
        var parts=request.getParts();if(parts.size()!=1 || !parts.iterator().next().getName().equals("file"))throw IdentityFailure.invalid();
        return IdempotencyHttp.response(service.upload(token,key,file.getOriginalFilename(),file.getContentType(),file::getInputStream,(String)request.getAttribute("rbac.requestId")),request);
    }
    @GetMapping("/{fileId}")
    public ResponseEntity<ImportFileService.FileView> get(@AuthenticationPrincipal Jwt token,@PathVariable String fileId)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(fileId,token));
    }
}
