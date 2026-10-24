package com.mobcontrol.net;

import com.mobcontrol.Events;
import com.mobcontrol.MobRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: guarda (reemplaza) la regla de un mob. Una regla vacia la borra. */
public class SaveRulePacket {
    public final MobRule rule;

    public SaveRulePacket(MobRule rule) {
        this.rule = rule;
    }

    public static void encode(SaveRulePacket p, FriendlyByteBuf b) {
        p.rule.write(b);
    }

    public static SaveRulePacket decode(FriendlyByteBuf b) {
        return new SaveRulePacket(MobRule.read(b));
    }

    public static void handle(SaveRulePacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sender == null || !sender.hasPermissions(2) || Events.data == null) return;
            MobRule rule = p.rule;
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rule.entity)) return;
            rule.sanitize();
            if (rule.isDefault()) {
                Events.data.rules.remove(rule.entity);
            } else {
                Events.data.rules.put(rule.entity, rule);
            }
            Events.data.setDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
