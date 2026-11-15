package com.rbac.rbac0.controller;
import com.rbac.rbac0.service.ApiService;
import com.rbac.rbac0.dto.ApiDtos.*;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import static com.rbac.rbac0.controller.SessionInterceptor.ACTOR;
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/apis")
public class ApiController {
    private final ApiService apis;
    public ApiController(ApiService apis) {this.apis=apis;}
    @GetMapping
    public ApiPage list(@RequestAttribute(ACTOR) SessionUser actor,@PathVariable @Positive long applicationId,
                        @RequestParam(defaultValue="") @Size(max=100) String keyword,
                        @RequestParam(defaultValue="1") @Positive int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return apis.list(actor,applicationId,keyword,page,pageSize);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiView create(@RequestAttribute(ACTOR) SessionUser actor,@PathVariable @Positive long applicationId,@Valid @RequestBody ApiInput input) {return apis.create(actor,applicationId,input);}
    @PatchMapping("/{apiId}")
    public ApiView update(@RequestAttribute(ACTOR) SessionUser actor,@PathVariable @Positive long applicationId,@PathVariable @Positive long apiId,@Valid @RequestBody ApiUpdate input) {return apis.update(actor,applicationId,apiId,input);}
    @DeleteMapping("/{apiId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(ACTOR) SessionUser actor,@PathVariable @Positive long applicationId,@PathVariable @Positive long apiId) {apis.delete(actor,applicationId,apiId);}
}
