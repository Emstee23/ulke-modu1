import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

public class ProtectionHandler {

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient()) return true;
            if (!canModify(world.getServer(), player.getUuid(), new ChunkPos(pos))) {
                player.sendMessage(Text.literal("Bu toprak başka bir ülkeye ait!").formatted(Formatting.RED), true);
                return false;
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            ItemStack held = player.getStackInHand(hand);

            if (held.isOf(Items.WHITE_BANNER) && player instanceof ServerPlayerEntity sp) {
                WarManager.onWhiteBannerPlaced(sp);
            }

            ChunkPos targetChunk = new ChunkPos(hitResult.getBlockPos());
            boolean placingBlock = held.getItem() instanceof BlockItem;
            ChunkPos placeChunk = new ChunkPos(hitResult.getBlockPos().offset(hitResult.getSide()));

            BlockEntity be = world.getBlockEntity(hitResult.getBlockPos());
            if (be instanceof LockableContainerBlockEntity && !canModify(world.getServer(), player.getUuid(), targetChunk)) {
                player.sendMessage(Text.literal("Bu ülkenin sandıklarını açamazsın!").formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }

            if (placingBlock && !canModify(world.getServer(), player.getUuid(), placeChunk)) {
                player.sendMessage(Text.literal("Başka bir ülkenin sınırına blok koyamazsın!").formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });
    }

    private static boolean canModify(MinecraftServer server, UUID playerUuid, ChunkPos chunkPos) {
        if (server == null) return true;
        CountryState state = CountryState.getServerState(server);
        Country chunkOwner = state.getCountryAt(chunkPos);
        if (chunkOwner == null) return true;

        Country playerCountry = state.getCountryByPlayer(playerUuid);
        return playerCountry != null && playerCountry.getId().equals(chunkOwner.getId());
    }
}
