/*
 * Forbric Bukkit bridge — primed TNT, as {@code org.bukkit.entity.TNTPrimed}.
 */
package net.neoforbrickit.bukkit;

import java.lang.reflect.Field;

/**
 * Primed TNT, as a plugin sees it.
 *
 * <p><b>Why this is a subclass of {@link ForbricEntity} rather than a second wrapper.</b> Bukkit models
 * primed TNT as an {@code Entity} with {@code Explosive} and {@code TNTPrimed} on top, and everything a
 * plugin does with one — its location, its UUID, its velocity, its world — is the entity API. Delegating
 * instead of extending would mean writing out every {@code Entity} method again to forward it, and each
 * of those forwards is a place for the two wrappers to drift apart.
 *
 * <p><b>The fuse is the interesting one.</b> The game keeps it in a synched data entry with a real
 * accessor ({@code setFuse}/{@code getFuse}), so a write through here reaches the server and the client
 * both. A pattern that sets an 80-tick fuse and gets it back is reading the same number the ticking
 * entity will use.
 *
 * <p><b>The source is written through a private field</b>, and that is worth stating rather than
 * hiding. {@code PrimedTnt} stores its owner as an {@code EntityReference} in a field with a getter and
 * no setter — the game only ever assigns it from the constructor, which the bridge cannot use because a
 * plugin sets the source AFTER spawning. Reflection on a field the game itself owns is the only way to
 * honour the call, so the field is looked up once and a failure is reported by name instead of being
 * swallowed: a plugin that sets a source and gets a null back from {@code getSource()} has a bug it
 * cannot see otherwise.
 */
final class ForbricTNTPrimed extends ForbricEntity implements org.bukkit.entity.TNTPrimed {

	private final net.minecraft.world.entity.item.PrimedTnt tnt;

	/**
	 * {@code PrimedTnt.owner}, the field Bukkit's {@code setSource} has to write.
	 *
	 * <p>Resolved once, lazily, and kept. A missing field is not a reason to fail construction — the
	 * entity still works without a source — so the lookup is deferred to the first write and its absence
	 * turns that one call into a named refusal.
	 */
	private static volatile Field ownerField;
	private static volatile boolean ownerFieldLookedUp;

	ForbricTNTPrimed(net.minecraft.world.entity.item.PrimedTnt tnt, ServerImpl server) {
		super(tnt, server);
		this.tnt = tnt;
	}

	@Override
	public void setFuseTicks(int fuseTicks) {
		tnt.setFuse(fuseTicks);
	}

	@Override
	public int getFuseTicks() {
		return tnt.getFuse();
	}

	/**
	 * Who the blast is credited to, or null.
	 *
	 * <p>Read through the game's own accessor, which resolves the stored reference against the level: an
	 * owner that has since left the world reads as null, which is what Bukkit's contract says.
	 */
	@Override
	public org.bukkit.entity.Entity getSource() {
		net.minecraft.world.entity.Entity owner = tnt.getOwner();
		return owner == null ? null : ForbricEntity.of(owner, server());
	}

	/**
	 * Sets who the blast is credited to, or clears it when given null.
	 *
	 * <p>The write goes through the private owner field, for the reason the class comment gives: the game
	 * offers no setter and this call arrives after the entity exists. The reference form is what the
	 * field holds, so the argument is converted rather than assigned directly — storing the entity
	 * itself would leave the field in a shape the game's own getter does not expect.
	 */
	@Override
	public void setSource(org.bukkit.entity.Entity source) {
		if (source == null) {
			setOwnerReference(null);
			return;
		}
		// Unwrapped through both wrappers, the way ForbricWorld#gameEntity does it. A player is NOT a
		// ForbricEntity -- it is its own implementation of Player -- so a check for one alone refuses
		// the shooter, which is the entity a plugin is most likely to name here.
		net.minecraft.world.entity.Entity unwrapped = null;
		if (source instanceof ForbricEntity wrapped) {
			unwrapped = wrapped.entity();
		} else if (source instanceof ForbricPlayer wrapped) {
			unwrapped = wrapped.player();
		}
		if (unwrapped == null) {
			throw new IllegalArgumentException("[Forbric/Bukkit] TNTPrimed.setSource() needs an entity this "
					+ "bridge produced, got " + source);
		}
		// Only a living entity can own a blast: that is the type the game's field holds. Refusing is
		// better than storing a reference the game would fail to resolve when it reads the field back.
		if (!(unwrapped instanceof net.minecraft.world.entity.LivingEntity living)) {
			throw new IllegalArgumentException("[Forbric/Bukkit] TNTPrimed.setSource() needs a living entity, "
					+ "because the game stores a living owner; got " + unwrapped.getType());
		}
		setOwnerReference(net.minecraft.world.entity.EntityReference.of(living));
	}

