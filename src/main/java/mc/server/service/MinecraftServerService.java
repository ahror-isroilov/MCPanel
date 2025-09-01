package mc.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.model.ServerInstance;
import mc.server.model.ServerStatus;
import mc.server.model.ConsoleMessage;
import mc.server.repository.ServerInstanceRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.Comparator;

import static mc.server.service.LogPatterns.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinecraftServerService {
    private final ServerInstanceRepository serverInstanceRepository;
    private final SystemMonitoringService systemMonitoringService;
    private final RconService rconService;
    private final ServerPropertiesService serverProperties;
    private final ServerDownloaderService serverDownloaderService;
    private final TemplateService templateService;
    private final ApplicationContext applicationContext;

    private final Map<Long, LocalDateTime> serverStartTimes = new ConcurrentHashMap<>();
    private final Map<Long, String> serverVersions = new ConcurrentHashMap<>();
    private final Map<Long, String> worldSeeds = new ConcurrentHashMap<>();
    private final Map<Long, Double> lastKnownTps = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> tpsDebugActive = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<Long, Integer> currentPlayerCounts = new ConcurrentHashMap<>();


    public ServerStatus getServerStatus(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        try {
            boolean isOnline = isServerRunning(instanceId);

            if (!isOnline) {
                return buildOfflineServerStatus(instance);
            }

            updatePlayerListFromRcon(instanceId);
            if (!tpsDebugActive.getOrDefault(instanceId, false)) {
                updateTpsFromRcon(instanceId);
            }
            updateServerInfoFromRcon(instanceId);

            var systemStats = systemMonitoringService.getSystemStats();
            String uptime = getServerUptime(instanceId);

            return ServerStatus.builder()
                    .instanceId(instance.getId())
                    .name(instance.getName())
                    .serverType(instance.getServerType())
                    .port(instance.getPort())
                    .online(true)
                    .playersOnline(currentPlayerCounts.getOrDefault(instanceId, 0))
                    .maxPlayers(serverProperties.getIntProperty(instanceId, "max-players", 20))
                    .cpuUsage((Double) systemStats.get("cpuUsage"))
                    .ramUsage((Double) systemStats.get("ramUsage"))
                    .totalRam((Double) systemStats.get("totalRam"))
                    .uptime(uptime)
                    .tps(lastKnownTps.getOrDefault(instanceId, 20.0))
                    .lastUpdated(LocalDateTime.now())
                    .onlinePlayers(new ArrayList<>(onlinePlayers.getOrDefault(instanceId, ConcurrentHashMap.newKeySet())))
                    .version(serverVersions.getOrDefault(instanceId, "Unknown"))
                    .worldName(getWorldName(instanceId))
                    .diskUsage((Double) systemStats.get("diskUsage"))
                    .totalDisk((Double) systemStats.get("totalDisk"))
                    .build();

        } catch (Exception e) {
            log.error("Error getting server status for instance {}", instanceId, e);
            return buildOfflineServerStatus(instance);
        }
    }

    private ServerStatus buildOfflineServerStatus(ServerInstance instance) {
        var systemStats = systemMonitoringService.getSystemStats();

        return ServerStatus.builder()
                .instanceId(instance.getId())
                .name(instance.getName())
                .serverType(instance.getServerType())
                .port(instance.getPort())
                .online(false)
                .playersOnline(0)
                .maxPlayers(serverProperties.getIntProperty(instance.getId(), "max-players", 20))
                .cpuUsage((Double) systemStats.get("cpuUsage"))
                .ramUsage((Double) systemStats.get("ramUsage"))
                .totalRam((Double) systemStats.get("totalRam"))
                .uptime("Offline")
                .tps(0.0)
                .lastUpdated(LocalDateTime.now())
                .onlinePlayers(new ArrayList<>())
                .version("Unknown")
                .worldName("Unknown")
                .diskUsage((Double) systemStats.get("diskUsage"))
                .totalDisk((Double) systemStats.get("totalDisk"))
                .build();
    }

    public List<ServerStatus> getAllServerStatuses() {
        List<ServerInstance> instances = serverInstanceRepository.findAll();
        List<ServerStatus> statuses = new ArrayList<>();
        for (ServerInstance instance : instances) {
            statuses.add(getServerStatus(instance.getId()));
        }
        return statuses;
    }

    private void updatePlayerListFromRcon(Long instanceId) {
        if (!rconService.isConfigured(instanceId)) {
            return;
        }

        String response = rconService.executeCommandSync(instanceId, "list");
        if (response != null) {
            parsePlayerListResponse(instanceId, response);
        }
    }

    private void parsePlayerListResponse(Long instanceId, String response) {
        Matcher matcher = LIST_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                int playerCount = Integer.parseInt(matcher.group(1));
                currentPlayerCounts.put(instanceId, playerCount);

                Set<String> players = ConcurrentHashMap.newKeySet();
                if (matcher.group(3) != null && !matcher.group(3).trim().isEmpty() && playerCount > 0) {
                    String[] playerNames = matcher.group(3).split(", ");
                    for (String player : playerNames) {
                        String cleanPlayer = player.trim();
                        if (!cleanPlayer.isEmpty()) {
                            players.add(cleanPlayer);
                        }
                    }
                }
                onlinePlayers.put(instanceId, players);

                log.debug("Updated player list for instance {}: {} players online", instanceId, playerCount);
            } catch (NumberFormatException e) {
                log.debug("Error parsing player count from response for instance {}: {}", instanceId, response);
            }
        }
    }

    private void updateTpsFromRcon(Long instanceId) {
        if (!rconService.isConfigured(instanceId)) {
            return;
        }

        rconService.executeCommand(instanceId, "tps")
                .thenAccept(response -> {
                    if (response == null) {
                        return;
                    }
                    Matcher matcher = TPS_PATTERN.matcher(response);
                    if (matcher.find()) {
                        try {
                            double tps = Double.parseDouble(matcher.group(1));
                            lastKnownTps.put(instanceId, Math.min(tps, 20.0));
                            log.debug("Updated TPS for instance {} to: {}", instanceId, lastKnownTps.get(instanceId));
                        } catch (NumberFormatException e) {
                            log.debug("Failed to parse TPS from response for instance {}: {}", instanceId, response);
                        }
                    }
                });
    }

    private void updateServerInfoFromRcon(Long instanceId) {
        if (!rconService.isConfigured(instanceId)) {
            return;
        }

        if ("Unknown".equals(serverVersions.getOrDefault(instanceId, "Unknown"))) {
            String versionResponse = rconService.executeCommandSync(instanceId, "version");
            if (versionResponse != null) {
                String strippedResponse = stripColorCodes(versionResponse);

                Matcher matcher = VERSION_PATTERN.matcher(strippedResponse);
                if (matcher.find()) {
                    String serverType = matcher.group(1);
                    String coreVersion = matcher.group(2);
                    serverVersions.put(instanceId, serverType + " " + coreVersion);
                    log.info("Discovered and parsed server version for instance {}: {}", instanceId, serverVersions.get(instanceId));
                }
            }
        }

        if ("Unknown".equals(worldSeeds.getOrDefault(instanceId, "Unknown"))) {
            String seedResponse = rconService.executeCommandSync(instanceId, "seed");
            if (seedResponse != null) {
                Matcher seedMatcher = SEED_PATTERN.matcher(seedResponse);
                if (seedMatcher.find()) {
                    worldSeeds.put(instanceId, seedMatcher.group(1));
                    log.debug("Updated world seed for instance {}: {}", instanceId, worldSeeds.get(instanceId));
                }
            }
        }
    }

    @Async
    public CompletableFuture<Boolean> sendCommand(Long instanceId, String command) {
        log.info("Sending command to Minecraft server instance {}: {}", instanceId, command);

        return CompletableFuture.supplyAsync(() -> {
            if (!isServerRunning(instanceId)) {
                log.warn("Cannot send command - server instance {} is not running", instanceId);
                return false;
            }

            if (rconService.isConfigured(instanceId)) {
                String response = rconService.executeCommandSync(instanceId, command);
                if (response != null) {
                    log.info("RCON command '{}' executed successfully on instance {}. Response: {}", command, instanceId, response);
                    return true;
                } else {
                    log.debug("RCON command failed for instance {}", instanceId);
                }
            }
            return false;
        });
    }

    @Async
    public CompletableFuture<Boolean> kickPlayer(Long instanceId, String playerName, String reason) {
        String command = reason != null && !reason.isEmpty() ?
                String.format("kick %s %s", playerName, reason) :
                String.format("kick %s", playerName);
        return sendCommand(instanceId, command);
    }

    @Async
    public CompletableFuture<Boolean> banPlayer(Long instanceId, String playerName, String reason) {
        String command = reason != null && !reason.isEmpty() ?
                String.format("ban %s %s", playerName, reason) :
                String.format("ban %s", playerName);
        return sendCommand(instanceId, command);
    }

    @Async
    public CompletableFuture<Boolean> pardonPlayer(Long instanceId, String playerName) {
        return sendCommand(instanceId, String.format("pardon %s", playerName));
    }

    @Async
    public CompletableFuture<Boolean> opPlayer(Long instanceId, String playerName) {
        return sendCommand(instanceId, String.format("op %s", playerName));
    }

    @Async
    public CompletableFuture<Boolean> deopPlayer(Long instanceId, String playerName) {
        return sendCommand(instanceId, String.format("deop %s", playerName));
    }

    @Async
    public CompletableFuture<Boolean> teleportPlayer(Long instanceId, String playerName, double x, double y, double z) {
        return sendCommand(instanceId, String.format("tp %s %.2f %.2f %.2f", playerName, x, y, z));
    }

    @Async
    public CompletableFuture<Boolean> giveItem(Long instanceId, String playerName, String item, int count) {
        return sendCommand(instanceId, String.format("give %s %s %d", playerName, item, count));
    }

    public List<Map<String, Object>> getPlayerList(Long instanceId) {
        if (!isServerRunning(instanceId) || !rconService.isConfigured(instanceId)) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> extendedPlayers = new ArrayList<>();

        for (String playerName : onlinePlayers.getOrDefault(instanceId, ConcurrentHashMap.newKeySet())) {
            Map<String, Object> playerInfo = new HashMap<>();
            playerInfo.put("name", playerName);
            playerInfo.put("status", "Online");
            playerInfo.put("joinTime", "Unknown");

            String posResponse = rconService.executeCommandSync(instanceId, "data get entity " + playerName + " Pos");
            if (posResponse != null && !posResponse.contains("No entity")) {
                playerInfo.put("position", parsePlayerPosition(posResponse));
            } else {
                playerInfo.put("position", "Unknown");
            }

            extendedPlayers.add(playerInfo);
        }

        return extendedPlayers;
    }

    private String parsePlayerPosition(String posResponse) {
        try {
            String[] parts = posResponse.split("\\[")[1].split("]")[0].split(",");
            if (parts.length == 3) {
                double x = Double.parseDouble(parts[0].trim().replace("d", ""));
                double y = Double.parseDouble(parts[1].trim().replace("d", ""));
                double z = Double.parseDouble(parts[2].trim().replace("d", ""));
                return String.format("%.1f, %.1f, %.1f", x, y, z);
            }
        } catch (Exception e) {
            log.debug("Error parsing player position: {}", e.getMessage());
        }
        return "Unknown";
    }

    @Async
    public CompletableFuture<String> getPlayerGameMode(Long instanceId, String playerName) {
        return rconService.executeCommand(instanceId, "data get entity " + playerName + " playerGameType")
                .thenApply(response -> {
                    if (response != null && response.contains("playerGameType")) {
                        if (response.contains("0")) return "Survival";
                        if (response.contains("1")) return "Creative";
                        if (response.contains("2")) return "Adventure";
                        if (response.contains("3")) return "Spectator";
                    }
                    return "Unknown";
                });
    }

    @Async
    public CompletableFuture<Boolean> setPlayerGameMode(Long instanceId, String playerName, String gameMode) {
        return sendCommand(instanceId, String.format("gamemode %s %s", gameMode.toLowerCase(), playerName));
    }

    @Async
    public CompletableFuture<String> getPlayerHealth(Long instanceId, String playerName) {
        return rconService.executeCommand(instanceId, "data get entity " + playerName + " Health")
                .thenApply(response -> {
                    if (response != null && response.contains("Health")) {
                        try {
                            String healthStr = response.split(":")[1].trim().replace("f", "");
                            float health = Float.parseFloat(healthStr);
                            return String.format("%.1f/20.0", health);
                        } catch (Exception e) {
                            log.debug("Error parsing health: {}", e.getMessage());
                        }
                    }
                    return "Unknown";
                });
    }

    @Async
    public CompletableFuture<Boolean> healPlayer(Long instanceId, String playerName) {
        return sendCommand(instanceId, String.format("effect give %s minecraft:instant_health 1 10", playerName));
    }

    @Async
    public CompletableFuture<Boolean> feedPlayer(Long instanceId, String playerName) {
        return sendCommand(instanceId, String.format("effect give %s minecraft:saturation 1 10", playerName));
    }

    @Async
    public CompletableFuture<Boolean> broadcastMessage(Long instanceId, String message) {
        return sendCommand(instanceId, String.format("say %s", message));
    }

    @Async
    public CompletableFuture<Boolean> sendPrivateMessage(Long instanceId, String playerName, String message) {
        return sendCommand(instanceId, String.format("tell %s %s", playerName, message));
    }

    @Async
    public CompletableFuture<Boolean> setTime(Long instanceId, String time) {
        return sendCommand(instanceId, String.format("time set %s", time));
    }

    @Async
    public CompletableFuture<Boolean> setWeather(Long instanceId, String weather, Integer duration) {
        String command = duration != null ?
                String.format("weather %s %d", weather, duration) :
                String.format("weather %s", weather);
        return sendCommand(instanceId, command);
    }

    @Async
    public CompletableFuture<Boolean> setGameRule(Long instanceId, String rule, String value) {
        return sendCommand(instanceId, String.format("gamerule %s %s", rule, value));
    }

    @Async
    public CompletableFuture<Boolean> setDifficulty(Long instanceId, String difficulty) {
        return sendCommand(instanceId, String.format("difficulty %s", difficulty));
    }

    @Async
    public CompletableFuture<Boolean> saveAll(Long instanceId) {
        return sendCommand(instanceId, "save-all");
    }

    @Async
    public CompletableFuture<Boolean> saveOn(Long instanceId) {
        return sendCommand(instanceId, "save-on");
    }

    @Async
    public CompletableFuture<Boolean> saveOff(Long instanceId) {
        return sendCommand(instanceId, "save-off");
    }

    @Async
    public CompletableFuture<Boolean> reloadServer(Long instanceId) {
        return sendCommand(instanceId, "reload");
    }

    @Async
    public CompletableFuture<Boolean> startServer(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        log.info("Starting Minecraft server instance {}...", instanceId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", instance.getJarFileName(), "nogui");
                processBuilder.directory(Paths.get(instance.getInstancePath()).toFile());
                Process process = processBuilder.start();
                instance.setPid((int) process.pid());
                serverInstanceRepository.save(instance);

                serverStartTimes.put(instanceId, LocalDateTime.now());
                onlinePlayers.put(instanceId, ConcurrentHashMap.newKeySet());
                currentPlayerCounts.put(instanceId, 0);
                log.info("Minecraft server instance {} start command completed successfully", instanceId);
                return true;
            } catch (Exception e) {
                log.error("Error starting Minecraft server instance {}", instanceId, e);
                return false;
            }
        });
    }

    @Async
    public CompletableFuture<Boolean> stopServer(Long instanceId) {
        log.info("Stopping Minecraft server instance {}...", instanceId);
        ServerInstance instance = getInstance(instanceId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (rconService.isConfigured(instanceId)) {
                    String response = rconService.executeCommandSync(instanceId, "stop");
                    if (response != null) {
                        log.info("Sent graceful stop command via RCON to instance {}", instanceId);
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                if (isServerRunning(instanceId)) {
                    log.warn("Server instance {} is still running, terminating process...", instanceId);
                    ProcessHandle.of(instance.getPid()).ifPresent(ProcessHandle::destroy);
                }

                instance.setPid(null);
                serverInstanceRepository.save(instance);
                onlinePlayers.remove(instanceId);
                currentPlayerCounts.remove(instanceId);
                serverStartTimes.remove(instanceId);
                log.info("Minecraft server instance {} stopped successfully", instanceId);
                return true;

            } catch (Exception e) {
                log.error("Error stopping Minecraft server instance {}", instanceId, e);
                return false;
            }
        });
    }

    @Async
    public CompletableFuture<Boolean> restartServer(Long instanceId) {
        log.info("Restarting Minecraft server instance {}...", instanceId);
        return stopServer(instanceId).thenCompose(stopped -> {
            if (stopped) {
                return startServer(instanceId);
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    

    public boolean isServerRunning(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        if (instance.getPid() == null) {
            return false;
        }
        return ProcessHandle.of(instance.getPid()).map(ProcessHandle::isAlive).orElse(false);
    }

    @Async
    public CompletableFuture<Boolean> backupWorld(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        log.info("Starting world backup for instance {}...", instanceId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                Path backupDir = Paths.get(instance.getInstancePath(), "backups");
                Files.createDirectories(backupDir);
                String backupFile = "world_backup_" + timestamp + ".tar.gz";
                Path backupPath = backupDir.resolve(backupFile);

                String command = String.format("tar -czf %s -C %s world world_nether world_the_end",
                        backupPath.toString(), instance.getInstancePath());
                Process process = new ProcessBuilder(command.split(" ")).start();
                int exitCode = process.waitFor();

                boolean success = exitCode == 0;
                if (success) {
                    log.info("World backup for instance {} created successfully: {}", instanceId, backupPath);
                } else {
                    log.error("World backup for instance {} failed with exit code: {}", instanceId, exitCode);
                }

                return success;
            } catch (Exception e) {
                log.error("Error creating world backup for instance {}", instanceId, e);
                return false;
            }
        });
    }

    public Set<String> getOnlinePlayersSet(Long instanceId) {
        return new HashSet<>(onlinePlayers.getOrDefault(instanceId, ConcurrentHashMap.newKeySet()));
    }

    public void refreshServerInfo(Long instanceId) {
        if (isServerRunning(instanceId)) {
            updatePlayerListFromRcon(instanceId);
            updateServerInfoFromRcon(instanceId);
            if (!tpsDebugActive.getOrDefault(instanceId, false)) {
                updateTpsFromRcon(instanceId);
            }
        }
    }

    public Map<String, Object> getDetailedServerInfo(Long instanceId) {
        Map<String, Object> info = new HashMap<>();

        if (!isServerRunning(instanceId)) {
            info.put("status", "offline");
            return info;
        }

        info.put("status", "online");
        info.put("playerCount", currentPlayerCounts.getOrDefault(instanceId, 0));
        info.put("maxPlayers", serverProperties.getIntProperty(instanceId, "max-players", 20));
        info.put("onlinePlayers", new ArrayList<>(onlinePlayers.getOrDefault(instanceId, ConcurrentHashMap.newKeySet())));
        info.put("tps", lastKnownTps.getOrDefault(instanceId, 20.0));
        info.put("worldSeed", worldSeeds.getOrDefault(instanceId, "Unknown"));
        info.put("version", serverVersions.getOrDefault(instanceId, "Unknown"));
        info.put("uptime", getServerUptime(instanceId));

        if (rconService.isConfigured(instanceId)) {
            String borderInfo = rconService.executeCommandSync(instanceId, "worldborder get");
            if (borderInfo != null) {
                info.put("worldBorder", borderInfo);
            }
        }

        return info;
    }

    private ServerStatus buildOfflineServerStatus(Long instanceId) {
        var systemStats = systemMonitoringService.getSystemStats();

        return ServerStatus.builder()
                .online(false)
                .playersOnline(0)
                .maxPlayers(serverProperties.getIntProperty(instanceId, "max-players", 20))
                .cpuUsage((Double) systemStats.get("cpuUsage"))
                .ramUsage((Double) systemStats.get("ramUsage"))
                .totalRam((Double) systemStats.get("totalRam"))
                .uptime("Offline")
                .tps(0.0)
                .lastUpdated(LocalDateTime.now())
                .onlinePlayers(new ArrayList<>())
                .version("Unknown")
                .worldName("Unknown")
                .diskUsage((Double) systemStats.get("diskUsage"))
                .totalDisk((Double) systemStats.get("totalDisk"))
                .build();
    }

    private String getServerUptime(Long instanceId) {
        if (!isServerRunning(instanceId)) {
            return "Offline";
        }

        LocalDateTime startTime = serverStartTimes.get(instanceId);
        if (startTime != null) {
            return formatDuration(Duration.between(startTime, LocalDateTime.now()));
        }

        return "Unknown";
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    private String getWorldName(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        try {
            Path worldPath = Paths.get(instance.getInstancePath()).resolve("world");
            if (Files.exists(worldPath)) {
                return "world";
            }
            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String stripColorCodes(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    private ServerInstance getInstance(Long instanceId) {
        return serverInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid instanceId: " + instanceId));
    }

    public ServerInstance createServerInstance(String instanceName, String templateId) {
        var template = templateService.getTemplates().stream()
                .filter(t -> t.id().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid templateId: " + templateId));

        Path instancePath = Paths.get("servers", instanceName).toAbsolutePath();
        String urlString = template.downloadUrl();
        String jarFileName = urlString.substring(urlString.lastIndexOf('/') + 1);

        ServerInstance instance = ServerInstance.builder()
                .name(instanceName)
                .version(template.name())
                .serverType(template.type())
                .instancePath(instancePath.toString())
                .jarFileName(jarFileName)
                .ip("0.0.0.0")
                .port(25565) // default port
                .rconEnabled(false)
                .build();

        return serverInstanceRepository.save(instance);
    }

    @Async
    public void downloadAndInstallServer(Long instanceId, String templateId) {
        ServerInstance instance = getInstance(instanceId);
        var template = templateService.getTemplates().stream()
                .filter(t -> t.id().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid templateId: " + templateId));

        WebSocketService webSocketService = applicationContext.getBean(WebSocketService.class);

        webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.info("Starting installation for " + instance.getName() + "..."));

        Path instancePath = Paths.get(instance.getInstancePath());
        try {
            Files.createDirectories(instancePath);
            webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.info("Created directory: " + instancePath));
        } catch (IOException e) {
            webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.error("Failed to create directory: " + e.getMessage()));
            throw new RuntimeException("Could not create directory for server instance", e);
        }

        try {
            Files.writeString(instancePath.resolve("eula.txt"), "eula=true");
            webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.info("EULA accepted."));
        } catch (IOException e) {
            webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.error("Failed to write eula.txt: " + e.getMessage()));
            throw new RuntimeException("Could not write eula.txt", e);
        }

        for (var step : template.installationSteps()) {
            String command = step.command()
                    .replace("{downloadUrl}", template.downloadUrl())
                    .replace("{jar}", instance.getJarFileName())
                    .replace("{ram}", parseRam(template.hardwareRequirements()));

            try {
                switch (step.type()) {
                    case "DOWNLOAD":
                        webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.info("Downloading server files..."));
                        Path jarPath = instancePath.resolve(instance.getJarFileName());
                        serverDownloaderService.downloadFile(command, jarPath).join();
                        webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.info("Download complete."));
                        break;
                    case "RUN":
                        webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.info("Executing command: " + command));
                        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
                        processBuilder.directory(instancePath.toFile());
                        Process process = processBuilder.start();
                        process.waitFor(2, TimeUnit.MINUTES);
                        webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.info("Command executed."));
                        break;
                    default:
                        webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.info("Unknown installation step type: " + step.type()));
                }
            } catch (Exception e) {
                webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.error("Installation failed at step " + step.type() + ": " + e.getMessage()));
                log.error("Installation failed for instance {}", instanceId, e);
                return;
            }
        }
        webSocketService.broadcastConsoleMessage(instanceId, ConsoleMessage.info("Installation finished successfully!"));
    }

    private String parseRam(String ramRequirement) {
        if (ramRequirement == null || !ramRequirement.toLowerCase().contains("ram")) {
            return "2G"; // Default fallback
        }
        // Example format: "RAM 2GB+"
        String[] parts = ramRequirement.split("\\s+");
        for (String part : parts) {
            if (part.toLowerCase().matches("\\d+[gm]b?(\\+)?")) {
                return part.replaceAll("[^\\dGMgm]", "").toUpperCase();
            }
        }
        return "2G"; // Default if parsing fails
    }

    public void deleteServerInstance(Long instanceId) throws IOException {
        log.info("Deleting server instance with ID: {}", instanceId);
        
        ServerInstance instance = serverInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Server instance not found"));
        
        // Stop the server if it's running
        if (isServerRunning(instanceId)) {
            log.info("Stopping server {} before deletion", instance.getName());
            stopServer(instanceId);
            
            // Wait a moment for the server to fully stop
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Clean up server processes and data
        serverStartTimes.remove(instanceId);
        onlinePlayers.remove(instanceId);
        currentPlayerCounts.remove(instanceId);
        serverVersions.remove(instanceId);
        worldSeeds.remove(instanceId);
        lastKnownTps.remove(instanceId);
        tpsDebugActive.remove(instanceId);
        
        // Delete server files
        Path serverPath = Paths.get(instance.getInstancePath());
        if (Files.exists(serverPath)) {
            log.info("Deleting server files at: {}", serverPath);
            deleteDirectoryRecursively(serverPath);
        }
        
        // Delete from database
        serverInstanceRepository.deleteById(instanceId);
        
        log.info("Successfully deleted server instance: {}", instance.getName());
    }
    
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                log.warn("Failed to delete file: {}", p, e);
                            }
                        });
            }
        } else if (Files.exists(path)) {
            Files.delete(path);
        }
    }
}
