package mc.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.model.ServerInstance;
import mc.server.repository.ServerInstanceRepository;
import mc.server.dto.WhitelistEntry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerPropertiesService {
    private final ServerInstanceRepository serverInstanceRepository;
    private final ObjectMapper objectMapper;
    private final MojangApiService mojangApiService;

    private static final String PROPERTIES_FILE = "server.properties";
    private static final String WHITELIST_FILE = "whitelist.json";

    public Map<String, String> getServerProperties(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        Map<String, String> properties = new HashMap<>();
        try {
            Path propsFile = Paths.get(instance.getInstancePath()).resolve(PROPERTIES_FILE);
            if (Files.exists(propsFile)) {
                Files.readAllLines(propsFile)
                        .stream()
                        .filter(line -> !line.startsWith("#") && line.contains("="))
                        .forEach(line -> {
                            String[] parts = line.split("=", 2);
                            if (parts.length == 2) {
                                properties.put(parts[0].trim(), parts[1].trim());
                            }
                        });
            }
        } catch (Exception e) {
            log.error("Error reading server properties file for instance {}", instanceId, e);
        }
        return properties;
    }

    @Async
    public CompletableFuture<Boolean> updateProperty(Long instanceId, String propertyKey, String propertyValue) {
        return CompletableFuture.supplyAsync(() -> updatePropertiesFile(instanceId, propertyKey, propertyValue));
    }

    @Async
    public CompletableFuture<Boolean> updateProperties(Long instanceId, Map<String, String> properties) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                for (Map.Entry<String, String> entry : properties.entrySet()) {
                    if (!updatePropertiesFile(instanceId, entry.getKey(), entry.getValue())) {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                log.error("Error updating server properties for instance {}", instanceId, e);
                return false;
            }
        });
    }

    private boolean updatePropertiesFile(Long instanceId, String propertyKey, String propertyValue) {
        ServerInstance instance = getInstance(instanceId);
        try {
            Path propsFile = Paths.get(instance.getInstancePath()).resolve(PROPERTIES_FILE);
            if (!Files.exists(propsFile)) {
                log.error("server.properties file not found for instance {} at: {}", instanceId, propsFile);
                return false;
            }

            List<String> lines = Files.readAllLines(propsFile);
            boolean propertyFound = false;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.startsWith("#") && line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    if (parts[0].trim().equals(propertyKey)) {
                        lines.set(i, propertyKey + "=" + propertyValue);
                        propertyFound = true;
                        break;
                    }
                }
            }

            if (!propertyFound) {
                lines.add(propertyKey + "=" + propertyValue);
            }

            Files.write(propsFile, lines);
            log.info("Updated server property in file for instance {}: {}={}", instanceId, propertyKey, propertyValue);
            return true;

        } catch (IOException e) {
            log.error("Error updating properties file for instance {}", instanceId, e);
            return false;
        }
    }

    public int getIntProperty(Long instanceId, String key, int defaultValue) {
        Map<String, String> properties = getServerProperties(instanceId);
        String propertyValue = properties.get(key);

        if (propertyValue != null) {
            try {
                return Integer.parseInt(propertyValue.trim());
            } catch (NumberFormatException e) {
                log.warn("Property '{}' for instance {} has a non-integer value: '{}'. Falling back to default.", key, instanceId, propertyValue);
            }
        }
        return defaultValue;
    }

    public List<WhitelistEntry> getWhitelist(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        try {
            Path whitelistFile = Paths.get(instance.getInstancePath()).resolve(WHITELIST_FILE);
            if (Files.exists(whitelistFile)) {
                String content = Files.readString(whitelistFile);
                return objectMapper.readValue(content, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.error("Error reading whitelist file for instance {}", instanceId, e);
        }
        return new ArrayList<>();
    }

    @Async
    public CompletableFuture<Boolean> addToWhitelist(Long instanceId, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            ServerInstance instance = getInstance(instanceId);
            try {
                Path whitelistFile = Paths.get(instance.getInstancePath()).resolve(WHITELIST_FILE);
                List<WhitelistEntry> whitelist = getWhitelist(instanceId);

                if (whitelist.stream().noneMatch(entry -> entry.getName().equalsIgnoreCase(playerName))) {
                    String uuid = mojangApiService.getPlayerUuid(playerName)
                            .orElse("00000000-0000-0000-0000-000000000000"); // Fallback UUID
                    
                    whitelist.add(new WhitelistEntry(playerName, uuid));
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(whitelistFile.toFile(), whitelist);
                    return true;
                }
                return false; // Player already in whitelist
            } catch (IOException e) {
                log.error("Error adding player to whitelist file for instance {}", instanceId, e);
                return false;
            }
        });
    }

    @Async
    public CompletableFuture<Boolean> removeFromWhitelist(Long instanceId, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            ServerInstance instance = getInstance(instanceId);
            try {
                Path whitelistFile = Paths.get(instance.getInstancePath()).resolve(WHITELIST_FILE);
                List<WhitelistEntry> whitelist = getWhitelist(instanceId);
                
                boolean removed = whitelist.removeIf(entry -> entry.getName().equalsIgnoreCase(playerName));
                
                if (removed) {
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(whitelistFile.toFile(), whitelist);
                }
                
                return removed;
            } catch (IOException e) {
                log.error("Error removing player from whitelist file for instance {}", instanceId, e);
                return false;
            }
        });
    }

    public boolean requiresRestart(String propertyKey) {
        Set<String> restartRequired = Set.of(
                "server-port", "server-ip", "online-mode", "max-players",
                "enable-rcon", "rcon.port", "rcon.password",
                "level-name", "level-seed", "level-type", "generator-settings"
        );
        return restartRequired.contains(propertyKey);
    }

    public Map<String, PropertyValidation> getPropertyValidations() {
        Map<String, PropertyValidation> validations = new HashMap<>();

        validations.put("max-players", new PropertyValidation(
                "integer", "Maximum number of players allowed on the server", 1, 1000));

        validations.put("gamemode", new PropertyValidation(
                "enum", "Default game mode for new players",
                Arrays.asList("survival", "creative", "adventure", "spectator"), null));

        validations.put("difficulty", new PropertyValidation(
                "enum", "Server difficulty level",
                Arrays.asList("peaceful", "easy", "normal", "hard"), null));

        validations.put("pvp", new PropertyValidation(
                "boolean", "Enable Player vs Player combat"));

        validations.put("online-mode", new PropertyValidation(
                "boolean", "Verify player accounts against Minecraft servers"));

        validations.put("white-list", new PropertyValidation(
                "boolean", "Enable whitelist (only whitelisted players can join)"));

        return validations;
    }

    private ServerInstance getInstance(Long instanceId) {
        return serverInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid instanceId: " + instanceId));
    }

    public static class PropertyValidation {
        public String type;
        public String description;
        public Object min;
        public Object max;

        public PropertyValidation(String type, String description) {
            this.type = type;
            this.description = description;
        }

        public PropertyValidation(String type, String description, Object min, Object max) {
            this.type = type;
            this.description = description;
            this.min = min;
            this.max = max;
        }
    }
}
