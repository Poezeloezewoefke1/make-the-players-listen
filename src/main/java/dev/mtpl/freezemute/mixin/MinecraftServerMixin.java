package dev.mtpl.freezemute.mixin;

import java.util.function.BooleanSupplier;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.lobby.LobbyDimension;
import dev.mtpl.freezemute.lobby.LobbyTicker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

/**
 * The lobby's heartbeat.
 *
 * <p>The queue, the grace windows and the parkour timers all need a clock, and a server tick is
 * the only clock that matters. Fabric API has an event for this; the mod hooks the method itself
 * so it keeps depending on nothing but the loader.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void freezemute$tick(BooleanSupplier shouldKeepTicking, CallbackInfo info) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		FreezeMute.rememberServer(server);
		LobbyDimension.reportOnce(server);
		LobbyTicker.tick(server);
	}
}
