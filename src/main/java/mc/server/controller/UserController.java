package mc.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mc.server.config.InitialSetupRunner;
import mc.server.dto.ApiResponse;
import mc.server.dto.PasswordUpdateRequest;
import mc.server.model.User;
import mc.server.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/username")
    public ResponseEntity<ApiResponse<String>> updateUsername(@AuthenticationPrincipal UserDetails userDetails,
                                                              @RequestParam String newUsername) {
        if (userService.findByUsername(newUsername).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username is already taken"));
        }

        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));

        userService.updateUsername(user, newUsername);
        return ResponseEntity.ok(ApiResponse.success("Username updated successfully. Please log in again."));
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<String>> updatePassword(@AuthenticationPrincipal UserDetails userDetails,
                                                              @Valid @RequestBody PasswordUpdateRequest request) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));

        if (!userService.checkPassword(user, request.getOldPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Incorrect old password"));
        }
        userService.updatePassword(user, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully"));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<String>> deleteAccount(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
        userService.deleteUser(userDetails.getUsername());

        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.logout(request, null, SecurityContextHolder.getContext().getAuthentication());

        InitialSetupRunner.setSetupRequired(true);
        return ResponseEntity.ok(
                ApiResponse.<String>builder()
                        .success(true)
                        .message("Account deleted")
                        .data("/setup")
                        .build()
        );
    }

    @DeleteMapping("/delete-all")
    public ResponseEntity<ApiResponse<String>> deleteAllUsers() {
        userService.deleteAllUsers();
        InitialSetupRunner.setSetupRequired(true);
        return ResponseEntity.ok(
                ApiResponse.<String>builder()
                        .success(true)
                        .message("All accounts deleted")
                        .data("/setup")
                        .build()
        );
    }
}
