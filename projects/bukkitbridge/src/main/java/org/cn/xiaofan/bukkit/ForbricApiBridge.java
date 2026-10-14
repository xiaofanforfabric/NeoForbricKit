/*
 * Forbric Bukkit bridge — the minimum Paper-compatibility layer.
 *
 * This class implements Paper's {@code InternalAPIBridge}, the single entry point Paper's API classes
 * reach their server through. Paper finds it by name with {@code Services.service(InternalAPIBridge.class)},
 * i.e. a plain {@code java.util.ServiceLoader} lookup, so the concrete class may live in any package as long
 * as it is registered in {@code META-INF/services/io.papermc.paper.InternalAPIBridge}.
 */
package org.cn.xiaofan.bukkit;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.Set;

import com.destroystokyo.paper.SkinParts;
import io.papermc.paper.InternalAPIBridge;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.poi.PoiType;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.world.damagesource.CombatEntry;
import io.papermc.paper.world.damagesource.FallLocationType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attributable;
import org.bukkit.block.Biome;
import org.bukkit.damage.DamageEffect;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pose;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The bridge Paper's API layer calls into, loaded by name through {@link java.util.ServiceLoader}.
 *
 * <p>Paper's real implementation is backed by Paper's own plugin provider system and its patched game. Forbric
 * has neither, so this class supplies a correct answer only where the API contract is genuinely satisfiable
 * without them, and refuses the rest with a named {@link UnsupportedOperationException}.
 *
 * <p><b>Why refuse loudly.</b> Every method below is reached from ordinary-looking API calls
 * ({@code Player#getAttribute}, {@code ItemStack#serializeAsBytes}, …). Returning a plausible-looking default
 * would turn "this bridge does not implement Paper's data components" into a wrong value that only shows up as a
 * bizarre gameplay symptom far from here. A named exception points straight at the missing piece.
 */
public final class ForbricApiBridge implements InternalAPIBridge {

	public ForbricApiBridge() {
	}

	/**
	 * The one call this bridge must satisfy to let a plugin boot: {@code JavaPlugin}'s constructor builds its
	 * lifecycle manager through here on every instantiation.
	 */
	@Override
	public LifecycleEventManager<Plugin> createPluginLifecycleEventManager(JavaPlugin plugin,
			BooleanSupplier allowsLifecycleRegistration) {
		ForbricLifecycleEventManager manager =
				new ForbricLifecycleEventManager(plugin, allowsLifecycleRegistration);
		// Kept so the bridge can fire the handlers a plugin registered. JavaPlugin builds its manager here
		// and holds it privately, so this call is the only moment the object is reachable -- a registry
		// filled any later would be filled from nowhere.
		MANAGERS.put(plugin, manager);
		return manager;
	}

	/**
	 * The managers this bridge has handed out, by plugin.
	 *
	 * <p>Static because the bridge is instantiated by ServiceLoader and the caller that needs the managers
	 * (the loader, after a plugin enables) has no reference to the instance.
	 *
	 * <p><b>An identity map, and that is not a style choice.</b> This is populated from inside
	 * {@code JavaPlugin}'s constructor -- the only moment the manager is reachable -- and at that point
	 * the plugin's own fields are not yet assigned. {@code PluginBase.hashCode()} is
	 * {@code getName().hashCode()}, {@code getName()} reads {@code pluginMeta}, and {@code pluginMeta} is
	 * still null, so a HASHING map throws a NullPointerException out of the constructor: the plugin
	 * cannot be instantiated at all, and the message names {@code getPluginMeta()} rather than the
	 * registry that asked. An identity map compares references and never calls the plugin's methods.
	 *
	 * <p>Synchronised because plugins load on the server thread here but nothing in the API promises
	 * that forever, and the map is static.
	 */
	private static final java.util.Map<JavaPlugin, ForbricLifecycleEventManager> MANAGERS =
				java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

	/** The lifecycle manager for a plugin, or null if it was never constructed through this bridge. */
	static ForbricLifecycleEventManager lifecycleManagerOf(JavaPlugin plugin) {
		return MANAGERS.get(plugin);
	}

	// ---- Not implemented -----------------------------------------------------------------------------------
	//
	// Each of these belongs to a Paper subsystem this bridge does not build yet. They are written out rather
	// than inherited so the failure carries the missing feature's name.

	private static UnsupportedOperationException notYet(String what) {
		return new UnsupportedOperationException(
				"[Forbric/Bukkit] Paper's " + what + " is not implemented by this bridge yet");
	}

	@Override
	public Biome constructLegacyCustomBiome() {
		throw notYet("legacy custom biomes");
	}

	@Override
	public CombatEntry createCombatEntry(LivingEntity entity, DamageSource source, float damage) {
		throw notYet("combat entry tracking");
	}

	@Override
	public CombatEntry createCombatEntry(DamageSource source, float damage, FallLocationType fallLocation,
			float fallDistance) {
		throw notYet("combat entry tracking");
	}

	@Override
	public Predicate<CommandSourceStack> restricted(Predicate<CommandSourceStack> base) {
		throw notYet("command restrictions");
	}

	@Override
	public ResolvableProfile defaultMannequinProfile() {
		throw notYet("mannequin profiles");
	}

	@Override
	public SkinParts.Mutable allSkinParts() {
		throw notYet("skin parts");
	}

	@Override
	public Component defaultMannequinDescription() {
		throw notYet("mannequin descriptions");
	}

	@Override
	public <MODERN, LEGACY> GameRule<LEGACY> legacyGameRuleBridge(GameRule<MODERN> rule,
			Function<LEGACY, MODERN> toModern, Function<MODERN, LEGACY> toLegacy, Class<LEGACY> legacyClass) {
		throw notYet("legacy game-rule bridging");
	}

	@Override
	public Set<Pose> validMannequinPoses() {
		throw notYet("mannequin poses");
	}

	@Override
	public PoiType.Occupancy createOccupancy(String name) {
		throw notYet("point-of-interest types");
	}

	@Override
	public DamageSource.Builder createDamageSourceBuilder(DamageType type) {
		throw notYet("damage sources");
	}

	@Override
	public DamageEffect getDamageEffect(String key) {
		throw notYet("damage effects");
	}

	@Override
	public String getTranslationKey(EntityType type) {
		throw notYet("entity translation keys");
	}

	@Override
	public SpawnCategory getSpawnCategory(EntityType type) {
		throw notYet("spawn categories");
	}

	@Override
	public ItemStack deserializeItem(byte[] data) {
		return ForbricItemCodec.decode(data);
	}

	@Override
	public boolean hasDefaultEntityAttributes(NamespacedKey key) {
		throw notYet("default entity attributes");
	}

	@Override
	public Attributable getDefaultEntityAttributes(NamespacedKey key) {
		throw notYet("default entity attributes");
	}

	@Override
	public String getStatisticCriteriaKey(Statistic statistic) {
		throw notYet("statistic criteria");
	}

	/**
	 * An empty stack, which {@code ItemStack.empty()} and the no-argument constructor both reach.
	 *
	 * <p>Air with a count of zero, built by this bridge's own class so every later call on it lands
	 * somewhere real — the API's own class would forward to a delegate that is not there.
	 */
	@Override
	public ItemStack createEmptyStack() {
		return ForbricItemStack.plain(org.bukkit.Material.AIR, 0);
	}

	@Override
	public Component resolveWithContext(Component component, org.bukkit.command.CommandSender sender, Entity entity,
			boolean translate) {
		throw notYet("component context resolution");
	}

	@Override
	public ComponentFlattener componentFlattener() {
		throw notYet("component flattening");
	}
}
