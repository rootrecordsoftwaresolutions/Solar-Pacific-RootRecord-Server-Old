package com.rootrecord.minecraft.common;

/** Result of a PvP (or death) wallet fee — treasury share is ledgered as DEATH inflow. */
public record DeathFeeSettlement(double grossFee, double treasuryAmount, double killerAmount) {}
