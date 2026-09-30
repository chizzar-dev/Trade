package com.metox.trade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Guvenli oyuncular arasi takas.
 *
 * Esya kaybina karsi iki guvence var:
 *   - Teklif degisince iki tarafin da onayi sifirlanir (son an degistirme scam'i)
 *   - Karsi taraf cikarsa esyalar teslim kuyruguna yazilir, girisinde verilir
 */
public class TradePlugin extends JavaPlugin implements Listener, TabExecutor {

    private static final int[] YOUR_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] THEIR_SLOTS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    private static final int CONFIRM_SLOT = 48;
    private static final int CANCEL_SLOT = 49;
    private static final int THEIR_STATUS = 50;

    private final Map<UUID, TradeSession> sessions = new HashMap<UUID, TradeSession>();
    private final Map<UUID, UUID> requests = new HashMap<UUID, UUID>();
    private final Map<UUID, Long> reqExpire = new HashMap<UUID, Long>();

    private Vault vault;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        vault = new Vault(this);

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("trade") != null) {
            getCommand("trade").setExecutor(this);
            getCommand("trade").setTabCompleter(this);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                expireRequests();
            }
        }.runTaskTimer(this, 20L, 20L);

        getLogger().info("Trade aktif - algilanan surum 1." + Compat.MINOR
                + (Compat.PATCH > 0 ? "." + Compat.PATCH : ""));
    }

    @Override
    public void onDisable() {
        // Acik takaslari iptal et; esyalar sahiplerine ya da kuyruga gider
        for (TradeSession s : new ArrayList<TradeSession>(sessions.values())) {
            cancel(s, "cancelled");
        }
    }

    // ------------------------------------------------------------------
    // Komut
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            send(sender, "players-only");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("trade.use")) {
            send(p, "no-permission");
            return true;
        }
        if (args.length < 1) {
            send(p, "usage");
            return true;
        }

        String a0 = args[0].toLowerCase(Locale.ENGLISH);
        if (a0.equals("kabul") || a0.equals("accept")) {
            accept(p);
            return true;
        }
        if (a0.equals("ret") || a0.equals("reddet") || a0.equals("deny")) {
            deny(p);
            return true;
        }
        if (a0.equals("iptal") || a0.equals("cancel")) {
            cancelSelf(p);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            send(p, "player-not-found");
            return true;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            send(p, "self");
            return true;
        }
        if (sessions.containsKey(p.getUniqueId())) {
            send(p, "busy-you");
            return true;
        }
        if (sessions.containsKey(target.getUniqueId())) {
            send(p, "busy-target");
            return true;
        }
        int maxDist = getConfig().getInt("max-distance", 0);
        if (maxDist > 0 && !withinDistance(p, target, maxDist)) {
            send(p, "too-far");
            return true;
        }

        // Karsi taraf zaten istek attiysa dogrudan basla
        UUID incoming = requesterFor(p.getUniqueId());
        if (incoming != null && incoming.equals(target.getUniqueId())) {
            requests.remove(incoming);
            reqExpire.remove(incoming);
            startTrade(target, p);
            return true;
        }

        requests.put(p.getUniqueId(), target.getUniqueId());
        reqExpire.put(p.getUniqueId(), System.currentTimeMillis()
                + getConfig().getInt("request-timeout", 60) * 1000L);
        send(p, "request-sent", "%player%", target.getName());
        send(target, "request-received", "%player%", p.getName());
        Compat.sound(target, "pling", 0.8f, 1.3f);
        return true;
    }

    private boolean withinDistance(Player a, Player b, int max) {
        try {
            if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return false;
            return a.getLocation().distanceSquared(b.getLocation()) <= (double) max * max;
        } catch (Throwable t) {
            return true;
        }
    }

    private void accept(Player me) {
        UUID from = requesterFor(me.getUniqueId());
        if (from == null) {
            send(me, "no-request");
            return;
        }
        Player other = Bukkit.getPlayer(from);
        requests.remove(from);
        reqExpire.remove(from);
        if (other == null || !other.isOnline()) {
            send(me, "player-not-found");
            return;
        }
        if (sessions.containsKey(me.getUniqueId()) || sessions.containsKey(other.getUniqueId())) {
            send(me, "busy-target");
            return;
        }
        startTrade(other, me);
    }

    private void deny(Player me) {
        UUID from = requesterFor(me.getUniqueId());
        if (from == null) {
            send(me, "no-request");
            return;
        }
        requests.remove(from);
        reqExpire.remove(from);
        send(me, "denied-you");
        Player other = Bukkit.getPlayer(from);
        if (other != null) send(other, "denied-other", "%player%", me.getName());
    }

    private void cancelSelf(Player p) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s != null) {
            cancel(s, "cancelled");
            return;
        }
        if (requests.remove(p.getUniqueId()) != null) {
            reqExpire.remove(p.getUniqueId());
            send(p, "cancelled-out");
            return;
        }
        send(p, "no-request");
    }

    private UUID requesterFor(UUID target) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, UUID> en : requests.entrySet()) {
            if (en.getValue().equals(target)) {
                Long ex = reqExpire.get(en.getKey());
                if (ex == null || now < ex) return en.getKey();
            }
        }
        return null;
    }

    private void expireRequests() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = reqExpire.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            if (now >= en.getValue()) {
                UUID from = en.getKey();
                UUID to = requests.remove(from);
                it.remove();
                Player pf = Bukkit.getPlayer(from);
                if (pf != null) send(pf, "expired");
                if (to != null) {
                    Player pt = Bukkit.getPlayer(to);
                    if (pt != null) send(pt, "expired");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Takas penceresi
    // ------------------------------------------------------------------

    private void startTrade(Player a, Player b) {
        TradeSession s = new TradeSession(a.getUniqueId(), a.getName(), b.getUniqueId(), b.getName());

        TradeHolder hA = new TradeHolder(s, true);
        s.invA = Bukkit.createInventory(hA, 54, title(b.getName()));
        hA.setInventory(s.invA);

        TradeHolder hB = new TradeHolder(s, false);
        s.invB = Bukkit.createInventory(hB, 54, title(a.getName()));
        hB.setInventory(s.invB);

        sessions.put(a.getUniqueId(), s);
        sessions.put(b.getUniqueId(), s);

        render(s);
        a.openInventory(s.invA);
        b.openInventory(s.invB);
        send(a, "opened");
        send(b, "opened");
    }

    private String title(String other) {
        return Compat.title32(getConfig().getString("gui.title", "&8Takas &7- &f%player%")
                .replace("%player%", other));
    }

    private void render(TradeSession s) {
        renderSide(s.invA, s.offerA, s.offerB, s.readyA, s.readyB);
        renderSide(s.invB, s.offerB, s.offerA, s.readyB, s.readyA);
    }

    private void renderSide(Inventory inv, List<ItemStack> mine, List<ItemStack> theirs,
                            boolean myReady, boolean theirReady) {
        if (inv == null) return;

        ItemStack filler = Compat.item(
                Compat.pane(getConfig().getString("gui.filler", "GRAY_STAINED_GLASS_PANE")),
                " ", null);

        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        for (int slot : YOUR_SLOTS) inv.setItem(slot, null);
        for (int slot : THEIR_SLOTS) inv.setItem(slot, null);

        for (int i = 0; i < mine.size() && i < YOUR_SLOTS.length; i++) {
            inv.setItem(YOUR_SLOTS[i], mine.get(i));
        }
        for (int i = 0; i < theirs.size() && i < THEIR_SLOTS.length; i++) {
            inv.setItem(THEIR_SLOTS[i], theirs.get(i));
        }

        Material green = Compat.materialOr(Material.STONE, "LIME_WOOL", "EMERALD_BLOCK", "WOOL");
        Material red = Compat.materialOr(Material.STONE, "RED_WOOL", "REDSTONE_BLOCK", "WOOL");

        inv.setItem(CONFIRM_SLOT, myReady
                ? Compat.item(green, msg("gui.ready-yes", "&a&lHAZIRSIN"),
                Arrays.asList(msg("gui.ready-yes-lore", "&7Vazgecmek icin tekrar tikla")))
                : Compat.item(red, msg("gui.ready-no", "&c&lHAZIR DEGILSIN"),
                Arrays.asList(msg("gui.ready-no-lore", "&7Onaylamak icin tikla"))));

        inv.setItem(THEIR_STATUS, theirReady
                ? Compat.item(green, msg("gui.other-ready", "&aKarsi taraf hazir"), null)
                : Compat.item(red, msg("gui.other-waiting", "&cKarsi taraf bekliyor..."), null));

        inv.setItem(CANCEL_SLOT, Compat.item(
                Compat.materialOr(Material.STONE, "BARRIER", "REDSTONE_BLOCK"),
                msg("gui.cancel", "&c&lIPTAL"),
                Arrays.asList(msg("gui.cancel-lore", "&7Takasi iptal et"))));
    }

    private String msg(String path, String def) {
        return getConfig().getString("messages." + path, def);
    }

    // ------------------------------------------------------------------
    // Tiklamalar
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView() == null ? null : e.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof TradeHolder)) return;

        // Takas penceresi acikken hicbir surukleme/kaydirma serbest degil
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;

        TradeHolder h = (TradeHolder) top.getHolder();
        TradeSession s = h.getSession();
        if (s.completed || s.ending) return;

        Player p = (Player) e.getWhoClicked();
        boolean sideA = h.isSideA();
        List<ItemStack> mine = sideA ? s.offerA : s.offerB;

        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        int raw = e.getRawSlot();

        // Ust penceredeki dugmeler
        if (clicked.equals(top)) {
            if (raw == CONFIRM_SLOT) {
                if (s.locking) return;
                if (sideA) s.readyA = !s.readyA;
                else s.readyB = !s.readyB;
                Compat.sound(p, "click", 0.7f, 1.2f);
                render(s);
                if (s.readyA && s.readyB) beginLock(s);
                return;
            }
            if (raw == CANCEL_SLOT) {
                cancel(s, "cancelled");
                return;
            }
            if (s.locking) return;
            int idx = indexOf(YOUR_SLOTS, raw);
            if (idx >= 0 && idx < mine.size()) {
                ItemStack removed = mine.remove(idx);
                giveOrDrop(p, removed);
                resetReady(s);
                render(s);
            }
            return;
        }

        // Kendi envanterinden teklife koyma
        if (s.locking) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.AIR) return;
        if (isBlacklisted(it)) {
            send(p, "blacklisted");
            return;
        }
        if (mine.size() >= YOUR_SLOTS.length) {
            send(p, "full");
            return;
        }
        mine.add(it.clone());
        clicked.setItem(e.getSlot(), null);
        resetReady(s);
        Compat.sound(p, "click", 0.6f, 1.0f);
        render(s);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView() == null ? null : e.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof TradeHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (inv == null || !(inv.getHolder() instanceof TradeHolder)) return;
        TradeSession s = ((TradeHolder) inv.getHolder()).getSession();
        if (s.ending || s.completed) return;
        cancel(s, "cancelled");
    }

    /** Iki taraf da hazir: kisa bir kilit suresi sonra takas yapilir. */
    private void beginLock(final TradeSession s) {
        int delay = Math.max(0, getConfig().getInt("confirm-delay-ticks", 20));
        if (delay == 0) {
            execute(s);
            return;
        }
        s.locking = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (s.ending || s.completed) return;
                if (!s.readyA || !s.readyB) {
                    s.locking = false;
                    render(s);
                    return;
                }
                execute(s);
            }
        }.runTaskLater(this, delay);
    }

    private void resetReady(TradeSession s) {
        s.readyA = false;
        s.readyB = false;
    }

    private void execute(TradeSession s) {
        if (s.completed || s.ending) return;
        s.completed = true;
        s.ending = true;

        Player pa = Bukkit.getPlayer(s.a);
        Player pb = Bukkit.getPlayer(s.b);

        // Karsi tarafin teklifi bana gelir
        deliver(s.b, s.nameB, pb, s.offerA);
        deliver(s.a, s.nameA, pa, s.offerB);

        s.offerA.clear();
        s.offerB.clear();
        closeBoth(s);
        drop(s);

        if (pa != null) {
            send(pa, "success");
            Compat.sound(pa, "levelup", 0.8f, 1.2f);
        }
        if (pb != null) {
            send(pb, "success");
            Compat.sound(pb, "levelup", 0.8f, 1.2f);
        }
        if (getConfig().getBoolean("log-trades", true)) {
            getLogger().info("Takas tamamlandi: " + s.nameA + " <-> " + s.nameB);
        }
    }

    /** Iptal: herkes kendi esyasini geri alir. */
    private void cancel(TradeSession s, String key) {
        if (s.ending) return;
        s.ending = true;

        Player pa = Bukkit.getPlayer(s.a);
        Player pb = Bukkit.getPlayer(s.b);

        deliver(s.a, s.nameA, pa, s.offerA);
        deliver(s.b, s.nameB, pb, s.offerB);

        s.offerA.clear();
        s.offerB.clear();
        closeBoth(s);
        drop(s);

        if (key != null) {
            if (pa != null) send(pa, key);
            if (pb != null) send(pb, key);
        }
    }

    /**
     * Esyalari sahibine ulastirir.
     * Oyuncu cevrimdisiysa teslim kuyruguna yazilir - hicbir sey kaybolmaz.
     */
    private void deliver(UUID id, String name, Player online, List<ItemStack> items) {
        if (items == null || items.isEmpty()) return;

        List<ItemStack> copy = new ArrayList<ItemStack>();
        for (ItemStack it : items) {
            if (it != null && it.getType() != Material.AIR) copy.add(it);
        }
        if (copy.isEmpty()) return;

        if (online != null && online.isOnline()) {
            for (ItemStack it : copy) giveOrDrop(online, it);
            return;
        }
        vault.store(id, copy);
        getLogger().info(name + " cevrimdisi, " + copy.size()
                + " esya teslim kuyruguna alindi.");
    }

    /** Takasta olen oyuncu: takas iptal olur, kimse esya kaybetmez. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        TradeSession s = sessions.get(e.getEntity().getUniqueId());
        if (s != null) cancel(s, "cancelled");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        TradeSession s = sessions.get(id);
        if (s != null) cancel(s, "cancelled");

        if (requests.remove(id) != null) reqExpire.remove(id);
        Iterator<Map.Entry<UUID, UUID>> it = requests.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().equals(id)) it.remove();
        }
    }

    /** Girişte bekleyen esyalari teslim et. */
    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!e.getPlayer().isOnline()) return;
                if (vault.deliver(e.getPlayer())) send(e.getPlayer(), "pending-delivered");
            }
        }.runTaskLater(this, 20L);
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    private boolean isBlacklisted(ItemStack it) {
        List<String> list = getConfig().getStringList("blacklist");
        if (list.isEmpty()) return false;
        String type = it.getType().name();
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(type)) return true;
        }
        return false;
    }

    private void drop(TradeSession s) {
        sessions.remove(s.a);
        sessions.remove(s.b);
    }

    private void closeBoth(TradeSession s) {
        closeIfTrade(Bukkit.getPlayer(s.a));
        closeIfTrade(Bukkit.getPlayer(s.b));
    }

    private void closeIfTrade(Player p) {
        if (p == null || !p.isOnline()) return;
        try {
            if (p.getOpenInventory() != null
                    && p.getOpenInventory().getTopInventory() != null
                    && p.getOpenInventory().getTopInventory().getHolder() instanceof TradeHolder) {
                p.closeInventory();
            }
        } catch (Throwable ignored) {
        }
    }

    private void giveOrDrop(Player p, ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack rem : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rem);
        }
    }

    private int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return -1;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ENGLISH);
            for (String s : Arrays.asList("kabul", "ret", "iptal")) {
                if (s.startsWith(pref)) out.add(s);
            }
            for (Player pl : Compat.online()) {
                if (sender instanceof Player && pl.equals(sender)) continue;
                if (pl.getName().toLowerCase(Locale.ENGLISH).startsWith(pref)) out.add(pl.getName());
            }
            return out;
        }
        return Collections.emptyList();
    }

    public void send(CommandSender s, String key, String... repl) {
        if (s == null) return;
        String m = getConfig().getString("messages." + key, "");
        if (m == null || m.isEmpty()) return;
        for (int i = 0; i + 1 < repl.length; i += 2) m = m.replace(repl[i], repl[i + 1]);
        s.sendMessage(Compat.color(m));
    }
}
