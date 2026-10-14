/*
 * Forbric Bukkit bridge — the {@code RegistryAccess} Paper's API insists on finding.
 */
package org.cn.xiaofan.bukkit;

import java.util.Collection;
import java.util.stream.Stream;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;

/**
 * The game's registries, in the shape {@code org.bukkit.Registry} asks for them.
 *
 * <p><b>Why this class has to exist.</b> {@code Registry.ITEM} and its siblings are not plain
 * fields: their static initialiser calls {@code RegistryAccess.getRegistry(key)}, and
 * {@code RegistryAccess.registryAccess()} is a {@code ServiceLoader} lookup, exactly like
 * {@code InternalAPIBridge}. On Paper the provider lives in the server jar; a bridge that ships only
 * the API has none, so the lookup comes back empty and the API throws — the first time any plugin
 * touches a registry.
 *
 * <p><b>Why it matters beyond one registry.</b> {@code Material.asItemType()} resolves its
 * {@code ItemType} through {@code Registry.ITEM}, and {@code ItemStack.of(material, amount)} goes
 * through {@code asItemType().createItemStack()}. So this provider is on the path of every
 * {@code new ItemStack(...)} a plugin writes — without it that constructor throws, which reads as a
 * broken API rather than a missing service.
 *
 * <p><b>Item types are the ones answered here.</b> {@code RegistryKey.ITEM} is what the item path
 * needs; the other registry keys a plugin may ask for are refused by name, because this bridge has
 * no {@code ItemType} table to give for them and an empty registry would make every lookup return
 * null — a plugin iterating blocks would find none, and conclude the game has no blocks.
 */
public final class ForbricRegistryAccess implements RegistryAccess {

    /** Required by {@link java.util.ServiceLoader}. */
    public ForbricRegistryAccess() {
    }

    /**
     * The {@code ItemType} registry, when the caller asks for an {@code ItemType} registry.
     *
     * <p>{@code RegistryKey} is not a real type token — it is a named handle, and Paper ships a
     * different one per registry. The check therefore has to be by the class, and
     * {@code RegistryKey.ITEM} is the only one this bridge can fill.
     */
    /**
     * The registry a {@code RegistryKey} names.
     *
     * <p><b>An unanswered key returns an EMPTY registry for the same reason the class overload does,
     * and the reason is worth repeating because the failure is so indirect.</b> {@code Registry}'s
     * static initialiser builds every legacy constant — {@code ATTRIBUTE}, {@code BLOCK},
     * {@code ENTITY_TYPE}, {@code MATERIAL} and the rest — and routes some of them through here and
     * some through {@link #getRegistry(Class)}. Throwing from EITHER poisons the whole class: the
     * exception escapes a {@code <clinit>}, leaves {@code Registry} permanently unusable, and then
     * {@code Material.asItemType()} fails too — because it reads {@code Registry.ITEM} — so a
     * plugin's {@code new ItemStack(...)} dies and the stack trace blames the plugin.
     *
     * <p>Only {@code ITEM} can actually be filled by this bridge. Everything else answers empty, which
     * satisfies {@code Registry}'s contract that a registry exists and makes every lookup return null
     * — the same answer Bukkit documents for an entry that is not there.
     */
    @Override
    public <T extends org.bukkit.Keyed> org.bukkit.Registry<T> getRegistry(RegistryKey<T> key) {
        if (key == RegistryKey.ITEM) {
            @SuppressWarnings("unchecked")
            org.bukkit.Registry<T> items = (org.bukkit.Registry<T>) ForbricItemRegistry.INSTANCE;
            return items;
        }
        if (key == RegistryKey.SOUND_EVENT) {
            @SuppressWarnings("unchecked")
            org.bukkit.Registry<T> sounds = (org.bukkit.Registry<T>) ForbricSoundRegistry.INSTANCE;
            return sounds;
        }
        return new ForbricEmptyRegistry<>(key);
    }

    /**
     * The same, by class, which is how {@code Registry.MATERIAL} and the older registry constants
     * reach it.
     *
     * <p><b>An unknown type answers with an EMPTY registry, not an exception.</b> This method is on
     * the static initialiser of {@code org.bukkit.Registry}, which builds every legacy constant in
     * one {@code <clinit>} — {@code ART}, {@code BANNER_PATTERN}, {@code BIOME}, {@code CAT_VARIANT}
     * and the rest. Each goes through {@code Registry.legacyRegistryFor}, which calls this and then
     * {@code Objects.requireNonNull}. Paper's contract is that a registry exists for the life of the
     * server, so the contract is satisfied by a registry that is empty rather than by none at all.
     *
     * <p>Throwing here is catastrophic in a way that is hard to read from the failure: the exception
     * propagates out of a static initialiser, so the class {@code Registry} is left permanently
     * unusable ({@code ExceptionInInitializerError}). {@code Material.asItemType()} then fails too,
     * because it reads {@code Registry.ITEM} — so a plugin's {@code new ItemStack(...)} dies, and the
     * stack trace points at the plugin's own line, not at the missing registry. The one type this
     * bridge can actually fill is {@code ItemType}; everything else is answered honestly as empty,
     * and a lookup in an empty registry returns null, which is the same answer a plugin gets for a
     * name that does not exist.
     */
    @Override
    public <T extends org.bukkit.Keyed> org.bukkit.Registry<T> getRegistry(Class<T> type) {
        if (type == org.bukkit.inventory.ItemType.class) {
            @SuppressWarnings("unchecked")
            org.bukkit.Registry<T> items = (org.bukkit.Registry<T>) ForbricItemRegistry.INSTANCE;
            return items;
        }
        return new ForbricEmptyRegistry<>(type);
    }

