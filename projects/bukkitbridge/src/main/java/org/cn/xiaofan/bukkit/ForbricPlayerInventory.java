/*
 * Forbric Bukkit bridge — a player's inventory, as a plugin sees it.
 */
package org.cn.xiaofan.bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import net.minecraft.world.entity.player.Inventory;

/**
 * One live player's inventory, as an {@code org.bukkit.inventory.PlayerInventory}.
 *
 * <p><b>A live view, not a copy.</b> This holds the {@code ServerPlayer} and reads and writes the
 * game's inventory on every call, which is what Bukkit's contract describes: a plugin that takes an
 * item out of slot 5 and asks the inventory again sees the change, because the change is in the game.
 * A snapshot would be easier to write and would silently disagree with the world the moment anything
 * else touched the player.
 *
 * <p><b>Why the slots are named, not numbered.</b> Bukkit and the game agree on which slot a thing
 * is in for the main storage and the hotbar, and this class uses that agreement directly. Where they
 * do not agree — Bukkit's armour and offhand slots versus the game's separate equipment slots — the
 * difference is handled at the point of access rather than by an offset field, because an offset that
 * is wrong by one is a plugin writing a helmet into a boot.
 *
 * <p><b>What is not here.</b> Opening a window, the cursor stack, viewers and the client-side
 * inventory the API also exposes are refused by name. They belong to a container layer this bridge
 * has not built, and a stand-in would give a plugin a value that is plausible and wrong.
 */
final class ForbricPlayerInventory implements org.bukkit.inventory.PlayerInventory {

    private final net.minecraft.server.level.ServerPlayer player;
    private final ServerImpl server;

    ForbricPlayerInventory(net.minecraft.server.level.ServerPlayer player, ServerImpl server) {
        this.player = player;
        this.server = server;
    }

    private Inventory inventory() {
        return player.getInventory();
    }

    /**
     * The number of slots, which is what the game's inventory holds.
     *
     * <p>Read from the game rather than hard-coded: the size is the game's own (36 for a player),
     * and a constant here would be wrong for any inventory the game changes the shape of.
     */
    @Override
    public int getSize() {
        return inventory().getContainerSize();
    }

    /**
     * One slot's contents.
     *
     * <p>Bukkit's slot numbers and the game's line up for the whole storage area, so this is a
     * direct read. An out-of-range slot gives air, which is what the API says for a slot that is
     * empty — and a negative index would be a plugin bug that the game's own bounds check turns
     * into air rather than an {@code IndexOutOfBoundsException} several frames deep.
     */
    @Override
    public org.bukkit.inventory.ItemStack getItem(int index) {
        Inventory inventory = inventory();
        if (index < 0 || index >= inventory.getContainerSize()) {
            return ForbricItemStack.plain(org.bukkit.Material.AIR, 0);
        }
        return ForbricItemStack.of(inventory.getItem(index));
    }

    /**
     * Puts an item in a slot.
     *
     * <p>Air means "clear this slot", which is the API's contract and the game's own representation
     * of an empty slot.
     */
    @Override
    public void setItem(int index, org.bukkit.inventory.ItemStack item) {
        Inventory inventory = inventory();
        if (index < 0 || index >= inventory.getContainerSize()) {
            throw new IllegalArgumentException(
                            "[Forbric/Bukkit] slot " + index + " is outside this inventory (size "
                                            + inventory.getContainerSize() + ")");
        }
        if (item == null || item.getType() == org.bukkit.Material.AIR || item.getAmount() <= 0) {
            inventory.setItem(index, net.minecraft.world.item.ItemStack.EMPTY);
            return;
        }
        inventory.setItem(index, ForbricItemCodec.gameStack(item));
    }

    /**
     * Every slot, in the order the API documents: storage and hotbar first, then armour, then the
     * offhand.
     *
     * <p>The order matters and is not the game's. Bukkit puts armour and the held item at the END of
     * {@code getContents()} so that a plugin indexing the first 36 entries always gets storage; the
     * game keeps armour in a different structure entirely. Getting this backwards would make a
     * plugin that fills the first 36 slots overwrite a helmet.
     */
    @Override
    public org.bukkit.inventory.ItemStack[] getContents() {
        Inventory inventory = inventory();
        int storage = inventory.getContainerSize();
        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[storage + 5];
        for (int i = 0; i < storage; i++) {
            contents[i] = ForbricItemStack.of(inventory.getItem(i));
        }
        contents[storage] = getBoots();
        contents[storage + 1] = getLeggings();
        contents[storage + 2] = getChestplate();
        contents[storage + 3] = getHelmet();
        contents[storage + 4] = getItemInOffHand();
        return contents;
    }

