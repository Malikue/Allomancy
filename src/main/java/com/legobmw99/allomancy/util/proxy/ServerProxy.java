package com.legobmw99.allomancy.util.proxy;


import net.minecraft.world.World;

public class ServerProxy extends CommonProxy {
    @Override
    public World getClientWorld() {
        throw new IllegalStateException("Only run this on the client!");
    }
}