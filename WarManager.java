import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.*;

public class WarManager {

    public static final int PREP_TIME_TICKS = 20 * 120;
    public static final int MAX_DEATHS = 5;
    public static final int MAX_ACTIVE_GOLEMS = 4;
    public static final int PUPPET_TAX_INTERVAL = 20 * 300;

    public static class WarSession {
        public final UUID attackerId;
        public final UUID defenderId;
        public final Set<UUID> attackerAllies = new HashSet<>();
        public final Set<UUID> defenderAllies = new HashSet<>();
        public int prepTicksRemaining = PREP_TIME_TICKS;
        public final Map<UUID, Integer> deaths = new HashMap<>();
        public final List<UUID> activeEnemyEntities = new ArrayList<>();
        public int golemsRemainingPool = 6;
        public int spawnCooldown = 0;

        public WarSession(UUID attackerId, UUID defenderId) {
            this.attackerId = attackerId;
            this.defenderId = defenderId;
        }

        public boolean involves(UUID countryId) {
            return attackerId.equals(countryId) || defenderId.equals(countryId)
                || attackerAllies.contains(countryId) || defenderAllies.contains(countryId);
        }

        public int getDeaths(UUID countryId) {
            return deaths.getOrDefault(countryId, 0);
        }

        public int getPrepSecondsLeft() {
            return Math.max(0, prepTicksRemaining / 20);
        }
    }

    private static final List<WarSession> ACTIVE_WARS = new ArrayList<>();
    private static int taxTimer = 0;

    public static WarSession getWarForCountry(UUID countryId) {
        for (WarSession w : ACTIVE_WARS) {
            if (w.involves(countryId)) return w;
        }
        return null;
    }

    public static void declareWar(MinecraftServer server, Country attacker, Country defender) {
        if (getWarForCountry(attacker.getId()) != null || getWarForCountry(defender.getId()) != null) {
            broadcast(server, "Taraflardan biri zaten aktif bir savaşta!", Formatting.RED);
            return;
        }
        attacker.setRelation(defender.getId(), Country.Relation.WAR);
        defender.setRelation(attacker.getId(), Country.Relation.WAR);
        ACTIVE_WARS.add(new WarSession(attacker.getId(), defender.getId()));
        broadcast(server, "SAVAŞ İLANI: " + attacker.getName() + " vs " + defender.getName() + "! 2 dakika hazırlık süresi başladı.", Formatting.DARK_RED);
    }

    public static void callAllyToWar(MinecraftServer server, Country caller, Country ally) {
        WarSession war = getWarForCountry(caller.getId());
        if (war == null) return;
        if (caller.getRelation(ally.getId()) != Country.Relation.ALLY) return;

        if (war.attackerId.equals(caller.getId())) war.attackerAllies.add(ally.getId());
        else if (war.defenderId.equals(caller.getId())) war.defenderAllies.add(ally.getId());

        broadcast(server, ally.getName() + ", müttefiki " + caller.getName() + " çağrısıyla savaşa katıldı!", Formatting.GOLD);
    }

    public static void onPlayerDeath(ServerPlayerEntity player) {
        CountryState state = CountryState.getServerState(player.getServer());
        Country c = state.getCountryByPlayer(player.getUuid());
        if (c == null) return;

        WarSession war = getWarForCountry(c.getId());
        if (war == null || war.prepTicksRemaining > 0) return;

        int newDeaths = war.getDeaths(c.getId()) + 1;
        war.deaths.put(c.getId(), newDeaths);
        player.sendMessage(Text.literal("Savaş Ölüm Sayacı: " + newDeaths + "/" + MAX_DEATHS).formatted(Formatting.RED), false);

        if (newDeaths >= MAX_DEATHS) {
            UUID winnerId = war.attackerId.equals(c.getId()) ? war.defenderId : war.attackerId;
            endWar(player.getServer(), war, winnerId, c.getId(), false);
        }
    }

    public static void onWhiteBannerPlaced(ServerPlayerEntity player) {
        CountryState state = CountryState.getServerState(player.getServer());
        Country c = state.getCountryByPlayer(player.getUuid());
        if (c == null) return;

        WarSession war = getWarForCountry(c.getId());
        if (war == null) return;

        UUID winnerId = war.attackerId.equals(c.getId()) ? war.defenderId : war.attackerId;
        broadcast(player.getServer(), c.getName() + " beyaz flama dikerek teslim oldu!", Formatting.YELLOW);
        endWar(player.getServer(), war, winnerId, c.getId(), false);
    }

    public static void onServerTick(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) return;
        CountryState state = CountryState.getServerState(server);

        taxTimer++;
        if (taxTimer >= PUPPET_TAX_INTERVAL) {
            taxTimer = 0;
            processPuppetTaxes(server, state);
        }

