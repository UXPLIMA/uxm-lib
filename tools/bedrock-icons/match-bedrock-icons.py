#!/usr/bin/env python3
"""Match every Java item to the Bedrock texture a form button shows for it.

A Bedrock form button takes a texture path from the client's own pack. Java and Bedrock name most
textures differently (golden_apple is textures/items/apple_golden), so a name rule misses most of
them. This script reads both games' art and matches by pixels:

    git clone --depth 1 --filter=blob:none --sparse https://github.com/Mojang/bedrock-samples.git samples
    git -C samples sparse-checkout set --no-cone /resource_pack/textures/
    python3 match-bedrock-icons.py 26.3 samples > bedrock-icons.txt

For each item the client jar's item definition gives the model, the model chain gives the texture
the item is drawn with (layer0 for a flat item, a front or side face for a block), and the first of
these that holds a match wins:

    1. a hand-kept OVERRIDES entry, for items Bedrock draws from another sheet (beds, potions,
       stained glass, shulker boxes, chests, leaves);
    2. a Bedrock texture with the very same pixels, items before blocks;
    3. a key of item_texture.json equal to the item id;
    4. a Bedrock texture file named like the item or its texture;
    5. the same pixels for another face of the model.

An item with no match is printed with no path: Bedrock draws it as an entity (a head, a banner, a
shield), so the button shows no picture rather than the missing texture. The jar is deleted at the
end. No downloaded file enters a repository.

Requires Pillow.
"""

import collections
import hashlib
import json
import os
import pathlib
import re
import sys
import tempfile
import urllib.request
import zipfile

from PIL import Image

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
ORDER = ["layer0", "all", "front", "side", "texture", "cross", "plant", "top", "end", "pattern", "wool", "particle"]

COLOURS = ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']
def bc(c): return 'silver' if c == 'light_gray' else c
OVERRIDES = {
    'potion': 'textures/items/potion_bottle_drinkable',
    'splash_potion': 'textures/items/potion_bottle_splash',
    'lingering_potion': 'textures/items/potion_bottle_lingering',
    'shulker_box': 'textures/blocks/shulker_top_undyed',
    'chest': 'textures/blocks/chest_front',
    'trapped_chest': 'textures/blocks/trapped_chest_front',
    'ender_chest': 'textures/blocks/ender_chest_front',
    'compass': 'textures/items/compass_item',
    'firework_star': 'textures/items/fireworks_charge',
    'music_disc_11': 'textures/items/record_11',
    'music_disc_stal': 'textures/items/record_stal',
    'music_disc_strad': 'textures/items/record_strad',
    'abandoned_camp_map': 'textures/items/map_abandoned_campsite',
    'buried_ancient_city_map': 'textures/items/map_ancient_city',
    'warm_ocean_ruins_map': 'textures/items/map_warm_ocean_ruins',
    'conduit': 'textures/blocks/conduit_closed',
    'lily_pad': 'textures/blocks/carried_waterlily',
    'short_grass': 'textures/blocks/tallgrass_carried',
    'tall_grass': 'textures/blocks/double_plant_grass_carried',
    'large_fern': 'textures/blocks/double_plant_fern_carried',
    'lilac': 'textures/blocks/double_plant_syringa_top',
    'pointed_dripstone': 'textures/blocks/pointed_dripstone_down_tip',
    'redstone_torch': 'textures/blocks/redstone_torch_on',
    'smooth_stone': 'textures/blocks/stone_slab_top',
    'smooth_stone_slab': 'textures/blocks/stone_slab_side',
    'purple_glazed_terracotta': 'textures/blocks/glazed_terracotta_purple',
    'bubble_coral_block': 'textures/blocks/coral_purple',
    'fire_coral_block': 'textures/blocks/coral_red',
    'light': 'textures/items/light_block_15',
    'anvil': 'textures/blocks/anvil_top_damaged_0',
    'tipped_arrow': 'textures/items/tipped_arrow',
    'flowering_azalea_leaves': 'textures/blocks/azalea_leaves_flowers',
    'decorated_pot': 'textures/blocks/decorated_pot_side',
    'mooshroom_spawn_egg': 'textures/items/spawn_eggs/spawn_egg_mooshroom',
    'wooden_spear': 'textures/items/spear/wood_spear',
    'golden_spear': 'textures/items/spear/gold_spear',
    'spectral_arrow': 'textures/items/arrow',
    'oak_leaves': 'textures/blocks/leaves_oak_carried',
    'spruce_leaves': 'textures/blocks/leaves_spruce_carried',
    'birch_leaves': 'textures/blocks/leaves_birch_carried',
    'jungle_leaves': 'textures/blocks/leaves_jungle_carried',
    'acacia_leaves': 'textures/blocks/leaves_acacia_carried',
    'dark_oak_leaves': 'textures/blocks/leaves_big_oak_carried',
}
for w in ('spruce_planks','spruce_button','spruce_fence','spruce_fence_gate','spruce_pressure_plate','spruce_slab','spruce_stairs'):
    OVERRIDES[w] = 'textures/blocks/planks_spruce'
