package com.mobcontrol.net;

import com.mobcontrol.MobRule;
import com.mobcontrol.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor -> cliente: dimensiones existentes + reglas (y opcionalmente abrir el panel). */
public class PanelPacket {
    public final boolean open;
    public final List<ResourceLocation> dims;
    public final List<MobRule> rules;

    public PanelPacket(boolean open, List<ResourceLocation> dims, List<MobRule> rules) {
        this.open = open;
        this.dims = dims;
        this.rules = rules;
    }

    public static void encode(PanelPacket p, FriendlyByteBuf b) {
        b.writeBoolean(p.open);
        b.writeVarInt(p.dims.size());
        for (ResourceLocation d : p.dims) b.writeResourceLocation(d);
        b.writeVarInt(p.rules.size());
        for (MobRule r : p.rules) r.write(b);
    }

    public static PanelPacket decode(FriendlyByteBuf b) {
        boolean open = b.readBoolean();
        int nd = Math.min(b.readVarInt(), 1000);
        List<ResourceLocation> dims = new ArrayList<>(nd);
        for (int i = 0; i < nd; i++) dims.add(b.readResourceLocation());
        int nr = Math.min(b.readVarInt(), 5000);
        List<MobRule> rules = new ArrayList<>(nr);
        for (int i = 0; i < nr; i++) rules.add(MobRule.read(b));
        return new PanelPacket(open, dims, rules);
    }

    public static void handle(PanelPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.receive(p.open, p.dims, p.rules)));
        ctx.get().setPacketHandled(true);
    }
}
