/*
 * Forbric Bukkit bridge — the player profile a login event carries.
 */
package net.neoforbrickit.bukkit;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.profile.PlayerTextures;

/**
 * A {@link PlayerProfile} built from an account id and a name, with no skin data.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>{@code AsyncPlayerPreLoginEvent}'s constructor calls {@code Bukkit.createProfile(name, uuid)} and puts
 * the result on the event. This bridge did not implement {@code createProfile}, so <b>constructing the event
 * threw</b> — and because the raise happens inside a catch that keeps a bad login from disconnecting anyone,
 * the exception was logged and the event never reached a plugin. That is why a permission plugin saw no users
 * even though every injected call was in place and every player joined normally.
 *
 * <h2>What it does not do</h2>
 *
 * <p>No textures, no skin download, no Mojang lookup. A login event carries the profile so a listener can
 * read who is connecting — the name and the id — and a plugin that wants the skin calls
 * {@link #completeFromCache()} or {@link #update()}, which this implementation answers honestly rather than
 * with a fabricated success:
 *
 * <ul>
 *   <li>{@code completeFromCache} answers {@code false}: nothing is cached, because this bridge has no profile
 *       cache. The player is already online by the time anyone could look, and the game holds the real
 *       textures on its own {@code GameProfile}.
 *   <li>{@code update()} answers a future that completes with this same profile. It is not a network call, and
 *       saying so is better than returning a future that never completes.
 * </ul>
 *
 * <p>The properties set is real and writable: a plugin that adds a texture property to the event's profile
 * must not lose it, and the game's own skin resolution is driven by exactly those properties.
 */
final class ForbricPlayerProfile implements PlayerProfile {

	private String name;
	private UUID id;
	private PlayerTextures textures;
	private final Set<ProfileProperty> properties = new LinkedHashSet<>();

	ForbricPlayerProfile(UUID id, String name) {
		this.id = id;
		this.name = name;
	}

	// ---- identity ---------------------------------------------------------------------------------------

	@Override
	public UUID getUniqueId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	/** Paper's own accessor, which is the same value under its own name. */
	public String setName(String name) {
		String previous = this.name;
		this.name = name;
		return previous;
	}

	/** Paper's own accessor; the contract returns the previous id. */
	public UUID setId(UUID id) {
		UUID previous = this.id;
		this.id = id;
		return previous;
	}

	public UUID getId() {
		return id;
	}

	// ---- textures ---------------------------------------------------------------------------------------

	@Override
	public PlayerTextures getTextures() {
		// Lazily built so a caller that only reads the name never pays for it. Never null: the API documents
		// this as a mutable view, and a null would make an ordinary `profile.getTextures().isEmpty()` throw.
		if (textures == null) {
			textures = new ForbricPlayerTextures();
		}
		return textures;
	}

	@Override
	public void setTextures(PlayerTextures textures) {
		this.textures = textures;
	}

	@Override
	public boolean hasTextures() {
		return textures != null && !textures.isEmpty();
	}

	// ---- properties -------------------------------------------------------------------------------------

	public Set<ProfileProperty> getProperties() {
		return properties;
	}

	public boolean hasProperty(String name) {
		return properties.stream().anyMatch(property -> property.getName().equals(name));
	}

	public void setProperty(ProfileProperty property) {
		if (property == null) {
			return;
		}
		properties.removeIf(existing -> existing.getName().equals(property.getName()));
		properties.add(property);
	}

	public void setProperties(Collection<ProfileProperty> properties) {
		this.properties.clear();
		if (properties != null) {
			for (ProfileProperty property : properties) {
				setProperty(property);
			}
		}
	}

	public boolean removeProperty(String name) {
		return properties.removeIf(property -> property.getName().equals(name));
	}

	public void clearProperties() {
		properties.clear();
	}

	// ---- completion -------------------------------------------------------------------------------------

	/**
	 * Whether this profile has everything a consumer would want.
	 *
	 * <p>{@code true} once a name and an id are both present is the honest answer for this bridge: it holds
	 * no texture data at all, so there is nothing further a "complete" flag could promise.
	 */
	@Override
	public boolean isComplete() {
		return name != null && id != null;
	}

	/** Always false: this bridge keeps no profile cache, so there is nothing to complete from. */
	@Override
	public boolean completeFromCache() {
		return false;
	}

	/** Always false, for the reason {@link #completeFromCache} gives. */
	@Override
	public boolean completeFromCache(boolean onlineMode) {
		return false;
	}

	/** Always false, for the reason {@link #completeFromCache} gives. */
	@Override
	public boolean completeFromCache(boolean onlineMode, boolean lookUpUUID) {
		return false;
	}

	/** Always false, for the reason {@link #completeFromCache} gives. */
	@Override
	public boolean complete(boolean textures) {
		return false;
	}

	/** Always false, for the reason {@link #completeFromCache} gives. */
	@Override
	public boolean complete(boolean textures, boolean onlineMode) {
		return false;
	}

	/**
	 * Completes with this same profile.
	 *
	 * <p>Not a network call. A future that completed with a different object would let a caller assume fresh
	 * data had arrived, and one that never completed would hang a plugin that waits on it.
	 */
	@Override
	public CompletableFuture<PlayerProfile> update() {
		return CompletableFuture.completedFuture(this);
	}

	@Override
	public PlayerProfile clone() {
		ForbricPlayerProfile copy = new ForbricPlayerProfile(id, name);
		copy.textures = textures;
		copy.properties.addAll(properties);
		return copy;
	}

	// ---- serialisation ----------------------------------------------------------------------------------

	/**
	 * The map {@code ConfigurationSerializable} asks for.
	 *
	 * <p>Only the two fields this bridge can vouch for. A profile from here carries no textures, so writing
	 * any would be inventing them; a plugin that serialises one gets back what it could have read.
	 */
	@Override
	public java.util.Map<String, Object> serialize() {
		java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
		if (id != null) {
			data.put("uuid", id.toString());
		}
		if (name != null) {
			data.put("name", name);
		}
		return data;
	}

	@Override
	public String toString() {
		return "ForbricPlayerProfile[" + (name == null ? "<no name>" : name) + ", " + id + "]";
	}

	/**
	 * A textures view with nothing in it.
	 *
	 * <p>Writable, because the API documents it as a mutable view and a plugin may set a skin before handing
	 * the profile on. Nothing here is ever used to fetch anything: this bridge does not resolve textures, and
	 * the game holds the real skin on its own profile.
	 */
	private static final class ForbricPlayerTextures implements PlayerTextures {

		private URL skin;
		private URL cape;
		private SkinModel skinModel = SkinModel.CLASSIC;
		private long timestamp;

		@Override
		public boolean isEmpty() {
			return skin == null && cape == null;
		}

		@Override
		public void clear() {
			skin = null;
			cape = null;
			timestamp = 0;
		}

		@Override
		public URL getSkin() {
			return skin;
		}

		@Override
		public void setSkin(URL skinUrl) {
			this.skin = skinUrl;
		}

		@Override
		public void setSkin(URL skinUrl, SkinModel model) {
			this.skin = skinUrl;
			this.skinModel = model;
		}

		@Override
		public SkinModel getSkinModel() {
			return skinModel;
		}

		@Override
		public URL getCape() {
			return cape;
		}

		@Override
		public void setCape(URL capeUrl) {
			this.cape = capeUrl;
		}

		@Override
		public long getTimestamp() {
			return timestamp;
		}

		@Override
		public boolean isSigned() {
			// Nothing was fetched, so nothing is signed. Claiming otherwise would make a plugin trust data
			// that does not exist.
			return false;
		}
	}
}
