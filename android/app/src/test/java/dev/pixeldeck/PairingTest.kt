package dev.pixeldeck

import dev.pixeldeck.net.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {
    @Test fun parsesLegacyString() {
        assertEquals(Pairing("192.168.1.5", 47800, "abcdef0123", ""), Pairing.parse("pixeldeck://192.168.1.5:47800/abcdef0123"))
    }

    @Test fun parsesNameAndDecodesIt() {
        val p = Pairing.parse("  pixeldeck://10.0.0.2:47800/0a1b2c?n=My%20Gaming%20PC  ")
        assertEquals("My Gaming PC", p?.name)
        assertEquals("10.0.0.2", p?.host)
    }

    @Test fun rejectsOtherTextAndBadPorts() {
        assertNull(Pairing.parse("https://example.com"))
        assertNull(Pairing.parse("pixeldeck://host:99999/abcd"))
        assertNull(Pairing.parse("pixeldeck://host:47800/not-hex"))
    }
}
