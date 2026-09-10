package com.uxplima.uxmlib.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.Server;

import com.uxplima.uxmlib.item.ItemConfig;

/**
 * Which of somebody else's content plugins this server runs, and the one seam per question that answers for
 * all of them.
 *
 * <p>The one entry point a consumer needs. Hand it the server and the ids the operator left switched on, and
 * it hands back a seam per question that already asks whichever vendors are installed. A server running none
 * of them gets the seam that answers nothing, and every caller reads that as "this is ordinary content".
 *
 * <p>Each registry below is the single source of truth for its question. Adding a vendor is one line here
 * and one new provider, and a consumer that lists what it soft-depends on reads the ids off
 * {@link #customItemVendors()} rather than keeping a second copy that drifts.
 */
public final class ContentHooks {

    private ContentHooks() {}

    /** The custom mob vendors, in the order they are asked. */
    private static final List<Registration<CustomMobs>> CUSTOM_MOBS = List.of(
            new Registration<>("mythicmobs", MythicMobsCustomMobs::new),
            new Registration<>("levelledmobs", LevelledMobsCustomMobs::new),
            new Registration<>("rosestacker", RoseStackerCustomMobs::new));

    /** The custom crop and fishing vendors, in the order they are asked. */
    private static final List<Registration<CustomHarvests>> CUSTOM_HARVESTS = List.of(
            new Registration<>("customcrops", CustomCropsHarvests::new),
            new Registration<>("customfishing", CustomFishingHarvests::new),
            new Registration<>("pyrofishingpro", PyroFishingHarvests::new),
            new Registration<>("infinitefishing", InfiniteFishingHarvests::new));

    /** The skill plugins whose levels can be read. */
    private static final List<Registration<SkillLevels>> SKILLS =
            List.of(new Registration<>("mcmmo", McMmoSkillLevels::new));

    /** The pet plugins whose kills belong to their owner. */
    private static final List<Registration<PetOwners>> PETS = List.of(new Registration<>("mypet", MyPetOwners::new));

    /** The enchantment plugins whose enchantments can trigger us. */
    private static final List<Registration<ForeignEnchantments>> ENCHANTMENTS =
            List.of(new Registration<>("ecoenchants", EcoEnchantsEnchantments::new));

    /** The custom item vendors, in the order they are asked. */
    private static final List<Registration<CustomItems>> CUSTOM_ITEMS = List.of(
            new Registration<>("oraxen", OraxenCustomItems::new),
            new Registration<>("nexo", NexoCustomItems::new),
            new Registration<>("itemsadder", ItemsAdderCustomItems::new),
            new Registration<>("craftengine", CraftEngineCustomItems::new));

