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
import net.william278.huskhomes.teleport.Teleportable;
import net.william278.huskhomes.user.CommandUser;
import net.william278.huskhomes.user.OnlineUser;
import net.william278.huskhomes.util.TransactionResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SpawnCommand extends Command implements TabCompletable {

    protected SpawnCommand(@NotNull HuskHomes plugin) {
        super(
                List.of("spawn"),
                "[player]",
                plugin
        );
        addAdditionalPermissions(Map.of("other", true));
    }

    @Override
    public void execute(@NotNull CommandUser executor, @NotNull String[] args) {
        boolean forceConfirm = hasConfirmFlag(args);
        String[] cleanArgs = removeConfirmFlag(args);

        final Optional<? extends Position> spawn = plugin.getSpawn();
        if (spawn.isEmpty()) {
            plugin.getLocales().getLocale("error_spawn_not_set")
                    .ifPresent(executor::sendMessage);
            return;
        }

        final Optional<Teleportable> optionalTeleporter = resolveTeleporter(executor, cleanArgs);
        if (optionalTeleporter.isEmpty()) {
            plugin.getLocales().getLocale("error_invalid_syntax", getUsage())
                    .ifPresent(executor::sendMessage);
            return;
        }

        this.teleportToSpawn(optionalTeleporter.get(), executor, spawn.get(), forceConfirm, cleanArgs);
    }

    public void teleportToSpawn(@NotNull Teleportable teleporter, @NotNull CommandUser executor,
                                @NotNull Position spawn, boolean forceConfirm, @NotNull String[] args) {
        if (!executor.equals(teleporter) && !executor.hasPermission(getPermission("other"))) {
            plugin.getLocales().getLocale("error_no_permission")
                    .ifPresent(executor::sendMessage);
            return;
        }

        // Handle economy-based teleport confirmation if needed
        if (executor instanceof OnlineUser onlineExecutor &&
            plugin.getSettings().getEconomy().isEnabled() &&
            plugin.getTeleportConfirmations().map(teleportConfirmations ->
                teleportConfirmations.requiresConfirmation(onlineExecutor, TransactionResolver.Action.SPAWN_TELEPORT)).orElse(false) &&
            teleporter instanceof OnlineUser onlineTeleporter && !forceConfirm) {

            // Validate funds before showing confirmation prompt
            if (!plugin.validateTransaction(onlineExecutor, TransactionResolver.Action.SPAWN_TELEPORT,
                    onlineExecutor.getPosition(), spawn)) {
                return; // validateTransaction already shows "error_insufficient_funds" message
            }

            // Send confirmation prompt for dynamic or static costs
            plugin.getTeleportConfirmations().ifPresent(teleportConfirmations -> {
                teleportConfirmations.sendConfirmationPrompt(
                        onlineExecutor,
                        TransactionResolver.Action.SPAWN_TELEPORT,
                        onlineExecutor.getPosition(),
                        spawn,
                        () -> {
                            Teleport.builder(plugin)
                                    .teleporter(teleporter)
                                    .actions(TransactionResolver.Action.SPAWN_TELEPORT)
                                    .target(spawn)
                                    .buildAndComplete(teleporter.equals(executor), args);
                        }
                );
            });
            return;
        }

        // Show cost information if using forceConfirm and economy is enabled
        if (forceConfirm && executor instanceof OnlineUser onlineExecutor &&
            plugin.getSettings().getEconomy().isEnabled() &&
            plugin.getTeleportConfirmations().map(confirmations ->
                confirmations.requiresConfirmation(onlineExecutor, TransactionResolver.Action.SPAWN_TELEPORT)).orElse(false)) {

            // Calculate and display cost information
            final double cost;
            if (plugin.getSettings().getEconomy().isDistanceBasedCostingEnabled(TransactionResolver.Action.SPAWN_TELEPORT)) {
                cost = plugin.calculateDistanceBasedCost(TransactionResolver.Action.SPAWN_TELEPORT, onlineExecutor.getPosition(), spawn);
            } else {
                cost = plugin.getSettings().getEconomy().getCost(TransactionResolver.Action.SPAWN_TELEPORT).orElse(0.0);
            }

            if (cost > 0) {
                String costInfo = plugin.getEconomyHook()
                    .map(hook -> hook.formatCurrency(cost))
                    .orElse(String.format("%.2f", cost));

                plugin.getLocales().getLocale("teleport_cost_bypass", costInfo)
                    .ifPresent(onlineExecutor::sendMessage);
            }
        }

        Teleport.builder(plugin)
                .teleporter(teleporter)
                .actions(TransactionResolver.Action.SPAWN_TELEPORT)
                .target(spawn)
                .buildAndComplete(teleporter.equals(executor), args);
    }

    // Legacy method for backward compatibility
    public void teleportToSpawn(@NotNull Teleportable teleporter, @NotNull CommandUser executor,
                                @NotNull Position spawn, @NotNull String[] args) {
        teleportToSpawn(teleporter, executor, spawn, false, args);
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
            case 2 -> {
                if (hasConfirmFlag(args) || args[1].equalsIgnoreCase("confirm")) {
                    yield List.of();
                }
                yield List.of("confirm");
            }
            default -> {
                // Always suggest "confirm" as the last argument
                if (!hasConfirmFlag(args)) {
                    yield List.of("confirm");
                }
                yield List.of();
            }
        };
    }

}
