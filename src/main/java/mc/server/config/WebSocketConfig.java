package mc.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.Slf4j;
import mc.server.service.SystemMonitoringService;
import mc.server.service.WebSocketService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketService webSocketService;
    private final SystemMonitoringService systemMonitoringService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new MinecraftConsoleWebSocketHandler(), "/ws/console/{instanceId}")
                .setAllowedOrigins("*");
        registry.addHandler(new SystemMonitoringWebSocketHandler(), "/ws/system")
                .setAllowedOrigins("*");
        registry.addHandler(new InstallationWebSocketHandler(), "/ws/install/{instanceName}")
                .setAllowedOrigins("*");
    }

    private class InstallationWebSocketHandler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            String instanceName = getInstanceName(session);
            if (instanceName != null) {
                session.getAttributes().put("instanceName", instanceName);
                log.info("Installation WebSocket connection established for instance {}: {}", instanceName, session.getId());
                // Using a temporary ID for installation messages
                webSocketService.addSession(-1L, session);
            } else {
                log.error("Could not determine instanceName from WebSocket session URI: {}", session.getUri());
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) {
            String instanceName = (String) session.getAttributes().get("instanceName");
            if (instanceName != null) {
                log.info("Installation WebSocket connection closed for instance {}: {} with status: {}", instanceName, session.getId(), status);
                webSocketService.removeSession(-1L, session);
            }
        }

        private String getInstanceName(WebSocketSession session) {
            try {
                String path = session.getUri().getPath();
                String[] segments = path.split("/");
                return segments[segments.length - 1];
            } catch (Exception e) {
                return null;
            }
        }
    }

    private class MinecraftConsoleWebSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            Long instanceId = getInstanceId(session);
            if (instanceId != null) {
                session.getAttributes().put("instanceId", instanceId);
                log.info("WebSocket connection established for instance {}: {}", instanceId, session.getId());
                webSocketService.addSession(instanceId, session);
            } else {
                log.error("Could not determine instanceId from WebSocket session URI: {}", session.getUri());
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) {
            Long instanceId = (Long) session.getAttributes().get("instanceId");
            if (instanceId != null) {
                log.info("WebSocket connection closed for instance {}: {} with status: {}", instanceId, session.getId(), status);
                webSocketService.removeSession(instanceId, session);
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            Long instanceId = (Long) session.getAttributes().get("instanceId");
            if (instanceId != null) {
                String payload = message.getPayload();
                log.debug("Received WebSocket message from {} for instance {}: {}", session.getId(), instanceId, payload);
                webSocketService.handleIncomingMessage(instanceId, session, payload);
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            Long instanceId = (Long) session.getAttributes().get("instanceId");
            if (instanceId != null) {
                log.error("WebSocket transport error for session {} for instance {}: {}", session.getId(), instanceId, exception.getMessage());
                webSocketService.removeSession(instanceId, session);
            }
        }

        private Long getInstanceId(WebSocketSession session) {
            try {
                String path = session.getUri().getPath();
                String[] segments = path.split("/");
                return Long.parseLong(segments[segments.length - 1]);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private class SystemMonitoringWebSocketHandler extends TextWebSocketHandler {

        private final Set<WebSocketSession> systemSessions = ConcurrentHashMap.newKeySet();
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            log.info("System monitoring WebSocket connection established: {}", session.getId());
            systemSessions.add(session);
            
            // Start sending periodic system stats updates
            scheduler.scheduleAtFixedRate(() -> {
                sendSystemStatsToAllSessions();
            }, 0, 5, TimeUnit.SECONDS);
        }
        
        private void sendSystemStatsToAllSessions() {
            if (systemSessions.isEmpty()) return;
            
            try {
                Map<String, Object> systemStats = systemMonitoringService.getSystemStats();
                Map<String, Object> jvmStats = systemMonitoringService.getJvmStats();
                
                Map<String, Object> allStats = Map.of(
                    "type", "system-stats",
                    "data", Map.of(
                        "system", systemStats,
                        "jvm", jvmStats
                    )
                );
                
                String jsonData = objectMapper.writeValueAsString(allStats);
                TextMessage message = new TextMessage(jsonData);
                
                // Send to all active sessions
                systemSessions.removeIf(session -> {
                    if (!session.isOpen()) {
                        return true; // Remove closed sessions
                    }
                    try {
                        session.sendMessage(message);
                        return false;
                    } catch (Exception e) {
                        log.error("Error sending system stats to WebSocket session {}: {}", session.getId(), e.getMessage());
                        return true; // Remove problematic sessions
                    }
                });
                
            } catch (Exception e) {
                log.error("Error preparing system stats for WebSocket: {}", e.getMessage());
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) {
            log.info("System monitoring WebSocket connection closed: {} with status: {}", session.getId(), status);
            systemSessions.remove(session);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.error("System monitoring WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
            systemSessions.remove(session);
        }
    }
}