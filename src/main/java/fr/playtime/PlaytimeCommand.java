package fr.playtime;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlaytimeCommand implements CommandExecutor, TabCompleter {

    private final PlaytimeRewards plugin;
    private final PlaytimeManager manager;
    private final RewardsGUI gui;

    public PlaytimeCommand(PlaytimeRewards plugin, PlaytimeManager manager, RewardsGUI gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.msg("usage"));
                return true;
            }
            Player p = (Player) sender;
            p.sendMessage(plugin.msg("own-time", "%time%", PlaytimeManager.format(manager.getTime(p.getUniqueId()))));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "rewards":
            case "recompenses":
            case "récompenses":
            case "r":
                return openRewards(sender);
            case "claim":
            case "reclamer":
            case "récupérer":
                return claim(sender);
            case "top":
                return top(sender);
            case "reload":
                if (!sender.hasPermission("playtime.admin")) {
                    sender.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(plugin.msg("reloaded"));
                return true;
            case "admin":
                return admin(sender, args);
            default:
                return showOther(sender, args[0]);
        }
    }

    private boolean openRewards(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!sender.hasPermission("playtime.use")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        gui.open((Player) sender);
        return true;
    }

    private boolean claim(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        Player p = (Player) sender;
        int count = manager.claimAll(p);
        if (count == 0) {
            p.sendMessage(plugin.msg("nothing-to-claim"));
        } else {
            p.sendMessage(plugin.msg("claimed-count", "%count%", String.valueOf(count)));
        }
        return true;
    }

    private boolean top(CommandSender sender) {
        List<Map.Entry<UUID, Long>> entries = new ArrayList<>(manager.getAllTimes().entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        sender.sendMessage(plugin.rawMsg("top-header"));
        int rank = 1;
        for (Map.Entry<UUID, Long> e : entries) {
            if (rank > 10) {
                break;
            }
            String name = manager.getName(e.getKey());
            if (name == null) {
                name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            }
            if (name == null) {
                name = "?";
            }
            sender.sendMessage(plugin.rawMsg("top-line",
                    "%rank%", String.valueOf(rank),
                    "%player%", name,
                    "%time%", PlaytimeManager.format(e.getValue())));
            rank++;
        }
        return true;
    }

    private boolean showOther(CommandSender sender, String name) {
        if (!sender.hasPermission("playtime.others")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        UUID id = findPlayer(name);
        if (id == null) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return true;
        }
        String display = manager.getName(id) != null ? manager.getName(id) : name;
        sender.sendMessage(plugin.msg("other-time",
                "%player%", display,
                "%time%", PlaytimeManager.format(manager.getTime(id))));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playtime.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("usage-admin"));
            return true;
        }
        String action = args[1].toLowerCase();
        UUID id = findPlayer(args[2]);
        if (id == null) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return true;
        }
        String name = manager.getName(id) != null ? manager.getName(id) : args[2];

        if (action.equals("reset")) {
            manager.reset(id);
            sender.sendMessage(plugin.msg("admin-done", "%player%", name, "%time%", "0s"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(plugin.msg("usage-admin"));
            return true;
        }
        long seconds = PlaytimeManager.parseTime(args[3]);
        if (seconds < 0) {
            sender.sendMessage(plugin.msg("invalid-time"));
            return true;
        }

        long current = manager.getTime(id);
        switch (action) {
            case "set":
                manager.setTime(id, name, seconds);
                break;
            case "add":
                manager.setTime(id, name, current + seconds);
                break;
            case "remove":
                manager.setTime(id, name, current - seconds);
                break;
            default:
                sender.sendMessage(plugin.msg("usage-admin"));
                return true;
        }
        sender.sendMessage(plugin.msg("admin-done",
                "%player%", name,
                "%time%", PlaytimeManager.format(manager.getTime(id))));
        return true;
    }

    @SuppressWarnings("deprecation")
    private UUID findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (Map.Entry<UUID, Long> e : manager.getAllTimes().entrySet()) {
            String known = manager.getName(e.getKey());
            if (known != null && known.equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off.hasPlayedBefore() ? off.getUniqueId() : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("rewards", "claim", "top"));
            if (sender.hasPermission("playtime.admin")) {
                subs.add("admin");
                subs.add("reload");
            }
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("playtime.admin")) {
            for (String s : Arrays.asList("set", "add", "remove", "reset")) {
                if (s.startsWith(args[1].toLowerCase())) {
                    out.add(s);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("playtime.admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
