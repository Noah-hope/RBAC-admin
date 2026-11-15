package com.rbac.rbac0.controller;
import com.rbac.rbac0.dto.RequestDtos.*;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.service.PermissionRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.rbac.rbac0.controller.SessionInterceptor.ACTOR;
@RestController
@RequestMapping("/api/v1")
public class PermissionRequestController {
    private final PermissionRequestService service;
    public PermissionRequestController(PermissionRequestService service) {this.service=service;}
    @GetMapping("/me/requestable-permissions")
    public RequestablePermissions requestable(@RequestAttribute(ACTOR) SessionUser actor) {return service.requestable(actor);}
    @GetMapping("/permission-requests")
    public PageResult<PermissionRequestView> list(@RequestAttribute(ACTOR) SessionUser actor,
        @RequestParam(defaultValue="MINE") @Pattern(regexp="MINE|REVIEW") String view,
        @RequestParam(required=false) @Pattern(regexp="PENDING|APPROVED|REJECTED|SATISFIED|WITHDRAWN") String status,
        @RequestParam(defaultValue="1") @Positive int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return service.list(actor,view,status,page,pageSize);
    }
    @PostMapping("/permission-requests") @ResponseStatus(HttpStatus.CREATED)
    public PermissionRequestView submit(@RequestAttribute(ACTOR) SessionUser actor,@Valid @RequestBody PermissionRequestInput input) {return service.submit(actor,input);}
    @GetMapping("/permission-requests/{requestId}")
    public PermissionRequestView get(@RequestAttribute(ACTOR) SessionUser actor,@PathVariable @Positive long requestId) {return service.get(actor,requestId);}
    @PostMapping("/permission-requests/{requestId}/withdrawals")
    public PermissionRequestView withdraw(@RequestAttribute(ACTOR) SessionUser actor,@PathVariable @Positive long requestId) {return service.withdraw(actor,requestId);}
    @PostMapping("/permission-requests/{requestId}/reviews")
    public PermissionRequestView review(@RequestAttribute(ACTOR) SessionUser actor,@PathVariable @Positive long requestId,@Valid @RequestBody PermissionReview input) {return service.review(actor,requestId,input);}
}
