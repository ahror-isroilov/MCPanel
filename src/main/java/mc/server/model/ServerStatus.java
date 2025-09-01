package mc.server.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServerStatus {
    private Long instanceId;
    private String name;
    private String serverType;
    private int port;
    private boolean online;
    private int playersOnline;
    private int maxPlayers;
    private double cpuUsage;
    private double ramUsage;
    private double totalRam;
    private String uptime;
    private double tps;
    private LocalDateTime lastUpdated;
    private List<String> onlinePlayers;
    private List<Map<String, Object>> extendedPlayerInfo;
    private String version;
    private String worldName;

    private double diskUsage;
    private double totalDisk;
    private double networkIn;
    private double networkOut;
}