    /**
     * Every custom item plugin this server runs and this operator left on.
     *
     * <p>A vendor is constructed and asked whether it is active, which touches its plugin manager entry and
     * nothing of its API. Probing them all on a server with none of them installed is therefore free.
     */
    public static CustomItems customItems(Server server, Set<String> enabled) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(enabled, "enabled");
        List<CustomItems> found = present(server, enabled, CUSTOM_ITEMS);
        return found.isEmpty() ? CustomItems.NONE : new CompositeCustomItems(found);
    }

    /** Every custom item plugin this server runs, whatever the operator said. */
    public static CustomItems customItems(Server server) {
        return customItems(server, customItemVendors());
    }

    /**
     * Let every item file on this server name a custom item.
     *
     * <p>The one call that closes the gap four analyses named. A file that writes
     * {@code material = "oraxen:ruby"} failed at load with "unknown material", and the operator's only
     * remaining option was to write the block it is drawn as, which is a different item. After this it is
     * the ruby, and everything else in the spec, the name, the lore, the enchantments, is applied on top
     * of it exactly as it would be on an ordinary one.
     *
     * <p>Called once at enable by whichever plugin of ours starts first, and it is safe for all of them to
     * call it: the source is the same seam either way. A server with no custom item plugin installs a
     * source that answers nothing, which is what every reader already assumed.
     */
    public static void nameItemsInFiles(CustomItems items) {
        Objects.requireNonNull(items, "items");
        ItemConfig.itemsFrom(items::itemOf);
    }

    /** The ids an operator writes to switch one custom item vendor on or off. */
    public static Set<String> customItemVendors() {
        return idsOf(CUSTOM_ITEMS);
    }

    /** Every custom mob plugin this server runs and this operator left on. */
    public static CustomMobs customMobs(Server server, Set<String> enabled) {
        List<CustomMobs> found = present(server, enabled, CUSTOM_MOBS);
        return found.isEmpty() ? CustomMobs.NONE : new CompositeCustomMobs(found);
    }

    /** Every custom mob plugin this server runs, whatever the operator said. */
    public static CustomMobs customMobs(Server server) {
        return customMobs(server, customMobVendors());
    }

    /** Every custom crop or fishing plugin this server runs and this operator left on. */
    public static CustomHarvests customHarvests(Server server, Set<String> enabled) {
        List<CustomHarvests> found = present(server, enabled, CUSTOM_HARVESTS);
        return found.isEmpty() ? CustomHarvests.NONE : new CompositeCustomHarvests(found);
    }

    /** Every custom crop or fishing plugin this server runs, whatever the operator said. */
    public static CustomHarvests customHarvests(Server server) {
        return customHarvests(server, customHarvestVendors());
    }

    /**
     * The skill plugin this server runs, or the seam that answers nothing.
     *
     * <p>One and not a composite. Two skill plugins holding a level for the same skill name would be two
     * different progressions under one word, and picking either would be a guess: the first present one is
     * the answer, and an operator running two says which by turning the other off.
     */
    public static SkillLevels skillLevels(Server server, Set<String> enabled) {
        List<SkillLevels> found = present(server, enabled, SKILLS);
        return found.isEmpty() ? SkillLevels.NONE : found.getFirst();
    }

    /** The skill plugin this server runs, whatever the operator said. */
    public static SkillLevels skillLevels(Server server) {
        return skillLevels(server, skillVendors());
    }

    /** The pet plugin this server runs, or the seam that answers nothing. */
    public static PetOwners petOwners(Server server, Set<String> enabled) {
        List<PetOwners> found = present(server, enabled, PETS);
        return found.isEmpty() ? PetOwners.NONE : found.getFirst();
    }

    /** The pet plugin this server runs, whatever the operator said. */
    public static PetOwners petOwners(Server server) {
        return petOwners(server, petVendors());
    }

    /** The other enchantment plugin this server runs, or the seam that answers nothing. */
    public static ForeignEnchantments foreignEnchantments(Server server, Set<String> enabled) {
        List<ForeignEnchantments> found = present(server, enabled, ENCHANTMENTS);
        return found.isEmpty() ? ForeignEnchantments.NONE : found.getFirst();
    }

    /** The other enchantment plugin this server runs, whatever the operator said. */
    public static ForeignEnchantments foreignEnchantments(Server server) {
        return foreignEnchantments(server, enchantmentVendors());
    }

    /** The ids an operator writes to switch one custom mob vendor on or off. */
    public static Set<String> customMobVendors() {
        return idsOf(CUSTOM_MOBS);
    }

    /** The ids an operator writes to switch one custom crop or fishing vendor on or off. */
    public static Set<String> customHarvestVendors() {
        return idsOf(CUSTOM_HARVESTS);
    }

    /** The ids an operator writes to switch one skill plugin on or off. */
    public static Set<String> skillVendors() {
        return idsOf(SKILLS);
    }

    /** The ids an operator writes to switch one pet plugin on or off. */
    public static Set<String> petVendors() {
        return idsOf(PETS);
    }

    /** The ids an operator writes to switch one other enchantment plugin on or off. */
    public static Set<String> enchantmentVendors() {
        return idsOf(ENCHANTMENTS);
    }

    /**
     * Every vendor of one question that is installed and left on.
     *
     * <p>Constructing a candidate and asking whether it is active touches its plugin manager entry and
     * nothing of its API, so probing every vendor on a server that runs none of them is free.
     */
    private static <T> List<T> present(Server server, Set<String> enabled, List<Registration<T>> registry) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(enabled, "enabled");
        List<T> found = new ArrayList<>();
        for (Registration<T> candidate : registry) {
            if (!enabled.contains(candidate.id())) {
                continue;
            }
            T built = candidate.factory().apply(server);
            if (isActive(built)) {
                found.add(built);
            }
        }
        return List.copyOf(found);
    }

    private static boolean isActive(Object provider) {
        return switch (provider) {
            case CustomItems items -> items.active();
            case CustomMobs mobs -> mobs.active();
            case CustomHarvests harvests -> harvests.active();
            case SkillLevels skills -> skills.active();
            case PetOwners pets -> pets.active();
            case ForeignEnchantments enchantments -> enchantments.active();
            default -> false;
        };
    }

    private static Set<String> idsOf(List<? extends Registration<?>> registry) {
        List<String> ids = new ArrayList<>(registry.size());
        for (Registration<?> entry : registry) {
            ids.add(entry.id());
        }
        return Set.copyOf(ids);
    }

    /** One vendor: the id an operator writes, and what builds its provider. */
    private record Registration<T>(String id, Function<Server, T> factory) {

        Registration {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(factory, "factory");
        }
    }
}
