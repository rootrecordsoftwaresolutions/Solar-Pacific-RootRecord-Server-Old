package com.rootrecord.minecraft.rootterritories.nation;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class NationFoundingStore {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final File grandfatherFile;
    private YamlConfiguration data;
    private YamlConfiguration grandfather;

    public NationFoundingStore(JavaPlugin plugin) {
        this.plugin = plugin;
        RootRecordFolders.ensureDir(plugin);
        this.dataFile = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_TERRITORIES_NATION_FOUNDING);
        this.grandfatherFile =
                RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_TERRITORIES_NATION_FOUNDING_GRANDFATHER);
        migrateLegacy(dataFile, "nation-founding.yml");
        migrateLegacy(grandfatherFile, "nation-founding-grandfather.yml");
        deleteEmptyLegacyFolder();
        reload();
    }

    public void reload() {
        data = YamlConfiguration.loadConfiguration(dataFile);
        grandfather = YamlConfiguration.loadConfiguration(grandfatherFile);
    }

    public Set<String> grandfatherNations() {
        List<String> list = grandfather.getStringList("nations");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String name : list) {
            if (name != null && !name.isBlank()) {
                out.add(name.trim());
            }
        }
        return Set.copyOf(out);
    }

    public void saveGrandfatherNations(Set<String> nations) {
        grandfather.set("nations", new ArrayList<>(nations));
        saveQuietly(grandfather, grandfatherFile);
    }

    public List<NationFoundingProposal> allProposals() {
        List<NationFoundingProposal> out = new ArrayList<>();
        var section = data.getConfigurationSection("proposals");
        if (section == null) {
            return out;
        }
        for (String id : section.getKeys(false)) {
            readProposal(id).ifPresent(out::add);
        }
        return out;
    }

    public Optional<NationFoundingProposal> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return readProposal(id.trim().toLowerCase(Locale.ROOT));
    }

    public Optional<NationFoundingProposal> findApprovedForCreation(String nationName, String capitalTown) {
        String nation = NationFoundingProposal.normalizeNation(nationName);
        String capital = NationFoundingProposal.normalizeTown(capitalTown);
        for (NationFoundingProposal proposal : allProposals()) {
            if (proposal.status() != NationFoundingProposal.Status.APPROVED) {
                continue;
            }
            if (proposal.nationName().equalsIgnoreCase(nation)
                    && proposal.capitalTown().equalsIgnoreCase(capital)) {
                return Optional.of(proposal);
            }
        }
        return Optional.empty();
    }

    public void save(NationFoundingProposal proposal) {
        String path = "proposals." + proposal.id();
        data.set(path + ".nation-name", proposal.nationName());
        data.set(path + ".capital-town", proposal.capitalTown());
        data.set(path + ".partner-towns", proposal.partnerTowns());
        data.set(path + ".status", proposal.status().name());
        data.set(path + ".created-at", proposal.createdAtEpochMs());
        data.set(path + ".created-by", proposal.createdByUuid());
        for (Map.Entry<String, NationFoundingProposal.Signature> entry : proposal.signatures().entrySet()) {
            String sigPath = path + ".signatures." + entry.getKey();
            NationFoundingProposal.Signature sig = entry.getValue();
            data.set(sigPath + ".mayor-uuid", sig.mayorUuid());
            data.set(sigPath + ".mayor-name", sig.mayorName());
            data.set(sigPath + ".signed-at", sig.signedAtEpochMs());
        }
        saveQuietly(data, dataFile);
    }

    private Optional<NationFoundingProposal> readProposal(String id) {
        String path = "proposals." + id;
        if (!data.isConfigurationSection(path)) {
            return Optional.empty();
        }
        String nationName = data.getString(path + ".nation-name", "");
        String capitalTown = data.getString(path + ".capital-town", "");
        List<String> partners = data.getStringList(path + ".partner-towns");
        NationFoundingProposal.Status status = NationFoundingProposal.Status.valueOf(
                data.getString(path + ".status", NationFoundingProposal.Status.PENDING.name()));
        long createdAt = data.getLong(path + ".created-at", 0L);
        String createdBy = data.getString(path + ".created-by", "");
        Map<String, NationFoundingProposal.Signature> signatures = new LinkedHashMap<>();
        var sigSection = data.getConfigurationSection(path + ".signatures");
        if (sigSection != null) {
            for (String town : sigSection.getKeys(false)) {
                signatures.put(
                        town,
                        new NationFoundingProposal.Signature(
                                sigSection.getString(town + ".mayor-uuid", ""),
                                sigSection.getString(town + ".mayor-name", ""),
                                sigSection.getLong(town + ".signed-at", 0L)));
            }
        }
        return Optional.of(new NationFoundingProposal(
                id,
                nationName,
                capitalTown,
                partners,
                status,
                createdAt,
                createdBy,
                signatures));
    }

    public String nextId() {
        return UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
    }

    private void migrateLegacy(File target, String legacyName) {
        if (target.exists()) {
            return;
        }
        File legacy = new File(plugin.getDataFolder(), legacyName);
        if (!legacy.isFile()) {
            return;
        }
        try {
            Files.createDirectories(target.toPath().getParent());
            Files.move(legacy.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Migrated " + legacyName + " → plugins/RootMC/" + target.getName());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not migrate " + legacyName + ": " + ex.getMessage());
        }
    }

    private void deleteEmptyLegacyFolder() {
        File folder = plugin.getDataFolder();
        if (!folder.isDirectory()) {
            return;
        }
        File[] leftover = folder.listFiles();
        if (leftover != null && leftover.length == 0) {
            //noinspection ResultOfMethodCallIgnored
            folder.delete();
        }
    }

    private void saveQuietly(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save " + file.getName() + ": " + ex.getMessage());
        }
    }
}
