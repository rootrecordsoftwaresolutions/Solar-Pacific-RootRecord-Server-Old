# Appreciation token redemption odds

Draft loot table for **right-click redemption** of appreciation tokens.

Source: `minecraft-26.2-spawn-eggs-power-ranking.md` (rank 1 = least powerful = most common).

## Formula

- Entries: **247**
- Weight curve: `(248 - rank)^1.2`
- No minimum floor -- higher power rank is always rarer (or tied only by rounding)
- Stored as integer **weight** units that sum to **1,000,000** (each unit = 0.0001%)
- Percents sum to **exactly 100%**

## Band summary

| Power band (rank) | Share of pulls | Role |
| --- | ---: | --- |
| 1-50 | **39.13%** | Junk / niche / weak |
| 51-100 | **28.84%** | Low utility |
| 101-150 | **19.14%** | Mid |
| 151-200 | **10.23%** | Strong |
| 201-247 | **2.65%** | Apex / prestige |

- Most common: **Enchanted book (Binding Curse)** -- 0.8867% (1 in 113)
- Rarest: **Nether Star** -- 0.0012% (1 in 83333)
- Expected power rank per pull: **77.8** / 247

## Full table

| Rank | Entry | Qty | Id | Weight | Probability % | Approx 1 in |
| ---: | --- | ---: | --- | ---: | ---: | ---: |
| 1 | Enchanted book (Binding Curse) | 1 | `Enchantment.BINDING_CURSE` | 8867 | 0.8867 | 113 |
| 2 | Enchanted book (Vanishing Curse) | 1 | `Enchantment.VANISHING_CURSE` | 8824 | 0.8824 | 113 |
| 3 | Potion (Thick) | 1 | `PotionType.THICK` | 8781 | 0.8781 | 114 |
| 4 | Potion (Awkward) | 1 | `PotionType.AWKWARD` | 8738 | 0.8738 | 114 |
| 5 | Tadpole | 1 | `TADPOLE_SPAWN_EGG` | 8695 | 0.8695 | 115 |
| 6 | Bat | 1 | `BAT_SPAWN_EGG` | 8652 | 0.8652 | 116 |
| 7 | Cod | 1 | `COD_SPAWN_EGG` | 8610 | 0.8610 | 116 |
| 8 | Salmon | 1 | `SALMON_SPAWN_EGG` | 8567 | 0.8567 | 117 |
| 9 | Tropical fish | 1 | `TROPICAL_FISH_SPAWN_EGG` | 8524 | 0.8524 | 117 |
| 10 | Glow squid | 1 | `GLOW_SQUID_SPAWN_EGG` | 8481 | 0.8481 | 118 |
| 11 | Squid | 1 | `SQUID_SPAWN_EGG` | 8438 | 0.8438 | 119 |
| 12 | Potion (Poison II) | 1 | `PotionType.STRONG_POISON` | 8396 | 0.8396 | 119 |
| 13 | Potion (Poison) | 1 | `PotionType.POISON` | 8353 | 0.8353 | 120 |
| 14 | Potion (Poison (long)) | 1 | `PotionType.LONG_POISON` | 8310 | 0.8310 | 120 |
| 15 | Potion (Harming II) | 1 | `PotionType.STRONG_HARMING` | 8268 | 0.8268 | 121 |
| 16 | Potion (Harming) | 1 | `PotionType.HARMING` | 8225 | 0.8225 | 122 |
| 17 | Potion (Weakness) | 1 | `PotionType.WEAKNESS` | 8183 | 0.8183 | 122 |
| 18 | Potion (Weakness (long)) | 1 | `PotionType.LONG_WEAKNESS` | 8140 | 0.8140 | 123 |
| 19 | Potion (Slowness IV) | 1 | `PotionType.STRONG_SLOWNESS` | 8098 | 0.8098 | 123 |
| 20 | Potion (Slowness) | 1 | `PotionType.SLOWNESS` | 8055 | 0.8055 | 124 |
| 21 | Potion (Slowness (long)) | 1 | `PotionType.LONG_SLOWNESS` | 8013 | 0.8013 | 125 |
| 22 | Potion (Infested) | 1 | `PotionType.INFESTED` | 7971 | 0.7971 | 125 |
| 23 | Potion (Oozing) | 1 | `PotionType.OOZING` | 7928 | 0.7928 | 126 |
| 24 | Potion (Weaving) | 1 | `PotionType.WEAVING` | 7886 | 0.7886 | 127 |
| 25 | Potion (Wind Charged) | 1 | `PotionType.WIND_CHARGED` | 7844 | 0.7844 | 127 |
| 26 | Enchanted book (Luck of the Sea) | 1 | `Enchantment.LUCK_OF_THE_SEA` | 7802 | 0.7802 | 128 |
| 27 | Enchanted book (Lure) | 1 | `Enchantment.LURE` | 7759 | 0.7759 | 129 |
| 28 | Potion (Luck) | 1 | `PotionType.LUCK` | 7717 | 0.7717 | 130 |
| 29 | Free Lotto Ticket | 1 | `Free Lotto Ticket` | 7675 | 0.7675 | 130 |
| 30 | Tipped Arrow (Thick) x16 | 16 | `TIPPED_ARROW` | 7633 | 0.7633 | 131 |
| 31 | Tipped Arrow (Awkward) x16 | 16 | `TIPPED_ARROW` | 7591 | 0.7591 | 132 |
| 32 | Tipped Arrow (Healing) x16 | 16 | `TIPPED_ARROW` | 7549 | 0.7549 | 132 |
| 33 | Tipped Arrow (Healing II) x16 | 16 | `TIPPED_ARROW` | 7507 | 0.7507 | 133 |
| 34 | Tipped Arrow (Regeneration) x16 | 16 | `TIPPED_ARROW` | 7465 | 0.7465 | 134 |
| 35 | Tipped Arrow (Regeneration long) x16 | 16 | `TIPPED_ARROW` | 7424 | 0.7424 | 135 |
| 36 | Tipped Arrow (Regeneration II) x16 | 16 | `TIPPED_ARROW` | 7382 | 0.7382 | 135 |
| 37 | Tipped Arrow (Strength) x16 | 16 | `TIPPED_ARROW` | 7340 | 0.7340 | 136 |
| 38 | Tipped Arrow (Strength long) x16 | 16 | `TIPPED_ARROW` | 7298 | 0.7298 | 137 |
| 39 | Tipped Arrow (Strength II) x16 | 16 | `TIPPED_ARROW` | 7257 | 0.7257 | 138 |
| 40 | Tipped Arrow (Swiftness) x16 | 16 | `TIPPED_ARROW` | 7215 | 0.7215 | 139 |
| 41 | Tipped Arrow (Swiftness long) x16 | 16 | `TIPPED_ARROW` | 7173 | 0.7173 | 139 |
| 42 | Tipped Arrow (Swiftness II) x16 | 16 | `TIPPED_ARROW` | 7132 | 0.7132 | 140 |
| 43 | Tipped Arrow (Leaping) x16 | 16 | `TIPPED_ARROW` | 7090 | 0.7090 | 141 |
| 44 | Tipped Arrow (Leaping long) x16 | 16 | `TIPPED_ARROW` | 7049 | 0.7049 | 142 |
| 45 | Tipped Arrow (Leaping II) x16 | 16 | `TIPPED_ARROW` | 7007 | 0.7007 | 143 |
| 46 | Tipped Arrow (Fire Resistance) x16 | 16 | `TIPPED_ARROW` | 6966 | 0.6966 | 144 |
| 47 | Tipped Arrow (Fire Resistance long) x16 | 16 | `TIPPED_ARROW` | 6925 | 0.6925 | 144 |
| 48 | Tipped Arrow (Night Vision) x16 | 16 | `TIPPED_ARROW` | 6883 | 0.6883 | 145 |
| 49 | Tipped Arrow (Night Vision long) x16 | 16 | `TIPPED_ARROW` | 6842 | 0.6842 | 146 |
| 50 | Tipped Arrow (Water Breathing) x16 | 16 | `TIPPED_ARROW` | 6801 | 0.6801 | 147 |
| 51 | Tipped Arrow (Water Breathing long) x16 | 16 | `TIPPED_ARROW` | 6760 | 0.6760 | 148 |
| 52 | Tipped Arrow (Invisibility) x16 | 16 | `TIPPED_ARROW` | 6718 | 0.6718 | 149 |
| 53 | Tipped Arrow (Invisibility long) x16 | 16 | `TIPPED_ARROW` | 6677 | 0.6677 | 150 |
| 54 | Tipped Arrow (Slow Falling) x16 | 16 | `TIPPED_ARROW` | 6636 | 0.6636 | 151 |
| 55 | Tipped Arrow (Slow Falling long) x16 | 16 | `TIPPED_ARROW` | 6595 | 0.6595 | 152 |
| 56 | Tipped Arrow (Luck) x16 | 16 | `TIPPED_ARROW` | 6554 | 0.6554 | 153 |
| 57 | Tipped Arrow (Turtle Master) x16 | 16 | `TIPPED_ARROW` | 6513 | 0.6513 | 154 |
| 58 | Tipped Arrow (Turtle Master long) x16 | 16 | `TIPPED_ARROW` | 6472 | 0.6472 | 155 |
| 59 | Tipped Arrow (Turtle Master II) x16 | 16 | `TIPPED_ARROW` | 6432 | 0.6432 | 155 |
| 60 | Tipped Arrow (Infested) x16 | 16 | `TIPPED_ARROW` | 6391 | 0.6391 | 156 |
| 61 | Tipped Arrow (Oozing) x16 | 16 | `TIPPED_ARROW` | 6350 | 0.6350 | 157 |
| 62 | Tipped Arrow (Weaving) x16 | 16 | `TIPPED_ARROW` | 6309 | 0.6309 | 159 |
| 63 | Tipped Arrow (Wind Charged) x16 | 16 | `TIPPED_ARROW` | 6269 | 0.6269 | 160 |
| 64 | Arrow x16 | 16 | `ARROW` | 6228 | 0.6228 | 161 |
| 65 | Spectral Arrow x16 | 16 | `SPECTRAL_ARROW` | 6187 | 0.6187 | 162 |
| 66 | Ender Pearl x16 | 16 | `ENDER_PEARL` | 6147 | 0.6147 | 163 |
| 67 | Tipped Arrow (Weakness) x16 | 16 | `TIPPED_ARROW` | 6106 | 0.6106 | 164 |
| 68 | Tipped Arrow (Weakness long) x16 | 16 | `TIPPED_ARROW` | 6066 | 0.6066 | 165 |
| 69 | Tipped Arrow (Slowness) x16 | 16 | `TIPPED_ARROW` | 6025 | 0.6025 | 166 |
| 70 | Tipped Arrow (Slowness long) x16 | 16 | `TIPPED_ARROW` | 5985 | 0.5985 | 167 |
| 71 | Tipped Arrow (Slowness IV) x16 | 16 | `TIPPED_ARROW` | 5945 | 0.5945 | 168 |
| 72 | Tipped Arrow (Poison) x16 | 16 | `TIPPED_ARROW` | 5904 | 0.5904 | 169 |
| 73 | Tipped Arrow (Poison long) x16 | 16 | `TIPPED_ARROW` | 5864 | 0.5864 | 171 |
| 74 | Tipped Arrow (Poison II) x16 | 16 | `TIPPED_ARROW` | 5824 | 0.5824 | 172 |
| 75 | Tipped Arrow (Harming) x16 | 16 | `TIPPED_ARROW` | 5784 | 0.5784 | 173 |
| 76 | Tipped Arrow (Harming II) x16 | 16 | `TIPPED_ARROW` | 5744 | 0.5744 | 174 |
| 77 | Chicken | 1 | `CHICKEN_SPAWN_EGG` | 5704 | 0.5704 | 175 |
| 78 | Rabbit | 1 | `RABBIT_SPAWN_EGG` | 5664 | 0.5664 | 177 |
| 79 | Pig | 1 | `PIG_SPAWN_EGG` | 5624 | 0.5624 | 178 |
| 80 | Sheep | 1 | `SHEEP_SPAWN_EGG` | 5584 | 0.5584 | 179 |
| 81 | Cow | 1 | `COW_SPAWN_EGG` | 5544 | 0.5544 | 180 |
| 82 | Mooshroom | 1 | `MOOSHROOM_SPAWN_EGG` | 5504 | 0.5504 | 182 |
| 83 | Armadillo | 1 | `ARMADILLO_SPAWN_EGG` | 5464 | 0.5464 | 183 |
| 84 | Sniffer | 1 | `SNIFFER_SPAWN_EGG` | 5425 | 0.5425 | 184 |
| 85 | Turtle | 1 | `TURTLE_SPAWN_EGG` | 5385 | 0.5385 | 186 |
| 86 | Frog | 1 | `FROG_SPAWN_EGG` | 5345 | 0.5345 | 187 |
| 87 | Enchanted book (Bane of Arthropods) | 1 | `Enchantment.BANE_OF_ARTHROPODS` | 5306 | 0.5306 | 188 |
| 88 | Cat | 1 | `CAT_SPAWN_EGG` | 5266 | 0.5266 | 190 |
| 89 | Ocelot | 1 | `OCELOT_SPAWN_EGG` | 5227 | 0.5227 | 191 |
| 90 | Parrot | 1 | `PARROT_SPAWN_EGG` | 5187 | 0.5187 | 193 |
| 91 | Fox | 1 | `FOX_SPAWN_EGG` | 5148 | 0.5148 | 194 |
| 92 | Allay | 1 | `ALLAY_SPAWN_EGG` | 5109 | 0.5109 | 196 |
| 93 | Sulfur cube | 1 | `SULFUR_CUBE_SPAWN_EGG` | 5069 | 0.5069 | 197 |
| 94 | Snow golem | 1 | `SNOW_GOLEM_SPAWN_EGG` | 5030 | 0.5030 | 199 |
| 95 | Copper golem | 1 | `COPPER_GOLEM_SPAWN_EGG` | 4991 | 0.4991 | 200 |
| 96 | Sentry Armor Trim | 1 | `SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE` | 4952 | 0.4952 | 202 |
| 97 | Dune Armor Trim | 1 | `DUNE_ARMOR_TRIM_SMITHING_TEMPLATE` | 4913 | 0.4913 | 204 |
| 98 | Coast Armor Trim | 1 | `COAST_ARMOR_TRIM_SMITHING_TEMPLATE` | 4874 | 0.4874 | 205 |
| 99 | Wild Armor Trim | 1 | `WILD_ARMOR_TRIM_SMITHING_TEMPLATE` | 4835 | 0.4835 | 207 |
| 100 | Wayfinder Armor Trim | 1 | `WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE` | 4796 | 0.4796 | 209 |
| 101 | Raiser Armor Trim | 1 | `RAISER_ARMOR_TRIM_SMITHING_TEMPLATE` | 4757 | 0.4757 | 210 |
| 102 | Shaper Armor Trim | 1 | `SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE` | 4718 | 0.4718 | 212 |
| 103 | Host Armor Trim | 1 | `HOST_ARMOR_TRIM_SMITHING_TEMPLATE` | 4680 | 0.4680 | 214 |
| 104 | Snout Armor Trim | 1 | `SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE` | 4641 | 0.4641 | 215 |
| 105 | Rib Armor Trim | 1 | `RIB_ARMOR_TRIM_SMITHING_TEMPLATE` | 4602 | 0.4602 | 217 |
| 106 | Tide Armor Trim | 1 | `TIDE_ARMOR_TRIM_SMITHING_TEMPLATE` | 4564 | 0.4564 | 219 |
| 107 | Vex Armor Trim | 1 | `VEX_ARMOR_TRIM_SMITHING_TEMPLATE` | 4525 | 0.4525 | 221 |
| 108 | Eye Armor Trim | 1 | `EYE_ARMOR_TRIM_SMITHING_TEMPLATE` | 4487 | 0.4487 | 223 |
| 109 | Bolt Armor Trim | 1 | `BOLT_ARMOR_TRIM_SMITHING_TEMPLATE` | 4448 | 0.4448 | 225 |
| 110 | Flow Armor Trim | 1 | `FLOW_ARMOR_TRIM_SMITHING_TEMPLATE` | 4410 | 0.4410 | 227 |
| 111 | Ward Armor Trim | 1 | `WARD_ARMOR_TRIM_SMITHING_TEMPLATE` | 4371 | 0.4371 | 229 |
| 112 | Spire Armor Trim | 1 | `SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE` | 4333 | 0.4333 | 231 |
| 113 | Silence Armor Trim | 1 | `SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE` | 4295 | 0.4295 | 233 |
| 114 | Enchanted book (Punch) | 1 | `Enchantment.PUNCH` | 4257 | 0.4257 | 235 |
| 115 | Enchanted book (Knockback) | 1 | `Enchantment.KNOCKBACK` | 4219 | 0.4219 | 237 |
| 116 | Enchanted book (Aqua Affinity) | 1 | `Enchantment.AQUA_AFFINITY` | 4181 | 0.4181 | 239 |
| 117 | Potion (Night Vision) | 1 | `PotionType.NIGHT_VISION` | 4143 | 0.4143 | 241 |
| 118 | Potion (Night Vision (long)) | 1 | `PotionType.LONG_NIGHT_VISION` | 4105 | 0.4105 | 244 |
| 119 | Potion (Leaping) | 1 | `PotionType.LEAPING` | 4067 | 0.4067 | 246 |
| 120 | Potion (Leaping (long)) | 1 | `PotionType.LONG_LEAPING` | 4029 | 0.4029 | 248 |
| 121 | Potion (Leaping II) | 1 | `PotionType.STRONG_LEAPING` | 3991 | 0.3991 | 251 |
| 122 | Potion (Water Breathing) | 1 | `PotionType.WATER_BREATHING` | 3954 | 0.3954 | 253 |
| 123 | Potion (Water Breathing (long)) | 1 | `PotionType.LONG_WATER_BREATHING` | 3916 | 0.3916 | 255 |
| 124 | Potion (Slow Falling) | 1 | `PotionType.SLOW_FALLING` | 3879 | 0.3879 | 258 |
| 125 | Potion (Slow Falling (long)) | 1 | `PotionType.LONG_SLOW_FALLING` | 3841 | 0.3841 | 260 |
| 126 | Coal x16 | 16 | `COAL` | 3804 | 0.3804 | 263 |
| 127 | Coal Block x16 | 16 | `COAL_BLOCK` | 3766 | 0.3766 | 266 |
| 128 | Glowstone x8 | 8 | `GLOWSTONE` | 3729 | 0.3729 | 268 |
| 129 | Iron Ingot x16 | 16 | `IRON_INGOT` | 3692 | 0.3692 | 271 |
| 130 | Iron block | 1 | `IRON_BLOCK` | 3654 | 0.3654 | 274 |
| 131 | Donkey | 1 | `DONKEY_SPAWN_EGG` | 3617 | 0.3617 | 276 |
| 132 | Mule | 1 | `MULE_SPAWN_EGG` | 3580 | 0.3580 | 279 |
| 133 | Horse | 1 | `HORSE_SPAWN_EGG` | 3543 | 0.3543 | 282 |
| 134 | Camel | 1 | `CAMEL_SPAWN_EGG` | 3506 | 0.3506 | 285 |
| 135 | Strider | 1 | `STRIDER_SPAWN_EGG` | 3469 | 0.3469 | 288 |
| 136 | Nautilus | 1 | `NAUTILUS_SPAWN_EGG` | 3433 | 0.3433 | 291 |
| 137 | Skeleton horse | 1 | `SKELETON_HORSE_SPAWN_EGG` | 3396 | 0.3396 | 294 |
| 138 | Zombie horse | 1 | `ZOMBIE_HORSE_SPAWN_EGG` | 3359 | 0.3359 | 298 |
| 139 | Happy ghast | 1 | `HAPPY_GHAST_SPAWN_EGG` | 3323 | 0.3323 | 301 |
| 140 | Enchanted book (Frost Walker) | 1 | `Enchantment.FROST_WALKER` | 3286 | 0.3286 | 304 |
| 141 | Enchanted book (Soul Speed) | 1 | `Enchantment.SOUL_SPEED` | 3250 | 0.3250 | 308 |
| 142 | Enchanted book (Flame) | 1 | `Enchantment.FLAME` | 3213 | 0.3213 | 311 |
| 143 | Enchanted book (Fire Aspect) | 1 | `Enchantment.FIRE_ASPECT` | 3177 | 0.3177 | 315 |
| 144 | Llama | 1 | `LLAMA_SPAWN_EGG` | 3140 | 0.3140 | 318 |
| 145 | Trader llama | 1 | `TRADER_LLAMA_SPAWN_EGG` | 3104 | 0.3104 | 322 |
| 146 | Wandering trader | 1 | `WANDERING_TRADER_SPAWN_EGG` | 3068 | 0.3068 | 326 |
| 147 | Villager | 1 | `VILLAGER_SPAWN_EGG` | 3032 | 0.3032 | 330 |
| 148 | Emerald block | 1 | `EMERALD_BLOCK` | 2996 | 0.2996 | 334 |
| 149 | Enchanted book (Respiration) | 1 | `Enchantment.RESPIRATION` | 2960 | 0.2960 | 338 |
| 150 | Enchanted book (Swift Sneak) | 1 | `Enchantment.SWIFT_SNEAK` | 2924 | 0.2924 | 342 |
| 151 | Enchanted book (Depth Strider) | 1 | `Enchantment.DEPTH_STRIDER` | 2889 | 0.2889 | 346 |
| 152 | Enchanted book (Loyalty) | 1 | `Enchantment.LOYALTY` | 2853 | 0.2853 | 351 |
| 153 | Enchanted book (Lunge) | 1 | `Enchantment.LUNGE` | 2817 | 0.2817 | 355 |
| 154 | Enchanted book (Channeling) | 1 | `Enchantment.CHANNELING` | 2782 | 0.2782 | 359 |
| 155 | Potion (Swiftness) | 1 | `PotionType.SWIFTNESS` | 2746 | 0.2746 | 364 |
| 156 | Potion (Swiftness (long)) | 1 | `PotionType.LONG_SWIFTNESS` | 2711 | 0.2711 | 369 |
| 157 | Potion (Swiftness II) | 1 | `PotionType.STRONG_SWIFTNESS` | 2676 | 0.2676 | 374 |
| 158 | Potion (Invisibility) | 1 | `PotionType.INVISIBILITY` | 2640 | 0.2640 | 379 |
| 159 | Potion (Invisibility (long)) | 1 | `PotionType.LONG_INVISIBILITY` | 2605 | 0.2605 | 384 |
| 160 | Potion (Turtle Master) | 1 | `PotionType.TURTLE_MASTER` | 2570 | 0.2570 | 389 |
| 161 | Potion (Turtle Master (long)) | 1 | `PotionType.LONG_TURTLE_MASTER` | 2535 | 0.2535 | 394 |
| 162 | Potion (Turtle Master II) | 1 | `PotionType.STRONG_TURTLE_MASTER` | 2500 | 0.2500 | 400 |
| 163 | Potion (Fire Resistance) | 1 | `PotionType.FIRE_RESISTANCE` | 2465 | 0.2465 | 406 |
| 164 | Potion (Fire Resistance (long)) | 1 | `PotionType.LONG_FIRE_RESISTANCE` | 2430 | 0.2430 | 412 |
| 165 | Potion (Healing) | 1 | `PotionType.HEALING` | 2396 | 0.2396 | 417 |
| 166 | Potion (Healing II) | 1 | `PotionType.STRONG_HEALING` | 2361 | 0.2361 | 424 |
| 167 | Potion (Regeneration) | 1 | `PotionType.REGENERATION` | 2327 | 0.2327 | 430 |
| 168 | Potion (Regeneration (long)) | 1 | `PotionType.LONG_REGENERATION` | 2292 | 0.2292 | 436 |
| 169 | Potion (Regeneration II) | 1 | `PotionType.STRONG_REGENERATION` | 2258 | 0.2258 | 443 |
| 170 | Potion (Strength) | 1 | `PotionType.STRENGTH` | 2224 | 0.2224 | 450 |
| 171 | Potion (Strength (long)) | 1 | `PotionType.LONG_STRENGTH` | 2190 | 0.2190 | 457 |
| 172 | Potion (Strength II) | 1 | `PotionType.STRONG_STRENGTH` | 2155 | 0.2155 | 464 |
| 173 | Panda | 1 | `PANDA_SPAWN_EGG` | 2121 | 0.2121 | 471 |
| 174 | Goat | 1 | `GOAT_SPAWN_EGG` | 2088 | 0.2088 | 479 |
| 175 | Wolf | 1 | `WOLF_SPAWN_EGG` | 2054 | 0.2054 | 487 |
| 176 | Bee | 1 | `BEE_SPAWN_EGG` | 2020 | 0.2020 | 495 |
| 177 | Dolphin | 1 | `DOLPHIN_SPAWN_EGG` | 1986 | 0.1986 | 504 |
| 178 | Axolotl | 1 | `AXOLOTL_SPAWN_EGG` | 1953 | 0.1953 | 512 |
| 179 | Pufferfish | 1 | `PUFFERFISH_SPAWN_EGG` | 1919 | 0.1919 | 521 |
| 180 | Enchanted book (Projectile Protection) | 1 | `Enchantment.PROJECTILE_PROTECTION` | 1886 | 0.1886 | 530 |
| 181 | Enchanted book (Blast Protection) | 1 | `Enchantment.BLAST_PROTECTION` | 1853 | 0.1853 | 540 |
| 182 | Enchanted book (Fire Protection) | 1 | `Enchantment.FIRE_PROTECTION` | 1820 | 0.1820 | 549 |
| 183 | Enchanted book (Wind Burst) | 1 | `Enchantment.WIND_BURST` | 1787 | 0.1787 | 560 |
| 184 | Enchanted book (Density) | 1 | `Enchantment.DENSITY` | 1754 | 0.1754 | 570 |
| 185 | Enchanted book (Quick Charge) | 1 | `Enchantment.QUICK_CHARGE` | 1721 | 0.1721 | 581 |
| 186 | Enchanted book (Multishot) | 1 | `Enchantment.MULTISHOT` | 1688 | 0.1688 | 592 |
| 187 | Enchanted book (Piercing) | 1 | `Enchantment.PIERCING` | 1656 | 0.1656 | 604 |
| 188 | Enchanted book (Sweeping Edge) | 1 | `Enchantment.SWEEPING_EDGE` | 1623 | 0.1623 | 616 |
| 189 | Enchanted book (Impaling) | 1 | `Enchantment.IMPALING` | 1591 | 0.1591 | 629 |
| 190 | Endermite | 1 | `ENDERMITE_SPAWN_EGG` | 1558 | 0.1558 | 642 |
| 191 | Zombie | 1 | `ZOMBIE_SPAWN_EGG` | 1526 | 0.1526 | 655 |
| 192 | Zombie villager | 1 | `ZOMBIE_VILLAGER_SPAWN_EGG` | 1494 | 0.1494 | 669 |
| 193 | Husk | 1 | `HUSK_SPAWN_EGG` | 1462 | 0.1462 | 684 |
| 194 | Drowned | 1 | `DROWNED_SPAWN_EGG` | 1430 | 0.1430 | 699 |
| 195 | Zombified piglin | 1 | `ZOMBIFIED_PIGLIN_SPAWN_EGG` | 1399 | 0.1399 | 715 |
| 196 | Spider | 1 | `SPIDER_SPAWN_EGG` | 1367 | 0.1367 | 732 |
| 197 | Cave spider | 1 | `CAVE_SPIDER_SPAWN_EGG` | 1335 | 0.1335 | 749 |
| 198 | Slime | 1 | `SLIME_SPAWN_EGG` | 1304 | 0.1304 | 767 |
| 199 | Magma cube | 1 | `MAGMA_CUBE_SPAWN_EGG` | 1273 | 0.1273 | 786 |
| 200 | Enchanted book (Smite) | 1 | `Enchantment.SMITE` | 1242 | 0.1242 | 805 |
| 201 | Skeleton | 1 | `SKELETON_SPAWN_EGG` | 1211 | 0.1211 | 826 |
| 202 | Stray | 1 | `STRAY_SPAWN_EGG` | 1180 | 0.1180 | 847 |
| 203 | Bogged | 1 | `BOGGED_SPAWN_EGG` | 1149 | 0.1149 | 870 |
| 204 | Parched | 1 | `PARCHED_SPAWN_EGG` | 1119 | 0.1119 | 894 |
| 205 | Phantom | 1 | `PHANTOM_SPAWN_EGG` | 1088 | 0.1088 | 919 |
| 206 | Enchanted book (Thorns) | 1 | `Enchantment.THORNS` | 1058 | 0.1058 | 945 |
| 207 | Enchanted book (Riptide) | 1 | `Enchantment.RIPTIDE` | 1028 | 0.1028 | 973 |
| 208 | Enchanted book (Power) | 1 | `Enchantment.POWER` | 998 | 0.0998 | 1002 |
| 209 | Enchanted book (Breach) | 1 | `Enchantment.BREACH` | 968 | 0.0968 | 1033 |
| 210 | Guardian | 1 | `GUARDIAN_SPAWN_EGG` | 938 | 0.0938 | 1066 |
| 211 | Pillager | 1 | `PILLAGER_SPAWN_EGG` | 909 | 0.0909 | 1100 |
| 212 | Vindicator | 1 | `VINDICATOR_SPAWN_EGG` | 879 | 0.0879 | 1138 |
| 213 | Witch | 1 | `WITCH_SPAWN_EGG` | 850 | 0.0850 | 1176 |
| 214 | Vex | 1 | `VEX_SPAWN_EGG` | 821 | 0.0821 | 1218 |
| 215 | Piglin | 1 | `PIGLIN_SPAWN_EGG` | 792 | 0.0792 | 1263 |
| 216 | Blaze | 1 | `BLAZE_SPAWN_EGG` | 763 | 0.0763 | 1311 |
| 217 | Wither skeleton | 1 | `WITHER_SKELETON_SPAWN_EGG` | 735 | 0.0735 | 1361 |
| 218 | Enchanted book (Infinity) | 1 | `Enchantment.INFINITY` | 706 | 0.0706 | 1416 |
| 219 | Enchanted book (Silk Touch) | 1 | `Enchantment.SILK_TOUCH` | 678 | 0.0678 | 1475 |
| 220 | Creeper | 1 | `CREEPER_SPAWN_EGG` | 650 | 0.0650 | 1538 |
| 221 | Shulker | 1 | `SHULKER_SPAWN_EGG` | 623 | 0.0623 | 1605 |
| 222 | Enderman | 1 | `ENDERMAN_SPAWN_EGG` | 595 | 0.0595 | 1681 |
| 223 | Hoglin | 1 | `HOGLIN_SPAWN_EGG` | 568 | 0.0568 | 1761 |
| 224 | Zoglin | 1 | `ZOGLIN_SPAWN_EGG` | 541 | 0.0541 | 1848 |
| 225 | Polar bear | 1 | `POLAR_BEAR_SPAWN_EGG` | 514 | 0.0514 | 1946 |
| 226 | Enchanted book (Fortune) | 1 | `Enchantment.FORTUNE` | 487 | 0.0487 | 2053 |
| 227 | Enchanted book (Looting) | 1 | `Enchantment.LOOTING` | 460 | 0.0460 | 2174 |
| 228 | Camel husk | 1 | `CAMEL_HUSK_SPAWN_EGG` | 434 | 0.0434 | 2304 |
| 229 | Zombie nautilus | 1 | `ZOMBIE_NAUTILUS_SPAWN_EGG` | 408 | 0.0408 | 2451 |
| 230 | Breeze | 1 | `BREEZE_SPAWN_EGG` | 383 | 0.0383 | 2611 |
| 231 | Enchanted book (Efficiency) | 1 | `Enchantment.EFFICIENCY` | 357 | 0.0357 | 2801 |
| 232 | Enchanted book (Unbreaking) | 1 | `Enchantment.UNBREAKING` | 332 | 0.0332 | 3012 |
| 233 | Enchanted book (Feather Falling) | 1 | `Enchantment.FEATHER_FALLING` | 308 | 0.0308 | 3247 |
| 234 | Piglin brute | 1 | `PIGLIN_BRUTE_SPAWN_EGG` | 283 | 0.0283 | 3534 |
| 235 | Evoker | 1 | `EVOKER_SPAWN_EGG` | 259 | 0.0259 | 3861 |
| 236 | Elder guardian | 1 | `ELDER_GUARDIAN_SPAWN_EGG` | 235 | 0.0235 | 4255 |
| 237 | Creaking | 1 | `CREAKING_SPAWN_EGG` | 212 | 0.0212 | 4717 |
| 238 | Enchanted book (Protection) | 1 | `Enchantment.PROTECTION` | 189 | 0.0189 | 5291 |
| 239 | Enchanted book (Sharpness) | 1 | `Enchantment.SHARPNESS` | 167 | 0.0167 | 5988 |
| 240 | Netherite Scrap | 1 | `NETHERITE_SCRAP` | 145 | 0.0145 | 6897 |
| 241 | Diamond x16 | 16 | `DIAMOND` | 123 | 0.0123 | 8130 |
| 242 | Netherite Ingot | 1 | `NETHERITE_INGOT` | 102 | 0.0102 | 9804 |
| 243 | Diamond block | 1 | `DIAMOND_BLOCK` | 82 | 0.0082 | 12195 |
| 244 | Enchanted book (Mending) | 1 | `Enchantment.MENDING` | 63 | 0.0063 | 15873 |
| 245 | Iron golem | 1 | `IRON_GOLEM_SPAWN_EGG` | 45 | 0.0045 | 22222 |
| 246 | Warden | 1 | `WARDEN_SPAWN_EGG` | 27 | 0.0027 | 37037 |
| 247 | Nether Star | 1 | `NETHER_STAR` | 12 | 0.0012 | 83333 |

