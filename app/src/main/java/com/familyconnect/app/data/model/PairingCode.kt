package com.familyconnect.app.data.model

import kotlin.random.Random

data class PairingCode(
    val code: String = "",
    val parentId: String = "",
    val parentName: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val isUsed: Boolean = false
) {
    companion object {
        const val CODE_LENGTH = 6
        private const val ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        fun generateCode(): String {
            return (1..CODE_LENGTH)
                .map { ALPHANUMERIC[Random.nextInt(ALPHANUMERIC.length)] }
                .joinToString("")
        }
    }
}
