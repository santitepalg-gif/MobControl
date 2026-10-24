package com.mobcontrol;

import com.mobcontrol.net.Network;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MobControlMod.MODID)
public class Events {
    private static final String BLOCK_TAG = "mobcontrol_blocked";

    /** Datos del mundo cargado (null si no hay servidor). */
    public static RulesData data;

    private static MobRule ruleFor(net.minecraft.world.entity.EntityType<?> type) {
        if (data == null) return null;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return data.rules.get(id);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        data = RulesData.get(e.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        data = null;
    }

    /** Solo se bloquean aparicion "natural": no huevos, comandos, dispensadores, crianza, etc. */
    private static boolean naturalLike(MobSpawnType t) {
        return t == MobSpawnType.NATURAL
                || t == MobSpawnType.CHUNK_GENERATION
                || t == MobSpawnType.STRUCTURE
                || t == MobSpawnType.SPAWNER
                || t == MobSpawnType.PATROL
                || t == MobSpawnType.REINFORCEMENT
                || t == MobSpawnType.JOCKEY;
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!naturalLike(event.getSpawnType())) return;
        Mob mob = event.getEntity();
        MobRule rule = ruleFor(mob.getType());
        if (rule == null) return;

        boolean block = rule.spawnDisabled;
        if (!block && !rule.dims.isEmpty()) {
            ResourceLocation dim = event.getLevel().getLevel().dimension().location();
            MobRule.DimRule d = rule.dims.get(dim);
            if (d != null) {
                double y = event.getY();
                if (!d.allowed || y < d.minY || y > d.maxY) block = true;
            }
        }
        if (block) {
            mob.getPersistentData().putBoolean(BLOCK_TAG, true);
            event.setSpawnCancelled(true);
        }
    }

    /** Respaldo: si el mob quedo marcado como bloqueado, no entra al mundo. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity().getPersistentData().getBoolean(BLOCK_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity le = event.getEntity();
        if (le.level().isClientSide) return;
        MobRule rule = ruleFor(le.getType());
        if (rule == null) return;

        if (rule.removeDefaultDrops) event.getDrops().clear();

        RandomSource rnd = le.getRandom();
        for (MobRule.DropEntry d : rule.drops) {
            if (d.stack.isEmpty()) continue;
            if (rnd.nextInt(100) >= d.chance) continue;
            int n = d.min + (d.max > d.min ? rnd.nextInt(d.max - d.min + 1) : 0);
            if (n <= 0) continue;
            ItemStack s = d.stack.copy();
            s.setCount(Math.min(n, s.getMaxStackSize()));
            ItemEntity ie = new ItemEntity(le.level(), le.getX(), le.getY(), le.getZ(), s);
            ie.setDefaultPickUpDelay();
            event.getDrops().add(ie);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("mobcontrol")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Network.sendPanel(player, true);
                            return 1;
                        })
        );
    }
}
