/*
 * Forbric Bukkit bridge — the class loader a plugin is defined by.
 */
package net.neoforbrickit.bukkit;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader;
import io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The class loader one plugin's classes come from.
 *
 * <p>Paper requires this shape for a structural reason rather than a stylistic one. {@code JavaPlugin}'s
 * constructor runs before any subclass field is set, and it refuses to finish unless its own class loader is a
 * {@link ConfiguredPluginClassLoader}:
 *
 * <pre>{@code
 * if (this.getClass().getClassLoader() instanceof ConfiguredPluginClassLoader loader) {
 *     loader.init(this);
 * } else {
 *     throw new IllegalStateException("JavaPlugin requires to be created by a valid classloader.");
 * }
 * }</pre>
 *
 * <p>So the loader is what links a freshly constructed plugin to the server: {@link #init} is called from inside
 * the constructor and stores the instance, which {@link #getPlugin()} then answers with.
 *
 * <h2>What this loader does not do</h2>
 *
 * <p>Paper gives each plugin its own loader and a group to look across. This bridge runs every plugin in the
 * child-first loader that also carries the API, and {@link #loadClass(String, boolean, boolean, boolean)}
 * delegates to the ordinary class-loading path. What it does have is a {@link ForbricClassLoaderGroup}, so a
 * plugin that references a type out of another plugin resolves it — without the group a cross-plugin
 * integration fails at the superclass lookup with {@code NoClassDefFoundError}.
 */
final class PluginClassLoader extends URLClassLoader implements ConfiguredPluginClassLoader {

	private final PluginMeta meta;
	private final Path jar;

	/**
	 * The group every plugin's loader belongs to, or null before {@link #joinGroup} has run.
	 *
	 * <p>Null is a real state, not a placeholder: the loader exists and defines the plugin's main class
	 * before the bridge has a group to add it to, and a lookup in that window must simply find nothing rather
	 * than throw.
	 */
	private volatile ForbricClassLoaderGroup group;

	/** Set by {@link #init} while the plugin's constructor is still running. */
	private JavaPlugin plugin;

	/**
	 * What {@link #init} writes into the instance before the plugin's own constructor body runs.
	 *
	 * <p>See {@link #init} for why these are stashed on the loader instead of being passed to
	 * {@code JavaPlugin.init(...)} alone.
	 */
	private final PluginFields fields;

	/**
	 * @param jar        the plugin's own jar, used as this loader's code source
	 * @param parent     the loader that carries the Bukkit API and the game — every plugin shares it, which is
	 *                   what lets one plugin's types and another's be the same types
	 * @param meta       the plugin's description, answered from {@link #getConfiguration()}
	 * @param fields     the values {@code getLogger()}, {@code getServer()} and friends must answer with
	 * @param libraries  jars the plugin declared under {@code libraries:} in its own {@code plugin.yml}, already
	 *                   on disk. They are on THIS loader rather than a shared one because two plugins may ask
	 *                   for different versions of the same artifact; see {@link ForbricPluginLibraries}.
	 */
	PluginClassLoader(Path jar, ClassLoader parent, PluginMeta meta, PluginFields fields, List<Path> libraries)
			throws IOException {
		super(urlsOf(jar, libraries), parent);
		this.jar = jar;
		this.meta = meta;
		this.fields = fields;
	}

	/** The plugin's own jar first, then its declared libraries in order: its own classes always win. */
	private static URL[] urlsOf(Path jar, List<Path> libraries) throws IOException {
		java.util.List<URL> urls = new java.util.ArrayList<>(1 + (libraries == null ? 0 : libraries.size()));
		urls.add(jar.toUri().toURL());
		if (libraries != null) {
			for (Path library : libraries) {
				urls.add(library.toUri().toURL());
			}
		}
		return urls.toArray(new URL[0]);
	}

	@Override
	public PluginMeta getConfiguration() {
		return meta;
	}

	/**
	 * Loads a class for this plugin.
	 *
	 * <p>The four booleans Paper passes describe how wide the search should be — across transitive dependencies,
	 * into libraries. This bridge has one flat loader plus the cross-plugin group, so all four collapse into
	 * the same lookup; {@code resolve} is the only one with a meaning here, and it is honoured.
	 */
	@Override
	public Class<?> loadClass(String name, boolean resolve, boolean checkGlobal, boolean checkLibraries)
			throws ClassNotFoundException {
		Class<?> found = loadClass(name);
		if (resolve) resolveClass(found);
		return found;
	}

	/**
	 * The lookup plugins actually go through, since {@code Class.forName} and every {@code new} in their own
	 * code reach this overload rather than the four-boolean one Paper declares.
	 *
	 * <p>Overridden for one reason: after this loader and its parent have both come up empty, the OTHER
	 * plugins are asked. Without that step, resolving a superclass that lives in another plugin's jar fails —
	 * see {@link ForbricClassLoaderGroup} for the case that made this necessary.
	 *
	 * <p>The parent-first order of {@link URLClassLoader} is preserved: the group is consulted last, so a class
	 * this plugin ships itself always wins, and neither the API nor the game can be shadowed by a plugin.
	 */
	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		try {
			return super.loadClass(name, resolve);
		} catch (ClassNotFoundException notHere) {
			ForbricClassLoaderGroup current = group;
			// Debug, not info: a miss is the ordinary answer for every optional integration a plugin probes
			// for by name (Paper classes, other plugins' APIs, LuckPerms' own nested-jar types), so at INFO this
			// line ran to hundreds of entries on a normal startup and buried the lines that matter. Guarded by
			// -Dneofobric.debug, which is when the "who was searched" answer is actually wanted.
			net.neoforbrickit.bridge.util.ForbricLog.debug(
					"[Forbric/Bukkit] cross-plugin lookup: %s not in %s; group=%s peers=%s",
					name, jar.getFileName(), current == null ? "NULL" : "present",
					current == null ? "-" : current.describePeers());
			if (current != null) {
				Class<?> fromAnotherPlugin = current.getClassByName(name, resolve, this);
				if (fromAnotherPlugin != null) {
					return fromAnotherPlugin;
				}
				current.reportMiss(name, this);
			}
			throw notHere;
		}
	}

	/**
	 * A class from THIS jar only, or null — the entry the group uses to search a peer.
	 *
	 * <p>Deliberately not {@link #loadClass}: that would consult the parent (fine) and then the group (not
	 * fine), so two plugins that each reference a type from the other would call back into each other until
	 * the stack ran out. This asks the one question the group needs answered — "is it in your jar?" — and
	 * leaves class identity to the ordinary path.
	 *
	 * <p>{@code findClass} throws rather than returning null when the jar does not have it, so the miss is
	 * normalised here.
	 */
	Class<?> findClassHere(String name) {
		// Already defined by THIS loader: hand back the existing one.
		//
		// Not a micro-optimisation. URLClassLoader#findClass does not consult the loaded-class cache — that is
		// loadClass's job — so a second findClass for a name this loader already defined calls defineClass
		// again and the JVM refuses it:
		//
		//   LinkageError: loader PluginClassLoader attempted duplicate abstract class definition for
		//                 me.clip.placeholderapi.expansion.PlaceholderExpansion
		//
		// PlaceholderAPI defines that class itself during its own onEnable, so by the time Voice Chat asks
		// the group for it the class is already defined by PAPI's loader. Without this check the lookup that
		// was meant to FIND the class instead destroyed it, and the plugin saw NoClassDefFoundError.
		Class<?> alreadyDefined = findLoadedClass(name);
		if (alreadyDefined != null) {
			return alreadyDefined;
		}
		try {
			return findClass(name);
		} catch (ClassNotFoundException notInThisJar) {
			// The ordinary answer: this jar simply does not have the class.
			return null;
		} catch (LinkageError foundButBroken) {
			// NOT the same as "not found", and treating it as one hid a real bug: the class IS in this jar,
			// but defining it failed — a superclass or interface it needs is itself unresolvable. The caller
			// then sees "no plugin supplies it", which sends the reader looking for a missing plugin instead
			// of at the type that could not be linked. Reported at WARN because it is a genuine defect, and
			// still returning null so the search continues rather than failing the whole lookup.
			net.neoforbrickit.bridge.util.ForbricLog.warn(
					"[Forbric/Bukkit] %s is in %s but could not be defined; the search continues past it",
					name, jar.getFileName(), foundButBroken);
			return null;
		}
	}

	/**
	 * Resolves a class this loader defined.
	 *
	 * <p>A method rather than a call to {@link #resolveClass} from the group, because {@code resolveClass} is
	 * {@code protected} on {@link ClassLoader}: only a subclass may call it, and the group is not one. Resolving
	 * has to happen on the DEFINING loader for the JVM's notion of class identity to hold, so the call belongs
	 * here and the group asks for it.
	 */
	void resolveHere(Class<?> defined) {
		resolveClass(defined);
	}

	/**
	 * Links a plugin instance to this loader, and fills the fields its own constructor may already read.
	 *
	 * <p>Called by {@code JavaPlugin}'s constructor, so it runs while the plugin is only partly constructed —
	 * nothing here may touch the instance beyond writing the plain fields listed in {@link #PRE_INIT_FIELDS}.
	 *
	 * <h2>Why the fields are written here</h2>
	 *
	 * <p>{@code JavaPlugin}'s constructor ends with {@code loader.init(this)}, and only then does the subclass
	 * constructor body run. {@code JavaPlugin.init(...)} — the call that normally assigns these fields — cannot
	 * run until after construction, because it takes the instance itself. So a plugin whose constructor calls
	 * {@code getLogger()} or {@code getServer()} sees {@code null}.
	 *
	 * <p>That is not hypothetical: LuckPerms' {@code BukkitLoaderPlugin} constructs {@code LPBukkitBootstrap} in
	 * its constructor, and that constructor captures both {@code getLogger()} (wrapped in a
	 * {@code JavaPluginLogger} whose field is {@code final}) and {@code getServer()} (wrapped in a
	 * {@code NullSafeConsoleCommandSender}). Each null is permanent, and the plugin dies on its first log line in
	 * {@code onLoad} and then on its first console message in {@code onEnable} — so a permission plugin other
	 * plugins look for never arrives.
	 *
	 * <p>This method is the last point where the instance exists and the plugin's own code has not run yet, so
	 * it is the only place the fields can be filled early. Writing them is safe: every name in
	 * {@link #PRE_INIT_FIELDS} is a plain non-final instance field, and {@code init(...)} assigns the same values
	 * again later.
	 */
	@Override
	public void init(JavaPlugin plugin) {
		this.plugin = plugin;
		fillFields(plugin, fields);
	}

	/**
	 * What {@link #init} writes into a half-constructed plugin.
	 *
	 * @param server     the {@code Server} {@code getServer()} must answer with
	 * @param description the description {@code getDescription()} and {@code getPluginMeta()} must answer with
	 * @param dataFolder the folder {@code getDataFolder()} must answer with
	 * @param jar        the jar {@code getFile()} must answer with
	 * @param logger     the logger {@code getLogger()} must answer with
	 */
	record PluginFields(Object server, PluginMeta description, java.io.File dataFolder, java.io.File jar,
			java.util.logging.Logger logger) {
	}

	/**
	 * The {@code JavaPlugin} fields that {@code JavaPlugin.init(...)} assigns from data the loader already has.
	 *
	 * <p>Deliberately a fixed list rather than something derived: each entry is a field this bridge can supply
	 * the real value for. {@code loader} is NOT here — it is a {@code PluginLoader}, which this bridge has no
	 * equivalent of, and {@code JavaPlugin.init(...)} leaves it null too.
	 */
	private static final String[] PRE_INIT_FIELDS = {"server", "description", "pluginMeta", "dataFolder", "file",
			"logger"};

	/** Makes a write failure be reported once per field, not once per plugin. */
	private static final java.util.Set<String> FAILED_FIELDS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Writes {@link #PRE_INIT_FIELDS} on an instance whose constructor is still on the stack.
	 *
	 * <p>Reflection rather than a direct assignment because the fields are private to the API class. Each is
	 * resolved once and cached. A failure never escapes: the plugin then behaves exactly as it did before this
	 * hook existed, which is the old null-field behaviour rather than a crash at load time.
	 */
	private static void fillFields(JavaPlugin plugin, PluginFields values) {
		if (values == null) {
			return;
		}
		set(plugin, "server", values.server());
		set(plugin, "description", values.description());
		// The same object: PluginDescriptionFile implements PluginMeta.
		set(plugin, "pluginMeta", values.description());
		set(plugin, "dataFolder", values.dataFolder());
		set(plugin, "file", values.jar());
		set(plugin, "logger", values.logger());
	}

	private static void set(JavaPlugin plugin, String name, Object value) {
		if (value == null) {
			return;
		}
		java.lang.reflect.Field field = field(name);
		if (field == null) {
			return;
		}
		try {
			field.set(plugin, value);
		} catch (ReflectiveOperationException | RuntimeException failure) {
			if (FAILED_FIELDS.add(name)) {
				net.neoforbrickit.bridge.util.ForbricLog.warn(
						"[Forbric/Bukkit] could not fill JavaPlugin." + name
								+ " before the plugin's constructor; a plugin reading it that early will see null",
						failure);
			}
		}
	}

	/** Field lookups by name; a missing field is a degraded answer, not a fatal one. */
	private static final java.util.Map<String, java.lang.reflect.Field> FIELDS =
			new java.util.concurrent.ConcurrentHashMap<>();

	private static java.lang.reflect.Field field(String name) {
		java.lang.reflect.Field cached = FIELDS.get(name);
		if (cached != null) {
			return cached;
		}
		try {
			java.lang.reflect.Field field = JavaPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			FIELDS.put(name, field);
			return field;
		} catch (ReflectiveOperationException | RuntimeException missing) {
			// Reported once by the caller's catch, exactly as a write failure is.
			if (FAILED_FIELDS.add(name)) {
				net.neoforbrickit.bridge.util.ForbricLog.warn(
						"[Forbric/Bukkit] JavaPlugin." + name + " is not reachable in this API version; "
								+ "plugins that read it from their constructor will see null",
						missing);
			}
			return null;
		}
	}

	@Override
	public JavaPlugin getPlugin() {
		return plugin;
	}

	/**
	 * Joins the cross-plugin group, so this plugin's classes are reachable by the ones loaded after it and
	 * vice versa.
	 *
	 * <p>Called by {@link PluginHandle} right after construction, before any plugin class is defined beyond
	 * the main one: a plugin whose main class is already looking for a peer's type would otherwise get the
	 * "no other plugin supplies it" answer once and cache that failure.
	 */
	void joinGroup(ForbricClassLoaderGroup group) {
		this.group = group;
	}

	/**
	 * The group this loader looks across, or null before {@link #joinGroup}.
	 *
	 * <p>Paper asks this to find the peers to search; this bridge's {@link #loadClass(String, boolean)} uses
	 * the field directly, so the method exists mainly because the interface declares it — returning null from
	 * it would be a lie about what {@code loadClass} actually does.
	 */
	@Override
	public PluginClassLoaderGroup getGroup() {
		return group;
	}

	@Override
	public void close() throws IOException {
		super.close();
	}

	Path jar() {
		return jar;
	}

	@Override
	public String toString() {
		return "PluginClassLoader[" + jar.getFileName() + "]";
	}
}
