package com.familyapp.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun testNewerMajorVersion() {
        assertTrue(SemVer.isNewer("1.0.0", "0.2.0"))
        assertTrue(SemVer.isNewer("v2.0.0", "1.9.9"))
    }

    @Test
    fun testNewerMinorVersion() {
        assertTrue(SemVer.isNewer("0.3.0", "0.2.0"))
        assertTrue(SemVer.isNewer("v0.3.0", "0.2.9"))
        assertTrue(SemVer.isNewer("0.10.0", "0.9.0"))
    }

    @Test
    fun testNewerPatchVersion() {
        assertTrue(SemVer.isNewer("0.2.1", "0.2.0"))
        assertTrue(SemVer.isNewer("v0.2.5", "v0.2.4"))
        assertTrue(SemVer.isNewer("0.2.10", "0.2.9"))
    }

    @Test
    fun testSameVersionReturnsFalse() {
        assertFalse(SemVer.isNewer("0.2.0", "0.2.0"))
        assertFalse(SemVer.isNewer("v0.2.0", "0.2.0"))
        assertFalse(SemVer.isNewer("0.2.0", "v0.2.0"))
        assertFalse(SemVer.isNewer("V0.2.0", "v0.2.0"))
    }

    @Test
    fun testOlderVersionReturnsFalse() {
        assertFalse(SemVer.isNewer("0.1.9", "0.2.0"))
        assertFalse(SemVer.isNewer("v0.1.0", "0.2.0"))
        assertFalse(SemVer.isNewer("0.2.0", "0.2.1"))
    }

    @Test
    fun testPrereleaseTagHandling() {
        // Tag with suffix like v0.3.0-beta compared to 0.2.0
        assertTrue(SemVer.isNewer("v0.3.0-beta", "0.2.0"))
        assertFalse(SemVer.isNewer("v0.2.0-beta", "0.2.0"))
    }
}
