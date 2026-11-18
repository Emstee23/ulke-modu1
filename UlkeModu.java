import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public class UlkeModu implements ModInitializer {
    @Override
    public void onInitialize() {
        ModNetworking.registerPackets();
        ProtectionHandler.register();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            VillageCountryGenerator.onServerTick(server);
            WarManager.onServerTick(server);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                WarManager.onPlayerDeath(player);
            }
        });
    }
}
