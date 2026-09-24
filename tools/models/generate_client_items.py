"""Generate 26.1 client-item definitions from the existing shield model roster."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets"
SHIELDS = (
    "wooden_buckler", "iron_buckler", "diamond_buckler", "netherite_buckler",
    "reinforced_iron_shield", "diamond_shield", "netherite_shield",
    "wooden_great_shield", "iron_great_shield", "diamond_great_shield", "netherite_great_shield",
)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


for name in SHIELDS:
    buckler = name.endswith("_buckler")
    raised = name + ("_raise_12" if buckler else "_blocking")
    write(ASSETS / "blockmod/items" / (name + ".json"), {
        "model": {
            "type": "minecraft:condition",
            "property": "blockmod:guard_raise" if buckler else "blockmod:blocking",
            "on_true": {"type": "minecraft:model", "model": "blockmod:item/" + raised},
            "on_false": {"type": "minecraft:model", "model": "blockmod:item/" + name},
        }
    })

# Preserve vanilla shield special rendering (including banner components).
# 26.1 special shield meshes use this coordinate-system transformation.
write(ASSETS / "minecraft/items/shield.json", {
    "model": {
        "type": "minecraft:condition",
        "property": "blockmod:blocking",
        "on_true": {"type": "minecraft:special", "base": "minecraft:item/shield_blocking",
                    "model": {"type": "minecraft:shield"}},
        "on_false": {"type": "minecraft:special", "base": "minecraft:item/shield",
                     "model": {"type": "minecraft:shield"}},
        "transformation": {
            "translation": [0.0, 0.0, 0.0],
            "left_rotation": [0.0, 0.0, 0.0, 1.0],
            "scale": [1.0, -1.0, -1.0],
            "right_rotation": [0.0, 0.0, 0.0, 1.0],
        },
    }
})

# Model selection now lives in assets/<namespace>/items, not model overrides.
for path in (ASSETS / "blockmod/models/item").glob("*.json"):
    source = path.read_text(encoding="utf-8")
    data = json.loads(source)
    if "overrides" in data:
        del data["overrides"]
        # The legacy generator placed overrides last; preserve geometry formatting.
        updated = re.sub(r',\s*"overrides"\s*:\s*\[.*\]\s*(?=\}\s*$)', '\n', source, flags=re.S)
        if json.loads(updated) != data:
            raise ValueError(f"Unexpected legacy overrides layout: {path}")
        path.write_text(updated, encoding="utf-8")
