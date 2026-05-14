package com.maris7.tpa;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MarisTpaPlugin extends JavaPlugin implements Listener, CommandExecutor {
    // Short-lived request and teleport countdown state stays in RAM only.
    // Persistent per-player settings, like tpauto, are cached in RAM and saved to data/players.yml when changed.
    private final Map<UUID, TpaRequest> byTarget = new ConcurrentHashMap<>();
    private final Map<UUID, TpaRequest> byRequester = new ConcurrentHashMap<>();
    private final Set<UUID> tpauto = ConcurrentHashMap.newKeySet();
    private final Set<UUID> teleporting = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Scheduler.TaskHandle> tpautoActionbars = new ConcurrentHashMap<>();
    private YamlConfiguration players, messages, gui;
    private File playersFile;
    private final AtomicBoolean playersDirty = new AtomicBoolean(false);
    private SettingsHook settingsHook;

    @Override public void onEnable() {
        mergeResourceDefaults("config.yml");
        reloadConfig();
        mergeResourceDefaults("message.yml");
        mergeResourceDefaults("gui.yml");
        File dataDir = new File(getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        playersFile = new File(dataDir, "players.yml");
        createYaml(playersFile, "players");
        players = YamlConfiguration.loadConfiguration(playersFile);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "message.yml"));
        gui = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "gui.yml"));
        settingsHook = new SettingsHook(this);
        loadPlayerSettings();
        for (String cmd : List.of("tpa", "tpahere", "tpaccept", "tpacancel", "tpdeny", "tpauto")) Objects.requireNonNull(getCommand(cmd)).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getOnlinePlayers().forEach(player -> {
            enforceTpautoWorld(player);
            startTpautoActionbar(player);
        });
    }


    @Override public void onDisable() {
        savePlayersIfDirty();
        tpautoActionbars.values().forEach(Scheduler.TaskHandle::cancel);
        tpautoActionbars.clear();
        byTarget.clear();
        byRequester.clear();
        teleporting.clear();
    }

    private void createYaml(File file, String root) {
        if (file.exists()) return;
        try { file.createNewFile(); YamlConfiguration y = new YamlConfiguration(); y.set(root, new LinkedHashMap<>()); y.save(file); } catch (IOException e) { getLogger().warning(e.getMessage()); }
    }

    private void mergeResourceDefaults(String resourceName) {
        File file = new File(getDataFolder(), resourceName);
        if (!file.exists()) {
            saveResource(resourceName, false);
            return;
        }
        try (InputStream in = getResource(resourceName)) {
            if (in == null) return;
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            current.setDefaults(defaults);
            current.options().copyDefaults(true);
            current.save(file);
        } catch (IOException e) {
            getLogger().warning("Failed to merge defaults into " + resourceName + ": " + e.getMessage());
        }
    }

    private void loadPlayerSettings() {
        if (!players.isConfigurationSection("players")) return;
        for (String key : players.getConfigurationSection("players").getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException ignored) { continue; }
            if (players.getBoolean("players." + key + ".tpauto", false)) tpauto.add(id);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa" -> { if (args.length < 1) return true; Player t = Bukkit.getPlayerExact(args[0]); if (t == null) return true; if (t.getUniqueId().equals(p.getUniqueId())) { no(p); return true; } if (isTpaGuiDisabled(p.getUniqueId())) sendRequest(p, t, TpaRequest.Type.TPA); else openSendGui(p, t, TpaRequest.Type.TPA); }
            case "tpahere" -> { if (args.length < 1) return true; Player t = Bukkit.getPlayerExact(args[0]); if (t == null) return true; if (t.getUniqueId().equals(p.getUniqueId())) { no(p); return true; } if (isTpaGuiDisabled(p.getUniqueId())) sendRequest(p, t, TpaRequest.Type.TPAHERE); else openSendGui(p, t, TpaRequest.Type.TPAHERE); }
            case "tpaccept" -> acceptCommand(p, args);
            case "tpacancel" -> cancelRequest(p);
            case "tpdeny", "tpadeny" -> denyRequest(p, args);
            case "tpauto" -> toggleTpauto(p);
        }
        return true;
    }

    private void openSendGui(Player viewer, Player target, TpaRequest.Type type) {
        GuiHolder holder = new GuiHolder(type == TpaRequest.Type.TPA ? GuiHolder.Action.SEND_TPA : GuiHolder.Action.SEND_TPAHERE, target.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtil.color(gui.getString("send.title", "&8Confirm request")));
        holder.setInventory(inv);
        fillCommon(inv, target, viewer, type, false);
        viewer.openInventory(inv);
    }

    private void openAcceptGui(Player accepter, Player requester, TpaRequest.Type type) {
        GuiHolder holder = new GuiHolder(GuiHolder.Action.ACCEPT, requester.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtil.color(gui.getString("accept.title", "&7Accept request")));
        holder.setInventory(inv);
        fillCommon(inv, requester, accepter, type, true);
        accepter.openInventory(inv);
    }

    private void fillCommon(Inventory inv, Player shownPlayer, Player locationPlayer, TpaRequest.Type type, boolean accept) {
        inv.setItem(gui.getInt("items.cancel.slot", 10), item(mat("items.cancel.material", Material.RED_STAINED_GLASS_PANE), g("items.cancel.name"), lore("items.cancel.lore", Map.of())));
        inv.setItem(gui.getInt("items.location.slot", 12), item(worldMaterial(locationPlayer.getWorld()), g("items.location.name"), lore("items.location.lore", Map.of("%world%", worldDisplay(locationPlayer.getWorld())))));
        inv.setItem(gui.getInt("items.player.slot", 13), skull(shownPlayer, g("items.player.name"), lore("items.player.lore", Map.of("%player%", shownPlayer.getName()))));
        inv.setItem(gui.getInt("items.region.slot", 14), item(mat("items.region.material", Material.FEATHER), g("items.region.name"), lore("items.region.lore", Map.of("%ping%", String.valueOf(shownPlayer.getPing()), "%region%", getConfig().getString("region", "Singapore")))));
        String base = accept ? "items.confirm-accept" : "items.confirm-send";
        String sub = type == TpaRequest.Type.TPA ? ".lore.tpa" : ".lore.tpahere";
        inv.setItem(gui.getInt(base + ".slot", 16), item(mat(base + ".material", Material.LIME_STAINED_GLASS_PANE), g(base + ".name"), lore(base + sub, Map.of("%player%", shownPlayer.getName()))));
    }

    private Material worldMaterial(World w) { String def = switch (w.getEnvironment()) { case NETHER -> "NETHERRACK"; case THE_END -> "END_STONE"; default -> "GRASS_BLOCK"; }; Material m = Material.matchMaterial(getConfig().getString("world-materials." + w.getName(), def)); return m == null ? Material.GRASS_BLOCK : m; }
    private String worldDisplay(World w) { return getConfig().getString("worlds." + w.getName(), w.getName()); }
    private String g(String path) { return gui.getString(path, ""); }
    private Material mat(String path, Material fallback) { Material m = Material.matchMaterial(gui.getString(path, fallback.name())); return m == null ? fallback : m; }
    private List<String> lore(String path, Map<String, String> placeholders) { List<String> list = gui.getStringList(path); if (list.isEmpty()) list = List.of(""); return list.stream().map(s -> replace(s, placeholders)).toList(); }
    private String m(String path) { return messages.getString(path, ""); }
    private String replace(String text, Map<String, String> placeholders) { String out = text; for (Map.Entry<String, String> e : placeholders.entrySet()) out = out.replace(e.getKey(), e.getValue()); return out; }

    private ItemStack item(Material material, String name, List<String> lore) { ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ColorUtil.gui(name)); meta.setLore(lore.stream().map(ColorUtil::gui).toList()); stack.setItemMeta(meta); return stack; }
    private ItemStack skull(Player owner, String name, List<String> lore) { ItemStack stack = new ItemStack(Material.PLAYER_HEAD); SkullMeta meta = (SkullMeta) stack.getItemMeta(); meta.setOwningPlayer(owner); meta.setDisplayName(ColorUtil.gui(name)); meta.setLore(lore.stream().map(ColorUtil::gui).toList()); stack.setItemMeta(meta); return stack; }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder holder) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        if (e.getRawSlot() == gui.getInt("items.cancel.slot", 10)) { p.closeInventory(); return; }
        String confirmPath = holder.action == GuiHolder.Action.ACCEPT ? "items.confirm-accept.slot" : "items.confirm-send.slot";
        if (e.getRawSlot() != gui.getInt(confirmPath, 16)) return;
        Player other = Bukkit.getPlayer(holder.other); if (other == null) { missing(p); return; }
        p.closeInventory();
        if (holder.action == GuiHolder.Action.SEND_TPA) sendRequest(p, other, TpaRequest.Type.TPA);
        else if (holder.action == GuiHolder.Action.SEND_TPAHERE) sendRequest(p, other, TpaRequest.Type.TPAHERE);
        else accept(p, other);
    }

    private void sendRequest(Player requester, Player target, TpaRequest.Type type) {
        if (requestDisabled(target, type)) {
            msgBoth(requester, m(type == TpaRequest.Type.TPA ? "tpa-disabled-target" : "tpahere-disabled-target"));
            sound(requester, Sound.ENTITY_VILLAGER_NO);
            return;
        }
        TpaRequest request = new TpaRequest(requester.getUniqueId(), target.getUniqueId(), type, System.currentTimeMillis());
        removeByRequester(requester.getUniqueId()); byTarget.put(target.getUniqueId(), request); byRequester.put(requester.getUniqueId(), request);
        sound(requester, Sound.ENTITY_PLAYER_LEVELUP); msg(requester, replace(m("request-sent"), Map.of("%player%", target.getName())));
        if (tpauto.contains(target.getUniqueId())) {
            enforceTpautoWorld(target);
            if (tpauto.contains(target.getUniqueId())) {
                accept(target, requester);
                return;
            }
        }
        TextComponent line1 = legacyComponent(replace(m("request-received"), Map.of("%player%", requester.getName())));
        TextComponent click = legacyComponent(m("request-received-click")); click.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + requester.getName()));
        TextComponent line2 = legacyComponent(" " + replace(m("request-received-type"), Map.of("%player%", requester.getName())));
        target.spigot().sendMessage(line1); target.spigot().sendMessage(click, line2); action(target, replace(m("request-received"), Map.of("%player%", requester.getName()))); sound(target, Sound.BLOCK_NOTE_BLOCK_BELL);
        Scheduler.globalLater(this, () -> { TpaRequest cur = byTarget.get(target.getUniqueId()); if (cur == request && request.expired(expireMs())) remove(request); }, expireMs() / 50);
    }

    private void acceptCommand(Player p, String[] args) { TpaRequest r = findIncoming(p, args.length > 0 ? args[0] : null); if (r == null) { missing(p); return; } Player requester = Bukkit.getPlayer(r.requester); if (requester == null) { missing(p); return; } if (isTpaGuiDisabled(p.getUniqueId())) accept(p, requester); else openAcceptGui(p, requester, r.type); }
    private void accept(Player accepter, Player requester) {
        TpaRequest r = byTarget.get(accepter.getUniqueId());
        if (r == null || !r.requester.equals(requester.getUniqueId()) || r.expired(expireMs())) { missing(accepter); return; }
        remove(r);
        sound(accepter, Sound.ENTITY_PLAYER_LEVELUP);
        msg(accepter, m("request-accepted"));
        msg(requester, replace(m("request-accepted-other"), Map.of("%player%", accepter.getName())));
        Player mover = r.type == TpaRequest.Type.TPA ? requester : accepter;
        Player dest = r.type == TpaRequest.Type.TPA ? accepter : requester;
        countdownTeleport(mover, dest.getLocation());
    }

    private void countdownTeleport(Player mover, Location destination) {
        Location start = mover.getLocation().clone(); UUID moveId = mover.getUniqueId(); teleporting.add(moveId);
        int seconds = Math.max(0, getConfig().getInt("teleport-delay-seconds", 5));
        for (int i = seconds; i >= 1; i--) {
            int left = i;
            Scheduler.globalLater(this, () -> {
                if (!mover.isOnline() || !teleporting.contains(moveId)) return;
                if (movedBlock(start, mover.getLocation())) { teleporting.remove(moveId); msgBoth(mover, m("teleport-cancelled-moved")); sound(mover, Sound.ENTITY_VILLAGER_NO); return; }
                action(mover, replace(m("teleporting"), Map.of("%cooldown%", String.valueOf(left))));
                sound(mover, Sound.ENTITY_ENDERMAN_TELEPORT);
            }, (seconds - left) * 20L);
        }
        Scheduler.globalLater(this, () -> {
            if (mover.isOnline() && teleporting.remove(moveId) && !movedBlock(start, mover.getLocation())) {
                Scheduler.teleport(this, mover, destination);
                sound(mover, Sound.ENTITY_ENDERMAN_TELEPORT);
            }
        }, seconds * 20L);
    }

    private boolean movedBlock(Location a, Location b) { return !Objects.equals(a.getWorld(), b.getWorld()) || a.getBlockX()!=b.getBlockX() || a.getBlockY()!=b.getBlockY() || a.getBlockZ()!=b.getBlockZ(); }
    private TpaRequest findIncoming(Player p, String requesterName) { TpaRequest r = byTarget.get(p.getUniqueId()); if (r == null) return null; if (r.expired(expireMs())) { remove(r); return null; } if (requesterName == null) return r; Player req = Bukkit.getPlayer(r.requester); return req != null && req.getName().equalsIgnoreCase(requesterName) ? r : null; }
    private boolean requestDisabled(Player target, TpaRequest.Type type) { UUID id = target.getUniqueId(); return type == TpaRequest.Type.TPA ? !settingsHook.isEnabled(id, "TPA_TOGGLE", true) : !settingsHook.isEnabled(id, "TPAHERE_TOGGLE", true); }
    private void cancelRequest(Player p) { TpaRequest r = byRequester.get(p.getUniqueId()); if (r == null || r.expired(expireMs())) { missing(p); return; } remove(r); msg(p, m("request-cancelled")); }
    private void denyRequest(Player p, String[] args) { TpaRequest r = findIncoming(p, args.length > 0 ? args[0] : null); if (r == null) { missing(p); return; } remove(r); msg(p, m("request-denied")); Player req = Bukkit.getPlayer(r.requester); if (req != null) msg(req, replace(m("request-denied-other"), Map.of("%player%", p.getName()))); }
    private void toggleTpauto(Player p) {
        UUID id = p.getUniqueId();
        if (tpauto.remove(id)) {
            setPlayerFlag(id, "tpauto", false);
            stopTpautoActionbar(id);
            msgBoth(p, m("tpauto-off"));
            sound(p, Sound.BLOCK_BEACON_DEACTIVATE);
            return;
        }
        if (isTpautoBlacklistedWorld(p.getWorld())) {
            no(p);
            return;
        }
        tpauto.add(id);
        setPlayerFlag(id, "tpauto", true);
        msg(p, m("tpauto-on-chat"));
        action(p, m("tpauto-on-actionbar"));
        sound(p, Sound.BLOCK_BEACON_ACTIVATE);
        startTpautoActionbar(p);
    }
    private boolean isTpaGuiDisabled(UUID id) { return !settingsHook.isEnabled(id, "TPAGUI_TOGGLE", true); }
    private void startTpautoActionbar(Player p) {
        UUID id = p.getUniqueId();
        tpautoActionbars.computeIfAbsent(id, ignored -> Scheduler.globalTimer(this, () -> {
            if (!p.isOnline() || !tpauto.contains(id)) { stopTpautoActionbar(id); return; }
            if (enforceTpautoWorld(p)) return;
            action(p, m("tpauto-on-actionbar"));
        }, 40, 40));
    }
    private void stopTpautoActionbar(UUID id) { Scheduler.TaskHandle task = tpautoActionbars.remove(id); if (task != null) task.cancel(); }
    private void setPlayerFlag(UUID id, String path, boolean value) {
        // Fast path stays in RAM via the setting sets. Disk is touched only when a setting changes.
        players.set("players." + id + "." + path, value ? true : null);
        playersDirty.set(true);
        scheduleSavePlayers();
    }

    private void scheduleSavePlayers() { Scheduler.async(this, this::savePlayersIfDirty); }

    private void savePlayersIfDirty() {
        if (!playersDirty.getAndSet(false) || playersFile == null) return;
        Set<UUID> ids = new HashSet<>();
        ids.addAll(tpauto);
        YamlConfiguration out = new YamlConfiguration();
        for (UUID id : ids) {
            out.set("players." + id + ".tpauto", tpauto.contains(id) ? true : null);
        }
        try {
            out.save(playersFile);
        } catch (IOException e) {
            playersDirty.set(true);
            getLogger().warning("Failed to save players.yml, retrying: " + e.getMessage());
            Scheduler.globalLater(this, this::scheduleSavePlayers, 20);
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { enforceTpautoWorld(e.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e) { enforceTpautoWorld(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { UUID id = e.getPlayer().getUniqueId(); stopTpautoActionbar(id); removeByRequester(id); TpaRequest r = byTarget.get(id); if (r != null) remove(r); teleporting.remove(id); }
    private boolean enforceTpautoWorld(Player player) {
        UUID id = player.getUniqueId();
        if (!tpauto.contains(id) || !isTpautoBlacklistedWorld(player.getWorld())) {
            return false;
        }
        tpauto.remove(id);
        setPlayerFlag(id, "tpauto", false);
        stopTpautoActionbar(id);
        return true;
    }
    private boolean isTpautoBlacklistedWorld(World world) {
        for (String configured : getConfig().getStringList("tpauto.blacklisted-worlds")) {
            if (matchesWorld(world, configured)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesWorld(World world, String configured) {
        if (world == null || configured == null || configured.isBlank()) return false;
        String key = world.getKey().getKey();
        return configured.equalsIgnoreCase(world.getName())
                || configured.equalsIgnoreCase(world.getKey().toString())
                || configured.equalsIgnoreCase(key)
                || configured.equalsIgnoreCase(key.substring(key.lastIndexOf('/') + 1));
    }
    private void removeByRequester(UUID uuid) { TpaRequest old = byRequester.get(uuid); if (old != null) remove(old); }
    private void remove(TpaRequest r) { byRequester.remove(r.requester); byTarget.remove(r.target); }
    private long expireMs() { return getConfig().getLong("request-expire-seconds", 180) * 1000L; }
    private void msg(Player p, String s) { p.spigot().sendMessage(TextComponent.fromLegacyText(ColorUtil.color(s))); }
    private void msgBoth(Player p, String s) { msg(p, s); action(p, s); }
    private void missing(Player p) { msg(p, m("request-missing")); }
    private void no(Player p) { sound(p, Sound.ENTITY_VILLAGER_NO); }
    private TextComponent legacyComponent(String s) { return new TextComponent(TextComponent.fromLegacyText(ColorUtil.color(s))); }
    private void action(Player p, String s) { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ColorUtil.color(s))); }
    private void sound(Player p, Sound sound) { p.playSound(p.getLocation(), sound, 1f, 1f); }


}

