/*
 * Forbric Bukkit bridge — the tag lifecycle event provider.
 */
package io.papermc.paper.plugin.lifecycle.event.types;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType.Prioritizable;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.tag.PostFlattenTagRegistrar;
import io.papermc.paper.tag.PreFlattenTagRegistrar;

/**
 * The two tag-registration points {@code LifecycleEvents.TAGS} exposes.
 *
 * <p><b>Refuses on use rather than answering.</b> Tag flattening happens while datapacks load and is driven by
 * the bootstrap context — a bundler-only phase this bridge does not run. Handing back a workable-looking type
 * would let a plugin register a tag handler that can never fire, which is the failure mode hardest to diagnose:
 * nothing throws, the tags simply are not there.
 *
 * <p>The refusal is at CALL time, not at class-initialisation time, and that distinction is deliberate. A
 * {@code th:throw} here would take out {@code LifecycleEvents.TAGS}, and with it the whole class — including
 * {@code COMMANDS}, which plugins do use. The provider has to be able to answer so that the field can exist.
 */
public final class ForbricTagEventTypeProvider implements TagEventTypeProvider {

    ForbricTagEventTypeProvider() {
    }

    @Override
    public <T> Prioritizable<BootstrapContext, ReloadableRegistrarEvent<PreFlattenTagRegistrar<T>>> preFlatten(
                    RegistryKey<T> registryKey) {
        throw notYet("preFlatten", registryKey);
    }

    @Override
    public <T> Prioritizable<BootstrapContext, ReloadableRegistrarEvent<PostFlattenTagRegistrar<T>>> postFlatten(
                    RegistryKey<T> registryKey) {
        throw notYet("postFlatten", registryKey);
    }

    private static UnsupportedOperationException notYet(String what, Object registryKey) {
        return new UnsupportedOperationException(
                        "[Forbric/Bukkit] LifecycleEvents.TAGS." + what + "(" + registryKey
                                        + ") is not implemented by this bridge yet: tag flattening runs in the "
                                        + "bootstrap phase, which this bridge does not host");
    }
}
