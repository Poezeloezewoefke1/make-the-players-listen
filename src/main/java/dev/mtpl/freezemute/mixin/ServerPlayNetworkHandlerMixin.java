package dev.mtpl.freezemute.mixin;

import dev.mtpl.freezemute.FreezeEnforcer;
import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.FrozenConnection;
import dev.mtpl.freezemute.ModerationData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The freeze itself.
 *
 * <p>Player movement in Minecraft is client driven: the client tells the server where it moved
 * to. Every one of those packets is dropped for a frozen player, so the server simply never
 * accepts a new position - no walking, sprinting, jumping, swimming, flying, elytra or riding.
 * Whenever the client reports that it drifted away from the position the server holds (or that
 * it turned its head), it is teleported back, which locks the view direction as well.
 *
 * <p>These handlers run on the netty thread first, so anything that touches the world is pushed
 * onto the server thread with {@code server.execute(...)}.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin implements FrozenConnection {
	@Shadow
	public ServerPlayerEntity player;

	@Shadow
	public abstract void requestTeleport(double x, double y, double z, float yaw, float pitch);

	@Unique
	private long freezemute$lastCorrectionNanos;

	@Override
	public void freezemute$snapBack() {
		ServerPlayerEntity target = this.player;

		if (target == null) {
			return;
		}

		target.setVelocity(0.0D, 0.0D, 0.0D);
		this.requestTeleport(target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
	}

	@Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
	private void freezemute$refuseMovement(PlayerMoveC2SPacket packet, CallbackInfo info) {
		ServerPlayerEntity target = this.player;

		if (!freezemute$isFrozen(target)) {
			return;
		}

		info.cancel();

		// NaN means "this packet did not carry that value".
		double x = packet.getX(Double.NaN);
		double y = packet.getY(Double.NaN);
		double z = packet.getZ(Double.NaN);
		float yaw = packet.getYaw(Float.NaN);
		float pitch = packet.getPitch(Float.NaN);

		MinecraftServer server = FreezeMute.server();

		if (server != null) {
			server.execute(() -> freezemute$correct(target, x, y, z, yaw, pitch));
		}
	}

	@Inject(method = "onVehicleMove", at = @At("HEAD"), cancellable = true)
	private void freezemute$refuseVehicleMovement(VehicleMoveC2SPacket packet, CallbackInfo info) {
		ServerPlayerEntity target = this.player;

		if (!freezemute$isFrozen(target)) {
			return;
		}

		info.cancel();

		MinecraftServer server = FreezeMute.server();

		if (server != null) {
			server.execute(() -> {
				if (freezemute$isFrozen(target) && target.hasVehicle()) {
					// A frozen player cannot drive a boat, horse or minecart out of the freeze.
					target.stopRiding();
					FreezeEnforcer.snapBack(target);
				}
			});
		}
	}

	@Inject(method = "onPlayerInput", at = @At("HEAD"), cancellable = true)
	private void freezemute$refuseInput(PlayerInputC2SPacket packet, CallbackInfo info) {
		if (freezemute$isFrozen(this.player)) {
			info.cancel();
		}
	}

	@Inject(method = "onClientCommand", at = @At("HEAD"), cancellable = true)
	private void freezemute$refuseClientCommand(ClientCommandC2SPacket packet, CallbackInfo info) {
		// Sneaking, sprinting, leaving a bed, horse jumps and starting elytra flight.
		if (freezemute$isFrozen(this.player)) {
			info.cancel();
		}
	}

	@Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
	private void freezemute$refuseItemUse(PlayerInteractItemC2SPacket packet, CallbackInfo info) {
		// Ender pearls and chorus fruit teleport a player server side, which would move them
		// out of the freeze, so using items is refused while frozen.
		if (freezemute$isFrozen(this.player)) {
			info.cancel();
		}
	}

	@Inject(method = "onSpectatorTeleport", at = @At("HEAD"), cancellable = true)
	private void freezemute$refuseSpectatorTeleport(SpectatorTeleportC2SPacket packet, CallbackInfo info) {
		if (freezemute$isFrozen(this.player)) {
			info.cancel();
		}
	}

	@Unique
	private void freezemute$correct(ServerPlayerEntity target, double x, double y, double z, float yaw, float pitch) {
		if (!freezemute$isFrozen(target) || !target.isAlive()) {
			return;
		}

		FreezeEnforcer.resetFloatingTicks((ServerPlayNetworkHandler) (Object) this);

		if (!FreezeEnforcer.hasDrifted(target, x, y, z, yaw, pitch)) {
			return;
		}

		long now = System.nanoTime();

		if (now - this.freezemute$lastCorrectionNanos < 45_000_000L) {
			// One correction per tick is enough, even when the client floods move packets.
			return;
		}

		this.freezemute$lastCorrectionNanos = now;
		this.freezemute$snapBack();
	}

	@Unique
	private boolean freezemute$isFrozen(ServerPlayerEntity target) {
		return target != null && ModerationData.get().isFrozen(target.getUuid());
	}
}
