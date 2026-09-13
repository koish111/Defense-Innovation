"""Regenerates the third-person `*_blocking` variant displays for every guard
shield (2026-09-13 ruling).

Never hand-edit a `*_blocking.json` display table. This script applies the
vanilla idle->blocking transform delta — the same motion a vanilla shield
makes when it starts blocking — to each shield's own idle third-person
display, so the result is geometry-independent (no assumption about where a
shield's elements sit around the model origin). Running it against the
vanilla shield's own idle display reproduces `item/shield_blocking.json`
exactly (asserted as a self-check).

Usage:
    python tools/models/calc_shield_block_variants.py
"""

import json
import math
import os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODELS = os.path.join(REPO, "src", "main", "resources", "assets", "blockmod", "models", "item")

SHIELDS = [
    "wooden_buckler", "iron_buckler", "diamond_buckler", "netherite_buckler",
    "reinforced_iron_shield", "diamond_shield", "netherite_shield",
    "wooden_great_shield", "iron_great_shield", "diamond_great_shield",
    "netherite_great_shield",
]

# vanilla item/shield.json vs item/shield_blocking.json third-person displays
# (1.21.1). The vanilla shield model is builtin/entity, so these display pairs
# are the only vanilla-side inputs.
VAN_IDLE = {
    False: {"rot": [0, 90, 0], "trans": [10, 6, -4]},   # right hand
    True:  {"rot": [0, 90, 0], "trans": [10, 6, 12]},   # left hand
}
VAN_BLOCK = {
    False: {"rot": [45, 155, 0], "trans": [-3.49, 11, -2]},
    True:  {"rot": [45, 155, 0], "trans": [11.51, 7, 2.5]},
}

# Hand-calibration (2026-09-13): in the composed blocking pose the buckler
# plate rides ~0.85 display units too far across the hand toward the body
# centre compared with the great/medium shields, in BOTH hands (the visual
# error mirrors between them). Derived value: aligns the buckler's plate
# centre laterally with the great/medium plate-centre consensus (-2.1698
# rendered units in either hand). Positive c shifts BOTH JSON entries' x by
# +c: the right-hand render applies translation verbatim (visual: outward
# right), while ItemTransform mirrors left-hand translation x (visual:
# outward left) — i.e. the shield moves off the body centre in both hands.
# Bump or negate after in-game review.
BUCKLER_X_CALIBRATION = 0.8514

RAD = math.pi / 180.0


def r_xyz(rx, ry, rz):
    """Minecraft ItemTransform rotation: rotationXYZ == Rx * Ry * Rz (degrees)."""
    cx, sx = math.cos(rx * RAD), math.sin(rx * RAD)
    cy, sy = math.cos(ry * RAD), math.sin(ry * RAD)
    cz, sz = math.cos(rz * RAD), math.sin(rz * RAD)
    return [
        [cy * cz, -cy * sz, sy],
        [sx * sy * cz + cx * sz, cx * cz - sx * sy * sz, -sx * cy],
        [sx * sz - cx * sy * cz, cx * sy * sz + sx * cz, cx * cy],
    ]


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def inv(m):
    d = (m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
         - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
         + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]))
    cof = [[(m[1][1] * m[2][2] - m[1][2] * m[2][1]), -(m[1][0] * m[2][2] - m[1][2] * m[2][0]),
            (m[1][0] * m[2][1] - m[1][1] * m[2][0])],
           [-(m[0][1] * m[2][2] - m[0][2] * m[2][1]), (m[0][0] * m[2][2] - m[0][2] * m[2][0]),
            -(m[0][0] * m[2][1] - m[0][1] * m[2][0])],
           [(m[0][1] * m[1][2] - m[0][2] * m[1][1]), -(m[0][0] * m[1][2] - m[0][2] * m[1][0]),
            (m[0][0] * m[1][1] - m[0][1] * m[1][0])]]
    # adjugate = transpose of the cofactor matrix
    return [[cof[j][i] / d for j in range(3)] for i in range(3)]


def mv(m, v):
    return [m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] for i in range(3)]


def entry_to_rendered(rot, trans, left):
    """ItemTransform.apply left-hand mirror: T.x, R.y, R.z negate."""
    rx, ry, rz = rot
    tx, ty, tz = trans
    if left:
        ry, rz, tx = -ry, -rz, -tx
    return r_xyz(rx, ry, rz), [tx, ty, tz]  # r_xyz takes degrees


def wrap180(x):
    return (x + 180.0) % 360.0 - 180.0


