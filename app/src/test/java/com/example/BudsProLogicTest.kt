package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.devices.BatterySource
import com.example.devices.BatteryTelemetry
import com.example.devices.EarbudsBrand
import com.example.equalizer.EqualizerCatalog
import com.example.equalizer.TenBandBiquadAudioProcessor
import com.example.integrations.EarbudsIntegrationRegistry
import com.example.integrations.OnePlusNordBuds3ProIntegration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class BudsProLogicTest {

    @Test
    fun `oneplus nord buds 3 pro integration matches accurately and never fakes ANC confirmation`() {
        val integration = EarbudsIntegrationRegistry.resolve("OnePlus Nord Buds 3 Pro", hasFastPairService = true)
        assertEquals(EarbudsBrand.ONEPLUS, integration.brand)
        assertTrue(OnePlusNordBuds3ProIntegration.isExactNordBuds3Pro("OnePlus Nord Buds 3 Pro"))

        val caps = integration.describeCapabilities("OnePlus Nord Buds 3 Pro", hasBatteryGatt = true, hasFastPair = true)
        assertTrue(caps.isOnePlusNordBuds3Pro)
        assertFalse(caps.supportsAncOverPublicGatt)
        assertEquals("HeyMelody", caps.companionAppName)

        val anc = integration.buildAncState("OnePlus Nord Buds 3 Pro", isConnected = true, discoveredCustomControlUuid = false)
        assertFalse(anc.isPublicGattControlSupported)
        assertNull(anc.currentConfirmedMode)
        assertTrue(anc.requiresCompanionApp)
    }

    @Test
    fun `multi brand registry resolves samsung sony jbl boat realme nothing and generic`() {
        assertEquals(EarbudsBrand.SAMSUNG, EarbudsIntegrationRegistry.resolve("Galaxy Buds2 Pro").brand)
        assertEquals(EarbudsBrand.SONY, EarbudsIntegrationRegistry.resolve("WF-1000XM5").brand)
        assertEquals(EarbudsBrand.JBL, EarbudsIntegrationRegistry.resolve("JBL Live Pro 2").brand)
        assertEquals(EarbudsBrand.BOAT, EarbudsIntegrationRegistry.resolve("boAt Airdopes 141").brand)
        assertEquals(EarbudsBrand.REALME, EarbudsIntegrationRegistry.resolve("realme Buds Air 5").brand)
        assertEquals(EarbudsBrand.NOTHING, EarbudsIntegrationRegistry.resolve("Nothing Ear (a)").brand)
        assertEquals(EarbudsBrand.GENERIC, EarbudsIntegrationRegistry.resolve("Studio Headset").brand)
    }

    @Test
    fun `battery telemetry never invents unavailable values`() {
        val empty = BatteryTelemetry()
        assertFalse(empty.hasAnyBatteryValue)
        assertNull(empty.leftPercent)
        assertNull(empty.rightPercent)
        assertNull(empty.casePercent)
        assertNull(empty.lowestAvailablePercent)
        assertEquals(BatterySource.UNAVAILABLE, empty.source)
    }

    @Test
    fun `equalizer catalog has 10 exact bands and 10 presets`() {
        assertEquals(10, EqualizerCatalog.bands.size)
        assertEquals(
            listOf(60, 120, 250, 500, 1000, 2000, 4000, 8000, 12000, 16000),
            EqualizerCatalog.bands.map { it.frequencyHz }
        )
        assertEquals(10, EqualizerCatalog.builtInPresets.size)
        assertTrue(EqualizerCatalog.builtInPresets.all { it.gainsDb.size == 10 })
    }

    @Test
    fun `ten band biquad processor alters 60Hz signal amplitude when boosted`() {
        val processor = TenBandBiquadAudioProcessor()
        val format = AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val boosted = listOf(9f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        processor.updateSettings(enabled = true, newGainsDb = boosted)

        val sampleCount = 512
        val input = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        var maxInput = 0
        for (i in 0 until sampleCount) {
            val s = (4000.0 * sin(2.0 * PI * 60.0 * i / 44100.0)).toInt().toShort()
            if (kotlin.math.abs(s.toInt()) > maxInput) maxInput = kotlin.math.abs(s.toInt())
            input.putShort(s)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        var maxOutput = 0
        while (output.remaining() >= 2) {
            val s = kotlin.math.abs(output.short.toInt())
            if (s > maxOutput) maxOutput = s
        }
        assertNotEquals(maxInput, maxOutput)
        assertTrue("Expected +9dB 60Hz boost to increase peak amplitude", maxOutput > maxInput)
    }
}
