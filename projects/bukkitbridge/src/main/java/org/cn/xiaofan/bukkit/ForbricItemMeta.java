/*
 * Forbric Bukkit bridge — an item's metadata, as {@code org.bukkit.inventory.meta.ItemMeta}.
 * *** GENERATED FILE — DO NOT EDIT ***
 *
 * Produced by tools/gen_stub.py from tools/stubs/ForbricItemMeta.table. Edits belong in the table or this
 * template.
 */
package org.cn.xiaofan.bukkit;

/**
 * An item's name, lore and plugin data, as a plugin edits them.
 *
 * <h2>The shape of this API, which is worth stating once</h2>
 *
 * <p>A {@code ItemMeta} is a <b>snapshot</b>. A plugin calls {@code item.getItemMeta()}, edits the copy,
 * and calls {@code item.setItemMeta(meta)} to write it back — edits that are not written back are
 * discarded. That is Bukkit's contract on every server, and it is why this class holds the values it read
 * rather than reading through to the item on each call: a plugin that sets a name and then reads it back
 * before writing must see the name it just set, or the API is unusable.
 *
 * <h2>Where the values live</h2>
 *
 * <p>In the game's item components: {@code minecraft:custom_name} for the name and
 * {@code minecraft:lore} for the lore. The snapshot is taken from those on construction and written back
 * to them by {@code ForbricItemStack.setItemMeta}, so a name this class holds is the name the game will
 * draw once the plugin writes it back.
 *
 * <h2>What refuses</h2>
 *
 * <p>The interface declares about a hundred and ten methods — attributes, enchantments, banners, books,
 * maps — and this bridge models four things. The rest refuse by name, which is the point of the
 * generator: a plugin calling {@code addEnchant} is told the bridge does not implement it rather than
 * being handed an item whose enchantment silently did not apply.
 */
class ForbricItemMeta implements org.bukkit.inventory.meta.ItemMeta {

    /**
     * The name as an Adventure component, or null when the item has none.
     *
     * <p>Held as the Adventure type because that is what the API hands a plugin and what it will compare
     * against; the game's own component is built from it at write-back time.
     */
    private net.kyori.adventure.text.Component displayName;

    /** The lore lines, in order. An empty list means "no lore", which is also what an absent component is. */
    private final java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();

    /**
     * The plugin's own data, backed by the game's {@code custom_data} component.
     *
     * <p>Shared with the item rather than copied: this container IS the tag inside the item's component,
     * so a {@code set} on it is visible to the item immediately and needs no write-back. That is a
     * deliberate departure from the snapshot rule above — Bukkit's own implementations behave this way,
     * and a plugin that sets a tag and forgets {@code setItemMeta} would otherwise lose it.
     */
    private final ForbricPersistentDataContainer persistentData;

    /**
     * Bukkit's per-application marker, which rounds-trips a number it never interprets.
     *
     * <p>Paper stores it on the item and expects it back verbatim; plugins use it to notice that another
     * plugin rewrote the meta. Keeping the number as given is the whole contract.
     */
    private int version;

    /** The item's plain-text name, kept separately because the API exposes both spellings. */
    private String itemName;

    ForbricItemMeta(net.kyori.adventure.text.Component displayName,
            java.util.List<net.kyori.adventure.text.Component> lore,
            ForbricPersistentDataContainer persistentData, int version, String itemName) {
        this.displayName = displayName;
        if (lore != null) {
            this.lore.addAll(lore);
        }
        this.persistentData = persistentData;
        this.version = version;
        this.itemName = itemName;
    }

    /** The snapshot's lore, for {@code ForbricItemStack.setItemMeta} to write back. */
    java.util.List<net.kyori.adventure.text.Component> loreSnapshot() {
        return java.util.List.copyOf(lore);
    }

    /** The snapshot's name, for {@code ForbricItemStack.setItemMeta} to write back. */
    net.kyori.adventure.text.Component nameSnapshot() {
        return displayName;
    }

