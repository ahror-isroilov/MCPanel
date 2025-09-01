package mc.server.service.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.dto.ServerTemplate;
import mc.server.model.ConsoleMessage;
import mc.server.model.InstallationStatus;
import mc.server.model.ServerInstance;
import mc.server.repository.ServerInstanceRepository;
import mc.server.service.CrossPlatformJavaService;
import mc.server.service.PortManagerService;
import mc.server.service.TemplateService;
import mc.server.service.WebSocketService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerInstallationService {
    
    private final ServerInstanceRepository serverInstanceRepository;
    private final ServerDownloaderService serverDownloaderService;
    private final TemplateService templateService;
    private final WebSocketService webSocketService;
    private final PortManagerService portManagerService;
    private final ServerPropertiesService serverPropertiesService;
    private final CrossPlatformJavaService crossPlatformJavaService;

    @Async
    public void installServer(Long instanceId, String templateId) {
        log.info("=== ASYNC INSTALLATION STARTED === Instance ID: {}, Template ID: {}", instanceId, templateId);
        
        ServerInstance instance = getServerInstance(instanceId);
        ServerTemplate template = getTemplate(templateId);
        
        try {
            log.info("Starting installation for server instance: {}", instance.getName());
            webSocketService.broadcastConsoleMessage(instanceId, 
                ConsoleMessage.info("🚀 Installation started for " + instance.getName()));
            updateInstanceStatus(instanceId, InstallationStatus.DOWNLOADING, "Starting installation...");
            
            Path javaExecutable = crossPlatformJavaService.ensureJavaAvailable(instanceId, template.systemRequirements());
            createServerDirectory(instance);

            acceptEula(instance);

            executeInstallationSteps(instance, template);

            generateServerFiles(instance, javaExecutable);

            configureServer(instance, template);

            updateInstanceStatus(instanceId, InstallationStatus.STOPPED, "Installation completed successfully!");
            webSocketService.broadcastConsoleMessage(instanceId, 
                ConsoleMessage.info("🎉 Server installation completed successfully! Your server is ready to start."));
                
            log.info("Installation completed for server instance: {}", instance.getName());
            
        } catch (Exception e) {
            log.error("Installation failed for server instance: {}", instance.getName(), e);
            updateInstanceStatus(instanceId, InstallationStatus.INSTALLATION_FAILED, 
                "Installation failed: " + e.getMessage());
            webSocketService.broadcastConsoleMessage(instanceId, 
                ConsoleMessage.error("Installation failed: " + e.getMessage()));
        }
    }
    
    private void createServerDirectory(ServerInstance instance) throws IOException {
        Path instancePath = Paths.get(instance.getInstancePath());
        Files.createDirectories(instancePath);
        webSocketService.broadcastConsoleMessage(instance.getId(), 
            ConsoleMessage.info("Created server directory: " + instancePath));
        log.info("Created server directory: {}", instancePath);
    }
    
    private void acceptEula(ServerInstance instance) throws IOException {
        Path eulaPath = Paths.get(instance.getInstancePath()).resolve("eula.txt");
        Files.writeString(eulaPath, "eula=true");
        webSocketService.broadcastConsoleMessage(instance.getId(), 
            ConsoleMessage.info("EULA accepted"));
        log.info("EULA accepted for instance: {}", instance.getName());
    }
    
    private void executeInstallationSteps(ServerInstance instance, ServerTemplate template) throws Exception {
        updateInstanceStatus(instance.getId(), InstallationStatus.DOWNLOADING, "Downloading server files...");
        
        for (var step : template.installationSteps()) {
            String command = step.command()
                    .replace("{downloadUrl}", template.downloadUrl())
                    .replace("{jar}", instance.getJarFileName())
                    .replace("{ram}", parseRam(template.hardwareRequirements()));
            
            switch (step.type()) {
                case "DOWNLOAD":
                    webSocketService.broadcastConsoleMessage(instance.getId(), 
                        ConsoleMessage.info("Downloading server files..."));
                    Path jarPath = Paths.get(instance.getInstancePath()).resolve(instance.getJarFileName());
                    serverDownloaderService.downloadFile(command, jarPath).join();
                    webSocketService.broadcastConsoleMessage(instance.getId(), 
                        ConsoleMessage.info("Download completed"));
                    break;
                    
                case "RUN":
                    updateInstanceStatus(instance.getId(), InstallationStatus.RUNNING_INSTALLER, 
                        "Running installation command: " + command);
                    webSocketService.broadcastConsoleMessage(instance.getId(), 
                        ConsoleMessage.info("Executing: " + command));
                    
                    ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
                    processBuilder.directory(Paths.get(instance.getInstancePath()).toFile());
                    processBuilder.redirectErrorStream(true);

                    Process process = processBuilder.start();

                    try (var reader = process.inputReader()) {
                        reader.lines().forEach(line -> {
                            webSocketService.broadcastConsoleMessage(instance.getId(), ConsoleMessage.info(line));
                            log.info("[Installer] {}", line);
                        });
                    }

                    boolean finished = process.waitFor(30, TimeUnit.SECONDS);

                    if (!finished) {
                        process.destroyForcibly();
                        throw new RuntimeException("Installation command timed out after 1 minute");
                    }

                    int exitCode = process.exitValue();
                    if (exitCode != 0) {
                        throw new RuntimeException("Installation command failed with exit code: " + exitCode);
                    }
                    
                    webSocketService.broadcastConsoleMessage(instance.getId(), 
                        ConsoleMessage.info("Command executed successfully"));
                    break;
                    
                default:
                    log.warn("Unknown installation step type: {}", step.type());
                    webSocketService.broadcastConsoleMessage(instance.getId(), 
                        ConsoleMessage.info("Unknown installation step type: " + step.type()));
            }
        }
    }
    
    private void generateServerFiles(ServerInstance instance, Path javaExecutable) throws Exception {
        updateInstanceStatus(instance.getId(), InstallationStatus.CONFIGURING, 
            "Generating baseline server files...");
        
        webSocketService.broadcastConsoleMessage(instance.getId(), 
            ConsoleMessage.info("Running server for the first time to generate baseline files..."));
        
        Path instancePath = Paths.get(instance.getInstancePath());

        String javaCommand = (javaExecutable != null) ? javaExecutable.toString() : "java";
        
        ProcessBuilder processBuilder = new ProcessBuilder(javaCommand, "-Xms512M", "-Xmx1G", "-jar", instance.getJarFileName(), "nogui");
        processBuilder.directory(instancePath.toFile());
        processBuilder.redirectErrorStream(true);
        
        try {
            Process process = processBuilder.start();
            boolean serverFullyLoaded = false;
            
            // Monitor output and look for server completion signals
            Thread outputThread = new Thread(() -> {
                try (var reader = process.inputReader()) {
                    reader.lines().forEach(line -> {
                        webSocketService.broadcastConsoleMessage(instance.getId(), 
                            ConsoleMessage.info("[Server Init] " + line));
                        log.debug("Server init output: {}", line);
                        
                        // Look for signs that the server has completed loading
                        if (line.contains("Done (") && line.contains("s)! For help, type \"help\"")) {
                            log.info("Server finished loading for instance: {}", instance.getName());
                            
                            // Send stop command to gracefully shut down server
                            try {
                                webSocketService.broadcastConsoleMessage(instance.getId(), 
                                    ConsoleMessage.info("Server loaded successfully, shutting down..."));
                                process.outputWriter().write("stop\n");
                                process.outputWriter().flush();
                            } catch (Exception e) {
                                log.warn("Failed to send stop command to server: {}", e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    log.warn("Error reading server init output: {}", e.getMessage());
                }
            });
            outputThread.start();
            
            // Give the server time to start up and generate files
            webSocketService.broadcastConsoleMessage(instance.getId(), 
                ConsoleMessage.info("Waiting for server to initialize and generate files..."));
            
            // Wait for the server to generate files and shut down gracefully
            boolean processFinished = process.waitFor(180, TimeUnit.SECONDS); // 3 minute timeout
            
            if (!processFinished) {
                webSocketService.broadcastConsoleMessage(instance.getId(), 
                    ConsoleMessage.info("Server taking longer than expected, forcing shutdown..."));
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS); // Wait a bit for cleanup
            }
            
            // Wait for output thread to finish
            outputThread.join(5000);
            
            // Give a moment for files to be written to disk
            Thread.sleep(2000);
            
            // Verify that essential files were created
            verifyGeneratedFiles(instancePath);
            
            webSocketService.broadcastConsoleMessage(instance.getId(), 
                ConsoleMessage.info("✅ Server files generated successfully"));
            log.info("Server files generated for instance: {}", instance.getName());
            
        } catch (Exception e) {
            log.error("Failed to generate server files for instance: {}", instance.getName(), e);
            throw new RuntimeException("Failed to generate server files: " + e.getMessage(), e);
        }
    }
    
    private void verifyGeneratedFiles(Path instancePath) throws IOException {
        // Check for essential files that should be generated
        String[] essentialFiles = {
            "server.properties",
            "eula.txt"
        };
        
        String[] essentialDirectories = {
            "logs",
            "world",
            "world_nether",
            "world_the_end",
        };
        
        for (String fileName : essentialFiles) {
            Path filePath = instancePath.resolve(fileName);
            if (!Files.exists(filePath)) {
                log.warn("Expected file not found: {}", filePath);
            } else {
                log.debug("Verified file exists: {}", filePath);
            }
        }
        
        for (String dirName : essentialDirectories) {
            Path dirPath = instancePath.resolve(dirName);
            if (!Files.exists(dirPath)) {
                log.warn("Expected directory not found: {}", dirPath);
            } else {
                log.debug("Verified directory exists: {}", dirPath);
            }
        }
        
        // Note: world directories are created when players join, so we don't check for them here
        log.info("File verification completed for instance path: {}", instancePath);
    }
    
    private void configureServer(ServerInstance instance, ServerTemplate template) {
        updateInstanceStatus(instance.getId(), InstallationStatus.CONFIGURING, "Configuring server settings...");
        
        try {
            // Allocate ports
            int gamePort = portManagerService.findAvailablePort();
            int rconPort = portManagerService.findAvailableRconPort();
            
            // Generate secure RCON password
            String rconPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            
            // Parse and set allocated memory
            String allocatedMemory = parseRam(template.hardwareRequirements());
            instance.setAllocatedMemory(allocatedMemory);
            
            // Update instance with allocated ports and RCON settings
            instance.setPort(gamePort);
            instance.setRconPort(rconPort);
            instance.setRconPassword(rconPassword);
            instance.setRconEnabled(true);
            instance.setIp("0.0.0.0");
            
            serverInstanceRepository.save(instance);
            
            // Configure server.properties
            configureServerProperties(instance, gamePort, rconPort, rconPassword);
            
            webSocketService.broadcastConsoleMessage(instance.getId(), 
                ConsoleMessage.info("Server configured - Game Port: " + gamePort + ", RCON Port: " + rconPort));
            log.info("Server configured for instance {}: Game Port: {}, RCON Port: {}", 
                instance.getName(), gamePort, rconPort);
                
        } catch (Exception e) {
            log.error("Failed to configure server for instance: {}", instance.getName(), e);
            throw new RuntimeException("Failed to configure server: " + e.getMessage(), e);
        }
    }
    
    private void configureServerProperties(ServerInstance instance, int gamePort, int rconPort, String rconPassword) {
        try {
            // Wait for server.properties to be generated if it doesn't exist yet
            Path propertiesPath = Paths.get(instance.getInstancePath()).resolve("server.properties");
            int attempts = 0;
            while (!Files.exists(propertiesPath) && attempts < 10) {
                Thread.sleep(1000);
                attempts++;
            }
            
            // If server.properties doesn't exist, create a basic one
            if (!Files.exists(propertiesPath)) {
                createDefaultServerProperties(propertiesPath);
            }
            
            // Update properties
            serverPropertiesService.updateProperty(instance.getId(), "server-port", String.valueOf(gamePort)).join();
            serverPropertiesService.updateProperty(instance.getId(), "enable-rcon", "true").join();
            serverPropertiesService.updateProperty(instance.getId(), "rcon.port", String.valueOf(rconPort)).join();
            serverPropertiesService.updateProperty(instance.getId(), "rcon.password", rconPassword).join();
            
            log.info("Server properties configured for instance: {}", instance.getName());
            
        } catch (Exception e) {
            log.error("Failed to configure server properties for instance: {}", instance.getName(), e);
            throw new RuntimeException("Failed to configure server properties", e);
        }
    }
    
    private void createDefaultServerProperties(Path propertiesPath) throws IOException {
        String defaultProperties = """
            #Minecraft server properties
            enable-jmx-monitoring=false
            rcon.port=25575
            level-seed=
            gamemode=survival
            enable-command-block=false
            enable-query=false
            generator-settings={}
            enforce-secure-profile=true
            level-name=world
            motd=A Minecraft Server
            query.port=25565
            pvp=true
            generate-structures=true
            max-chained-neighbor-updates=1000000
            difficulty=easy
            network-compression-threshold=256
            max-tick-time=60000
            require-resource-pack=false
            use-native-transport=true
            max-players=20
            online-mode=true
            enable-status=true
            allow-flight=false
            initial-disabled-packs=
            broadcast-rcon-to-ops=true
            view-distance=10
            server-ip=
            resource-pack-prompt=
            allow-nether=true
            server-port=25565
            enable-rcon=false
            sync-chunk-writes=true
            op-permission-level=4
            prevent-proxy-connections=false
            hide-online-players=false
            resource-pack=
            entity-broadcast-range-percentage=100
            simulation-distance=10
            rcon.password=
            player-idle-timeout=0
            debug=false
            force-gamemode=false
            rate-limit=0
            hardcore=false
            white-list=false
            broadcast-console-to-ops=true
            spawn-npcs=true
            spawn-animals=true
            function-permission-level=2
            initial-enabled-packs=vanilla
            level-type=minecraft\\:normal
            text-filtering-config=
            spawn-monsters=true
            enforce-whitelist=false
            spawn-protection=16
            resource-pack-sha1=
            max-world-size=29999984
            """;
        Files.writeString(propertiesPath, defaultProperties);
        log.info("Created default server.properties file");
    }
    
    private String parseRam(String ramRequirement) {
        if (ramRequirement == null || !ramRequirement.toLowerCase().contains("ram")) {
            return "2G";
        }
        String[] parts = ramRequirement.split("\\s+");
        for (String part : parts) {
            if (part.toLowerCase().matches("\\d+[gm]b?(\\+)?")) {
                return part.replaceAll("[^\\dGMgm]", "").toUpperCase();
            }
        }
        return "2G";
    }
    
    private void updateInstanceStatus(Long instanceId, InstallationStatus status, String message) {
        ServerInstance instance = getServerInstance(instanceId);
        instance.setStatus(status);
        instance.setStatusMessage(message);
        serverInstanceRepository.save(instance);
        log.info("Updated status for instance {}: {} - {}", instanceId, status, message);
    }
    
    private ServerInstance getServerInstance(Long instanceId) {
        return serverInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Server instance not found: " + instanceId));
    }
    
    private ServerTemplate getTemplate(String templateId) {
        return templateService.getTemplates().stream()
                .filter(t -> t.id().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
    }
}