package fr.playtime;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class PlaytimeRewards extends JavaPlugin {

    private PlaytimeManager manager;
    private RewardsGUI gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        manager = new PlaytimeManager(this);
        gui = new RewardsGUI(this, manager);

        getServer().getPluginManager().registerEvents(manager, this);
        getServer().getPluginManager().registerEvents(gui, this);

        PlaytimeCommand executor = new PlaytimeCommand(this, manager, gui);
        PluginCommand command = getCommand("playtime");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        ShardsCommand shardsExecutor = new ShardsCommand(this, manager);
        PluginCommand shardsCommand = getCommand("shards");
        if (shardsCommand != null) {
            shardsCommand.setExecutor(shardsExecutor);
            shardsCommand.setTabCompleter(shardsExecutor);
        }

        // Tick toutes les secondes : ajoute 1s de temps de jeu aux joueurs actifs
        Bukkit.getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);
        // Sauvegarde automatique toutes les 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, manager::save, 6000L, 6000L);

        getLogger().info("PlaytimeRewards activé : " + manager.getRewards().size() + " récompense(s) chargée(s).");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.save();
        }
    }

    public void reload() {
        reloadConfig();
        manager.loadRewards();
    }

    /** Message de la config, avec préfixe et couleurs. Les paires sont clé/valeur de remplacement. */
    public String msg(String key, String... replacements) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return color(getConfig().getString("messages.prefix", "") + text);
    }

    /** Message de la config sans préfixe (pour les listes, ex: top). */
    public String rawMsg(String key, String... replacements) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return color(text);
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
