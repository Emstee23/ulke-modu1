import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.*;

public class CountryState extends PersistentState {
    private final Map<UUID, Country> countries = new HashMap<>();
    private final Map<Long, UUID> chunkOwnerMap = new HashMap<>();
    private final Map<UUID, UUID> playerCountryMap = new HashMap<>();

    public static final int MIN_COUNTRY_DISTANCE_CHUNKS = 10;

    public static Type<CountryState> getPersistentStateType() {
        return new Type<>(CountryState::new, CountryState::createFromNbt, null);
    }

    public static CountryState getServerState(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) return new CountryState();
        CountryState state = world.getPersistentStateManager().getOrCreate(getPersistentStateType(), "ulke_modu_countries");
        state.markDirty();
        return state;
    }

    public Country getCountryById(UUID id) { return countries.get(id); }
    public Country getCountryByPlayer(UUID playerUuid) {
        UUID countryId = playerCountryMap.get(playerUuid);
        return countryId != null ? countries.get(countryId) : null;
    }
    public UUID getPlayerByCountry(UUID countryId) {
        for (Map.Entry<UUID, UUID> entry : playerCountryMap.entrySet()) {
            if (entry.getValue().equals(countryId)) return entry.getKey();
        }
        return null;
    }
    public Country getCountryAt(ChunkPos pos) {
        UUID countryId = chunkOwnerMap.get(pos.toLong());
        return countryId != null ? countries.get(countryId) : null;
    }
    public Collection<Country> getAllCountries() { return countries.values(); }

    public boolean isTooCloseToOtherCountry(ChunkPos center) {
        for (long claimedLong : chunkOwnerMap.keySet()) {
            ChunkPos claimed = new ChunkPos(claimedLong);
            int dist = Math.max(Math.abs(center.x - claimed.x), Math.abs(center.z - claimed.z));
            if (dist < MIN_COUNTRY_DISTANCE_CHUNKS) return true;
        }
        return false;
    }

    public void registerCountry(Country country, UUID ownerPlayerUuid, ChunkPos initialChunk) {
        countries.put(country.getId(), country);
        if (ownerPlayerUuid != null) playerCountryMap.put(ownerPlayerUuid, country.getId());
        if (initialChunk != null) claimChunk(country, initialChunk);
        markDirty();
    }

    public boolean claimChunk(Country country, ChunkPos pos) {
        if (chunkOwnerMap.containsKey(pos.toLong())) return false;
        country.addChunk(pos);
        chunkOwnerMap.put(pos.toLong(), country.getId());
        markDirty();
        return true;
    }

    public boolean unclaimChunk(Country country, ChunkPos pos) {
        if (!country.hasChunk(pos)) return false;
        country.removeChunk(pos);
        chunkOwnerMap.remove(pos.toLong());
        markDirty();
        return true;
    }

    public void annexCountry(Country winner, Country loser) {
        List<Long> chunksToTransfer = new ArrayList<>(loser.getClaimedChunks());
        for (long l : chunksToTransfer) {
            ChunkPos cp = new ChunkPos(l);
            unclaimChunk(loser, cp);
            claimChunk(winner, cp);
        }
        countries.remove(loser.getId());
        UUID loserPlayer = getPlayerByCountry(loser.getId());
        if (loserPlayer != null) playerCountryMap.remove(loserPlayer);
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList countryList = new NbtList();
        for (Country country : countries.values()) countryList.add(country.toNbt(registries));
        nbt.put("countries", countryList);

        NbtCompound playerMapNbt = new NbtCompound();
        playerCountryMap.forEach((pId, cId) -> playerMapNbt.putUuid(pId.toString(), cId));
        nbt.put("playerMap", playerMapNbt);
        return nbt;
    }

    public static CountryState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        CountryState state = new CountryState();
        NbtList countryList = nbt.getList("countries", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < countryList.size(); i++) {
            Country c = Country.fromNbt(countryList.getCompound(i), registries);
            state.countries.put(c.getId(), c);
            for (Long chunkLong : c.getClaimedChunks()) state.chunkOwnerMap.put(chunkLong, c.getId());
        }
        NbtCompound playerMapNbt = nbt.getCompound("playerMap");
        for (String key : playerMapNbt.getKeys()) {
            state.playerCountryMap.put(UUID.fromString(key), playerMapNbt.getUuid(key));
        }
        return state;
    }
}
