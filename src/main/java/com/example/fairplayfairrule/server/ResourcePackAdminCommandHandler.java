package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.compat.CommandFeedback;
import com.example.fairplayfairrule.compat.TextComponents;
import com.example.fairplayfairrule.resourcepack.ResourcePackHashCommandException;
import com.example.fairplayfairrule.resourcepack.ResourcePackHashCommandResult;
import com.example.fairplayfairrule.resourcepack.ResourcePackHashCommandService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

/** Registers the operator-only pack hash command and owns its server-scoped worker. */
public final class ResourcePackAdminCommandHandler {
    private static final String INPUT_DIRECTORY = "pack-hash-input";
    private static ResourcePackHashCommandService service;

    private ResourcePackAdminCommandHandler() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fpfr")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("packhash")
                        .then(Commands.argument("file-name", StringArgumentType.string())
                                .executes(context -> submit(context, false))
                                .then(Commands.argument("player-uuid", StringArgumentType.word())
                                        .executes(context -> submit(context, true))))));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        closeService();
    }

    private static int submit(CommandContext<CommandSourceStack> context, boolean hasPlayerUuid) {
        String fileName = StringArgumentType.getString(context, "file-name");
        String playerUuid = hasPlayerUuid
                ? StringArgumentType.getString(context, "player-uuid")
                : null;

        try {
            getService().hash(fileName, playerUuid).whenComplete((result, failure) ->
                    sendCompletion(context.getSource(), result, failure));
            return 1;
        } catch (ResourcePackHashCommandException exception) {
            context.getSource().sendFailure(TextComponents.literal(exception.getMessage()));
            return 0;
        }
    }

    private static void sendCompletion(CommandSourceStack source,
                                       ResourcePackHashCommandResult result,
                                       Throwable failure) {
        try {
            source.getServer().execute(() -> {
                String safeFailure = safeFailure(failure);
                if (safeFailure == null) {
                    CommandFeedback.success(source, result.formattedOutput());
                } else {
                    source.sendFailure(TextComponents.literal(safeFailure));
                }
            });
        } catch (RejectedExecutionException exception) {
            FairPlayFairRule.LOGGER.debug(
                    "Discarded administrator pack-hash output because the server is stopping");
        }
    }

    private static String safeFailure(Throwable failure) {
        if (failure == null) {
            return null;
        }
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause.getClass() == RuntimeException.class)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ResourcePackHashCommandException commandException) {
            return commandException.getMessage();
        }
        FairPlayFairRule.LOGGER.error("Administrator resource-pack hashing failed: {}",
                cause.getClass().getSimpleName());
        return "The resource-pack hash request failed unexpectedly.";
    }

    private static synchronized ResourcePackHashCommandService getService() {
        if (service == null) {
            Path input = FMLPaths.CONFIGDIR.get()
                    .resolve(FairPlayFairRule.MOD_ID)
                    .resolve(INPUT_DIRECTORY);
            service = new ResourcePackHashCommandService(input);
        }
        return service;
    }

    private static synchronized void closeService() {
        if (service != null) {
            service.close();
            service = null;
        }
    }
}
