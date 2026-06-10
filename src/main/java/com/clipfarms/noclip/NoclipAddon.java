package com.clipfarms.noclip;

import com.clipfarms.noclip.modules.Noclip;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoclipAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("NoclipAddon");

    @Override
    public void onInitialize() {
        LOG.info("Noclip Addon initialized.");
        Modules.get().add(new Noclip());
    }

    @Override
    public String getPackage() {
        return "com.clipfarms.noclip";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("clipfarms", "noclip-addon");
    }
}
