package com.routeplan.user.api;

import com.routeplan.user.application.UserService;
import com.routeplan.user.application.UserService.UserResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResult> create(@Valid @RequestBody CreateUserRequest request) {
        UserResult result = userService.create(request.nickname());
        return ResponseEntity.created(URI.create("/api/v1/users/" + result.id())).body(result);
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 50) String nickname
    ) {
    }
}
