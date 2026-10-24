package com.mobcontrol.client;

import com.mobcontrol.MobRule;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class ClientHooks {
    private ClientHooks() {}

    public static void receive(boolean open, List<ResourceLocation> dims, List<MobRule> rules) {
        Minecraft mc = Minecraft.getInstance();
        if (open) {
            mc.setScreen(new MobControlScreen(dims, rules));
        }
    }
}
