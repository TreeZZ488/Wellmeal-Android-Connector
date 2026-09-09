package com.wellmeal.connector

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptOcrRepositoryTest {

    @Test
    fun combineRecognizedLines_removesExactDuplicatesFromLatinRecognizer() {
        val japaneseLines = listOf(
            "イオン錦糸町店",
            "2026/09/08",
            "国産若鶏ムネ",
            "438",
            "MILK",
            "トマト",
            "298",
            "たまごM10個",
            "248",
            "TOTAL",
            "1087"
        )
        val latinLines = listOf(
            "2026/09/08",
            "438",
            "MILK",
            "298",
            "248",
            "TOTAL",
            "1087"
        )

        val result = ReceiptOcrRepository.combineRecognizedLines(japaneseLines, latinLines)

        // Exact duplicates from latinLines should not be added
        assertEquals(japaneseLines, result)
    }

    @Test
    fun combineRecognizedLines_appendsUniqueLatinLines() {
        val japaneseLines = listOf(
            "スーパーマーケット",
            "納豆",
            "98"
        )
        val latinLines = listOf(
            "98",
            "TAX INCLUDED",
            "BARCODE 123456"
        )

        val result = ReceiptOcrRepository.combineRecognizedLines(japaneseLines, latinLines)

        val expected = listOf(
            "スーパーマーケット",
            "納豆",
            "98",
            "TAX INCLUDED",
            "BARCODE 123456"
        )
        assertEquals(expected, result)
    }

    @Test
    fun combineRecognizedLines_handlesEmptyJapaneseLines() {
        val japaneseLines = emptyList<String>()
        val latinLines = listOf("MILK", "2.99", "BREAD", "1.99")

        val result = ReceiptOcrRepository.combineRecognizedLines(japaneseLines, latinLines)

        assertEquals(latinLines, result)
    }

    @Test
    fun combineRecognizedLines_handlesBothEmpty() {
        val result = ReceiptOcrRepository.combineRecognizedLines(emptyList(), emptyList())
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun combineRecognizedLines_preservesDuplicatesWithinJapaneseLines() {
        val japaneseLines = listOf(
            "リンゴ",
            "150",
            "バナナ",
            "150"
        )
        val latinLines = listOf("150")

        val result = ReceiptOcrRepository.combineRecognizedLines(japaneseLines, latinLines)

        // Both "150" entries in japaneseLines should be kept, and the duplicate in latinLines skipped
        assertEquals(japaneseLines, result)
    }

    @Test
    fun ocrModes_haveDistinctAndExpectedNames() {
        assertEquals("Baseline", ReceiptOcrRepository.MODE_BASELINE)
        assertEquals("Enhanced Japanese", ReceiptOcrRepository.MODE_ENHANCED_JAPANESE)
        org.junit.Assert.assertNotEquals(
            ReceiptOcrRepository.MODE_BASELINE,
            ReceiptOcrRepository.MODE_ENHANCED_JAPANESE
        )
    }
}
