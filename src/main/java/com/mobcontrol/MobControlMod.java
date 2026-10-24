package com.mobcontrol;

import com.mobcontrol.net.Network;
import net.minecraftforge.fml.common.Mod;

@Mod(MobControlMod.MODID)
public class MobControlMod {
    public static final String MODID = "mobcontrol";

    public MobControlMod() {
        Network.register();
    }
}
