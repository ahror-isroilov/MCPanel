package mc.server.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class WebSocketRequestMessage extends WebSocketMessage {
    
    public WebSocketRequestMessage(String type) {
        super(type);
    }
}