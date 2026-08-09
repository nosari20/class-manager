package edu.fnosari.classmanager.domain

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {
    @Test fun commaSeparated() {
        val t = CsvParser.parse("Nom,Prénom\nMartin,Emma\nDurand,Léo".toByteArray())
        assertEquals(listOf("Nom", "Prénom"), t.headers)
        assertEquals(listOf("Martin", "Emma"), t.rows[0])
        assertEquals(2, t.rows.size)
    }

    @Test fun semicolonSeparated() {
        val t = CsvParser.parse("Nom;Prénom\nMartin;Emma".toByteArray())
        assertEquals(listOf("Martin", "Emma"), t.rows[0])
    }

    @Test fun semicolonWinsWhenBothPresent() {
        // Pronote style: semicolon separator, comma inside a field
        val t = CsvParser.parse("Nom;Prénom\nMartin, Jr;Emma".toByteArray())
        assertEquals(listOf("Martin, Jr", "Emma"), t.rows[0])
    }

    @Test fun quotedFieldsWithSeparatorAndEscapedQuotes() {
        val t = CsvParser.parse("Nom,Prénom\n\"Martin, Jr\",\"E\"\"mma\"".toByteArray())
        assertEquals(listOf("Martin, Jr", "E\"mma"), t.rows[0])
    }

    @Test fun quotedFieldWithNewline() {
        val t = CsvParser.parse("Nom,Info\nMartin,\"line1\nline2\"".toByteArray())
        assertEquals(listOf("Martin", "line1\nline2"), t.rows[0])
    }

    @Test fun windows1252Fallback() {
        val bytes = "Nom;Prénom\nDurand;Léo".toByteArray(Charset.forName("windows-1252"))
        val t = CsvParser.parse(bytes)
        assertEquals("Prénom", t.headers[1])
        assertEquals("Léo", t.rows[0][1])
    }

    @Test fun utf8BomStripped() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Nom,Prénom\nA,B".toByteArray()
        assertEquals("Nom", CsvParser.parse(bytes).headers[0])
    }

    @Test fun malformedRowsSkippedAndReported() {
        val t = CsvParser.parse("Nom,Prénom\nMartin,Emma\nBrokenRowWithoutComma\nDurand,Léo".toByteArray())
        assertEquals(2, t.rows.size)
        assertEquals(listOf(3), t.skippedLines)
    }

    @Test fun emptyLinesIgnored() {
        val t = CsvParser.parse("Nom,Prénom\n\nMartin,Emma\n\n".toByteArray())
        assertEquals(1, t.rows.size)
        assertTrue(t.skippedLines.isEmpty())
    }

    @Test fun guessColumnFindsAccentAndCase() {
        assertEquals(1, CsvParser.guessColumn(listOf("Classe", "NOM", "Prénom"), LAST_NAME_HEADERS))
        assertEquals(2, CsvParser.guessColumn(listOf("Classe", "NOM", "Prénom"), FIRST_NAME_HEADERS))
        assertNull(CsvParser.guessColumn(listOf("x", "y"), LAST_NAME_HEADERS))
    }
}
