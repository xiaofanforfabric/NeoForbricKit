/*
 * Forbric Bukkit bridge — between Adventure's components and the game's.
 */
package org.cn.xiaofan.bukkit;

/**
 * Translates between the two component systems a plugin and the game each use.
 *
 * <h2>Why a translation is needed at all</h2>
 *
 * <p>Paper hands a plugin Kyori Adventure's {@code net.kyori.adventure.text.Component}; the game stores
 * and sends {@code net.minecraft.network.chat.Component}. They model the same JSON — Adventure was
 * designed around the game's format — but they are different object graphs with no identity conversion
 * between them. Neither library knows about the other, so a bridge has to go through the JSON they share.
 *
 * <h2>The route, and why it is this one</h2>
 *
 * <pre>
 * Adventure Component --(GsonComponentSerializer)--> JSON tree
 *                     --(ComponentSerialization.CODEC)--> game Component
 * </pre>
 *
 * <p>Adventure's own {@code GsonComponentSerializer} renders to a {@code JsonElement}, and the game's
 * {@code ComponentSerialization.CODEC} parses one back. Both are the libraries' public entry points for
 * exactly this, so no reflection is involved and a version bump on either side surfaces as a compile
 * error rather than as a component that silently loses its colour.
 *
 * <h2>What is preserved</h2>
 *
 * <p>Everything the JSON carries: text, colour, all decorations, click and hover events, translatable
 * keys with their arguments, and the child structure. That is the point of going through JSON rather
 * than copying fields — a hand-written mapping would silently drop whichever field it did not know about,
 * and the symptom would be a plugin's styled item name arriving plain with nothing to explain it.
 */
final class ForbricComponentBridge {

    private ForbricComponentBridge() {
    }

    /**
     * An Adventure component as the game's, through the JSON they share.
     *
     * <p>A null input answers null rather than an empty component: "the plugin did not set a name" and
     * "the plugin set an empty name" are different states, and the game treats them differently when it
     * decides what to draw.
     */
    static net.minecraft.network.chat.Component toGame(net.kyori.adventure.text.Component component) {
        if (component == null) {
            return null;
        }
        com.google.gson.JsonElement json =
                net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serializeToTree(component);
        return parse(json);
    }

    /**
     * The game's component as Adventure's.
     *
     * <p>Used when reading a name back off an item: the game stores its own type, and a plugin that reads
     * {@code displayName()} must get the same kind of object it set.
     */
    static net.kyori.adventure.text.Component toAdventure(net.minecraft.network.chat.Component component) {
        if (component == null) {
            return null;
        }
        com.google.gson.JsonElement json = serialize(component);
        return net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserializeFromTree(json);
    }

    /**
     * JSON to the game's component.
     *
     * <p>{@code CODEC.parse} needs a {@code DynamicOps}; {@code JsonOps.INSTANCE} is the one that reads the
     * {@code JsonElement} Adventure produced. A parse failure is reported as an exception rather than
     * swallowed — it can only mean one of the two libraries changed its format, which is a fact the
     * operator needs and not something to hide behind a default.
     */
    private static net.minecraft.network.chat.Component parse(com.google.gson.JsonElement json) {
        return net.minecraft.network.chat.ComponentSerialization.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                .getOrThrow(error -> new IllegalArgumentException(
                        "[Forbric/Bukkit] an Adventure component could not be read by the game's component codec: "
                                + error));
    }

    /** The game's component as a JSON element, for the trip back. */
    private static com.google.gson.JsonElement serialize(net.minecraft.network.chat.Component component) {
        return net.minecraft.network.chat.ComponentSerialization.CODEC
                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, component)
                .getOrThrow(error -> new IllegalArgumentException(
                        "[Forbric/Bukkit] a game component could not be written by its own codec: " + error));
    }
}
