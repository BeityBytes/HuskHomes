/*
 * This file is part of HuskHomes, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskhomes.command;

import net.william278.huskhomes.HuskHomes;
import net.william278.huskhomes.manager.RequestsManager;
import net.william278.huskhomes.teleport.TeleportRequest;
import net.william278.huskhomes.user.OnlineUser;
import net.william278.huskhomes.util.TransactionResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class TpRequestCommand extends InGameCommand implements UserListTabCompletable {

    private final TeleportRequest.Type requestType;

    protected TpRequestCommand(@NotNull HuskHomes plugin, @NotNull TeleportRequest.Type type) {
        super(
                List.of(type == TeleportRequest.Type.TPA ? "tpa" : "tpahere"),
                "<player>",
                plugin
        );
        this.requestType = type;
    }

    @Override
    public void execute(@NotNull OnlineUser onlineUser, @NotNull String[] args) {
        final RequestsManager manager = plugin.getManager().requests();
        if (manager.isIgnoringRequests(onlineUser)) {
            plugin.getLocales().getLocale("error_ignoring_teleport_requests")
                    .ifPresent(onlineUser::sendMessage);
            return;
        }

        final Optional<String> optionalTarget = parseStringArg(args, 0);
        if (optionalTarget.isEmpty()) {
            plugin.getLocales().getLocale("error_invalid_syntax", getUsage())
                    .ifPresent(onlineUser::sendMessage);
            return;
        }

        // Ensure the user does not send a request to themselves
        final String target = optionalTarget.get();
        if (target.equalsIgnoreCase(onlineUser.getName())) {
            plugin.getLocales().getLocale("error_teleport_request_self")
                    .ifPresent(onlineUser::sendMessage);
            return;
        }

        // Find the target user for position calculation
        Optional<OnlineUser> targetUser = plugin.getOnlineUser(target);
        if (targetUser.isEmpty()) {
            plugin.getLocales().getLocale("error_player_not_found", target)
                    .ifPresent(onlineUser::sendMessage);
            return;
        }

        // Handle economy-based teleport confirmation if needed
        if (plugin.getSettings().getEconomy().isEnabled() &&
            plugin.getTeleportConfirmations().map(teleportConfirmations ->
                teleportConfirmations.requiresConfirmation(onlineUser, TransactionResolver.Action.SEND_TELEPORT_REQUEST)).orElse(false)) {

            // Validate funds before showing confirmation prompt
            if (!plugin.validateTransaction(onlineUser, TransactionResolver.Action.SEND_TELEPORT_REQUEST,
                    onlineUser.getPosition(), targetUser.get().getPosition())) {
                return; // validateTransaction already shows "error_insufficient_funds" message
            }

            // Send confirmation prompt for distance-based costs
            plugin.getTeleportConfirmations().ifPresent(teleportConfirmations -> {
                teleportConfirmations.sendConfirmationPrompt(
                        onlineUser,
                        TransactionResolver.Action.SEND_TELEPORT_REQUEST,
                        onlineUser.getPosition(),
                        targetUser.get().getPosition(),
                        () -> {
                            // User confirmed, perform the actual transaction and send request
                            try {
                                // Perform the economy transaction
                                plugin.performTransaction(onlineUser, TransactionResolver.Action.SEND_TELEPORT_REQUEST,
                                        onlineUser.getPosition(), targetUser.get().getPosition());

                                // Transaction successful, send the request
                                manager.sendTeleportRequest(onlineUser, target, requestType, () -> handleSuccessfulRequest(onlineUser, target));
                            } catch (IllegalArgumentException e) {
                                plugin.getLocales().getLocale("error_player_not_found", target)
                                        .ifPresent(onlineUser::sendMessage);
                            }
                        }
                );
            });
        } else {
            // Economy is disabled or no confirmation needed, but still validate economy if enabled
            if (plugin.getSettings().getEconomy().isEnabled()) {
                // Validate economy without confirmation (direct charge)
                if (!plugin.validateTransaction(onlineUser, TransactionResolver.Action.SEND_TELEPORT_REQUEST,
                        onlineUser.getPosition(), targetUser.get().getPosition())) {
                    return; // validateTransaction already shows the error message
                }
                // Perform the transaction
                plugin.performTransaction(onlineUser, TransactionResolver.Action.SEND_TELEPORT_REQUEST,
                        onlineUser.getPosition(), targetUser.get().getPosition());
            }

            // Send the request
            try {
                manager.sendTeleportRequest(onlineUser, target, requestType, () -> handleSuccessfulRequest(onlineUser, target));
            } catch (IllegalArgumentException e) {
                plugin.getLocales().getLocale("error_player_not_found", target)
                        .ifPresent(onlineUser::sendMessage);
            }
        }
    }

    private void handleSuccessfulRequest(@NotNull OnlineUser onlineUser, @NotNull String target) {
        plugin.getLocales()
                .getLocale((requestType == TeleportRequest.Type.TPA ? "tpa" : "tpahere")
                        + "_request_sent", target)
                .ifPresent(onlineUser::sendMessage);
    }

}
