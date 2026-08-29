# AGENTS.md

> Instructions for AI coding agents working in this repository.
> **Read this file completely before making any change.**
> This file is the authoritative contract between the codebase and any agent operating on it.
> Domain rules here are derived from `docs/BlockParry-软件规格说明书-v2.0.md` (the Spec). When in doubt, the Spec wins.

---

## 1. Project

**Block & Parry** is a Minecraft Java mod for **NeoForge 1.21.1** that replaces vanilla's "hold shield, take no damage" model with a **stamina-management combat system**: blocking costs stamina, stamina depletion disables defense entirely, and well-timed parries cost nothing and counter-attack.

Design goal: force a rhythm of **engage → block → exhaust → retreat → recover → re-engage**, rewarding timing over turtling.

- **Mod ID:** `blockmod`
- **Base package:** `com.example.blockmod` *(replace with your own reversed domain before first commit)*
- **Registry namespace:** `blockmod:*`
- **Current phase:** MVP (see `docs/MVP开发计划-v1.0.md`)

### Key documents

| Document | Purpose |
|:---|:---|
| `docs/BlockParry-软件规格说明书-v2.0.md` | **The Spec.** Requirements (FR-01..FR-27), design, ADRs, formulas. Authoritative. |
| `docs/MVP开发计划-v1.0.md` | Task breakdown (T-01..T-44), estimates, acceptance checklist. |
| `docs/api-verification.md` | Results of NeoForge API probes. **Check this before touching the damage pipeline.** |
| `tools/balance/calc_guard.py` | Regenerates every balance table. **Never hand-edit a balance table.** |

---

## 2. Tech stack — pinned versions

| Item | Version | Note |
|:---|:---|:---|
| Java | **21** | Required by MC 1.21.1. Do not change the toolchain. |
| Minecraft | **1.21.1** | |
| NeoForge | **21.1.x** (latest stable) | Generated from the official NeoForge MDK. |
| Gradle | Wrapper bundled with MDK | Do **not** upgrade manually. |
| Test framework | JUnit 5 (pure functions only) | |

Use the NeoForge **MDK**, not a third-party template.

---

## 3. Repository layout

```
src/main/java/com/example/blockmod/
├── BlockMod.java              # main entry, config + bus registration
├── config/Config.java         # ModConfigSpec (server-side)
├── registry/                  # ModItems, ModEffects, ModSounds, ModTags,
│                              # ModDataComponents, ModAttachments, ModPayloads,
│                              # ModKeyMappings, ModCreativeTabs, ModCommands
├── data/                      # GuardProfile, ShieldType, DamageClass (records/enums, no logic)
├── state/                     # StaminaData, GuardStateData (attachments)
├── logic/                     # ALL gameplay decisions live here (server-side)
├── input/                     # client input capture + server input state
├── handler/                   # PlayerTickHandler, FoodStaminaHandler
├── network/                   # payload records
├── client/                    # HUD, FX, tooltips — CLIENT ONLY
└── compat/                    # CompatManager (post-MVP)

src/main/resources/
├── assets/blockmod/           # lang, models, textures, sounds
└── data/blockmod/             # damage_type, tags, recipes, item components

src/test/java/                 # unit tests for pure functions only
tools/balance/calc_guard.py    # balance table generator
docs/                          # spec, MVP plan, API verification
```

---

## 4. Commands

Run from the repository root.

```bash
./gradlew build                 # compile + package; must produce zero new warnings
./gradlew runClient             # client dev run
./gradlew runServer             # dedicated server dev run — REQUIRED for gameplay changes
./gradlew runGameTestServer     # game tests (post-MVP)
./gradlew genIntellijRuns       # regenerate IDE run configs
python tools/balance/calc_guard.py   # regenerate + verify balance tables
```

**Before every commit:** `./gradlew build` must pass.
**Before finishing any gameplay task:** verify on `runServer`, not only `runClient`.

---

## 5. Architecture rules

### 5.1 Layering — dependency direction is strict and one-way

```
client ─┐
input  ─┼──▶ network ──▶ logic ──▶ state ──▶ data
        │       │          │         │
        └───────┴──▶ config ◀────────┘
```

| Rule | Enforcement |
|:---|:---|
| `logic` **must never** import `client` | Gameplay decisions are server-side only |
| `client` **must never** mutate `state` | Client holds a read-only mirror updated via S2C payloads |
| `data` **must never** import anything from the mod | Records and enums only |
| `state` **must never** import `logic` | Data holders; behaviour lives in services |
| Only `client` may touch rendering classes | Guard with `@OnlyIn(Dist.CLIENT)` / `DistExecutor` |

### 5.2 Where code belongs