    /**
     * A registry this bridge cannot fill, answering "nothing here" rather than refusing to exist.
     *
     * <p>Every read is empty and every lookup is null. Nothing can be written to it, so a plugin that
     * iterates one sees no entries instead of an exception, and one that looks up a name sees null —
     * which is what Bukkit's own {@code Registry} documents for an absent entry.
     *
     * <p><b>Returning this is not a fallback; it is the only shape that works.</b> {@code Registry}'s
     * static initialiser runs {@code Objects.requireNonNull} over the result of the class overload,
     * so {@code null} is not an option there; and the key overload feeds the same {@code <clinit>}, so
     * throwing from either leaves {@code Registry} permanently unusable and takes {@code Material} and
     * {@code ItemStack} down with it. An empty registry satisfies "the registry exists" while
     * answering every query the way an absent entry does.
     *
     * <p>The description is only used in messages, and both overloads pass their own so a plugin that
     * asks for something specific sees that name in the error rather than a generic one.
     */
    static final class ForbricEmptyRegistry<T extends org.bukkit.Keyed> implements org.bukkit.Registry<T> {

        /** What was asked for, for messages: a class simple name or a key's toString. */
        private final String description;

        ForbricEmptyRegistry(Class<T> type) {
            this.description = type.getSimpleName();
        }

        ForbricEmptyRegistry(RegistryKey<T> key) {
            this.description = String.valueOf(key);
        }

        @Override
        public T get(org.bukkit.NamespacedKey key) {
            return null;
        }

        // getOrThrow(NamespacedKey) is left to the interface's DEFAULT: it calls get() and raises a
        // NoSuchElementException naming the key when that returns null. Overriding it here would only
        // reproduce that, and the default already reports the key in its message.

        @Override
        public org.bukkit.NamespacedKey getKey(T value) {
            return value == null ? null : value.getKey();
        }

        @Override
        public boolean hasTag(TagKey<T> key) {
            return false;
        }

        @Override
        public Tag<T> getTag(TagKey<T> key) {
            throw new java.util.NoSuchElementException(
                            "[Forbric/Bukkit] this bridge has no " + description + " registry, so it has no tag "
                                            + key);
        }

        @Override
        public Collection<Tag<T>> getTags() {
            return java.util.List.of();
        }

        @Override
        public Stream<T> stream() {
            return Stream.empty();
        }

        @Override
        public Stream<org.bukkit.NamespacedKey> keyStream() {
            return Stream.empty();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return java.util.Collections.emptyIterator();
        }
    }

    /**
     * The {@code ItemType} registry itself.
     *
     * <p>Read from the game's item registry on every call rather than snapshotted: the world loads
     * after the service is first asked for a registry, and a snapshot taken before that would be
     * missing every item the game adds during its own bootstrap.
     *
     * <p>An empty registry before the server has installed the game's registries, and this is a
     * deliberate choice over throwing: Paper's contract is that a registry exists for the life of
     * the server, and a plugin that reads it very early — during its own bootstrap — is asking a
     * question whose answer is genuinely "nothing yet". {@link #size()} is honest about that, and
     * every lookup returns null, which is the same null a plugin gets for a name that is not in the
     * registry. Throwing here would instead break a plugin's class initialiser.
     */
    static final class ForbricItemRegistry implements org.bukkit.Registry<org.bukkit.inventory.ItemType> {

        static final ForbricItemRegistry INSTANCE = new ForbricItemRegistry();

        private ForbricItemRegistry() {
        }

        /** The game's item ids, as this bridge sees them, in the order the game keeps them. */
        private java.util.List<org.bukkit.NamespacedKey> keys() {
            net.minecraft.core.RegistryAccess registries = ForbricRegistries.access();
            if (registries == null) {
                return java.util.List.of();
            }
            return registries.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM)
                            .keySet().stream()
                            .map(id -> new org.bukkit.NamespacedKey(id.getNamespace(), id.getPath()))
                            .toList();
        }

