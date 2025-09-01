package mc.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.model.ConsoleMessage;
import mc.server.model.ServerInstance;
import mc.server.model.ServerStatus;
import lombok.extern.slf4j.Slf4j;
import mc.server.repository.ServerInstanceRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTasksService {

    private final MinecraftServerService minecraftServerService;
    private final SystemMonitoringService systemMonitoringService;
    private final WebSocketService webSocketService;
    private final RconService rconService;
    private final ServerInstanceRepository serverInstanceRepository;

    private final Map<Long, ServerStatus> lastServerStatuses = new ConcurrentHashMap<>();
    private final Map<Long, AtomicBoolean> tpsMonitoringStates = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 15000)
    public void broadcastServerStatusUpdates() {
        for (ServerInstance instance : serverInstanceRepository.findAll()) {
            try {
                if (webSocketService.hasActiveSessions()) {
                    ServerStatus currentStatus = minecraftServerService.getServerStatus(instance.getId());
                    webSocketService.broadcastServerStatus(instance.getId(), currentStatus);
                    lastServerStatuses.put(instance.getId(), currentStatus);
                }
            } catch (Exception e) {
                log.error("Error broadcasting server status update for instance {}", instance.getId(), e);
            }
        }
    }

    @Scheduled(fixedRate = 5000)
    public void broadcastSystemStats() {
        try {
            if (webSocketService.hasActiveSessions()) {
                var systemStats = systemMonitoringService.getSystemStats();
                var jvmStats = systemMonitoringService.getJvmStats();

                systemStats.put("jvm", jvmStats);
                systemStats.put("activeWebSocketSessions", webSocketService.getActiveSessionCount());
                
                for (ServerInstance instance : serverInstanceRepository.findAll()) {
                    systemStats.put("rconConnected", rconService.testConnection(instance.getId()));
                    // webSocketService.broadcastSystemStats(instance.getId(), systemStats);
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting system stats update", e);
        }
    }

    @Scheduled(fixedRate = 30000)
    public void refreshPlayerLists() {
        for (ServerInstance instance : serverInstanceRepository.findAll()) {
            try {
                if (minecraftServerService.isServerRunning(instance.getId()) && rconService.isConfigured(instance.getId())) {
                    rconService.executeCommand(instance.getId(), "list")
                            .thenAccept(response -> {
                                if (response != null) {
                                    log.debug("Refreshed player list for instance {} via RCON", instance.getId());
                                }
                            })
                            .exceptionally(throwable -> {
                                log.debug("Failed to refresh player list for instance {} via RCON: {}", instance.getId(), throwable.getMessage());
                                return null;
                            });
                }
            } catch (Exception e) {
                log.debug("Error in scheduled player list refresh for instance {}", instance.getId(), e);
            }
        }
    }

    @Scheduled(fixedRate = 30000)
    public void monitorTPS() {
        for (ServerInstance instance : serverInstanceRepository.findAll()) {
            try {
                if (minecraftServerService.isServerRunning(instance.getId()) &&
                        rconService.isConfigured(instance.getId()) &&
                        !tpsMonitoringStates.getOrDefault(instance.getId(), new AtomicBoolean(false)).get()) {

                    tpsMonitoringStates.computeIfAbsent(instance.getId(), k -> new AtomicBoolean(false)).set(true);

                    rconService.executeCommand(instance.getId(), "debug start")
                            .thenCompose(startResponse -> {
                                if (startResponse != null && startResponse.contains("Started debug profiling")) {
                                    try {
                                        Thread.sleep(3000);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    return rconService.executeCommand(instance.getId(), "debug stop");
                                }
                                return java.util.concurrent.CompletableFuture.completedFuture(null);
                            })
                            .thenAccept(stopResponse -> {
                                if (stopResponse != null) {
                                    log.debug("TPS monitoring completed for instance {} via RCON", instance.getId());
                                }
                            })
                            .exceptionally(throwable -> {
                                log.debug("TPS monitoring failed for instance {}: {}", instance.getId(), throwable.getMessage());
                                return null;
                            })
                            .whenComplete((result, throwable) -> {
                                tpsMonitoringStates.get(instance.getId()).set(false);
                            });
                }
            } catch (Exception e) {
                log.debug("Error in TPS monitoring for instance {}", instance.getId(), e);
                if (tpsMonitoringStates.get(instance.getId()) != null) {
                    tpsMonitoringStates.get(instance.getId()).set(false);
                }
            }
        }
    }

    @Scheduled(fixedRate = 30000)
    public void monitorResourceUsage() {
        try {
            boolean highCpu = systemMonitoringService.isHighCpuUsage();
            boolean highMemory = systemMonitoringService.isHighMemoryUsage();
            boolean highDisk = systemMonitoringService.isHighDiskUsage();

            if (highCpu || highMemory || highDisk) {
                String alertMessage = buildResourceAlert(highCpu, highMemory, highDisk);
                log.warn(alertMessage);

                for (ServerInstance instance : serverInstanceRepository.findAll()) {
                    if (webSocketService.hasActiveSessions()) {
                        webSocketService.broadcastConsoleMessage(instance.getId(),
                                ConsoleMessage.builder()
                                        .type("warning")
                                        .message("[SYSTEM] " + alertMessage)
                                        .timestamp(LocalDateTime.now())
                                        .source("system")
                                        .build()
                        );
                    }

                    if (minecraftServerService.isServerRunning(instance.getId()) && rconService.isConfigured(instance.getId())) {
                        minecraftServerService.broadcastMessage(instance.getId(), "Server resources are running high. Please be patient.");
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error monitoring resource usage", e);
        }
    }

    @Scheduled(fixedRate = 120000)
    public void serverHealthCheck() {
        for (ServerInstance instance : serverInstanceRepository.findAll()) {
            try {
                boolean wasRunning = lastServerStatuses.getOrDefault(instance.getId(), new ServerStatus()).isOnline();
                boolean isRunning = minecraftServerService.isServerRunning(instance.getId());

                if (wasRunning && !isRunning) {
                    handleServerCrash(instance.getId());
                } else if (!wasRunning && isRunning) {
                    handleServerStart(instance.getId());
                }

                if (isRunning && rconService.isConfigured(instance.getId())) {
                    boolean rconConnected = rconService.testConnection(instance.getId());
                    if (!rconConnected) {
                        log.warn("RCON connection test failed for instance {} - server may not be responding", instance.getId());

                        if (webSocketService.hasActiveSessions()) {
                            webSocketService.broadcastConsoleMessage(instance.getId(),
                                    ConsoleMessage.builder()
                                            .type("warning")
                                            .message("[SYSTEM] RCON connection lost - some features may not work")
                                            .timestamp(LocalDateTime.now())
                                            .source("system")
                                            .build()
                            );
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Error during server health check for instance {}", instance.getId(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyMaintenance() {
        for (ServerInstance instance : serverInstanceRepository.findAll()) {
            try {
                log.info("Starting daily maintenance tasks for instance {}...", instance.getId());

                if (minecraftServerService.isServerRunning(instance.getId()) && rconService.isConfigured(instance.getId())) {
                    performServerMaintenance(instance.getId());
                }

                performSystemCleanup(instance);

                log.info("Daily maintenance tasks completed for instance {}", instance.getId());

            } catch (Exception e) {
                log.error("Error during daily maintenance for instance {}", instance.getId(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledBackup() {
        for (ServerInstance instance : serverInstanceRepository.findAll()) {
            try {
                if (minecraftServerService.isServerRunning(instance.getId())) {
                    log.info("Starting scheduled world backup for instance {}...", instance.getId());

                    if (rconService.isConfigured(instance.getId())) {
                        minecraftServerService.broadcastMessage(instance.getId(), "Starting scheduled world backup...");

                        minecraftServerService.saveAll(instance.getId())
                                .thenCompose(saved -> {
                                    if (saved) {
                                        return minecraftServerService.backupWorld(instance.getId());
                                    }
                                    return java.util.concurrent.CompletableFuture.completedFuture(false);
                                })
                                .thenAccept(success -> {
                                    String message = success ?
                                            "Scheduled world backup completed successfully" :
                                            "Scheduled world backup failed";

                                    log.info(message);

                                    if (webSocketService.hasActiveSessions()) {
                                        webSocketService.broadcastConsoleMessage(instance.getId(),
                                                ConsoleMessage.info("[SYSTEM] " + message)
                                        );
                                    }

                                    if (rconService.isConfigured(instance.getId())) {
                                        minecraftServerService.broadcastMessage(instance.getId(),
                                                success ? "Backup completed!" : "Backup failed - contact admin");
                                    }
                                });
                    }
                } else {
                    log.info("Skipping scheduled backup for instance {} - server is offline", instance.getId());
                }
            } catch (Exception e) {
                log.error("Error during scheduled backup for instance {}", instance.getId(), e);
            }
        }
    }

    private void handleServerCrash(Long instanceId) {
        log.warn("Minecraft server instance {} appears to have stopped unexpectedly!", instanceId);

        if (webSocketService.hasActiveSessions()) {
            webSocketService.broadcastConsoleMessage(instanceId,
                    ConsoleMessage.builder()
                            .type("error")
                            .message("[SYSTEM] Server appears to have stopped unexpectedly")
                            .timestamp(LocalDateTime.now())
                            .source("system")
                            .build()
            );
        }
    }

    private void handleServerStart(Long instanceId) {
        log.info("Minecraft server instance {} has started", instanceId);

        if (webSocketService.hasActiveSessions()) {
            webSocketService.broadcastConsoleMessage(instanceId,
                    ConsoleMessage.info("[SYSTEM] Server has started")
            );
        }

        minecraftServerService.refreshServerInfo(instanceId);
    }

    private String buildResourceAlert(boolean highCpu, boolean highMemory, boolean highDisk) {
        StringBuilder alert = new StringBuilder("High resource usage detected: ");

        if (highCpu) alert.append("CPU ");
        if (highMemory) alert.append("Memory ");
        if (highDisk) alert.append("Disk ");

        return alert.toString().trim();
    }

    private void performServerMaintenance(Long instanceId) {
        log.info("Performing server maintenance tasks for instance {}...", instanceId);

        minecraftServerService.saveAll(instanceId);

        rconService.executeCommand(instanceId, "kill @e[type=minecraft:item]")
                .thenAccept(response -> log.debug("Cleaned up dropped items for instance {}", instanceId));

        minecraftServerService.setWeather(instanceId, "clear", null);
        minecraftServerService.setTime(instanceId, "day");
    }

    private void performSystemCleanup(ServerInstance instance) {
        log.info("Performing system cleanup tasks for instance {}...", instance.getId());
        cleanOldBackups(instance);
    }

    private void cleanOldBackups(ServerInstance instance) {
        try {
            Path backupDir = Paths.get(instance.getInstancePath(), "backups");
            if (!Files.exists(backupDir)) {
                return;
            }

            int retentionDays = 7;
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            log.info("Cleaning backup files for instance {} older than {} days (before {})", instance.getId(), retentionDays, cutoffDate);

            int deletedCount = 0;
            long totalSize = 0;

            try (Stream<Path> backupFiles = Files.list(backupDir)) {
                List<Path> filesToDelete = backupFiles
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String fileName = path.getFileName().toString();
                            return fileName.startsWith("world_backup_") &&
                                    (fileName.endsWith(".tar.gz") || fileName.endsWith(".zip"));
                        })
                        .filter(path -> {
                            try {
                                return Files.getLastModifiedTime(path).toInstant().isBefore(cutoffDate.atZone(java.time.ZoneId.systemDefault()).toInstant());
                            } catch (IOException e) {
                                log.debug("Error getting file modification time for {}: {}", path, e.getMessage());
                                return false;
                            }
                        }).toList();

                for (Path fileToDelete : filesToDelete) {
                    try {
                        long fileSize = Files.size(fileToDelete);
                        Files.delete(fileToDelete);
                        deletedCount++;
                        totalSize += fileSize;
                        log.debug("Deleted old backup file for instance {}: {} (size: {} bytes)", instance.getId(), fileToDelete.getFileName(), fileSize);
                    } catch (IOException e) {
                        log.warn("Failed to delete backup file {} for instance {}: {}", fileToDelete, instance.getId(), e.getMessage());
                    }
                }
            }

            if (deletedCount > 0) {
                double sizeMB = totalSize / (1024.0 * 1024.0);
                log.info("Backup cleanup for instance {} completed: deleted {} old backup files, freed {} MB",
                        instance.getId(), deletedCount, String.format("%.2f", sizeMB));

                if (webSocketService.hasActiveSessions()) {
                    String message = String.format("[SYSTEM] Backup cleanup: deleted %d old files, freed %.2f MB",
                            deletedCount, sizeMB);
                    webSocketService.broadcastConsoleMessage(instance.getId(),
                            ConsoleMessage.builder()
                                    .type("info")
                                    .message(message)
                                    .timestamp(LocalDateTime.now())
                                    .source("system")
                                    .build()
                    );
                }
            } else {
                log.info("Backup cleanup for instance {} completed: no old backup files found to delete", instance.getId());
            }

        } catch (IOException e) {
            log.error("Error during backup cleanup for instance {}", instance.getId(), e);
            if (webSocketService.hasActiveSessions()) {
                webSocketService.broadcastConsoleMessage(instance.getId(),
                        ConsoleMessage.builder()
                                .type("error")
                                .message("[SYSTEM] Backup cleanup failed: " + e.getMessage())
                                .timestamp(LocalDateTime.now())
                                .source("system")
                                .build()
                );
            }
        }
    }
}
