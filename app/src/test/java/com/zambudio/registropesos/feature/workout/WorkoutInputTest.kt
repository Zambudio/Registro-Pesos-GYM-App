package com.zambudio.registropesos.feature.workout

import com.zambudio.registropesos.feature.workout.common.formatElapsed
import com.zambudio.registropesos.feature.workout.common.formatWeightInput
import com.zambudio.registropesos.feature.workout.common.parseRepsText
import com.zambudio.registropesos.feature.workout.common.parseWeightText
import com.zambudio.registropesos.feature.workout.common.roundKg
import com.zambudio.registropesos.feature.workout.session.NumberFieldSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutInputTest {

    @Test
    fun parsesWeightWithCommaOrDotAndRejectsInvalid() {
        assertEquals(82.5, parseWeightText("82,5"))
        assertEquals(82.5, parseWeightText(" 82.5 "))
        assertEquals(82.0, parseWeightText("82,"))
        assertNull(parseWeightText(""))
        assertNull(parseWeightText(","))
        assertNull(parseWeightText("-2,5"))
        assertNull(parseWeightText("1500"))
        assertNull(parseWeightText("NaN"))
        assertEquals(10, parseRepsText("10"))
        assertNull(parseRepsText("1,5"))
        assertNull(parseRepsText("-1"))
    }

    @Test
    fun formatsWeightForEditingWithoutThousandsSeparator() {
        assertEquals("82,5", formatWeightInput(82.5))
        assertEquals("80", formatWeightInput(80.0))
        assertEquals("1000", formatWeightInput(1_000.0))
        assertEquals("", formatWeightInput(null))
        assertEquals(1000.0, parseWeightText(formatWeightInput(1_000.0)))
    }

    @Test
    fun formatsElapsedTime() {
        assertEquals("00:00", formatElapsed(-5))
        assertEquals("05:09", formatElapsed(309_000))
        assertEquals("1:05:09", formatElapsed(3_909_000))
        assertEquals(0.3, roundKg(0.1 + 0.2), 0.0)
    }

    @Test
    fun fieldKeepsTypedTextThatStillRepresentsTheValue() {
        val sync = NumberFieldSync(::parseWeightText)

        assertTrue(sync.onTyped("82", "82,"))
        // El estado sigue en 82: "82," lo representa, no se reescribe a "82".
        assertNull(sync.onValue("82,", 82.0, ::formatWeightInput))
        assertFalse(sync.onTyped("82,", "82,"))
    }

    @Test
    fun fieldIgnoresLateEchoesOfEarlierKeystrokes() {
        val sync = NumberFieldSync(::parseWeightText)
        sync.onTyped("", "8")
        sync.onTyped("8", "82")

        // Llega tarde el valor de la primera pulsación: no debe pisar "82".
        assertNull(sync.onValue("82", 8.0, ::formatWeightInput))
        assertNull(sync.onValue("82", 82.0, ::formatWeightInput))
    }

    @Test
    fun fieldTakesValuesChangedFromOutside() {
        val sync = NumberFieldSync(::parseWeightText)
        sync.onTyped("", "80")
        assertNull(sync.onValue("80", 80.0, ::formatWeightInput))

        // +2,5 o copiar anterior.
        assertEquals("82,5", sync.onValue("80", 82.5, ::formatWeightInput))
        assertEquals("", sync.onValue("82,5", null, ::formatWeightInput))
    }
}
