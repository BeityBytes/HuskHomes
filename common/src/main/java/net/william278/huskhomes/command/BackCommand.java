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
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.teleport.Teleport;
import net.william278.huskhomes.user.CommandUser;
import net.william278.huskhomes.user.OnlineUser;
import net.william278.huskhomes.util.TransactionResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BackCommand extends InGameCommand implements TabCompletable {

    protected BackCommand(@NotNull HuskHomes plugin) {
        super(
                List.of("back"),
                "",
                plugin
        );
        addAdditionalPermissions(Map.of(
                "death", false,
                "previous", false
        ));
    }

    @Override
    public void execute(@NotNull OnlineUser executor, @NotNull String[] args) {
        boolean forceConfirm = hasConfirmFlag(args);

        final Optional<Position> lastPosition = plugin.getDatabase().getLastPosition(executor);
        if (lastPosition.isEmpty()) {
            plugin.getLocales().getLocale("error_no_last_position")
                    .ifPresent(executor::sendMessage);
            return;
        }

        // Handle economy-based teleport confirmation if needed
        if (plugin.getSettings().getEconomy().isEnabled() &&
            plugin.getTeleportConfirmations().map(teleportConfirmations ->
                teleportConfirmations.requiresConfirmation(executor, TransactionResolver.Action.BACK_COMMAND)).orElse(false) &&
            !forceConfirm) {

            // Validate funds before showing confirmation prompt
            if (!plugin.validateTransaction(executor, TransactionResolver.Action.BACK_COMMAND,
                    executor.getPosition(), lastPosition.get())) {
                return; // validateTransaction already shows "error_insufficient_funds" message
            }

            // Send confirmation prompt for dynamic or static costs
            plugin.getTeleportConfirmations().ifPresent(teleportConfirmations -> {
                teleportConfirmations.sendConfirmationPrompt(
                        executor,
                        TransactionResolver.Action.BACK_COMMAND,
                        executor.getPosition(),
                        lastPosition.get(),
                        () -> {
                            Teleport.builder(plugin)
                                    .teleporter(executor)
                                    .target(lastPosition.get())
                                    .actions(TransactionResolver.Action.BACK_COMMAND)
                                    .type(Teleport.Type.BACK)
                                    .buildAndComplete(true);
                        }
                );
            });
            return;
        }

        // Show cost information if using forceConfirm and economy is enabled
        if (forceConfirm && plugin.getSettings().getEconomy().isEnabled() &&
            plugin.getTeleportConfirmations().map(confirmations ->
                confirmations.requiresConfirmation(executor, TransactionResolver.Action.BACK_COMMAND)).orElse(false)) {

            // Calculate and display cost information
            final double cost;
            if (plugin.getSettings().getEconomy().isDistanceBasedCostingEnabled(TransactionResolver.Action.BACK_COMMAND)) {
                cost = plugin.calculateDistanceBasedCost(TransactionResolver.Action.BACK_COMMAND, executor.getPosition(), lastPosition.get());
            } else {
                cost = plugin.getSettings().getEconomy().getCost(TransactionResolver.Action.BACK_COMMAND).orElse(0.0);
            }

            if (cost > 0) {
                String costInfo = plugin.getEconomyHook()
                    .map(hook -> hook.formatCurrency(cost))
                    .orElse(String.format("%.2f", cost));

                plugin.getLocales().getLocale("teleport_cost_bypass", costInfo)
                    .ifPresent(executor::sendMessage);
            }
        }

        Teleport.builder(plugin)
                .teleporter(executor)
                .target(lastPosition.get())
                .actions(TransactionResolver.Action.BACK_COMMAND)
                .type(Teleport.Type.BACK)
                .buildAndComplete(forceConfirm);
    }

    @Override
    @NotNull
    public List<String> suggest(@NotNull CommandUser user, @NotNull String[] args) {
        return switch (args.length) {
            case 0, 1 -> {
                if (hasConfirmFlag(args)) {
                    yield List.of();
                }
                yield List.of("confirm");
            }
            default -> List.of();
        };
    }

}
