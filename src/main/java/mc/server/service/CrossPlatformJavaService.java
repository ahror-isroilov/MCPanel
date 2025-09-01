package mc.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.model.ConsoleMessage;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.FileInputStream;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrossPlatformJavaService {
    
    private final WebSocketService webSocketService;
    
    private static final String JAVA_17_WINDOWS_URL = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.12_7.zip";
    private static final String JAVA_17_LINUX_URL = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.12_7.tar.gz";
    private static final String JAVA_17_MACOS_URL = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_mac_hotspot_17.0.12_7.tar.gz";
    
    private static final String JAVA_21_WINDOWS_URL = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.4%2B7/OpenJDK21U-jdk_x64_windows_hotspot_21.0.4_7.zip";
    private static final String JAVA_21_LINUX_URL = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.4%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.4_7.tar.gz";
    private static final String JAVA_21_MACOS_URL = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.4%2B7/OpenJDK21U-jdk_x64_mac_hotspot_21.0.4_7.tar.gz";
    
    public Path ensureJavaAvailable(Long instanceId, String systemRequirements) throws Exception {
        JavaInstallationStatus status = checkSystemJavaInstallation();
        
        int requiredVersion = parseJavaVersionFromRequirements(systemRequirements);
        
        webSocketService.broadcastConsoleMessage(instanceId, 
            ConsoleMessage.info("Java requirement: Java " + requiredVersion + "+"));
        
        if (status.installed() && status.version() >= requiredVersion) {
            webSocketService.broadcastConsoleMessage(instanceId, 
                ConsoleMessage.info("✅ System Java " + status.version() + " satisfies requirement"));
            return null;
        }
        
        Path portableJavaPath = getStoredJavaPath(requiredVersion);
        if (portableJavaPath != null && Files.exists(portableJavaPath)) {
            webSocketService.broadcastConsoleMessage(instanceId, 
                ConsoleMessage.info("✅ Found existing portable Java " + requiredVersion + " at: " + portableJavaPath));
            return portableJavaPath;
        }
        
        webSocketService.broadcastConsoleMessage(instanceId, 
            ConsoleMessage.info("⚠️ Java " + requiredVersion + "+ not available, setting up portable version..."));
        
        return setupPortableJava(instanceId, requiredVersion);
    }
    
    private JavaInstallationStatus checkSystemJavaInstallation() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("java", "-version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (var reader = process.inputReader()) {
                reader.lines().forEach(line -> output.append(line).append("\n"));
            }
            
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                String versionOutput = output.toString();
                int version = parseJavaVersion(versionOutput);
                return new JavaInstallationStatus(true, version, versionOutput.trim());
            }
        } catch (Exception e) {
            log.debug("System Java check failed: {}", e.getMessage());
        }
        
        return new JavaInstallationStatus(false, 0, "");
    }
    
    private Path setupPortableJava(Long instanceId, int requiredVersion) throws Exception {
        webSocketService.broadcastConsoleMessage(instanceId, 
            ConsoleMessage.info("🔄 Setting up portable Java " + requiredVersion + "..."));
        
        int versionToInstall = requiredVersion >= 21 ? 21 : 17;
        String downloadUrl = getDownloadUrlForPlatform(versionToInstall);
        String osName = getOSName();
        
        webSocketService.broadcastConsoleMessage(instanceId, 
            ConsoleMessage.info("Downloading Java " + versionToInstall + " for " + osName + "..."));
        
        Path javaPath = downloadAndExtractJava(instanceId, downloadUrl, versionToInstall);
        
        storeJavaPath(versionToInstall, javaPath);
        
        webSocketService.broadcastConsoleMessage(instanceId, 
            ConsoleMessage.info("✅ Portable Java " + versionToInstall + " ready at: " + javaPath));
        
        return javaPath;
    }
    
    private String getDownloadUrlForPlatform(int version) {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (version == 21) {
            if (osName.contains("win")) {
                return JAVA_21_WINDOWS_URL;
            } else if (osName.contains("mac")) {
                return JAVA_21_MACOS_URL;
            } else {
                return JAVA_21_LINUX_URL;
            }
        } else {
            if (osName.contains("win")) {
                return JAVA_17_WINDOWS_URL;
            } else if (osName.contains("mac")) {
                return JAVA_17_MACOS_URL;
            } else {
                return JAVA_17_LINUX_URL;
            }
        }
    }
    
    private String getOSName() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "Windows";
        } else if (osName.contains("mac")) {
            return "macOS";
        } else {
            return "Linux/Unix";
        }
    }
    
    private Path downloadAndExtractJava(Long instanceId, String downloadUrl, int version) throws Exception {
        Path javaDir = Paths.get("java");
        Files.createDirectories(javaDir);
        
        Path archiveFile = javaDir.resolve("java" + version + "_" + getOSName() + getArchiveExtension(downloadUrl));
        downloadFile(instanceId, downloadUrl, archiveFile);
        
        Path extractDir = javaDir.resolve("java" + version);
        extractArchive(instanceId, archiveFile, extractDir);
        
        Path javaExecutable = findJavaExecutable(extractDir);
        
        Files.deleteIfExists(archiveFile);
        
        return javaExecutable;
    }
    
    private void downloadFile(Long instanceId, String downloadUrl, Path targetFile) throws Exception {
        webSocketService.broadcastConsoleMessage(instanceId, 
            ConsoleMessage.info("Downloading: " + downloadUrl));
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofMinutes(10))
                .build();
        
        HttpResponse<Path> response = client.send(request, 
            HttpResponse.BodyHandlers.ofFile(targetFile));
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download Java. HTTP " + response.statusCode());
        }
        
        long fileSize = Files.size(targetFile);
        webSocketService.broadcastConsoleMessage(instanceId, 
            ConsoleMessage.info("✅ Downloaded " + formatFileSize(fileSize)));
    }
    
    private void extractArchive(Long instanceId, Path archiveFile, Path extractDir) throws Exception {
        webSocketService.broadcastConsoleMessage(instanceId, 
            ConsoleMessage.info("Extracting Java archive..."));
        
        Files.createDirectories(extractDir);
        
        if (archiveFile.toString().endsWith(".zip")) {
            extractZip(archiveFile, extractDir);
        } else if (archiveFile.toString().endsWith(".tar.gz")) {
            extractTarGz(archiveFile, extractDir);
        } else {
            throw new RuntimeException("Unsupported archive format: " + archiveFile);
        }
        
        webSocketService.broadcastConsoleMessage(instanceId, 
            ConsoleMessage.info("✅ Java extracted successfully"));
    }
    
    private void extractZip(Path zipFile, Path extractDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = extractDir.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                        zis.transferTo(fos);
                    }
                    
                    if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                        if (entryPath.toString().endsWith("java") || entryPath.toString().contains("bin/")) {
                            entryPath.toFile().setExecutable(true);
                        }
                    }
                }
            }
        }
    }
    
    private void extractTarGz(Path tarGzFile, Path extractDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tarGzFile.toString(), "-C", extractDir.toString());
        Process process = pb.start();
        boolean finished = process.waitFor(2, TimeUnit.MINUTES);
        
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Tar extraction timed out");
        }
        
        if (process.exitValue() != 0) {
            throw new RuntimeException("Tar extraction failed with exit code: " + process.exitValue());
        }
    }
    
    private Path findJavaExecutable(Path extractDir) throws IOException {
        Path[] possiblePaths = {
            extractDir.resolve("bin/java"),
            extractDir.resolve("bin/java.exe")
        };
        
        try (var stream = Files.walk(extractDir, 3)) {
            return stream
                .filter(path -> path.getFileName().toString().equals("java") || 
                               path.getFileName().toString().equals("java.exe"))
                .filter(path -> path.getParent().getFileName().toString().equals("bin"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find java executable in extracted archive"));
        }
    }
    
    private String getArchiveExtension(String downloadUrl) {
        if (downloadUrl.endsWith(".zip")) {
            return ".zip";
        } else if (downloadUrl.endsWith(".tar.gz")) {
            return ".tar.gz";
        } else {
            return ".archive";
        }
    }
    
    private void storeJavaPath(int version, Path javaPath) {
        try {
            Path configFile = Paths.get("java/java-paths.properties");
            Properties props = new Properties();
            
            if (Files.exists(configFile)) {
                try (var input = Files.newInputStream(configFile)) {
                    props.load(input);
                }
            }
            
            props.setProperty("java." + version + ".path", javaPath.toString());
            
            Files.createDirectories(configFile.getParent());
            try (var output = Files.newOutputStream(configFile)) {
                props.store(output, "Java installation paths");
            }
            
            log.info("Stored Java {} path: {}", version, javaPath);
        } catch (Exception e) {
            log.warn("Failed to store Java path: {}", e.getMessage());
        }
    }
    
    private Path getStoredJavaPath(int requiredVersion) {
        try {
            Path configFile = Paths.get("java/java-paths.properties");
            if (!Files.exists(configFile)) {
                return null;
            }
            
            Properties props = new Properties();
            try (var input = Files.newInputStream(configFile)) {
                props.load(input);
            }
            
            for (int version = requiredVersion; version <= 25; version++) {
                String pathStr = props.getProperty("java." + version + ".path");
                if (pathStr != null) {
                    Path javaPath = Paths.get(pathStr);
                    if (Files.exists(javaPath)) {
                        return javaPath;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to load Java paths: {}", e.getMessage());
        }
        
        return null;
    }
    
    private int parseJavaVersionFromRequirements(String systemRequirements) {
        if (systemRequirements == null) return 17;
        
        String lower = systemRequirements.toLowerCase();
        if (lower.contains("java")) {
            String[] parts = systemRequirements.split("\\s+");
            for (String part : parts) {
                String cleanPart = part.replaceAll("[^\\d]", "");
                if (!cleanPart.isEmpty()) {
                    try {
                        return Integer.parseInt(cleanPart);
                    } catch (NumberFormatException e) {
                        // Continue to next part
                    }
                }
            }
        }
        
        return 17;
    }
    
    private int parseJavaVersion(String versionOutput) {
        try {
            if (versionOutput.contains("version \"")) {
                String versionPart = versionOutput.split("version \"")[1].split("\"")[0];
                String[] parts = versionPart.split("\\.");
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
            log.warn("Failed to parse Java version from: {}", versionOutput, e);
        }
        return 8;
    }
    
    private String formatFileSize(long bytes) {
        if (bytes >= 1_000_000) {
            return String.format("%.1f MB", bytes / 1_000_000.0);
        } else if (bytes >= 1_000) {
            return String.format("%.1f KB", bytes / 1_000.0);
        } else {
            return bytes + " bytes";
        }
    }

    public record JavaInstallationStatus(boolean installed, int version, String versionString) {
    }
}