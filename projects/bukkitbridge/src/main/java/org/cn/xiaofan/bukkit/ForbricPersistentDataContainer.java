/*
 * Forbric Bukkit bridge — the persistent data a plugin stores on an item.
 */
package org.cn.xiaofan.bukkit;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * A plugin's own key/value data on an item, backed by the game's {@code CustomData} component.
 *
 * <h2>What this is for</h2>
 *
 * <p>It is how a modern plugin tags an item with its own identity — {@code new ItemStack(FISHING_ROD)}
 * plus a {@code mode=stab} entry is a plugin's own item, and the tag survives a server restart, a chest,
 * and being renamed. Before this existed, a plugin that wrote one threw
 * {@code UnsupportedOperationException} from {@code getItemMeta()}, which is where the container is
 * reached from.
 *
 * <h2>The storage, and why it is NBT rather than a map</h2>
 *
 * <p>Bukkit's container is a keyed tree that ends up as NBT. The game has exactly that in the
 * {@code minecraft:custom_data} item component, whose value is a {@code CompoundTag} — so every write
 * goes straight into the item and is persisted, synced and shown in {@code /data} for free, with no
 * bookkeeping on this side.
 *
 * <h2>Nesting and the round trip</h2>
 *
 * <p>{@code TAG_CONTAINER} stores a child container under a key. The game's {@code CompoundTag} nests the
 * same way, so a child is a real sub-tag and not a flattened key with a dot in it. That matters because a
 * plugin that writes {@code a.b} as a key and one that writes a child container {@code b} under
 * {@code a} must not collide — the game keeps them apart, so this does too.
 */
final class ForbricPersistentDataContainer implements PersistentDataContainer {

    /** The tag this container reads and writes, shared with the item it came from. */
    private final net.minecraft.nbt.CompoundTag tag;

    ForbricPersistentDataContainer(net.minecraft.nbt.CompoundTag tag) {
        this.tag = tag == null ? new net.minecraft.nbt.CompoundTag() : tag;
    }

    /** A copy of the tag, for writing back into an item. */
    net.minecraft.nbt.CompoundTag tag() {
        return tag;
    }