    /**
     * Replaces everything, in the same order {@link #getContents()} returns.
     *
     * <p>A shorter array leaves the remaining slots alone rather than clearing them, which is what
     * the API specifies — a plugin that passes exactly the storage part means to change only that.
     */
    @Override
    public void setContents(org.bukkit.inventory.ItemStack[] items) {
        if (items == null) {
            throw new IllegalArgumentException("[Forbric/Bukkit] setContents(null) is not meaningful");
        }
        Inventory inventory = inventory();
        int storage = inventory.getContainerSize();
        for (int i = 0; i < storage; i++) {
            if (i < items.length) {
                setItem(i, items[i]);
            }
        }
        if (items.length > storage) {
            setBoots(items[storage]);
        }
        if (items.length > storage + 1) {
            setLeggings(items[storage + 1]);
        }
        if (items.length > storage + 2) {
            setChestplate(items[storage + 2]);
        }
        if (items.length > storage + 3) {
            setHelmet(items[storage + 3]);
        }
        if (items.length > storage + 4) {
            setItemInOffHand(items[storage + 4]);
        }
    }

    /**
     * The storage and hotbar only, without the armour and held item {@link #getContents()} appends.
     *
     * <p>An alias for the first {@code getSize()} entries, which is the same array a plugin gets
     * from the game's own view of a player's inventory.
     */
    @Override
    public org.bukkit.inventory.ItemStack[] getStorageContents() {
        Inventory inventory = inventory();
        int storage = inventory.getContainerSize();
        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[storage];
        for (int i = 0; i < storage; i++) {
            contents[i] = ForbricItemStack.of(inventory.getItem(i));
        }
        return contents;
    }

    /** Replaces the storage and hotbar, leaving armour and the held item untouched. */
    @Override
    public void setStorageContents(org.bukkit.inventory.ItemStack[] items) {
        if (items == null) {
            throw new IllegalArgumentException("[Forbric/Bukkit] setStorageContents(null) is not meaningful");
        }
        int storage = inventory().getContainerSize();
        for (int i = 0; i < storage && i < items.length; i++) {
            setItem(i, items[i]);
        }
    }

    /**
     * Puts items wherever they fit, and reports what did not.
     *
     * <p>The map is keyed by the index of the argument that could not be placed, which is how the
     * API lets a plugin find out which of the items it offered were dropped. A count above an item's
     * stack size spills into further slots before being reported, matching what the game does when
     * it gives a player items.
     */
    @Override
    public HashMap<Integer, org.bukkit.inventory.ItemStack> addItem(org.bukkit.inventory.ItemStack... items) {
        HashMap<Integer, org.bukkit.inventory.ItemStack> leftover = new HashMap<>();
        int storage = inventory().getContainerSize();
        for (int argument = 0; argument < items.length; argument++) {
            org.bukkit.inventory.ItemStack remaining = items[argument];
            if (remaining == null || remaining.getType() == org.bukkit.Material.AIR) {
                continue;
            }
            net.minecraft.world.item.ItemStack toPlace = ForbricItemCodec.gameStack(remaining);
            // The game's own add() fills partial stacks first and then free slots, and returns
            // false when something did not fit -- using it keeps this identical to what the world
            // does when a mod gives a player an item.
            boolean placed = inventory().add(toPlace);
            if (!placed || !toPlace.isEmpty()) {
                leftover.put(argument, remaining);
            }
        }
        return leftover;
    }

