package mc.server.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketResponse<T> {
    private String type;
    private T data;
    private Long timestamp;
    
    public static <T> WebSocketResponse<T> create(String type, T data) {
        return WebSocketResponse.<T>builder()
                .type(type)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}