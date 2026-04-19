package com.tamim.hydrationtracker.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeBleManagerTest {

    @Test
    fun `scan returns sample devices`() = runTest {
        val manager = FakeBleManager()

        manager.startScan()

        assertEquals(3, manager.devices.value.size)
        assertNotNull(manager.devices.value.firstOrNull())
    }

    @Test
    fun `trigger sip emits event`() = runTest {
        val manager = FakeBleManager()

        val deferred = async { manager.sipEventsMl.first() }
        runCurrent()
        manager.triggerSip(180)

        assertEquals(180, deferred.await())
    }
}
