package dev.mtpl.freezemute.mixin;

import dev.mtpl.freezemute.FreezeEnforcer;
import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.ModerationData;
import dev.mtpl.freezemute.ModerationData.FreezeEntry;
import dev.mtpl.freezemute.ModerationData.MuteEntry;
import dev.mtpl.freezemute.util.Messages;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;

/** Reminds players that they are still frozen or muted when they log back in. */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
	@Inject(method = "onPlayerConnect", at = @At("TAIL"))
	private void freezemute$onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo info) {
		FreezeMute.rememberServer(((PlayerManager) (Object) this).getServer());

		ModerationData data = ModerationData.get();
		// Keep the stored name in sync so /unfreeze and /unmute keep working after a rename.
		data.refreshName(player.getUuid(), player.getGameProfile().name());

		FreezeEntry freeze = data.freezeOf(player.getUuid());

		if (freeze != null) {
			FreezeEnforcer.onRejoinedWhileFrozen(player);
			player.sendMessage(Messages.youAreStillFrozen(freeze));
		}

		MuteEntry mute = data.muteOf(player.getUuid());

		if (mute != null) {
			player.sendMessage(Messages.youAreMuted(mute));
		}
	}
}
