package com.uxplima.uxmlib.claim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.common.Log;
import org.jspecify.annotations.NullMarked;

/**
 * Discovers which land-claim plugins are installed and binds a {@link CompositeClaimProvider} over every one
 * that is present and left enabled in config, or the no-op provider when none applies. It is the one entry
 * point a consumer needs: hand it the operator's choices, the plugin, the server and a log, and it hands back
 * a {@link ClaimProvider} that already answers for every claim plugin the server actually runs.
 *
 * <p>Every candidate (uxmClaims, Lands, GriefPrevention, GriefDefender, ExcellentClaims, SimpleClaimSystem,
 * RClaim, XClaim, Homestead, WorldGuard, Towny, KingdomsX, HuskClaims, HuskTowns, Factions, BentoBox, Residence,
 * PlotSquared,
 * SuperiorSkyblock2)
 * is constructed and asked {@link ClaimProvider#active()}; those that are both
 * active and enabled become composite members, so a server running two claim plugins consults both and their
 * answers are folded per {@link ClaimProvidersConfig#combine()}. Ordering no longer matters. Constructing a
 * candidate never loads its plugin SDK (each typed provider keeps its references behind its own present-guard,
 * and the reflective providers touch no SDK type at class-load time), so probing them on a server without any
 * claim plugin is safe and the no-op {@link #INACTIVE} provider is returned.
 */
@NullMarked
public final class ClaimProviders {

    private ClaimProviders() {}

    private static final ClaimProvider INACTIVE = new InactiveClaimProvider();

    /**
     * The one ordered registry of claim-provider candidates: each pairs a {@code claims.providers} config key
     * with the factory that builds its (lazily-guarded) provider. It is the single source of truth for the
     * provider set. {@link #detectAll} folds every entry into the composite and {@link #candidateKeys()}
     * exposes their keys, so adding a provider is one edit here and nowhere else. A consumer that lists the
     * claim plugins it soft-depends on, in a {@code paper-plugin.yml} or in its own documentation, reads the
     * set off {@link #candidateKeys()} rather than keeping a second copy that drifts.
     */
    private static final List<Registration> REGISTRY = List.of(
            new Registration("uxmclaims", "uxmClaims", UxmClaimsClaimProvider::new),
            new Registration("lands", "Lands", LandsClaimProvider::new),
            new Registration("griefprevention", "GriefPrevention", GriefPreventionClaimProvider::new),
            new Registration("griefdefender", "GriefDefender", GriefDefenderClaimProvider::new),
            new Registration("excellentclaims", "ExcellentClaims", ExcellentClaimsClaimProvider::new),
            new Registration("simpleclaimsystem", "SimpleClaimSystem", SimpleClaimSystemClaimProvider::new),
            new Registration("rclaim", "RClaim", RClaimClaimProvider::new),
            new Registration("xclaim", "XClaim", XClaimClaimProvider::new),
            new Registration("homestead", "Homestead", HomesteadClaimProvider::new),
            new Registration("worldguard", "WorldGuard", WorldGuardClaimProvider::new),
            new Registration("towny", "Towny", TownyClaimProvider::new),
            new Registration("kingdoms", "Kingdoms", KingdomsClaimProvider::new),
            new Registration("huskclaims", "HuskClaims", HuskClaimsClaimProvider::new),
            new Registration("husktowns", "HuskTowns", HuskTownsClaimProvider::new),
            new Registration("factions", "Factions", FactionsClaimProvider::new),
            new Registration("bentobox", "BentoBox", BentoBoxClaimProvider::new),
            new Registration("residence", "Residence", ResidenceClaimProvider::new),
            new Registration("plotsquared", "PlotSquared", PlotSquaredClaimProvider::new),
            new Registration("superiorskyblock", "SuperiorSkyblock2", SuperiorSkyblockClaimProvider::new));

    /**
     * Binds a composite over every claim plugin that is both installed-and-active and enabled in
     * {@code config}, or the no-op provider when none qualifies. The returned provider's answers are folded
     * per {@link ClaimProvidersConfig#combine()}.
     */
    public static ClaimProvider detectAll(ClaimProvidersConfig config, Plugin plugin, Server server, Log log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");

        List<Candidate> candidates = REGISTRY.stream()
                .map(registration ->
                        new Candidate(registration.key(), registration.factory().create(plugin, server, log)))
                .toList();
        return compose(config, candidates, log);
    }

