@file:Suppress("MagicNumber")

package io.music_assistant.client.utils

import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Formats a decimal with given decimals after dot,
 * because `"%.2f"` is JVM-only — unavailable in KMP `commonMain`.
 */
internal fun formatDecimal(decimal: Double, decimalPlaces: Int): String {
    val pow = 10.0.pow(decimalPlaces).roundToInt()
    val multiplied = (decimal * pow).roundToInt().absoluteValue
    return "${
        if (decimal < 0) "-" else ""
    }${
        multiplied / pow
    }.${
        (multiplied % pow).toString().padStart(decimalPlaces, '0')
    }"
}
