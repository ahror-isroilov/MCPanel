package mc.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "minecraft.server")
public class MinecraftServerConfig {
    private String serviceName = "minecraft";
    private String serverPath = "/home/minecraft/mc-server";
    private int maxPlayers = 20;
    private String logFile = "${minecraft.server.path}/logs/latest.log";

    private Console console = new Console();

    private Rcon rcon = new Rcon();

    @Data
    public static class Console {
        private int maxHistory = 1000;
    }

    @Data
    public static class Rcon {
        private String host = "localhost";
        private int port = 25575;
        private String password;
        private int timeout = 5000;
        private boolean enabled = true;
        private int retryAttempts = 3;
        private int retryDelay = 1000;

        public boolean isConfigured() {
            return enabled && password != null && !password.trim().isEmpty();
        }
    }

    private Backup backup = new Backup();

    @Data
    public static class Backup {
        private boolean enabled = true;
        private String path = "${minecraft.server.path}/backups";
        private String schedule = "0 0 3 * * ?";
        private int retentionDays = 7;
        private boolean compressBackups = true;
    }

    private Monitoring monitoring = new Monitoring();

    @Data
    public static class Monitoring {
        private boolean enabled = true;
        private int tpsCheckInterval = 30;
        private int playerListRefreshInterval = 10;
        private boolean autoRestartOnCrash = false;
        private int healthCheckInterval = 60;
    }
}