package com.ahu.ahutong.data.crawler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentSemesterScriptParserTest {

    @Test
    fun `extracts nested object literal without truncating it`() {
        val script = """
            var currentSemester = {"id":42,"calendarAssoc":{"id":7},"name":"2025-2026-2"};
            var unrelated = true;
        """.trimIndent()

        assertEquals(
            """{"id":42,"calendarAssoc":{"id":7},"name":"2025-2026-2"}""",
            extractCurrentSemesterJson(script)
        )
    }

    @Test
    fun `extracts and decodes JSON parse string`() {
        val script = """
            const currentSemester = JSON.parse('{\"id\":42,\"name\":\"2025-2026-2\",\"label\":\"\\u5b66\\u671f\"}');
        """.trimIndent()

        assertEquals(
            """{"id":42,"name":"2025-2026-2","label":"\u5b66\u671f"}""",
            extractCurrentSemesterJson(script)
        )
    }

    @Test
    fun `rejects incomplete assignments`() {
        assertNull(extractCurrentSemesterJson("var currentSemester = JSON.parse('{\\\"id\\\":42}'"))
        assertNull(extractCurrentSemesterJson("var currentSemester = {\\\"id\\\":42"))
    }
}
