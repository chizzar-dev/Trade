package com.metox.trade;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Iki oyuncu arasindaki acik takas. */
public class TradeSession {

    public final UUID a;
    public final UUID b;
    public final String nameA;
    public final String nameB;

    public final List<ItemStack> offerA = new ArrayList<ItemStack>();
    public final List<ItemStack> offerB = new ArrayList<ItemStack>();

    public boolean readyA;
    public boolean readyB;

    /** Takas tamamlandi. */
    public boolean completed;
    /** Kapanis sureci basladi; ikinci kez calismasin. */
    public boolean ending;
    /** Son onay bekleniyor - bu sirada teklif degistirilemez. */
    public boolean locking;

    public Inventory invA;
    public Inventory invB;

    public TradeSession(UUID a, String nameA, UUID b, String nameB) {
        this.a = a;
        this.nameA = nameA;
        this.b = b;
        this.nameB = nameB;
    }

    public boolean isSideA(UUID id) {
        return a.equals(id);
    }

    public List<ItemStack> offerOf(UUID id) {
        return isSideA(id) ? offerA : offerB;
    }

    public UUID other(UUID id) {
        return isSideA(id) ? b : a;
    }
}
