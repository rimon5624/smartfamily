package com.rimon.smartfamily.security

import com.rimon.smartfamily.R

/**
 * 2.2: password rules for a new cloud account, checked on the phone (free). Supabase's own leaked-password check
 * needs a paid plan, so the app blocks the most common passwords itself.
 */
object PasswordRules {
    private val common = setOf(
        "123456", "1234567", "12345678", "123456789", "1234567890", "12345678910", "0123456789", "111111", "000000", "121212",
        "password", "password1", "password123", "qwerty", "qwerty123", "qwertyuiop", "abc123", "abcd1234", "iloveyou", "admin",
        "welcome", "letmein", "monkey", "dragon", "football", "sunshine", "princess", "shalom", "shalom123", "israel", "israel123",
        "a1b2c3d4", "1q2w3e4r", "1q2w3e4r5t", "zaq12wsx", "asdfghjkl", "passw0rd", "123123123", "987654321", "11111111", "88888888",
    )

    /** Null when the password is fine, otherwise the string id of the reason. */
    fun problem(password: String, email: String = ""): Int? = when {
        password.length < 10 -> R.string.pw_too_short
        password.lowercase() in common || password.toSet().size <= 2 -> R.string.pw_too_common
        email.isNotBlank() && password.lowercase().contains(email.substringBefore('@').lowercase().takeIf { it.length >= 4 } ?: "\u0000") -> R.string.pw_has_email
        password.all { it.isDigit() } || password.all { it.isLetter() } -> R.string.pw_mix
        else -> null
    }
}