for ox in ('', 'exposed_', 'weathered_', 'oxidized_'):
    for wax in ('', 'waxed_'):
        OVERRIDES[f'{wax}{ox}copper_chest'] = f'textures/blocks/{ox}copper_chest_inventory_front'
for c in COLOURS:
    OVERRIDES[f'{c}_bed'] = f'textures/items/bed_{bc(c)}'
    OVERRIDES[f'{c}_shulker_box'] = f'textures/blocks/shulker_top_{bc(c)}'
    OVERRIDES[f'{c}_stained_glass'] = f'textures/blocks/glass_{bc(c)}'
    OVERRIDES[f'{c}_stained_glass_pane'] = f'textures/blocks/glass_{bc(c)}'


def fetch(url):
    with urllib.request.urlopen(url) as response:
        return response.read()


def client_jar(version, into):
    manifest = json.loads(fetch(MANIFEST))
    entry = next((v for v in manifest["versions"] if v["id"] == version), None)
    if entry is None:
        raise SystemExit(f"version {version} is not in the manifest")
    meta = json.loads(fetch(entry["url"]))
    jar = into / "client.jar"
    jar.write_bytes(fetch(meta["downloads"]["client"]["url"]))
    return jar


def load(path):
    """A JSON file; Bedrock's carry // comment lines."""
    text = re.sub(r"^\s*//.*$", "", pathlib.Path(path).read_text(), flags=re.M)
    return json.loads(text)


def strip(ref):
    return ref.split(":", 1)[-1]


def pixels(path):
    """A digest of the image's pixels; an animated strip counts by its first frame."""
    try:
        image = Image.open(path).convert("RGBA")
    except OSError:
        return None
    width, height = image.size
    if height > width and height % width == 0:
        image = image.crop((0, 0, width, width))
        height = width
    data = bytearray()
    raw = image.tobytes()
    for i in range(0, len(raw), 4):
        r, g, b, a = raw[i : i + 4]
        data += bytes((a, r, g, b)) if a else bytes(4)
    return hashlib.sha1(f"{width}x{height}".encode() + bytes(data)).hexdigest()


def first_model(node):
    if isinstance(node, dict):
        if node.get("type", "").endswith("model") and "model" in node:
            return node["model"]
        for key in ("model", "on_false", "fallback", "on_true"):
            if key in node:
                found = first_model(node[key])
                if found:
                    return found
        for key in ("cases", "entries", "models"):
            for case in node.get(key, []):
                found = first_model(case)
                if found:
                    return found
    if isinstance(node, list):
        for case in node:
            found = first_model(case)
            if found:
                return found
    return None


