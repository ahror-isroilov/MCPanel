package mc.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsoleMessage {
    private String type; // "info", "warning", "error", "command"
    private String message;
    private LocalDateTime timestamp;
    private String source; // "server", "admin", "system"

    public static ConsoleMessage info(String message) {
        return ConsoleMessage.builder()
                .type("info")
                .message(message)
                .timestamp(LocalDateTime.now())
                .source("system")
                .build();
    }

    public static ConsoleMessage command(String message) {
        return ConsoleMessage.builder()
                .type("command")
                .message(message)
                .timestamp(LocalDateTime.now())
                .source("admin")
                .build();
    }

    public static ConsoleMessage serverOutput(String message) {
        return ConsoleMessage.builder()
                .type("info")
                .message(message)
                .timestamp(LocalDateTime.now())
                .source("server")
                .build();
    }

    public static ConsoleMessage error(String message) {
        return ConsoleMessage.builder()
                .type("error")
                .message(message)
                .timestamp(LocalDateTime.now())
                .source("system")
                .build();
    }
}