    /** The container itself, which is shared with the item rather than copied. */
    ForbricPersistentDataContainer container() {
        return persistentData;
    }

    private static UnsupportedOperationException notYet(String what) {
        return new UnsupportedOperationException(
                "[Forbric/Bukkit] ItemMeta." + what + "() is not implemented by this bridge yet");
    }

    /**
     * The item's display name, or null when it has none.
     *
     * <p>Null rather than an empty component: Bukkit distinguishes "no name" from "an empty name", and a
     * plugin that branches on {@code getDisplayName() == null} to decide whether to name the item would
     * be given the wrong answer by an empty component.
     */
    @Override
    public net.kyori.adventure.text.Component displayName() {
        return displayName;
    }

    /**
     * Sets the item's display name.
     *
     * <p>A null clears it, which is what the API documents and what a plugin restoring an item's original
     * look relies on.
     */
    @Override
    public void displayName(net.kyori.adventure.text.Component name) {
        this.displayName = name;
    }

    /**
     * The display name as legacy text.
     *
     * <p>Rendered through Adventure's section-code serializer, which is the format this accessor's name
     * refers to on a modern server. A plugin that set a component name and reads it back here gets the
     * same colours and decorations, in the {@code §}-prefixed form.
     */
    @Override
    public String getDisplayName() {
        if (displayName == null) {
            return null;
        }
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(displayName);
    }

    /**
     * Sets the display name from legacy text.
     *
     * <p>The {@code §} codes in the string are parsed as formatting rather than shown literally, which is
     * what a plugin writing {@code "&c}..."-style text expects after converting it.
     */
    @Override
    public void setDisplayName(String name) {
        if (name == null) {
            this.displayName = null;
            return;
        }
        this.displayName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(name);
    }

    /** The lore lines, as a live list a plugin may edit in place, which is what Bukkit's contract says. */
    @Override
    public java.util.List<net.kyori.adventure.text.Component> lore() {
        return lore;
    }

    /** Replaces the lore wholesale. A null or empty list removes it. */
    @Override
    public void lore(java.util.List<? extends net.kyori.adventure.text.Component> lines) {
        lore.clear();
        if (lines != null) {
            lore.addAll(lines);
        }
    }

    /** Whether the item has any lore, which is one question and not "does the list exist". */
    @Override
    public boolean hasLore() {
        return !lore.isEmpty();
    }

    /**
     * The item's plain name, which is a different thing from its display name.
     *
     * <p>Bukkit keeps both: the name is what the item IS ("Diamond Sword"), the display name is what it
     * LOOKS like. A plugin that sets only the display name leaves this null, which is why reading it back
     * after setting a display name answers null rather than the same text.
     */
    @Override
    public String getItemName() {
        return itemName;
    }

    /** Sets the plain name. */
    @Override
    public void setItemName(String name) {
        this.itemName = name;
    }

    /**
     * The plugin data container, which is where a plugin tags its own items.
     *
     * <p>Never null: an item with no data still has a container, and it is empty. A plugin that checks for
     * null here before writing — a habit formed on servers that returned null — still works; one that
     * writes straight away works too, which is the point.
     */
    @Override
    public ForbricPersistentDataContainer getPersistentDataContainer() {
        return persistentData;
    }

    /**
     * Bukkit's own marker number, returned as it was given.
     *
     * <p>Not an {@code @Override}: the interface declares only the setter, and Paper reads the value
     * back through {@code getVersionFromItemStack}-style helpers this bridge does not model. Keeping
     * both spellings here lets a plugin set and read the marker on the same object.
     */
    public int getVersion() {
        return version;
    }

    /** Sets the marker number, which this bridge only stores. */
    @Override
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * A copy a plugin may edit without touching this one.
     *
     * <p>The lore list and the container are copied rather than shared — a clone whose edits reached the
     * original would not be a clone, and Bukkit's contract is that a plugin can build a variant of an
     * item by cloning its meta.
     */
    @Override
    public ForbricItemMeta clone() {
        return new ForbricItemMeta(displayName, java.util.List.copyOf(lore),
                new ForbricPersistentDataContainer(persistentData.tag().copy()), version, itemName);
    }

