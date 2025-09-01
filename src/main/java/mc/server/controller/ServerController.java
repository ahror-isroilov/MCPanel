package mc.server.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.dto.ApiResponse;
import mc.server.dto.CommandRequest;
import mc.server.dto.WhitelistEntry;
import mc.server.model.ConsoleMessage;
import mc.server.model.ServerStatus;
import mc.server.service.*;
import mc.server.service.server.MinecraftServerService;
import mc.server.service.server.ServerPropertiesService;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/servers/{instanceId}")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "*")
public class ServerController {
    private final ServerPropertiesService serverPropertiesService;
    private final MinecraftServerService minecraftServerService;
    private final WebSocketService webSocketService;
    private final LogMonitoringService logMonitoringService;
    private final RconService rconService;
    private final ApplicationContext applicationContext;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ServerStatus>> getServerStatus(@PathVariable Long instanceId) {
        try {
            ServerStatus status = minecraftServerService.getServerStatus(instanceId);
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (Exception e) {
            log.error("Error getting server status for instance {}", instanceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get server status"));
        }
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDetailedServerInfo(@PathVariable Long instanceId) {
        try {
            Map<String, Object> info = minecraftServerService.getDetailedServerInfo(instanceId);
            info.put("rconConfigured", rconService.isConfigured(instanceId));
            info.put("rconConnectionInfo", rconService.getConnectionInfo(instanceId));

            return ResponseEntity.ok(ApiResponse.success(info));
        } catch (Exception e) {
            log.error("Error getting detailed server info for instance {}", instanceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get detailed server information"));
        }
    }

    @GetMapping("/rcon/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testRconConnection(@PathVariable Long instanceId) {
        try {
            boolean connected = rconService.testConnection(instanceId);
            Map<String, Object> result = Map.of(
                    "connected", connected,
                    "configured", rconService.isConfigured(instanceId),
                    "connectionInfo", rconService.getConnectionInfo(instanceId)
            );

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Error testing RCON connection for instance {}", instanceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to test RCON connection"));
        }
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> refreshServerInfo(@PathVariable Long instanceId) {
        try {
            minecraftServerService.refreshServerInfo(instanceId);
            return ResponseEntity.ok(ApiResponse.success("Server information refreshed successfully"));
        } catch (Exception e) {
            log.error("Error refreshing server info for instance {}", instanceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to refresh server information"));
        }
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> startServer(@PathVariable Long instanceId) {
        log.info("Received request to start Minecraft server for instance {}", instanceId);

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.info("[ADMIN] Starting server...")
        );

        try {
            var instance = minecraftServerService.getInstance(instanceId);
            var template = applicationContext.getBean(mc.server.service.TemplateService.class)
                .getTemplateById(instance.getTemplateId());

            Path javaExecutable = applicationContext.getBean(mc.server.service.CrossPlatformJavaService.class)
                .ensureJavaAvailable(instanceId, template.systemRequirements());

            return minecraftServerService.startServer(instanceId, javaExecutable, instance.getAllocatedMemory())
                .thenApply(success -> {
                    if (success) {
                        String message = "Server start command executed successfully";
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.info("[SYSTEM] " + message)
                        );
                        return ResponseEntity.ok(ApiResponse.success(message, "started"));
                    } else {
                        String error = "Failed to start server";
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.error("[ERROR] " + error)
                        );
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error(error));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Exception starting server for instance {}", instanceId, throwable);
                    return ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Exception occurred while starting server: " + throwable.getMessage()));
                });
        } catch (Exception e) {
            log.error("Pre-start validation failed for instance {}", instanceId, e);
            return CompletableFuture.completedFuture(
                ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to initiate server start: " + e.getMessage()))
            );
        }
    }

    @PostMapping("/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> stopServer(@PathVariable Long instanceId) {
        log.info("Received request to stop Minecraft server for instance {}", instanceId);

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.info("[ADMIN] Stopping server...")
        );

        return minecraftServerService.stopServer(instanceId)
                .thenApply(success -> {
                    if (success) {
                        String message = "Server stop command executed successfully";
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.info("[SYSTEM] " + message)
                        );
                        return ResponseEntity.ok(ApiResponse.success(message, "stopped"));
                    } else {
                        String error = "Failed to stop server";
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.info("[ERROR] " + error)
                        );
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error(error));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Exception stopping server for instance {}", instanceId, throwable);
                    return ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Exception occurred while stopping server"));
                });
    }

    @PostMapping("/restart")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> restartServer(@PathVariable Long instanceId) {
        log.info("Received request to restart Minecraft server for instance {}", instanceId);

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.info("[ADMIN] Restarting server...")
        );

        return minecraftServerService.restartServer(instanceId)
                .thenApply(success -> {
                    if (success) {
                        String message = "Server restart command executed successfully";
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.info("[SYSTEM] " + message)
                        );
                        return ResponseEntity.ok(ApiResponse.success(message, "restarted"));
                    } else {
                        String error = "Failed to restart server";
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.info("[ERROR] " + error)
                        );
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error(error));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Exception restarting server for instance {}", instanceId, throwable);
                    return ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Exception occurred while restarting server"));
                });
    }

    @PostMapping("/command")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> sendCommand(@PathVariable Long instanceId,
                                                                                   @Valid @RequestBody CommandRequest request) {

        String command = request.getCommand().trim();
        log.info("Received command for instance {}: {}", instanceId, command);

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.command("> " + command)
        );

        return minecraftServerService.sendCommand(instanceId, command)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(
                                ApiResponse.success("Command sent successfully", command)
                        );
                    } else {
                        String error = "Failed to send command: " + command;
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.info("[ERROR] " + error)
                        );
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error(error));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Exception sending command for instance {}: {}", instanceId, command, throwable);
                    return ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Exception occurred while sending command"));
                });
    }

    @PostMapping("/players/{playerName}/kick")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> kickPlayer(@PathVariable Long instanceId,
                                                                                  @PathVariable String playerName,
                                                                                  @RequestParam(required = false) String reason) {

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.info(String.format("[ADMIN] Kicking player: %s", playerName))
        );

        return minecraftServerService.kickPlayer(instanceId, playerName, reason)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Player kicked successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to kick player"));
                    }
                });
    }

    @PostMapping("/players/{playerName}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> banPlayer(@PathVariable Long instanceId,
                                                                                 @PathVariable String playerName,
                                                                                 @RequestParam(required = false) String reason) {

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.info(String.format("[ADMIN] Banning player: %s", playerName))
        );

        return minecraftServerService.banPlayer(instanceId, playerName, reason)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Player banned successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to ban player"));
                    }
                });
    }

    @PostMapping("/players/{playerName}/pardon")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> pardonPlayer(@PathVariable Long instanceId,
                                                                                    @PathVariable String playerName) {
        return minecraftServerService.pardonPlayer(instanceId, playerName)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Player pardoned successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to pardon player"));
                    }
                });
    }

    @PostMapping("/players/{playerName}/op")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> opPlayer(@PathVariable Long instanceId,
                                                                                @PathVariable String playerName) {

        return minecraftServerService.opPlayer(instanceId, playerName)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Player opped successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to op player"));
                    }
                });
    }

    @PostMapping("/players/{playerName}/deop")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> deopPlayer(@PathVariable Long instanceId,
                                                                                  @PathVariable String playerName) {

        return minecraftServerService.deopPlayer(instanceId, playerName)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Player deopped successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to deop player"));
                    }
                });
    }

    @PostMapping("/players/{playerName}/teleport")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> teleportPlayer(@PathVariable Long instanceId,
                                                                                      @PathVariable String playerName,
                                                                                      @RequestParam double x,
                                                                                      @RequestParam double y,
                                                                                      @RequestParam double z) {

        return minecraftServerService.teleportPlayer(instanceId, playerName, x, y, z)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Player teleported successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to teleport player"));
                    }
                });
    }

    @PostMapping("/players/{playerName}/give")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> giveItem(@PathVariable Long instanceId,
                                                                                @PathVariable String playerName,
                                                                                @RequestParam String item,
                                                                                @RequestParam @Min(1) @Max(64) int count) {

        return minecraftServerService.giveItem(instanceId, playerName, item, count)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Item given successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to give item"));
                    }
                });
    }

    @GetMapping("/players")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPlayerList(@PathVariable Long instanceId) {
        try {
            List<Map<String, Object>> players = minecraftServerService.getPlayerList(instanceId);
            return ResponseEntity.ok(ApiResponse.success(players));
        } catch (Exception e) {
            log.error("Error getting extended player list for instance {}", instanceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get player list"));
        }
    }

    @GetMapping("/players/{playerName}")
    public CompletableFuture<ResponseEntity<ApiResponse<Map<String, Object>>>> getPlayerInfo(@PathVariable Long instanceId,
                                                                                             @PathVariable String playerName) {

        return minecraftServerService.getPlayerGameMode(instanceId, playerName)
                .thenCombine(minecraftServerService.getPlayerHealth(instanceId, playerName), (gameMode, health) -> {
                    Map<String, Object> playerInfo = Map.of(
                            "name", playerName,
                            "gameMode", gameMode,
                            "health", health,
                            "online", minecraftServerService.getOnlinePlayersSet(instanceId).contains(playerName)
                    );
                    return ResponseEntity.ok(ApiResponse.success(playerInfo));
                })
                .exceptionally(throwable -> {
                    log.error("Error getting player info for {} on instance {}", playerName, instanceId, throwable);
                    return ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Failed to get player information"));
                });
    }

    @PostMapping("/players/{playerName}/gamemode")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> setPlayerGameMode(@PathVariable Long instanceId,
                                                                                         @PathVariable String playerName,
                                                                                         @RequestParam String gameMode) {

        return minecraftServerService.setPlayerGameMode(instanceId, playerName, gameMode)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Game mode set successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to set game mode"));
                    }
                });
    }

    @PostMapping("/players/{playerName}/heal")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> healPlayer(@PathVariable Long instanceId,
                                                                                  @PathVariable String playerName) {

        return minecraftServerService.healPlayer(instanceId, playerName)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Player healed successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to heal player"));
                    }
                });
    }

    @PostMapping("/players/{playerName}/feed")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> feedPlayer(@PathVariable Long instanceId,
                                                                                  @PathVariable String playerName) {

        return minecraftServerService.feedPlayer(instanceId, playerName)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Player fed successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to feed player"));
                    }
                });
    }

    @PostMapping("/players/{playerName}/message")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> sendMessageToPlayer(@PathVariable Long instanceId,
                                                                                           @PathVariable String playerName,
                                                                                           @RequestParam String message) {

        return minecraftServerService.sendPrivateMessage(instanceId, playerName, message)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Message sent successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to send message"));
                    }
                });
    }

    @PostMapping("/world/time")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> setTime(@PathVariable Long instanceId,
                                                                               @RequestParam String time) {

        return minecraftServerService.setTime(instanceId, time)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Time set successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to set time"));
                    }
                });
    }

    @PostMapping("/world/weather")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> setWeather(@PathVariable Long instanceId,
                                                                                  @RequestParam String weather,
                                                                                  @RequestParam(required = false) Integer duration) {

        return minecraftServerService.setWeather(instanceId, weather, duration)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Weather set successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to set weather"));
                    }
                });
    }

    @PostMapping("/world/gamerule")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> setGameRule(@PathVariable Long instanceId,
                                                                                   @RequestParam String rule,
                                                                                   @RequestParam String value) {

        return minecraftServerService.setGameRule(instanceId, rule, value)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Game rule set successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to set game rule"));
                    }
                });
    }

    @PostMapping("/world/difficulty")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> setDifficulty(@PathVariable Long instanceId,
                                                                                     @RequestParam String difficulty) {

        return minecraftServerService.setDifficulty(instanceId, difficulty)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Difficulty set successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to set difficulty"));
                    }
                });
    }

    @PostMapping("/save-all")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> saveAll(@PathVariable Long instanceId) {
        return minecraftServerService.saveAll(instanceId)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("World saved successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to save world"));
                    }
                });
    }

    @PostMapping("/reload")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> reloadServer(@PathVariable Long instanceId) {
        return minecraftServerService.reloadServer(instanceId)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Server reloaded successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to reload server"));
                    }
                });
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> broadcastMessage(@PathVariable Long instanceId,
                                                                                        @RequestParam @NotBlank String message) {

        return minecraftServerService.broadcastMessage(instanceId, message)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.success("Message broadcasted successfully"));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to broadcast message"));
                    }
                });
    }

    @PostMapping("/backup")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> createBackup(@PathVariable Long instanceId) {
        log.info("Received request to create world backup for instance {}", instanceId);

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.info("[ADMIN] Creating world backup...")
        );

        return minecraftServerService.backupWorld(instanceId)
                .thenApply(success -> {
                    if (success) {
                        String message = "World backup created successfully";
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.info("[SYSTEM] " + message)
                        );
                        return ResponseEntity.ok(ApiResponse.success(message));
                    } else {
                        String error = "Failed to create world backup";
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.info("[ERROR] " + error)
                        );
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error(error));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Exception creating backup for instance {}", instanceId, throwable);
                    return ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Exception occurred while creating backup"));
                });
    }

    @GetMapping("/console/history")
    public ResponseEntity<ApiResponse<java.util.List<ConsoleMessage>>> getConsoleHistory(@PathVariable Long instanceId) {
        try {
            java.util.List<ConsoleMessage> history = new ArrayList<>(logMonitoringService.getConsoleHistory(instanceId));
            return ResponseEntity.ok(ApiResponse.success(history));
        } catch (Exception e) {
            log.error("Error getting console history for instance {}", instanceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get console history"));
        }
    }

    @DeleteMapping("/console/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> clearConsoleHistory(@PathVariable Long instanceId) {
        try {
            logMonitoringService.clearHistory(instanceId);
            webSocketService.broadcastConsoleMessage(instanceId,
                    ConsoleMessage.info("[ADMIN] Console history cleared")
            );
            return ResponseEntity.ok(ApiResponse.success("Console history cleared"));
        } catch (Exception e) {
            log.error("Error clearing console history for instance {}", instanceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to clear console history"));
        }
    }

    @GetMapping("/properties")
    public ResponseEntity<ApiResponse<Map<String, String>>> getServerProperties(@PathVariable Long instanceId) {
        try {
            Map<String, String> properties = serverPropertiesService.getServerProperties(instanceId);
            return ResponseEntity.ok(ApiResponse.success(properties));
        } catch (Exception e) {
            log.error("Error getting server properties for instance {}", instanceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get server properties"));
        }
    }

    @PostMapping("/properties/{propertyKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> updateServerProperty(@PathVariable Long instanceId,
                                                                                            @PathVariable String propertyKey,
                                                                                            @RequestParam String value) {

        log.info("Updating server property for instance {}: {}={}", instanceId, propertyKey, value);

        Map<String, ServerPropertiesService.PropertyValidation> validations =
                serverPropertiesService.getPropertyValidations();

        if (validations.containsKey(propertyKey)) {
            ServerPropertiesService.PropertyValidation validation = validations.get(propertyKey);
            String validationError = validateProperty(propertyKey, value, validation);
            if (validationError != null) {
                return CompletableFuture.completedFuture(
                        ResponseEntity.badRequest().body(ApiResponse.error(validationError))
                );
            }
        }

        boolean requiresRestart = serverPropertiesService.requiresRestart(propertyKey);

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.info(String.format("[ADMIN] Updating property %s=%s", propertyKey, value))
        );

        return serverPropertiesService.updateProperty(instanceId, propertyKey, value)
                .thenCompose(success -> {
                    if (success) {
                        if (minecraftServerService.isServerRunning(instanceId)) {
                            return minecraftServerService.reloadServer(instanceId).thenApply(reloadSuccess -> {
                                String message = "Property updated successfully";
                                if (requiresRestart) {
                                    message += " (server restart required)";
                                }

                                webSocketService.broadcastConsoleMessage(instanceId,
                                        ConsoleMessage.info("[SYSTEM] " + message)
                                );

                                Map<String, Object> response = Map.of(
                                        "message", message,
                                        "requiresRestart", requiresRestart
                                );

                                return ResponseEntity.ok(ApiResponse.success(response));
                            });
                        } else {
                            String message = "Property updated successfully";
                            if (requiresRestart) {
                                message += " (server restart required)";
                            }
                            Map<String, Object> response = Map.of(
                                    "message", message,
                                    "requiresRestart", requiresRestart
                            );
                            return CompletableFuture.completedFuture(ResponseEntity.ok(ApiResponse.success(response)));
                        }
                    } else {
                        return CompletableFuture.completedFuture(ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to update property")));
                    }
                });
    }

    @PostMapping("/properties")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> updateServerProperties(@PathVariable Long instanceId,
                                                                                              @RequestBody Map<String, String> properties) {

        log.info("Updating {} server properties for instance {}", properties.size(), instanceId);

        Map<String, ServerPropertiesService.PropertyValidation> validations =
                serverPropertiesService.getPropertyValidations();

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (validations.containsKey(entry.getKey())) {
                ServerPropertiesService.PropertyValidation validation = validations.get(entry.getKey());
                String validationError = validateProperty(entry.getKey(), entry.getValue(), validation);
                if (validationError != null) {
                    return CompletableFuture.completedFuture(
                            ResponseEntity.badRequest().body(ApiResponse.error(validationError))
                    );
                }
            }
        }

        boolean requiresRestart = properties.keySet().stream()
                .anyMatch(serverPropertiesService::requiresRestart);

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.info(String.format("[ADMIN] Updating %d server properties", properties.size()))
        );

        return serverPropertiesService.updateProperties(instanceId, properties)
                .thenCompose(success -> {
                    if (success) {
                        if (minecraftServerService.isServerRunning(instanceId)) {
                            return minecraftServerService.reloadServer(instanceId).thenApply(reloadSuccess -> {
                                String message = "Properties updated successfully";
                                if (requiresRestart) {
                                    message += " (server restart required)";
                                }

                                webSocketService.broadcastConsoleMessage(instanceId,
                                        ConsoleMessage.info("[SYSTEM] " + message)
                                );

                                Map<String, Object> response = Map.of(
                                        "message", message,
                                        "requiresRestart", requiresRestart,
                                        "updatedCount", properties.size()
                                );

                                return ResponseEntity.ok(ApiResponse.success(response));
                            });
                        } else {
                            String message = "Properties updated successfully";
                            if (requiresRestart) {
                                message += " (server restart required)";
                            }
                            Map<String, Object> response = Map.of(
                                    "message", message,
                                    "requiresRestart", requiresRestart,
                                    "updatedCount", properties.size()
                            );
                            return CompletableFuture.completedFuture(ResponseEntity.ok(ApiResponse.success(response)));
                        }
                    } else {
                        return CompletableFuture.completedFuture(ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to update properties")));
                    }
                });
    }

    @GetMapping("/properties/validations")
    public ResponseEntity<ApiResponse<Map<String, ServerPropertiesService.PropertyValidation>>> getPropertyValidations(@PathVariable Long instanceId) {
        try {
            Map<String, ServerPropertiesService.PropertyValidation> validations =
                    serverPropertiesService.getPropertyValidations();
            return ResponseEntity.ok(ApiResponse.success(validations));
        } catch (Exception e) {
            log.error("Error getting property validations for instance {}", instanceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get property validations"));
        }
    }

    @GetMapping("/whitelist")
    public ResponseEntity<ApiResponse<List<WhitelistEntry>>> getWhitelist(@PathVariable Long instanceId) {
        try {
            List<WhitelistEntry> whitelist = serverPropertiesService.getWhitelist(instanceId);
            return ResponseEntity.ok(ApiResponse.success(whitelist));
        } catch (Exception e) {
            log.error("Error getting whitelist for instance {}", instanceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get whitelist"));
        }
    }

    @PostMapping("/whitelist")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> addToWhitelist(@PathVariable Long instanceId,
                                                                                      @RequestParam @NotEmpty String playerName) {

        log.info("Adding player to whitelist for instance {}: {}", instanceId, playerName);

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.info(String.format("[ADMIN] Adding %s to whitelist", playerName))
        );

        return serverPropertiesService.addToWhitelist(instanceId, playerName)
                .thenApply(success -> {
                    if (success) {
                        String message = String.format("Player %s added to whitelist", playerName);
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.info("[SYSTEM] " + message)
                        );
                        return ResponseEntity.ok(ApiResponse.success(message));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to add player to whitelist"));
                    }
                });
    }

    @DeleteMapping("/whitelist/{playerName}")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<? extends ApiResponse<?>>> removeFromWhitelist(@PathVariable Long instanceId,
                                                                                           @PathVariable String playerName) {

        log.info("Removing player from whitelist for instance {}: {}", instanceId, playerName);

        webSocketService.broadcastConsoleMessage(instanceId,
                ConsoleMessage.info(String.format("[ADMIN] Removing %s from whitelist", playerName))
        );

        return serverPropertiesService.removeFromWhitelist(instanceId, playerName)
                .thenApply(success -> {
                    if (success) {
                        String message = String.format("Player %s removed from whitelist", playerName);
                        webSocketService.broadcastConsoleMessage(instanceId,
                                ConsoleMessage.info("[SYSTEM] " + message)
                        );
                        return ResponseEntity.ok(ApiResponse.success(message));
                    } else {
                        return ResponseEntity.internalServerError()
                                .body(ApiResponse.error("Failed to remove player from whitelist"));
                    }
                });
    }

    private String validateProperty(String key, String value, ServerPropertiesService.PropertyValidation validation) {
        switch (validation.type) {
            case "integer":
                try {
                    int intValue = Integer.parseInt(value);
                    if (validation.min != null && intValue < (Integer) validation.min) {
                        return String.format("%s must be at least %d", key, validation.min);
                    }
                    if (validation.max != null && intValue > (Integer) validation.max) {
                        return String.format("%s must be at most %d", key, validation.max);
                    }
                } catch (NumberFormatException e) {
                    return String.format("%s must be a valid integer", key);
                }
                break;
            case "boolean":
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    return String.format("%s must be 'true' or 'false'", key);
                }
                break;
            case "enum":
                @SuppressWarnings("unchecked")
                List<String> allowedValues = (List<String>) validation.min;
                if (!allowedValues.contains(value.toLowerCase())) {
                    return String.format("%s must be one of: %s", key, String.join(", ", allowedValues));
                }
                break;
            case "string":
                if (value.length() > 255) {
                    return String.format("%s is too long (max 255 characters)", key);
                }
                break;
        }
        return null;
    }
}
