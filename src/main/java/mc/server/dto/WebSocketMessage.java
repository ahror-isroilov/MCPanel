package mc.server.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WebSocketCommandMessage.class, name = "command"),
        @JsonSubTypes.Type(value = WebSocketRequestMessage.class, name = "request_status"),
        @JsonSubTypes.Type(value = WebSocketRequestHistoryMessage.class, name = "request_history")
})
public abstract class WebSocketMessage {
    private String type;
}