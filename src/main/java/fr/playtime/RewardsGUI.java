package fr.playtime;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RewardsGUI implements Listener {

    /** Holder dédié pour reconnaître notre inventaire. */
    private static class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final int MAX_REWARDS = 45;

    private final PlaytimeRewards plugin;
    private final PlaytimeManager manager;

    public RewardsGUI(PlaytimeRewards plugin, PlaytimeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(Player p) {
        List<PlaytimeManager.Reward> rewards = manager.getRewards();
        int shown = Math.min(rewards.size(), MAX_REWARDS);
        int rows = Math.min(6, (int) Math.ceil(shown / 9.0) + 1);
        int size = rows * 9;

        Inventory inv = Bukkit.createInventory(new Holder(), size,
                PlaytimeRewards.color(plugin.getConfig().getString("gui.title", "&8Récompenses")));

        long playerTime = manager.getTime(p.getUniqueId());

        for (int i = 0; i < shown; i++) {
            inv.setItem(i, buildRewardItem(p, rewards.get(i), playerTime));
        }

        inv.setItem(size - 5, buildInfoItem(p, playerTime));
        inv.setItem(size - 1, buildClaimAllItem(p));

        p.openInventory(inv);
    }

    private ItemStack buildRewardItem(Player p, PlaytimeManager.Reward r, long playerTime) {
        boolean claimed = manager.isClaimed(p.getUniqueId(), r);
        boolean claimable = !claimed && playerTime >= r.seconds;

        Material mat = r.icon;
        if (claimed) {
            Material m = Material.matchMaterial(plugin.getConfig().getString("gui.claimed-material", "LIME_STAINED_GLASS_PANE"));
            mat = m != null ? m : Material.LIME_STAINED_GLASS_PANE;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(PlaytimeRewards.color(r.name));

        List<String> lore = new ArrayList<>();
        for (String line : r.description) {
            lore.add(PlaytimeRewards.color(line));
        }
        lore.add("");
        lore.add(PlaytimeRewards.color(plugin.getConfig().getString("lore.required", "&7Temps requis : &f%time%")
                .replace("%time%", PlaytimeManager.format(r.seconds))));

        if (claimed) {
            lore.add(PlaytimeRewards.color(plugin.getConfig().getString("lore.status-claimed", "&7Déjà récupéré")));
        } else if (claimable) {
            lore.add(PlaytimeRewards.color(plugin.getConfig().getString("lore.status-claimable", "&aClique pour récupérer !")));
            addGlow(meta);
        } else {
            lore.add(PlaytimeRewards.color(plugin.getConfig().getString("lore.remaining", "&7Il te reste : &e%time%")
                    .replace("%time%", PlaytimeManager.format(r.seconds - playerTime))));
            lore.add(PlaytimeRewards.color(plugin.getConfig().getString("lore.status-locked", "&cVerrouillé")));
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildInfoItem(Player p, long playerTime) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PlaytimeRewards.color(plugin.getConfig().getString("gui.info-name", "&eTon temps de jeu")));
            List<String> lore = new ArrayList<>();
            lore.add(PlaytimeRewards.color("&a" + PlaytimeManager.format(playerTime)));
            lore.add(PlaytimeRewards.color("&b" + manager.getShards(p.getUniqueId()) + " shards"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildClaimAllItem(Player p) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PlaytimeRewards.color(plugin.getConfig().getString("gui.claim-all-name", "&aTout récupérer")));
            List<String> lore = new ArrayList<>();
            lore.add(PlaytimeRewards.color("&7" + manager.countClaimable(p.getUniqueId()) + " disponible(s)"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Effet brillant compatible toutes versions (unbreaking par clé, masqué). */
    private void addGlow(ItemMeta meta) {
        Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (ench != null) {
            meta.addEnchant(ench, 1, true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        if (e.getClickedInventory() == null || !(e.getClickedInventory().getHolder() instanceof Holder)) {
            return;
        }

        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        int size = e.getInventory().getSize();

        if (slot == size - 1) {
            int count = manager.claimAll(p);
            if (count == 0) {
                p.sendMessage(plugin.msg("nothing-to-claim"));
            } else {
                p.sendMessage(plugin.msg("claimed-count", "%count%", String.valueOf(count)));
            }
            open(p);
            return;
        }

        List<PlaytimeManager.Reward> rewards = manager.getRewards();
        if (slot >= 0 && slot < rewards.size() && slot < MAX_REWARDS) {
            if (manager.claim(p, rewards.get(slot))) {
                open(p);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
