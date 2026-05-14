package com.maris7.tpa;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class GuiHolder implements InventoryHolder {
    public enum Action { SEND_TPA, SEND_TPAHERE, ACCEPT }
    public final Action action;
    public final UUID other;
    private Inventory inventory;

    public GuiHolder(Action action, UUID other) {
        this.action = action;
        this.other = other;
    }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
