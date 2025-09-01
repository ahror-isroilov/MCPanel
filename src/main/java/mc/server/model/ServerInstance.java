package mc.server.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class ServerInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String instancePath;
    private String jarFileName;
    private String version;
    private String serverType;
    private String templateId;
    private String ip;
    private int port;
    private int rconPort;
    private String rconPassword;
    private boolean rconEnabled;
    private Integer pid;
    
    @Enumerated(EnumType.STRING)
    private InstallationStatus status;
    
    @Column(length = 512)
    private String statusMessage;
    
    private String allocatedMemory;
}