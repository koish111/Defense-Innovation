"""Regenerates the third-person `*_blocking` variant displays for the medium
and great guard shields (2026-09-13 ruling), plus the buckler guard-raise
variant ladder (2026-09-13 ruling, supersedes the same-day buckler revert):
`<buckler>_raise_<k>.json` samples the interpolation from each buckler's own
idle third-person display to its raised blocking display (vanilla delta +
arm-front X calibration), selected at runtime by the `blockmod:guard_raise`
item property while the guard-raise envelope runs. Every raise variant copies
the idle model's non-third-person displays verbatim, so first-person and GUI
rendering never change.

Never hand-edit a `*_blocking.json` or `*_raise_*.json` display table. The
blocking script applies the vanilla idle->blocking transform delta — the same
motion a vanilla shield makes when it starts blocking — to each shield's own
idle third-person display, so the result is geometry-independent (no
assumption about where a shield's elements sit around the model origin).
Running it against the vanilla shield's own idle display reproduces
`item/shield_blocking.json` exactly (asserted as a self-check).

Usage:
    python tools/models/calc_shield_block_variants.py
"""

import json
import math
import os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODELS = os.path.join(REPO, "src", "main", "resources", "assets", "blockmod", "models", "item")

SHIELDS = [
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
    out_path = os.path.join(MODELS, name + "_blocking.json")
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(variant, f, indent="\t")
        f.write("\n")
    d = variant["display"]
    print(name, "rh", d["thirdperson_righthand"], "lh", d["thirdperson_lefthand"])


# --- buckler guard-raise ladder (2026-09-13 ruling) --------------------------
# `blockmod:guard_raise` (BucklerGuardRaiseProperty) returns the eased envelope
# progress; the idle model's overrides select `<buckler>_raise_<k>.json` at the
# thresholds below (same stepped selection a drawn bow uses). The final step is
# the raised blocking display with the arm-front X calibration.

BUCKLERS = ["wooden_buckler", "iron_buckler", "diamond_buckler", "netherite_buckler"]
GUARD_RAISE_PREDICATE = "blockmod:guard_raise"
BUCKLER_RAISE_STEPS = 12
# Designer calibration (2026-09-13): shifts the raised shield face onto the
# front end of the arm box, applied in entry space to both hands (the left
# hand's mirror flips it in rendered space, moving each hand's shield outward).
BUCKLER_X_CALIBRATION = 0.8514


def raise_endpoints(idle_rh, idle_lh):
    """Per-hand rendered-space endpoints: the idle rotation matrix/translation
    and the raised blocking target (vanilla delta + X calibration baked in
    BEFORE sampling, so the ladder including the last step is continuous)."""
    endpoints = {}
    for left, idle in ((False, idle_rh), (True, idle_lh)):
        ri, ti = entry_to_rendered(idle["rotation"], idle.get("translation", [0, 0, 0]), left)
        rb, tb = entry_to_rendered(VAN_BLOCK[left]["rot"], VAN_BLOCK[left]["trans"], left)
        vi, vt = entry_to_rendered(VAN_IDLE[left]["rot"], VAN_IDLE[left]["trans"], left)
        delta = mul(rb, inv(vi))
        t_final = [tb[i] + mv(delta, [ti[k] - vt[k] for k in range(3)])[i] for i in range(3)]
        r_final = mul(delta, ri)
        entry_rot, entry_trans = rendered_to_entry(decompose_xyz(r_final), t_final, left)
        entry_trans[0] += BUCKLER_X_CALIBRATION
        r_cal, t_cal = entry_to_rendered(entry_rot, entry_trans, left)
        # the calibration is a pure entry-space translation shift: the rendered
        # rotation must be untouched and the translation shifted per mirror
        # (tolerances cover rendered_to_entry's 4-decimal rounding granularity)
        assert all(abs(r_cal[i][j] - r_final[i][j]) < 1e-4 for i in range(3) for j in range(3)), name
        expected_x = t_final[0] - BUCKLER_X_CALIBRATION if left else t_final[0] + BUCKLER_X_CALIBRATION
        assert abs(t_cal[0] - expected_x) < 1e-4, (name, left, t_cal, t_final)
        e_idle = decompose_xyz(ri)
        e_final = decompose_xyz(r_cal)
        # component lerp needs compatible euler branches (decompose folds c near 0)
        assert abs(e_idle[2]) <= 90.0 and abs(e_final[2]) <= 90.0, (name, e_idle, e_final)
        endpoints[left] = {"idle_r": ri, "idle_t": ti, "e_idle": e_idle,
                           "e_final": e_final, "final_r": r_cal, "final_t": t_cal}
    return endpoints


def raise_entry(endpoint, left, t, idle):
    rot = [endpoint["e_idle"][k] + (endpoint["e_final"][k] - endpoint["e_idle"][k]) * t for k in range(3)]
    trans = [endpoint["idle_t"][k] + (endpoint["final_t"][k] - endpoint["idle_t"][k]) * t for k in range(3)]
    entry_rot, entry_trans = rendered_to_entry(rot, trans, left)
    entry = {"rotation": entry_rot, "translation": entry_trans}
    if "scale" in idle:
        entry["scale"] = list(idle["scale"])
    return entry


for name in BUCKLERS:
    base_path = os.path.join(MODELS, name + ".json")
    with open(base_path, encoding="utf-8") as f:
        base = json.load(f)
    idle = base["display"]
    # drop stale raise variants from previous runs
    for existing in os.listdir(MODELS):
        if existing.startswith(name + "_raise_") and existing.endswith(".json"):
            os.remove(os.path.join(MODELS, existing))
    endpoints = raise_endpoints(idle["thirdperson_righthand"], idle["thirdperson_lefthand"])
    overrides = []
    for k in range(1, BUCKLER_RAISE_STEPS + 1):
        t = k / BUCKLER_RAISE_STEPS
        display = {key: idle[key] for key in idle
                   if key not in ("thirdperson_righthand", "thirdperson_lefthand")}
        for left, hand in ((False, "thirdperson_righthand"), (True, "thirdperson_lefthand")):
            display[hand] = raise_entry(endpoints[left], left, t, idle[hand])
        variant = {
            "parent": "blockmod:item/" + name,
            "display": display,
        }
        out_path = os.path.join(MODELS, f"{name}_raise_{k}.json")
        with open(out_path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(variant, f, indent="\t")
            f.write("\n")
        overrides.append({
            "predicate": {GUARD_RAISE_PREDICATE: round(t, 4)},
            "model": "blockmod:item/" + name + "_raise_" + str(k),
        })
    base["overrides"] = overrides
    with open(base_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(base, f, indent="\t")
        f.write("\n")
    # final-step correctness: k=STEPS must rebuild the calibrated blocking pose
    for left, hand in ((False, "thirdperson_righthand"), (True, "thirdperson_lefthand")):
        final_entry = raise_entry(endpoints[left], left, 1.0, idle[hand])
        rr, tr = entry_to_rendered(final_entry["rotation"], final_entry["translation"], left)
        assert all(abs(rr[i][j] - endpoints[left]["final_r"][i][j]) < 1e-4
                   for i in range(3) for j in range(3)), (name, hand, "rot")
        assert all(abs(tr[i] - endpoints[left]["final_t"][i]) < 1e-4
                   for i in range(3)), (name, hand, "trans")
    print(name, "raise ladder", BUCKLER_RAISE_STEPS, "steps + overrides written")