    @Override
    public String toString() {
        return "ForbricItemMeta[name=" + displayName + ", lore=" + lore.size() + "]";
    }

    /**
     * The item's custom name, which is the component form of the display name.
     *
     * <p><b>This is the pair that actually matters.</b> Paper's interface declares the old
     * {@code getDisplayName}/{@code setDisplayName} string accessors AND this Adventure component pair
     * — and {@code displayName(Component)} is a {@code default} method that forwards HERE, to
     * {@code customName}. So a plugin that calls {@code meta.displayName(component)} — which is how
     * every modern plugin names an item — reaches this method and not the string one. Answering only
     * the string pair left that call throwing, and a plugin with no try/catch around it lost the name
     * silently if its caller swallowed the error.
     */
    @Override
    public net.kyori.adventure.text.Component customName() {
        return displayName;
    }

    /** Sets the custom name, which is the same value {@code getDisplayName} renders. */
    @Override
    public void customName(net.kyori.adventure.text.Component name) {
        this.displayName = name;
    }

    /** Whether the item has a custom name at all, which is a different question from an empty one. */
    @Override
    public boolean hasCustomName() {
        return displayName != null;
    }

    /**
     * The item's name, in the Adventure form.
     *
     * <p>The same value as {@link #customName()}: Bukkit's newer spelling, and the one
     * {@code itemName(Component)} forwards to. Kept as one field because the game stores one component
     * — two fields here would let a plugin reading one spelling disagree with one reading the other.
     */
    @Override
    public net.kyori.adventure.text.Component itemName() {
        return displayName;
    }

    /** Sets the name through the newer spelling. */
    @Override
    public void itemName(net.kyori.adventure.text.Component name) {
        this.displayName = name;
    }

