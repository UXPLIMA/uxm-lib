package com.uxplima.uxmlib.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The seven questions, on a server that runs none of the plugins they ask.
 *
 * <p>That is the case worth proving and the case every one of our customers is in on day one. A seam that
 * throws, logs or loads a class on a server with none of these plugins installed is a seam that costs every
 * server something to serve the few that have one.
 *
 * <p>The composites and the folds are proved beside it with fakes, because a fold is arithmetic and does not
 * need a vendor to check.
 */
final class ContentHooksTest {

    private ServerMock server;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        Reflected.forgetEverything();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
        Reflected.forgetEverything();
    }

    @Test
    @DisplayName("with none of them installed every seam is the one that answers nothing")
    void aBareServerGetsTheEmptySeams() {
        assertThat(ContentHooks.customItems(server)).isSameAs(CustomItems.NONE);
        assertThat(ContentHooks.customMobs(server)).isSameAs(CustomMobs.NONE);
        assertThat(ContentHooks.customHarvests(server)).isSameAs(CustomHarvests.NONE);
        assertThat(ContentHooks.skillLevels(server)).isSameAs(SkillLevels.NONE);
        assertThat(ContentHooks.petOwners(server)).isSameAs(PetOwners.NONE);
        assertThat(ContentHooks.foreignEnchantments(server)).isSameAs(ForeignEnchantments.NONE);
    }

    @Test
    @DisplayName("the empty seams answer nothing rather than throwing, which is what every caller reads")
    void theEmptySeamsAnswerNothing() {
        ItemStack stack = new ItemStack(Material.DIAMOND);

        assertThat(CustomItems.NONE.idOf(stack)).isEmpty();
        assertThat(CustomItems.NONE.itemOf("oraxen:ruby")).isEmpty();
        assertThat(CustomMobs.NONE.countOf(server.addPlayer()))
                .describedAs("one and not zero: an entity nobody stacked is one entity")
                .isEqualTo(1);
        assertThat(CustomHarvests.NONE.catchOf(stack)).isEmpty();
        assertThat(SkillLevels.NONE.levelOf(UUID.randomUUID(), "mining")).isEmpty();
        assertThat(PetOwners.NONE.ownerOf(server.addPlayer())).isEmpty();
        assertThat(ForeignEnchantments.NONE.on(stack)).isEmpty();
    }

    @Test
    @DisplayName("every provider says it is inactive on a server that does not run it, and loads no class doing so")
    void everyProviderIsInactiveWithoutItsPlugin() {
        assertThat(new OraxenCustomItems(server).active()).isFalse();
        assertThat(new NexoCustomItems(server).active()).isFalse();
        assertThat(new ItemsAdderCustomItems(server).active()).isFalse();
        assertThat(new CraftEngineCustomItems(server).active()).isFalse();
        assertThat(new MythicMobsCustomMobs(server).active()).isFalse();
        assertThat(new LevelledMobsCustomMobs(server).active()).isFalse();
        assertThat(new RoseStackerCustomMobs(server).active()).isFalse();
        assertThat(new CustomCropsHarvests(server).active()).isFalse();
        assertThat(new CustomFishingHarvests(server).active()).isFalse();
        assertThat(new PyroFishingHarvests(server).active()).isFalse();
        assertThat(new InfiniteFishingHarvests(server).active()).isFalse();
        assertThat(new McMmoSkillLevels(server).active()).isFalse();
        assertThat(new MyPetOwners(server).active()).isFalse();
        assertThat(new EcoEnchantsEnchantments(server).active()).isFalse();
    }

    @Test
    @DisplayName("an inactive provider answers nothing to every question, rather than throwing")
    void anInactiveProviderAnswersNothing() {
        ItemStack stack = new ItemStack(Material.DIAMOND);

        assertThat(new OraxenCustomItems(server).idOf(stack)).isEmpty();
        assertThat(new OraxenCustomItems(server).itemOf("oraxen:ruby")).isEmpty();
        assertThat(new MythicMobsCustomMobs(server).countOf(server.addPlayer())).isEqualTo(1);
        assertThat(new CustomFishingHarvests(server).catchOf(stack)).isEmpty();
        assertThat(new McMmoSkillLevels(server).levelOf(UUID.randomUUID(), "mining"))
                .isEmpty();
        assertThat(new EcoEnchantsEnchantments(server).on(stack)).isEmpty();
    }

    @Test
    @DisplayName("the item composite takes the first answer, so two vendors do not fight over one item")
    void theItemCompositeTakesTheFirstAnswer() {
        CustomItems first = fakeItems("oraxen:ruby");
        CustomItems second = fakeItems("nexo:ruby");

        CompositeCustomItems both = new CompositeCustomItems(List.of(first, second));

        assertThat(both.active()).isTrue();
        assertThat(both.idOf(new ItemStack(Material.DIAMOND))).contains("oraxen:ruby");
    }

    @Test
    @DisplayName("the mob composite takes the largest count, because a stacker knows what a mob plugin does not")
    void theMobCompositeTakesTheLargestCount() {
        CompositeCustomMobs both = new CompositeCustomMobs(List.of(fakeMobs("mythicmobs:king", 1), fakeMobs(null, 40)));

        assertThat(both.countOf(server.addPlayer()))
                .describedAs("a player who killed a stack of forty killed forty")
                .isEqualTo(40);
        assertThat(both.idOf(server.addPlayer())).contains("mythicmobs:king");
    }

    @Test
    @DisplayName("an operator who switched a vendor off is not asked about it")
    void avendorTheOperatorSwitchedOffIsNotAsked() {
        assertThat(ContentHooks.customItems(server, java.util.Set.of())).isSameAs(CustomItems.NONE);
        assertThat(ContentHooks.customItemVendors())
                .describedAs("the ids an operator writes, read off the registry rather than kept twice")
                .contains("oraxen", "nexo", "itemsadder", "craftengine");
        assertThat(ContentHooks.customMobVendors()).contains("mythicmobs", "levelledmobs", "rosestacker");
        assertThat(ContentHooks.customHarvestVendors())
                .contains("customcrops", "customfishing", "pyrofishingpro", "infinitefishing");
        assertThat(ContentHooks.skillVendors()).containsExactly("mcmmo");
        assertThat(ContentHooks.petVendors()).containsExactly("mypet");
        assertThat(ContentHooks.enchantmentVendors()).containsExactly("ecoenchants");
    }

    @Test
    @DisplayName("fifteen providers over seven questions, so none of the chosen list was quietly dropped")
    void everyChosenProviderIsRegistered() {
        int providers = ContentHooks.customItemVendors().size()
                + ContentHooks.customMobVendors().size()
                + ContentHooks.customHarvestVendors().size()
                + ContentHooks.skillVendors().size()
                + ContentHooks.petVendors().size()
                + ContentHooks.enchantmentVendors().size();

        assertThat(providers)
                .describedAs("fourteen here and WorldGuard in the region seam, which already had one")
                .isEqualTo(14);
    }

    @Test
    @DisplayName("a method this server's vendor does not carry is nothing, and asking twice costs one lookup")
    void amissingMethodIsRememberedAsMissing() {
        assertThat(Reflected.method("java.lang.String", "thereIsNoSuchMethod")).isEmpty();
        assertThat(Reflected.method("java.lang.String", "thereIsNoSuchMethod")).isEmpty();
        assertThat(Reflected.type("com.example.NotHere")).isEmpty();
        assertThat(Reflected.method("com.example.NotHere", "anything")).isEmpty();
    }

    @Test
    @DisplayName("a method that is there is found and called, which is what the providers do all day")
    void amethodThatIsThereIsCalled() {
        assertThat(Reflected.method("java.lang.String", "trim")).isPresent();
        assertThat(Reflected.callForString(
                        Reflected.method("java.lang.String", "trim").orElseThrow(), "  spaced  "))
                .contains("spaced");
    }

    private static CustomItems fakeItems(String id) {
        return new CustomItems() {

            @Override
            public boolean active() {
                return true;
            }

            @Override
            public Optional<String> idOf(ItemStack stack) {
                return Optional.of(id);
            }

            @Override
            public Optional<String> idOfBlock(org.bukkit.block.Block block) {
                return Optional.of(id);
            }

            @Override
            public Optional<ItemStack> itemOf(String wanted) {
                return wanted.equals(id) ? Optional.of(new ItemStack(Material.DIAMOND)) : Optional.empty();
            }
        };
    }

    private static CustomMobs fakeMobs(@org.jspecify.annotations.Nullable String id, int count) {
        return new CustomMobs() {

            @Override
            public boolean active() {
                return true;
            }

            @Override
            public Optional<String> idOf(org.bukkit.entity.Entity entity) {
                return Optional.ofNullable(id);
            }

            @Override
            public int countOf(org.bukkit.entity.Entity entity) {
                return count;
            }
        };
    }

    @Test
    @DisplayName("with nothing installed a name is the vanilla one, which is what every file already reads")
    void abareServerNamesTheVanillaThing() {
        ContentNames.forgetEverything();

        assertThat(ContentNames.active()).isFalse();
        assertThat(ContentNames.of(new ItemStack(Material.DEEPSLATE))).isEqualTo("deepslate");
        assertThat(ContentNames.countOf(server.addPlayer())).isEqualTo(1);
    }

    @Test
    @DisplayName("a custom item is named by its own id, and the upper case form keeps the vendor's spelling")
    void acustomItemKeepsItsOwnSpelling() {
        ContentNames.namedBy(fakeItems("oraxen:ruby_ore"), CustomMobs.NONE, CustomHarvests.NONE);

        assertThat(ContentNames.of(new ItemStack(Material.DEEPSLATE))).isEqualTo("oraxen:ruby_ore");
        assertThat(ContentNames.active()).isTrue();
        ContentNames.forgetEverything();
    }

    @Test
    @DisplayName("a stacked mob counts as its stack through the static seam too")
    void thestaticSeamCountsAStack() {
        ContentNames.namedBy(CustomItems.NONE, fakeMobs("mythicmobs:king", 40), CustomHarvests.NONE);

        assertThat(ContentNames.countOf(server.addPlayer())).isEqualTo(40);
        assertThat(ContentNames.of(server.addPlayer())).isEqualTo("mythicmobs:king");
        ContentNames.forgetEverything();
    }

    @Test
    @DisplayName("a foreign enchantment keeps the namespace its own key carries")
    void aforeignEnchantmentKeepsItsNamespace() {
        MockBukkit.createMockPlugin("EcoEnchants");
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> {
            meta.getPersistentDataContainer()
                    .set(
                            new org.bukkit.NamespacedKey("ecoenchants", "telekinesis"),
                            org.bukkit.persistence.PersistentDataType.INTEGER,
                            2);
            meta.getPersistentDataContainer()
                    .set(
                            new org.bukkit.NamespacedKey("someone-else", "windup"),
                            org.bukkit.persistence.PersistentDataType.INTEGER,
                            1);
        });

        var found = new EcoEnchantsEnchantments(server).on(sword);

        assertThat(found)
                .describedAs("stamping one namespace over all of them would rename a third plugin's"
                        + " enchantment into EcoEnchants'")
                .containsEntry("ecoenchants:telekinesis", 2)
                .containsEntry("someone-else:windup", 1);
    }
}
