package io.music_assistant.client.data.model.server

import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CriticalReceptionSerializationTest {

    private val metadataJson = """
        {
          "dynamic_range": 12.0,
          "critical_reception": {
            "amg_dr": 11.0,
            "sources": [
              {"source": "AMG", "rating": 4.5, "types": ["Review", "TYMHM"],
               "labels": ["AOTY-2024"], "authors": ["J. Smith"]},
              {"source": "TPS", "rating": 8.4},
              {"source": null, "rating": 9.9}
            ]
          },
          "last_refresh": null
        }
    """.trimIndent()

    @Test
    fun decodesDynamicRangeAndCriticalReception() {
        val md = myJson.decodeFromString<ServerMetadata>(metadataJson)

        assertEquals(12.0, md.dynamicRange)
        assertEquals(11.0, md.criticalReception?.amgDr)
        assertEquals(3, md.criticalReception?.sources?.size)
        assertEquals("AMG", md.criticalReception?.sources?.get(0)?.source)
        assertEquals(4.5, md.criticalReception?.sources?.get(0)?.rating)
        assertEquals(listOf("Review", "TYMHM"), md.criticalReception?.sources?.get(0)?.types)
        // null source decodes to "" (default), not a crash
        assertEquals("", md.criticalReception?.sources?.get(2)?.source)
    }

    @Test
    fun decodesMetadataWithoutNewFields() {
        val md = myJson.decodeFromString<ServerMetadata>("""{"last_refresh": null}""")
        assertNull(md.dynamicRange)
        assertNull(md.criticalReception)
    }
}
