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

public class ShardsCommand implements CommandExecutor, TabCompleter {

    private final PlaytimeRewards plugin;
    private final PlaytimeManager manager;

    public ShardsCommand(PlaytimeRewards plugin, PlaytimeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.msg("usage-shards"));
                return true;
            }
            Player p = (Player) sender;
            p.sendMessage(plugin.msg("shards-balance", "%shards%", String.valueOf(manager.getShards(p.getUniqueId()))));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "top":
                return top(sender);
            case "give":
            case "add":
            case "take":
            case "remove":
            case "set":
                return admin(sender, sub, args);
            default:
                return showOther(sender, args[0]);
        }
    }

    private boolean top(CommandSender sender) {
        List<Map.Entry<UUID, Long>> entries = new ArrayList<>(manager.getAllShards().entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        sender.sendMessage(plugin.rawMsg("shards-top-header"));
        int rank = 1;
        for (Map.Entry<UUID, Long> e : entries) {
            if (rank > 10) {
                break;
            }
            String name = manager.getName(e.getKey());
            if (name == null) {
                name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            }
            sender.sendMessage(plugin.rawMsg("shards-top-line",
                    "%rank%", String.valueOf(rank),
                    "%player%", name == null ? "?" : name,
                    "%shards%", String.valueOf(e.getValue())));
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
        sender.sendMessage(plugin.msg("shards-other",
                "%player%", display,
                "%shards%", String.valueOf(manager.getShards(id))));
        return true;
    }

    private boolean admin(CommandSender sender, String action, String[] args) {
        if (!sender.hasPermission("playtime.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("usage-shards-admin"));
            return true;
        }
        UUID id = findPlayer(args[1]);
        if (id == null) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.msg("invalid-number"));
            return true;
        }
        if (amount < 0) {
            sender.sendMessage(plugin.msg("invalid-number"));
            return true;
        }

        String name = manager.getName(id) != null ? manager.getName(id) : args[1];
        switch (action) {
            case "give":
            case "add":
                manager.addShards(id, name, amount);
                break;
            case "take":
            case "remove":
                manager.addShards(id, name, -amount);
                break;
            default:
                manager.setShards(id, name, amount);
                break;
        }
        sender.sendMessage(plugin.msg("shards-admin-done",
                "%player%", name,
                "%shards%", String.valueOf(manager.getShards(id))));
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
        boolean admin = sender.hasPermission("playtime.admin");
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("top"));
            if (admin) {
                subs.addAll(Arrays.asList("give", "take", "set"));
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
        } else if (args.length == 2 && admin
                && Arrays.asList("give", "take", "set", "add", "remove").contains(args[0].toLowerCase())) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
