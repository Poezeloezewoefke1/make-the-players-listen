package dev.mtpl.freezemute.mixin;

import dev.mtpl.freezemute.lobby.LobbyManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Two jobs, both on the way out of the server.
 *
 * <p>The first is the isolation. A player the client was never told about does not exist as far as
 * that client is concerned, so hiding lobby members from each other is a matter of refusing the one
 * packet that introduces them. Everything that follows - movement, animations, equipment - names an
 * entity id the client has never heard of and is dropped by vanilla without a word. That is far
 * less code, and far less to break between versions, than reaching into entity tracking, and it
 * leaves staff seeing everybody because the filter only applies between members.
 *
 * <p>The second is noticing a kick. A kicked player has to lose their grace window immediately -
 * otherwise kicking somebody who has gone quiet holds their slot for another five minutes and the
 * kick does nothing. A kick goes through {@code disconnect}; a timeout or a closed window does not,
 * which is exactly the distinction the grace window is built on.
 */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {
	@Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
	private void freezemute$hideOtherMembers(Packet<?> packet, CallbackInfo info) {
		if (!LobbyManager.isolating()) {
			return;
		}

		int entityId;

		if (packet instanceof EntitySpawnS2CPacket spawn) {
			entityId = spawn.getEntityId();
		} else if (packet instanceof PlaySoundFromEntityS2CPacket sound) {
			// Footsteps would give away a player nobody can see.
			entityId = sound.getEntityId();
		} else {
			return;
		}

		if (!LobbyManager.isHiddenEntity(entityId)) {
			return;
		}

		if (LobbyManager.hiddenFrom(entityId, freezemute$receiver())) {
			info.cancel();
		}
	}

	@Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
	private void freezemute$kicked(Text reason, CallbackInfo info) {
		ServerPlayerEntity player = freezemute$receiver();

		if (player != null) {
			LobbyManager.onKicked(player.getUuid(), player.getGameProfile().name());
		}
	}

	@Unique
	private ServerPlayerEntity freezemute$receiver() {
		return (Object) this instanceof ServerPlayNetworkHandler handler ? handler.player : null;
	}
}