def textures(java, model):
    """The model chain's texture variables, resolved to texture names."""
    found = {}
    for _ in range(20):
        if not model:
            break
        path = java / "models" / (strip(model) + ".json")
        if not path.exists():
            break
        definition = load(path)
        for key, value in definition.get("textures", {}).items():
            found.setdefault(key, value)
        model = definition.get("parent")

    def resolve(value):
        for _ in range(10):
            if not (isinstance(value, str) and value.startswith("#")):
                break
            value = found.get(value[1:])
        return strip(value) if isinstance(value, str) and not value.startswith("#") else None

    return {key: resolve(value) for key, value in found.items() if resolve(value)}


def match(java, bedrock):
    files = {}
    for folder in ("textures/items", "textures/blocks"):
        for root, _, names in os.walk(bedrock / folder):
            for name in names:
                stem, ext = os.path.splitext(name)
                if ext in (".png", ".tga"):
                    files.setdefault(os.path.relpath(os.path.join(root, stem), bedrock), pathlib.Path(root, name))
    by_name = collections.defaultdict(list)
    by_pixels = collections.defaultdict(list)
    for rel, path in files.items():
        by_name[rel.rsplit("/", 1)[1]].append(rel)
        if path.suffix == ".png":
            digest = pixels(path)
            if digest:
                by_pixels[digest].append(rel)
    java_pixels = {}
    for path in (java / "textures").rglob("*.png"):
        java_pixels[str(path.relative_to(java / "textures").with_suffix(""))] = pixels(path)
    keys = load(bedrock / "textures/item_texture.json")["texture_data"]

    table = {}
    for definition in sorted((java / "items").glob("*.json")):
        item = definition.stem
        if item == "air":
            continue
        model = first_model(load(definition).get("model"))
        found = textures(java, model) if model else {}
        names = [found[k] for k in ORDER if k in found] + [v for k, v in found.items() if k not in ORDER]
        names = [n for n in names if "overlay" not in n] or names
        pick = OVERRIDES.get(item)
        if not pick and names and by_pixels.get(java_pixels.get(names[0])):
            same = by_pixels[java_pixels[names[0]]]
            pick = sorted(same, key=lambda r: (not r.startswith("textures/items"), r.rsplit("/", 1)[1] != item, len(r), r))[0]
        if not pick and item in keys:
            texture = keys[item]["textures"]
            texture = texture[0] if isinstance(texture, list) else texture
            pick = texture.get("path") if isinstance(texture, dict) else texture
        if not pick:
            for name in [item] + [n.rsplit("/", 1)[-1] for n in names[:1]]:
                same = sorted(by_name.get(name, []), key=lambda r: (not r.startswith("textures/items"), len(r)))
                if same:
                    pick = same[0]
                    break
        if not pick:
            for name in names[1:]:
                same = by_pixels.get(java_pixels.get(name))
                if same:
                    pick = sorted(same, key=len)[0]
                    break
        table[item] = pick if pick in files else None
    return table


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: match-bedrock-icons.py <java version> <bedrock-samples checkout>")
    version, samples = sys.argv[1], pathlib.Path(sys.argv[2])
    with tempfile.TemporaryDirectory() as work:
        work = pathlib.Path(work)
        with zipfile.ZipFile(client_jar(version, work)) as jar:
            for name in jar.namelist():
                if name.startswith(("assets/minecraft/items/", "assets/minecraft/models/", "assets/minecraft/textures/item/", "assets/minecraft/textures/block/")):
                    jar.extract(name, work)
        table = match(work / "assets/minecraft", samples / "resource_pack")
    print(f"# Generated by match-bedrock-icons.py from the {version} client and Mojang bedrock-samples. Do not edit by hand.")
    print("# <java item id> <bedrock texture path>; an id alone has no Bedrock picture.")
    for item in sorted(table):
        print(f"{item} {table[item]}" if table[item] else item)


if __name__ == "__main__":
    main()
