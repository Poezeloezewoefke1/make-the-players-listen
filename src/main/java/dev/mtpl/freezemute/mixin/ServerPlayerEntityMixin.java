package dev.mtpl.freezemute.mixin;

import java.util.UUID;

import dev.mtpl.freezemute.ModerationData;
import dev.mtpl.freezemute.ModerationData.MuteEntry;
import dev.mtpl.freezemute.lobby.LobbyManager;
import dev.mtpl.freezemute.util.Messages;
import dev.mtpl.freezemute.util.StaffAlerts;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SentMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The mute.
 *
 * <p>Chat is blocked where messages are handed to a player instead of where they arrive from
 * one. That matters: since 1.19 every chat message a client sends is part of a signature chain,
 * and silently throwing such a packet away breaks that chain, which makes the server kick the
 * player with "Chat validation failed". Here the message is validated by vanilla as usual and
 * only the delivery is dropped, so nobody ever sees it - not the other players, not the sender.
 *
 * <p>Blocking delivery also covers every command that produces chat: /msg, /tell, /w, /me,
 * /say and /teammsg all end up in this method.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
	@Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
	private void freezemute$dropMutedChat(SentMessage message, boolean filterMaskEnabled, MessageType.Parameters parameters, CallbackInfo info) {
		if (!(message instanceof SentMessage.Chat chat)) {
			// System messages and command output are never muted.
			return;
		}

		UUID sender = chat.message().getSender();

		if (sender == null) {
			return;
		}

		MuteEntry mute = ModerationData.get().muteOf(sender);
		boolean inLobby = LobbyManager.isMember(sender);

		if (mute == null && !inLobby) {
			return;
		}

		info.cancel();

		ServerPlayerEntity receiver = (ServerPlayerEntity) (Object) this;

		if (!sender.equals(receiver.getUuid())) {
			return;
		}

		// The sender is one of the receivers, which is a good moment to tell them why their
		// message disappeared - exactly once per message.
		if (mute != null) {
			receiver.sendMessage(Messages.youAreMuted(mute));
			StaffAlerts.mutedPlayerTriedToTalk(receiver, chat.message().getSignedContent());
			return;
		}

		receiver.sendMessage(Text.literal("Chat is off in the lobby. Staff can still hear you if you need them.")
				.formatted(Formatting.GRAY));
	}
}
