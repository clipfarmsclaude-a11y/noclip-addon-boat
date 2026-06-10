package com.clipfarms.noclip.modules;

import meteordevelopment.meteorclient.events.entity.BoatMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * BoatNoclip — Boat fly with noclip (phase through ground and blocks).
 *
 * Extends Meteor's existing BoatFly concept with:
 *   • noclip  — phases the boat (and rider) through all blocks
 *   • downKey — configurable keybind to descend (up is always Space / Jump)
 *   • speed   — horizontal speed multiplier
 *   • verticalSpeed — how fast up/down movement is
 *   • antiKick — periodically dips slightly to prevent server kick
 *   • noFall  — zeroes fall distance so you don't die on dismount
 */
public class BoatNoclip extends Module {

    // ── Setting groups ────────────────────────────────────────────────────────

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgAntiKick = settings.createGroup("Anti-Kick");

    // ── General settings ──────────────────────────────────────────────────────

    public final Setting<Boolean> noclip = sgGeneral.add(new BoolSetting.Builder()
        .name("noclip")
        .description("Phase the boat through blocks and the ground.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> noFall = sgGeneral.add(new BoolSetting.Builder()
        .name("no-fall")
        .description("Zero fall distance when you disable the module so you don't die.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> cancelServerPackets = sgGeneral.add(new BoolSetting.Builder()
        .name("cancel-server-packets")
        .description("Cancel incoming vehicle-move packets so the server can't rubber-band you.")
        .defaultValue(true)
        .build()
    );

    // ── Movement settings ─────────────────────────────────────────────────────

    public final Setting<Double> speed = sgMovement.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Horizontal movement speed (blocks per tick).")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(10.0)
        .build()
    );

    public final Setting<Double> verticalSpeed = sgMovement.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical speed when pressing up (Space) or the down key.")
        .defaultValue(0.5)
        .min(0.05)
        .sliderMax(5.0)
        .build()
    );

    public final Setting<Integer> downKey = sgMovement.add(new IntSetting.Builder()
        .name("down-key")
        .description("GLFW key code used to descend. Default: Left Shift (GLFW 340). " +
                     "Common codes — LShift: 340, LCtrl: 341, X: 88, C: 67, Z: 90.")
        .defaultValue(GLFW.GLFW_KEY_LEFT_SHIFT)
        .min(0)
        .sliderMax(400)
        .build()
    );

    // ── Anti-kick settings ────────────────────────────────────────────────────

    public final Setting<Boolean> antiKick = sgAntiKick.add(new BoolSetting.Builder()
        .name("anti-kick")
        .description("Periodically move down a tiny bit to prevent the server kicking you for flying.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> antiKickDelay = sgAntiKick.add(new IntSetting.Builder()
        .name("delay")
        .description("How many ticks between each anti-kick dip.")
        .defaultValue(40)
        .min(5)
        .sliderMax(200)
        .visible(antiKick::get)
        .build()
    );

    // ── State ─────────────────────────────────────────────────────────────────

    private int ticksSinceKickDip = 0;
    private boolean dipping        = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public BoatNoclip() {
        super(Categories.Movement, "boat-noclip",
              "Fly a boat through blocks. Space = up, configurable key = down.");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;

        // Restore collision on the player
        mc.player.noClip = false;

        // Restore vehicle collision too, if still in a boat
        if (mc.player.getVehicle() != null) {
            mc.player.getVehicle().noClip = false;
        }

        if (noFall.get()) {
            mc.player.fallDistance = 0;
        }

        ticksSinceKickDip = 0;
        dipping = false;
    }

    // ── Tick: handle up/down input and anti-kick ──────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Must be riding a vehicle (boat)
        var vehicle = mc.player.getVehicle();
        if (vehicle == null) return;

        // Apply noclip to both player and vehicle every tick
        if (noclip.get()) {
            mc.player.noClip  = true;
            vehicle.noClip    = true;
        }

        // Zero fall distance so landing after dismount is safe
        mc.player.fallDistance = 0;
        vehicle.fallDistance   = 0;

        // Anti-kick logic — dip briefly every N ticks
        if (antiKick.get()) {
            ticksSinceKickDip++;
            if (ticksSinceKickDip >= antiKickDelay.get()) {
                ticksSinceKickDip = 0;
                dipping = true;
            }
        }
    }

    // ── BoatMoveEvent: apply horizontal speed + vertical input ────────────────

    @EventHandler
    private void onBoatMove(BoatMoveEvent event) {
        if (mc.player == null) return;
        if (mc.player.getVehicle() != event.boat) return;

        Vec3d vel = event.boat.getVelocity();

        // ── Horizontal: scale the XZ movement by the speed setting ──────────
        double hSpeed = speed.get();
        double vx = vel.x;
        double vz = vel.z;

        // If the boat is actually moving horizontally, normalise and rescale
        double hLen = Math.sqrt(vx * vx + vz * vz);
        if (hLen > 1e-5) {
            vx = (vx / hLen) * hSpeed * 0.1;
            vz = (vz / hLen) * hSpeed * 0.1;
        }

        // ── Vertical ─────────────────────────────────────────────────────────
        double vy = 0;

        boolean jumpPressed = mc.options.jumpKey.isPressed();
        boolean downPressed = Input.isKeyPressed(downKey.get());

        if (jumpPressed && !downPressed) {
            vy = verticalSpeed.get() * 0.1;
        } else if (downPressed && !jumpPressed) {
            vy = -verticalSpeed.get() * 0.1;
        } else if (dipping) {
            // Anti-kick micro-dip
            vy = -0.03;
            dipping = false;
        } else {
            // Hold position — counteract gravity
            vy = 0;
        }

        event.boat.setVelocity(vx, vy, vz);
    }

    // ── Cancel server rubber-band packets ─────────────────────────────────────

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!cancelServerPackets.get()) return;
        if (mc.player == null || mc.player.getVehicle() == null) return;

        if (event.packet instanceof VehicleMoveS2CPacket) {
            event.cancel();
        }
    }
}
