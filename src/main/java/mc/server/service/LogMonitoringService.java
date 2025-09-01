package mc.server.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.Slf4j;
import mc.server.model.ConsoleMessage;
import mc.server.model.ServerInstance;
import mc.server.repository.ServerInstanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import static mc.server.service.LogPatterns.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogMonitoringService {

    @Value("${minecraft.server.console.max-history:1000}")
    private int maxHistorySize;

    private final WebSocketService webSocketService;
    private final ServerInstanceRepository serverInstanceRepository;

    private final Map<Long, ConcurrentLinkedQueue<ConsoleMessage>> consoleHistories = new ConcurrentHashMap<>();
    private final Map<Long, WatchService> watchServices = new ConcurrentHashMap<>();
    private final Map<Long, ExecutorService> executorServices = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> monitoringStates = new ConcurrentHashMap<>();

    @PreDestroy
    public void cleanup() {
        monitoringStates.keySet().forEach(this::stopMonitoring);
    }

    public void startMonitoring(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        Path logFile = Paths.get(instance.getInstancePath(), "logs", "latest.log");

        if (!Files.exists(logFile)) {
            log.warn("Log file does not exist for instance {}: {}. Monitoring will not start.", instanceId, logFile);
            return;
        }

        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path logDir = logFile.getParent();
            logDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            watchServices.put(instanceId, watchService);

            ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "log-monitor-" + instanceId);
                t.setDaemon(true);
                return t;
            });
            executorServices.put(instanceId, executorService);

            monitoringStates.put(instanceId, true);
            consoleHistories.put(instanceId, new ConcurrentLinkedQueue<>());
            loadRecentLogHistory(instanceId);

            executorService.submit(() -> monitorLogFile(instanceId, logFile));

            log.info("Started file-based log monitoring for instance {}: {}", instanceId, logFile);

        } catch (Exception e) {
            log.error("Error starting log monitoring for instance {}", instanceId, e);
        }
    }

    public void stopMonitoring(Long instanceId) {
        monitoringStates.put(instanceId, false);

        WatchService watchService = watchServices.remove(instanceId);
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception e) {
                log.debug("Error closing watch service for instance {}", instanceId, e);
            }
        }

        ExecutorService executorService = executorServices.remove(instanceId);
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Log monitoring service stopped for instance {}", instanceId);
    }

    private void monitorLogFile(Long instanceId, Path logFile) {
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            file.seek(file.length());
            WatchService watchService = watchServices.get(instanceId);

            while (monitoringStates.getOrDefault(instanceId, false)) {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            Path changed = (Path) event.context();
                            if (changed.toString().equals(logFile.getFileName().toString())) {
                                readNewLines(instanceId, file);
                            }
                        }
                    }
                    key.reset();
                }
            }
        } catch (Exception e) {
            log.error("Error monitoring log file for instance {}", instanceId, e);
        }
    }

    private void readNewLines(Long instanceId, RandomAccessFile file) throws IOException {
        String line;
        while ((line = file.readLine()) != null) {
            processLogLine(instanceId, line);
        }
    }

    private void processLogLine(Long instanceId, String rawLine) {
        try {
            ConsoleMessage message = parseLogLine(rawLine);
            if (message != null) {
                addToHistory(instanceId, message);

                if (webSocketService.hasActiveSessions()) {
                    webSocketService.broadcastConsoleMessage(instanceId, message);
                }
            }
        } catch (Exception e) {
            log.debug("Error processing log line for instance {}: {}", instanceId, rawLine, e);
        }
    }

    private ConsoleMessage parseLogLine(String rawLine) {
        if (rawLine == null || rawLine.trim().isEmpty()) {
            return null;
        }

        if (RCON_THREAD_PATTERN.matcher(rawLine).find() ||
                RCON_CONNECTION_PATTERN.matcher(rawLine).find() ||
                UUID_LOOKUP_PATTERN.matcher(rawLine).find()) {
            return null;
        }

        LocalDateTime timestamp = extractTimestamp(rawLine);

        Matcher errorMatcher = ERROR_PATTERN.matcher(rawLine);
        if (errorMatcher.find()) {
            return ConsoleMessage.builder()
                    .type("error")
                    .message(errorMatcher.group(1))
                    .timestamp(timestamp)
                    .source("server")
                    .build();
        }

        Matcher warnMatcher = WARN_PATTERN.matcher(rawLine);
        if (warnMatcher.find()) {
            return ConsoleMessage.builder()
                    .type("warning")
                    .message(warnMatcher.group(1))
                    .timestamp(timestamp)
                    .source("server")
                    .build();
        }

        Matcher infoMatcher = INFO_PATTERN.matcher(rawLine);
        if (infoMatcher.find()) {
            String message = infoMatcher.group(1);

            if (PLAYER_JOIN_PATTERN.matcher(message).find()) {
                return ConsoleMessage.builder()
                        .type("player_join")
                        .message("🟢 " + message)
                        .timestamp(timestamp)
                        .source("server")
                        .build();
            }

            if (PLAYER_LEAVE_PATTERN.matcher(message).find()) {
                return ConsoleMessage.builder()
                        .type("player_leave")
                        .message("🔴 " + message)
                        .timestamp(timestamp)
                        .source("server")
                        .build();
            }

            return ConsoleMessage.builder()
                    .type("info")
                    .message(message)
                    .timestamp(LocalDateTime.now())
                    .source("server")
                    .build();
        }

        return ConsoleMessage.builder()
                .type("raw")
                .message(rawLine)
                .timestamp(timestamp)
                .source("server")
                .build();
    }

    private void addToHistory(Long instanceId, ConsoleMessage message) {
        ConcurrentLinkedQueue<ConsoleMessage> history = consoleHistories.get(instanceId);
        if (history != null) {
            history.offer(message);
            while (history.size() > maxHistorySize) {
                history.poll();
            }
        }
    }

    private void loadRecentLogHistory(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        Path logFile = Paths.get(instance.getInstancePath(), "logs", "latest.log");
        try {
            if (Files.exists(logFile)) {
                List<String> lines = Files.readAllLines(logFile);
                lines.stream()
                        .skip(Math.max(0, lines.size() - 100))
                        .forEach(line -> processLogLine(instanceId, line));
            }
            log.info("Loaded {} console messages from history for instance {}", consoleHistories.get(instanceId).size(), instanceId);
        } catch (Exception e) {
            log.error("Error loading recent log history for instance {}", instanceId, e);
        }
    }

    private LocalDateTime extractTimestamp(String logLine) {
        try {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(logLine);
            if (matcher.find()) {
                int hour = Integer.parseInt(matcher.group(1));
                int minute = Integer.parseInt(matcher.group(2));
                int second = Integer.parseInt(matcher.group(3));

                return LocalDateTime.now()
                        .withHour(hour)
                        .withMinute(minute)
                        .withSecond(second)
                        .withNano(0);
            }
        } catch (Exception e) {
            log.debug("Error parsing timestamp from log line: {}", logLine);
        }

        return LocalDateTime.now();
    }

    public void clearHistory(Long instanceId) {
        ConcurrentLinkedQueue<ConsoleMessage> history = consoleHistories.get(instanceId);
        if (history != null) {
            history.clear();
        }
        log.info("Console history cleared for instance {}", instanceId);
    }

    public ConcurrentLinkedQueue<ConsoleMessage> getConsoleHistory(Long instanceId) {
        return consoleHistories.getOrDefault(instanceId, new ConcurrentLinkedQueue<>());
    }

    private ServerInstance getInstance(Long instanceId) {
        return serverInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid instanceId: " + instanceId));
    }
}
