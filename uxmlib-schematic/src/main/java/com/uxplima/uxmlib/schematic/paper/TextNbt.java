package com.uxplima.uxmlib.schematic.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import com.uxplima.uxmlib.schematic.nbt.NbtList;
import com.uxplima.uxmlib.schematic.nbt.NbtTag;

/**
 * A sign's line or a custom name, as a file keeps it and as a component.
 *
 * <p>Until 1.21.5 the game kept text as a JSON string. Since then it keeps it as tags: a plain string for
 * plain text, a compound or a list for anything styled, laid out as the JSON was. Both are read; the
 * current form is written.
 *
 * <p>Text crosses to Adventure as a string and never as a JSON tree. A plugin that bundles Gson relocates it,
 * and a tree of its relocated classes is not the tree of the server's Gson that Adventure takes and gives.
 */
final class TextNbt {

    /** 1.21.5, the first version to keep text as tags rather than as a JSON string. */
    static final int TEXT_AS_TAGS = 4325;

    private TextNbt() {}

    static Component read(NbtTag tag, int dataVersion) {
        try {
            if (tag instanceof NbtTag.StringTag text) {
                return dataVersion < TEXT_AS_TAGS
                        ? GsonComponentSerializer.gson().deserialize(text.value())
                        : Component.text(text.value());
            }
            return GsonComponentSerializer.gson().deserialize(json(tag).toString());
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException notText) {
            return tag instanceof NbtTag.StringTag text ? Component.text(text.value()) : Component.empty();
        }
    }

    static NbtTag write(Component component) {
        return tag(JsonParser.parseString(GsonComponentSerializer.gson().serialize(component)));
    }

    private static JsonElement json(NbtTag tag) {
        return switch (tag) {
            case NbtTag.StringTag text -> new JsonPrimitive(text.value());
            // A byte in text is a style switched on or off.
            case NbtTag.ByteTag flag -> new JsonPrimitive(flag.value() != 0);
            case NbtCompound compound -> {
                JsonObject object = new JsonObject();
                for (Map.Entry<String, NbtTag> entry : compound.entries().entrySet()) {
                    object.add(entry.getKey(), json(entry.getValue()));
                }
                yield object;
            }
            case NbtList list -> {
                JsonArray array = new JsonArray();
                list.values().forEach(value -> array.add(json(value)));
                yield array;
            }
            case NbtTag.IntArrayTag ints -> {
                JsonArray array = new JsonArray();
                for (int value : ints.value()) {
                    array.add(value);
                }
                yield array;
            }
            default -> new JsonPrimitive(NbtTag.asDouble(tag).orElse(0));
        };
    }

    private static NbtTag tag(JsonElement json) {
        if (json.isJsonObject()) {
            NbtCompound.Builder compound = NbtCompound.builder();
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                compound.put(entry.getKey(), tag(entry.getValue()));
            }
            return compound.build();
        }
        if (json.isJsonArray()) {
            List<NbtTag> values = new ArrayList<>();
            json.getAsJsonArray().forEach(value -> values.add(tag(value)));
            boolean mixed = values.stream().map(NbtTag::type).distinct().count() > 1;
            if (mixed) {
                // A list of tags is of one kind. Text that is a bare string becomes a text compound.
                values.replaceAll(value -> value instanceof NbtTag.StringTag text
                        ? NbtCompound.builder().putString("text", text.value()).build()
                        : value);
            }
            return NbtList.of(values);
        }
        if (json.isJsonPrimitive()) {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return new NbtTag.ByteTag((byte) (primitive.getAsBoolean() ? 1 : 0));
            }
            if (primitive.isNumber()) {
                double number = primitive.getAsDouble();
                return number == Math.rint(number) && Math.abs(number) <= Integer.MAX_VALUE
                        ? new NbtTag.IntTag((int) number)
                        : new NbtTag.DoubleTag(number);
            }
            return new NbtTag.StringTag(primitive.getAsString());
        }
        return new NbtTag.StringTag("");
    }
}