    @Override
    public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        if (key == null || type == null) {
            return;
        }
        if (value == null) {
            // Bukkit treats a null value as a removal, which is what a plugin clearing its own tag means.
            remove(key);
            return;
        }
        P primitive = type.toPrimitive(value, getAdapterContext());
        if (primitive == null) {
            remove(key);
            return;
        }
        write(key.toString(), primitive);
    }

    @Override
    public void remove(NamespacedKey key) {
        if (key != null) {
            tag.remove(key.toString());
        }
    }

    @Override
    public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
        if (key == null || type == null) {
            return false;
        }
        // A key present with the wrong type is not a match: the game stores the primitive, and only the
        // type that owns it can read it back, so answering true here would make get() return null.
        return tag.contains(key.toString());
    }

    @Override
    public boolean has(NamespacedKey key) {
        return key != null && tag.contains(key.toString());
    }

    @Override
    public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
        if (key == null || type == null) {
            return null;
        }
        P primitive = read(key.toString(), type.getPrimitiveType());
        return primitive == null ? null : type.fromPrimitive(primitive, getAdapterContext());
    }

    @Override
    public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C fallback) {
        C value = get(key, type);
        return value == null ? fallback : value;
    }

    @Override
    public Set<NamespacedKey> getKeys() {
        Set<NamespacedKey> keys = new LinkedHashSet<>();
        for (String name : tag.keySet()) {
            // A key that is not "namespace:path" cannot have come from Bukkit, so it is skipped rather
            // than turned into a broken key: the game's own compound may hold anything a mod wrote.
            NamespacedKey key = NamespacedKey.fromString(name);
            if (key != null) {
                keys.add(key);
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    @Override
    public boolean isEmpty() {
        return tag.isEmpty();
    }

    @Override
    public int getSize() {
        return tag.size();
    }

    @Override
    public PersistentDataAdapterContext getAdapterContext() {
        return ForbricPersistentDataContext.INSTANCE;
    }

    @Override
    public void copyTo(PersistentDataContainer other, boolean replace) {
        if (!(other instanceof ForbricPersistentDataContainer target)) {
            return;
        }
        if (replace) {
            for (String name : new java.util.ArrayList<>(target.tag.keySet())) {
                target.tag.remove(name);
            }
        }
        for (String name : tag.keySet()) {
            target.tag.put(name, tag.get(name).copy());
        }
    }

    @Override
    public byte[] serializeToBytes() {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(tag, out);
            return out.toByteArray();
        } catch (java.io.IOException failed) {
            // Bukkit declares no checked exception here, and a caller asking for bytes it cannot have is
            // better served by an empty array than by a crash inside an unrelated inventory operation.
            return new byte[0];
        }
    }

    @Override
    public void readFromBytes(byte[] bytes, boolean clear) {
        if (bytes == null) {
            return;
        }
        if (clear) {
            for (String name : new java.util.ArrayList<>(tag.keySet())) {
                tag.remove(name);
            }
        }
        try {
            net.minecraft.nbt.CompoundTag read = net.minecraft.nbt.NbtIo.readCompressed(
                    new java.io.ByteArrayInputStream(bytes),
                    net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            for (String name : read.keySet()) {
                tag.put(name, read.get(name).copy());
            }
        } catch (java.io.IOException unreadable) {
            // Same contract as serializeToBytes: the container keeps what it had rather than throwing into
            // a caller that has no way to react.
        }
    }

    /**
     * Writes one primitive under a name, in the type the game's tag understands.
     *
     * <p>The primitive types Bukkit allows are exactly the ones {@code Tag} has, so this is a mapping and
     * not a conversion. An unsupported one is refused by name rather than stored as its {@code toString}:
     * a plugin that reads it back as a different type would get a wrong value with no idea why.
     */
    private void write(String name, Object primitive) {
        if (primitive instanceof String text) {
            tag.putString(name, text);
        } else if (primitive instanceof Byte value) {
            tag.putByte(name, value);
        } else if (primitive instanceof Short value) {
            tag.putShort(name, value);
        } else if (primitive instanceof Integer value) {
            tag.putInt(name, value);
        } else if (primitive instanceof Long value) {
            tag.putLong(name, value);
        } else if (primitive instanceof Float value) {
            tag.putFloat(name, value);
        } else if (primitive instanceof Double value) {
            tag.putDouble(name, value);
        } else if (primitive instanceof byte[] value) {
            tag.putByteArray(name, value);
        } else if (primitive instanceof int[] value) {
            tag.putIntArray(name, value);
        } else if (primitive instanceof long[] value) {
            tag.putLongArray(name, value);
        } else if (primitive instanceof ForbricPersistentDataContainer nested) {
            tag.put(name, nested.tag.copy());
        } else {
            throw new UnsupportedOperationException(
                    "[Forbric/Bukkit] PersistentDataContainer cannot store " + primitive.getClass().getName()
                            + " yet: this bridge writes the primitive types the game's NBT tag has");
        }
    }

    /**
     * Reads one primitive back, in the type the caller says it is.
     *
     * <p>The read is by the DECLARED primitive class, so a value stored as an int is not returned to a
     * caller asking for a long — Bukkit's types are distinct and a plugin relies on that.
     */
    @SuppressWarnings("unchecked")
    private <P> P read(String name, Class<P> primitiveType) {
        if (primitiveType == String.class) {
            // getString returns "" for an absent key, and an empty value is a legitimate thing to store,
            // so presence is checked separately rather than inferred from the value.
            return (P) (tag.contains(name) ? tag.getString(name).orElse("") : null);
        }
        if (primitiveType == Byte.class) {
            return (P) (tag.contains(name) ? tag.getByte(name).orElse(null) : null);
        }
        if (primitiveType == Short.class) {
            return (P) (tag.contains(name) ? tag.getShort(name).orElse(null) : null);
        }
        if (primitiveType == Integer.class) {
            return (P) (tag.contains(name) ? tag.getInt(name).orElse(null) : null);
        }
        if (primitiveType == Long.class) {
            return (P) (tag.contains(name) ? tag.getLong(name).orElse(null) : null);
        }
        if (primitiveType == Float.class) {
            return (P) (tag.contains(name) ? tag.getFloat(name).orElse(null) : null);
        }
        if (primitiveType == Double.class) {
            return (P) (tag.contains(name) ? tag.getDouble(name).orElse(null) : null);
        }
        if (primitiveType == byte[].class) {
            return (P) (tag.contains(name) ? tag.getByteArray(name).orElse(null) : null);
        }
        if (primitiveType == int[].class) {
            return (P) (tag.contains(name) ? tag.getIntArray(name).orElse(null) : null);
        }
        if (primitiveType == long[].class) {
            return (P) (tag.contains(name) ? tag.getLongArray(name).orElse(null) : null);
        }
        throw new UnsupportedOperationException(
                "[Forbric/Bukkit] PersistentDataContainer cannot read " + primitiveType.getName()
                        + " yet: this bridge reads the primitive types the game's NBT tag has");
    }

    @Override
    public String toString() {
        return "ForbricPersistentDataContainer" + tag;
    }

    /**
     * The factory a {@code PersistentDataType} uses to build a nested container.
     *
     * <p>Only reachable through a type's {@code fromPrimitive}/{@code toPrimitive}, which is how a
     * plugin stores a container inside a container. The child is a fresh, empty container with its own
     * tag — a nested one, not a view onto the parent — because that is what the parent's tag will hold
     * once the plugin writes the child under its key.
     */
    static final class ForbricPersistentDataContext implements PersistentDataAdapterContext {

        static final ForbricPersistentDataContext INSTANCE = new ForbricPersistentDataContext();

        private ForbricPersistentDataContext() {
        }

        @Override
        public ForbricPersistentDataContainer newPersistentDataContainer() {
            return new ForbricPersistentDataContainer(new net.minecraft.nbt.CompoundTag());
        }
    }
}
