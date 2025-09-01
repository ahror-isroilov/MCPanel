package mc.server.controller;

import lombok.RequiredArgsConstructor;
import mc.server.model.ServerInstance;
import mc.server.service.MinecraftServerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerInstanceController {

    private final MinecraftServerService minecraftServerService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServerInstance> createServerInstance(
            @RequestParam String name,
            @RequestParam String version) {
        return ResponseEntity.ok(minecraftServerService.createServerInstance(name, version));
    }

    @DeleteMapping("/{instanceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteServerInstance(@PathVariable Long instanceId) {
        try {
            minecraftServerService.deleteServerInstance(instanceId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Server deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", "Failed to delete server files: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
}
