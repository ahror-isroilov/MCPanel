package mc.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.Slf4j;
import mc.server.dto.WebSocketCommandMessage;
import mc.server.dto.WebSocketMessage;
import mc.server.dto.WebSocketResponse;
import mc.server.model.ConsoleMessage;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Service
public class WebSocketService {
    private final Map<Long, CopyOnWriteArraySet<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private MinecraftServerService minecraftServerService;
    private final ApplicationContext applicationContext;

    public WebSocketService(ObjectMapper objectMapper, ApplicationContext applicationContext) {
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
    }

    @Autowired
    public void setMinecraftServerService(@Lazy MinecraftServerService minecraftServerService) {
        this.minecraftServerService = minecraftServerService;
    }

    public void addSession(Long instanceId, WebSocketSession session) {
        sessions.computeIfAbsent(instanceId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("WebSocket session added for instance {}: {}. Total sessions for instance: {}", instanceId, session.getId(), sessions.get(instanceId).size());

        String welcomeText = instanceId == -1L ? "WebSocket connected for installation." : "WebSocket connected successfully to instance " + instanceId;
        ConsoleMessage welcomeMessage = ConsoleMessage.info(welcomeText);
        sendMessageToSession(session, welcomeMessage);

        if (instanceId != -1L) {
            sendRecentConsoleHistory(instanceId, session);
        }
    }

    public void removeSession(Long instanceId, WebSocketSession session) {
        CopyOnWriteArraySet<WebSocketSession> instanceSessions = sessions.get(instanceId);
        if (instanceSessions != null) {
            instanceSessions.remove(session);
            log.info("WebSocket session removed for instance {}: {}. Total sessions for instance: {}", instanceId, session.getId(), instanceSessions.size());
        }
    }

    public void handleIncomingMessage(Long instanceId, WebSocketSession session, String payload) {
        try {
            WebSocketMessage message = objectMapper.readValue(payload, WebSocketMessage.class);

            switch (message.getType()) {
                case "command":
                    WebSocketCommandMessage commandMsg = (WebSocketCommandMessage) message;
                    handleCommand(instanceId, commandMsg.getMessage());
                    break;
                case "request_status":
                    sendServerStatus(instanceId, session);
                    break;
                case "request_history":
                    sendRecentConsoleHistory(instanceId, session);
                    break;
                default:
                    log.warn("Unknown message type for instance {}: {}", instanceId, message.getType());
            }
        } catch (Exception e) {
            log.error("Error handling incoming WebSocket message for instance {}: {}", instanceId, e.getMessage(), e);
        }
    }

    public void broadcastConsoleMessage(Long instanceId, ConsoleMessage message) {
        broadcastMessage(instanceId, "console", message);
    }

    public void broadcastServerStatus(Long instanceId, Object status) {
        broadcastMessage(instanceId, "status", status);
    }

    private void broadcastMessage(Long instanceId, String type, Object data) {
        WebSocketResponse<Object> response = WebSocketResponse.create(type, data);
        String json;
        try {
            json = objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Error serializing message for instance {}", instanceId, e);
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        CopyOnWriteArraySet<WebSocketSession> instanceSessions = sessions.get(instanceId);

        if (instanceSessions != null) {
            instanceSessions.removeIf(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                        return false;
                    } else {
                        log.debug("Removing closed session for instance {}: {}", instanceId, session.getId());
                        return true;
                    }
                } catch (IOException e) {
                    log.error("Error sending message to session {} for instance {}: {}", session.getId(), instanceId, e.getMessage());
                    return true;
                }
            });
        }
    }

    private void sendMessageToSession(WebSocketSession session, ConsoleMessage message) {
        WebSocketResponse<ConsoleMessage> response = WebSocketResponse.create("console", message);
        try {
            String json = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendServerStatus(Long instanceId, WebSocketSession session) {
        try {
            Object status = minecraftServerService.getServerStatus(instanceId);
            WebSocketResponse<Object> response = WebSocketResponse.create("status", status);

            String json = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Error sending server status to session {} for instance {}: {}", session.getId(), instanceId, e.getMessage());
        }
    }

    private void sendRecentConsoleHistory(Long instanceId, WebSocketSession session) {
        try {
            LogMonitoringService logService = applicationContext.getBean(LogMonitoringService.class);
            List<ConsoleMessage> history = logService.getConsoleHistory(instanceId)
                    .stream()
                    .skip(Math.max(0, logService.getConsoleHistory(instanceId).size() - 50))
                    .toList();

            WebSocketResponse<List<ConsoleMessage>> response = WebSocketResponse.create("history", history);

            String json = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(json));
            log.debug("Sent {} console history messages to session {} for instance {}", history.size(), session.getId(), instanceId);
        } catch (Exception e) {
            log.error("Error sending console history to session {} for instance {}: {}", session.getId(), instanceId, e.getMessage());
        }
    }

    private void handleCommand(Long instanceId, String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }

        log.info("Executing command from WebSocket for instance {}: {}", instanceId, command);

        ConsoleMessage commandMessage = ConsoleMessage.command("> " + command);
        broadcastConsoleMessage(instanceId, commandMessage);

        minecraftServerService.sendCommand(instanceId, command)
                .thenAccept(success -> {
                    if (!success) {
                        ConsoleMessage errorMessage = ConsoleMessage.info("Failed to execute command: " + command);
                        broadcastConsoleMessage(instanceId, errorMessage);
                    }
                });
    }

    public int getActiveSessionCount() {
        return sessions.values().stream().mapToInt(CopyOnWriteArraySet::size).sum();
    }

    public boolean hasActiveSessions() {
        return !sessions.isEmpty();
    }
}
