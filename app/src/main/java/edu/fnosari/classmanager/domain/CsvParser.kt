package edu.fnosari.classmanager.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class CsvTable(
    val headers: List<String>,
    val rows: List<List<String>>,
    val skippedLines: List<Int>,
)

val LAST_NAME_HEADERS = listOf("nom", "nom de famille", "lastname", "last name", "name")
val FIRST_NAME_HEADERS = listOf("prénom", "prenom", "firstname", "first name")

object CsvParser {

    fun parse(bytes: ByteArray): CsvTable {
        val text = decode(bytes)
        val separator = detectSeparator(text)
        val records = splitRecords(text, separator)
        if (records.isEmpty()) return CsvTable(emptyList(), emptyList(), emptyList())
        val headers = records.first().fields.map { it.trim() }
        val rows = mutableListOf<List<String>>()
        val skipped = mutableListOf<Int>()
        for (rec in records.drop(1)) {
            if (rec.fields.size == 1 && rec.fields[0].isBlank()) continue
            if (rec.fields.size == headers.size) rows.add(rec.fields.map { it.trim() })
            else skipped.add(rec.lineNumber)
        }
        return CsvTable(headers, rows, skipped)
    }

    fun guessColumn(headers: List<String>, candidates: List<String>): Int? {
        val normalized = headers.map { it.trim().lowercase() }
        for (cand in candidates) {
            val i = normalized.indexOf(cand)
            if (i >= 0) return i
        }
        return null
    }

    private fun decode(bytes: ByteArray): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) bytes.copyOfRange(3, bytes.size) else bytes
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body)).toString()
        } catch (e: CharacterCodingException) {
            String(body, Charset.forName("windows-1252"))
        }
    }

    private fun detectSeparator(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        var semis = 0
        var commas = 0
        var inQuotes = false
        for (ch in firstLine) when {
            ch == '"' -> inQuotes = !inQuotes
            ch == ';' && !inQuotes -> semis++
            ch == ',' && !inQuotes -> commas++
        }
        return if (semis >= commas) ';' else ','
    }

    private class Record(val fields: List<String>, val lineNumber: Int)

    private fun splitRecords(text: String, sep: Char): List<Record> {
        val records = mutableListOf<Record>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordStartLine = 1
        var i = 0
        fun endField() { fields.add(field.toString()); field.clear() }
        fun endRecord() {
            endField()
            records.add(Record(fields, recordStartLine))
            fields = mutableListOf()
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                ch == sep && !inQuotes -> endField()
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    line++
                    endRecord()
                    recordStartLine = line
                }
                else -> {
                    if (ch == '\n') line++
                    field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }
}
