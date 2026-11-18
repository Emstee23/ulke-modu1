import net.minecraft.block.entity.BannerPatterns;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BannerPatternsComponent;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.*;

public class VillageCountryGenerator {

    private static final String[] PREFIXES = {"Kuzey", "Güney", "Doğu", "Batı", "Altın", "Demir", "Zümrüt", "Ulu", "Yeni", "Kadim"};
    private static final String[] SUFFIXES = {"Krallığı", "Cumhuriyeti", "Beyliği", "Dükalığı", "İmparatorluğu", "Birliği", "Devleti"};
    private static final String[] LEADERS = {"Hakan", "Börü", "Alparslan", "Kaan", "Demir", "Turgut", "Oğuz", "Selçuk", "Batu", "Ertuğrul"};
    private static final Item[] BANNERS = {
        Items.RED_BANNER, Items.BLUE_BANNER, Items.GREEN_BANNER, Items.YELLOW_BANNER,
        Items.PURPLE_BANNER, Items.CYAN_BANNER, Items.ORANGE_BANNER, Items.BLACK_BANNER
    };

    private static int tickCounter = 0;

    public static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < 100) return;
        tickCounter = 0;

        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) return;
        CountryState state = CountryState.getServerState(server);

        for (VillagerEntity villager : world.getEntitiesByType(net.minecraft.entity.EntityType.VILLAGER, v -> true)) {
            ChunkPos vChunk = villager.getChunkPos();
            if (state.getCountryAt(vChunk) != null) continue;

            boolean nearExistingNpc = false;
            for (Country c : state.getAllCountries()) {
                if (c.isNpc()) {
                    ChunkPos center = c.getCenterChunk();
                    int dist = Math.max(Math.abs(vChunk.x - center.x), Math.abs(vChunk.z - center.z));
                    if (dist < 14) {
                        nearExistingNpc = true;
                        break;
                    }
                }
            }
            if (!nearExistingNpc) {
                foundVillageCountry(world, state, vChunk);
            }
        }
    }

    private static void foundVillageCountry(ServerWorld world, CountryState state, ChunkPos center) {
        Random rand = new Random();
        String name = PREFIXES[rand.nextInt(PREFIXES.length)] + " Köy " + SUFFIXES[rand.nextInt(SUFFIXES.length)];
        String leader = LEADERS[rand.nextInt(LEADERS.length)] + " Bey";
        int color = 0xFF000000 | (rand.nextInt(180) + 60) << 16 | (rand.nextInt(180) + 60) << 8 | (rand.nextInt(180) + 60);

        ItemStack banner = createRandomBanner(world, rand);
        Country npcCountry = new Country(UUID.randomUUID(), name, leader, true, color, banner);
        state.registerCountry(npcCountry, null, null);

        int targetChunks = 50 + rand.nextInt(11);
        int claimed = 0;
        for (int radius = 0; radius <= 6 && claimed < targetChunks; radius++) {
            for (int dx = -radius; dx <= radius && claimed < targetChunks; dx++) {
                for (int dz = -radius; dz <= radius && claimed < targetChunks; dz++) {
                    ChunkPos target = new ChunkPos(center.x + dx, center.z + dz);
                    if (state.getCountryAt(target) == null) {
                        state.claimChunk(npcCountry, target);
                        claimed++;
                    }
                }
            }
        }
    }

    private static ItemStack createRandomBanner(ServerWorld world, Random rand) {
        ItemStack stack = new ItemStack(BANNERS[rand.nextInt(BANNERS.length)]);
        try {
            var patternRegistry = world.getRegistryManager().get(RegistryKeys.BANNER_PATTERN);
            var patterns = List.of(
                BannerPatterns.CROSS, BannerPatterns.STRIPE_CENTER, BannerPatterns.CIRCLE,
                BannerPatterns.RHOMBUS, BannerPatterns.TRIANGLE_TOP, BannerPatterns.BORDER
            );
            var chosen = patternRegistry.getEntry(patterns.get(rand.nextInt(patterns.size())));
            if (chosen.isPresent()) {
                DyeColor dye = DyeColor.values()[rand.nextInt(DyeColor.values().length)];
                BannerPatternsComponent comp = new BannerPatternsComponent.Builder().add(chosen.get(), dye).build();
                stack.set(DataComponentTypes.BANNER_PATTERNS, comp);
            }
        } catch (Exception ignored) {}
        return stack;
    }
}