## Sorted by probability (most common first)

| Prob % | Weight | Rank | Entry | Approx 1 in |
| ---: | ---: | ---: | --- | ---: |
| 0.8867 | 8867 | 1 | Enchanted book (Binding Curse) | 113 |
| 0.8824 | 8824 | 2 | Enchanted book (Vanishing Curse) | 113 |
| 0.8781 | 8781 | 3 | Potion (Thick) | 114 |
| 0.8738 | 8738 | 4 | Potion (Awkward) | 114 |
| 0.8695 | 8695 | 5 | Tadpole | 115 |
| 0.8652 | 8652 | 6 | Bat | 116 |
| 0.8610 | 8610 | 7 | Cod | 116 |
| 0.8567 | 8567 | 8 | Salmon | 117 |
| 0.8524 | 8524 | 9 | Tropical fish | 117 |
| 0.8481 | 8481 | 10 | Glow squid | 118 |
| 0.8438 | 8438 | 11 | Squid | 119 |
| 0.8396 | 8396 | 12 | Potion (Poison II) | 119 |
| 0.8353 | 8353 | 13 | Potion (Poison) | 120 |
| 0.8310 | 8310 | 14 | Potion (Poison (long)) | 120 |
| 0.8268 | 8268 | 15 | Potion (Harming II) | 121 |
| 0.8225 | 8225 | 16 | Potion (Harming) | 122 |
| 0.8183 | 8183 | 17 | Potion (Weakness) | 122 |
| 0.8140 | 8140 | 18 | Potion (Weakness (long)) | 123 |
| 0.8098 | 8098 | 19 | Potion (Slowness IV) | 123 |
| 0.8055 | 8055 | 20 | Potion (Slowness) | 124 |
| 0.8013 | 8013 | 21 | Potion (Slowness (long)) | 125 |
| 0.7971 | 7971 | 22 | Potion (Infested) | 125 |
| 0.7928 | 7928 | 23 | Potion (Oozing) | 126 |
| 0.7886 | 7886 | 24 | Potion (Weaving) | 127 |
| 0.7844 | 7844 | 25 | Potion (Wind Charged) | 127 |
| 0.7802 | 7802 | 26 | Enchanted book (Luck of the Sea) | 128 |
| 0.7759 | 7759 | 27 | Enchanted book (Lure) | 129 |
| 0.7717 | 7717 | 28 | Potion (Luck) | 130 |
| 0.7675 | 7675 | 29 | Free Lotto Ticket | 130 |
| 0.7633 | 7633 | 30 | Tipped Arrow (Thick) x16 | 131 |
| 0.7591 | 7591 | 31 | Tipped Arrow (Awkward) x16 | 132 |
| 0.7549 | 7549 | 32 | Tipped Arrow (Healing) x16 | 132 |
| 0.7507 | 7507 | 33 | Tipped Arrow (Healing II) x16 | 133 |
| 0.7465 | 7465 | 34 | Tipped Arrow (Regeneration) x16 | 134 |
| 0.7424 | 7424 | 35 | Tipped Arrow (Regeneration long) x16 | 135 |
| 0.7382 | 7382 | 36 | Tipped Arrow (Regeneration II) x16 | 135 |
| 0.7340 | 7340 | 37 | Tipped Arrow (Strength) x16 | 136 |
| 0.7298 | 7298 | 38 | Tipped Arrow (Strength long) x16 | 137 |
| 0.7257 | 7257 | 39 | Tipped Arrow (Strength II) x16 | 138 |
| 0.7215 | 7215 | 40 | Tipped Arrow (Swiftness) x16 | 139 |
| 0.7173 | 7173 | 41 | Tipped Arrow (Swiftness long) x16 | 139 |
| 0.7132 | 7132 | 42 | Tipped Arrow (Swiftness II) x16 | 140 |
| 0.7090 | 7090 | 43 | Tipped Arrow (Leaping) x16 | 141 |
| 0.7049 | 7049 | 44 | Tipped Arrow (Leaping long) x16 | 142 |
| 0.7007 | 7007 | 45 | Tipped Arrow (Leaping II) x16 | 143 |
| 0.6966 | 6966 | 46 | Tipped Arrow (Fire Resistance) x16 | 144 |
| 0.6925 | 6925 | 47 | Tipped Arrow (Fire Resistance long) x16 | 144 |
| 0.6883 | 6883 | 48 | Tipped Arrow (Night Vision) x16 | 145 |
| 0.6842 | 6842 | 49 | Tipped Arrow (Night Vision long) x16 | 146 |
| 0.6801 | 6801 | 50 | Tipped Arrow (Water Breathing) x16 | 147 |
| 0.6760 | 6760 | 51 | Tipped Arrow (Water Breathing long) x16 | 148 |
| 0.6718 | 6718 | 52 | Tipped Arrow (Invisibility) x16 | 149 |
| 0.6677 | 6677 | 53 | Tipped Arrow (Invisibility long) x16 | 150 |
| 0.6636 | 6636 | 54 | Tipped Arrow (Slow Falling) x16 | 151 |
| 0.6595 | 6595 | 55 | Tipped Arrow (Slow Falling long) x16 | 152 |
| 0.6554 | 6554 | 56 | Tipped Arrow (Luck) x16 | 153 |
| 0.6513 | 6513 | 57 | Tipped Arrow (Turtle Master) x16 | 154 |
| 0.6472 | 6472 | 58 | Tipped Arrow (Turtle Master long) x16 | 155 |
| 0.6432 | 6432 | 59 | Tipped Arrow (Turtle Master II) x16 | 155 |
| 0.6391 | 6391 | 60 | Tipped Arrow (Infested) x16 | 156 |
| 0.6350 | 6350 | 61 | Tipped Arrow (Oozing) x16 | 157 |
| 0.6309 | 6309 | 62 | Tipped Arrow (Weaving) x16 | 159 |
| 0.6269 | 6269 | 63 | Tipped Arrow (Wind Charged) x16 | 160 |
| 0.6228 | 6228 | 64 | Arrow x16 | 161 |
| 0.6187 | 6187 | 65 | Spectral Arrow x16 | 162 |
| 0.6147 | 6147 | 66 | Ender Pearl x16 | 163 |
| 0.6106 | 6106 | 67 | Tipped Arrow (Weakness) x16 | 164 |
| 0.6066 | 6066 | 68 | Tipped Arrow (Weakness long) x16 | 165 |
| 0.6025 | 6025 | 69 | Tipped Arrow (Slowness) x16 | 166 |
| 0.5985 | 5985 | 70 | Tipped Arrow (Slowness long) x16 | 167 |
| 0.5945 | 5945 | 71 | Tipped Arrow (Slowness IV) x16 | 168 |
| 0.5904 | 5904 | 72 | Tipped Arrow (Poison) x16 | 169 |
| 0.5864 | 5864 | 73 | Tipped Arrow (Poison long) x16 | 171 |
| 0.5824 | 5824 | 74 | Tipped Arrow (Poison II) x16 | 172 |
| 0.5784 | 5784 | 75 | Tipped Arrow (Harming) x16 | 173 |
| 0.5744 | 5744 | 76 | Tipped Arrow (Harming II) x16 | 174 |
| 0.5704 | 5704 | 77 | Chicken | 175 |
| 0.5664 | 5664 | 78 | Rabbit | 177 |
| 0.5624 | 5624 | 79 | Pig | 178 |
| 0.5584 | 5584 | 80 | Sheep | 179 |
| 0.5544 | 5544 | 81 | Cow | 180 |
| 0.5504 | 5504 | 82 | Mooshroom | 182 |
| 0.5464 | 5464 | 83 | Armadillo | 183 |
| 0.5425 | 5425 | 84 | Sniffer | 184 |
| 0.5385 | 5385 | 85 | Turtle | 186 |
| 0.5345 | 5345 | 86 | Frog | 187 |
| 0.5306 | 5306 | 87 | Enchanted book (Bane of Arthropods) | 188 |
| 0.5266 | 5266 | 88 | Cat | 190 |
| 0.5227 | 5227 | 89 | Ocelot | 191 |
| 0.5187 | 5187 | 90 | Parrot | 193 |
| 0.5148 | 5148 | 91 | Fox | 194 |
| 0.5109 | 5109 | 92 | Allay | 196 |
| 0.5069 | 5069 | 93 | Sulfur cube | 197 |
| 0.5030 | 5030 | 94 | Snow golem | 199 |
| 0.4991 | 4991 | 95 | Copper golem | 200 |
| 0.4952 | 4952 | 96 | Sentry Armor Trim | 202 |
| 0.4913 | 4913 | 97 | Dune Armor Trim | 204 |
| 0.4874 | 4874 | 98 | Coast Armor Trim | 205 |
| 0.4835 | 4835 | 99 | Wild Armor Trim | 207 |
| 0.4796 | 4796 | 100 | Wayfinder Armor Trim | 209 |
| 0.4757 | 4757 | 101 | Raiser Armor Trim | 210 |
| 0.4718 | 4718 | 102 | Shaper Armor Trim | 212 |
| 0.4680 | 4680 | 103 | Host Armor Trim | 214 |
| 0.4641 | 4641 | 104 | Snout Armor Trim | 215 |
| 0.4602 | 4602 | 105 | Rib Armor Trim | 217 |
| 0.4564 | 4564 | 106 | Tide Armor Trim | 219 |
| 0.4525 | 4525 | 107 | Vex Armor Trim | 221 |
| 0.4487 | 4487 | 108 | Eye Armor Trim | 223 |
| 0.4448 | 4448 | 109 | Bolt Armor Trim | 225 |
| 0.4410 | 4410 | 110 | Flow Armor Trim | 227 |
| 0.4371 | 4371 | 111 | Ward Armor Trim | 229 |
| 0.4333 | 4333 | 112 | Spire Armor Trim | 231 |
| 0.4295 | 4295 | 113 | Silence Armor Trim | 233 |
| 0.4257 | 4257 | 114 | Enchanted book (Punch) | 235 |
| 0.4219 | 4219 | 115 | Enchanted book (Knockback) | 237 |
| 0.4181 | 4181 | 116 | Enchanted book (Aqua Affinity) | 239 |
| 0.4143 | 4143 | 117 | Potion (Night Vision) | 241 |
| 0.4105 | 4105 | 118 | Potion (Night Vision (long)) | 244 |
| 0.4067 | 4067 | 119 | Potion (Leaping) | 246 |
| 0.4029 | 4029 | 120 | Potion (Leaping (long)) | 248 |
| 0.3991 | 3991 | 121 | Potion (Leaping II) | 251 |
| 0.3954 | 3954 | 122 | Potion (Water Breathing) | 253 |
| 0.3916 | 3916 | 123 | Potion (Water Breathing (long)) | 255 |
| 0.3879 | 3879 | 124 | Potion (Slow Falling) | 258 |
| 0.3841 | 3841 | 125 | Potion (Slow Falling (long)) | 260 |
| 0.3804 | 3804 | 126 | Coal x16 | 263 |
| 0.3766 | 3766 | 127 | Coal Block x16 | 266 |
| 0.3729 | 3729 | 128 | Glowstone x8 | 268 |
| 0.3692 | 3692 | 129 | Iron Ingot x16 | 271 |
| 0.3654 | 3654 | 130 | Iron block | 274 |
| 0.3617 | 3617 | 131 | Donkey | 276 |
| 0.3580 | 3580 | 132 | Mule | 279 |
| 0.3543 | 3543 | 133 | Horse | 282 |
| 0.3506 | 3506 | 134 | Camel | 285 |
| 0.3469 | 3469 | 135 | Strider | 288 |
| 0.3433 | 3433 | 136 | Nautilus | 291 |
| 0.3396 | 3396 | 137 | Skeleton horse | 294 |
| 0.3359 | 3359 | 138 | Zombie horse | 298 |
| 0.3323 | 3323 | 139 | Happy ghast | 301 |
| 0.3286 | 3286 | 140 | Enchanted book (Frost Walker) | 304 |
| 0.3250 | 3250 | 141 | Enchanted book (Soul Speed) | 308 |
| 0.3213 | 3213 | 142 | Enchanted book (Flame) | 311 |
| 0.3177 | 3177 | 143 | Enchanted book (Fire Aspect) | 315 |
| 0.3140 | 3140 | 144 | Llama | 318 |
| 0.3104 | 3104 | 145 | Trader llama | 322 |
| 0.3068 | 3068 | 146 | Wandering trader | 326 |
| 0.3032 | 3032 | 147 | Villager | 330 |
| 0.2996 | 2996 | 148 | Emerald block | 334 |
| 0.2960 | 2960 | 149 | Enchanted book (Respiration) | 338 |
| 0.2924 | 2924 | 150 | Enchanted book (Swift Sneak) | 342 |
| 0.2889 | 2889 | 151 | Enchanted book (Depth Strider) | 346 |
| 0.2853 | 2853 | 152 | Enchanted book (Loyalty) | 351 |
| 0.2817 | 2817 | 153 | Enchanted book (Lunge) | 355 |
| 0.2782 | 2782 | 154 | Enchanted book (Channeling) | 359 |
| 0.2746 | 2746 | 155 | Potion (Swiftness) | 364 |
| 0.2711 | 2711 | 156 | Potion (Swiftness (long)) | 369 |
| 0.2676 | 2676 | 157 | Potion (Swiftness II) | 374 |
| 0.2640 | 2640 | 158 | Potion (Invisibility) | 379 |
| 0.2605 | 2605 | 159 | Potion (Invisibility (long)) | 384 |
| 0.2570 | 2570 | 160 | Potion (Turtle Master) | 389 |
| 0.2535 | 2535 | 161 | Potion (Turtle Master (long)) | 394 |
| 0.2500 | 2500 | 162 | Potion (Turtle Master II) | 400 |
| 0.2465 | 2465 | 163 | Potion (Fire Resistance) | 406 |
| 0.2430 | 2430 | 164 | Potion (Fire Resistance (long)) | 412 |
| 0.2396 | 2396 | 165 | Potion (Healing) | 417 |
| 0.2361 | 2361 | 166 | Potion (Healing II) | 424 |
| 0.2327 | 2327 | 167 | Potion (Regeneration) | 430 |
| 0.2292 | 2292 | 168 | Potion (Regeneration (long)) | 436 |
| 0.2258 | 2258 | 169 | Potion (Regeneration II) | 443 |
| 0.2224 | 2224 | 170 | Potion (Strength) | 450 |
| 0.2190 | 2190 | 171 | Potion (Strength (long)) | 457 |
| 0.2155 | 2155 | 172 | Potion (Strength II) | 464 |
| 0.2121 | 2121 | 173 | Panda | 471 |
| 0.2088 | 2088 | 174 | Goat | 479 |
| 0.2054 | 2054 | 175 | Wolf | 487 |
| 0.2020 | 2020 | 176 | Bee | 495 |
| 0.1986 | 1986 | 177 | Dolphin | 504 |
| 0.1953 | 1953 | 178 | Axolotl | 512 |
| 0.1919 | 1919 | 179 | Pufferfish | 521 |
| 0.1886 | 1886 | 180 | Enchanted book (Projectile Protection) | 530 |
| 0.1853 | 1853 | 181 | Enchanted book (Blast Protection) | 540 |
| 0.1820 | 1820 | 182 | Enchanted book (Fire Protection) | 549 |
| 0.1787 | 1787 | 183 | Enchanted book (Wind Burst) | 560 |
| 0.1754 | 1754 | 184 | Enchanted book (Density) | 570 |
| 0.1721 | 1721 | 185 | Enchanted book (Quick Charge) | 581 |
| 0.1688 | 1688 | 186 | Enchanted book (Multishot) | 592 |
| 0.1656 | 1656 | 187 | Enchanted book (Piercing) | 604 |
| 0.1623 | 1623 | 188 | Enchanted book (Sweeping Edge) | 616 |
| 0.1591 | 1591 | 189 | Enchanted book (Impaling) | 629 |
| 0.1558 | 1558 | 190 | Endermite | 642 |
| 0.1526 | 1526 | 191 | Zombie | 655 |
| 0.1494 | 1494 | 192 | Zombie villager | 669 |
| 0.1462 | 1462 | 193 | Husk | 684 |
| 0.1430 | 1430 | 194 | Drowned | 699 |
| 0.1399 | 1399 | 195 | Zombified piglin | 715 |
| 0.1367 | 1367 | 196 | Spider | 732 |
| 0.1335 | 1335 | 197 | Cave spider | 749 |
| 0.1304 | 1304 | 198 | Slime | 767 |
| 0.1273 | 1273 | 199 | Magma cube | 786 |
| 0.1242 | 1242 | 200 | Enchanted book (Smite) | 805 |
| 0.1211 | 1211 | 201 | Skeleton | 826 |
| 0.1180 | 1180 | 202 | Stray | 847 |
| 0.1149 | 1149 | 203 | Bogged | 870 |
| 0.1119 | 1119 | 204 | Parched | 894 |
| 0.1088 | 1088 | 205 | Phantom | 919 |
| 0.1058 | 1058 | 206 | Enchanted book (Thorns) | 945 |
| 0.1028 | 1028 | 207 | Enchanted book (Riptide) | 973 |
| 0.0998 | 998 | 208 | Enchanted book (Power) | 1002 |
| 0.0968 | 968 | 209 | Enchanted book (Breach) | 1033 |
| 0.0938 | 938 | 210 | Guardian | 1066 |
| 0.0909 | 909 | 211 | Pillager | 1100 |
| 0.0879 | 879 | 212 | Vindicator | 1138 |
| 0.0850 | 850 | 213 | Witch | 1176 |
| 0.0821 | 821 | 214 | Vex | 1218 |
| 0.0792 | 792 | 215 | Piglin | 1263 |
| 0.0763 | 763 | 216 | Blaze | 1311 |
| 0.0735 | 735 | 217 | Wither skeleton | 1361 |
| 0.0706 | 706 | 218 | Enchanted book (Infinity) | 1416 |
| 0.0678 | 678 | 219 | Enchanted book (Silk Touch) | 1475 |
| 0.0650 | 650 | 220 | Creeper | 1538 |
| 0.0623 | 623 | 221 | Shulker | 1605 |
| 0.0595 | 595 | 222 | Enderman | 1681 |
| 0.0568 | 568 | 223 | Hoglin | 1761 |
| 0.0541 | 541 | 224 | Zoglin | 1848 |
| 0.0514 | 514 | 225 | Polar bear | 1946 |
| 0.0487 | 487 | 226 | Enchanted book (Fortune) | 2053 |
| 0.0460 | 460 | 227 | Enchanted book (Looting) | 2174 |
| 0.0434 | 434 | 228 | Camel husk | 2304 |
| 0.0408 | 408 | 229 | Zombie nautilus | 2451 |
| 0.0383 | 383 | 230 | Breeze | 2611 |
| 0.0357 | 357 | 231 | Enchanted book (Efficiency) | 2801 |
| 0.0332 | 332 | 232 | Enchanted book (Unbreaking) | 3012 |
| 0.0308 | 308 | 233 | Enchanted book (Feather Falling) | 3247 |
| 0.0283 | 283 | 234 | Piglin brute | 3534 |
| 0.0259 | 259 | 235 | Evoker | 3861 |
| 0.0235 | 235 | 236 | Elder guardian | 4255 |
| 0.0212 | 212 | 237 | Creaking | 4717 |
| 0.0189 | 189 | 238 | Enchanted book (Protection) | 5291 |
| 0.0167 | 167 | 239 | Enchanted book (Sharpness) | 5988 |
| 0.0145 | 145 | 240 | Netherite Scrap | 6897 |
| 0.0123 | 123 | 241 | Diamond x16 | 8130 |
| 0.0102 | 102 | 242 | Netherite Ingot | 9804 |
| 0.0082 | 82 | 243 | Diamond block | 12195 |
| 0.0063 | 63 | 244 | Enchanted book (Mending) | 15873 |
| 0.0045 | 45 | 245 | Iron golem | 22222 |
| 0.0027 | 27 | 246 | Warden | 37037 |
| 0.0012 | 12 | 247 | Nether Star | 83333 |

## Implementation notes

- Roll `random(1..1000000)` and walk cumulative weights (or alias method).
- Free Lotto Ticket: grant Root-Gamble lotto free-play credit, do not give a fake Material.
- Tune GAMMA after playtest.

