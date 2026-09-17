# Minecraft 26.2 - Spawn eggs, potions, arrows, armor trims, mineral blocks, enchanted books, RootMC (least -> most powerful)

**Source:** Bukkit/Spigot API **26.2** -- spawn eggs, potions, arrow types (plain / spectral / tipped), armor trims, mineral blocks, enchanted books, RootMC Free Lotto Ticket.
**Count:** 83 spawn eggs + 44 potions + **46 arrow stacks (x16 each)** + **1 Ender Pearl stack (x16)** + **1 Coal stack (x16)** + **1 Coal Block stack (x16)** + **1 Glowstone stack (x8)** + **1 Iron Ingot stack (x16)** + **1 Diamond stack (x16)** + **Netherite scrap** + **Netherite ingot** + **Nether Star** + 18 armor trims + 3 mineral blocks + 43 enchanted books + 1 RootMC item = **247** entries.
**Arrows:** every entry is a stack of **16**. Tipped arrows use `TIPPED_ARROW` + potion contents (remaining drinkable `PotionType`s after dropping Water/Mundane).

### Ranking criteria (subjective)

- **Mobs:** survival PvE combat / ally impact. Excluded spawn eggs that destroy terrain (Wither, Ender Dragon, Ghast, Ravager, Silverfish); **Creeper** and **Enderman** kept as exceptions.
- **Potions:** drinkable value for the user.
- **Arrows:** ammo usefulness (effects apply to the entity hit; qty always 16).
- **Ender pearls:** stack of 16; teleport mobility / escape.
- **Armor trims:** obtain difficulty / prestige (cosmetic).
- **Mineral blocks / enchanted books:** progression / prestige value.
- **RootMC Free Lotto Ticket:** grants a Root-Gamble lotto free-play credit (see section below) -- face value ~25 G, lottery EV still low.

Not official Mojang balance.

---

## Ranked list (1 = least powerful -> 247 = most powerful)

