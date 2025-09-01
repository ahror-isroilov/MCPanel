package mc.server.service;

import java.util.regex.Pattern;

public interface LogPatterns {
    Pattern INFO_PATTERN = Pattern.compile("\\[\\d{2}:\\d{2}:\\d{2}\\] \\[.*?/INFO\\]: (.+)");
    Pattern WARN_PATTERN = Pattern.compile("\\[\\d{2}:\\d{2}:\\d{2}\\] \\[.*?/WARN\\]: (.+)");
    Pattern ERROR_PATTERN = Pattern.compile("\\[\\d{2}:\\d{2}:\\d{2}\\] \\[.*?/ERROR\\]: (.+)");

    // Player events
    Pattern PLAYER_JOIN_PATTERN = Pattern.compile("(.+) joined the game");
    Pattern PLAYER_LEAVE_PATTERN = Pattern.compile("(.+) left the game");
    Pattern CHAT_PATTERN = Pattern.compile("<(.+)> (.+)");

    // Death events
    Pattern PLAYER_DEATH_PATTERN = Pattern.compile("(.+) (was|died|drowned|burned|fell|was blown up|was shot|was killed|suffocated|starved|was struck|was pummeled|was slain|withered|was squished|experienced kinetic energy|went up in flames|discovered|tried to swim|was doomed|was impaled|was skewered|was roasted|walked into fire|went off with a bang|hit the ground|was pricked|froze|was stung)(.*)");

    // Achievement/Advancement events
    Pattern ADVANCEMENT_PATTERN = Pattern.compile("(.+) has (made the advancement|completed the challenge|reached the goal) \\[(.+)\\]");

    // Server events
    Pattern SERVER_START_PATTERN = Pattern.compile("Done \\((.+)s\\)! For help, type \"help\"");
    Pattern SERVER_STOP_PATTERN = Pattern.compile("Stopping server");
    Pattern SAVE_COMPLETE_PATTERN = Pattern.compile("Saved the game");

    // Command events
    Pattern COMMAND_PATTERN = Pattern.compile("\\[(.+): (.+)\\]");

    // World events
    Pattern TIME_SET_PATTERN = Pattern.compile("Set the time to (.+)");
    Pattern WEATHER_PATTERN = Pattern.compile("(Changing|Set) (?:the )?weather to (.+)");

    // Teleportation events
    Pattern TELEPORT_PATTERN = Pattern.compile("Teleported (.+) to (.+)");

    // Gamemode events
    Pattern GAMEMODE_PATTERN = Pattern.compile("Set (.+)'s game mode to (.+)");

    // Item/Block events
    Pattern GIVE_PATTERN = Pattern.compile("Gave (.+) \\* (.+) to (.+)");

    // Patterns for messages to filter out (spam)
    Pattern RCON_THREAD_PATTERN = Pattern.compile("Thread RCON Client .* (started|shutting down)");
    Pattern RCON_CONNECTION_PATTERN = Pattern.compile("RCON running on.*");
    Pattern UUID_LOOKUP_PATTERN = Pattern.compile("UUID of player .* is .*");

    // Pattern to extract timestamp from log line
    Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2}):(\\d{2})\\]");

    Pattern LIST_PATTERN = Pattern.compile("There are (\\d+) of a max of (\\d+) players online:?\\s*(.*)");
    Pattern SEED_PATTERN = Pattern.compile("Seed: \\[(-?\\d+)\\]");
    Pattern DEBUG_STOP_PATTERN = Pattern.compile("Stopped debug profiling after ([0-9.]+) seconds \\(([0-9.]+) ticks\\)");

    // In MinecraftServerService.java, with the other PATTERN constants
    Pattern VERSION_PATTERN = Pattern.compile("(Paper|Spigot|CraftBukkit|Forge|Fabric|Vanilla) version ([\\d.]+)-");
    Pattern TPS_PATTERN = Pattern.compile("TPS from last 1m, 5m, 15m: \\*?(\\d+\\.\\d+),");

}
