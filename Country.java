import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

public class Country {
    public enum Relation { NEUTRAL, ALLY, ENEMY, WAR }

    private final UUID id;
    private String name;
    private String leaderName;
    private boolean isNpc;
    private int color;
    private ItemStack bannerStack;
    private final Set<Long> claimedChunks = new HashSet<>();
    private final Map<UUID, Relation> relations = new HashMap<>();
    private final Set<UUID> receivedAllyGiftFrom = new HashSet<>();
    private UUID overlordId = null;

    public Country(UUID id, String name, String leaderName, boolean isNpc, int color, ItemStack bannerStack) {
        this.id = id;
        this.name = name;
        this.leaderName = leaderName;
        this.isNpc = isNpc;
        this.color = color;
        this.bannerStack = (bannerStack == null || bannerStack.isEmpty()) ? new ItemStack(Items.WHITE_BANNER) : bannerStack.copy();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLeaderName() { return leaderName; }
    public int getColor() { return color; }
    public boolean isNpc() { return isNpc; }
    public ItemStack getBannerStack() { return bannerStack; }
    public void setBannerStack(ItemStack stack) { this.bannerStack = stack.copy(); }
    public Set<Long> getClaimedChunks() { return claimedChunks; }
    public UUID getOverlordId() { return overlordId; }
    public void setOverlordId(UUID overlordId) { this.overlordId = overlordId; }
    public boolean isPuppet() { return overlordId != null; }

    public void addChunk(ChunkPos pos) { claimedChunks.add(pos.toLong()); }
    public void removeChunk(ChunkPos pos) { claimedChunks.remove(pos.toLong()); }
    public boolean hasChunk(ChunkPos pos) { return claimedChunks.contains(pos.toLong()); }

    public boolean isAdjacentToClaim(ChunkPos target) {
        if (claimedChunks.isEmpty()) return true;
        long[] neighbors = {
            ChunkPos.toLong(target.x + 1, target.z),
            ChunkPos.toLong(target.x - 1, target.z),
            ChunkPos.toLong(target.x, target.z + 1),
            ChunkPos.toLong(target.x, target.z - 1)
        };
        for (long n : neighbors) {
            if (claimedChunks.contains(n)) return true;
        }
        return false;
    }

    public ChunkPos getCenterChunk() {
        if (claimedChunks.isEmpty()) return new ChunkPos(0, 0);
        long sumX = 0, sumZ = 0;
        for (long l : claimedChunks) {
            ChunkPos cp = new ChunkPos(l);
            sumX += cp.x;
            sumZ += cp.z;
        }
        return new ChunkPos((int) (sumX / claimedChunks.size()), (int) (sumZ / claimedChunks.size()));
    }

    public Relation getRelation(UUID otherCountryId) {
        return relations.getOrDefault(otherCountryId, Relation.NEUTRAL);
    }

    public void setRelation(UUID otherCountryId, Relation relation) {
        relations.put(otherCountryId, relation);
    }

    public boolean canReceiveAllyGift(UUID allyId) {
        return !receivedAllyGiftFrom.contains(allyId);
    }

    public void markAllyGiftReceived(UUID allyId) {
        receivedAllyGiftFrom.add(allyId);
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("id", id);
        nbt.putString("name", name);
        nbt.putString("leaderName", leaderName);
        nbt.putBoolean("isNpc", isNpc);
        nbt.putInt("color", color);
        if (overlordId != null) nbt.putUuid("overlordId", overlordId);
        if (!bannerStack.isEmpty()) {
            nbt.put("banner", bannerStack.encode(registries));
        }

        NbtList chunkList = new NbtList();
        for (Long chunkLong : claimedChunks) chunkList.add(NbtLong.of(chunkLong));
        nbt.put("chunks", chunkList);

        NbtCompound relNbt = new NbtCompound();
        relations.forEach((uuid, rel) -> relNbt.putString(uuid.toString(), rel.name()));
        nbt.put("relations", relNbt);

        NbtList giftList = new NbtList();
        for (UUID uuid : receivedAllyGiftFrom) {
            NbtCompound g = new NbtCompound();
            g.putUuid("u", uuid);
            giftList.add(g);
        }
        nbt.put("gifts", giftList);
        return nbt;
    }

    public static Country fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        UUID id = nbt.getUuid("id");
        String name = nbt.getString("name");
        String leader = nbt.getString("leaderName");
        boolean isNpc = nbt.getBoolean("isNpc");
        int color = nbt.getInt("color");
        ItemStack banner = ItemStack.EMPTY;
        if (nbt.contains("banner")) {
            banner = ItemStack.fromNbt(registries, nbt.getCompound("banner")).orElse(new ItemStack(Items.WHITE_BANNER));
        }

        Country country = new Country(id, name, leader, isNpc, color, banner);
        if (nbt.containsUuid("overlordId")) country.setOverlordId(nbt.getUuid("overlordId"));

        NbtList chunkList = nbt.getList("chunks", NbtElement.LONG_TYPE);
        for (int i = 0; i < chunkList.size(); i++) {
            country.claimedChunks.add(((NbtLong) chunkList.get(i)).longValue());
        }

        NbtCompound relNbt = nbt.getCompound("relations");
        for (String key : relNbt.getKeys()) {
            country.relations.put(UUID.fromString(key), Relation.valueOf(relNbt.getString(key)));
        }

        NbtList giftList = nbt.getList("gifts", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < giftList.size(); i++) {
            country.receivedAllyGiftFrom.add(giftList.getCompound(i).getUuid("u"));
        }
        return country;
    }
}
