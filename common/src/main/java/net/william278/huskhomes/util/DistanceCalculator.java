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

import net.william278.huskhomes.position.Location;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.position.World;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for calculating distances between positions for cost calculations.
 */
public final class DistanceCalculator {

    /**
     * Calculate the distance between two positions for teleportation cost calculation.
     *
     * @param from      the starting position
     * @param to        the destination position
     * @param use3d     whether to use 3D distance (including Y-axis) or 2D distance (X-Z plane only)
     * @return the calculated distance in blocks
     */
    public static double calculateDistance(@NotNull Position from, @NotNull Position to, boolean use3d) {
        // Calculate distance even between dimensions - we'll add inter-dimensional fee later
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance2d = Math.sqrt(dx * dx + dz * dz);

        // If 3D distance is enabled, include Y-axis
        if (use3d) {
            double dy = to.getY() - from.getY();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        return distance2d;
    }

    /**
     * Calculate the distance between two positions using 2D distance (X-Z plane only).
     *
     * @param from the starting position
     * @param to   the destination position
     * @return the calculated distance in blocks, or -1 for inter-dimensional teleport
     */
    public static double calculateDistance2d(@NotNull Position from, @NotNull Position to) {
        return calculateDistance(from, to, false);
    }

    /**
     * Calculate the distance between two positions using 3D distance (including Y-axis).
     *
     * @param from the starting position
     * @param to   the destination position
     * @return the calculated distance in blocks, or -1 for inter-dimensional teleport
     */
    public static double calculateDistance3d(@NotNull Position from, @NotNull Position to) {
        return calculateDistance(from, to, true);
    }

    /**
     * Check if teleportation between two positions is inter-dimensional.
     *
     * @param from the starting position
     * @param to   the destination position
     * @return true if positions are in different dimensions, false otherwise
     */
    public static boolean isInterDimensional(@NotNull Position from, @NotNull Position to) {
        return !from.getWorld().equals(to.getWorld());
    }

    /**
     * Get the environment name for a world.
     *
     * @param world the world
     * @return the environment name
     */
    public static String getEnvironmentName(@NotNull World world) {
        return world.getEnvironment().name().toLowerCase();
    }

    /**
     * Check if two positions are in different environment types (overworld, nether, end).
     *
     * @param from the starting position
     * @param to   the destination position
     * @return true if positions are in different environment types, false otherwise
     */
    public static boolean isDifferentEnvironment(@NotNull Position from, @NotNull Position to) {
        return !from.getWorld().getEnvironment().equals(to.getWorld().getEnvironment());
    }

    /**
     * Format distance into a human-readable string.
     *
     * @param distance the distance in blocks
     * @return formatted distance string
     */
    public static String formatDistance(double distance) {
        if (distance < 1000) {
            return String.format("%.0f blocks", distance);
        } else {
            return String.format("%.1fK blocks", distance / 1000);
        }
    }

    /**
     * Calculate the base teleportation cost based on distance and settings.
     *
     * @param distance       the distance in blocks
     * @param costPerBlock   the cost per block
     * @param minimumCost    the minimum cost
     * @param maximumCost    the maximum cost (0 = no maximum)
     * @return the calculated cost
     */
    public static double calculateBaseCost(double distance, double costPerBlock, double minimumCost, double maximumCost) {
        if (distance <= 0) {
            return 0;
        }

        double cost = distance * costPerBlock;

        // Apply minimum cost
        if (minimumCost > 0 && cost < minimumCost) {
            cost = minimumCost;
        }

        // Apply maximum cost
        if (maximumCost > 0 && cost > maximumCost) {
            cost = maximumCost;
        }

        // Round to 2 decimal places
        return Math.round(cost * 100.0) / 100.0;
    }

    /**
     * Calculate the total teleportation cost including inter-dimensional fees.
     *
     * @param distance              the distance in blocks
     * @param costPerBlock         the cost per block
     * @param interDimensionalFee  the inter-dimensional fee
     * @param minimumCost         the minimum cost
     * @param maximumCost         the maximum cost (0 = no maximum)
     * @param applyInterDimensionalFee whether to apply inter-dimensional fees
     * @param isInterDimensional   whether the teleport is inter-dimensional
     * @return the calculated total cost
     */
    public static double calculateTotalCost(double distance, double costPerBlock, double interDimensionalFee,
                                           double minimumCost, double maximumCost, boolean applyInterDimensionalFee,
                                           boolean isInterDimensional) {
        double baseCost = 0;

        // Calculate distance-based cost (for all teleports with distance)
        if (distance > 0) {
            baseCost = calculateBaseCost(distance, costPerBlock, 0, 0); // Don't apply min/max yet
        }

        // Add inter-dimensional fee if applicable
        if (applyInterDimensionalFee && isInterDimensional) {
            baseCost += interDimensionalFee;
        }

        // Apply minimum cost last (to both distance-only and distance+fee teleports)
        if (minimumCost > 0 && baseCost < minimumCost) {
            baseCost = minimumCost;
        }

        // Apply maximum cost as final cap
        if (maximumCost > 0 && baseCost > maximumCost) {
            baseCost = maximumCost;
        }

        return baseCost;
    }
}