package com.rootrecord.minecraft.rootbonds.gui;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.common.GoldMintHelper;
import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.SystemGoldPayout;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.data.BondsStore;
import com.rootrecord.minecraft.rootbonds.service.BondPrincipalResolver;
import com.rootrecord.minecraft.rootbonds.service.BondService;
import com.rootrecord.minecraft.rootbonds.towny.TownyLeadership;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BondsMenuRegistry {

    private static final String BONDS_INFO_URL = "https://rootmc.net/economy/bonds/";
    private static final ZoneId HST = ZoneId.of("Pacific/Honolulu");
    private static final DateTimeFormatter DEADLINE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).withZone(HST);
    private static final DateTimeFormatter NOTE_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.US).withZone(HST);

    private final RootBondsPlugin plugin;
    private final Map<UUID, BondsMenuHolder> open = new HashMap<>();
    private final Map<UUID, BondNoteMenuHolder> openNotes = new HashMap<>();
    private final Map<UUID, GovBondMenuHolder> openGov = new HashMap<>();

    public BondsMenuRegistry(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        BondService bonds = plugin.bonds();
        if (!bonds.enabled()) {
            player.sendMessage(BondsMenuRegistry.legacyColor(plugin.msg("disabled")));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                BondsStore store = bonds.store();
                UUID id = player.getUniqueId();
                List<BondsStore.BondRow> owned = store.listActiveForOwner(id);
                BondsStore.AccruedRow accrued = store.accrued(id);
                double ownerPrincipal = store.ownerPrincipal(id);
                List<BondPrincipalResolver.Holder> holders = resolveHolders();
                double poolPrincipal = sumHolders(holders);
                BondsStore.YieldStats yield = store.averageDailyYield(48);
                double weight = poolPrincipal > 0
                        ? (ownerPrincipal / poolPrincipal) * 100.0
                        : 0;
                Optional<TownyLeadership.TownRole> townRole = TownyLeadership.mayorTown(player);
                Optional<TownyLeadership.NationRole> nationRole = TownyLeadership.kingNation(player);
                GovSummary townSummary = null;
                GovSummary nationSummary = null;
                if (townRole.isPresent()) {
                    var role = townRole.get();
                    townSummary = loadGovSummary(store, role.bankUuid(), role.bankName(), holders, poolPrincipal);
                }
                if (nationRole.isPresent()) {
                    var role = nationRole.get();
                    nationSummary = loadGovSummary(store, role.bankUuid(), role.bankName(), holders, poolPrincipal);
                }
                final TownyLeadership.TownRole town = townRole.orElse(null);
                final TownyLeadership.NationRole nation = nationRole.orElse(null);
                final GovSummary townPanel = townSummary;
                final GovSummary nationPanel = nationSummary;
                final BondsStore.YieldStats yieldStats = yield;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    show(player, owned, accrued, ownerPrincipal, weight, poolPrincipal, yieldStats,
                            town, nation, townPanel, nationPanel);
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Bond menu load failed: " + ex.getMessage());
            }
        });
    }

    public void openGovernmentSettings(Player player, String kind) {
        if ("town".equalsIgnoreCase(kind)) {
            TownyLeadership.mayorTown(player).ifPresent(role ->
                    openGovernmentMenu(player, "town", role.name(), role.bankUuid(), role.bankName()));
            return;
        }
        if ("nation".equalsIgnoreCase(kind)) {
            TownyLeadership.kingNation(player).ifPresent(role ->
                    openGovernmentMenu(player, "nation", role.name(), role.bankUuid(), role.bankName()));
        }
    }

    private void openGovernmentMenu(Player player, String kind, String displayName, UUID bankUuid, String bankName) {
        BondService bonds = plugin.bonds();
        if (!bonds.enabled()) {
            player.sendMessage(legacyColor(plugin.msg("disabled")));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                BondsStore store = bonds.store();
                List<BondPrincipalResolver.Holder> holders = resolveHolders();
                double poolPrincipal = sumHolders(holders);
                BondsStore.YieldStats yield = store.averageDailyYield(48);
                GovSummary summary = loadGovSummary(store, bankUuid, bankName, holders, poolPrincipal);
                var economy = RootMcEconomyResolver.resolve(plugin);
                double bankBalance = economy == null ? 0 : GoldMoney.round(economy.balance(bankUuid, bankName));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    showGovernmentMenu(player, kind, displayName, bankUuid, bankName, bankBalance, summary, yield);
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Government bond menu load failed: " + ex.getMessage());
            }
        });
    }

    private List<BondPrincipalResolver.Holder> resolveHolders() throws java.sql.SQLException {
        BondPrincipalResolver resolver = plugin.principalResolver();
        if (resolver == null) {
            return List.of();
        }
        return resolver.resolveActivePrincipals();
    }

    private static double sumHolders(List<BondPrincipalResolver.Holder> holders) {
        double total = 0;
        for (BondPrincipalResolver.Holder holder : holders) {
            total += holder.principalG();
        }
        return GoldMoney.round(total);
    }

    private double resolvePoolPrincipal() throws java.sql.SQLException {
        double fromHolders = sumHolders(resolveHolders());
        if (fromHolders > 0) {
            return fromHolders;
        }
        return plugin.bonds().store().marketTotals().totalPrincipal();
    }

    private GovSummary loadGovSummary(
            BondsStore store,
            UUID bankUuid,
            String bankName,
            List<BondPrincipalResolver.Holder> holders,
            double poolPrincipal) throws java.sql.SQLException {
        BondsStore.AccruedRow accrued = store.accrued(bankUuid);
        double principal = 0;
        if (bankUuid != null) {
            var economy = RootMcEconomyResolver.resolve(plugin);
            if (economy != null) {
                principal = GoldMoney.round(economy.balance(bankUuid, bankName == null ? "" : bankName));
            }
        }
        boolean global = plugin.govSettings() == null || plugin.govSettings().globalAutoBondEnabled();
        boolean optedIn = plugin.govSettings() == null
                || plugin.govSettings().isEnabled(bankUuid, true);
        boolean activeInPool = false;
        double poolPrincipalForGov = principal;
        if (bankUuid != null && holders != null) {
            for (BondPrincipalResolver.Holder holder : holders) {
                if (holder.kind() != BondPrincipalResolver.Kind.PLAYER
                        && bankUuid.equals(holder.accountUuid())) {
                    activeInPool = true;
                    poolPrincipalForGov = holder.principalG();
                    break;
                }
            }
        }
        double weight = activeInPool && poolPrincipal > 0
                ? (poolPrincipalForGov / poolPrincipal) * 100.0
                : 0;
        return new GovSummary(
                principal,
                accrued == null ? 0 : accrued.lifetimeEarnedG(),
                weight,
                optedIn,
                global,
                activeInPool);
    }

    private void showGovernmentMenu(
            Player player,
            String kind,
            String displayName,
            UUID bankUuid,
            String bankName,
            double bankBalance,
            GovSummary summary,
            BondsStore.YieldStats yield) {
        boolean town = "town".equalsIgnoreCase(kind);
        String title = legacyColor(town ? plugin.msg("gov-town-title") : plugin.msg("gov-nation-title"));
        GovBondMenuHolder holder = new GovBondMenuHolder(player.getUniqueId(), kind, displayName, bankUuid, bankName);
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.bind(inv);

        inv.setItem(0, actionItem(Material.ARROW, "Back", "&7Return to bond vault"));
        inv.setItem(4, infoPaper(
                town ? "Town bank bonds" : "Nation bank bonds",
                "&f" + displayName,
                "&7Bank balance: &f" + GoldMoney.format(bankBalance) + " G",
                "&7Market weight: &f" + String.format(Locale.US, "%.3f", summary.weightPct()) + "%",
                yieldLine(yield),
                yieldPerGLine(yield),
                "&7Lifetime bond deposits: &f" + GoldMoney.format(summary.lifetimeEarnedG()) + " G",
                "",
                summary.activeInPool()
                        ? "&aParticipating in reserve bond pool"
                        : (!summary.globalEnabled()
                                ? "&cAuto-bond disabled server-wide"
                                : (summary.enabled() ? "&eBelow minimum or inactive members" : "&cAuto-bond disabled")),
                "&7Earnings deposit to the " + (town ? "town" : "nation") + " bank each MC day."));
        inv.setItem(8, infoBook(
                plugin.msg("about-title"),
                plugin.msg("gov-about-1"),
                plugin.msg("gov-about-2"),
                plugin.msg("gov-about-3")));

        if (!summary.globalEnabled()) {
            inv.setItem(13, infoPaper(
                    "Auto-bond",
                    "&cDisabled server-wide",
                    "&7Staff turned off government auto-bonds."));
        } else {
            boolean optedIn = plugin.govSettings() != null
                    && plugin.govSettings().isEnabled(bankUuid, true);
            Material toggleMat = optedIn ? Material.LIME_DYE : Material.GRAY_DYE;
            inv.setItem(13, actionItem(
                    toggleMat,
                    optedIn ? "Auto-bond: ON" : "Auto-bond: OFF",
                    "&7Click to " + (optedIn ? "disable" : "enable"),
                    "&7Uses " + (town ? "town" : "nation") + " bank balance",
                    "&7in the reserve bond pool"));
        }

        openGov.put(player.getUniqueId(), holder);
        player.openInventory(inv);
    }

    public void toggleGovernmentAutoBond(Player player, GovBondMenuHolder holder) {
        if (holder == null || plugin.govSettings() == null) {
            return;
        }
        if (!plugin.govSettings().globalAutoBondEnabled()) {
            player.sendMessage(legacyColor(plugin.msg("gov-disabled-global")));
            return;
        }
        boolean town = "town".equalsIgnoreCase(holder.kind());
        if (town && TownyLeadership.mayorTown(player).filter(r -> r.name().equalsIgnoreCase(holder.displayName())).isEmpty()) {
            player.sendMessage(legacyColor(plugin.msg("gov-not-leader")));
            return;
        }
        if (!town && TownyLeadership.kingNation(player).filter(r -> r.name().equalsIgnoreCase(holder.displayName())).isEmpty()) {
            player.sendMessage(legacyColor(plugin.msg("gov-not-leader")));
            return;
        }
        boolean next = !plugin.govSettings().isEnabled(holder.bankUuid(), true);
        plugin.govSettings().setEnabled(holder.bankUuid(), holder.kind(), holder.displayName(), next);
        String template = town
                ? (next ? plugin.msg("gov-town-enabled") : plugin.msg("gov-town-disabled"))
                : (next ? plugin.msg("gov-nation-enabled") : plugin.msg("gov-nation-disabled"));
        player.sendMessage(legacyColor(template
                .replace("{town}", holder.displayName())
                .replace("{nation}", holder.displayName())));
        openGovernmentMenu(player, holder.kind(), holder.displayName(), holder.bankUuid(), holder.bankName());
    }

    private record GovSummary(
            double principalG,
            double lifetimeEarnedG,
            double weightPct,
            boolean enabled,
            boolean globalEnabled,
            boolean activeInPool) {}

    public void openNoteDetail(Player player, UUID bondId, ItemStack heldItem) {
        BondService bonds = plugin.bonds();
        if (!bonds.enabled()) {
            player.sendMessage(legacyColor(plugin.msg("disabled")));
            return;
        }
        if (bondId == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                BondsStore store = bonds.store();
                Optional<BondsStore.BondRow> bondOpt = store.findBond(bondId);
                double poolPrincipal = resolvePoolPrincipal();
                BondsStore.YieldStats yield = store.averageDailyYield(48);
                BondsStore.AccruedRow accrued = store.accrued(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    showNoteDetail(player, bondId, bondOpt.orElse(null), heldItem, poolPrincipal, accrued, yield);
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Bond note menu load failed: " + ex.getMessage());
            }
        });
    }

    private void showNoteDetail(
            Player player,
            UUID bondId,
            BondsStore.BondRow bond,
            ItemStack heldItem,
            double poolPrincipal,
            BondsStore.AccruedRow accrued,
            BondsStore.YieldStats yield) {
        double principal = bond != null
                ? bond.principal()
                : Optional.ofNullable(plugin.bonds().certificates().readPrincipal(heldItem)).orElse(0.0);
        Instant issuedAt = bond != null ? bond.issuedAt() : null;
        boolean owner = bond != null && bond.ownerUuid().equals(player.getUniqueId());
        boolean bondedRoot = BondService.isBondedRoot(bond)
                || plugin.bonds().certificates().isBondedRoot(heldItem);
        String ownerName = bond != null ? bond.ownerName() : "?";
        double weightPct = poolPrincipal > 0 && principal > 0
                ? (principal / poolPrincipal) * 100.0
                : 0;

        BondNoteMenuHolder holder = new BondNoteMenuHolder(player.getUniqueId(), bondId);
        Inventory inv = Bukkit.createInventory(holder, 27, legacyColor(plugin.msg("note-detail-title")));
        holder.bind(inv);

        inv.setItem(0, actionItem(Material.ARROW, "Bond vault", "&7Open full &f/bonds &7menu"));
        inv.setItem(8, actionItem(Material.CHEST, "Bond vault", "&7Coupons, issue notes, all holdings"));

        ItemStack noteDisplay = heldItem != null && plugin.bonds().certificates().readBondId(heldItem) != null
                ? heldItem.clone()
                : (bondedRoot
                        ? plugin.bonds().certificates().createRoot(
                                bondId,
                                Math.max(principal, plugin.bonds().config().bondedRootCostG()),
                                issuedAt != null ? issuedAt : Instant.now())
                        : plugin.bonds().certificates().create(
                                bondId,
                                Math.max(principal, plugin.bonds().config().minPrincipalG()),
                                issuedAt != null ? issuedAt : Instant.now()));
        inv.setItem(4, noteDisplay);

        List<String> detailLines = new ArrayList<>();
        detailLines.add("&f" + GoldMoney.format(principal) + " G &7"
                + (bondedRoot ? "unredeemable root principal" : "principal in reserve"));
        if (issuedAt != null) {
            detailLines.add("&7Issued &f" + NOTE_DATE_FMT.format(issuedAt));
        } else {
            detailLines.add("&7Issued date unavailable on paper");
        }
        detailLines.add("&7Note weight: &f" + String.format(Locale.US, "%.3f", weightPct) + "% &7of market");
        detailLines.add(yieldLine(yield));
        detailLines.add(yieldPerGLine(yield));
        if (yield.hasData() && principal > 0) {
            detailLines.add("&7Est. avg earn: &f"
                    + GoldMoney.format(principal * yield.avgGPerGPerMcDay())
                    + " G &7/ MC day");
        }
        detailLines.add("&7Reserve coupon share: &f25% &7of daily inflows");
        if (bondedRoot) {
            detailLines.add("&7Does not count as circulating reserve G");
            detailLines.add("&cCannot redeem for physical gold");
        }
        if (bond == null) {
            detailLines.add("");
            detailLines.add("&cNot active in reserve ledger");
            detailLines.add("&7May be redeemed, invalid, or unsynced");
        } else if (owner) {
            detailLines.add("");
            detailLines.add("&aYou hold this note");
            detailLines.add("&7Your uncollected: &f" + GoldMoney.format(accrued.accruedG()) + " G");
            detailLines.add(claimDeadlineLine(accrued));
        } else {
            detailLines.add("");
            detailLines.add("&7Registered owner: &f" + ownerName);
            detailLines.add("&eRight-click or pick up to register");
            detailLines.add("&7Then redeem at &f/bonds");
        }
        detailLines.add("");
        detailLines.add("&8ID: " + bondId.toString().substring(0, 8) + "…");
        inv.setItem(2, infoPaper("This note", detailLines.toArray(String[]::new)));

        inv.setItem(6, infoBook(
                plugin.msg("about-title"),
                plugin.msg("about-4"),
                plugin.msg("about-5"),
                plugin.msg("about-6"),
                plugin.msg("about-7"),
                plugin.msg("about-8"),
                plugin.msg("about-9")));

        if (owner && bond != null && !bondedRoot) {
            inv.setItem(7, actionItem(
                    Material.GOLD_BLOCK,
                    "Redeem this note",
                    "&7Withdraw &f" + GoldMoney.format(principal) + " G",
                    "&7as physical gold",
                    "&7Removes this paper"));
        } else if (bondedRoot) {
            inv.setItem(7, infoPaper(
                    "Bonded Root",
                    "&7Earns bond coupons in Gen2",
                    "&cNot redeemable for physical gold"));
        } else {
            inv.setItem(7, infoPaper(
                    "Redeem",
                    bond == null
                            ? "&cCannot redeem — inactive note"
                            : "&7Held by &f" + ownerName,
                    "&7Redeem at &f/bonds &7as owner"));
        }

        openNotes.put(player.getUniqueId(), holder);
        player.openInventory(inv);
    }

    private void show(
            Player player,
            List<BondsStore.BondRow> owned,
            BondsStore.AccruedRow accrued,
            double ownerPrincipal,
            double weightPct,
            double marketPrincipal,
            BondsStore.YieldStats yield,
            TownyLeadership.TownRole townRole,
            TownyLeadership.NationRole nationRole,
            GovSummary townSummary,
            GovSummary nationSummary) {
        int size = plugin.bonds().config().guiSize();
        BondsMenuHolder holder = new BondsMenuHolder(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, size, legacyColor(plugin.msg("menu-title")));
        holder.bind(inv);

        inv.setItem(0, actionItem(
                Material.GOLD_NUGGET,
                "Get bonded notes",
                "&7Pick an amount → paper note",
                "&7Gold moves to Server Reserve"));
        inv.setItem(3, aboutBondsItem());
        inv.setItem(1, infoPaper(
                "Your notes",
                "&f" + GoldMoney.format(ownerPrincipal) + " G &7bond principal",
                "&7Market weight: &f" + String.format(Locale.US, "%.3f", weightPct) + "%",
                yieldLine(yield),
                yield.hasData() && ownerPrincipal > 0
                        ? "&7Est. avg earn: &f"
                                + GoldMoney.format(ownerPrincipal * yield.avgGPerGPerMcDay())
                                + " G &7/ MC day"
                        : "&7Est. avg earn: &8—",
                "&7Uncollected: &f" + GoldMoney.format(accrued.accruedG()) + " G",
                claimDeadlineLine(accrued),
                "&7Lifetime claimed: &f" + GoldMoney.format(accrued.lifetimeEarnedG()) + " G"));
        inv.setItem(4, infoPaper(
                "Market",
                "&f" + GoldMoney.format(marketPrincipal) + " G &7pool principal",
                yieldLine(yield),
                yieldPerGLine(yield),
                "&7" + owned.size() + " note(s) in your name",
                "",
                plugin.msg("tip-ender")));
        if (townRole != null && townSummary != null) {
            inv.setItem(2, governmentShortcutItem(
                    Material.EMERALD_BLOCK,
                    "Town bonds",
                    townRole.name(),
                    townSummary));
        }
        if (nationRole != null && nationSummary != null) {
            inv.setItem(5, governmentShortcutItem(
                    Material.DIAMOND_BLOCK,
                    "Nation bonds",
                    nationRole.name(),
                    nationSummary));
        }
        inv.setItem(6, earningsActionItem(accrued));
        inv.setItem(7, actionItem(
                Material.GOLD_BLOCK,
                "Redeem note",
                "&7Hold a bonded note in your hand",
                "&7and click to withdraw principal",
                "&7as physical gold."));

        double displayG = accrued.accruedG();
        Inventory goldChest = Bukkit.createInventory(null, plugin.bonds().config().goldChestSlots());
        int units = GoldMintHelper.toNuggetUnits(displayG);
        int maxUnits = GoldMintHelper.maxUnitsForStorage(goldChest, units);
        double shownG = GoldMintHelper.goldCost(maxUnits);
        if (maxUnits > 0) {
            for (ItemStack stack : SystemGoldPayout.markedStacks(GoldMintHelper.condense(maxUnits))) {
                goldChest.addItem(stack);
            }
        }
        int start = plugin.bonds().config().goldChestStartSlot();
        for (int i = 0; i < goldChest.getSize(); i++) {
            inv.setItem(start + i, goldChest.getItem(i));
        }

        holder.setDisplayedGold(shownG);
        open.put(player.getUniqueId(), holder);
        sendInfoLink(player);
        player.openInventory(inv);
    }

    public void openCreateMenu(Player player) {
        BondService bonds = plugin.bonds();
        if (!bonds.enabled()) {
            player.sendMessage(legacyColor(plugin.msg("disabled")));
            return;
        }
        if (!player.hasPermission("rootbonds.use")) {
            player.sendMessage(legacyColor(plugin.msg("no-permission")));
            return;
        }
        var economy = RootMcEconomyResolver.resolve(plugin);
        double balance = economy == null ? 0 : economy.balance(player.getUniqueId());
        double min = bonds.config().minPrincipalG();
        BondCreateSessions.Session session = plugin.createListener().sessions().session(player.getUniqueId());

        BondCreateMenuHolder holder = new BondCreateMenuHolder(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, legacyColor(plugin.msg("create-menu-title")));
        holder.bind(inv);

        inv.setItem(0, actionItem(Material.ARROW, "Back", "&7Return to bond vault"));
        inv.setItem(4, infoPaper(
                "Your wallet",
                "&f" + GoldMoney.format(balance) + " G &7available",
                "&7Minimum: &f" + GoldMoney.format(min) + " G",
                "",
                "&7Pick amount → get &fBonded note&7 paper"));
        String amountLine = session.amount() >= min
                ? "&f" + GoldMoney.format(session.amount()) + " G → note"
                : "&7Click a preset or custom amount";
        inv.setItem(6, actionItem(Material.PAPER, "Amount", amountLine));
        double[] presets = {1, 5, 10, 25, 50, 100};
        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < presets.length; i++) {
            boolean selected = Math.abs(session.amount() - presets[i]) < 1e-9;
            inv.setItem(slots[i], presetAmountItem(presets[i], selected));
        }
        inv.setItem(22, actionItem(Material.ANVIL, "Custom amount", "&7Enter any amount in G"));
        inv.setItem(26, actionItem(
                Material.LIME_CONCRETE,
                "Confirm",
                "&7Wallet → Server Reserve",
                "&7Receive bonded note paper"));
        inv.setItem(8, createAboutItem());

        player.openInventory(inv);
    }

    private ItemStack governmentShortcutItem(Material material, String title, String name, GovSummary summary) {
        String status = !summary.globalEnabled()
                ? "&cServer off"
                : (summary.enabled() ? "&aAuto-bond ON" : "&7Auto-bond OFF");
        return actionItem(
                material,
                title,
                "&f" + name,
                "&7Bank: &f" + GoldMoney.format(summary.principalG()) + " G",
                status,
                "&7Click for settings");
    }

    public GovBondMenuHolder govSession(Player player) {
        return player == null ? null : openGov.get(player.getUniqueId());
    }

    private ItemStack aboutBondsItem() {
        return infoBook(
                plugin.msg("about-title"),
                plugin.msg("about-1"),
                plugin.msg("about-2"),
                plugin.msg("about-3"),
                plugin.msg("about-4"),
                plugin.msg("about-5"),
                plugin.msg("about-6"),
                plugin.msg("about-7"),
                plugin.msg("about-8"),
                plugin.msg("about-9"));
    }

    private void sendInfoLink(Player player) {
        player.sendMessage(ChatLinks.labelDashUrl("[Bonds dashboard]", BONDS_INFO_URL));
    }

    private ItemStack createAboutItem() {
        return infoBook(
                "&6Bonded notes",
                plugin.msg("create-about-1"),
                plugin.msg("create-about-2"),
                plugin.msg("create-about-3"));
    }

    private ItemStack infoBook(String title, String... lines) {
        ItemStack stack = new ItemStack(Material.BOOK);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(legacyComponentPrivate(title));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                lore.add(Component.empty());
            } else {
                lore.add(legacyComponentPrivate(line));
            }
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack presetAmountItem(double amount, boolean selected) {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET, Math.max(1, Math.min(64, (int) amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(legacyComponent(selected ? "&a" + GoldMoney.format(amount) + " G" : "&f" + GoldMoney.format(amount) + " G"));
        meta.lore(List.of(legacyComponent(selected ? "&7Selected" : "&7Click to select")));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack earningsActionItem(BondsStore.AccruedRow accrued) {
        boolean autoWallet = plugin.bonds().config().autoPayEarningsWallet();
        double legacy = accrued == null ? 0 : accrued.accruedG();
        if (autoWallet && legacy < GoldMoney.MIN_AMOUNT) {
            return actionItem(
                    Material.GOLD_INGOT,
                    "Bond earnings",
                    "&7Paid to your &fwallet&7 each MC day",
                    "&7while you hold registered notes");
        }
        return actionItem(
                Material.GOLD_INGOT,
                "Claim legacy earnings",
                "&7Collect older unclaimed earnings",
                "&7as physical gold (&f" + plugin.bonds().config().claimExpiryLabel() + "&7 window)",
                "&7Click to claim &f" + GoldMoney.format(legacy) + " G");
    }

    private String claimDeadlineLine(BondsStore.AccruedRow accrued) {
        Instant deadline = accrued.nextClaimDeadline();
        if (accrued.accruedG() < GoldMoney.MIN_AMOUNT || deadline == null) {
            return "&7Claim window: &f" + plugin.bonds().config().claimExpiryLabel() + " &7per payout";
        }
        return "&cClaim by: &f" + DEADLINE_FMT.format(deadline) + " HST";
    }

    public BondsMenuSession session(Player player) {
        if (player == null) {
            return null;
        }
        BondsMenuHolder holder = open.get(player.getUniqueId());
        return holder == null ? null : new BondsMenuSession(holder);
    }

    public void close(Player player) {
        if (player == null) {
            return;
        }
        open.remove(player.getUniqueId());
        openNotes.remove(player.getUniqueId());
        openGov.remove(player.getUniqueId());
    }

    public void refreshOpenMenus() {
        for (UUID id : new ArrayList<>(open.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                open(player);
            }
        }
    }

    public boolean payoutPhysical(Player player, double amountG) {
        if (player == null || amountG < GoldMoney.MIN_AMOUNT) {
            return false;
        }
        int units = GoldMintHelper.toNuggetUnits(amountG);
        List<ItemStack> stacks = SystemGoldPayout.markedStacks(GoldMintHelper.condense(units));
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stacks.toArray(ItemStack[]::new));
        overflow.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        return true;
    }

    private static String yieldLine(BondsStore.YieldStats yield) {
        if (yield == null || !yield.hasData()) {
            return "&7Avg return: &8no settlement history yet";
        }
        return "&7Avg return: &f"
                + String.format(Locale.US, "%.4f", yield.avgYieldPctPerMcDay())
                + "% &7/ G / MC day &8("
                + yield.sampleDays()
                + " days)";
    }

    private static String yieldPerGLine(BondsStore.YieldStats yield) {
        if (yield == null || !yield.hasData()) {
            return "&7Per 1 G: &8—";
        }
        return "&7Per 1 G: &f"
                + String.format(Locale.US, "%.6f", yield.avgGPerGPerMcDay())
                + " G &7avg / MC day";
    }

    private ItemStack infoPaper(String title, String... lines) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(legacyComponentPrivate(title));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                lore.add(Component.empty());
            } else {
                lore.add(legacyComponentPrivate(line));
            }
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack actionItem(Material material, String title, String... lines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(legacyComponentPrivate(title));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(legacyComponentPrivate(line));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public static String legacyColor(String raw) {
        return raw == null ? "" : raw.replace('&', '\u00A7');
    }

    public static Component legacyComponent(String raw) {
        return Component.text(legacyColor(raw))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component legacyComponentPrivate(String raw) {
        return legacyComponent(raw);
    }
}
