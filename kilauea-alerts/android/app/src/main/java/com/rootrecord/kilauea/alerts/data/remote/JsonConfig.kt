package com.rootrecord.kilauea.alerts.data.remote

import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = false
}