        Iterator<WarSession> it = ACTIVE_WARS.iterator();
        while (it.hasNext()) {
            WarSession war = it.next();

            if (war.prepTicksRemaining > 0) {
                war.prepTicksRemaining--;
                if (war.prepTicksRemaining == 0) {
                    broadcast(server, "Hazırlık süresi bitti! Düşman birlikleri saldırıya geçti!", Formatting.RED);
                }
                continue;
            }

            war.activeEnemyEntities.removeIf(uuid -> world.getEntity(uuid) == null || !world.getEntity(uuid).isAlive());

            Country npcEnemy = getNpcParticipant(state, war);
            if (npcEnemy != null && war.golemsRemainingPool <= 0 && war.activeEnemyEntities.isEmpty()) {
                UUID playerWinner = war.attackerId.equals(npcEnemy.getId()) ? war.defenderId : war.attackerId;
                broadcast(server, npcEnemy.getName() + " golemleri tükendiği için beyaz flama dikip teslim oldu!", Formatting.GREEN);
                endWar(server, war, playerWinner, npcEnemy.getId(), true);
                it.remove();
                continue;
            }

            war.spawnCooldown--;
            if (war.spawnCooldown <= 0 && npcEnemy != null && war.golemsRemainingPool > 0) {
                war.spawnCooldown = 20 * 20;
                spawnRaiders(server, world, state, war);
            }
        }

        if (server.getTicks() % 20 == 0) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ModNetworking.sendSyncToPlayer(p);
            }
        }
    }

    private static void spawnRaiders(MinecraftServer server, ServerWorld world, CountryState state, WarSession war) {
        Country playerCountry = getPlayerParticipant(state, war);
        if (playerCountry == null) return;
        UUID pUuid = state.getPlayerByCountry(playerCountry.getId());
        ServerPlayerEntity targetPlayer = pUuid != null ? server.getPlayerManager().getPlayer(pUuid) : null;
        if (targetPlayer == null) return;

        long activeGolems = war.activeEnemyEntities.stream()
            .map(world::getEntity)
            .filter(e -> e instanceof IronGolemEntity)
            .count();

        BlockPos spawnBase = targetPlayer.getBlockPos().add(18, 0, 18);
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawnBase.getX(), spawnBase.getZ());
        BlockPos safePos = new BlockPos(spawnBase.getX(), topY, spawnBase.getZ());

        if (activeGolems < MAX_ACTIVE_GOLEMS && war.golemsRemainingPool > 0) {
            IronGolemEntity golem = EntityType.IRON_GOLEM.create(world);
            if (golem != null) {
                golem.refreshPositionAndAngles(safePos.getX(), safePos.getY(), safePos.getZ(), 0, 0);
                golem.setTarget(targetPlayer);
                golem.setCustomName(Text.literal("Düşman Muhafız Golemi").formatted(Formatting.RED));
                world.spawnEntity(golem);
                war.activeEnemyEntities.add(golem.getUuid());
                war.golemsRemainingPool--;
            }
        }

        for (int i = 0; i < 2; i++) {
            VindicatorEntity raider = EntityType.VINDICATOR.create(world);
            if (raider != null) {
                raider.refreshPositionAndAngles(safePos.getX() + i, safePos.getY(), safePos.getZ(), 0, 0);
                raider.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20.0);
                raider.setTarget(targetPlayer);
                raider.setCustomName(Text.literal("Düşman Köy Milisi").formatted(Formatting.GOLD));
                world.spawnEntity(raider);
                war.activeEnemyEntities.add(raider.getUuid());
            }
        }
    }

    private static void endWar(MinecraftServer server, WarSession war, UUID winnerId, UUID loserId, boolean removeFromList) {
        CountryState state = CountryState.getServerState(server);
        Country winner = state.getCountryById(winnerId);
        Country loser = state.getCountryById(loserId);

        if (winner != null && loser != null) {
            winner.setRelation(loser.getId(), Country.Relation.NEUTRAL);
            loser.setRelation(winner.getId(), Country.Relation.NEUTRAL);

            if (!winner.isNpc()) {
                state.annexCountry(winner, loser);
                broadcast(server, winner.getName() + " savaşı kazandı ve " + loser.getName() + " topraklarını ilhak etti!", Formatting.GREEN);
            } else {
                loser.setOverlordId(winner.getId());
                broadcast(server, winner.getName() + " savaşı kazandı! " + loser.getName() + " artık bir Kukla Devlet (düzenli altın vergisi ödeyecek).", Formatting.DARK_RED);
            }
            state.markDirty();
        }
        if (!removeFromList) ACTIVE_WARS.remove(war);
    }

    private static void processPuppetTaxes(MinecraftServer server, CountryState state) {
        for (Country c : state.getAllCountries()) {
            if (c.isPuppet()) {
                UUID pUuid = state.getPlayerByCountry(c.getId());
                ServerPlayerEntity player = pUuid != null ? server.getPlayerManager().getPlayer(pUuid) : null;
                if (player != null) {
                    CountryManager.consumeGold(player, 5);
                    player.sendMessage(Text.literal("Kukla devlet vergisi olarak 5 Altın Külçesi kesildi.").formatted(Formatting.GOLD), false);
                }
            }
        }
    }

    private static Country getNpcParticipant(CountryState state, WarSession war) {
        Country a = state.getCountryById(war.attackerId);
        if (a != null && a.isNpc()) return a;
        Country d = state.getCountryById(war.defenderId);
        if (d != null && d.isNpc()) return d;
        return null;
    }

    private static Country getPlayerParticipant(CountryState state, WarSession war) {
        Country a = state.getCountryById(war.attackerId);
        if (a != null && !a.isNpc()) return a;
        Country d = state.getCountryById(war.defenderId);
        if (d != null && !d.isNpc()) return d;
        return null;
    }

    private static void broadcast(MinecraftServer server, String msg, Formatting color) {
        server.getPlayerManager().broadcast(Text.literal("[Ülke Modu] " + msg).formatted(color), false);
    }
}
