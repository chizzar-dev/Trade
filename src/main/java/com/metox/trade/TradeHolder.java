package com.metox.trade;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Acik takas penceresini oturuma ve tarafa baglar. */
public class TradeHolder implements InventoryHolder {

    private final TradeSession session;
    private final boolean sideA;
    private Inventory inventory;

    public TradeHolder(TradeSession session, boolean sideA) {
        this.session = session;
        this.sideA = sideA;
    }

    public TradeSession getSession() {
        return session;
    }

    public boolean isSideA() {
        return sideA;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
