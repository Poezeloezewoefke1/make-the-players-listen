package dev.mtpl.freezemute.mixin;

import com.mojang.brigadier.CommandDispatcher;

import dev.mtpl.freezemute.command.ModCommands;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Registers the commands.
 *
 * <p>Fabric API has an event for this, but this mod deliberately depends on Fabric Loader only,
 * so it hooks the same spot Fabric API does: right after vanilla filled the dispatcher and just
 * before brigadier is finalised.
 */
@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {
	@Shadow
	public abstract CommandDispatcher<ServerCommandSource> getDispatcher();

	@Inject(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/brigadier/CommandDispatcher;setConsumer(Lcom/mojang/brigadier/ResultConsumer;)V"))
	private void freezemute$registerCommands(CommandManager.RegistrationEnvironment environment, CommandRegistryAccess registryAccess, CallbackInfo info) {
		ModCommands.register(this.getDispatcher());
	}
}