| Concern | Owner |
|:---|:---|
| Any decision that affects damage, stamina, or entity state | `logic/` |
| Reading input from the keyboard/mouse | `input/` |
| Sending or receiving packets | `network/` + `registry/ModPayloads` |
| Rendering, particles, HUD, tooltips, sounds | `client/` |
| Per-tick drivers | `handler/` |
| Numbers | `config/Config.java` — nowhere else |

### 5.3 Player state storage

Use the NeoForge **Attachment API** (`NeoForgeRegistries.ATTACHMENT_TYPES`), never the legacy Capability system.

| Attachment | Serialized | Contents |
|:---|:---|:---|
| `blockmod:stamina` | **Yes** | `stamina`, `lastEventTick` |
| `blockmod:guard_state` | No | `guarding`, `parryWindowEndTick`, `parryReadyTick`, `powerGuarding`, `bashWindupEndTick`, `bashReadyTick`, `activeMoveMalusUuid`, `wasDepleted` |

Item statistics (guard strength, shield type, parry window, move malus, power-guard bonus) live in the **`blockmod:guard_profile` data component** on the `ItemStack`, so datapacks and third-party items can override them without code.

---

## 6. Domain invariants — do not break these

These are the rules that define the mod. Violating any of them is a correctness bug, not a style issue.

### 6.1 Stamina

- Max `40.0` (configurable). **No lower clamp — stamina may go negative.**
- Regeneration, evaluated top-down, first match wins:

| # | Condition | Rate | Delay applies |
|:---|:---|---:|:---|
| 1 | Creative/spectator and exempt | refilled | — |
| 2 | `stamina <= 0` (depleted) | **8.0/s** | No |
| 3 | Power Guard active | 0 | — |
| 4 | Within `regen_delay` (2s) of last blocked/hit event | 0 | — |
| 5 | Guarding | `4.0 × 0.5` = **2.0/s** | Yes |
| 6 | Otherwise | **4.0/s** | Yes |

- `stamina == 0` counts as **depleted** (8/s), not normal. This keeps the rate monotonic in stamina (ADR-18).

### 6.2 Depletion (v2.0) — Guard Break has been REMOVED

Depletion is a **derived property** (`stamina <= 0`). It has exactly three consequences:

1. Blocking does not work — damage resolves normally.
2. Parrying does not work — even inside the parry window.
3. **Movement-speed penalty is removed**, even while the player still holds right-click.

Depletion must **never**:

- apply stun, slowness, or any control loss;
- force `guarding = false`;
- register or rely on a `MobEffect`;
- be clearable by milk, `/effect clear`, or any other means.

There is **no `broken` field**. Never reintroduce one. Any code needing the state must call `StaminaData#isDepleted()`.

### 6.3 Depletion edge detection

Side effects (remove/restore move malus, play the depletion cue) run **only on the tick where `isDepleted()` flips**, tracked via `GuardStateData.wasDepleted`. Never trigger them on every tick while depleted — that causes sound spam and attribute-modifier thrash.

`wasDepleted` must be re-initialized on `PlayerEvent.Clone` / login, otherwise a dimension change replays the cue.

### 6.4 Formulas

```java
// Stamina cost of one blocked hit
cost = pow(max(dmg, 0) * MFIX, PFIX) * (1 - gb)
// MFIX = 9.4   PFIX = 0.7 (PvE) | 0.9 (PvP)

// Effective guard strength — multiplicative, always < 1
effectiveGb = 1 - (1 - baseGb) * Π(1 - r_i)
```

- `gb` is clamped to `[0.01, 0.95]`. `gb = 1.0` would make cost zero (infinite blocking) and must never be reachable.
- `cost` is clamped to `[min_cost_per_guard = 0.5, max_stamina × 2.0]`.
- Non-finite results (`NaN`, `Infinity`) log an error and return `0` — never propagate.

### 6.5 Blocking

A hit is blocked only if **all** of these hold: target is a `ServerPlayer`, a `GuardProfile` exists, `guarding == true`, `stamina > 0`, the source is within the frontal 180°, and the damage class is not `IGNORED`.

- Frontal test: `toSource.dot(lookVector) < 0.0` (horizontal components only), matching vanilla shield behaviour. A `null` source position means **not blockable**.
- Damage classes: `MELEE`, `PROJECTILE` (both blockable **and** parryable), `EXPLOSION` (blockable, **not** parryable), `IGNORED` (magic, fall, fire, void, /kill).
- Durability: only shields, only when `floor(dmg) + 1` with `dmg >= 3`. Swords never lose durability. Parries never cost durability.
- Equipment priority: **offhand shield always wins** over a mainhand sword. Dual shields are unsupported.

