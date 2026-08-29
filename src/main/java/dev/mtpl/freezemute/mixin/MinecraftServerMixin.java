package dev.mtpl.freezemute.mixin;

import java.util.function.BooleanSupplier;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.lobby.LobbyDimension;
import dev.mtpl.freezemute.lobby.LobbyManager;
import dev.mtpl.freezemute.lobby.LobbyState;
import dev.mtpl.freezemute.lobby.LobbyTicker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
	@Unique
	private boolean freezemute$first = true;

	@Inject(method = "tick", at = @At("TAIL"))
	private void freezemute$tick(BooleanSupplier shouldKeepTicking, CallbackInfo info) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		FreezeMute.rememberServer(server);

		if (freezemute$first) {
			freezemute$first = false;
			LobbyDimension.reportOnce(server);
			// Has to happen whether or not the lobby is switched on: the team it cleans up is
			// stored in the world and outlives the run that filled it.
			LobbyManager.resetOnStartup(server);
		}

		LobbyTicker.tick(server);
	}

	/**
	 * The queue is written a second behind itself, so a clean stop has to catch up.
	 *
	 * <p>Writes are collapsed rather than done on every change, which is what keeps a hundred
	 * people disconnecting at once from turning into a hundred file writes. The cost of that is
	 * a second of lag between the state and the disk, and a stop that did not close it would
	 * throw away whatever happened in that second.
	 */
	@Inject(method = "shutdown", at = @At("HEAD"))
	private void freezemute$flushOnShutdown(CallbackInfo info) {
		LobbyState.get().flush();
	}
}
