package top.ourisland.diakoutils;

import net.minecraft.server.MinecraftServer;

public interface TickingModule {

    void onEndServerTick(MinecraftServer server);

}
