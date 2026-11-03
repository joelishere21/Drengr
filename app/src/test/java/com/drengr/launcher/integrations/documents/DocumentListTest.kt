package com.drengr.launcher.integrations.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentListTest {
    private fun doc(n: Int, usedAt: Long = n.toLong(), name: String = "Doc $n") =
        RecentDocument("content://docs/$n", name, "application/pdf", usedAt)

    @Test fun addPutsNewFilesFirst() {
        val result = DocumentList.add(listOf(doc(1), doc(2)), listOf(doc(3, usedAt = 100)))
        assertEquals(listOf("Doc 3", "Doc 1", "Doc 2"), result.kept.map { it.name })
        assertTrue(result.dropped.isEmpty())
    }

    @Test fun addingAgainMovesToTopWithoutDuplicates() {
        val result = DocumentList.add(listOf(doc(1), doc(2)), listOf(doc(2, usedAt = 100, name = "Renamed")))
        assertEquals(listOf("Renamed", "Doc 1"), result.kept.map { it.name })
    }

    @Test fun overTheCapDropsTheOldest() {
        val existing = (1..DocumentList.MAX_DOCUMENTS).map { doc(it, usedAt = 0) }
        val result = DocumentList.add(existing, listOf(doc(99, usedAt = 100)))
        assertEquals(DocumentList.MAX_DOCUMENTS, result.kept.size)
        assertEquals("Doc 99", result.kept.first().name)
        assertEquals(listOf("Doc ${DocumentList.MAX_DOCUMENTS}"), result.dropped.map { it.name })
    }

    @Test fun touchMovesKnownDocumentsOnly() {
        val list = listOf(doc(1), doc(2))
        assertEquals(listOf("New", "Doc 1"), DocumentList.touch(list, doc(2, name = "New")).map { it.name })
        assertEquals(list, DocumentList.touch(list, doc(3)))
    }

    @Test fun encodeDecodeRoundTripNewestFirst() {
        val list = listOf(doc(1, usedAt = 5), doc(2, usedAt = 9).copy(mimeType = null, writable = true))
        assertEquals(list.sortedByDescending { it.usedAt }, DocumentList.decode(DocumentList.encode(list)))
    }

    @Test fun decodeToleratesBadInput() {
        assertTrue(DocumentList.decode(null).isEmpty())
        assertTrue(DocumentList.decode("not json").isEmpty())
        assertEquals(1, DocumentList.decode("""[{"uri":"content://a"},{"name":"no uri"}]""").size)
    }
}
