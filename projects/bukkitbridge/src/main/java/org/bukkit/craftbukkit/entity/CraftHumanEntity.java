/*
 * Forbric Bukkit bridge — the {@code org.bukkit.craftbukkit.entity.CraftHumanEntity} that plugins
 * reach for by name.
 */
package org.bukkit.craftbukkit.entity;

import org.bukkit.permissions.PermissibleBase;

/**
 * The handle-holding human-entity base, under the upstream name.
 *
 * <p>{@link CraftEntity} explains why these upstream-named classes exist at all. This one exists
 * for a second, sharper reason: a plugin that hooks Bukkit's permission checks locates the field
 * it has to replace <b>by name on this class</b>, and it does so in a static initialiser. When the
 * lookup fails, the failure is permanent — a {@code NoClassDefFoundError} on every later use, for
 * the rest of the server's life.
 *
 * <h2>Why {@link #perm} is here and not in {@link CraftPlayer}</h2>
 *
 * <p>Upstream it lives on {@code CraftHumanEntity}, and callers look for it there:
 * {@code CraftHumanEntity.class.getDeclaredField("perm")}. LuckPerms does exactly that, and its
 * fallback when that throws is to try Glowstone's {@code GlowHumanEntity} — which this server does
 * not have either, so the fallback throws too and {@code PermissibleInjector} dies in its static
 * initialiser:
 *
 * <pre>
 * ExceptionInInitializerError
 *   Caused by: ClassNotFoundException: net.glowstone.entity.GlowHumanEntity
 * </pre>
 *
 * <p>From that point on every permission hook LuckPerms installs throws {@code NoClassDefFoundError}
 * — on player join, on quit, on every permission recalculation — while the plugin itself still
 * reports that it enabled successfully. The field being present is what breaks that chain.
 *
 * <h2>What the field is for</h2>
 *
 * <p>{@code perm} is the object Bukkit consults for {@code hasPermission}. Replacing it with a
 * subclass that answers from a permission cache is how a permission plugin avoids recomputing the
 * inheritance graph on every check. The bridge's own player answers permissions from its own state
 * and does not read this field, so nothing here has to be kept in sync — it exists so that a plugin
 * that wants to install its own implementation has something to install it into.
 *
 * <p>It is not {@code final} for that reason: a reflective write to a final field is exactly the
 * thing such a plugin cannot do reliably.
 */
public abstract class CraftHumanEntity extends CraftEntity {

	/**
	 * The permissible Bukkit consults for this entity's permissions.
	 *
	 * <p>Never read by the bridge — see the class comment. It is, however, initialised rather than
	 * left null, because the plugin that replaces it reads it first: LuckPerms' injector pulls the
	 * field value, calls {@code getClass()} on it to compare against the stock type, and reads its
	 * attachment list before swapping its own implementation in. A null here fails that sequence at
	 * the first step, with an NPE that looks nothing like the cause.
	 *
	 * <p>Not {@code final}: a reflective write to a final field is exactly the thing such a plugin
	 * cannot do reliably.
	 */
	/**
	 * The permissible Bukkit consults for this entity's permissions.
	 *
	 * <p>Never read by the bridge. It exists because a permission plugin looks the field up by NAME on this
	 * class and replaces it — see the class comment for the failure that follows when it is missing — and
	 * because the lookup happens in a static initialiser, the field has to exist for every future call, not
	 * just the first.
	 *
	 * <p>Initialised rather than null because such a plugin reads it before writing: LuckPerms pulls the value,
	 * calls {@code getClass()} on it to compare against the stock type, and drains its attachment list before
	 * swapping its own implementation in. A null fails that sequence at the first step.
	 *
	 * <p>Not {@code final}: a reflective write to a final field is exactly the thing such a plugin cannot do
	 * reliably.
	 */
	@SuppressWarnings("unused")
	private PermissibleBase perm;

	/**
	 * The {@link org.bukkit.permissions.ServerOperator} the default permissible reports against.
	 *
	 * <p>This object exists to be <b>replaced</b>, so what it answers is only consulted when no permission
	 * plugin is installed. Operator status is therefore answered conservatively: {@code false}. The game's own
	 * ops list is authoritative, and the bridge's player answers {@code isOp()} from it directly.
	 */
	private static org.bukkit.permissions.ServerOperator opsView() {
		return new org.bukkit.permissions.ServerOperator() {
			@Override
			public boolean isOp() {
				return false;
			}

			@Override
			public void setOp(boolean value) {
				// Bukkit's own contract: the permissible does not grant or revoke; the server does.
				throw new UnsupportedOperationException(
						"[Forbric/Bukkit] operator status is changed through the server, not through a Permissible");
			}
		};
	}

	protected CraftHumanEntity(net.minecraft.world.entity.Entity handle) {
		super(handle);
		// Operators are answered conservatively by this view; see opsView() for why that is enough.
		this.perm = new PermissibleBase(opsView());
	}
}
