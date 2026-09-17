package com.rootrecord.rootmc.domain.model

import com.rootrecord.rootmc.data.local.entity.SpawnerTrackEntity
import com.rootrecord.rootmc.data.local.entity.SpawnerType

object SpawnerCooldown {
    /** Trial / ominous trial spawners: 30 minutes (36000 ticks), counts down while unloaded. */
    const val TRIAL_COOLDOWN_MS = 30L * 60L * 1000L

    enum class Status { READY, COOLING, FARMING }

    fun statusFor(spawner: SpawnerTrackEntity, nowMs: Long = System.currentTimeMillis()): Status =
        when {
            spawner.spawnerType == SpawnerType.MONSTER.name -> Status.FARMING
            spawner.cooldownEndsAt == null || spawner.cooldownEndsAt <= nowMs -> Status.READY
            else -> Status.COOLING
        }

    fun remainingMs(spawner: SpawnerTrackEntity, nowMs: Long = System.currentTimeMillis()): Long {
        val end = spawner.cooldownEndsAt ?: return 0L
        return (end - nowMs).coerceAtLeast(0L)
    }

    fun defaultCooldownEnd(nowMs: Long = System.currentTimeMillis()): Long =
        nowMs + TRIAL_COOLDOWN_MS

    fun formatRemaining(ms: Long): String {
        if (ms <= 0L) return "Ready"
        val totalSec = (ms + 999) / 1000
        val hours = totalSec / 3600
        val min = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return when {
            hours > 0 -> "${hours}h ${min}m"
            min > 0 -> "${min}m ${sec}s"
            else -> "${sec}s"
        }
    }

    fun readyAtLabel(cooldownEndsAt: Long?): String? {
        if (cooldownEndsAt == null) return null
        val remaining = cooldownEndsAt - System.currentTimeMillis()
        if (remaining <= 0L) return null
        return "Ready in ${formatRemaining(remaining)}"
    }
}
