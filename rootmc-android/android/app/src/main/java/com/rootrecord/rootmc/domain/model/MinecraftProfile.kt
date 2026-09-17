package com.rootrecord.rootmc.domain.model

data class MinecraftProfile(
    val username: String,
    val uuid: String,
    val skinUrl: String?,
    val skinSlim: Boolean,
) {
    /** Head render URL (Crafatar reads Mojang skin data). */
    fun avatarUrl(size: Int = 96): String =
        "https://crafatar.com/avatars/${uuid.replace("-", "")}?overlay&size=$size"

    companion object {
        fun formatUuid(raw: String): String {
            val compact = raw.replace("-", "").lowercase()
            if (compact.length != 32 || compact.any { it !in '0'..'9' && it !in 'a'..'f' }) return raw
            return buildString {
                append(compact.take(8))
                append('-')
                append(compact.substring(8, 12))
                append('-')
                append(compact.substring(12, 16))
                append('-')
                append(compact.substring(16, 20))
                append('-')
                append(compact.substring(20, 32))
            }
        }

        fun looksLikeUuid(input: String): Boolean {
            val compact = input.trim().replace("-", "")
            return compact.length == 32 && compact.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        }
    }
}
