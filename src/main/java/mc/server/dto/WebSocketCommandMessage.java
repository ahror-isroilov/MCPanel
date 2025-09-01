package mc.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class WebSocketCommandMessage extends WebSocketMessage {
    
    @NotBlank(message = "Message cannot be empty")
    @Size(max = 256, message = "Message too long")
    private String message;
    
    public WebSocketCommandMessage(String type, String message) {
        super(type);
        this.message = message;
    }
    
    public WebSocketCommandMessage(String message) {
        this.message = message;
    }
}