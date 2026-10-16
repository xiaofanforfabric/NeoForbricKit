/*
 * Forbric Bukkit bridge — the metadata store every player in the bridge shares.
 */
package net.neoforbrickit.bukkit;

/**
 * The one {@code MetadataStore} the bridge answers {@code Player.getMetadata} and its three siblings from.
 *
 * <p><b>Why the bridge joins in at all.</b> {@code org.bukkit.metadata.Metadatable} is one of the interfaces
 * every Bukkit entity carries, and plugins use it for exactly one thing: one plugin writes a marker onto a
 * player and a different plugin reads it later. TAB's {@code %vanished%} is the canonical shape — a vanish
 * plugin sets a value, TAB looks it up to decide whether to hide the name — and a bridge that refuses the
 * call does not merely lose the marker, it loses it loudly: TAB's placeholder manager catches the exception,
 * writes it to {@code placeholder-errors.log}, and tries again on the next refresh, which is how one missing
 * method produced a 17MB log.
 *
 * <p><b>Why {@link org.bukkit.metadata.MetadataStoreBase} and not a map of our own.</b> The base class is
 * the semantics the API documents and that plugins are already written against, and it is worth being
 * explicit about the three rules it implements, because each one is a way a hand-written map would be
 * subtly wrong:
 *
 * <ul>
 *   <li><b>One value per plugin per key.</b> Two plugins may both set {@code "vanished"} on the same player
 *       without either seeing the other's value disappear — the store keeps a map from plugin to value.</li>
 *   <li><b>{@code getMetadata} returns all of them.</b> The caller gets every plugin's value in one list,
 *       not just the last writer's, because that is the only way a reader can tell which plugin said what.</li>
 *   <li><b>{@code removeMetadata} removes one plugin's value only.</b> Removing another plugin's marker
 *       would be a privilege escalation between plugins, and the base class refuses to allow it.</li>
 * </ul>
 *
 * <p><b>Why the class implements {@code MetadataStore} as well as extending {@code MetadataStoreBase}.</b>
 * The base class carries the semantics but <em>not</em> the interface: it is declared as
 * {@code abstract class MetadataStoreBase<T>} with {@code Object} as its superclass, so nothing about it
 * says it is a {@code MetadataStore}. Its methods happen to match the interface one for one, which makes the
 * omission easy to miss — the compiler only objects at the point of use, with a message
 * ("{@code ForbricMetadataStore cannot be converted to MetadataStore<ServerPlayer>}") that names the wrong
 * thing. Declaring the interface here is what makes the field in {@link ForbricPlayer} type-check.
 *
 * <p>The base class is {@code @Deprecated} on Paper — the javadoc there recommends
 * {@code PersistentDataContainer} for anything that must survive, and a plugin-owned map for anything
 * temporary. That recommendation is about plugin authors choosing an API, not about the interface being
 * absent: {@code Metadatable} is still what {@code Player} declares, so a bridge still has to answer it, and
 * answering it with the deprecated-but-correct implementation is better than answering it with an
 * approximation. Paper's own {@code CraftPlayer} does the same.
 *
 * <h2>The key the base class asks for</h2>
 *
 * <p>{@code disambiguate} returns the string the store indexes by, and its contract is stricter than it
 * looks: <em>any two objects that represent the same subject must produce the same string, even if the
 * objects are not reference-equal</em>. That is not a formality here — {@link ForbricPlayer} builds a fresh
 * wrapper per call, so two {@code Player} objects for the same account are different objects by design, and
 * a store keyed on identity would hand each caller its own private metadata map. The vanish scenario would
 * then fail in the most confusing way possible: the plugin that set the marker works, TAB sees nothing, and
 * nothing in either plugin's log explains why.
 *
 * <p>So the key is the account's UUID, which is the one thing two wrappers for the same player always agree
 * on. The metadata key is appended with a separator that cannot occur in either part: a UUID's string form
 * is hexadecimal and dashes, and Bukkit's own metadata keys are namespaced strings that plugins pick, so a
 * key containing the separator could in principle collide with a crafted one — but the separator is a NUL,
 * which no plugin passes and which no UUID contains, so the two halves can never be confused. Concatenating
 * without it would let {@code ("ab", "c")} and {@code ("a", "bc")} collide, which is a real bug class in
 * stores that do this.
 *
 * <p><b>A null subject is refused, not silently stored.</b> The base class calls {@code disambiguate} before
 * it validates anything, so a null player would otherwise reach {@link #disambiguate} and produce the key
 * {@code "null\0..."} — one shared bucket every null-subject caller would read and write. Throwing here
 * keeps that from being reachable; no caller in the bridge has a null player to pass, so this is a guard
 * against a future one, and the exception names the problem instead of hiding it.
 */
final class ForbricMetadataStore extends org.bukkit.metadata.MetadataStoreBase<net.minecraft.server.level.ServerPlayer>
		implements org.bukkit.metadata.MetadataStore<net.minecraft.server.level.ServerPlayer> {

	/**
	 * Separates the subject's identity from the metadata key.
	 *
	 * <p>A NUL, chosen because neither half can contain it: a UUID is hexadecimal and dashes, and a metadata
	 * key is a string a plugin writes, and plugins do not put NULs in names — a key that did would be
	 * rejected by every other string-based API in Bukkit before it ever reached here.
	 */
	private static final char SEPARATOR = '\0';

	/**
	 * The string the store indexes this player's metadata by.
	 *
	 * <p>The UUID rather than {@code System.identityHashCode} or the object itself, because wrappers are
	 * built per call — see the class comment. It is also what {@code equals} on {@link ForbricPlayer} uses,
	 * so this agrees with the equality the rest of the bridge exposes.
	 */
	@Override
	protected String disambiguate(net.minecraft.server.level.ServerPlayer subject, String metadataKey) {
		if (subject == null) {
			throw new IllegalArgumentException(
					"[Forbric/Bukkit] metadata was requested for a null player; there is no identity to key it by");
		}
		if (metadataKey == null) {
			throw new IllegalArgumentException(
					"[Forbric/Bukkit] metadata was requested under a null key; the key is what a plugin reads the value back by");
		}
		return subject.getUUID().toString() + SEPARATOR + metadataKey;
	}
}
