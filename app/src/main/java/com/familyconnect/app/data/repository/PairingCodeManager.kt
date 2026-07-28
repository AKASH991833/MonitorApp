package com.familyconnect.app.data.repository

import kotlin.random.Random

class PairingCodeManager {

    companion object {
        private const val CODE_LENGTH = 6
        private const val ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val EXPIRY_MINUTES = 10
    }

    fun generateCode(): String {
        return (1..CODE_LENGTH)
            .map { ALPHANUMERIC[Random.nextInt(ALPHANUMERIC.length)] }
            .joinToString("")
    }

    fun isCodeExpired(createdAt: Long): Boolean {
        val elapsed = System.currentTimeMillis() - createdAt
        return elapsed > EXPIRY_MINUTES * 60 * 1000L
    }
}