	/** Writes the game's owner field, resolving it on first use. */
	private void setOwnerReference(net.minecraft.world.entity.EntityReference<net.minecraft.world.entity.LivingEntity> reference) {
		Field field = ownerField();
		if (field == null) {
			throw new UnsupportedOperationException(
					"[Forbric/Bukkit] TNTPrimed.setSource() is not implemented by this bridge yet: the game's"
							+ " PrimedTnt has no owner setter and its owner field could not be reached");
		}
		try {
			field.set(tnt, reference);
		} catch (ReflectiveOperationException refused) {
			throw new UnsupportedOperationException(
					"[Forbric/Bukkit] TNTPrimed.setSource() could not write the game's owner field", refused);
		}
	}

	/** The owner field, looked up once. Null when the game's shape does not match this bridge's copy. */
	private static Field ownerField() {
		if (!ownerFieldLookedUp) {
			synchronized (ForbricTNTPrimed.class) {
				if (!ownerFieldLookedUp) {
					try {
						Field found = net.minecraft.world.entity.item.PrimedTnt.class
								.getDeclaredField("owner");
						found.setAccessible(true);
						ownerField = found;
					} catch (ReflectiveOperationException absent) {
						ownerField = null;
					}
					ownerFieldLookedUp = true;
				}
			}
		}
		return ownerField;
	}

	/**
	 * Refused: the game computes a blast's power from the level's difficulty and the entity's own
	 * {@code explosionPower}, which it keeps private with no accessor.
	 */
	@Override
	public void setYield(float yield) {
		throw new UnsupportedOperationException(
				"[Forbric/Bukkit] Explosive.setYield() is not implemented by this bridge yet: the game"
						+ " derives a primed charge's power from the level and keeps its own value private");
	}

	/** Refused for the same reason as {@link #setYield(float)}. */
	@Override
	public float getYield() {
		throw new UnsupportedOperationException(
				"[Forbric/Bukkit] Explosive.getYield() is not implemented by this bridge yet: the game"
						+ " keeps the charge's power private with no accessor");
	}

	/**
	 * Refused: whether a charge sets fire comes from the level's explosion settings, not from a field on
	 * the entity, so there is nothing here to read or write.
	 */
	@Override
	public void setIsIncendiary(boolean isIncendiary) {
		throw new UnsupportedOperationException(
				"[Forbric/Bukkit] Explosive.setIsIncendiary() is not implemented by this bridge yet:"
						+ " incendiaries are the level's decision, not the entity's");
	}

	/** Refused for the same reason as {@link #setIsIncendiary(boolean)}. */
	@Override
	public boolean isIncendiary() {
		throw new UnsupportedOperationException(
				"[Forbric/Bukkit] Explosive.isIncendiary() is not implemented by this bridge yet:"
						+ " incendiaries are the level's decision, not the entity's");
	}

	/**
	 * The block a charge renders as, which is what a plugin changes to disguise one as a block of
	 * something else.
	 */
	/**
	 * The block a charge renders as, which is what a plugin changes to disguise one.
	 *
	 * <p>The round trip is name-based on both ends, the same way {@link ForbricBlock} does
	 * it: the data's material names the block and the game's default state for it is what gets
	 * written, while a read goes the other way. Properties the API models and the game does not
	 * are dropped rather than guessed at.
	 */
	@Override
	public void setBlockData(org.bukkit.block.data.BlockData data) {
		if (data == null) {
			throw new IllegalArgumentException("[Forbric/Bukkit] TNTPrimed.setBlockData() needs block data");
		}
		org.bukkit.Material material = data.getMaterial();
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
			material.getKey().getNamespace(), material.getKey().getKey());
		net.minecraft.world.level.block.Block block =
			net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id);
		if (block == null) {
			throw new UnsupportedOperationException(
				"[Forbric/Bukkit] TNTPrimed.setBlockData() has no game block for " + id);
		}
		tnt.setBlockState(block.defaultBlockState());
	}

	/** The block the charge currently renders as, as Bukkit block data. */
	@Override
	public org.bukkit.block.data.BlockData getBlockData() {
		net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
			.getKey(tnt.getBlockState().getBlock());
		org.bukkit.Material material = org.bukkit.Material.matchMaterial(id.toString());
		if (material == null) {
			throw new UnsupportedOperationException(
				"[Forbric/Bukkit] TNTPrimed.getBlockData() has no Material for block " + id);
		}
		return material.createBlockData();
	}
}
