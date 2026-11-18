import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;

import java.util.Random;
import java.util.UUID;

public class CountryManager {

    public static final int CREATE_COST = 25;
    public static final int CLAIM_COST = 1;
    public static final int ALLY_GIFT_AMOUNT = 100;

    public static boolean consumeGold(ServerPlayerEntity player, int amount) {
        if (player.isCreative()) return true;
        int totalGold = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.GOLD_INGOT)) totalGold += stack.getCount();
        }
        if (totalGold < amount) return false;

        int remaining = amount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.GOLD_INGOT)) {
                int take = Math.min(stack.getCount(), remaining);
                stack.decrement(take);
                remaining -= take;
            }
        }
        return true;
    }

    public static void giveGold(ServerPlayerEntity player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(64, remaining);
            ItemStack gold = new ItemStack(Items.GOLD_INGOT, give);
            if (!player.getInventory().insertStack(gold)) {
                player.dropItem(gold, false);
            }
            remaining -= give;
        }
    }

    public static void createCountry(ServerPlayerEntity player, String countryName, String leaderName, ItemStack banner) {
        CountryState state = CountryState.getServerState(player.getServer());
        ChunkPos currentChunk = player.getChunkPos();

        if (state.getCountryByPlayer(player.getUuid()) != null) {
            player.sendMessage(Text.literal("Zaten bir ülken var!").formatted(Formatting.RED), false);
            return;
        }
        if (state.getCountryAt(currentChunk) != null) {
            player.sendMessage(Text.literal("Bu toprak zaten başka bir ülkeye ait!").formatted(Formatting.RED), false);
            return;
        }
        if (state.isTooCloseToOtherCountry(currentChunk)) {
            player.sendMessage(Text.literal("Başka bir ülkeye çok yakınsın! En az " + CountryState.MIN_COUNTRY_DISTANCE_CHUNKS + " chunk uzaklaş.").formatted(Formatting.RED), false);
            return;
        }
        if (!consumeGold(player, CREATE_COST)) {
            player.sendMessage(Text.literal("Ülke kurmak için 25 Altın Külçesi gerekli!").formatted(Formatting.RED), false);
            return;
        }

        Random rand = new Random();
        int color = 0xFF000000 | (rand.nextInt(200) + 55) << 16 | (rand.nextInt(200) + 55) << 8 | (rand.nextInt(200) + 55);
        String finalName = countryName.isBlank() ? player.getName().getString() + " Krallığı" : countryName;
        String finalLeader = leaderName.isBlank() ? player.getName().getString() : leaderName;

        Country country = new Country(UUID.randomUUID(), finalName, finalLeader, false, color, banner);
        state.registerCountry(country, player.getUuid(), currentChunk);
        player.sendMessage(Text.literal(finalName + " ülkesi kuruldu!").formatted(Formatting.GREEN), false);
    }

    public static void claimCurrentChunk(ServerPlayerEntity player) {
        CountryState state = CountryState.getServerState(player.getServer());
        Country country = state.getCountryByPlayer(player.getUuid());
        if (country == null) {
            player.sendMessage(Text.literal("Önce N tuşuna basıp bir ülke kurmalısın!").formatted(Formatting.RED), false);
            return;
        }
        ChunkPos pos = player.getChunkPos();
        if (state.getCountryAt(pos) != null) {
            player.sendMessage(Text.literal("Bu chunk zaten sahiplenilmiş!").formatted(Formatting.RED), false);
            return;
        }
        if (!country.isAdjacentToClaim(pos)) {
            player.sendMessage(Text.literal("Yeni chunk mevcut topraklarına bitişik olmalı!").formatted(Formatting.RED), false);
            return;
        }
        if (!consumeGold(player, CLAIM_COST)) {
            player.sendMessage(Text.literal("Claim için 1 Altın Külçesi gerekli!").formatted(Formatting.RED), false);
            return;
        }
        state.claimChunk(country, pos);
        player.sendMessage(Text.literal("Chunk sahiplenildi! (" + pos.x + ", " + pos.z + ")").formatted(Formatting.GREEN), true);
    }

    public static void unclaimCurrentChunk(ServerPlayerEntity player) {
        CountryState state = CountryState.getServerState(player.getServer());
        Country country = state.getCountryByPlayer(player.getUuid());
        if (country == null) return;
        ChunkPos pos = player.getChunkPos();
        if (!country.hasChunk(pos)) {
            player.sendMessage(Text.literal("Bu chunk senin ülkene ait değil!").formatted(Formatting.RED), true);
            return;
        }
        if (country.getClaimedChunks().size() <= 1) {
            player.sendMessage(Text.literal("Son kalan başkent chunk'ını bırakamazsın!").formatted(Formatting.RED), true);
            return;
        }
        state.unclaimChunk(country, pos);
        player.sendMessage(Text.literal("Chunk bırakıldı!").formatted(Formatting.YELLOW), true);
    }

    public static void handleDiplomacy(ServerPlayerEntity player, UUID targetId, String action) {
        CountryState state = CountryState.getServerState(player.getServer());
        Country myCountry = state.getCountryByPlayer(player.getUuid());
        Country target = state.getCountryById(targetId);
        if (myCountry == null || target == null || myCountry.getId().equals(target.getId())) return;

        switch (action) {
            case "ALLY" -> {
                myCountry.setRelation(target.getId(), Country.Relation.ALLY);
                target.setRelation(myCountry.getId(), Country.Relation.ALLY);
                if (myCountry.canReceiveAllyGift(target.getId())) {
                    myCountry.markAllyGiftReceived(target.getId());
                    giveGold(player, ALLY_GIFT_AMOUNT);
                    player.sendMessage(Text.literal(target.getName() + " ile müttefik oldun! +100 Altın Külçesi hediye edildi.").formatted(Formatting.GREEN), false);
                } else {
                    player.sendMessage(Text.literal(target.getName() + " ile tekrar müttefik oldun. (Hediye daha önce alındı)").formatted(Formatting.AQUA), false);
                }
                state.markDirty();
            }
            case "ENEMY" -> {
                myCountry.setRelation(target.getId(), Country.Relation.ENEMY);
                target.setRelation(myCountry.getId(), Country.Relation.ENEMY);
                player.sendMessage(Text.literal(target.getName() + " ile ilişkiler bozuldu (Düşman)!").formatted(Formatting.GOLD), false);
                state.markDirty();
            }
            case "WAR" -> {
                if (myCountry.isPuppet()) {
                    player.sendMessage(Text.literal("Kukla devlet olduğun için kendi başına savaş ilan edemezsin!").formatted(Formatting.RED), false);
                    return;
                }
                WarManager.declareWar(player.getServer(), myCountry, target);
            }
            case "CALL_ALLY" -> {
                WarManager.callAllyToWar(player.getServer(), myCountry, target);
            }
        }
    }
}