### 6.6 Parrying

| Equipment | Window |
|:---|:---|
| Sword | 5 ticks |
| Buckler | 10 ticks |
| Medium / Great shield | cannot parry |

- A successful parry: cancels damage, costs **zero stamina**, costs **zero durability**, and counters.
- Counter rules: `MELEE` → 1s stun on the attacker. `PROJECTILE` → deflect the projectile, **do not stun the shooter** and **do not reflect it back**.
- **One parry per raise.** After a successful parry the window closes immediately; reopening requires releasing right-click and waiting `parry_cooldown_ticks` (10) to prevent spam.
- Bosses require `boss_parry_threshold` (3) successful parries before being stunned; the counter expires after 200 ticks.

### 6.7 Shield Bash (medium shields) and Power Guard (great shields)

| | Shield Bash | Power Guard |
|:---|:---|:---|
| Trigger | Left click while guarding | Hold the PG key (default **Left Alt**) while guarding |
| Cost | 8.0 damage, 4.0 blocks knockback | `max_stamina × 5%` = 2.0/s |
| Timing | 5-tick windup, 20-tick cooldown **counted from resolution** | Ends on key release, right-click release, or `stamina <= 0` |
| Extra | keeps guarding | disables jump, suspends regeneration |

Power Guard **requires `stamina > 0` to activate**. It is not available while depleted.

### 6.8 Stun

`blockmod:stun`, 20 ticks. Its **only** source is a successful parry — it is never a punishment for the defender.

Implementation: cancel movement by zeroing X/Z each tick, zero positive Y to block jumping, cancel `AttackEntityEvent` and item-use events, and call `stopUsingItem()`. **Do not use `setNoAi(true)`** (no effect on players) and **do not** implement it in `MobEffect#applyEffectTick` (movement resolves after effect ticks).

---

## 7. Coding conventions

### 7.1 Java

- Target Java 21. Prefer `var` for locals, records for immutable data, sealed types where sensible.
- **Nullability:** annotate parameters and returns; never return `null` from a lookup that has a sensible empty value — return `Optional` or a null-object instead.
- Services in `logic/` are stateless singletons taking explicit parameters. No static mutable state except clearly-scoped caches (e.g. `BossTracker`), and those need bounded size.
- Name booleans as predicates: `canDefend()`, `isDepleted()`, `parryWindowActive()`.
- Log through `BlockModLogger` with structured prefixes: `[BP] t=<tick> p=<player> <EVENT> key=value ...`

### 7.2 Performance (NFR-01/02)

- The per-tick path and the damage path must be allocation-free. Reuse `Vec3` scratch objects; never build strings, lists, or boxed values per tick.
- No I/O, no registry lookups, and no `Optional` chains inside the damage handler.
- Budget: < 0.05 ms per hit resolution, < 0.01 ms per player per tick.

### 7.3 Registration

Use `DeferredRegister` for **every** registry entry. Never call `BuiltInRegistries` directly, and never register outside the mod event bus.

---

## 8. Configuration rules

- **Every gameplay number lives in `config/blockmod-server.toml`.** Magic numbers in code are a build-blocking defect.
- Every numeric entry uses `defineInRange`; enums and booleans use whitelists. Invalid values log `ERROR` and fall back to the default — never crash, never use the invalid value.
- `ModConfigEvent.Reloading` must re-validate and push `ConfigSyncPayload` to all online players.
- Adding a config key requires updating: `Config.java`, the TOML sample in Spec §13.2, and `tools/balance/calc_guard.py` when it affects a formula.

### Balance tables

Never hand-edit a numeric table in the Spec or a README. Change the formula or the constant, then run:

```bash
python tools/balance/calc_guard.py
```

and paste the regenerated output. The Spec's tables are **outputs of the formula**, never inputs.

---

## 9. Networking and authority rules

- **The server is authoritative for everything that affects gameplay.** The client sends *intent*, never *results*.
- C2S payloads: `guard_input`, `shield_bash`, `power_guard`. Every one of them is rate-limited (20/s) and state-validated; illegal requests are dropped with a `WARN`.
- S2C payloads: `stamina_sync`, `parry_fx`, `guard_break_fx`, `config_sync`.
- Sync throttling: send when the change exceeds `sync_threshold` (0.25) or every 20 ticks. **Depletion transitions, parries, and guard enter/exit always send immediately.**
- The client interpolates `displayStamina` toward `targetStamina` for HUD smoothness. The client must never predict or write stamina.
- Never trust a client-reported damage value, stamina value, or judgement result.

---

## 10. Testing rules

