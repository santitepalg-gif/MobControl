package com.mobcontrol.net;

import com.mobcontrol.Events;
import com.mobcontrol.MobControlMod;
import com.mobcontrol.MobRule;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;

public final class Network {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MobControlMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private Network() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, PanelPacket.class, PanelPacket::encode, PanelPacket::decode, PanelPacket::handle);
        CHANNEL.registerMessage(id++, SaveRulePacket.class, SaveRulePacket::encode, SaveRulePacket::decode, SaveRulePacket::handle);
    }

    /** Manda al jugador las dimensiones del servidor y todas las reglas; si open=true abre el panel. */
    public static void sendPanel(ServerPlayer player, boolean open) {
        MinecraftServer server = player.getServer();
        if (server == null || Events.data == null) return;
        List<ResourceLocation> dims = new ArrayList<>();
        for (ResourceKey<Level> k : server.levelKeys()) dims.add(k.location());
        List<MobRule> rules = new ArrayList<>(Events.data.rules.values());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PanelPacket(open, dims, rules));
    }
}
