package fr.playtime;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaytimeManager implements Listener {

    public static class Reward {
        public final String id;
        public final long seconds;
        public final String name;
        public final Material icon;
        public final List<String> description;
        public final List<String> commands;

        public Reward(String id, long seconds, String name, Material icon,
                      List<String> description, List<String> commands) {
            this.id = id;
            this.seconds = seconds;
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.commands = commands;
        }
    }

    private static final Pattern TIME_PART = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);

    private final PlaytimeRewards plugin;
    private final File dataFile;

    private final Map<UUID, Long> time = new HashMap<>();
    private final Map<UUID, Set<String>> claimed = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final List<Reward> rewards = new ArrayList<>();
    private final Map<UUID, Long> shards = new HashMap<>();
    private long shardInterval = 0;
    private long shardAmount = 1;

    public PlaytimeManager(PlaytimeRewards plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        loadRewards();
        loadData();
    }

    // ------------------------------------------------------------------
    // Chargement / sauvegarde
    // ------------------------------------------------------------------

    public void loadRewards() {
        rewards.clear();
        shardInterval = Math.max(0L, parseTime(plugin.getConfig().getString("shards.interval", "10m")));
        shardAmount = Math.max(0L, plugin.getConfig().getLong("shards.amount", 1L));
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("rewards");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(id);
            if (r == null) {
                continue;
            }
            long seconds = parseTime(r.getString("time", "1h"));
            if (seconds <= 0) {
                plugin.getLogger().warning("Récompense '" + id + "' ignorée : temps invalide.");
                continue;
            }
            Material icon = Material.matchMaterial(r.getString("icon", "CHEST"));
            if (icon == null) {
                plugin.getLogger().warning("Récompense '" + id + "' : icône inconnue, CHEST utilisé.");
                icon = Material.CHEST;
            }
            rewards.add(new Reward(
                    id,
                    seconds,
                    r.getString("name", id),
                    icon,
                    r.getStringList("description"),
                    r.getStringList("commands")));
        }
        rewards.sort((a, b) -> Long.compare(a.seconds, b.seconds));
    }

    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                time.put(id, players.getLong(key + ".time", 0L));
                shards.put(id, players.getLong(key + ".shards", 0L));
                claimed.put(id, new HashSet<>(players.getStringList(key + ".claimed")));
                String name = players.getString(key + ".name");
                if (name != null) {
                    names.put(id, name);
                }
            } catch (IllegalArgumentException ignored) {
                // UUID invalide : on saute l'entrée
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : time.entrySet()) {
            String base = "players." + e.getKey();
            yaml.set(base + ".time", e.getValue());
            yaml.set(base + ".shards", shards.getOrDefault(e.getKey(), 0L));
            yaml.set(base + ".name", names.get(e.getKey()));
            yaml.set(base + ".claimed", new ArrayList<>(claimed.getOrDefault(e.getKey(), new HashSet<>())));
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder data.yml : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Temps de jeu
    // ------------------------------------------------------------------

    public void tick() {
        long now = System.currentTimeMillis();
        long afkMs = plugin.getConfig().getLong("afk-timeout-seconds", 300) * 1000L;

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (afkMs > 0 && now - lastActivity.getOrDefault(id, now) > afkMs) {
                continue;
            }
            long total = time.merge(id, 1L, Long::sum);
            names.put(id, p.getName());

            // Shards : gagnés à intervalle régulier de temps de jeu actif
            if (shardInterval > 0 && shardAmount > 0 && total % shardInterval == 0) {
                long balance = addShards(id, p.getName(), shardAmount);
                if (plugin.getConfig().getBoolean("shards.notify", true)) {
                    p.sendMessage(plugin.msg("shard-earned",
                            "%amount%", String.valueOf(shardAmount),
                            "%total%", String.valueOf(balance)));
                }
            }

            for (Reward r : rewards) {
                if (r.seconds == total) {
                    onReached(p, r);
                }
            }
        }
    }

    private void onReached(Player p, Reward r) {
        if (plugin.getConfig().getBoolean("auto-claim", false)) {
            claim(p, r);
            return;
        }
        p.sendMessage(plugin.msg("reward-unlocked", "%reward%", PlaytimeRewards.color(r.name)));
        p.playSound(p.getLocation(), "entity.player.levelup", 1f, 1f);
    }

    public long getTime(UUID id) {
        return time.getOrDefault(id, 0L);
    }

    public void setTime(UUID id, String name, long seconds) {
        time.put(id, Math.max(0L, seconds));
        if (name != null) {
            names.put(id, name);
        }
    }

    public void reset(UUID id) {
        time.put(id, 0L);
        claimed.remove(id);
    }

    // --- Shards ---

    public long getShards(UUID id) {
        return shards.getOrDefault(id, 0L);
    }

    /** Ajoute (ou retire si négatif) des shards. Retourne la nouvelle balance (jamais < 0). */
    public long addShards(UUID id, String name, long amount) {
        long value = Math.max(0L, getShards(id) + amount);
        shards.put(id, value);
        time.putIfAbsent(id, 0L);
        if (name != null) {
            names.put(id, name);
        }
        return value;
    }

    public void setShards(UUID id, String name, long amount) {
        shards.put(id, Math.max(0L, amount));
        time.putIfAbsent(id, 0L);
        if (name != null) {
            names.put(id, name);
        }
    }

    public Map<UUID, Long> getAllShards() {
        return shards;
    }

    public String getName(UUID id) {
        return names.get(id);
    }

    public Map<UUID, Long> getAllTimes() {
        return time;
    }

    public List<Reward> getRewards() {
        return rewards;
    }

    // ------------------------------------------------------------------
    // Récompenses
    // ------------------------------------------------------------------

    public boolean isClaimed(UUID id, Reward r) {
        return claimed.getOrDefault(id, new HashSet<>()).contains(r.id);
    }

    public boolean isClaimable(UUID id, Reward r) {
        return getTime(id) >= r.seconds && !isClaimed(id, r);
    }

    public int countClaimable(UUID id) {
        int n = 0;
        for (Reward r : rewards) {
            if (isClaimable(id, r)) {
                n++;
            }
        }
        return n;
    }

    public boolean claim(Player p, Reward r) {
        UUID id = p.getUniqueId();
        if (!isClaimable(id, r)) {
            return false;
        }
        claimed.computeIfAbsent(id, k -> new HashSet<>()).add(r.id);

        for (String raw : r.commands) {
            String cmd = raw.replace("%player%", p.getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            // "broadcast" : message global sans dépendre d'un autre plugin
            if (cmd.toLowerCase().startsWith("broadcast ")) {
                Bukkit.broadcastMessage(PlaytimeRewards.color(cmd.substring("broadcast ".length())));
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        p.sendMessage(plugin.msg("reward-claimed", "%reward%", PlaytimeRewards.color(r.name)));
        p.playSound(p.getLocation(), "entity.experience_orb.pickup", 1f, 1f);
        return true;
    }

    public int claimAll(Player p) {
        int count = 0;
        for (Reward r : rewards) {
            if (claim(p, r)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Formats de temps
    // ------------------------------------------------------------------

    /** "1d12h30m" -> secondes. Un nombre seul = minutes. Retourne -1 si invalide. */
    public static long parseTime(String input) {
        if (input == null || input.trim().isEmpty()) {
            return -1;
        }
        String s = input.trim();
        if (s.matches("\\d+")) {
            return Long.parseLong(s) * 60L;
        }
        Matcher m = TIME_PART.matcher(s);
        long total = 0;
        int consumed = 0;
        while (m.find()) {
            long value = Long.parseLong(m.group(1));
            switch (Character.toLowerCase(m.group(2).charAt(0))) {
                case 'd': total += value * 86400L; break;
                case 'h': total += value * 3600L; break;
                case 'm': total += value * 60L; break;
                default: total += value; break;
            }
            consumed += m.group(0).length();
        }
        return consumed == s.replace(" ", "").length() ? total : -1;
    }

    /** secondes -> "1j 2h 3m 4s" */
    public static String format(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("j ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 && d == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------
    // Détection d'activité (anti-AFK)
    // ------------------------------------------------------------------

    private void touch(Player p) {
        lastActivity.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) {
            return;
        }
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            touch(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        touch(p);
        names.put(p.getUniqueId(), p.getName());
        time.putIfAbsent(p.getUniqueId(), 0L);

        int pending = countClaimable(p.getUniqueId());
        if (pending > 0 && !plugin.getConfig().getBoolean("auto-claim", false)) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> p.sendMessage(plugin.msg("join-pending", "%count%", String.valueOf(pending))), 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastActivity.remove(e.getPlayer().getUniqueId());
    }
}
