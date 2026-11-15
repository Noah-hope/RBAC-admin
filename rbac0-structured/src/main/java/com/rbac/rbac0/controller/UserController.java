package com.rbac.rbac0.controller;

import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.dto.UserDtos.UserCreate;
import com.rbac.rbac0.dto.UserDtos.UserCreated;
import com.rbac.rbac0.dto.UserDtos.UserPage;
import com.rbac.rbac0.dto.UserDtos.UserUpdate;
import com.rbac.rbac0.dto.UserDtos.UserView;
import com.rbac.rbac0.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.rbac.rbac0.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac0.dto.DepartmentDtos.MAX_ID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public UserPage list(@RequestAttribute(ACTOR) SessionUser actor,
                         @RequestParam(defaultValue = "1") @Positive int page,
                         @RequestParam(defaultValue = "20") @Positive @Max(100) int pageSize,
                         @RequestParam(defaultValue = "") @CodePointLength(max = 100) String keyword,
                         @RequestParam(defaultValue = "false") boolean includeDepartmentName,
                         @RequestParam(required = false) @Positive @Max(MAX_ID) Long departmentId) {
        return users.list(actor, departmentId, keyword, includeDepartmentName, page, pageSize);
    }

    @GetMapping("/{userId}")
    public UserView get(@RequestAttribute(ACTOR) SessionUser actor, @PathVariable @Positive @Max(MAX_ID) long userId) {
        return users.get(actor, userId);
    }

    @PostMapping
    public ResponseEntity<UserCreated> create(@RequestAttribute(ACTOR) SessionUser actor,
                                              @Valid @RequestBody UserCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(users.create(actor, request));
    }

    @PatchMapping("/{userId}")
    public UserView update(@RequestAttribute(ACTOR) SessionUser actor,
                           @PathVariable @Positive @Max(MAX_ID) long userId,
                           @Valid @RequestBody UserUpdate request) {
        return users.update(actor, userId, request);
    }
}
