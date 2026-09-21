package com.noor.shoplocal

import com.noor.shoplocal.data.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the registration / login input validation rules. */
class ValidatorsTest {

    @Test
    fun email_acceptsWellFormedAddresses() {
        assertTrue(Validators.isValidEmail("noor@example.com"))
        assertTrue(Validators.isValidEmail("a.b-c_d@shop.local.co.za"))
    }

    @Test
    fun email_rejectsMalformedAddresses() {
        assertFalse(Validators.isValidEmail("not-an-email"))
        assertFalse(Validators.isValidEmail("missing@domain"))
        assertFalse(Validators.isValidEmail("@no-user.com"))
        assertFalse(Validators.isValidEmail(""))
    }

    @Test
    fun password_requiresLengthLetterAndDigit() {
        assertTrue(Validators.isValidPassword("shoplocal1"))
        assertFalse(Validators.isValidPassword("short1"))     // too short
        assertFalse(Validators.isValidPassword("allletters")) // no digit
        assertFalse(Validators.isValidPassword("12345678"))   // no letter
    }

    @Test
    fun name_requiresAtLeastTwoCharacters() {
        assertTrue(Validators.isValidName("Jo"))
        assertFalse(Validators.isValidName("A"))
        assertFalse(Validators.isValidName("  "))
    }

    @Test
    fun passwordsMatch_onlyWhenEqualAndNonEmpty() {
        assertTrue(Validators.passwordsMatch("secret12", "secret12"))
        assertFalse(Validators.passwordsMatch("secret12", "secret13"))
        assertFalse(Validators.passwordsMatch("", ""))
    }

    @Test
    fun registrationError_isNullForValidForm() {
        assertNull(Validators.registrationError("Noor", "noor@example.com", "shoplocal1", "shoplocal1"))
    }

    @Test
    fun registrationError_reportsFirstProblem() {
        // Bad name is reported before anything else.
        assertEquals(
            "Please enter your name.",
            Validators.registrationError("", "noor@example.com", "shoplocal1", "shoplocal1")
        )
        // Then email, then password, then mismatch.
        assertEquals(
            "Please enter a valid email address.",
            Validators.registrationError("Noor", "bad", "shoplocal1", "shoplocal1")
        )
        assertEquals(
            "Passwords do not match.",
            Validators.registrationError("Noor", "noor@example.com", "shoplocal1", "different1")
        )
    }
}
