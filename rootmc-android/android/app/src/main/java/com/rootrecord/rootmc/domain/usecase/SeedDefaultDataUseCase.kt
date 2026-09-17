package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.data.local.DatabaseSeeder
import javax.inject.Inject

class SeedDefaultDataUseCase @Inject constructor(
    private val databaseSeeder: DatabaseSeeder,
) {
    suspend operator fun invoke() = databaseSeeder.seedIfNeeded()
}
