package com.lumina.domain.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for preprocessing text before Text-to-Speech to improve readability.
 *
 * Handles common TTS issues like:
 * - Currency formatting ($15.99 → 15 dollars and 99 cents)
 * - Date formatting (12/25/2023 → December 25th, 2023)
 * - Times (3:30 PM → 3:30 P M)
 * - Phone numbers (555-123-4567 → 5 5 5, 1 2 3, 4 5 6 7)
 * - Special characters and symbols
 * - Number sequences and codes
 */
@Singleton
class TtsPreprocessingService @Inject constructor() {

    /**
     * Preprocesses text to make it more suitable for TTS pronunciation.
     *
     * @param text Raw text from AI or OCR
     * @return Processed text optimized for TTS
     */
    fun preprocessForTts(text: String): String {
        var processed = text

        processed = preprocessCurrency(processed)
        processed = preprocessDates(processed)
        processed = preprocessTimes(processed)
        processed = preprocessPhoneNumbers(processed)
        processed = preprocessNumbers(processed)
        processed = preprocessSpecialCharacters(processed)
        processed = preprocessAbbreviations(processed)

        return processed
    }

    /**
     * Converts currency formats to spoken form.
     */
    private fun preprocessCurrency(text: String): String {
        var result = text

        result = result.replace(Regex("\\$([0-9]+)\\.([0-9]{2})")) { match ->
            val dollars = match.groupValues[1]
            val cents = match.groupValues[2]
            "$dollars dollars and $cents cents"
        }
        result = result.replace(Regex("\\$([0-9]+)(?![0-9.])")) { match ->
            val dollars = match.groupValues[1]
            "$dollars dollars"
        }
        result = result.replace(Regex("€([0-9]+)\\.([0-9]{2})")) { match ->
            val euros = match.groupValues[1]
            val cents = match.groupValues[2]
            "$euros euros and $cents cents"
        }
        result = result.replace(Regex("€([0-9]+)(?![0-9.])")) { match ->
            val euros = match.groupValues[1]
            "$euros euros"
        }
        return result
    }

    /**
     * Converts date formats to spoken form.
     */
    private fun preprocessDates(text: String): String {
        var result = text

        result = result.replace(Regex("([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})")) { match ->
            val month = match.groupValues[1].toIntOrNull()
            val day = match.groupValues[2].toIntOrNull()
            val year = match.groupValues[3]
            if (month != null && day != null && month in 1..12 && day in 1..31) {
                val monthName = getMonthName(month)
                val dayOrdinal = getDayOrdinal(day)
                "$monthName $dayOrdinal, $year"
            } else {
                match.value
            }
        }
        result = result.replace(Regex("([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})")) { match ->
            val year = match.groupValues[1]
            val month = match.groupValues[2].toIntOrNull()
            val day = match.groupValues[3].toIntOrNull()
            if (month != null && day != null && month in 1..12 && day in 1..31) {
                val monthName = getMonthName(month)
                val dayOrdinal = getDayOrdinal(day)
                "$monthName $dayOrdinal, $year"
            } else {
                match.value
            }
        }
        return result
    }

    /**
     * Converts time formats to spoken form.
     */
    private fun preprocessTimes(text: String): String {
        var result = text

        result = result.replace(Regex("([0-9]{1,2}):([0-9]{2})\\s*(AM|PM)")) { match ->
            val time = "${match.groupValues[1]}:${match.groupValues[2]}"
            val period = match.groupValues[3].map { "$it " }.joinToString("").trim()
            "$time $period"
        }
        return result
    }

    /**
     * Converts phone numbers to spoken form.
     */
    private fun preprocessPhoneNumbers(text: String): String {
        var result = text
        result =
            result.replace(Regex("\\(?([0-9]{3})\\)?[-\\s]?([0-9]{3})[-\\s]?([0-9]{4})")) { match ->
                val area = match.groupValues[1].map { "$it " }.joinToString("").trim()
                val exchange = match.groupValues[2].map { "$it " }.joinToString("").trim()
                val number = match.groupValues[3].map { "$it " }.joinToString("").trim()
                "$area, $exchange, $number"
            }
        return result
    }

    /**
     * Handles special number formatting for receipts and documents.
     */
    private fun preprocessNumbers(text: String): String {
        var result = text
        result = result.replace(Regex("#([0-9A-Z]{3,})")) { match ->
            val number = match.groupValues[1].map { "$it " }.joinToString("").trim()
            "number $number"
        }
        result = result.replace(Regex("\\b([0-9]{6,})\\b")) { match ->
            match.groupValues[1].map { "$it " }.joinToString("").trim()
        }
        return result
    }

    /**
     * Converts special characters to spoken form.
     */
    private fun preprocessSpecialCharacters(text: String): String {
        var result = text
        result = result.replace("&", " and ")
        result = result.replace("@", " at ")
        result = result.replace("%", " percent")
        result = result.replace("#", " number ")
        result = result.replace("*", " star ")
        result = result.replace("+", " plus ")
        result = result.replace("=", " equals ")
        result = result.replace("<", " less than ")
        result = result.replace(">", " greater than ")
        return result
    }

    /**
     * Expands common abbreviations.
     */
    private fun preprocessAbbreviations(text: String): String {
        var result = text
        val abbreviations = mapOf(
            "Dr" to "Doctor",
            "St" to "Street",
            "Str" to "Street",
            "Ave" to "Avenue",
            "Blvd" to "Boulevard",
            "Inc" to "Incorporated",
            "LLC" to "L L C",
            "Ltd" to "Limited",
            "Corp" to "Corporation",
            "Co" to "Company",
            "Dept" to "Department",
            "Mgr" to "Manager",
            "Qty" to "Quantity",
            "Ref" to "Reference"
        )
        abbreviations.forEach { (abbrev, expansion) ->
            result = result.replace(
                Regex("\\b$abbrev(?=[\\.,;:!?\\s]|$)", RegexOption.IGNORE_CASE),
                expansion
            )
        }
        return result
    }

    /**
     * Gets month name from number (1-12).
     */
    private fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "January"
            2 -> "February"
            3 -> "March"
            4 -> "April"
            5 -> "May"
            6 -> "June"
            7 -> "July"
            8 -> "August"
            9 -> "September"
            10 -> "October"
            11 -> "November"
            12 -> "December"
            else -> month.toString()
        }
    }

    /**
     * Gets ordinal form of day (1st, 2nd, 3rd, etc.).
     */
    private fun getDayOrdinal(day: Int): String {
        val suffix = when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }
}