    /**
     * The {@code claims.providers} key of every registered candidate, in registry order. This is the single
     * source of truth two surfaces read rather than keeping their own copy: {@link ClaimProvidersConfig#from}
     * validates operator keys against it (so a typo'd key that toggles nothing is flagged), and a consumer
     * walks it to render the toggle list an operator edits, rather than retyping the nineteen names.
     */
    public static List<String> candidateKeys() {
        return REGISTRY.stream().map(Registration::key).toList();
    }

    /**
     * The Bukkit plugin name every candidate looks for, in registry order.
     *
     * <p>What a consumer declares in its {@code paper-plugin.yml}, each with {@code load: BEFORE} and
     * {@code required: false}. A claim plugin that is not declared there is a coin flip on the load order:
     * on the servers where it loses, this detects nothing for the whole run and the only symptom is a
     * plugin that stops respecting claims. Reading the set from here rather than retyping nineteen names
     * is what keeps a consumer's file in step with the registry.
     *
     * <p>{@code uxmClaims} is in the list even though its provider is reached by class rather than by the
     * plugin manager, and for the same reason as the rest: {@link #detectAll} asks every candidate
     * {@link ClaimProvider#active()} once, while the composite is built, and folds in only the ones that
     * answered yes. A candidate that answers no at that moment is out for the whole run. uxmClaims answers
     * by resolving a class out of another plugin's jar, so a uxmClaims that loads after the caller has no
     * class to find. uxmEssentials excused it on the grounds that it names no plugin, and that was the
     * wrong reading of what the load order decides here.
     */
    public static List<String> candidatePluginNames() {
        return REGISTRY.stream().map(Registration::pluginName).toList();
    }

    /**
     * Folds the candidates that are both {@link ClaimProvidersConfig#enabled(String) enabled} and
     * {@link ClaimProvider#active() active} into a composite, or returns the no-op provider when none qualifies.
     * Split from {@link #detectAll} so the enable/active selection can be exercised with fake candidates.
     */
    static ClaimProvider compose(ClaimProvidersConfig config, List<Candidate> candidates, Log log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(log, "log");
        List<ClaimProvider> members = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            if (config.enabled(candidate.configKey()) && candidate.provider().active()) {
                log.info(
                        "event=claim_provider_bound provider={} combine={}",
                        candidate.provider().getClass().getSimpleName(),
                        config.combine().configName());
                members.add(candidate.provider());
            }
        }
        return members.isEmpty() ? INACTIVE : new CompositeClaimProvider(members, config.combine());
    }

    /** A discovery candidate: its {@code claims.providers} config key paired with the (lazily-guarded) provider. */
    record Candidate(String configKey, ClaimProvider provider) {

        Candidate {
            Objects.requireNonNull(configKey, "configKey");
            Objects.requireNonNull(provider, "provider");
        }
    }

    /** Builds a candidate's provider from the wiring context; every claim provider shares this constructor shape. */
    @FunctionalInterface
    private interface ProviderFactory {
        ClaimProvider create(Plugin plugin, Server server, Log log);
    }

    /**
     * One candidate: the {@code claims.providers} key an operator toggles, the Bukkit plugin name it looks
     * for, and the factory that builds it.
     *
     * <p>The plugin name is here rather than only inside the provider because a consumer needs it before
     * anything is built: a claim plugin that is not declared in a {@code paper-plugin.yml} may load after
     * the plugin that asks for it, and then it reads as absent for the whole run and nothing says why.
     */
    private record Registration(String key, String pluginName, ProviderFactory factory) {

        Registration {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(pluginName, "pluginName");
            Objects.requireNonNull(factory, "factory");
        }
    }

    /** The provider used when no claim plugin is installed: inactive, so every policy check short-circuits. */
    private static final class InactiveClaimProvider implements ClaimProvider {

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public Optional<ClaimLookup> claimAt(ClaimWorld world, int blockX, int blockZ) {
            Objects.requireNonNull(world, "world");
            return Optional.empty();
        }
    }
}
