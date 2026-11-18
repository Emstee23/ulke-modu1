import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModNetworking {

    public record CreateCountryPayload(String name, String leader, ItemStack banner) implements CustomPayload {
        public static final Id<CreateCountryPayload> ID = new Id<>(Identifier.of("ulke_modu", "create_country"));
        public static final PacketCodec<RegistryByteBuf, CreateCountryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, CreateCountryPayload::name,
            PacketCodecs.STRING, CreateCountryPayload::leader,
            ItemStack.OPTIONAL_PACKET_CODEC, CreateCountryPayload::banner,
            CreateCountryPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ClaimActionPayload(boolean isClaim) implements CustomPayload {
        public static final Id<ClaimActionPayload> ID = new Id<>(Identifier.of("ulke_modu", "claim_action"));
        public static final PacketCodec<RegistryByteBuf, ClaimActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, ClaimActionPayload::isClaim,
            ClaimActionPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record DiplomacyActionPayload(String targetCountryUuid, String action) implements CustomPayload {
        public static final Id<DiplomacyActionPayload> ID = new Id<>(Identifier.of("ulke_modu", "diplomacy_action"));
        public static final PacketCodec<RegistryByteBuf, DiplomacyActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, DiplomacyActionPayload::targetCountryUuid,
            PacketCodecs.STRING, DiplomacyActionPayload::action,
            DiplomacyActionPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SyncMapPayload(List<CountrySyncData> countries, String playerCountryId, int warDeaths, int maxWarDeaths, boolean inWar, int prepSecondsLeft) implements CustomPayload {
        public static final Id<SyncMapPayload> ID = new Id<>(Identifier.of("ulke_modu", "sync_map"));
        public static final PacketCodec<RegistryByteBuf, SyncMapPayload> CODEC = PacketCodec.tuple(
            CountrySyncData.CODEC.collect(PacketCodecs.toList()), SyncMapPayload::countries,
            PacketCodecs.STRING, SyncMapPayload::playerCountryId,
            PacketCodecs.VAR_INT, SyncMapPayload::warDeaths,
            PacketCodecs.VAR_INT, SyncMapPayload::maxWarDeaths,
            PacketCodecs.BOOL, SyncMapPayload::inWar,
            PacketCodecs.VAR_INT, SyncMapPayload::prepSecondsLeft,
            SyncMapPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record RequestSyncPayload() implements CustomPayload {
        public static final Id<RequestSyncPayload> ID = new Id<>(Identifier.of("ulke_modu", "request_sync"));
        public static final PacketCodec<RegistryByteBuf, RequestSyncPayload> CODEC = PacketCodec.unit(new RequestSyncPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CountrySyncData(String id, String name, String leader, int color, ItemStack banner, List<Long> chunks, String relationToPlayer, boolean isPuppet) {
        public static final PacketCodec<RegistryByteBuf, CountrySyncData> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, CountrySyncData::id,
            PacketCodecs.STRING, CountrySyncData::name,
            PacketCodecs.STRING, CountrySyncData::leader,
            PacketCodecs.INT, CountrySyncData::color,
            ItemStack.OPTIONAL_PACKET_CODEC, CountrySyncData::banner,
            PacketCodecs.VAR_LONG.collect(PacketCodecs.toList()), CountrySyncData::chunks,
            PacketCodecs.STRING, CountrySyncData::relationToPlayer,
            PacketCodecs.BOOL, CountrySyncData::isPuppet,
            CountrySyncData::new
        );
    }

    public static void registerPackets() {
        PayloadTypeRegistry.playC2S().register(CreateCountryPayload.ID, CreateCountryPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClaimActionPayload.ID, ClaimActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DiplomacyActionPayload.ID, DiplomacyActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestSyncPayload.ID, RequestSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncMapPayload.ID, SyncMapPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CreateCountryPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                CountryManager.createCountry(context.player(), payload.name(), payload.leader(), payload.banner());
                sendSyncToPlayer(context.player());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ClaimActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (payload.isClaim()) CountryManager.claimCurrentChunk(context.player());
                else CountryManager.unclaimCurrentChunk(context.player());
                sendSyncToPlayer(context.player());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DiplomacyActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                CountryManager.handleDiplomacy(context.player(), UUID.fromString(payload.targetCountryUuid()), payload.action());
                sendSyncToPlayer(context.player());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestSyncPayload.ID, (payload, context) -> {
            context.server().execute(() -> sendSyncToPlayer(context.player()));
        });
    }

    public static void sendSyncToPlayer(ServerPlayerEntity player) {
        CountryState state = CountryState.getServerState(player.getServer());
        Country myCountry = state.getCountryByPlayer(player.getUuid());
        String myId = myCountry != null ? myCountry.getId().toString() : "";

        List<CountrySyncData> list = new ArrayList<>();
        for (Country c : state.getAllCountries()) {
            String rel = "NEUTRAL";
            if (myCountry != null && !myCountry.getId().equals(c.getId())) {
                rel = myCountry.getRelation(c.getId()).name();
            }
            list.add(new CountrySyncData(
                c.getId().toString(),
                c.getName(),
                c.getLeaderName(),
                c.getColor(),
                c.getBannerStack(),
                new ArrayList<>(c.getClaimedChunks()),
                rel,
                c.isPuppet()
            ));
        }

        WarManager.WarSession war = myCountry != null ? WarManager.getWarForCountry(myCountry.getId()) : null;
        boolean inWar = war != null;
        int deaths = inWar ? war.getDeaths(myCountry.getId()) : 0;
        int prepSec = inWar ? war.getPrepSecondsLeft() : 0;

        ServerPlayNetworking.send(player, new SyncMapPayload(list, myId, deaths, WarManager.MAX_DEATHS, inWar, prepSec));
    }
}
