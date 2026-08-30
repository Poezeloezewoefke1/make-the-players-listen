package dev.mtpl.freezemute.voice;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.lobby.LobbyManager;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;

/**
 * The Simple Voice Chat side of {@code /vcmute} and {@code /vcdeafen}.
 *
 * <p>This is the only class in the mod that touches a Simple Voice Chat type, and Simple Voice
 * Chat is what loads it - through the service file and the {@code voicechat} entrypoint - so on a
 * server without the voice chat mod this class is never touched and nothing breaks.
 *
 * <p>Two things are cancelled:
 *
 * <ul>
 *   <li>the microphone packet of a muted player, so nothing they say ever reaches the server;
 *   <li>every audio packet on its way to a deafened player, so they hear nobody.
 * </ul>
 *
 * <p>Outgoing packets are also dropped when they came from a muted player. Cancelling the
 * microphone packet already stops those from being produced, but audio can also be sent by other
 * plugins, and a mute should mean nobody hears them either way.
 *
 * <p>The lobby rides on the same two rules. A member's microphone is cancelled, so nobody hears
 * them, and audio from another member is cancelled on its way to them, so they hear nobody either.
 * Staff are never members, which means a member still hears staff - being able to talk to the room
 * you are holding people in is the point of holding them there.
 *
 * <p>That is deliberately stricter than text chat, where a member is heard by staff and nobody
 * else. Text chat is stopped once per receiver, so leaving one of them out is exact. Voice is
 * stopped at the microphone, before there are any receivers to be exact about, and the way to make
 * staff an exception would be to let a member's audio into the server and rely on the outgoing
 * filter to catch every path it could take to another member. A member who could be heard by the
 * room is a worse failure than one who has to type to reach staff, so the microphone stays shut.
 */
public class VoicePlugin implements VoicechatPlugin {
	/** Both registration routes are declared, so whichever one this build uses works. */
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();

	@Override
	public String getPluginId() {
		return FreezeMute.MOD_ID;
	}

	@Override
	public void initialize(VoicechatApi api) {
		VoiceSupport.markActive();
		FreezeMute.LOGGER.info("Voice chat plugin registered - /vcmute and /vcdeafen are live");
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		if (!REGISTERED.compareAndSet(false, true)) {
			// Registered through both the service file and the entrypoint on this build; once is
			// enough, and doing it twice would cancel every packet two times over.
			return;
		}

		registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
		registration.registerEvent(EntitySoundPacketEvent.class, this::onSound);
		registration.registerEvent(LocationalSoundPacketEvent.class, this::onSound);
		registration.registerEvent(StaticSoundPacketEvent.class, this::onSound);
		VoiceSupport.markActive();
	}

	private void onMicrophone(MicrophonePacketEvent event) {
		if (VoiceData.get().isEmpty() && LobbyManager.memberCount() == 0) {
			return;
		}

		UUID sender = uuidOf(event.getSenderConnection());

		if (sender == null) {
			return;
		}

		if (VoiceData.get().isMuted(sender) || LobbyManager.isMember(sender)) {
			event.cancel();
		}
	}

	private void onSound(SoundPacketEvent event) {
		if (VoiceData.get().isEmpty() && LobbyManager.memberCount() == 0) {
			return;
		}

		UUID receiver = uuidOf(event.getReceiverConnection());

		if (receiver != null && VoiceData.get().isDeafened(receiver)) {
			event.cancel();
			return;
		}

		UUID sender = uuidOf(event.getSenderConnection());

		if (sender == null) {
			return;
		}

		if (VoiceData.get().isMuted(sender)) {
			event.cancel();
			return;
		}

		// Member to member only: staff can still be heard in the lobby.
		if (receiver != null && LobbyManager.isMember(sender) && LobbyManager.isMember(receiver)) {
			event.cancel();
		}
	}

	private static UUID uuidOf(VoicechatConnection connection) {
		if (connection == null || connection.getPlayer() == null) {
			return null;
		}

		return connection.getPlayer().getUuid();
	}
}
