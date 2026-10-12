package com.rackaudit.app.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards against drift between contract/assignment-status.json (the single source of truth,
 * also used by the Firestore rules tests and the dashboard tests) and the Kotlin mirror.
 */
class ContractTest {
    private val contract: JSONObject by lazy {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("assignment-status.json")) {
            "assignment-status.json not on the test classpath (see sourceSets in app/build.gradle.kts)"
        }
        JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @Test fun statusesMatch() {
        val json = contract.getJSONArray("statuses")
        val fromJson = (0 until json.length()).map { json.getString(it) }.toSet()
        assertEquals(fromJson, AssignmentStatus.entries.map { it.wire }.toSet())
    }

    @Test fun transitionsMatchExactly() {
        val json = contract.getJSONArray("transitions")
        val fromJson = (0 until json.length()).map {
            val t = json.getJSONObject(it)
            Triple(t.getString("from"), t.getString("to"), t.getString("by"))
        }.toSet()
        val fromCode = StatusRules.transitions.map { Triple(it.from.wire, it.to.wire, it.by.wire) }.toSet()
        assertEquals(fromJson, fromCode)
    }

    @Test fun limitsMatch() {
        val l = contract.getJSONObject("limits")
        assertEquals(l.getInt("minPhotos"), Limits.MIN_PHOTOS)
        assertEquals(l.getInt("maxPhotos"), Limits.MAX_PHOTOS)
        assertEquals(l.getInt("noteMaxLength"), Limits.NOTE_MAX_LENGTH)
        assertEquals(l.getInt("captionMaxLength"), Limits.CAPTION_MAX_LENGTH)
        assertEquals(l.getInt("photoMaxBytes"), Limits.PHOTO_MAX_BYTES)
    }
}