        @Override
        public org.bukkit.inventory.ItemType get(org.bukkit.NamespacedKey key) {
            if (key == null) {
                return null;
            }
            // Whether the item exists is a question for the game's registry, not for a table here:
            // a mod-added item is as real as a vanilla one, and only the game knows it.
            net.minecraft.core.RegistryAccess registries = ForbricRegistries.access();
            if (registries == null) {
                return null;
            }
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            key.getNamespace(), key.getKey());
            if (!registries.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM).containsKey(id)) {
                return null;
            }
            return ForbricItemType.of(id);
        }

        @Override
        public org.bukkit.NamespacedKey getKey(org.bukkit.inventory.ItemType type) {
            if (type == null) {
                return null;
            }
            return type.getKey();
        }

        @Override
        public boolean hasTag(TagKey<org.bukkit.inventory.ItemType> key) {
            return false;
        }

        @Override
        public Tag<org.bukkit.inventory.ItemType> getTag(TagKey<org.bukkit.inventory.ItemType> key) {
            throw new UnsupportedOperationException(
                            "[Forbric/Bukkit] item tags are not implemented by this bridge yet");
        }

        @Override
        public Collection<Tag<org.bukkit.inventory.ItemType>> getTags() {
            return java.util.List.of();
        }

        @Override
        public Stream<org.bukkit.inventory.ItemType> stream() {
            return keys().stream().map(key -> ForbricItemType.of(
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(key.getNamespace(), key.getKey())));
        }

        @Override
        public Stream<org.bukkit.NamespacedKey> keyStream() {
            return keys().stream();
        }

        @Override
        public int size() {
            return keys().size();
        }

        @Override
        public java.util.Iterator<org.bukkit.inventory.ItemType> iterator() {
            return stream().iterator();
        }
    }

    /**
     * The game's sound events, as {@code Registry.SOUNDS}.
     *
     * <p><b>This one cannot be empty, and that is the whole reason it exists.</b> An empty registry is
     * enough for {@code Art} or {@code Attribute} — nothing in the API resolves those in a static
     * initialiser. Sounds are different: {@code org.bukkit.Sound} declares about two thousand constants
     * and builds every one of them during its own {@code <clinit>} by calling
     * {@code Registry.SOUNDS.getOrThrow(key)}. A registry that cannot answer raises
     * {@code NoSuchElementException} out of that initialiser, which leaves {@code Sound} permanently
     * unusable ({@code ExceptionInInitializerError} on the first touch) and takes {@code playSound} with
     * it — with a stack trace that points at the plugin.
     *
     * <p>So the answers come from the game's own {@code SOUND_EVENT} registry, which means a sound a mod
     * registered is a real entry and a vanilla one resolves to the same id the game plays.
     */
    static final class ForbricSoundRegistry implements org.bukkit.Registry<org.bukkit.Sound> {

        static final ForbricSoundRegistry INSTANCE = new ForbricSoundRegistry();

        private ForbricSoundRegistry() {
        }

        /** The game's sound ids, in the order the game keeps them. */
        private java.util.List<org.bukkit.NamespacedKey> keys() {
            net.minecraft.core.RegistryAccess registries = ForbricRegistries.access();
            if (registries == null) {
                return java.util.List.of();
            }
            return registries.lookupOrThrow(net.minecraft.core.registries.Registries.SOUND_EVENT)
                            .keySet().stream()
                            .map(id -> new org.bukkit.NamespacedKey(id.getNamespace(), id.getPath()))
                            .toList();
        }

        @Override
        public org.bukkit.Sound get(org.bukkit.NamespacedKey key) {
            if (key == null) {
                return null;
            }
            // Asked of the game, like every other registry here: whether a sound exists is a fact about
            // the running server, and a mod's sound is as real as a vanilla one.
            net.minecraft.core.RegistryAccess registries = ForbricRegistries.access();
            if (registries == null) {
                return null;
            }
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            key.getNamespace(), key.getKey());
            if (!registries.lookupOrThrow(net.minecraft.core.registries.Registries.SOUND_EVENT)
                            .containsKey(id)) {
                return null;
            }
            return ForbricSound.of(key);
        }

        @Override
        public org.bukkit.NamespacedKey getKey(org.bukkit.Sound sound) {
            return sound == null ? null : sound.getKey();
        }

        @Override
        public boolean hasTag(TagKey<org.bukkit.Sound> key) {
            return false;
        }

        @Override
        public Tag<org.bukkit.Sound> getTag(TagKey<org.bukkit.Sound> key) {
            throw new UnsupportedOperationException(
                            "[Forbric/Bukkit] sound tags are not implemented by this bridge yet");
        }

        @Override
        public Collection<Tag<org.bukkit.Sound>> getTags() {
            return java.util.List.of();
        }

        @Override
        public Stream<org.bukkit.Sound> stream() {
            return keys().stream().map(ForbricSound::of);
        }

        @Override
        public Stream<org.bukkit.NamespacedKey> keyStream() {
            return keys().stream();
        }

        @Override
        public int size() {
            return keys().size();
        }

        @Override
        public java.util.Iterator<org.bukkit.Sound> iterator() {
            return stream().iterator();
        }
    }
}