    /**
     * Takes items out of the inventory, and reports what was not found.
     *
     * <p>Removal is by similarity, not equality: asking for one stone takes a stone out of a stack
     * of 64, which is what a plugin means and what the game does.
     */
    @Override
    public HashMap<Integer, org.bukkit.inventory.ItemStack> removeItem(org.bukkit.inventory.ItemStack... items) {
        HashMap<Integer, org.bukkit.inventory.ItemStack> missing = new HashMap<>();
        for (int argument = 0; argument < items.length; argument++) {
            org.bukkit.inventory.ItemStack wanted = items[argument];
            if (wanted == null || wanted.getType() == org.bukkit.Material.AIR) {
                continue;
            }
            if (!removeUpTo(wanted)) {
                missing.put(argument, wanted);
            }
        }
        return missing;
    }

    /** The same as {@link #removeItem} for a player inventory; the API's distinction does not apply here. */
    @Override
    public HashMap<Integer, org.bukkit.inventory.ItemStack> removeItemAnySlot(
                    org.bukkit.inventory.ItemStack... items) {
        return removeItem(items);
    }

    /**
     * Takes {@code wanted.getAmount()} of {@code wanted}'s kind out of the storage area.
     *
     * <p>True when the whole amount was removed. The game's inventory has no "remove N of this kind"
     * call that reports a shortfall, so the count is walked slot by slot — which is also what makes a
     * partial removal (3 of a wanted 5) leave the inventory in the state a plugin expects: the 3 that
     * were there are gone, and the caller is told the rest was missing.
     */
    private boolean removeUpTo(org.bukkit.inventory.ItemStack wanted) {
        Inventory inventory = inventory();
        int remaining = wanted.getAmount();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            net.minecraft.world.item.ItemStack here = inventory.getItem(slot);
            if (here.isEmpty() || ForbricItemStack.materialOf(here) != wanted.getType()) {
                continue;
            }
            int taken = Math.min(remaining, here.getCount());
            here.shrink(taken);
            remaining -= taken;
        }
        return remaining == 0;
    }

    /**
     * The first slot holding this kind, or -1.
     *
     * <p>Count is not compared: "first stone" means the first slot with a stone in it, whatever it
     * holds.
     */
    @Override
    public int first(org.bukkit.inventory.ItemStack item) {
        if (item == null) {
            return -1;
        }
        Inventory inventory = inventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            net.minecraft.world.item.ItemStack here = inventory.getItem(slot);
            if (!here.isEmpty() && ForbricItemStack.materialOf(here) == item.getType()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * The first slot holding a material, or -1.
     *
     * <p>A separate method from {@link #first(org.bukkit.inventory.ItemStack)} because Paper declares
     * both and neither is a {@code default} — a plugin may call either.
     */
    @Override
    public int first(org.bukkit.Material material) {
        return first(ForbricItemStack.plain(material, 1));
    }

    /** The first free slot, or -1 when the storage area is full. */
    @Override
    public int firstEmpty() {
        Inventory inventory = inventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** Whether the whole storage area is empty. */
    @Override
    public boolean isEmpty() {
        Inventory inventory = inventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Empties one slot. */
    @Override
    public void clear(int index) {
        setItem(index, null);
    }

    /** Empties the storage and hotbar, leaving armour and the held item. */
    @Override
    public void clear() {
        Inventory inventory = inventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    /** Whether the storage area holds at least this much of a kind. */
    @Override
    public boolean contains(org.bukkit.inventory.ItemStack item) {
        return containsAtLeast(item, item == null ? 0 : item.getAmount());
    }

    /** Whether the storage area holds at least a material, at all. */
    @Override
    public boolean contains(org.bukkit.Material material) {
        return first(ForbricItemStack.plain(material, 1)) != -1;
    }

    /** Whether the storage area holds at least this many of a material. */
    @Override
    public boolean contains(org.bukkit.Material material, int amount) {
        return containsAtLeast(ForbricItemStack.plain(material, amount), amount);
    }

    /** Whether the storage area holds at least {@code amount} of a kind. */
    @Override
    public boolean containsAtLeast(org.bukkit.inventory.ItemStack item, int amount) {
        if (item == null || amount <= 0) {
            return false;
        }
        Inventory inventory = inventory();
        int found = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            net.minecraft.world.item.ItemStack here = inventory.getItem(slot);
            if (!here.isEmpty() && ForbricItemStack.materialOf(here) == item.getType()) {
                found += here.getCount();
                if (found >= amount) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the storage area holds at least {@code amount} of a kind.
     *
     * <p>The same question as {@link #containsAtLeast}, which Paper declares separately — neither is
     * a {@code default}, so both must be answered.
     */
    @Override
    public boolean contains(org.bukkit.inventory.ItemStack item, int amount) {
        return containsAtLeast(item, amount);
    }

    /** Every slot holding a kind, by index. */
    @Override
    public HashMap<Integer, ? extends org.bukkit.inventory.ItemStack> all(org.bukkit.inventory.ItemStack item) {
        HashMap<Integer, org.bukkit.inventory.ItemStack> found = new HashMap<>();
        if (item == null) {
            return found;
        }
        Inventory inventory = inventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            net.minecraft.world.item.ItemStack here = inventory.getItem(slot);
            if (!here.isEmpty() && ForbricItemStack.materialOf(here) == item.getType()) {
                found.put(slot, ForbricItemStack.of(here));
            }
        }
        return found;
    }

    /** Every slot holding a material, by index. */
    @Override
    public HashMap<Integer, ? extends org.bukkit.inventory.ItemStack> all(org.bukkit.Material material) {
        return all(ForbricItemStack.plain(material, 1));
    }

    /** Removes every slot holding a kind. */
    @Override
    public void remove(org.bukkit.inventory.ItemStack item) {
        if (item == null) {
            return;
        }
        Inventory inventory = inventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            net.minecraft.world.item.ItemStack here = inventory.getItem(slot);
            if (!here.isEmpty() && ForbricItemStack.materialOf(here) == item.getType()) {
                inventory.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
    }

    /** Removes every slot holding a material. */
    @Override
    public void remove(org.bukkit.Material material) {
        remove(ForbricItemStack.plain(material, 1));
    }

    // ---- Armour and hands -------------------------------------------------------------------------
    //
    // Bukkit and the game disagree here: the game keeps armour in slots of its own, while Bukkit
    // folds it into the player inventory at fixed offsets. The mapping is written out at each access
    // rather than kept as an offset field, so a wrong number shows up in the file that uses it.

    /** The helmet, or air. */
    @Override
    public org.bukkit.inventory.ItemStack getHelmet() {
        return ForbricItemStack.of(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
    }

    /** The chestplate, or air. */
    @Override
    public org.bukkit.inventory.ItemStack getChestplate() {
        return ForbricItemStack.of(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
    }

    /** The leggings, or air. */
    @Override
    public org.bukkit.inventory.ItemStack getLeggings() {
        return ForbricItemStack.of(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
    }

    /** The boots, or air. */
    @Override
    public org.bukkit.inventory.ItemStack getBoots() {
        return ForbricItemStack.of(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
    }

    /** Sets the helmet. */
    @Override
    public void setHelmet(org.bukkit.inventory.ItemStack item) {
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                        ForbricItemCodec.gameStackOrEmpty(item));
    }

    /** Sets the chestplate. */
    @Override
    public void setChestplate(org.bukkit.inventory.ItemStack item) {
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                        ForbricItemCodec.gameStackOrEmpty(item));
    }

    /** Sets the leggings. */
    @Override
    public void setLeggings(org.bukkit.inventory.ItemStack item) {
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,
                        ForbricItemCodec.gameStackOrEmpty(item));
    }

    /** Sets the boots. */
    @Override
    public void setBoots(org.bukkit.inventory.ItemStack item) {
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,
                        ForbricItemCodec.gameStackOrEmpty(item));
    }

    /** The four armour slots, boots first, which is the order the API documents. */
    @Override
    public org.bukkit.inventory.ItemStack[] getArmorContents() {
        return new org.bukkit.inventory.ItemStack[] {getBoots(), getLeggings(), getChestplate(), getHelmet()};
    }

    /** Replaces the four armour slots, boots first. */
    @Override
    public void setArmorContents(org.bukkit.inventory.ItemStack[] items) {
        if (items == null) {
            throw new IllegalArgumentException("[Forbric/Bukkit] setArmorContents(null) is not meaningful");
        }
        if (items.length > 0) {
            setBoots(items[0]);
        }
        if (items.length > 1) {
            setLeggings(items[1]);
        }
        if (items.length > 2) {
            setChestplate(items[2]);
        }
        if (items.length > 3) {
            setHelmet(items[3]);
        }
    }

    /** The main hand's item. */
    @Override
    public org.bukkit.inventory.ItemStack getItemInMainHand() {
        return ForbricItemStack.of(inventory().getSelectedItem());
    }

    /** Sets the main hand's item. */
    @Override
    public void setItemInMainHand(org.bukkit.inventory.ItemStack item) {
        inventory().setSelectedItem(ForbricItemCodec.gameStackOrEmpty(item));
    }

    /**
     * The offhand's item.
     *
     * <p>Read from the player's own equipment slot, not from the inventory: the game keeps the
     * offhand outside the 36 storage slots, and reading a numbered slot here would be an off-by-one
     * into the hotbar.
     */
    @Override
    public org.bukkit.inventory.ItemStack getItemInOffHand() {
        return ForbricItemStack.of(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND));
    }

    /** Sets the offhand's item. */
    @Override
    public void setItemInOffHand(org.bukkit.inventory.ItemStack item) {
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                        ForbricItemCodec.gameStackOrEmpty(item));
    }

    /** The main hand's item; {@code getItemInHand} is the legacy name for the same slot. */
    @Override
    public org.bukkit.inventory.ItemStack getItemInHand() {
        return getItemInMainHand();
    }

    /** Sets the main hand's item; the legacy name for the same slot. */
    @Override
    public void setItemInHand(org.bukkit.inventory.ItemStack item) {
        setItemInMainHand(item);
    }

    /** By slot, for the slots the API addresses this way. */
    @Override
    public org.bukkit.inventory.ItemStack getItem(org.bukkit.inventory.EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> getItemInMainHand();
            case OFF_HAND -> getItemInOffHand();
            case HEAD -> getHelmet();
            case CHEST -> getChestplate();
            case LEGS -> getLeggings();
            case FEET -> getBoots();
            // The body and saddle slots belong to entities that wear them (horses, wolves) and a
            // player never has one, so the game answers air and so does this.
            case BODY, SADDLE -> ForbricItemStack.plain(org.bukkit.Material.AIR, 0);
        };
    }

    /** By slot, for the slots the API addresses this way. */
    @Override
    public void setItem(org.bukkit.inventory.EquipmentSlot slot, org.bukkit.inventory.ItemStack item) {
        switch (slot) {
            case HAND -> setItemInMainHand(item);
            case OFF_HAND -> setItemInOffHand(item);
            case HEAD -> setHelmet(item);
            case CHEST -> setChestplate(item);
            case LEGS -> setLeggings(item);
            case FEET -> setBoots(item);
            // A player has no body or saddle slot; the game's own setter would refuse, and this
            // says so plainly rather than writing somewhere unexpected.
            case BODY, SADDLE -> throw new IllegalArgumentException(
                            "[Forbric/Bukkit] a player has no " + slot + " equipment slot");
        }
    }

    /** Which hotbar slot is selected, 0-8. */
    @Override
    public int getHeldItemSlot() {
        return inventory().getSelectedSlot();
    }

    /**
     * Selects a hotbar slot.
     *
     * <p>Refuses an out-of-range slot instead of clamping: a plugin that picked slot 9 has a bug,
     * and silently giving it slot 8 would hide it.
     */
    @Override
    public void setHeldItemSlot(int slot) {
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException(
                            "[Forbric/Bukkit] the held item slot is 0-8, got " + slot);
        }
        inventory().setSelectedSlot(slot);
    }

    /** The player this inventory belongs to. */
    @Override
    public org.bukkit.entity.HumanEntity getHolder() {
        return ForbricPlayer.of(player, server);
    }

    /** The API's answer for a player inventory: there is no separate view of the armour. */
    @Override
    public org.bukkit.inventory.ItemStack[] getExtraContents() {
        return new org.bukkit.inventory.ItemStack[0];
    }

    /** Refuses: the extra contents are empty, so there is nothing to set. */
    @Override
    public void setExtraContents(org.bukkit.inventory.ItemStack[] items) {
        throw new UnsupportedOperationException(
                        "[Forbric/Bukkit] PlayerInventory.setExtraContents() has no meaning for a player"
                                        + " inventory in this bridge");
    }

    /** Refuses, and names the missing container layer. */
    @Override
    public int getMaxStackSize() {
        return 64;
    }

    /** Refuses: a per-inventory stack limit belongs to a container this bridge has not built. */
    @Override
    public void setMaxStackSize(int size) {
        throw new UnsupportedOperationException(
                        "[Forbric/Bukkit] PlayerInventory.setMaxStackSize() is not implemented by this bridge"
                                        + " yet (a player inventory's limit is the game's)");
    }

    /** The inventory's kind, as the API names a player's. */
    @Override
    public org.bukkit.event.inventory.InventoryType getType() {
        return org.bukkit.event.inventory.InventoryType.PLAYER;
    }

    /** The API's two-argument form; the holder is the same either way for a player inventory. */
    @Override
    public org.bukkit.inventory.InventoryHolder getHolder(boolean useSnapshot) {
        return getHolder();
    }

    /** The Iterator a plugin gets from {@code for (ItemStack s : inventory)}. */
    @Override
    public ListIterator<org.bukkit.inventory.ItemStack> iterator() {
        return iterator(0);
    }

    /**
     * An iterator over the slots, from an index.
     *
     * <p>Unlike {@link java.util.ArrayList}'s, this one is <b>mutable and writes through</b>: a
     * plugin that sets an element in the list changes the inventory, which is the API's contract.
     * The index is also allowed to start anywhere, which is why this is not a view of an array.
     */
    @Override
    public java.util.ListIterator<org.bukkit.inventory.ItemStack> iterator(int index) {
        return new SlotIterator(index, getSize());
    }

    /**
     * A list iterator over the inventory's slots that writes back.
     *
     * <p>{@code set} and {@code remove} are the two a plugin realistically uses on an inventory
     * iterator; {@code add} is not part of the API's contract for a fixed-size inventory and is
     * refused rather than inserting at an index that means nothing.
     */
    private final class SlotIterator implements java.util.ListIterator<org.bukkit.inventory.ItemStack> {

        private int cursor;

        private final int size;

        SlotIterator(int start, int size) {
            this.cursor = start;
            this.size = size;
        }

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        public org.bukkit.inventory.ItemStack next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException("past the end of the inventory");
            }
            return getItem(cursor++);
        }

        @Override
        public boolean hasPrevious() {
            return cursor > 0;
        }

        @Override
        public org.bukkit.inventory.ItemStack previous() {
            if (!hasPrevious()) {
                throw new java.util.NoSuchElementException("before the start of the inventory");
            }
            return getItem(--cursor);
        }

        @Override
        public int nextIndex() {
            return cursor;
        }

        @Override
        public int previousIndex() {
            return cursor - 1;
        }

        /** Clears the slot the iterator last returned. */
        @Override
        public void remove() {
            if (cursor <= 0) {
                throw new IllegalStateException("remove() before next()");
            }
            clear(cursor - 1);
        }

        /** Writes the slot the iterator last returned, which is the API's documented behaviour. */
        @Override
        public void set(org.bukkit.inventory.ItemStack item) {
            if (cursor <= 0) {
                throw new IllegalStateException("set() before next()");
            }
            setItem(cursor - 1, item);
        }

        /** Refuses: a player inventory has a fixed size. */
        @Override
        public void add(org.bukkit.inventory.ItemStack item) {
            throw new UnsupportedOperationException(
                            "[Forbric/Bukkit] a player inventory has a fixed size; use addItem(...)");
        }
    }

    // ---- Not implemented --------------------------------------------------------------------------
    //
    // The container layer: opening a window, the stack on the cursor, and who is looking at it.
    // Written out rather than inherited so the failure carries the missing feature's name.

    private static UnsupportedOperationException notYet(String what) {
        return new UnsupportedOperationException(
                        "[Forbric/Bukkit] PlayerInventory." + what + "() is not implemented by this bridge"
                                        + " yet (needs the container layer)");
    }

    @Override
    public int close() {
        throw notYet("close");
    }

    @Override
    public List<org.bukkit.entity.HumanEntity> getViewers() {
        throw notYet("getViewers");
    }

    @Override
    public org.bukkit.Location getLocation() {
        throw notYet("getLocation");
    }
}