- **Unit tests cover pure functions only:** `GuardFormulas`, `EffectiveStrengthResolver`, `DamageClassifier`, `BossTracker`, config validation, and — added in v2.0 — regeneration-branch selection, depletion edge detection, and move-malus mount/remove decisions. Target ≥ 90% line coverage on those classes.
- Use table-driven tests matching the cases enumerated in Spec §9.2.
- Manual verification uses the 30-item checklist in Spec §9.4 / MVP plan §8. **Run all 30 before declaring the MVP done.**
- Every bug fix gets a regression test when the logic is a pure function.

---

## 11. Error handling

- Wrap the entire damage-interception handler in `try/catch`. On any exception: log `ERROR` with the stack trace and **let the damage through**. A mod bug must never make a player invulnerable or unkillable.
- Every edge case listed in Spec §8 (E-01..E-31) must have a corresponding branch. When you find a new one, add it to that list.
- Never swallow an exception silently.

### Specifically tricky cases already known

- **Food → stamina:** capture `foodLevel` in `LivingEntityUseItemEvent.Start` and evaluate it in `.Finish`. Reading `foodLevel` in `Finish` is wrong — the food's nutrition has already been applied.
- **Vanilla shield double-mitigation:** also handle `LivingShieldBlockEvent` and call `setBlocked(false)` + `setShieldDamage(0)`, or the vanilla path will reduce damage and consume durability a second time.
- **Re-entrancy:** guard the damage handler with a per-player re-entrancy set so a cancelled event cannot re-enter the same judgement.

---

## 12. Git conventions

- **Commit messages:** Conventional Commits, prefixed with the task ID.
  `T-15: implement StaminaService three-branch regen`
  `T-24: add GuardResolver with vanilla shield double-guard`
- **Branches:** `feat/T-xx-short-description`. `main` only receives working code.
- **Tags** at each milestone: `v0.1-m0`, `v0.1-m2`, …
- **Never commit:** `build/`, `run/`, `*.log`, `.idea/`, `*.iml`, local config overrides.
- One logical change per commit. Do not mix refactors with behaviour changes.

---

## 13. Do NOT

1. **Do not use the legacy Capability API.** NeoForge 1.21 uses Attachments.
2. **Do not add Mixins during MVP.** The design is event-only. If an event genuinely cannot cover a case, record it in `docs/api-verification.md` and raise it before writing a Mixin.
3. **Do not put gameplay logic on the client.** The client renders and sends intent; nothing more.
4. **Do not trust client payloads.** Validate state and rate-limit every C2S message.
5. **Do not hardcode numbers.** Config or `GuardProfile`, never a literal.
6. **Do not reintroduce a `broken` field, a Guard Break state, or a depletion stun.** They were removed deliberately in v2.0.
7. **Do not rely on a `MobEffect` for depletion.** It is derived from stamina precisely so it cannot be cleared.
8. **Do not apply a movement-speed penalty while depleted.** Removing it is what closes the "exhaust → retreat" loop (ADR-15).
9. **Do not use `setNoAi(true)` to implement stun.**
10. **Do not allocate in per-tick or damage-handler paths.**
11. **Do not run depletion side effects every tick.** Edge detection only.
12. **Do not hand-edit balance tables.** Regenerate them.
13. **Do not skip `runServer`.** Client-only testing hides authority and sync bugs.
14. **Do not add a feature that is not in the Spec.** Open a Spec item first.
15. **Do not mix Chinese and English in identifiers or comments.** Identifiers, log prefixes, and comments are English; user-facing strings go through `I18n` (`zh_cn` first, `en_us` post-MVP).

---

## 14. Definition of Done

A task is done only when **all** of the following hold:

- [ ] `./gradlew build` passes with no new warnings
- [ ] Verified on **both** `runClient` and `runServer`
- [ ] Every acceptance criterion for the task was actually executed, not assumed
- [ ] New numbers are configurable; no magic numbers
- [ ] Edge cases from Spec §8 are handled
- [ ] Unit tests added where the logic is a pure function
- [ ] No per-tick allocations introduced
- [ ] Commit references its task ID

---

## 15. Getting started checklist for a new agent

1. Read this file.
2. Read `docs/BlockParry-软件规格说明书-v2.0.md` §5 (detailed design) and §7 (open questions).
3. Check `docs/MVP开发计划-v1.0.md` to find the current task ID.
4. **If your change touches the damage pipeline, read `docs/api-verification.md` first.** Several NeoForge API assumptions there are load-bearing.
5. Confirm which open items (`O-xx`) affect your task. Do not silently pick a side — raise it.

---

*Keep this file in sync with the Spec. If a rule here and the Spec disagree, update the Spec first, then this file.*
