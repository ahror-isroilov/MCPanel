package mc.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommandRequest {

    @NotBlank(message = "Command cannot be empty")
    @Size(max = 256, message = "Command too long")
    private String command;
}