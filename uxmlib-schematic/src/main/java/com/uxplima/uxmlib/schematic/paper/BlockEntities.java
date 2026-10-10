package com.uxplima.uxmlib.schematic.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.IntConsumer;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Banner;
import org.bukkit.block.Barrel;
import org.bukkit.block.Beacon;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.BrushableBlock;
import org.bukkit.block.Campfire;
import org.bukkit.block.Chest;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.DecoratedPot;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.HangingSign;
import org.bukkit.block.Hopper;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Lectern;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.Smoker;
import org.bukkit.block.TileState;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.block.TileStateInventoryHolder;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import com.uxplima.uxmlib.schematic.nbt.NbtList;
import com.uxplima.uxmlib.schematic.nbt.NbtTag;
import org.jspecify.annotations.Nullable;

/**
 * What a block holds besides its state, carried by kind.
 *
 * <p>Paper serialises items and entities, and not block entities, so each kind a structure is built with is
 * carried here through the API, in the fields the game itself writes: a container's items and loot table, a
 * sign's two sides, a head's owner and texture, a banner's patterns, a spawner's mob and timings, a command
 * block's command, a jukebox's record, a lectern's book, a beacon's effects, a campfire's food, a brushable
 * block's find, a decorated pot's sherds and a custom name. A block entity of another kind keeps its state
 * and its defaults, and is counted in the report as not carried.
 */
final class BlockEntities {

    /** 1.20, the first version whose signs have two sides. */
    static final int SIGNS_WITH_TWO_SIDES = 3463;

    private BlockEntities() {}

    /** Gives the block entity at {@code block} the fields of {@code data}. Answers whether its kind is carried. */
    static boolean apply(Block block, NbtCompound data, int dataVersion) {
        BlockState state = block.getState();
        if (!(state instanceof TileState)) {
            return false;
        }
        boolean carried = false;
        if (state instanceof Nameable nameable && data.get("CustomName") != null) {
            nameable.customName(TextNbt.read(data.get("CustomName"), dataVersion));
            carried = true;
        }
        if (state instanceof Lootable lootable && data.string("LootTable").isPresent()) {
            LootTable table = lootTable(data.string("LootTable").get());
            if (table != null) {
                lootable.setLootTable(table, longOf(data.get("LootTableSeed")));
            }
            carried = true;
        }
        switch (state) {
            case Lectern lectern -> {
                data.compound("Book")
                        .flatMap(book -> ItemNbt.read(book, dataVersion))
                        .ifPresent(book -> lectern.getSnapshotInventory().setItem(0, book));
                data.intValue("Page").ifPresent(lectern::setPage);
                carried = true;
            }
            case Jukebox jukebox -> {
                data.compound("RecordItem")
                        .flatMap(record -> ItemNbt.read(record, dataVersion))
                        .ifPresent(jukebox::setRecord);
                carried = true;
            }
            case DecoratedPot pot -> {
                data.list("sherds").ifPresent(sherds -> sherds(pot, sherds));
                data.compound("item")
                        .flatMap(item -> ItemNbt.read(item, dataVersion))
                        .ifPresent(item -> pot.getSnapshotInventory().setItem(0, item));
                carried = true;
            }
            case TileStateInventoryHolder holder -> {
                items(data, dataVersion, holder.getSnapshotInventory());
                carried = true;
            }
            default -> {}
        }
        switch (state) {
            case Sign sign -> {
                sign(sign, data, dataVersion);
                carried = true;
            }
            case Skull skull -> {
                skull(skull, data);
                carried = true;
            }
            case Banner banner -> {
                banner.setPatterns(patterns(data));
                carried = true;
            }
            case CreatureSpawner spawner -> {
                spawner(spawner, data);
                carried = true;
            }
            case CommandBlock command -> {
                data.string("Command").ifPresent(command::setCommand);
                carried = true;
            }
            case Beacon beacon -> {
                data.string("primary_effect").map(BlockEntities::effect).ifPresent(beacon::setPrimaryEffect);
                data.string("secondary_effect").map(BlockEntities::effect).ifPresent(beacon::setSecondaryEffect);
                carried = true;
            }
            case Campfire campfire -> {
                campfire(campfire, data, dataVersion);
                carried = true;
            }
            case BrushableBlock brushable -> {
                data.compound("item")
                        .flatMap(item -> ItemNbt.read(item, dataVersion))
                        .ifPresent(brushable::setItem);
                carried = true;
            }
            case BrewingStand stand -> {
                data.intValue("BrewTime").ifPresent(stand::setBrewingTime);
                data.intValue("Fuel").ifPresent(stand::setFuelLevel);
            }
            default -> {}
        }
        state.update(true, false);
        return carried;
    }

