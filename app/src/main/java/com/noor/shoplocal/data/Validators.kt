package com.noor.shoplocal.data

/**
 * Input validation used by the register / login / settings screens so the app can
 * reject bad input *before* hitting the network and never crash on it. Pure
 * functions — covered by ValidatorsTest.
 */
object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean =
        EMAIL_REGEX.matches(email.trim())

    /** Name must be present and at least two characters. */
    fun isValidName(name: String): Boolean =
        name.trim().length >= 2

    /**
     * Password policy (mirrors the Part 1 design): at least 8 characters, with at
     * least one letter and one digit.
     */
    fun isValidPassword(password: String): Boolean {
        if (password.length < 8) return false
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }

    fun passwordsMatch(password: String, confirm: String): Boolean =
        password == confirm && password.isNotEmpty()

    /** Human-readable reason a registration form is invalid, or null when it is fine. */
    fun registrationError(name: String, email: String, password: String, confirm: String): String? = when {
        !isValidName(name) -> "Please enter your name."
        !isValidEmail(email) -> "Please enter a valid email address."
        !isValidPassword(password) -> "Password must be at least 8 characters and include a letter and a number."
        !passwordsMatch(password, confirm) -> "Passwords do not match."
        else -> null
    }
}
