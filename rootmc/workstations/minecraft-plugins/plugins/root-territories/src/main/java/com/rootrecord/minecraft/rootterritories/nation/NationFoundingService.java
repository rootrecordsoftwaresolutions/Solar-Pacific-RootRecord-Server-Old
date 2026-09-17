package com.rootrecord.minecraft.rootterritories.nation;

import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rootterritories.config.TerritoriesConfig;
import com.rootrecord.minecraft.rootterritories.service.TerritoryService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class NationFoundingService {

    private final JavaPlugin plugin;
    private final NationFoundingConfig config;
    private final NationFoundingStore store;
    private final TerritoryService territories;

    public NationFoundingService(
            JavaPlugin plugin,
            NationFoundingConfig config,
            NationFoundingStore store,
            TerritoryService territories) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.territories = territories;
    }

    public void bootstrapGrandfather() {
        if (!config.enabled() || !config.snapshotGrandfatherOnEnable()) {
            return;
        }
        if (!store.grandfatherNations().isEmpty()) {
            return;
        }
        if (!TownyTownAccess.isAvailable()) {
            return;
        }
        Set<String> nations = TownyTownAccess.allNationNames();
        if (nations.isEmpty()) {
            return;
        }
        store.saveGrandfatherNations(nations);
        plugin.getLogger().info("Nation founding: grandfathered existing nation(s): " + String.join(", ", nations));
    }

    public boolean proposalsRequired() {
        return config.enabled() && config.requireProposal();
    }

    public Optional<String> propose(Player mayor, String nationName, List<String> partnerTownsRaw) {
        if (!config.enabled()) {
            return Optional.of("&cNation founding proposals are disabled.");
        }
        if (!TownyTownAccess.isAvailable()) {
            return Optional.of("&cTowny is required for nation founding.");
        }
        Optional<String> capitalOpt = TownyTownAccess.playerTownName(mayor);
        if (capitalOpt.isEmpty()) {
            return Optional.of("&cYou must be in a town to propose a nation.");
        }
        String capital = capitalOpt.get();
        if (!TownyTownAccess.isMayorOf(mayor, capital)) {
            return Optional.of(colorize(config.msgNotMayor().replace("{town}", capital)));
        }
        if (TownyTownAccess.townHasNation(capital)) {
            return Optional.of(colorize(config.msgAlreadyInNation().replace("{town}", capital)));
        }
        String nation = NationFoundingProposal.normalizeNation(nationName);
        if (nation.isBlank()) {
            return Optional.of("&cEnter a nation name.");
        }
        if (TownyTownAccess.nationExists(nation)) {
            return Optional.of("&cA nation with that name already exists.");
        }
        List<String> partners = TownyTownAccess.normalizeTownList(partnerTownsRaw).stream()
                .filter(t -> !t.equalsIgnoreCase(capital))
                .collect(Collectors.toList());
        LinkedHashSet<String> allTowns = new LinkedHashSet<>();
        allTowns.add(capital);
        allTowns.addAll(partners);
        if (allTowns.size() < config.minTowns()) {
            return Optional.of("&cAt least &f" + config.minTowns() + " &ctowns must join (capital + partners).");
        }
        Optional<Object> capitalTownObj = TownyTownAccess.townByName(capital);
        if (capitalTownObj.isEmpty()) {
            return Optional.of("&cCapital town not found.");
        }
        for (String partner : partners) {
            if (TownyTownAccess.townHasNation(partner)) {
                return Optional.of(colorize(config.msgAlreadyInNation().replace("{town}", partner)));
            }
            Optional<Object> partnerObj = TownyTownAccess.townByName(partner);
            if (partnerObj.isEmpty()) {
                return Optional.of("&cTown not found: &f" + partner);
            }
            if (!TownyTownAccess.townsTouch(
                    capitalTownObj.get(), partnerObj.get(), territories.config().townBuffer())) {
                return Optional.of(colorize(
                        config.msgNoTouch().replace("{town}", partner).replace("{capital}", capital)));
            }
        }
        String id = store.nextId();
        long now = System.currentTimeMillis();
        NationFoundingProposal proposal = new NationFoundingProposal(
                id,
                nation,
                capital,
                partners,
                NationFoundingProposal.Status.PENDING,
                now,
                mayor.getUniqueId().toString(),
                new java.util.LinkedHashMap<>());
        proposal.sign(
                capital,
                new NationFoundingProposal.Signature(
                        mayor.getUniqueId().toString(), mayor.getName(), now));
        refreshApproval(proposal);
        store.save(proposal);
        String partnersLabel = String.join(", ", partners);
        mayor.sendMessage(colorize(config.msgProposed()
                .replace("{id}", id)
                .replace("{nation}", nation)
                .replace("{partners}", partnersLabel)));
        if (proposal.status() == NationFoundingProposal.Status.APPROVED) {
            mayor.sendMessage(colorize(config.msgApproved()
                    .replace("{id}", id)
                    .replace("{nation}", nation)));
        }
        return Optional.empty();
    }

    public Optional<String> agree(Player mayor, String proposalId) {
        Optional<NationFoundingProposal> found = store.findById(proposalId);
        if (found.isEmpty()) {
            return Optional.of("&cProposal not found: &f" + proposalId);
        }
        NationFoundingProposal proposal = found.get();
        if (proposal.status() != NationFoundingProposal.Status.PENDING
                && proposal.status() != NationFoundingProposal.Status.APPROVED) {
            return Optional.of("&cThat proposal is no longer open.");
        }
        Optional<String> townOpt = TownyTownAccess.playerTownName(mayor);
        if (townOpt.isEmpty()) {
            return Optional.of("&cYou must be in a town to sign.");
        }
        String town = townOpt.get();
        if (!proposal.allTowns().stream().anyMatch(t -> t.equalsIgnoreCase(town))) {
            return Optional.of("&cYour town is not part of proposal &f" + proposal.id() + "&c.");
        }
        if (!TownyTownAccess.isMayorOf(mayor, town)) {
            return Optional.of(colorize(config.msgNotMayor().replace("{town}", town)));
        }
        if (proposal.isSigned(town)) {
            return Optional.of("&7Your town already signed proposal &f" + proposal.id() + "&7.");
        }
        long now = System.currentTimeMillis();
        proposal.sign(town, new NationFoundingProposal.Signature(
                mayor.getUniqueId().toString(), mayor.getName(), now));
        refreshApproval(proposal);
        store.save(proposal);
        mayor.sendMessage(colorize(config.msgAgreed()
                .replace("{id}", proposal.id())
                .replace("{signed}", String.valueOf(proposal.signedCount()))
                .replace("{total}", String.valueOf(proposal.requiredTownCount()))));
        if (proposal.status() == NationFoundingProposal.Status.APPROVED) {
            broadcastCapital(proposal, config.msgApproved()
                    .replace("{id}", proposal.id())
                    .replace("{nation}", proposal.nationName()));
        }
        return Optional.empty();
    }

    public Optional<String> cancel(Player player, String proposalId) {
        Optional<NationFoundingProposal> found = store.findById(proposalId);
        if (found.isEmpty()) {
            return Optional.of("&cProposal not found.");
        }
        NationFoundingProposal proposal = found.get();
        if (proposal.status() == NationFoundingProposal.Status.EXECUTED) {
            return Optional.of("&cThat proposal was already executed.");
        }
        Optional<String> townOpt = TownyTownAccess.playerTownName(player);
        if (townOpt.isEmpty() || !townOpt.get().equalsIgnoreCase(proposal.capitalTown())
                || !TownyTownAccess.isMayorOf(player, proposal.capitalTown())) {
            return Optional.of("&cOnly the capital mayor can cancel this proposal.");
        }
        proposal.setStatus(NationFoundingProposal.Status.CANCELLED);
        store.save(proposal);
        return Optional.empty();
    }

    public Optional<String> validateCreation(String nationName, String capitalTown) {
        if (!proposalsRequired()) {
            return Optional.empty();
        }
        Optional<NationFoundingProposal> approved =
                store.findApprovedForCreation(nationName, capitalTown);
        if (approved.isEmpty()) {
            return Optional.of(colorize(config.msgDeniedNoProposal()));
        }
        return Optional.empty();
    }

    public Optional<String> executePayments(NationFoundingProposal proposal) {
        List<String> towns = new ArrayList<>(proposal.allTowns());
        List<TownBankPay.Share> shares = TownBankPay.splitCost(config.totalCostGold(), towns);
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        Economy economy = TownBankPay.economyOrNull();
        TownBankPay.PaymentResult result = TownBankPay.collectSplit(treasury, economy, shares, proposal.id());
        if (!result.success()) {
            return Optional.of(colorize(config.msgDeniedPayment()
                    .replace("{town}", result.failedTown() == null ? "?" : result.failedTown())
                    .replace("{amount}", formatGold(result.failedAmount()))));
        }
        proposal.setStatus(NationFoundingProposal.Status.EXECUTED);
        store.save(proposal);
        return Optional.empty();
    }

    public NationFoundingConfig config() {
        return config;
    }

    public NationFoundingStore store() {
        return store;
    }

    private void refreshApproval(NationFoundingProposal proposal) {
        if (proposal.isFullySigned()) {
            proposal.setStatus(NationFoundingProposal.Status.APPROVED);
        }
    }

    private void broadcastCapital(NationFoundingProposal proposal, String message) {
        TownyTownAccess.townByName(proposal.capitalTown()).ifPresent(town -> {
            Object mayor = com.rootrecord.minecraft.rootterritories.towny.TownyReflection.invokeNoArg(town, "getMayor");
            if (mayor == null) {
                return;
            }
            Object uuid = com.rootrecord.minecraft.rootterritories.towny.TownyReflection.invokeNoArg(mayor, "getUUID", "getUniqueId");
            if (uuid instanceof java.util.UUID id) {
                Player online = plugin.getServer().getPlayer(id);
                if (online != null) {
                    online.sendMessage(colorize(message));
                }
            }
        });
    }

    public static String formatGold(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    public static String colorize(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