    /** The block entity at {@code block} as the game writes it, or nothing for a block that has none. */
    static Optional<Captured> capture(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof TileState)) {
            return Optional.empty();
        }
        NbtCompound.Builder data = NbtCompound.builder();
        if (state instanceof Nameable nameable && nameable.customName() != null) {
            data.put("CustomName", TextNbt.write(nameable.customName()));
        }
        boolean looted = false;
        if (state instanceof Lootable lootable && lootable.getLootTable() != null) {
            data.putString("LootTable", lootable.getLootTable().getKey().asString());
            data.putLong("LootTableSeed", lootable.getSeed());
            looted = true;
        }
        switch (state) {
            case Lectern lectern -> {
                ItemStack book = lectern.getSnapshotInventory().getItem(0);
                if (book != null && !book.isEmpty()) {
                    data.put("Book", ItemNbt.write(book));
                    data.putInt("Page", lectern.getPage());
                }
            }
            case Jukebox jukebox -> {
                ItemStack record = jukebox.getRecord();
                if (!record.isEmpty()) {
                    data.put("RecordItem", ItemNbt.write(record));
                }
            }
            case DecoratedPot pot -> {
                List<NbtTag> sherds = new ArrayList<>();
                for (DecoratedPot.Side side : SIDES) {
                    sherds.add(new NbtTag.StringTag(pot.getSherd(side).getKey().asString()));
                }
                data.put("sherds", NbtList.of(sherds));
                ItemStack item = pot.getSnapshotInventory().getItem(0);
                if (item != null && !item.isEmpty()) {
                    data.put("item", ItemNbt.write(item));
                }
            }
            case TileStateInventoryHolder holder -> {
                // A container still to be looted holds what its loot table rolls, not what was in it.
                if (!looted) {
                    data.put("Items", items(holder.getSnapshotInventory()));
                }
            }
            default -> {}
        }
        switch (state) {
            case Sign sign -> {
                data.put("front_text", side(sign.getSide(Side.FRONT)));
                data.put("back_text", side(sign.getSide(Side.BACK)));
                data.putByte("is_waxed", sign.isWaxed() ? 1 : 0);
            }
            case Skull skull -> {
                ResolvableProfile profile = skull.getProfile();
                if (profile != null) {
                    data.put("profile", profile(profile));
                }
                NamespacedKey sound = skull.getNoteBlockSound();
                if (sound != null) {
                    data.putString("note_block_sound", sound.asString());
                }
            }
            case Banner banner -> {
                List<NbtCompound> patterns = new ArrayList<>();
                for (Pattern pattern : banner.getPatterns()) {
                    NamespacedKey key = bannerPatterns().getKey(pattern.getPattern());
                    if (key != null) {
                        patterns.add(NbtCompound.builder()
                                .putString("pattern", key.asString())
                                .putString("color", pattern.getColor().name().toLowerCase(Locale.ROOT))
                                .build());
                    }
                }
                data.put("patterns", NbtList.of(patterns));
            }
            case CreatureSpawner spawner -> {
                EntityType type = spawner.getSpawnedType();
                if (type != null && type.getKey() != null) {
                    data.put(
                            "SpawnData",
                            NbtCompound.builder()
                                    .put(
                                            "entity",
                                            NbtCompound.builder()
                                                    .putString(
                                                            "id", type.getKey().asString())
                                                    .build())
                                    .build());
                }
                data.putShort("Delay", spawner.getDelay())
                        .putShort("MinSpawnDelay", spawner.getMinSpawnDelay())
                        .putShort("MaxSpawnDelay", spawner.getMaxSpawnDelay())
                        .putShort("SpawnCount", spawner.getSpawnCount())
                        .putShort("MaxNearbyEntities", spawner.getMaxNearbyEntities())
                        .putShort("RequiredPlayerRange", spawner.getRequiredPlayerRange())
                        .putShort("SpawnRange", spawner.getSpawnRange());
            }
            case CommandBlock command -> data.putString("Command", command.getCommand());
            case Beacon beacon -> {
                if (beacon.getPrimaryEffect() != null) {
                    data.putString(
                            "primary_effect",
                            beacon.getPrimaryEffect().getType().getKey().asString());
                }
                if (beacon.getSecondaryEffect() != null) {
                    data.putString(
                            "secondary_effect",
                            beacon.getSecondaryEffect().getType().getKey().asString());
                }
            }
            case Campfire campfire -> {
                List<NbtCompound> items = new ArrayList<>();
                int[] times = new int[campfire.getSize()];
                int[] totals = new int[campfire.getSize()];
                for (int slot = 0; slot < campfire.getSize(); slot++) {
                    ItemStack item = campfire.getItem(slot);
                    if (item != null && !item.isEmpty()) {
                        items.add(ItemNbt.write(item).toBuilder()
                                .putByte("Slot", slot)
                                .build());
                    }
                    times[slot] = campfire.getCookTime(slot);
                    totals[slot] = campfire.getCookTimeTotal(slot);
                }
                data.put("Items", NbtList.of(items))
                        .putIntArray("CookingTimes", times)
                        .putIntArray("CookingTotalTimes", totals);
            }
            case BrushableBlock brushable -> {
                ItemStack item = brushable.getItem();
                if (item != null && !item.isEmpty()) {
                    data.put("item", ItemNbt.write(item));
                }
            }
            case BrewingStand stand ->
                data.putShort("BrewTime", stand.getBrewingTime()).putByte("Fuel", stand.getFuelLevel());
            default -> {}
        }
        return Optional.of(new Captured(idOf(state), data.build()));
    }

    /** A captured block entity: its kind as the game names it, and its fields. */
    record Captured(String id, NbtCompound data) {}

    private static final DecoratedPot.Side[] SIDES = {
        DecoratedPot.Side.BACK, DecoratedPot.Side.LEFT, DecoratedPot.Side.RIGHT, DecoratedPot.Side.FRONT
    };

    private static void items(NbtCompound data, int dataVersion, Inventory inventory) {
        data.list("Items").ifPresent(list -> {
            for (NbtCompound entry : list.compounds()) {
                OptionalInt slot = entry.intValue("Slot");
                if (slot.isPresent() && slot.getAsInt() >= 0 && slot.getAsInt() < inventory.getSize()) {
                    ItemNbt.read(entry, dataVersion).ifPresent(item -> inventory.setItem(slot.getAsInt(), item));
                }
            }
        });
    }

    private static NbtList items(Inventory inventory) {
        List<NbtCompound> items = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.isEmpty()) {
                items.add(ItemNbt.write(item).toBuilder().putByte("Slot", slot).build());
            }
        }
        return NbtList.of(items);
    }

    private static void sign(Sign sign, NbtCompound data, int dataVersion) {
        if (dataVersion < SIGNS_WITH_TWO_SIDES && !data.has("front_text")) {
            SignSide front = sign.getSide(Side.FRONT);
            for (int line = 0; line < 4; line++) {
                NbtTag text = data.get("Text" + (line + 1));
                if (text != null) {
                    front.line(line, TextNbt.read(text, dataVersion));
                }
            }
            data.string("Color").map(BlockEntities::dye).ifPresent(front::setColor);
            data.intValue("GlowingText").ifPresent(glowing -> front.setGlowingText(glowing != 0));
            return;
        }
        data.compound("front_text").ifPresent(side -> side(sign.getSide(Side.FRONT), side, dataVersion));
        data.compound("back_text").ifPresent(side -> side(sign.getSide(Side.BACK), side, dataVersion));
        data.intValue("is_waxed").ifPresent(waxed -> sign.setWaxed(waxed != 0));
    }

    private static void side(SignSide side, NbtCompound data, int dataVersion) {
        data.list("messages").ifPresent(messages -> {
            for (int line = 0; line < Math.min(4, messages.size()); line++) {
                side.line(line, TextNbt.read(messages.values().get(line), dataVersion));
            }
        });
        data.string("color").map(BlockEntities::dye).ifPresent(side::setColor);
        data.intValue("has_glowing_text").ifPresent(glowing -> side.setGlowingText(glowing != 0));
    }

    private static NbtCompound side(SignSide side) {
        List<NbtTag> messages = new ArrayList<>();
        for (int line = 0; line < 4; line++) {
            messages.add(TextNbt.write(side.line(line)));
        }
        DyeColor color = side.getColor();
        return NbtCompound.builder()
                .put("messages", NbtList.of(messages))
                .putString(
                        "color", (color == null ? DyeColor.BLACK : color).name().toLowerCase(Locale.ROOT))
                .putByte("has_glowing_text", side.isGlowingText() ? 1 : 0)
                .build();
    }

    /** A head's owner: the profile of 1.20.5 and after, and the SkullOwner of before. */
    private static void skull(Skull skull, NbtCompound data) {
        Optional<NbtCompound> profile = data.compound("profile");
        Optional<NbtCompound> owner = data.compound("SkullOwner");
        @Nullable PlayerProfile made = null;
        if (profile.isPresent()) {
            made = profile(
                    profile.get().string("name").orElse(null),
                    profile.get().intArray("id").orElse(null),
                    texture(profile.get().list("properties").orElse(null), "name", "value", "signature"));
        } else if (owner.isPresent()) {
            made = profile(
                    owner.get().string("Name").orElse(null),
                    owner.get().intArray("Id").orElse(null),
                    owner.get()
                            .compound("Properties")
                            .flatMap(properties -> properties.list("textures"))
                            .map(textures -> texture(textures, null, "Value", "Signature"))
                            .orElse(null));
        } else if (data.string("SkullOwner").isPresent()) {
            made = profile(data.string("SkullOwner").get(), null, null);
        }
        if (made != null) {
            skull.setProfile(ResolvableProfile.resolvableProfile(made));
        }
        data.string("note_block_sound").map(NamespacedKey::fromString).ifPresent(skull::setNoteBlockSound);
    }

    private static @Nullable ProfileProperty texture(
            @Nullable NbtList properties, @Nullable String nameKey, String valueKey, String signatureKey) {
        if (properties == null) {
            return null;
        }
        for (NbtCompound property : properties.compounds()) {
            boolean textures =
                    nameKey == null || property.string(nameKey).orElse("").equals("textures");
            Optional<String> value = property.string(valueKey);
            if (textures && value.isPresent()) {
                return new ProfileProperty(
                        "textures", value.get(), property.string(signatureKey).orElse(null));
            }
        }
        return null;
    }

    private static @Nullable PlayerProfile profile(
            @Nullable String name, int @Nullable [] id, @Nullable ProfileProperty texture) {
        UUID uuid = id != null && id.length == 4 ? uuid(id) : null;
        if (uuid == null && name == null) {
            if (texture == null) {
                return null;
            }
            uuid = UUID.nameUUIDFromBytes(texture.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        PlayerProfile profile = Bukkit.createProfile(uuid, name);
        if (texture != null) {
            profile.setProperty(texture);
        }
        return profile;
    }

    private static NbtCompound profile(ResolvableProfile profile) {
        NbtCompound.Builder tag = NbtCompound.builder();
        String name = profile.name();
        if (name != null) {
            tag.putString("name", name);
        }
        UUID id = profile.uuid();
        if (id != null) {
            tag.putIntArray("id", ints(id));
        }
        List<NbtCompound> properties = new ArrayList<>();
        for (ProfileProperty property : profile.properties()) {
            NbtCompound.Builder entry =
                    NbtCompound.builder().putString("name", property.getName()).putString("value", property.getValue());
            if (property.getSignature() != null) {
                entry.putString("signature", property.getSignature());
            }
            properties.add(entry.build());
        }
        if (!properties.isEmpty()) {
            tag.put("properties", NbtList.of(properties));
        }
        return tag.build();
    }

    /** A banner's patterns: named, as since 1.20.5, or by the short codes of before. */
    private static List<Pattern> patterns(NbtCompound data) {
        Registry<PatternType> registry = bannerPatterns();
        List<Pattern> patterns = new ArrayList<>();
        data.list("patterns").ifPresent(list -> {
            for (NbtCompound entry : list.compounds()) {
                Optional<PatternType> type =
                        entry.string("pattern").map(NamespacedKey::fromString).map(registry::get);
                Optional<DyeColor> color = entry.string("color").map(BlockEntities::dye);
                if (type.isPresent() && color.isPresent()) {
                    patterns.add(new Pattern(color.get(), type.get()));
                }
            }
        });
        data.list("Patterns").ifPresent(list -> {
            for (NbtCompound entry : list.compounds()) {
                PatternType type = entry.string("Pattern")
                        .map(LEGACY_PATTERNS::get)
                        .map(NamespacedKey::minecraft)
                        .map(registry::get)
                        .orElse(null);
                OptionalInt color = entry.intValue("Color");
                if (type != null && color.isPresent() && color.getAsInt() >= 0 && color.getAsInt() < DYES.size()) {
                    patterns.add(new Pattern(DYES.get(color.getAsInt()), type));
                }
            }
        });
        return patterns;
    }

    /** The dyes by the number files before 1.20.5 wrote them under. */
    private static final List<DyeColor> DYES = List.of(
            DyeColor.WHITE,
            DyeColor.ORANGE,
            DyeColor.MAGENTA,
            DyeColor.LIGHT_BLUE,
            DyeColor.YELLOW,
            DyeColor.LIME,
            DyeColor.PINK,
            DyeColor.GRAY,
            DyeColor.LIGHT_GRAY,
            DyeColor.CYAN,
            DyeColor.PURPLE,
            DyeColor.BLUE,
            DyeColor.BROWN,
            DyeColor.GREEN,
            DyeColor.RED,
            DyeColor.BLACK);

    private static Registry<PatternType> bannerPatterns() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN);
    }

    /** The short codes banners wrote their patterns under before 1.20.5, and the names that replaced them. */
    private static final Map<String, String> LEGACY_PATTERNS = Map.ofEntries(
            Map.entry("b", "base"),
            Map.entry("bl", "square_bottom_left"),
            Map.entry("br", "square_bottom_right"),
            Map.entry("tl", "square_top_left"),
            Map.entry("tr", "square_top_right"),
            Map.entry("bs", "stripe_bottom"),
            Map.entry("ts", "stripe_top"),
            Map.entry("ls", "stripe_left"),
            Map.entry("rs", "stripe_right"),
            Map.entry("cs", "stripe_center"),
            Map.entry("ms", "stripe_middle"),
            Map.entry("drs", "stripe_downright"),
            Map.entry("dls", "stripe_downleft"),
            Map.entry("ss", "small_stripes"),
            Map.entry("cr", "cross"),
            Map.entry("sc", "straight_cross"),
            Map.entry("bt", "triangle_bottom"),
            Map.entry("tt", "triangle_top"),
            Map.entry("bts", "triangles_bottom"),
            Map.entry("tts", "triangles_top"),
            Map.entry("ld", "diagonal_left"),
            Map.entry("rd", "diagonal_up_right"),
            Map.entry("lud", "diagonal_up_left"),
            Map.entry("rud", "diagonal_right"),
            Map.entry("mc", "circle"),
            Map.entry("mr", "rhombus"),
            Map.entry("vh", "half_vertical"),
            Map.entry("hh", "half_horizontal"),
            Map.entry("vhr", "half_vertical_right"),
            Map.entry("hhb", "half_horizontal_bottom"),
            Map.entry("bo", "border"),
            Map.entry("cbo", "curly_border"),
            Map.entry("gra", "gradient"),
            Map.entry("gru", "gradient_up"),
            Map.entry("bri", "bricks"),
            Map.entry("glb", "globe"),
            Map.entry("cre", "creeper"),
            Map.entry("sku", "skull"),
            Map.entry("flo", "flower"),
            Map.entry("moj", "mojang"),
            Map.entry("pig", "piglin"));

    private static void spawner(CreatureSpawner spawner, NbtCompound data) {
        data.compound("SpawnData")
                .flatMap(spawn -> spawn.compound("entity").or(() -> Optional.of(spawn)))
                .flatMap(entity -> entity.string("id"))
                .map(NamespacedKey::fromString)
                .map(Registry.ENTITY_TYPE::get)
                .ifPresent(spawner::setSpawnedType);
        // The spawn delays are checked against each other, so the widest is set first.
        set(data, "MaxSpawnDelay", spawner::setMaxSpawnDelay);
        set(data, "MinSpawnDelay", spawner::setMinSpawnDelay);
        set(data, "MaxSpawnDelay", spawner::setMaxSpawnDelay);
        set(data, "Delay", spawner::setDelay);
        set(data, "SpawnCount", spawner::setSpawnCount);
        set(data, "MaxNearbyEntities", spawner::setMaxNearbyEntities);
        set(data, "RequiredPlayerRange", spawner::setRequiredPlayerRange);
        set(data, "SpawnRange", spawner::setSpawnRange);
    }

    private static void set(NbtCompound data, String key, IntConsumer setter) {
        data.intValue(key).ifPresent(value -> {
            try {
                setter.accept(value);
            } catch (IllegalArgumentException outOfRange) {
                // A value the server refuses keeps its default.
            }
        });
    }

    private static void campfire(Campfire campfire, NbtCompound data, int dataVersion) {
        data.list("Items").ifPresent(list -> {
            for (NbtCompound entry : list.compounds()) {
                OptionalInt slot = entry.intValue("Slot");
                if (slot.isPresent() && slot.getAsInt() >= 0 && slot.getAsInt() < campfire.getSize()) {
                    ItemNbt.read(entry, dataVersion).ifPresent(item -> campfire.setItem(slot.getAsInt(), item));
                }
            }
        });
        data.intArray("CookingTimes").ifPresent(times -> {
            for (int slot = 0; slot < Math.min(times.length, campfire.getSize()); slot++) {
                campfire.setCookTime(slot, times[slot]);
            }
        });
        data.intArray("CookingTotalTimes").ifPresent(times -> {
            for (int slot = 0; slot < Math.min(times.length, campfire.getSize()); slot++) {
                campfire.setCookTimeTotal(slot, times[slot]);
            }
        });
    }

    private static void sherds(DecoratedPot pot, NbtList sherds) {
        for (int i = 0; i < Math.min(SIDES.length, sherds.size()); i++) {
            if (sherds.values().get(i) instanceof NbtTag.StringTag id) {
                Material material = Material.matchMaterial(id.value());
                if (material != null) {
                    pot.setSherd(SIDES[i], material);
                }
            }
        }
    }

    private static @Nullable PotionEffectType effect(String key) {
        NamespacedKey parsed = NamespacedKey.fromString(key);
        return parsed == null ? null : Registry.EFFECT.get(parsed);
    }

    private static @Nullable LootTable lootTable(String key) {
        NamespacedKey parsed = NamespacedKey.fromString(key);
        return parsed == null ? null : Bukkit.getLootTable(parsed);
    }

    private static @Nullable DyeColor dye(String name) {
        try {
            return DyeColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static long longOf(@Nullable NbtTag tag) {
        if (tag instanceof NbtTag.LongTag value) {
            return value.value();
        }
        return tag == null ? 0L : NbtTag.asInt(tag).orElse(0);
    }

    private static UUID uuid(int[] id) {
        long most = ((long) id[0] << 32) | (id[1] & 0xFFFFFFFFL);
        long least = ((long) id[2] << 32) | (id[3] & 0xFFFFFFFFL);
        return new UUID(most, least);
    }

    private static int[] ints(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
    }

    /** The kind of block entity the game names for this state. */
    private static String idOf(BlockState state) {
        String kind =
                switch (state) {
                    case Chest chest -> chest.getType() == Material.TRAPPED_CHEST ? "trapped_chest" : "chest";
                    case Barrel barrel -> "barrel";
                    case ShulkerBox box -> "shulker_box";
                    case Hopper hopper -> "hopper";
                    case Dropper dropper -> "dropper";
                    case Dispenser dispenser -> "dispenser";
                    case BlastFurnace furnace -> "blast_furnace";
                    case Smoker smoker -> "smoker";
                    case Furnace furnace -> "furnace";
                    case BrewingStand stand -> "brewing_stand";
                    case HangingSign sign -> "hanging_sign";
                    case Sign sign -> "sign";
                    case Skull skull -> "skull";
                    case Banner banner -> "banner";
                    case CreatureSpawner spawner -> "mob_spawner";
                    case CommandBlock command -> "command_block";
                    case Jukebox jukebox -> "jukebox";
                    case Lectern lectern -> "lectern";
                    case Beacon beacon -> "beacon";
                    case Campfire campfire -> "campfire";
                    case BrushableBlock brushable -> "brushable_block";
                    case DecoratedPot pot -> "decorated_pot";
                    case ChiseledBookshelf shelf -> "chiseled_bookshelf";
                    default -> state.getType().getKey().getKey();
                };
        return "minecraft:" + kind;
    }
}
