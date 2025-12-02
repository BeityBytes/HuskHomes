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

package net.william278.huskhomes.util;

import lombok.Getter;
import lombok.NoArgsConstructor;
import net.william278.huskhomes.HuskHomes;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles teleport confirmation prompts and timeouts.
 */
public class TeleportConfirmations {

    private final HuskHomes plugin;
    private final Map<UUID, PendingConfirmation> pendingConfirmations;
    private final ScheduledExecutorService scheduler;

    public TeleportConfirmations(@NotNull HuskHomes plugin) {
        this.plugin = plugin;
        this.pendingConfirmations = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * Check if a user needs to confirm a teleport.
     *
     * @param user   the user
     * @param action the teleport action
     * @return true if confirmation is needed, false otherwise
     */
    public boolean requiresConfirmation(@NotNull OnlineUser user, @NotNull TransactionResolver.Action action) {
        return plugin.getSettings().getEconomy().isTeleportConfirmationEnabled(action);
    }

    /**
     * Check if a user has a pending confirmation.
     *
     * @param user the user
     * @return true if user has a pending confirmation, false otherwise
     */
    public boolean hasPendingConfirmation(@NotNull OnlineUser user) {
        return pendingConfirmations.containsKey(user.getUuid());
    }

    /**
     * Send a teleport confirmation prompt to the user.
     *
     * @param user          the user
     * @param action        the teleport action
     * @param fromPosition  the starting position
     * @param toPosition    the destination position
     * @param teleportTask  the task to execute if confirmed
     */
    public void sendConfirmationPrompt(@NotNull OnlineUser user, @NotNull TransactionResolver.Action action,
                                     @NotNull Position fromPosition, @NotNull Position toPosition,
                                     @NotNull Runnable teleportTask) {
        if (hasPendingConfirmation(user)) {
            plugin.getLocales().getLocale("error_teleport_already_pending")
                    .ifPresent(user::sendMessage);
            return;
        }

        double cost = 0;
        boolean showCost = plugin.getSettings().getEconomy().getTeleportConfirmations().isShowCost();

        // Calculate cost if distance-based costing is enabled
        if (plugin.getSettings().getEconomy().isDistanceBasedCostingEnabled(action)) {
            cost = plugin.calculateDistanceBasedCost(action, fromPosition, toPosition);
        } else {
            // Use static cost
            cost = plugin.getSettings().getEconomy().getCost(action).orElse(0.0);
        }

        double distance = DistanceCalculator.calculateDistance(
                fromPosition, toPosition,
                plugin.getSettings().getEconomy().getDistanceBasedCosts().isUse3dDistance()
        );

        // Send confirmation message
        sendConfirmationMessage(user, action, cost, distance);

        // Create pending confirmation
        PendingConfirmation confirmation = new PendingConfirmation(
                user.getUuid(), action, fromPosition, toPosition,
                teleportTask, cost
        );

        pendingConfirmations.put(user.getUuid(), confirmation);

        // Schedule timeout if configured
        int timeoutSeconds = plugin.getSettings().getEconomy().getTeleportConfirmations().getTimeoutSeconds();
        if (timeoutSeconds > 0) {
            scheduler.schedule(() -> {
                if (pendingConfirmations.containsKey(user.getUuid())) {
                    cancelConfirmation(user, true);
                }
            }, timeoutSeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * Confirm a pending teleport.
     *
     * @param user the user
     * @return true if confirmation was successful, false if no pending confirmation
     */
    public boolean confirmTeleport(@NotNull OnlineUser user) {
        PendingConfirmation confirmation = pendingConfirmations.get(user.getUuid());
        if (confirmation == null) {
            plugin.getLocales().getLocale("error_no_pending_teleport")
                    .ifPresent(user::sendMessage);
            return false;
        }

        // Execute the teleport
        confirmation.getTeleportTask().run();

        // Remove from pending
        pendingConfirmations.remove(user.getUuid());

        // Send confirmation message
        plugin.getLocales().getLocale("teleport_confirmed")
                .ifPresent(user::sendMessage);

        return true;
    }

    /**
     * Cancel a pending teleport.
     *
     * @param user     the user
     * @param timeout  whether the cancellation is due to timeout
     */
    public void cancelConfirmation(@NotNull OnlineUser user, boolean timeout) {
        PendingConfirmation confirmation = pendingConfirmations.get(user.getUuid());
        if (confirmation == null) {
            return;
        }

        pendingConfirmations.remove(user.getUuid());

        String messageKey = timeout ? "teleport_confirmation_timeout" : "teleport_cancelled";
        plugin.getLocales().getLocale(messageKey)
                .ifPresent(user::sendMessage);
    }

    /**
     * Cancel a pending teleport.
     *
     * @param user the user
     */
    public void cancelConfirmation(@NotNull OnlineUser user) {
        cancelConfirmation(user, false);
    }

    /**
     * Get pending confirmation for a user.
     *
     * @param user the user
     * @return the pending confirmation, or null if none
     */
    @Nullable
    public PendingConfirmation getPendingConfirmation(@NotNull OnlineUser user) {
        return pendingConfirmations.get(user.getUuid());
    }

    /**
     * Send the confirmation message to the user.
     */
    private void sendConfirmationMessage(@NotNull OnlineUser user, @NotNull TransactionResolver.Action action,
                                       double cost, double distance) {
        String costText = "";
        if (cost > 0 && plugin.getSettings().getEconomy().getTeleportConfirmations().isShowCost()) {
            final String costMessage = plugin.getLocales().getRawLocale("teleport_confirmation_cost",
                            plugin.getEconomyHook()
                                    .map(hook -> hook.formatCurrency(cost))
                                    .orElse(String.format("%.2f", cost)))
                    .orElse("0.00");
            costText = " " + costMessage;
        }

        String distanceText = "";
        if (distance >= 0) {
            distanceText = " (" + DistanceCalculator.formatDistance(distance) + ")";
        }

        plugin.getLocales().getLocale("teleport_confirmation_prompt",
                        action.name().toLowerCase().replace("_", " "),
                        distanceText,
                        costText)
                .ifPresent(message -> user.sendMessage(message));

        plugin.getLocales().getLocale("teleport_confirmation_instructions")
                .ifPresent(user::sendMessage);
    }

    /**
     * Clean up resources.
     */
    public void shutdown() {
        scheduler.shutdown();
        pendingConfirmations.clear();
    }

    /**
     * Represents a pending teleport confirmation.
     */
    @Getter
    @NoArgsConstructor
    public static class PendingConfirmation {
        private UUID userUuid;
        private TransactionResolver.Action action;
        private Position fromPosition;
        private Position toPosition;
        private Runnable teleportTask;
        private double cost;
        private long creationTime;

        public PendingConfirmation(@NotNull UUID userUuid, @NotNull TransactionResolver.Action action,
                                @NotNull Position fromPosition, @NotNull Position toPosition,
                                @NotNull Runnable teleportTask, double cost) {
            this.userUuid = userUuid;
            this.action = action;
            this.fromPosition = fromPosition;
            this.toPosition = toPosition;
            this.teleportTask = teleportTask;
            this.cost = cost;
            this.creationTime = System.currentTimeMillis();
        }
    }
}