| Rank | Kind | Qty | Bukkit id | Namespaced / stored | Entry | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| 1 | Enchanted book | 1 | **`Enchantment.BINDING_CURSE`** | **`minecraft:enchanted_book` + `minecraft:binding_curse`** | **Enchanted book (Binding Curse)** | Curse -- armor stuck on; actively harmful |
| 2 | Enchanted book | 1 | **`Enchantment.VANISHING_CURSE`** | **`minecraft:enchanted_book` + `minecraft:vanishing_curse`** | **Enchanted book (Vanishing Curse)** | Curse -- item deleted on death |
| 3 | Potion | 1 | **`PotionType.THICK`** | **`minecraft:potion` + `minecraft:thick`** | **Potion (Thick)** | Brewing dead-end; no effect |
| 4 | Potion | 1 | **`PotionType.AWKWARD`** | **`minecraft:potion` + `minecraft:awkward`** | **Potion (Awkward)** | Brewing base only; no combat effect |
| 5 | Spawn egg | 1 | `TADPOLE_SPAWN_EGG` | `minecraft:tadpole_spawn_egg` | Tadpole | Tiny baby; no combat |
| 6 | Spawn egg | 1 | `BAT_SPAWN_EGG` | `minecraft:bat_spawn_egg` | Bat | Ambient; no damage |
| 7 | Spawn egg | 1 | `COD_SPAWN_EGG` | `minecraft:cod_spawn_egg` | Cod | Passive fish |
| 8 | Spawn egg | 1 | `SALMON_SPAWN_EGG` | `minecraft:salmon_spawn_egg` | Salmon | Passive fish |
| 9 | Spawn egg | 1 | `TROPICAL_FISH_SPAWN_EGG` | `minecraft:tropical_fish_spawn_egg` | Tropical fish | Passive fish |
| 10 | Spawn egg | 1 | `GLOW_SQUID_SPAWN_EGG` | `minecraft:glow_squid_spawn_egg` | Glow squid | Passive; ink only |
| 11 | Spawn egg | 1 | `SQUID_SPAWN_EGG` | `minecraft:squid_spawn_egg` | Squid | Passive; ink only |
| 12 | Potion | 1 | **`PotionType.STRONG_POISON`** | **`minecraft:potion` + `minecraft:strong_poison`** | **Potion (Poison II)** | Harms drinker hard -- PvP/splash weapon, not self-buff |
| 13 | Potion | 1 | **`PotionType.POISON`** | **`minecraft:potion` + `minecraft:poison`** | **Potion (Poison)** | DoT on drinker / splash target |
| 14 | Potion | 1 | **`PotionType.LONG_POISON`** | **`minecraft:potion` + `minecraft:long_poison`** | **Potion (Poison (long))** | Longer poison DoT |
| 15 | Potion | 1 | **`PotionType.STRONG_HARMING`** | **`minecraft:potion` + `minecraft:strong_harming`** | **Potion (Harming II)** | Instant damage II -- offensive splash |
| 16 | Potion | 1 | **`PotionType.HARMING`** | **`minecraft:potion` + `minecraft:harming`** | **Potion (Harming)** | Instant damage -- offensive splash |
| 17 | Potion | 1 | **`PotionType.WEAKNESS`** | **`minecraft:potion` + `minecraft:weakness`** | **Potion (Weakness)** | Lowers melee damage |
| 18 | Potion | 1 | **`PotionType.LONG_WEAKNESS`** | **`minecraft:potion` + `minecraft:long_weakness`** | **Potion (Weakness (long))** | Longer weakness |
| 19 | Potion | 1 | **`PotionType.STRONG_SLOWNESS`** | **`minecraft:potion` + `minecraft:strong_slowness`** | **Potion (Slowness IV)** | Heavy slow -- trap / kite |
| 20 | Potion | 1 | **`PotionType.SLOWNESS`** | **`minecraft:potion` + `minecraft:slowness`** | **Potion (Slowness)** | Movement slow |
| 21 | Potion | 1 | **`PotionType.LONG_SLOWNESS`** | **`minecraft:potion` + `minecraft:long_slowness`** | **Potion (Slowness (long))** | Longer slow |
| 22 | Potion | 1 | **`PotionType.INFESTED`** | **`minecraft:potion` + `minecraft:infested`** | **Potion (Infested)** | Trial brew -- spawns silverfish on hit (situational) |
| 23 | Potion | 1 | **`PotionType.OOZING`** | **`minecraft:potion` + `minecraft:oozing`** | **Potion (Oozing)** | Trial brew -- spawns slimes on death (situational) |
| 24 | Potion | 1 | **`PotionType.WEAVING`** | **`minecraft:potion` + `minecraft:weaving`** | **Potion (Weaving)** | Trial brew -- cobwebs on death (situational) |
| 25 | Potion | 1 | **`PotionType.WIND_CHARGED`** | **`minecraft:potion` + `minecraft:wind_charged`** | **Potion (Wind Charged)** | Trial brew -- burst on death (situational) |
| 26 | Enchanted book | 1 | **`Enchantment.LUCK_OF_THE_SEA`** | **`minecraft:enchanted_book` + `minecraft:luck_of_the_sea`** | **Enchanted book (Luck of the Sea)** | Fishing -- less junk; niche |
| 27 | Enchanted book | 1 | **`Enchantment.LURE`** | **`minecraft:enchanted_book` + `minecraft:lure`** | **Enchanted book (Lure)** | Fishing -- faster bites; niche |
| 28 | Potion | 1 | **`PotionType.LUCK`** | **`minecraft:potion` + `minecraft:luck`** | **Potion (Luck)** | Fishing luck -- niche, hard to brew |
| 29 | RootMC | 1 | **`Free Lotto Ticket`** | **`rootmc:free_lotto_ticket`** (Root-Gamble free-play credit) | **Free Lotto Ticket** | RootMC -- redeem = +1 lotto free-play credit (same as first unused free); then `/lotto`; face ~25 G; EV low |
| 30 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:thick`** | **Tipped Arrow (Thick) x16** | No effect; qty 16 |
| 31 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:awkward`** | **Tipped Arrow (Awkward) x16** | No effect; qty 16 |
| 32 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:healing`** | **Tipped Arrow (Healing) x16** | Heals hit entity (hurts undead); qty 16 |
| 33 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:strong_healing`** | **Tipped Arrow (Healing II) x16** | Strong heal on hit; qty 16 |
| 34 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:regeneration`** | **Tipped Arrow (Regeneration) x16** | Regen on hit target; qty 16 |
| 35 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_regeneration`** | **Tipped Arrow (Regeneration long) x16** | Long regen on hit; qty 16 |
| 36 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:strong_regeneration`** | **Tipped Arrow (Regeneration II) x16** | Regen II on hit; qty 16 |
| 37 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:strength`** | **Tipped Arrow (Strength) x16** | Buffs hit target melee; qty 16 |
| 38 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_strength`** | **Tipped Arrow (Strength long) x16** | Long strength on hit; qty 16 |
| 39 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:strong_strength`** | **Tipped Arrow (Strength II) x16** | Strength II on hit; qty 16 |
| 40 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:swiftness`** | **Tipped Arrow (Swiftness) x16** | Speeds hit target; qty 16 |
| 41 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_swiftness`** | **Tipped Arrow (Swiftness long) x16** | Long speed on hit; qty 16 |
| 42 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:strong_swiftness`** | **Tipped Arrow (Swiftness II) x16** | Speed II on hit; qty 16 |
| 43 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:leaping`** | **Tipped Arrow (Leaping) x16** | Jump boost on hit; qty 16 |
| 44 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_leaping`** | **Tipped Arrow (Leaping long) x16** | Long leap on hit; qty 16 |
| 45 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:strong_leaping`** | **Tipped Arrow (Leaping II) x16** | Leap II on hit; qty 16 |
| 46 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:fire_resistance`** | **Tipped Arrow (Fire Resistance) x16** | Fire res on hit; qty 16 |
| 47 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_fire_resistance`** | **Tipped Arrow (Fire Resistance long) x16** | Long fire res on hit; qty 16 |
| 48 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:night_vision`** | **Tipped Arrow (Night Vision) x16** | NV on hit; qty 16 |
| 49 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_night_vision`** | **Tipped Arrow (Night Vision long) x16** | Long NV on hit; qty 16 |
| 50 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:water_breathing`** | **Tipped Arrow (Water Breathing) x16** | Water breathing on hit; qty 16 |
| 51 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_water_breathing`** | **Tipped Arrow (Water Breathing long) x16** | Long WB on hit; qty 16 |
| 52 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:invisibility`** | **Tipped Arrow (Invisibility) x16** | Invis on hit; qty 16 |
| 53 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_invisibility`** | **Tipped Arrow (Invisibility long) x16** | Long invis on hit; qty 16 |
| 54 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:slow_falling`** | **Tipped Arrow (Slow Falling) x16** | Slow falling on hit; qty 16 |
| 55 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_slow_falling`** | **Tipped Arrow (Slow Falling long) x16** | Long slow fall on hit; qty 16 |
| 56 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:luck`** | **Tipped Arrow (Luck) x16** | Luck on hit; niche; qty 16 |
| 57 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:turtle_master`** | **Tipped Arrow (Turtle Master) x16** | Res+slow on hit; qty 16 |
| 58 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_turtle_master`** | **Tipped Arrow (Turtle Master long) x16** | Long turtle on hit; qty 16 |
| 59 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:strong_turtle_master`** | **Tipped Arrow (Turtle Master II) x16** | Strong turtle on hit; qty 16 |
| 60 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:infested`** | **Tipped Arrow (Infested) x16** | Trial -- silverfish on hit; qty 16 |
| 61 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:oozing`** | **Tipped Arrow (Oozing) x16** | Trial -- slime on death; qty 16 |
| 62 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:weaving`** | **Tipped Arrow (Weaving) x16** | Trial -- cobwebs on death; qty 16 |
| 63 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:wind_charged`** | **Tipped Arrow (Wind Charged) x16** | Trial -- wind burst on death; qty 16 |
| 64 | Arrow | **16** | **`ARROW`** | **`minecraft:arrow`** | **Arrow x16** | Plain arrow stack; qty 16 |
| 65 | Arrow | **16** | **`SPECTRAL_ARROW`** | **`minecraft:spectral_arrow`** | **Spectral Arrow x16** | Glowing outline on hit; qty 16 |
| 66 | Item | **16** | **`ENDER_PEARL`** | **`minecraft:ender_pearl`** | **Ender Pearl x16** | Throwable teleport; combat/escape mobility; qty 16 |
| 67 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:weakness`** | **Tipped Arrow (Weakness) x16** | Weakness on hit; qty 16 |
| 68 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_weakness`** | **Tipped Arrow (Weakness long) x16** | Long weakness on hit; qty 16 |
| 69 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:slowness`** | **Tipped Arrow (Slowness) x16** | Slowness on hit; qty 16 |
| 70 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_slowness`** | **Tipped Arrow (Slowness long) x16** | Long slowness on hit; qty 16 |
| 71 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:strong_slowness`** | **Tipped Arrow (Slowness IV) x16** | Heavy slowness on hit; qty 16 |
| 72 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:poison`** | **Tipped Arrow (Poison) x16** | Poison DoT on hit; qty 16 |
| 73 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:long_poison`** | **Tipped Arrow (Poison long) x16** | Long poison on hit; qty 16 |
| 74 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:strong_poison`** | **Tipped Arrow (Poison II) x16** | Poison II on hit; qty 16 |
| 75 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:harming`** | **Tipped Arrow (Harming) x16** | Instant damage on hit; qty 16 |
| 76 | Arrow | **16** | **`TIPPED_ARROW`** | **`minecraft:tipped_arrow` + `minecraft:strong_harming`** | **Tipped Arrow (Harming II) x16** | Instant damage II -- strongest tipped; qty 16 |
| 77 | Spawn egg | 1 | `CHICKEN_SPAWN_EGG` | `minecraft:chicken_spawn_egg` | Chicken | Passive livestock |
| 78 | Spawn egg | 1 | `RABBIT_SPAWN_EGG` | `minecraft:rabbit_spawn_egg` | Rabbit | Passive (killer bunny rare/command) |
| 79 | Spawn egg | 1 | `PIG_SPAWN_EGG` | `minecraft:pig_spawn_egg` | Pig | Passive livestock |
| 80 | Spawn egg | 1 | `SHEEP_SPAWN_EGG` | `minecraft:sheep_spawn_egg` | Sheep | Passive livestock |
| 81 | Spawn egg | 1 | `COW_SPAWN_EGG` | `minecraft:cow_spawn_egg` | Cow | Passive livestock |
| 82 | Spawn egg | 1 | `MOOSHROOM_SPAWN_EGG` | `minecraft:mooshroom_spawn_egg` | Mooshroom | Passive; stew utility |
| 83 | Spawn egg | 1 | `ARMADILLO_SPAWN_EGG` | `minecraft:armadillo_spawn_egg` | Armadillo | Passive; scute / roll defense |
| 84 | Spawn egg | 1 | `SNIFFER_SPAWN_EGG` | `minecraft:sniffer_spawn_egg` | Sniffer | Passive digger; seeds |
| 85 | Spawn egg | 1 | `TURTLE_SPAWN_EGG` | `minecraft:turtle_spawn_egg` | Turtle | Passive; shell scraps |
| 86 | Spawn egg | 1 | `FROG_SPAWN_EGG` | `minecraft:frog_spawn_egg` | Frog | Passive; eats small slimes/magma |
| 87 | Enchanted book | 1 | **`Enchantment.BANE_OF_ARTHROPODS`** | **`minecraft:enchanted_book` + `minecraft:bane_of_arthropods`** | **Enchanted book (Bane of Arthropods)** | Melee -- spiders/arthropods only |
| 88 | Spawn egg | 1 | `CAT_SPAWN_EGG` | `minecraft:cat_spawn_egg` | Cat | Tameable; scare creepers/phantoms |
| 89 | Spawn egg | 1 | `OCELOT_SPAWN_EGG` | `minecraft:ocelot_spawn_egg` | Ocelot | Passive; creep scare niche |
| 90 | Spawn egg | 1 | `PARROT_SPAWN_EGG` | `minecraft:parrot_spawn_egg` | Parrot | Passive; mob mimic |
| 91 | Spawn egg | 1 | `FOX_SPAWN_EGG` | `minecraft:fox_spawn_egg` | Fox | Passive; trust mechanic |
| 92 | Spawn egg | 1 | `ALLAY_SPAWN_EGG` | `minecraft:allay_spawn_egg` | Allay | Item courier; no combat |
| 93 | Spawn egg | 1 | `SULFUR_CUBE_SPAWN_EGG` | `minecraft:sulfur_cube_spawn_egg` | Sulfur cube | Passive cube (Chaos Cubed); block absorb utility |
| 94 | Spawn egg | 1 | `SNOW_GOLEM_SPAWN_EGG` | `minecraft:snow_golem_spawn_egg` | Snow golem | Weak ally; melts / low DPS |
| 95 | Spawn egg | 1 | `COPPER_GOLEM_SPAWN_EGG` | `minecraft:copper_golem_spawn_egg` | Copper golem | Utility golem; not a combat tank |
| 96 | Armor trim | 1 | **`SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:sentry_armor_trim_smithing_template`** | **Sentry Armor Trim** | Pillager outpost -- commonish structure trim |
| 97 | Armor trim | 1 | **`DUNE_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:dune_armor_trim_smithing_template`** | **Dune Armor Trim** | Desert pyramid -- commonish structure trim |
| 98 | Armor trim | 1 | **`COAST_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:coast_armor_trim_smithing_template`** | **Coast Armor Trim** | Ocean ruins -- commonish structure trim |
| 99 | Armor trim | 1 | **`WILD_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:wild_armor_trim_smithing_template`** | **Wild Armor Trim** | Jungle temple -- commonish structure trim |
| 100 | Armor trim | 1 | **`WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:wayfinder_armor_trim_smithing_template`** | **Wayfinder Armor Trim** | Trail ruins -- archaeology trim |
| 101 | Armor trim | 1 | **`RAISER_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:raiser_armor_trim_smithing_template`** | **Raiser Armor Trim** | Trail ruins -- archaeology trim |
| 102 | Armor trim | 1 | **`SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:shaper_armor_trim_smithing_template`** | **Shaper Armor Trim** | Trail ruins -- archaeology trim |
| 103 | Armor trim | 1 | **`HOST_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:host_armor_trim_smithing_template`** | **Host Armor Trim** | Trail ruins -- archaeology trim |
| 104 | Armor trim | 1 | **`SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:snout_armor_trim_smithing_template`** | **Snout Armor Trim** | Bastion remnant -- Nether trim |
| 105 | Armor trim | 1 | **`RIB_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:rib_armor_trim_smithing_template`** | **Rib Armor Trim** | Nether fortress -- Nether trim |
| 106 | Armor trim | 1 | **`TIDE_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:tide_armor_trim_smithing_template`** | **Tide Armor Trim** | Ocean monument -- elder guardian path |
| 107 | Armor trim | 1 | **`VEX_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:vex_armor_trim_smithing_template`** | **Vex Armor Trim** | Woodland mansion -- rare structure |
| 108 | Armor trim | 1 | **`EYE_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:eye_armor_trim_smithing_template`** | **Eye Armor Trim** | Stronghold -- End portal path |
| 109 | Armor trim | 1 | **`BOLT_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:bolt_armor_trim_smithing_template`** | **Bolt Armor Trim** | Trial chambers -- vault trim |
| 110 | Armor trim | 1 | **`FLOW_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:flow_armor_trim_smithing_template`** | **Flow Armor Trim** | Trial chambers -- ominous vault trim |
| 111 | Armor trim | 1 | **`WARD_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:ward_armor_trim_smithing_template`** | **Ward Armor Trim** | Ancient city -- Deep Dark trim |
| 112 | Armor trim | 1 | **`SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:spire_armor_trim_smithing_template`** | **Spire Armor Trim** | End city -- outer End trim |
| 113 | Armor trim | 1 | **`SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE`** | **`minecraft:silence_armor_trim_smithing_template`** | **Silence Armor Trim** | Ancient city -- rarest Deep Dark trim |
| 114 | Enchanted book | 1 | **`Enchantment.PUNCH`** | **`minecraft:enchanted_book` + `minecraft:punch`** | **Enchanted book (Punch)** | Bow knockback; utility CC |
| 115 | Enchanted book | 1 | **`Enchantment.KNOCKBACK`** | **`minecraft:enchanted_book` + `minecraft:knockback`** | **Enchanted book (Knockback)** | Melee knockback; often mixed value |
| 116 | Enchanted book | 1 | **`Enchantment.AQUA_AFFINITY`** | **`minecraft:enchanted_book` + `minecraft:aqua_affinity`** | **Enchanted book (Aqua Affinity)** | Helmet -- normal mine speed underwater |
| 117 | Potion | 1 | **`PotionType.NIGHT_VISION`** | **`minecraft:potion` + `minecraft:night_vision`** | **Potion (Night Vision)** | See in dark -- explore/caving |
| 118 | Potion | 1 | **`PotionType.LONG_NIGHT_VISION`** | **`minecraft:potion` + `minecraft:long_night_vision`** | **Potion (Night Vision (long))** | Longer night vision |
| 119 | Potion | 1 | **`PotionType.LEAPING`** | **`minecraft:potion` + `minecraft:leaping`** | **Potion (Leaping)** | Jump boost |
| 120 | Potion | 1 | **`PotionType.LONG_LEAPING`** | **`minecraft:potion` + `minecraft:long_leaping`** | **Potion (Leaping (long))** | Longer jump boost |
| 121 | Potion | 1 | **`PotionType.STRONG_LEAPING`** | **`minecraft:potion` + `minecraft:strong_leaping`** | **Potion (Leaping II)** | Jump boost II |
| 122 | Potion | 1 | **`PotionType.WATER_BREATHING`** | **`minecraft:potion` + `minecraft:water_breathing`** | **Potion (Water Breathing)** | Breathe underwater |
| 123 | Potion | 1 | **`PotionType.LONG_WATER_BREATHING`** | **`minecraft:potion` + `minecraft:long_water_breathing`** | **Potion (Water Breathing (long))** | Longer water breathing |
| 124 | Potion | 1 | **`PotionType.SLOW_FALLING`** | **`minecraft:potion` + `minecraft:slow_falling`** | **Potion (Slow Falling)** | Negate fall damage / elytra helper |
| 125 | Potion | 1 | **`PotionType.LONG_SLOW_FALLING`** | **`minecraft:potion` + `minecraft:long_slow_falling`** | **Potion (Slow Falling (long))** | Longer slow falling |
| 126 | Item | **16** | **`COAL`** | **`minecraft:coal`** | **Coal x16** | Basic fuel / torches; qty 16 -- below coal block stack |
| 127 | Block | **16** | **`COAL_BLOCK`** | **`minecraft:coal_block`** | **Coal Block x16** | Fuel density -- 16x9 smelts; qty 16; below iron |
| 128 | Block | **8** | **`GLOWSTONE`** | **`minecraft:glowstone`** | **Glowstone x8** | Nether light / brewing dust source; qty 8 -- above coal blocks, below iron |
| 129 | Item | **16** | **`IRON_INGOT`** | **`minecraft:iron_ingot`** | **Iron Ingot x16** | Mid gear / golem / anvil; qty 16 -- just below iron block |
| 130 | Block | 1 | **`IRON_BLOCK`** | **`minecraft:iron_block`** | **Iron block** | Mineral -- 9x iron; golem/beacon/mid gear |
| 131 | Spawn egg | 1 | `DONKEY_SPAWN_EGG` | `minecraft:donkey_spawn_egg` | Donkey | Mount + chest |
| 132 | Spawn egg | 1 | `MULE_SPAWN_EGG` | `minecraft:mule_spawn_egg` | Mule | Mount + chest |
| 133 | Spawn egg | 1 | `HORSE_SPAWN_EGG` | `minecraft:horse_spawn_egg` | Horse | Fast overworld mount |
| 134 | Spawn egg | 1 | `CAMEL_SPAWN_EGG` | `minecraft:camel_spawn_egg` | Camel | Two-rider desert mount |
| 135 | Spawn egg | 1 | `STRIDER_SPAWN_EGG` | `minecraft:strider_spawn_egg` | Strider | Nether lava mount |
| 136 | Spawn egg | 1 | `NAUTILUS_SPAWN_EGG` | `minecraft:nautilus_spawn_egg` | Nautilus | Underwater mount (MoM); air while ridden |
| 137 | Spawn egg | 1 | `SKELETON_HORSE_SPAWN_EGG` | `minecraft:skeleton_horse_spawn_egg` | Skeleton horse | Fast undead mount |
| 138 | Spawn egg | 1 | `ZOMBIE_HORSE_SPAWN_EGG` | `minecraft:zombie_horse_spawn_egg` | Zombie horse | Undead mount; natural in MoM |
| 139 | Spawn egg | 1 | `HAPPY_GHAST_SPAWN_EGG` | `minecraft:happy_ghast_spawn_egg` | Happy ghast | Rideable/utility ghast |
| 140 | Enchanted book | 1 | **`Enchantment.FROST_WALKER`** | **`minecraft:enchanted_book` + `minecraft:frost_walker`** | **Enchanted book (Frost Walker)** | Treasure boots -- walk on water as frost |
| 141 | Enchanted book | 1 | **`Enchantment.SOUL_SPEED`** | **`minecraft:enchanted_book` + `minecraft:soul_speed`** | **Enchanted book (Soul Speed)** | Treasure boots -- speed on soul sand/soil |
| 142 | Enchanted book | 1 | **`Enchantment.FLAME`** | **`minecraft:enchanted_book` + `minecraft:flame`** | **Enchanted book (Flame)** | Bow -- flaming arrows |
| 143 | Enchanted book | 1 | **`Enchantment.FIRE_ASPECT`** | **`minecraft:enchanted_book` + `minecraft:fire_aspect`** | **Enchanted book (Fire Aspect)** | Melee -- set targets on fire |
| 144 | Spawn egg | 1 | `LLAMA_SPAWN_EGG` | `minecraft:llama_spawn_egg` | Llama | Spits if provoked; caravan |
| 145 | Spawn egg | 1 | `TRADER_LLAMA_SPAWN_EGG` | `minecraft:trader_llama_spawn_egg` | Trader llama | Same spit; trader escort |
| 146 | Spawn egg | 1 | `WANDERING_TRADER_SPAWN_EGG` | `minecraft:wandering_trader_spawn_egg` | Wandering trader | Trades; flees |
| 147 | Spawn egg | 1 | `VILLAGER_SPAWN_EGG` | `minecraft:villager_spawn_egg` | Villager | Economy power, zero combat |
| 148 | Block | 1 | **`EMERALD_BLOCK`** | **`minecraft:emerald_block`** | **Emerald block** | Mineral -- trading density + beacon |
| 149 | Enchanted book | 1 | **`Enchantment.RESPIRATION`** | **`minecraft:enchanted_book` + `minecraft:respiration`** | **Enchanted book (Respiration)** | Helmet -- longer underwater air |
| 150 | Enchanted book | 1 | **`Enchantment.SWIFT_SNEAK`** | **`minecraft:enchanted_book` + `minecraft:swift_sneak`** | **Enchanted book (Swift Sneak)** | Treasure legs -- faster sneak (Deep Dark) |
| 151 | Enchanted book | 1 | **`Enchantment.DEPTH_STRIDER`** | **`minecraft:enchanted_book` + `minecraft:depth_strider`** | **Enchanted book (Depth Strider)** | Boots -- swim/walk in water |
| 152 | Enchanted book | 1 | **`Enchantment.LOYALTY`** | **`minecraft:enchanted_book` + `minecraft:loyalty`** | **Enchanted book (Loyalty)** | Trident returns after throw |
| 153 | Enchanted book | 1 | **`Enchantment.LUNGE`** | **`minecraft:enchanted_book` + `minecraft:lunge`** | **Enchanted book (Lunge)** | Spear (MoM) -- lunge further on attack |
| 154 | Enchanted book | 1 | **`Enchantment.CHANNELING`** | **`minecraft:enchanted_book` + `minecraft:channeling`** | **Enchanted book (Channeling)** | Trident -- lightning in thunderstorms |
| 155 | Potion | 1 | **`PotionType.SWIFTNESS`** | **`minecraft:potion` + `minecraft:swiftness`** | **Potion (Swiftness)** | Speed I -- travel / kite |
| 156 | Potion | 1 | **`PotionType.LONG_SWIFTNESS`** | **`minecraft:potion` + `minecraft:long_swiftness`** | **Potion (Swiftness (long))** | Longer speed |
| 157 | Potion | 1 | **`PotionType.STRONG_SWIFTNESS`** | **`minecraft:potion` + `minecraft:strong_swiftness`** | **Potion (Swiftness II)** | Speed II -- strong mobility |
| 158 | Potion | 1 | **`PotionType.INVISIBILITY`** | **`minecraft:potion` + `minecraft:invisibility`** | **Potion (Invisibility)** | Stealth / escape |
| 159 | Potion | 1 | **`PotionType.LONG_INVISIBILITY`** | **`minecraft:potion` + `minecraft:long_invisibility`** | **Potion (Invisibility (long))** | Longer invis |
| 160 | Potion | 1 | **`PotionType.TURTLE_MASTER`** | **`minecraft:potion` + `minecraft:turtle_master`** | **Potion (Turtle Master)** | Resistance III + Slowness IV -- tank tradeoff |
| 161 | Potion | 1 | **`PotionType.LONG_TURTLE_MASTER`** | **`minecraft:potion` + `minecraft:long_turtle_master`** | **Potion (Turtle Master (long))** | Longer turtle master |
| 162 | Potion | 1 | **`PotionType.STRONG_TURTLE_MASTER`** | **`minecraft:potion` + `minecraft:strong_turtle_master`** | **Potion (Turtle Master II)** | Resistance IV + Slowness VI -- max turtle |
| 163 | Potion | 1 | **`PotionType.FIRE_RESISTANCE`** | **`minecraft:potion` + `minecraft:fire_resistance`** | **Potion (Fire Resistance)** | Immune fire/lava -- Nether staple |
| 164 | Potion | 1 | **`PotionType.LONG_FIRE_RESISTANCE`** | **`minecraft:potion` + `minecraft:long_fire_resistance`** | **Potion (Fire Resistance (long))** | Longer fire resistance |
| 165 | Potion | 1 | **`PotionType.HEALING`** | **`minecraft:potion` + `minecraft:healing`** | **Potion (Healing)** | Instant Health I -- clutch heal |
| 166 | Potion | 1 | **`PotionType.STRONG_HEALING`** | **`minecraft:potion` + `minecraft:strong_healing`** | **Potion (Healing II)** | Instant Health II -- big clutch |
| 167 | Potion | 1 | **`PotionType.REGENERATION`** | **`minecraft:potion` + `minecraft:regeneration`** | **Potion (Regeneration)** | Regen I -- sustain |
| 168 | Potion | 1 | **`PotionType.LONG_REGENERATION`** | **`minecraft:potion` + `minecraft:long_regeneration`** | **Potion (Regeneration (long))** | Longer regen |
| 169 | Potion | 1 | **`PotionType.STRONG_REGENERATION`** | **`minecraft:potion` + `minecraft:strong_regeneration`** | **Potion (Regeneration II)** | Regen II -- strong sustain |
| 170 | Potion | 1 | **`PotionType.STRENGTH`** | **`minecraft:potion` + `minecraft:strength`** | **Potion (Strength)** | Strength I -- melee power |
| 171 | Potion | 1 | **`PotionType.LONG_STRENGTH`** | **`minecraft:potion` + `minecraft:long_strength`** | **Potion (Strength (long))** | Longer strength |
| 172 | Potion | 1 | **`PotionType.STRONG_STRENGTH`** | **`minecraft:potion` + `minecraft:strong_strength`** | **Potion (Strength II)** | Strength II -- top drinkable combat buff |
| 173 | Spawn egg | 1 | `PANDA_SPAWN_EGG` | `minecraft:panda_spawn_egg` | Panda | Mostly passive; aggressive variants |
| 174 | Spawn egg | 1 | `GOAT_SPAWN_EGG` | `minecraft:goat_spawn_egg` | Goat | Ram knockback; horn drops |
| 175 | Spawn egg | 1 | `WOLF_SPAWN_EGG` | `minecraft:wolf_spawn_egg` | Wolf | Strong tameable pack DPS |
| 176 | Spawn egg | 1 | `BEE_SPAWN_EGG` | `minecraft:bee_spawn_egg` | Bee | Swarm poison when angered |
| 177 | Spawn egg | 1 | `DOLPHIN_SPAWN_EGG` | `minecraft:dolphin_spawn_egg` | Dolphin | Grace buff; can attack if hurt |
| 178 | Spawn egg | 1 | `AXOLOTL_SPAWN_EGG` | `minecraft:axolotl_spawn_egg` | Axolotl | Strong aquatic ally |
| 179 | Spawn egg | 1 | `PUFFERFISH_SPAWN_EGG` | `minecraft:pufferfish_spawn_egg` | Pufferfish | Poison + thorns when inflated |
| 180 | Enchanted book | 1 | **`Enchantment.PROJECTILE_PROTECTION`** | **`minecraft:enchanted_book` + `minecraft:projectile_protection`** | **Enchanted book (Projectile Protection)** | Armor -- arrows/tridents |
| 181 | Enchanted book | 1 | **`Enchantment.BLAST_PROTECTION`** | **`minecraft:enchanted_book` + `minecraft:blast_protection`** | **Enchanted book (Blast Protection)** | Armor -- explosions |
| 182 | Enchanted book | 1 | **`Enchantment.FIRE_PROTECTION`** | **`minecraft:enchanted_book` + `minecraft:fire_protection`** | **Enchanted book (Fire Protection)** | Armor -- fire/lava |
| 183 | Enchanted book | 1 | **`Enchantment.WIND_BURST`** | **`minecraft:enchanted_book` + `minecraft:wind_burst`** | **Enchanted book (Wind Burst)** | Mace -- burst of wind on smash hit |
| 184 | Enchanted book | 1 | **`Enchantment.DENSITY`** | **`minecraft:enchanted_book` + `minecraft:density`** | **Enchanted book (Density)** | Mace -- more damage from fall smash |
| 185 | Enchanted book | 1 | **`Enchantment.QUICK_CHARGE`** | **`minecraft:enchanted_book` + `minecraft:quick_charge`** | **Enchanted book (Quick Charge)** | Crossbow -- faster charge |
| 186 | Enchanted book | 1 | **`Enchantment.MULTISHOT`** | **`minecraft:enchanted_book` + `minecraft:multishot`** | **Enchanted book (Multishot)** | Crossbow -- three projectiles |
| 187 | Enchanted book | 1 | **`Enchantment.PIERCING`** | **`minecraft:enchanted_book` + `minecraft:piercing`** | **Enchanted book (Piercing)** | Crossbow -- pierce entities (vs Multishot) |
| 188 | Enchanted book | 1 | **`Enchantment.SWEEPING_EDGE`** | **`minecraft:enchanted_book` + `minecraft:sweeping_edge`** | **Enchanted book (Sweeping Edge)** | Sword -- stronger sweeps (Java) |
| 189 | Enchanted book | 1 | **`Enchantment.IMPALING`** | **`minecraft:enchanted_book` + `minecraft:impaling`** | **Enchanted book (Impaling)** | Trident -- more damage to aquatic |
| 190 | Spawn egg | 1 | `ENDERMITE_SPAWN_EGG` | `minecraft:endermite_spawn_egg` | Endermite | Tiny End pest |
| 191 | Spawn egg | 1 | `ZOMBIE_SPAWN_EGG` | `minecraft:zombie_spawn_egg` | Zombie | Baseline undead melee |
| 192 | Spawn egg | 1 | `ZOMBIE_VILLAGER_SPAWN_EGG` | `minecraft:zombie_villager_spawn_egg` | Zombie villager | Same threat + cure value |
| 193 | Spawn egg | 1 | `HUSK_SPAWN_EGG` | `minecraft:husk_spawn_egg` | Husk | Hunger; no burn |
| 194 | Spawn egg | 1 | `DROWNED_SPAWN_EGG` | `minecraft:drowned_spawn_egg` | Drowned | Trident chance; aquatic |
| 195 | Spawn egg | 1 | `ZOMBIFIED_PIGLIN_SPAWN_EGG` | `minecraft:zombified_piglin_spawn_egg` | Zombified piglin | Neutral until aggro -> swarm |
| 196 | Spawn egg | 1 | `SPIDER_SPAWN_EGG` | `minecraft:spider_spawn_egg` | Spider | Climb + leap |
| 197 | Spawn egg | 1 | `CAVE_SPIDER_SPAWN_EGG` | `minecraft:cave_spider_spawn_egg` | Cave spider | Poison; smaller |
| 198 | Spawn egg | 1 | `SLIME_SPAWN_EGG` | `minecraft:slime_spawn_egg` | Slime | Splits; size scales threat |
| 199 | Spawn egg | 1 | `MAGMA_CUBE_SPAWN_EGG` | `minecraft:magma_cube_spawn_egg` | Magma cube | Fire + split; Nether |
| 200 | Enchanted book | 1 | **`Enchantment.SMITE`** | **`minecraft:enchanted_book` + `minecraft:smite`** | **Enchanted book (Smite)** | Melee -- undead specialist |
| 201 | Spawn egg | 1 | `SKELETON_SPAWN_EGG` | `minecraft:skeleton_spawn_egg` | Skeleton | Ranged baseline |
| 202 | Spawn egg | 1 | `STRAY_SPAWN_EGG` | `minecraft:stray_spawn_egg` | Stray | Slowness arrows |
| 203 | Spawn egg | 1 | `BOGGED_SPAWN_EGG` | `minecraft:bogged_spawn_egg` | Bogged | Poison arrows; mushrooms |
| 204 | Spawn egg | 1 | `PARCHED_SPAWN_EGG` | `minecraft:parched_spawn_egg` | Parched | Desert skeleton; Weakness arrows (MoM) |
| 205 | Spawn egg | 1 | `PHANTOM_SPAWN_EGG` | `minecraft:phantom_spawn_egg` | Phantom | Aerial dive; insomnia |
| 206 | Enchanted book | 1 | **`Enchantment.THORNS`** | **`minecraft:enchanted_book` + `minecraft:thorns`** | **Enchanted book (Thorns)** | Armor -- reflect damage; durability cost |
| 207 | Enchanted book | 1 | **`Enchantment.RIPTIDE`** | **`minecraft:enchanted_book` + `minecraft:riptide`** | **Enchanted book (Riptide)** | Trident -- propel in water/rain |
| 208 | Enchanted book | 1 | **`Enchantment.POWER`** | **`minecraft:enchanted_book` + `minecraft:power`** | **Enchanted book (Power)** | Bow -- primary ranged damage |
| 209 | Enchanted book | 1 | **`Enchantment.BREACH`** | **`minecraft:enchanted_book` + `minecraft:breach`** | **Enchanted book (Breach)** | Mace -- ignore armor |
| 210 | Spawn egg | 1 | `GUARDIAN_SPAWN_EGG` | `minecraft:guardian_spawn_egg` | Guardian | Laser + thorns |
| 211 | Spawn egg | 1 | `PILLAGER_SPAWN_EGG` | `minecraft:pillager_spawn_egg` | Pillager | Crossbow raids |
| 212 | Spawn egg | 1 | `VINDICATOR_SPAWN_EGG` | `minecraft:vindicator_spawn_egg` | Vindicator | High axe DPS |
| 213 | Spawn egg | 1 | `WITCH_SPAWN_EGG` | `minecraft:witch_spawn_egg` | Witch | Potion spam + heal |
| 214 | Spawn egg | 1 | `VEX_SPAWN_EGG` | `minecraft:vex_spawn_egg` | Vex | Phase through walls |
| 215 | Spawn egg | 1 | `PIGLIN_SPAWN_EGG` | `minecraft:piglin_spawn_egg` | Piglin | Crossbow / barter |
| 216 | Spawn egg | 1 | `BLAZE_SPAWN_EGG` | `minecraft:blaze_spawn_egg` | Blaze | Fireball volleys; fly |
| 217 | Spawn egg | 1 | `WITHER_SKELETON_SPAWN_EGG` | `minecraft:wither_skeleton_spawn_egg` | Wither skeleton | Wither effect; fortress |
| 218 | Enchanted book | 1 | **`Enchantment.INFINITY`** | **`minecraft:enchanted_book` + `minecraft:infinity`** | **Enchanted book (Infinity)** | Bow -- unlimited arrows (conflicts Mending) |
| 219 | Enchanted book | 1 | **`Enchantment.SILK_TOUCH`** | **`minecraft:enchanted_book` + `minecraft:silk_touch`** | **Enchanted book (Silk Touch)** | Tool -- drop blocks intact (conflicts Fortune) |
| 220 | Spawn egg | 1 | `CREEPER_SPAWN_EGG` | `minecraft:creeper_spawn_egg` | Creeper | Terrain + player wipe risk |
| 221 | Spawn egg | 1 | `SHULKER_SPAWN_EGG` | `minecraft:shulker_spawn_egg` | Shulker | Levitation bullets |
| 222 | Spawn egg | 1 | `ENDERMAN_SPAWN_EGG` | `minecraft:enderman_spawn_egg` | Enderman | High melee; teleport grief |
| 223 | Spawn egg | 1 | `HOGLIN_SPAWN_EGG` | `minecraft:hoglin_spawn_egg` | Hoglin | Heavy knockback tank |
| 224 | Spawn egg | 1 | `ZOGLIN_SPAWN_EGG` | `minecraft:zoglin_spawn_egg` | Zoglin | Undead hoglin; attacks everything |
| 225 | Spawn egg | 1 | `POLAR_BEAR_SPAWN_EGG` | `minecraft:polar_bear_spawn_egg` | Polar bear | High HP melee when provoked |
| 226 | Enchanted book | 1 | **`Enchantment.FORTUNE`** | **`minecraft:enchanted_book` + `minecraft:fortune`** | **Enchanted book (Fortune)** | Tool -- more ore/drops |
| 227 | Enchanted book | 1 | **`Enchantment.LOOTING`** | **`minecraft:enchanted_book` + `minecraft:looting`** | **Enchanted book (Looting)** | Weapon -- more mob drops |
| 228 | Spawn egg | 1 | `CAMEL_HUSK_SPAWN_EGG` | `minecraft:camel_husk_spawn_egg` | Camel husk | Mount; jockey threat when ridden |
| 229 | Spawn egg | 1 | `ZOMBIE_NAUTILUS_SPAWN_EGG` | `minecraft:zombie_nautilus_spawn_egg` | Zombie nautilus | Undead water mount + drowned jockey |
| 230 | Spawn egg | 1 | `BREEZE_SPAWN_EGG` | `minecraft:breeze_spawn_egg` | Breeze | Wind charges; trial chambers |
| 231 | Enchanted book | 1 | **`Enchantment.EFFICIENCY`** | **`minecraft:enchanted_book` + `minecraft:efficiency`** | **Enchanted book (Efficiency)** | Tool -- mine/dig much faster |
| 232 | Enchanted book | 1 | **`Enchantment.UNBREAKING`** | **`minecraft:enchanted_book` + `minecraft:unbreaking`** | **Enchanted book (Unbreaking)** | Any -- durability multiplier |
| 233 | Enchanted book | 1 | **`Enchantment.FEATHER_FALLING`** | **`minecraft:enchanted_book` + `minecraft:feather_falling`** | **Enchanted book (Feather Falling)** | Boots -- fall damage (survival essential) |
| 234 | Spawn egg | 1 | `PIGLIN_BRUTE_SPAWN_EGG` | `minecraft:piglin_brute_spawn_egg` | Piglin brute | Bastion elite |
| 235 | Spawn egg | 1 | `EVOKER_SPAWN_EGG` | `minecraft:evoker_spawn_egg` | Evoker | Fangs + vex summons |
| 236 | Spawn egg | 1 | `ELDER_GUARDIAN_SPAWN_EGG` | `minecraft:elder_guardian_spawn_egg` | Elder guardian | Mining Fatigue III + laser |
| 237 | Spawn egg | 1 | `CREAKING_SPAWN_EGG` | `minecraft:creaking_spawn_egg` | Creaking | Near-unkillable while heart lives |
| 238 | Enchanted book | 1 | **`Enchantment.PROTECTION`** | **`minecraft:enchanted_book` + `minecraft:protection`** | **Enchanted book (Protection)** | Armor -- best general damage reduction |
| 239 | Enchanted book | 1 | **`Enchantment.SHARPNESS`** | **`minecraft:enchanted_book` + `minecraft:sharpness`** | **Enchanted book (Sharpness)** | Melee -- best general weapon damage |
| 240 | Item | 1 | **`NETHERITE_SCRAP`** | **`minecraft:netherite_scrap`** | **Netherite Scrap** | Ancient debris product -- 4 scrap + 4 gold = ingot |
| 241 | Item | **16** | **`DIAMOND`** | **`minecraft:diamond`** | **Diamond x16** | Raw diamonds -- gear/netherite path / trade; qty 16 |
| 242 | Item | 1 | **`NETHERITE_INGOT`** | **`minecraft:netherite_ingot`** | **Netherite Ingot** | Upgrade path to netherite gear; rarer than diamond stack |
| 243 | Block | 1 | **`DIAMOND_BLOCK`** | **`minecraft:diamond_block`** | **Diamond block** | Mineral -- apex compressed ore |
| 244 | Enchanted book | 1 | **`Enchantment.MENDING`** | **`minecraft:enchanted_book` + `minecraft:mending`** | **Enchanted book (Mending)** | Treasure -- XP repairs gear; usually best book |
| 245 | Spawn egg | 1 | `IRON_GOLEM_SPAWN_EGG` | `minecraft:iron_golem_spawn_egg` | Iron golem | Top friendly combatant |
| 246 | Spawn egg | 1 | `WARDEN_SPAWN_EGG` | `minecraft:warden_spawn_egg` | Warden | Sonic boom; Overworld apex threat |
| 247 | Item | 1 | **`NETHER_STAR`** | **`minecraft:nether_star`** | **Nether Star** | Wither drop -- beacon craft / apex rare; most rare on this list |

---

## Arrows only (least -> most, qty 16 each)

| Overall rank | Qty | Entry | Namespaced |
| ---: | ---: | --- | --- |
| 30 | 16 | Tipped Arrow (Thick) x16 | `minecraft:tipped_arrow{thick}` |
| 31 | 16 | Tipped Arrow (Awkward) x16 | `minecraft:tipped_arrow{awkward}` |
| 32 | 16 | Tipped Arrow (Healing) x16 | `minecraft:tipped_arrow{healing}` |
| 33 | 16 | Tipped Arrow (Healing II) x16 | `minecraft:tipped_arrow{strong_healing}` |
| 34 | 16 | Tipped Arrow (Regeneration) x16 | `minecraft:tipped_arrow{regeneration}` |
| 35 | 16 | Tipped Arrow (Regeneration long) x16 | `minecraft:tipped_arrow{long_regeneration}` |
| 36 | 16 | Tipped Arrow (Regeneration II) x16 | `minecraft:tipped_arrow{strong_regeneration}` |
| 37 | 16 | Tipped Arrow (Strength) x16 | `minecraft:tipped_arrow{strength}` |
| 38 | 16 | Tipped Arrow (Strength long) x16 | `minecraft:tipped_arrow{long_strength}` |
| 39 | 16 | Tipped Arrow (Strength II) x16 | `minecraft:tipped_arrow{strong_strength}` |
| 40 | 16 | Tipped Arrow (Swiftness) x16 | `minecraft:tipped_arrow{swiftness}` |
| 41 | 16 | Tipped Arrow (Swiftness long) x16 | `minecraft:tipped_arrow{long_swiftness}` |
| 42 | 16 | Tipped Arrow (Swiftness II) x16 | `minecraft:tipped_arrow{strong_swiftness}` |
| 43 | 16 | Tipped Arrow (Leaping) x16 | `minecraft:tipped_arrow{leaping}` |
| 44 | 16 | Tipped Arrow (Leaping long) x16 | `minecraft:tipped_arrow{long_leaping}` |
| 45 | 16 | Tipped Arrow (Leaping II) x16 | `minecraft:tipped_arrow{strong_leaping}` |
| 46 | 16 | Tipped Arrow (Fire Resistance) x16 | `minecraft:tipped_arrow{fire_resistance}` |
| 47 | 16 | Tipped Arrow (Fire Resistance long) x16 | `minecraft:tipped_arrow{long_fire_resistance}` |
| 48 | 16 | Tipped Arrow (Night Vision) x16 | `minecraft:tipped_arrow{night_vision}` |
| 49 | 16 | Tipped Arrow (Night Vision long) x16 | `minecraft:tipped_arrow{long_night_vision}` |
| 50 | 16 | Tipped Arrow (Water Breathing) x16 | `minecraft:tipped_arrow{water_breathing}` |
| 51 | 16 | Tipped Arrow (Water Breathing long) x16 | `minecraft:tipped_arrow{long_water_breathing}` |
| 52 | 16 | Tipped Arrow (Invisibility) x16 | `minecraft:tipped_arrow{invisibility}` |
| 53 | 16 | Tipped Arrow (Invisibility long) x16 | `minecraft:tipped_arrow{long_invisibility}` |
| 54 | 16 | Tipped Arrow (Slow Falling) x16 | `minecraft:tipped_arrow{slow_falling}` |
| 55 | 16 | Tipped Arrow (Slow Falling long) x16 | `minecraft:tipped_arrow{long_slow_falling}` |
| 56 | 16 | Tipped Arrow (Luck) x16 | `minecraft:tipped_arrow{luck}` |
| 57 | 16 | Tipped Arrow (Turtle Master) x16 | `minecraft:tipped_arrow{turtle_master}` |
| 58 | 16 | Tipped Arrow (Turtle Master long) x16 | `minecraft:tipped_arrow{long_turtle_master}` |
| 59 | 16 | Tipped Arrow (Turtle Master II) x16 | `minecraft:tipped_arrow{strong_turtle_master}` |
| 60 | 16 | Tipped Arrow (Infested) x16 | `minecraft:tipped_arrow{infested}` |
| 61 | 16 | Tipped Arrow (Oozing) x16 | `minecraft:tipped_arrow{oozing}` |
| 62 | 16 | Tipped Arrow (Weaving) x16 | `minecraft:tipped_arrow{weaving}` |
| 63 | 16 | Tipped Arrow (Wind Charged) x16 | `minecraft:tipped_arrow{wind_charged}` |
| 64 | 16 | Arrow x16 | `minecraft:arrow` |
| 65 | 16 | Spectral Arrow x16 | `minecraft:spectral_arrow` |
| 67 | 16 | Tipped Arrow (Weakness) x16 | `minecraft:tipped_arrow{weakness}` |
| 68 | 16 | Tipped Arrow (Weakness long) x16 | `minecraft:tipped_arrow{long_weakness}` |
| 69 | 16 | Tipped Arrow (Slowness) x16 | `minecraft:tipped_arrow{slowness}` |
| 70 | 16 | Tipped Arrow (Slowness long) x16 | `minecraft:tipped_arrow{long_slowness}` |
| 71 | 16 | Tipped Arrow (Slowness IV) x16 | `minecraft:tipped_arrow{strong_slowness}` |
| 72 | 16 | Tipped Arrow (Poison) x16 | `minecraft:tipped_arrow{poison}` |
| 73 | 16 | Tipped Arrow (Poison long) x16 | `minecraft:tipped_arrow{long_poison}` |
| 74 | 16 | Tipped Arrow (Poison II) x16 | `minecraft:tipped_arrow{strong_poison}` |
| 75 | 16 | Tipped Arrow (Harming) x16 | `minecraft:tipped_arrow{harming}` |
| 76 | 16 | Tipped Arrow (Harming II) x16 | `minecraft:tipped_arrow{strong_harming}` |

---

## Armor trims only (least -> most)

| Overall rank | Material | Namespaced id |
| ---: | --- | --- |
| 96 | `SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:sentry_armor_trim_smithing_template` |
| 97 | `DUNE_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:dune_armor_trim_smithing_template` |
| 98 | `COAST_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:coast_armor_trim_smithing_template` |
| 99 | `WILD_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:wild_armor_trim_smithing_template` |
| 100 | `WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:wayfinder_armor_trim_smithing_template` |
| 101 | `RAISER_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:raiser_armor_trim_smithing_template` |
| 102 | `SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:shaper_armor_trim_smithing_template` |
| 103 | `HOST_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:host_armor_trim_smithing_template` |
| 104 | `SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:snout_armor_trim_smithing_template` |
| 105 | `RIB_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:rib_armor_trim_smithing_template` |
| 106 | `TIDE_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:tide_armor_trim_smithing_template` |
| 107 | `VEX_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:vex_armor_trim_smithing_template` |
| 108 | `EYE_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:eye_armor_trim_smithing_template` |
| 109 | `BOLT_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:bolt_armor_trim_smithing_template` |
| 110 | `FLOW_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:flow_armor_trim_smithing_template` |
| 111 | `WARD_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:ward_armor_trim_smithing_template` |
| 112 | `SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:spire_armor_trim_smithing_template` |
| 113 | `SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE` | `minecraft:silence_armor_trim_smithing_template` |

---

## Potions only (least -> most)

| Overall rank | PotionType | Namespaced key |
| ---: | --- | --- |
| 3 | `THICK` | `minecraft:thick` |
| 4 | `AWKWARD` | `minecraft:awkward` |
| 12 | `STRONG_POISON` | `minecraft:strong_poison` |
| 13 | `POISON` | `minecraft:poison` |
| 14 | `LONG_POISON` | `minecraft:long_poison` |
| 15 | `STRONG_HARMING` | `minecraft:strong_harming` |
| 16 | `HARMING` | `minecraft:harming` |
| 17 | `WEAKNESS` | `minecraft:weakness` |
| 18 | `LONG_WEAKNESS` | `minecraft:long_weakness` |
| 19 | `STRONG_SLOWNESS` | `minecraft:strong_slowness` |
| 20 | `SLOWNESS` | `minecraft:slowness` |
| 21 | `LONG_SLOWNESS` | `minecraft:long_slowness` |
| 22 | `INFESTED` | `minecraft:infested` |
| 23 | `OOZING` | `minecraft:oozing` |
| 24 | `WEAVING` | `minecraft:weaving` |
| 25 | `WIND_CHARGED` | `minecraft:wind_charged` |
| 28 | `LUCK` | `minecraft:luck` |
| 117 | `NIGHT_VISION` | `minecraft:night_vision` |
| 118 | `LONG_NIGHT_VISION` | `minecraft:long_night_vision` |
| 119 | `LEAPING` | `minecraft:leaping` |
| 120 | `LONG_LEAPING` | `minecraft:long_leaping` |
| 121 | `STRONG_LEAPING` | `minecraft:strong_leaping` |
| 122 | `WATER_BREATHING` | `minecraft:water_breathing` |
| 123 | `LONG_WATER_BREATHING` | `minecraft:long_water_breathing` |
| 124 | `SLOW_FALLING` | `minecraft:slow_falling` |
| 125 | `LONG_SLOW_FALLING` | `minecraft:long_slow_falling` |
| 155 | `SWIFTNESS` | `minecraft:swiftness` |
| 156 | `LONG_SWIFTNESS` | `minecraft:long_swiftness` |
| 157 | `STRONG_SWIFTNESS` | `minecraft:strong_swiftness` |
| 158 | `INVISIBILITY` | `minecraft:invisibility` |
| 159 | `LONG_INVISIBILITY` | `minecraft:long_invisibility` |
| 160 | `TURTLE_MASTER` | `minecraft:turtle_master` |
| 161 | `LONG_TURTLE_MASTER` | `minecraft:long_turtle_master` |
| 162 | `STRONG_TURTLE_MASTER` | `minecraft:strong_turtle_master` |
| 163 | `FIRE_RESISTANCE` | `minecraft:fire_resistance` |
| 164 | `LONG_FIRE_RESISTANCE` | `minecraft:long_fire_resistance` |
| 165 | `HEALING` | `minecraft:healing` |
| 166 | `STRONG_HEALING` | `minecraft:strong_healing` |
| 167 | `REGENERATION` | `minecraft:regeneration` |
| 168 | `LONG_REGENERATION` | `minecraft:long_regeneration` |
| 169 | `STRONG_REGENERATION` | `minecraft:strong_regeneration` |
| 170 | `STRENGTH` | `minecraft:strength` |
| 171 | `LONG_STRENGTH` | `minecraft:long_strength` |
| 172 | `STRONG_STRENGTH` | `minecraft:strong_strength` |

---

## Enchanted books only (least -> most)

| Overall rank | Enchantment | Namespaced key |
| ---: | --- | --- |
| 1 | Binding Curse | `minecraft:binding_curse` |
| 2 | Vanishing Curse | `minecraft:vanishing_curse` |
| 26 | Luck of the Sea | `minecraft:luck_of_the_sea` |
| 27 | Lure | `minecraft:lure` |
| 87 | Bane of Arthropods | `minecraft:bane_of_arthropods` |
| 114 | Punch | `minecraft:punch` |
| 115 | Knockback | `minecraft:knockback` |
| 116 | Aqua Affinity | `minecraft:aqua_affinity` |
| 140 | Frost Walker | `minecraft:frost_walker` |
| 141 | Soul Speed | `minecraft:soul_speed` |
| 142 | Flame | `minecraft:flame` |
| 143 | Fire Aspect | `minecraft:fire_aspect` |
| 149 | Respiration | `minecraft:respiration` |
| 150 | Swift Sneak | `minecraft:swift_sneak` |
| 151 | Depth Strider | `minecraft:depth_strider` |
| 152 | Loyalty | `minecraft:loyalty` |
| 153 | Lunge | `minecraft:lunge` |
| 154 | Channeling | `minecraft:channeling` |
| 180 | Projectile Protection | `minecraft:projectile_protection` |
| 181 | Blast Protection | `minecraft:blast_protection` |
| 182 | Fire Protection | `minecraft:fire_protection` |
| 183 | Wind Burst | `minecraft:wind_burst` |
| 184 | Density | `minecraft:density` |
| 185 | Quick Charge | `minecraft:quick_charge` |
| 186 | Multishot | `minecraft:multishot` |
| 187 | Piercing | `minecraft:piercing` |
| 188 | Sweeping Edge | `minecraft:sweeping_edge` |
| 189 | Impaling | `minecraft:impaling` |
| 200 | Smite | `minecraft:smite` |
| 206 | Thorns | `minecraft:thorns` |
| 207 | Riptide | `minecraft:riptide` |
| 208 | Power | `minecraft:power` |
| 209 | Breach | `minecraft:breach` |
| 218 | Infinity | `minecraft:infinity` |
| 219 | Silk Touch | `minecraft:silk_touch` |
| 226 | Fortune | `minecraft:fortune` |
| 227 | Looting | `minecraft:looting` |
| 231 | Efficiency | `minecraft:efficiency` |
| 232 | Unbreaking | `minecraft:unbreaking` |
| 233 | Feather Falling | `minecraft:feather_falling` |
| 238 | Protection | `minecraft:protection` |
| 239 | Sharpness | `minecraft:sharpness` |
| 244 | Mending | `minecraft:mending` |

---

## Free Lotto Ticket (RootMC)

Not a vanilla `Material`. Rank **29** -- `rootmc:free_lotto_ticket` is a RootMC reward / crate-style item tied to **Root-Gamble** `/lotto`.

### What redeeming it does

- Adds **one free-play credit** for lotto on the player account -- the same entitlement as when a player still has their **first unused** free lotto play.
- That credit makes the next `/lotto <number>` cost **0 G** instead of the configured ticket price (default **25 G** in `root-gamble.yml` `lotto.ticket-price`).
- After the free play is spent, Root-Gamble marks lotto free-play used (`gamble_free_play`, `game_id = lotto`). Redeeming another Free Lotto Ticket **puts a credit back** (clears / re-grants that unused free-play state) so they can take another complimentary ticket.
- Player still chooses a number and enters the draw with `/lotto`; the item is not itself a numbered slip.

### What it is not

- Not wallet Gold (does not mint G).
- Not an automatic jackpot entry -- redeem credit, then buy with `/lotto`.
- Not a multi-game voucher; credit is for **lotto** only (other games have their own one-time free plays).

### Why it ranks low-mid

Face value is one ticket (~25 G) plus jackpot upside, but lottery expected value is poor -- same tier as niche utility, not combat power.

---

## Quick tiers

| Tier | Summary |
| --- | --- |
| Bottom | Curses, base potions, useless/buff tipped arrows, ambient mobs |
| Low-mid | Livestock, free-lotto credit, plain/spectral arrows, utility potions, armor trims, mounts |
| Mid-high | Offensive tipped arrows (weakness/slowness/poison/harming), combat potions, hostiles |
| Apex | Top books, diamond / netherite, Mending, Warden, **Nether Star** |

### Mineral ladder

| Overall rank | Entry |
| ---: | --- |
| 126 | Coal x16 |
| 127 | Coal Block x16 |
| 128 | Glowstone x8 |
| 129 | Iron Ingot x16 |
| 130 | Iron block |
| 148 | Emerald block |
| 240 | Netherite Scrap |
| 241 | Diamond x16 |
| 242 | Netherite Ingot |
| 243 | Diamond block |
| 247 | Nether Star |

---

## Alphabetical index

| Key | Overall rank |
| --- | ---: |
| `ALLAY_SPAWN_EGG` | 92 |
| `ARMADILLO_SPAWN_EGG` | 83 |
| `AXOLOTL_SPAWN_EGG` | 178 |
| `BAT_SPAWN_EGG` | 6 |
| `BEE_SPAWN_EGG` | 176 |
| `BLAZE_SPAWN_EGG` | 216 |
| `BOGGED_SPAWN_EGG` | 203 |
| `BOLT_ARMOR_TRIM_SMITHING_TEMPLATE` | 109 |
| `BREEZE_SPAWN_EGG` | 230 |
| `CAMEL_HUSK_SPAWN_EGG` | 228 |
| `CAMEL_SPAWN_EGG` | 134 |
| `CAT_SPAWN_EGG` | 88 |
| `CAVE_SPIDER_SPAWN_EGG` | 197 |
| `CHICKEN_SPAWN_EGG` | 77 |
| `COAL` | 126 |
| `COAL_BLOCK` | 127 |
| `COAST_ARMOR_TRIM_SMITHING_TEMPLATE` | 98 |
| `COD_SPAWN_EGG` | 7 |
| `COPPER_GOLEM_SPAWN_EGG` | 95 |
| `COW_SPAWN_EGG` | 81 |
| `CREAKING_SPAWN_EGG` | 237 |
| `CREEPER_SPAWN_EGG` | 220 |
| `DIAMOND` | 241 |
| `DIAMOND_BLOCK` | 243 |
| `DOLPHIN_SPAWN_EGG` | 177 |
| `DONKEY_SPAWN_EGG` | 131 |
| `DROWNED_SPAWN_EGG` | 194 |
| `DUNE_ARMOR_TRIM_SMITHING_TEMPLATE` | 97 |
| `ELDER_GUARDIAN_SPAWN_EGG` | 236 |
| `EMERALD_BLOCK` | 148 |
| `ENDERMAN_SPAWN_EGG` | 222 |
| `ENDERMITE_SPAWN_EGG` | 190 |
| `ENDER_PEARL` | 66 |
| `EVOKER_SPAWN_EGG` | 235 |
| `EYE_ARMOR_TRIM_SMITHING_TEMPLATE` | 108 |
| `Enchantment.AQUA_AFFINITY` | 116 |
| `Enchantment.BANE_OF_ARTHROPODS` | 87 |
| `Enchantment.BINDING_CURSE` | 1 |
| `Enchantment.BLAST_PROTECTION` | 181 |
| `Enchantment.BREACH` | 209 |
| `Enchantment.CHANNELING` | 154 |
| `Enchantment.DENSITY` | 184 |
| `Enchantment.DEPTH_STRIDER` | 151 |
| `Enchantment.EFFICIENCY` | 231 |
| `Enchantment.FEATHER_FALLING` | 233 |
| `Enchantment.FIRE_ASPECT` | 143 |
| `Enchantment.FIRE_PROTECTION` | 182 |
| `Enchantment.FLAME` | 142 |
| `Enchantment.FORTUNE` | 226 |
| `Enchantment.FROST_WALKER` | 140 |
| `Enchantment.IMPALING` | 189 |
| `Enchantment.INFINITY` | 218 |
| `Enchantment.KNOCKBACK` | 115 |
| `Enchantment.LOOTING` | 227 |
| `Enchantment.LOYALTY` | 152 |
| `Enchantment.LUCK_OF_THE_SEA` | 26 |
| `Enchantment.LUNGE` | 153 |
| `Enchantment.LURE` | 27 |
| `Enchantment.MENDING` | 244 |
| `Enchantment.MULTISHOT` | 186 |
| `Enchantment.PIERCING` | 187 |
| `Enchantment.POWER` | 208 |
| `Enchantment.PROJECTILE_PROTECTION` | 180 |
| `Enchantment.PROTECTION` | 238 |
| `Enchantment.PUNCH` | 114 |
| `Enchantment.QUICK_CHARGE` | 185 |
| `Enchantment.RESPIRATION` | 149 |
| `Enchantment.RIPTIDE` | 207 |
| `Enchantment.SHARPNESS` | 239 |
| `Enchantment.SILK_TOUCH` | 219 |
| `Enchantment.SMITE` | 200 |
| `Enchantment.SOUL_SPEED` | 141 |
| `Enchantment.SWEEPING_EDGE` | 188 |
| `Enchantment.SWIFT_SNEAK` | 150 |
| `Enchantment.THORNS` | 206 |
| `Enchantment.UNBREAKING` | 232 |
| `Enchantment.VANISHING_CURSE` | 2 |
| `Enchantment.WIND_BURST` | 183 |
| `FLOW_ARMOR_TRIM_SMITHING_TEMPLATE` | 110 |
| `FOX_SPAWN_EGG` | 91 |
| `FROG_SPAWN_EGG` | 86 |
| `GLOWSTONE` | 128 |
| `GLOW_SQUID_SPAWN_EGG` | 10 |
| `GOAT_SPAWN_EGG` | 174 |
| `GUARDIAN_SPAWN_EGG` | 210 |
| `HAPPY_GHAST_SPAWN_EGG` | 139 |
| `HOGLIN_SPAWN_EGG` | 223 |
| `HORSE_SPAWN_EGG` | 133 |
| `HOST_ARMOR_TRIM_SMITHING_TEMPLATE` | 103 |
| `HUSK_SPAWN_EGG` | 193 |
| `IRON_BLOCK` | 130 |
| `IRON_GOLEM_SPAWN_EGG` | 245 |
| `IRON_INGOT` | 129 |
| `LLAMA_SPAWN_EGG` | 144 |
| `MAGMA_CUBE_SPAWN_EGG` | 199 |
| `MOOSHROOM_SPAWN_EGG` | 82 |
| `MULE_SPAWN_EGG` | 132 |
| `NAUTILUS_SPAWN_EGG` | 136 |
| `NETHERITE_INGOT` | 242 |
| `NETHERITE_SCRAP` | 240 |
| `NETHER_STAR` | 247 |
| `OCELOT_SPAWN_EGG` | 89 |
| `PANDA_SPAWN_EGG` | 173 |
| `PARCHED_SPAWN_EGG` | 204 |
| `PARROT_SPAWN_EGG` | 90 |
| `PHANTOM_SPAWN_EGG` | 205 |
| `PIGLIN_BRUTE_SPAWN_EGG` | 234 |
| `PIGLIN_SPAWN_EGG` | 215 |
| `PIG_SPAWN_EGG` | 79 |
| `PILLAGER_SPAWN_EGG` | 211 |
| `POLAR_BEAR_SPAWN_EGG` | 225 |
| `PUFFERFISH_SPAWN_EGG` | 179 |
| `PotionType.AWKWARD` | 4 |
| `PotionType.FIRE_RESISTANCE` | 163 |
| `PotionType.HARMING` | 16 |
| `PotionType.HEALING` | 165 |
| `PotionType.INFESTED` | 22 |
| `PotionType.INVISIBILITY` | 158 |
| `PotionType.LEAPING` | 119 |
| `PotionType.LONG_FIRE_RESISTANCE` | 164 |
| `PotionType.LONG_INVISIBILITY` | 159 |
| `PotionType.LONG_LEAPING` | 120 |
| `PotionType.LONG_NIGHT_VISION` | 118 |
| `PotionType.LONG_POISON` | 14 |
| `PotionType.LONG_REGENERATION` | 168 |
| `PotionType.LONG_SLOWNESS` | 21 |
| `PotionType.LONG_SLOW_FALLING` | 125 |
| `PotionType.LONG_STRENGTH` | 171 |
| `PotionType.LONG_SWIFTNESS` | 156 |
| `PotionType.LONG_TURTLE_MASTER` | 161 |
| `PotionType.LONG_WATER_BREATHING` | 123 |
| `PotionType.LONG_WEAKNESS` | 18 |
| `PotionType.LUCK` | 28 |
| `PotionType.NIGHT_VISION` | 117 |
| `PotionType.OOZING` | 23 |
| `PotionType.POISON` | 13 |
| `PotionType.REGENERATION` | 167 |
| `PotionType.SLOWNESS` | 20 |
| `PotionType.SLOW_FALLING` | 124 |
| `PotionType.STRENGTH` | 170 |
| `PotionType.STRONG_HARMING` | 15 |
| `PotionType.STRONG_HEALING` | 166 |
| `PotionType.STRONG_LEAPING` | 121 |
| `PotionType.STRONG_POISON` | 12 |
| `PotionType.STRONG_REGENERATION` | 169 |
| `PotionType.STRONG_SLOWNESS` | 19 |
| `PotionType.STRONG_STRENGTH` | 172 |
| `PotionType.STRONG_SWIFTNESS` | 157 |
| `PotionType.STRONG_TURTLE_MASTER` | 162 |
| `PotionType.SWIFTNESS` | 155 |
| `PotionType.THICK` | 3 |
| `PotionType.TURTLE_MASTER` | 160 |
| `PotionType.WATER_BREATHING` | 122 |
| `PotionType.WEAKNESS` | 17 |
| `PotionType.WEAVING` | 24 |
| `PotionType.WIND_CHARGED` | 25 |
| `RABBIT_SPAWN_EGG` | 78 |
| `RAISER_ARMOR_TRIM_SMITHING_TEMPLATE` | 101 |
| `RIB_ARMOR_TRIM_SMITHING_TEMPLATE` | 105 |
| `SALMON_SPAWN_EGG` | 8 |
| `SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE` | 96 |
| `SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE` | 102 |
| `SHEEP_SPAWN_EGG` | 80 |
| `SHULKER_SPAWN_EGG` | 221 |
| `SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE` | 113 |
| `SKELETON_HORSE_SPAWN_EGG` | 137 |
| `SKELETON_SPAWN_EGG` | 201 |
| `SLIME_SPAWN_EGG` | 198 |
| `SNIFFER_SPAWN_EGG` | 84 |
| `SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE` | 104 |
| `SNOW_GOLEM_SPAWN_EGG` | 94 |
| `SPIDER_SPAWN_EGG` | 196 |
| `SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE` | 112 |
| `SQUID_SPAWN_EGG` | 11 |
| `STRAY_SPAWN_EGG` | 202 |
| `STRIDER_SPAWN_EGG` | 135 |
| `SULFUR_CUBE_SPAWN_EGG` | 93 |
| `TADPOLE_SPAWN_EGG` | 5 |
| `TIDE_ARMOR_TRIM_SMITHING_TEMPLATE` | 106 |
| `TRADER_LLAMA_SPAWN_EGG` | 145 |
| `TROPICAL_FISH_SPAWN_EGG` | 9 |
| `TURTLE_SPAWN_EGG` | 85 |
| `VEX_ARMOR_TRIM_SMITHING_TEMPLATE` | 107 |
| `VEX_SPAWN_EGG` | 214 |
| `VILLAGER_SPAWN_EGG` | 147 |
| `VINDICATOR_SPAWN_EGG` | 212 |
| `WANDERING_TRADER_SPAWN_EGG` | 146 |
| `WARDEN_SPAWN_EGG` | 246 |
| `WARD_ARMOR_TRIM_SMITHING_TEMPLATE` | 111 |
| `WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE` | 100 |
| `WILD_ARMOR_TRIM_SMITHING_TEMPLATE` | 99 |
| `WITCH_SPAWN_EGG` | 213 |
| `WITHER_SKELETON_SPAWN_EGG` | 217 |
| `WOLF_SPAWN_EGG` | 175 |
| `ZOGLIN_SPAWN_EGG` | 224 |
| `ZOMBIE_HORSE_SPAWN_EGG` | 138 |
| `ZOMBIE_NAUTILUS_SPAWN_EGG` | 229 |
| `ZOMBIE_SPAWN_EGG` | 191 |
| `ZOMBIE_VILLAGER_SPAWN_EGG` | 192 |
| `ZOMBIFIED_PIGLIN_SPAWN_EGG` | 195 |
| `arrow:plain` | 64 |
| `arrow:spectral` | 65 |
| `arrow:tipped_awkward` | 31 |
| `arrow:tipped_fire_resistance` | 46 |
| `arrow:tipped_harming` | 75 |
| `arrow:tipped_healing` | 32 |
| `arrow:tipped_infested` | 60 |
| `arrow:tipped_invisibility` | 52 |
| `arrow:tipped_leaping` | 43 |
| `arrow:tipped_long_fire_resistance` | 47 |
| `arrow:tipped_long_invisibility` | 53 |
| `arrow:tipped_long_leaping` | 44 |
| `arrow:tipped_long_night_vision` | 49 |
| `arrow:tipped_long_poison` | 73 |
| `arrow:tipped_long_regeneration` | 35 |
| `arrow:tipped_long_slow_falling` | 55 |
| `arrow:tipped_long_slowness` | 70 |
| `arrow:tipped_long_strength` | 38 |
| `arrow:tipped_long_swiftness` | 41 |
| `arrow:tipped_long_turtle_master` | 58 |
| `arrow:tipped_long_water_breathing` | 51 |
| `arrow:tipped_long_weakness` | 68 |
| `arrow:tipped_luck` | 56 |
| `arrow:tipped_night_vision` | 48 |
| `arrow:tipped_oozing` | 61 |
| `arrow:tipped_poison` | 72 |
| `arrow:tipped_regeneration` | 34 |
| `arrow:tipped_slow_falling` | 54 |
| `arrow:tipped_slowness` | 69 |
| `arrow:tipped_strength` | 37 |
| `arrow:tipped_strong_harming` | 76 |
| `arrow:tipped_strong_healing` | 33 |
| `arrow:tipped_strong_leaping` | 45 |
| `arrow:tipped_strong_poison` | 74 |
| `arrow:tipped_strong_regeneration` | 36 |
| `arrow:tipped_strong_slowness` | 71 |
| `arrow:tipped_strong_strength` | 39 |
| `arrow:tipped_strong_swiftness` | 42 |
| `arrow:tipped_strong_turtle_master` | 59 |
| `arrow:tipped_swiftness` | 40 |
| `arrow:tipped_thick` | 30 |
| `arrow:tipped_turtle_master` | 57 |
| `arrow:tipped_water_breathing` | 50 |
| `arrow:tipped_weakness` | 67 |
| `arrow:tipped_weaving` | 62 |
| `arrow:tipped_wind_charged` | 63 |
| `rootmc:free_lotto_ticket` | 29 |
---

## Plugin / command snippets

```text
/give @p minecraft:arrow 16
/give @p minecraft:spectral_arrow 16
/give @p minecraft:tipped_arrow[potion_contents={potion:strong_harming}] 16
/give @p minecraft:tipped_arrow[potion_contents={potion:poison}] 16
/give @p minecraft:ender_pearl 16
/give @p minecraft:coal 16
/give @p minecraft:coal_block 16
/give @p minecraft:glowstone 8
/give @p minecraft:iron_ingot 16
/give @p minecraft:diamond 16
/give @p minecraft:netherite_scrap 1
/give @p minecraft:netherite_ingot 1
/give @p minecraft:nether_star 1
# Free Lotto Ticket: RootMC item -- redeem grants +1 Root-Gamble lotto free-play credit
# (same as first unused free), then player runs /lotto <number>
```

```java
ItemStack arrows = new ItemStack(Material.ARROW, 16);
ItemStack tipped = new ItemStack(Material.TIPPED_ARROW, 16);
PotionMeta meta = (PotionMeta) tipped.getItemMeta();
meta.setBasePotionType(PotionType.STRONG_HARMING);
tipped.setItemMeta(meta);
```

Arrow coverage: `ARROW` + `SPECTRAL_ARROW` + tipped for all 46 `PotionType` values (48 stacks x16).

---

*Document generated for RootMC workspace -- Minecraft / Paper API line 26.2.*