def normalize_branch(rot):
    """Euler branch: (a,b,c) == (a±180, 180-b, c±180). Prefer c near 0."""
    a, b, c = rot
    if c > 90.0 or c < -90.0:
        s = 1.0 if c < 0 else -1.0
        a, b, c = wrap180(a + 180.0 * s), wrap180(180.0 - b), wrap180(c + 180.0 * s)
    return [a, b, c]


def decompose_xyz(m):
    """m == Rx(a)*Ry(b)*Rz(c) -> degrees, c folded near 0."""
    b = math.asin(max(-1.0, min(1.0, m[0][2])))
    if abs(math.cos(b)) > 1e-9:
        a = math.atan2(-m[1][2], m[2][2])
        c = math.atan2(-m[0][1], m[0][0])
    else:  # gimbal lock: z fold into y
        a = math.atan2(m[1][0], m[1][1])
        c = 0.0
    return normalize_branch([a / RAD, b / RAD, c / RAD])


def rendered_to_entry(rot_deg, trans, left):
    a, b, c = rot_deg
    tx, ty, tz = trans
    if left:
        b, c, tx = -b, -c, -tx
    return [round(a, 4), round(b, 4), round(c, 4)], [round(tx, 4), round(ty, 4), round(tz, 4)]


# --- sanity: the delta reproduces vanilla blocking from vanilla idle ---
for left in (False, True):
    ri, _ = entry_to_rendered(VAN_IDLE[left]["rot"], VAN_IDLE[left]["trans"], left)
    rb, tb = entry_to_rendered(VAN_BLOCK[left]["rot"], VAN_BLOCK[left]["trans"], left)
    delta = mul(rb, inv(ri))
    t_final = [tb[i] + mv(delta, [0, 0, 0])[i] for i in range(3)]  # idle trans == vanilla idle trans
    r_final = mul(delta, ri)
    e = rendered_to_entry(decompose_xyz(r_final), t_final, left)
    # real correctness test: the entry must rebuild the exact target matrix
    rr, _ = entry_to_rendered(e[0], e[1], left)
    assert all(abs(rr[i][j] - rb[i][j]) < 1e-9 for i in range(3) for j in range(3)), (e, rb)
    assert all(abs(e[1][k] - VAN_BLOCK[left]["trans"][k]) < 1e-6 for k in range(3)), e
print("sanity check passed: delta reproduces vanilla blocking")


def variant_display(idle_rh, idle_lh):
    display = {}
    for left, idle in ((False, idle_rh), (True, idle_lh)):
        ri, ti = entry_to_rendered(idle["rotation"], idle.get("translation", [0, 0, 0]), left)
        rb, tb = entry_to_rendered(VAN_BLOCK[left]["rot"], VAN_BLOCK[left]["trans"], left)
        vi, vt = entry_to_rendered(VAN_IDLE[left]["rot"], VAN_IDLE[left]["trans"], left)
        delta = mul(rb, inv(vi))
        t_final = [tb[i] + mv(delta, [ti[k] - vt[k] for k in range(3)])[i] for i in range(3)]
        r_final = mul(delta, ri)
        rot, trans = rendered_to_entry(decompose_xyz(r_final), t_final, left)
        hand = "thirdperson_lefthand" if left else "thirdperson_righthand"
        entry = {"rotation": rot, "translation": trans}
        if "scale" in idle:
            entry["scale"] = idle["scale"]
        display[hand] = entry
    return display


for name in SHIELDS:
    base_path = os.path.join(MODELS, name + ".json")
    with open(base_path, encoding="utf-8") as f:
        base = json.load(f)
    idle = base["display"]
    variant = {
        "parent": "blockmod:item/" + name,
        "display": variant_display(idle["thirdperson_righthand"], idle["thirdperson_lefthand"]),
    }
    if name.endswith("buckler") and BUCKLER_X_CALIBRATION:
        d = variant["display"]
        # Same JSON sign for both hands: the left-hand render mirrors
        # translation x, so +c reads as outward-right on the right hand and
        # outward-left on the left hand.
        d["thirdperson_righthand"]["translation"][0] = round(
            d["thirdperson_righthand"]["translation"][0] + BUCKLER_X_CALIBRATION, 4)
        d["thirdperson_lefthand"]["translation"][0] = round(
            d["thirdperson_lefthand"]["translation"][0] + BUCKLER_X_CALIBRATION, 4)
    out_path = os.path.join(MODELS, name + "_blocking.json")
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(variant, f, indent="\t")
        f.write("\n")
    d = variant["display"]
    print(name, "rh", d["thirdperson_righthand"], "lh", d["thirdperson_lefthand"])
