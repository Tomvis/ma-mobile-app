package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SortOptionTest {
    @Test fun receptionFieldsProduceServerKeys() {
        assertEquals("dr", SortOption(SortField.DR).toServerString())
        assertEquals("dr_desc", SortOption(SortField.DR, descending = true).toServerString())
        assertEquals("amg_rating", SortOption(SortField.AMG_RATING).toServerString())
        assertEquals(
            "amg_rating_desc",
            SortOption(SortField.AMG_RATING, descending = true).toServerString(),
        )
        assertEquals("tps_rating", SortOption(SortField.TPS_RATING).toServerString())
        assertEquals(
            "tps_rating_desc",
            SortOption(SortField.TPS_RATING, descending = true).toServerString(),
        )
    }

    @Test fun albumFieldsIncludeReceptionSorts() {
        val fields = SortConfig.fieldsFor(MediaType.ALBUM)
        assertTrue(SortField.DR in fields)
        assertTrue(SortField.AMG_RATING in fields)
        assertTrue(SortField.TPS_RATING in fields)
    }

    @Test fun receptionFieldsDefaultDescendingExistingFieldsDoNot() {
        assertTrue(SortField.DR.defaultDescending)
        assertTrue(SortField.AMG_RATING.defaultDescending)
        assertTrue(SortField.TPS_RATING.defaultDescending)
        assertFalse(SortField.NAME.defaultDescending)
        assertFalse(SortField.YEAR.defaultDescending)
    }
}
