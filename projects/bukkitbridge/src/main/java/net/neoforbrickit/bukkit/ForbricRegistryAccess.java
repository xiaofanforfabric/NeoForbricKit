/*
 * Forbric Bukkit bridge — the {@code RegistryAccess} Paper's API insists on finding.
 */
package net.neoforbrickit.bukkit;

import java.util.Collection;
import java.util.stream.Stream;

import net.neoforbrickit.bridge.util.ForbricLog;

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

        /** One report per distinct problem: this runs per lookup, and a plugin loops over items. */
        private static final java.util.Set<String> REPORTED =
                        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

        private static void report(String what, Throwable failure) {
            if (!REPORTED.add(what)) {
                return;
            }
            if (failure == null) {
                ForbricLog.warn("[Forbric/Bukkit] item registry: %s", what);
            } else {
                ForbricLog.error("[Forbric/Bukkit] item registry: " + what, failure);
            }
        }

        /**
         * The game-layer half of this registry, loaded from the layer that can see Minecraft.
         *
         * <p>{@code gameLayer} is taken from the {@code RegistryAccess} the server published, so it is the game
         * layer by construction. The class it loads is public with static methods precisely so that this call
         * works from here — see {@link ForbricItemLookup}.
         */
        private static Class<?> gameLayerHalf(ClassLoader gameLayer) throws ClassNotFoundException {
            return Class.forName("net.neoforbrickit.bukkit.ForbricItemLookup", true, gameLayer);
        }

        /** The game's item ids, in the order the game keeps them. */
        private java.util.List<org.bukkit.NamespacedKey> keys() {
            Object registries = ForbricRegistries.access();
            if (registries == null) {
                report("no game registries have been published yet, so every item lookup answers null", null);
                return java.util.List.of();
            }
            try {
                @SuppressWarnings("unchecked")
                java.util.List<org.bukkit.NamespacedKey> keys = (java.util.List<org.bukkit.NamespacedKey>)
                                gameLayerHalf(registries.getClass().getClassLoader())
                                                .getMethod("keys").invoke(null);
                return keys;
            } catch (Throwable failure) {
                report("listing the game's items failed", failure);
                return java.util.List.of();
            }
        }

        /**
         * One item, or null when the game has no such id.
         *
         * <p>The null is the GAME's answer, asked of its own item registry in its own layer. Every other
         * failure — a layer that cannot see Minecraft, a lookup that threw — is reported rather than turned
         * into the same null, which is what made a registry that answered nothing read as "PAPER isn't an
         * item".
         */
        @Override
        public org.bukkit.inventory.ItemType get(org.bukkit.NamespacedKey key) {
            if (key == null) {
                return null;
            }
            Object registries = ForbricRegistries.access();
            if (registries == null) {
                report("no game registries have been published yet, so every item lookup answers null", null);
                return null;
            }
            try {
                return (org.bukkit.inventory.ItemType) gameLayerHalf(registries.getClass().getClassLoader())
                                .getMethod("of", String.class, String.class)
                                .invoke(null, key.getNamespace(), key.getKey());
            } catch (Throwable failure) {
                report("item lookup failed for " + key, failure);
                return null;
            }
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
            // Through get(), which asks the game layer. Building an Identifier here would name a
            // net.minecraft.* type in THIS class, and this class is defined by the layer that cannot see
            // Minecraft — the same trap the lookup itself used to fall into.
            return keyStream().map(this::get).filter(java.util.Objects::nonNull);
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

        /** Set by the first miss, so the evidence is printed once rather than per lookup. */
        private static final java.util.concurrent.atomic.AtomicBoolean MISS_REPORTED =
                        new java.util.concurrent.atomic.AtomicBoolean();

        /**
         * Set by the first reflective failure, so the stack is printed once rather than per lookup.
         *
         * <p>An {@code AtomicInteger} rather than a boolean because {@link #get} runs from {@code Sound}'s static
         * initialiser, on whatever thread first touches the class, and two of them racing for one log line is a
         * noise problem worth an atomic to avoid. {@code 0} means "not yet reported".
         */
        private static final java.util.concurrent.atomic.AtomicInteger TRACED =
                        new java.util.concurrent.atomic.AtomicInteger();

        private ForbricSoundRegistry() {
        }

        /** The game's sound ids, in the order the game keeps them. */
        private java.util.List<org.bukkit.NamespacedKey> keys() {
            Object registries = ForbricRegistries.access();
            if (registries == null) {
                return java.util.List.of();
            }
            try {
                ClassLoader gameLoader = registries.getClass().getClassLoader();
                Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey", true, gameLoader);
                Object soundRegistry = registries.getClass()
                        .getMethod("lookupOrThrow", resourceKeyClass)
                        .invoke(registries, Class.forName("net.minecraft.core.registries.Registries", true, gameLoader)
                                .getField("SOUND_EVENT").get(null));
                @SuppressWarnings("unchecked")
                java.util.Set<Object> keySet = (java.util.Set<Object>) soundRegistry.getClass()
                        .getMethod("keySet").invoke(soundRegistry);
                return keySet.stream()
                        .map(id -> {
                            try {
                                String ns = (String) id.getClass().getMethod("getNamespace").invoke(id);
                                String path = (String) id.getClass().getMethod("getPath").invoke(id);
                                return new org.bukkit.NamespacedKey(ns, path);
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(java.util.Objects::nonNull)
                        .toList();
            } catch (Exception e) {
                return java.util.List.of();
            }
        }

        @Override
        public org.bukkit.Sound get(org.bukkit.NamespacedKey key) {
            if (key == null) {
                return null;
            }
            // Asked of the game, like every other registry here: whether a sound exists is a fact about
            // the running server, and a mod's sound is as real as a vanilla one.
            Object registries = ForbricRegistries.access();
            if (registries == null) {
                return null;
            }
            try {
                // Use the game layer's ClassLoader to load Minecraft classes: Class.forName with no loader
                // defaults to the caller's loader, which is FML-Plugins (URLClassLoader) and cannot see Minecraft.
                // The registries object comes from the game layer, so its ClassLoader can resolve game classes.
                ClassLoader gameLoader = registries.getClass().getClassLoader();
                Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier", true, gameLoader);
                Object id = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class)
                        .invoke(null, key.getNamespace(), key.getKey());
                
                Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey", true, gameLoader);
                Object soundRegistry = registries.getClass()
                        .getMethod("lookupOrThrow", resourceKeyClass)
                        .invoke(registries, Class.forName("net.minecraft.core.registries.Registries", true, gameLoader)
                                .getField("SOUND_EVENT").get(null));
                
                boolean contains = (boolean) soundRegistry.getClass()
                        .getMethod("containsKey", identifierClass).invoke(soundRegistry, id);
                if (!contains) {
                    // Sound.<clinit> asks for ~2000 hard-coded ids, so a miss here is worth reporting once: a
                    // plugin asking for a sound the game does not have is a plugin bug, but a bridge that cannot
                    // see the registry at all would report the same way, and one line each is how the two are told
                    // apart. Reported once rather than per lookup, because Sound's own initialiser would otherwise
                    // produce two thousand of them.
                    //
                    // ForbricLog.warn rather than a throw: an absent sound is a legitimate null answer, the same
                    // one Bukkit documents for any name that is not in a registry.
                    if (MISS_REPORTED.compareAndSet(false, true)) {
                        net.neoforbrickit.bridge.util.ForbricLog.warn(
                                "[Forbric/Bukkit] no sound is registered under %s; answering null", id);
                    }
                    return null;
                }
                return ForbricSound.of(key);
            } catch (Exception e) {
                // A reflective miss, not an absent sound: the game's registry could not be walked at all. Worth a
                // line with the stack, because the cause is never in this method, but only once.
                if (TRACED.compareAndSet(0, -1)) {
                    net.neoforbrickit.bridge.util.ForbricLog.warn(
                            "[Forbric/Bukkit] could not read the game's sound registry; sounds will not resolve", e);
                }
                return null;
            }
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
