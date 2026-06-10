package com.clipfarms.noclip.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerAbilities;

public class Noclip extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Movement speed multiplier while noclipping.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(10.0)
        .build()
    );

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Fly: free 3D movement. Walk: phase through blocks with gravity.")
        .defaultValue(Mode.Fly)
        .build()
    );

    public final Setting<Boolean> noFall = sgGeneral.add(new BoolSetting.Builder()
        .name("no-fall")
        .description("Resets fall distance when you turn Noclip off.")
        .defaultValue(true)
        .build()
    );

    private boolean prevFlying = false;
    private float prevFlySpeed = 0.05f;

    public Noclip() {
        super(Categories.Movement, "Noclip", "Phase through blocks and entities.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        PlayerAbilities ab = mc.player.getAbilities();
        prevFlying   = ab.flying;
        prevFlySpeed = ab.getFlyingSpeed();
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;
        mc.player.noClip = false;
        PlayerAbilities ab = mc.player.getAbilities();
        ab.flying = prevFlying;
        ab.setFlyingSpeed(prevFlySpeed);
        if (noFall.get()) mc.player.fallDistance = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        mc.player.noClip = true;
        mc.player.fallDistance = 0;
        if (mode.get() == Mode.Fly) {
            PlayerAbilities ab = mc.player.getAbilities();
            ab.flying = true;
            ab.setFlyingSpeed((float)(0.05 * speed.get()));
        }
    }

    public enum Mode {
        Fly, Walk;
        @Override public String toString() { return name(); }
    }
}
