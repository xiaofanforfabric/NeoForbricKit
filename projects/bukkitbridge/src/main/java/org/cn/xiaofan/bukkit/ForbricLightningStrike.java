/*
 * Forbric Bukkit bridge — a lightning bolt, as {@code org.bukkit.entity.LightningStrike}.
 */
package org.cn.xiaofan.bukkit;

/**
 * A bolt of lightning, as a plugin sees it.
 *
 * <p><b>Why this is a subclass of {@link ForbricEntity} rather than a second wrapper.</b> Bukkit models a
 * bolt as an {@code Entity} with an extra interface on top, and everything a plugin does with one — its
 * location, its UUID, its world, teleporting it — is the entity API. Delegating instead of extending
 * would mean writing out all hundred and fifty {@code Entity} methods again to forward them, and every
 * one of those forwards is a place for the two wrappers to drift apart.
 *
 * <p><b>Only three of the interface's methods can be answered</b>, and that is a property of the game
 * rather than of this bridge: {@code LightningBolt} keeps its flash count, life and visual-only flag in
 * private fields with no accessors, so a bridge cannot read or write them. Those refuse by name instead
 * of inventing a number — a plugin that sets a flash count and reads back a fabricated value has a bug
 * it cannot see.
 */
final class ForbricLightningStrike extends ForbricEntity implements org.bukkit.entity.LightningStrike {

    private final net.minecraft.world.entity.LightningBolt bolt;

    ForbricLightningStrike(net.minecraft.world.entity.LightningBolt bolt, ServerImpl server) {
        super(bolt, server);
        this.bolt = bolt;
    }

    /**
     * Whether this bolt only shows itself.
     *
     * <p>Refused rather than guessed: {@code visualOnly} is a private field with no getter, so the honest
     * answer is that this bridge cannot tell. The value a plugin sets through
     * {@link #setEffect(boolean)} is not recoverable here, and reporting a made-up {@code false} for a
     * bolt that damages nothing would be the wrong way round.
     */
    @Override
    public boolean isEffect() {
        throw new UnsupportedOperationException(
                "[Forbric/Bukkit] LightningStrike.isEffect() is not implemented by this bridge yet: the game"
                        + " keeps the visual-only flag private with no accessor, so it cannot be read back");
    }

    /**
     * Turns the damaging bolt into a visual one, which is what {@code strikeLightningEffect} means.
     *
     * <p>The game owns this flag and does expose a setter, so the write is real — the bolt stops hurting
     * entities and igniting blocks. Reading it back is what is unavailable, not setting it.
     */
    void setEffect(boolean visualOnly) {
        bolt.setVisualOnly(visualOnly);
    }

    /** Refused: {@code flashes} is private with no accessor. */
    @Override
    public int getFlashes() {
        throw new UnsupportedOperationException(
                "[Forbric/Bukkit] LightningStrike.getFlashes() is not implemented by this bridge yet: the game"
                        + " keeps the flash count private with no accessor");
    }

    /** Refused for the same reason as {@link #getFlashes()}. */
    @Override
    public void setFlashes(int flashes) {
        throw new UnsupportedOperationException(
                "[Forbric/Bukkit] LightningStrike.setFlashes() is not implemented by this bridge yet: the game"
                        + " keeps the flash count private with no accessor");
    }

    /** Refused: {@code life} is private with no accessor. */
    @Override
    public int getLifeTicks() {
        throw new UnsupportedOperationException(
                "[Forbric/Bukkit] LightningStrike.getLifeTicks() is not implemented by this bridge yet: the game"
                        + " keeps the bolt's life private with no accessor");
    }

    /** Refused for the same reason as {@link #getLifeTicks()}. */
    @Override
    public void setLifeTicks(int ticks) {
        throw new UnsupportedOperationException(
                "[Forbric/Bukkit] LightningStrike.setLifeTicks() is not implemented by this bridge yet: the game"
                        + " keeps the bolt's life private with no accessor");
    }

    /**
     * The player the bolt is credited to, when this bridge can name them.
     *
     * <p>The game stores its cause as a {@code ServerPlayer}, which is what a Bukkit {@code Player}
     * wraps, so the conversion is the same one every other player answer uses.
     */
    @Override
    public org.bukkit.entity.Player getCausingPlayer() {
        net.minecraft.server.level.ServerPlayer cause = bolt.getCause();
        return cause == null ? null : ForbricPlayer.of(cause, server());
    }

    /** Sets who the bolt is credited to, or clears it when given null. */
    @Override
    public void setCausingPlayer(org.bukkit.entity.Player player) {
        if (player == null) {
            bolt.setCause(null);
            return;
        }
        // Only a player this bridge wraps can be handed to the game, because the game needs its own object.
        if (player instanceof ForbricPlayer wrapped) {
            bolt.setCause(wrapped.player());
        }
    }

    /** The same cause, as a general entity. */
    @Override
    public org.bukkit.entity.Entity getCausingEntity() {
        return getCausingPlayer();
    }

    /** Bukkit's nested Spigot accessor, which has no state of its own here. */
    @Override
    public org.bukkit.entity.LightningStrike.Spigot spigot() {
        return new org.bukkit.entity.LightningStrike.Spigot();
    }

    /** Bukkit adds these two to the interface; both name the same count the game does not expose. */
    @Override
    public int getFlashCount() {
        return getFlashes();
    }

    @Override
    public void setFlashCount(int flashes) {
        setFlashes(flashes);
    }
}
