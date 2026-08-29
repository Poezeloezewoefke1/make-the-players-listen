package dev.mtpl.freezemute.mixin;

import dev.mtpl.freezemute.FreezeEnforcer;
import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.ModerationData;
import dev.mtpl.freezemute.ModerationData.FreezeEntry;
import dev.mtpl.freezemute.ModerationData.MuteEntry;
import dev.mtpl.freezemute.lobby.LobbyManager;
import dev.mtpl.freezemute.util.Messages;
import dev.mtpl.freezemute.util.StaffAlerts;
import dev.mtpl.freezemute.voice.VoiceData;
import dev.mtpl.freezemute.voice.VoiceData.Kind;
import dev.mtpl.freezemute.voice.VoiceData.VoiceEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * What happens when somebody arrives and when they go.
 *
 * <p>Arriving means being reminded of a punishment that is still running, and - when the lobby is
 * switched on - being routed to wherever they belong. Going means the grace clocks start.
 */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
	@Inject(method = "onPlayerConnect", at = @At("TAIL"))
	private void freezemute$onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo info) {
		FreezeMute.rememberServer(((PlayerManager) (Object) this).getServer());

		ModerationData data = ModerationData.get();
		// Keep the stored name in sync so /unfreeze and /unmute keep working after a rename.
		data.refreshName(player.getUuid(), player.getGameProfile().name());
		VoiceData.get().refreshName(player.getUuid(), player.getGameProfile().name());

		FreezeEntry freeze = data.freezeOf(player.getUuid());

		if (freeze != null) {
			FreezeEnforcer.onRejoinedWhileFrozen(player);
			player.sendMessage(Messages.youAreStillFrozen(freeze));
		}

		MuteEntry mute = data.muteOf(player.getUuid());

		if (mute != null) {
			player.sendMessage(Messages.youAreMuted(mute));
		}

		for (Kind kind : Kind.values()) {
			VoiceEntry voice = VoiceData.get().entryOf(kind, player.getUuid());

			if (voice != null) {
				player.sendMessage(Text.literal("You are still " + kind.past() + " in voice chat "
						+ Messages.describeRemaining(voice.until()) + "."));
			}
		}

		// Last, because it may move the player to another dimension.
		LobbyManager.onJoin(((PlayerManager) (Object) this).getServer(), player);
	}

	/**
	 * A player is leaving.
	 *
	 * <p>Their place in line and their slot are both kept, marked with the moment they went, and
	 * swept once the grace window runs out. Nothing here can tell a crash from a quit, which is
	 * exactly why the window exists.
	 */
	@Inject(method = "remove", at = @At("HEAD"))
	private void freezemute$onPlayerLeave(ServerPlayerEntity player, CallbackInfo info) {
		LobbyManager.onLeave(player);
		// The throttle that stops staff being told the same thing twice keeps one entry per
		// player, and until now only lifting the punishment cleared it.
		StaffAlerts.forget(player.getUuid());
	}
}
