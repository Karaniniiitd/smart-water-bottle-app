package com.tamim.hydrationtracker.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealBottleSensorGatewayParserTest {

    @Test
    fun `parses little-endian binary u16 payload`() {
        val payload = byteArrayOf(0x78, 0x00) // 120ml

        val parsed = RealBottleSensorGateway.parseSipMlFromPayload(payload)

        assertEquals(120, parsed)
    }

    @Test
    fun `parses plain integer text payload`() {
        val parsed = RealBottleSensorGateway.parseSipMlFromPayload("250".toByteArray())

        assertEquals(250, parsed)
    }

    @Test
    fun `parses key value text payload`() {
        val parsed = RealBottleSensorGateway.parseSipMlFromPayload("sip_ml: 180".toByteArray())

        assertEquals(180, parsed)
    }

    @Test
    fun `returns null for invalid payload`() {
        val parsed = RealBottleSensorGateway.parseSipMlFromPayload("hello".toByteArray())

        assertNull(parsed)
    }
}