    /**
     * The lore as plain strings, one per line.
     *
     * <p>The legacy-format view of {@link #lore()}, rendered through the section-code serializer: a
     * plugin that set lore as components and reads it here gets the same colours in {@code §} form.
     */
    @Override
    public java.util.List<String> getLore() {
        java.util.List<String> lines = new java.util.ArrayList<>(lore.size());
        for (net.kyori.adventure.text.Component line : lore) {
            lines.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(line));
        }
        return lines;
    }

	@Override
	public net.md_5.bungee.api.chat.BaseComponent[] getDisplayNameComponent() {
		throw notYet("getDisplayNameComponent");
	}

	@Override
	public void setDisplayNameComponent(net.md_5.bungee.api.chat.BaseComponent[] arg0) {
		throw notYet("setDisplayNameComponent");
	}

	@Override
	public boolean hasItemName() {
		throw notYet("hasItemName");
	}

	@Override
	public boolean hasLocalizedName() {
		throw notYet("hasLocalizedName");
	}

	@Override
	public java.lang.String getLocalizedName() {
		throw notYet("getLocalizedName");
	}

	@Override
	public void setLocalizedName(java.lang.String arg0) {
		throw notYet("setLocalizedName");
	}

	@Override
	public java.util.List<net.md_5.bungee.api.chat.BaseComponent[]> getLoreComponents() {
		throw notYet("getLoreComponents");
	}

	@Override
	public void setLore(java.util.List<java.lang.String> arg0) {
		throw notYet("setLore");
	}

	@Override
	public void setLoreComponents(java.util.List<net.md_5.bungee.api.chat.BaseComponent[]> arg0) {
		throw notYet("setLoreComponents");
	}

	@Override
	public boolean hasCustomModelData() {
		throw notYet("hasCustomModelData");
	}

	@Override
	public int getCustomModelData() {
		throw notYet("getCustomModelData");
	}

	@Override
	public org.bukkit.inventory.meta.components.CustomModelDataComponent getCustomModelDataComponent() {
		throw notYet("getCustomModelDataComponent");
	}

	@Override
	public void setCustomModelData(java.lang.Integer arg0) {
		throw notYet("setCustomModelData");
	}

	@Override
	public boolean hasCustomModelDataComponent() {
		throw notYet("hasCustomModelDataComponent");
	}

	@Override
	public void setCustomModelDataComponent(org.bukkit.inventory.meta.components.CustomModelDataComponent arg0) {
		throw notYet("setCustomModelDataComponent");
	}

	@Override
	public boolean hasEnchantable() {
		throw notYet("hasEnchantable");
	}

	@Override
	public int getEnchantable() {
		throw notYet("getEnchantable");
	}

	@Override
	public void setEnchantable(java.lang.Integer arg0) {
		throw notYet("setEnchantable");
	}

	@Override
	public boolean hasEnchants() {
		throw notYet("hasEnchants");
	}

	@Override
	public boolean hasEnchant(org.bukkit.enchantments.Enchantment arg0) {
		throw notYet("hasEnchant");
	}

	@Override
	public int getEnchantLevel(org.bukkit.enchantments.Enchantment arg0) {
		throw notYet("getEnchantLevel");
	}

	@Override
	public java.util.Map<org.bukkit.enchantments.Enchantment, java.lang.Integer> getEnchants() {
		throw notYet("getEnchants");
	}

	@Override
	public boolean addEnchant(org.bukkit.enchantments.Enchantment arg0, int arg1, boolean arg2) {
		throw notYet("addEnchant");
	}

	@Override
	public boolean removeEnchant(org.bukkit.enchantments.Enchantment arg0) {
		throw notYet("removeEnchant");
	}

	@Override
	public void removeEnchantments() {
		throw notYet("removeEnchantments");
	}

	@Override
	public boolean hasConflictingEnchant(org.bukkit.enchantments.Enchantment arg0) {
		throw notYet("hasConflictingEnchant");
	}

	@Override
	public void addItemFlags(org.bukkit.inventory.ItemFlag... arg0) {
		throw notYet("addItemFlags");
	}

	@Override
	public void removeItemFlags(org.bukkit.inventory.ItemFlag... arg0) {
		throw notYet("removeItemFlags");
	}

	@Override
	public java.util.Set<org.bukkit.inventory.ItemFlag> getItemFlags() {
		throw notYet("getItemFlags");
	}

	@Override
	public boolean hasItemFlag(org.bukkit.inventory.ItemFlag arg0) {
		throw notYet("hasItemFlag");
	}

	@Override
	public boolean isHideTooltip() {
		throw notYet("isHideTooltip");
	}

	@Override
	public void setHideTooltip(boolean arg0) {
		throw notYet("setHideTooltip");
	}

	@Override
	public boolean hasTooltipStyle() {
		throw notYet("hasTooltipStyle");
	}

	@Override
	public org.bukkit.NamespacedKey getTooltipStyle() {
		throw notYet("getTooltipStyle");
	}

	@Override
	public void setTooltipStyle(org.bukkit.NamespacedKey arg0) {
		throw notYet("setTooltipStyle");
	}

	@Override
	public boolean hasItemModel() {
		throw notYet("hasItemModel");
	}

	@Override
	public org.bukkit.NamespacedKey getItemModel() {
		throw notYet("getItemModel");
	}

	@Override
	public void setItemModel(org.bukkit.NamespacedKey arg0) {
		throw notYet("setItemModel");
	}

	@Override
	public boolean isUnbreakable() {
		throw notYet("isUnbreakable");
	}

	@Override
	public void setUnbreakable(boolean arg0) {
		throw notYet("setUnbreakable");
	}

	@Override
	public boolean hasEnchantmentGlintOverride() {
		throw notYet("hasEnchantmentGlintOverride");
	}

	@Override
	public java.lang.Boolean getEnchantmentGlintOverride() {
		throw notYet("getEnchantmentGlintOverride");
	}

	@Override
	public void setEnchantmentGlintOverride(java.lang.Boolean arg0) {
		throw notYet("setEnchantmentGlintOverride");
	}

	@Override
	public boolean isGlider() {
		throw notYet("isGlider");
	}

	@Override
	public void setGlider(boolean arg0) {
		throw notYet("setGlider");
	}

	@Override
	public boolean isFireResistant() {
		throw notYet("isFireResistant");
	}

	@Override
	public void setFireResistant(boolean arg0) {
		throw notYet("setFireResistant");
	}

	@Override
	public boolean hasDamageResistant() {
		throw notYet("hasDamageResistant");
	}

	@Override
	public org.bukkit.Tag<org.bukkit.damage.DamageType> getDamageResistant() {
		throw notYet("getDamageResistant");
	}

	@Override
	public void setDamageResistant(org.bukkit.Tag<org.bukkit.damage.DamageType> arg0) {
		throw notYet("setDamageResistant");
	}

	@Override
	public io.papermc.paper.registry.set.RegistryKeySet<org.bukkit.damage.DamageType> getDamageResistantTypes() {
		throw notYet("getDamageResistantTypes");
	}

	@Override
	public void setDamageResistantTypes(io.papermc.paper.registry.set.RegistryKeySet<org.bukkit.damage.DamageType> arg0) {
		throw notYet("setDamageResistantTypes");
	}

	@Override
	public boolean hasMaxStackSize() {
		throw notYet("hasMaxStackSize");
	}

	@Override
	public int getMaxStackSize() {
		throw notYet("getMaxStackSize");
	}

	@Override
	public void setMaxStackSize(java.lang.Integer arg0) {
		throw notYet("setMaxStackSize");
	}

	@Override
	public boolean hasRarity() {
		throw notYet("hasRarity");
	}

	@Override
	public org.bukkit.inventory.ItemRarity getRarity() {
		throw notYet("getRarity");
	}

	@Override
	public void setRarity(org.bukkit.inventory.ItemRarity arg0) {
		throw notYet("setRarity");
	}

	@Override
	public boolean hasUseRemainder() {
		throw notYet("hasUseRemainder");
	}

	@Override
	public org.bukkit.inventory.ItemStack getUseRemainder() {
		throw notYet("getUseRemainder");
	}

	@Override
	public void setUseRemainder(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("setUseRemainder");
	}

	@Override
	public boolean hasUseCooldown() {
		throw notYet("hasUseCooldown");
	}

	@Override
	public org.bukkit.inventory.meta.components.UseCooldownComponent getUseCooldown() {
		throw notYet("getUseCooldown");
	}

	@Override
	public void setUseCooldown(org.bukkit.inventory.meta.components.UseCooldownComponent arg0) {
		throw notYet("setUseCooldown");
	}

	@Override
	public boolean hasFood() {
		throw notYet("hasFood");
	}

	@Override
	public org.bukkit.inventory.meta.components.FoodComponent getFood() {
		throw notYet("getFood");
	}

	@Override
	public void setFood(org.bukkit.inventory.meta.components.FoodComponent arg0) {
		throw notYet("setFood");
	}

	@Override
	public boolean hasTool() {
		throw notYet("hasTool");
	}

	@Override
	public org.bukkit.inventory.meta.components.ToolComponent getTool() {
		throw notYet("getTool");
	}

	@Override
	public void setTool(org.bukkit.inventory.meta.components.ToolComponent arg0) {
		throw notYet("setTool");
	}

	@Override
	public boolean hasEquippable() {
		throw notYet("hasEquippable");
	}

	@Override
	public org.bukkit.inventory.meta.components.EquippableComponent getEquippable() {
		throw notYet("getEquippable");
	}

	@Override
	public void setEquippable(org.bukkit.inventory.meta.components.EquippableComponent arg0) {
		throw notYet("setEquippable");
	}

	@Override
	public boolean hasJukeboxPlayable() {
		throw notYet("hasJukeboxPlayable");
	}

	@Override
	public org.bukkit.inventory.meta.components.JukeboxPlayableComponent getJukeboxPlayable() {
		throw notYet("getJukeboxPlayable");
	}

	@Override
	public void setJukeboxPlayable(org.bukkit.inventory.meta.components.JukeboxPlayableComponent arg0) {
		throw notYet("setJukeboxPlayable");
	}

	@Override
	public boolean hasAttributeModifiers() {
		throw notYet("hasAttributeModifiers");
	}

	@Override
	public com.google.common.collect.Multimap<org.bukkit.attribute.Attribute, org.bukkit.attribute.AttributeModifier> getAttributeModifiers() {
		throw notYet("getAttributeModifiers");
	}

	@Override
	public com.google.common.collect.Multimap<org.bukkit.attribute.Attribute, org.bukkit.attribute.AttributeModifier> getAttributeModifiers(org.bukkit.inventory.EquipmentSlot arg0) {
		throw notYet("getAttributeModifiers");
	}

	@Override
	public java.util.Collection<org.bukkit.attribute.AttributeModifier> getAttributeModifiers(org.bukkit.attribute.Attribute arg0) {
		throw notYet("getAttributeModifiers");
	}

	@Override
	public boolean addAttributeModifier(org.bukkit.attribute.Attribute arg0, org.bukkit.attribute.AttributeModifier arg1) {
		throw notYet("addAttributeModifier");
	}

	@Override
	public void setAttributeModifiers(com.google.common.collect.Multimap<org.bukkit.attribute.Attribute, org.bukkit.attribute.AttributeModifier> arg0) {
		throw notYet("setAttributeModifiers");
	}

	@Override
	public boolean removeAttributeModifier(org.bukkit.attribute.Attribute arg0) {
		throw notYet("removeAttributeModifier");
	}

	@Override
	public boolean removeAttributeModifier(org.bukkit.inventory.EquipmentSlot arg0) {
		throw notYet("removeAttributeModifier");
	}

	@Override
	public boolean removeAttributeModifier(org.bukkit.attribute.Attribute arg0, org.bukkit.attribute.AttributeModifier arg1) {
		throw notYet("removeAttributeModifier");
	}

	@Override
	public java.lang.String getAsString() {
		throw notYet("getAsString");
	}

	@Override
	public java.lang.String getAsComponentString() {
		throw notYet("getAsComponentString");
	}

	@Override
	public org.bukkit.inventory.meta.tags.CustomItemTagContainer getCustomTagContainer() {
		throw notYet("getCustomTagContainer");
	}

	@Override
	public java.util.Set<org.bukkit.Material> getCanDestroy() {
		throw notYet("getCanDestroy");
	}

	@Override
	public void setCanDestroy(java.util.Set<org.bukkit.Material> arg0) {
		throw notYet("setCanDestroy");
	}

	@Override
	public java.util.Set<org.bukkit.Material> getCanPlaceOn() {
		throw notYet("getCanPlaceOn");
	}

	@Override
	public void setCanPlaceOn(java.util.Set<org.bukkit.Material> arg0) {
		throw notYet("setCanPlaceOn");
	}

	@Override
	public java.util.Set<com.destroystokyo.paper.Namespaced> getDestroyableKeys() {
		throw notYet("getDestroyableKeys");
	}

	@Override
	public void setDestroyableKeys(java.util.Collection<com.destroystokyo.paper.Namespaced> arg0) {
		throw notYet("setDestroyableKeys");
	}

	@Override
	public java.util.Set<com.destroystokyo.paper.Namespaced> getPlaceableKeys() {
		throw notYet("getPlaceableKeys");
	}

	@Override
	public void setPlaceableKeys(java.util.Collection<com.destroystokyo.paper.Namespaced> arg0) {
		throw notYet("setPlaceableKeys");
	}

	@Override
	public boolean hasPlaceableKeys() {
		throw notYet("hasPlaceableKeys");
	}

	@Override
	public boolean hasDestroyableKeys() {
		throw notYet("hasDestroyableKeys");
	}

	@Override
	public java.util.Map<java.lang.String, java.lang.Object> serialize() {
		throw notYet("serialize");
	}}
