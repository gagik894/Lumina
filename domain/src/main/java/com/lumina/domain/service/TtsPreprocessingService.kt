package com.lumina.domain.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for preprocessing text before Text-to-Speech to improve readability.
 *
 * Handles common TTS issues like:
 * - Date formatting (12/25/2023 → "December 25th, 2023")
 * - Currency formatting ($15.99 → "15 dollars and 99 cents")
 * - Phone numbers (555-123-4567 → "5 5 5, 1 2 3, 4 5 6 7")
 * - Times (3:30 PM → "3:30 P M")
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

        // Apply preprocessing in order of importance
        processed =
            preprocessStructuredLabels(processed) // Run first to establish sentence structure
        processed = preprocessUrls(processed)
        processed = preprocessEmails(processed) 
        processed = preprocessCurrency(processed)
        processed = preprocessDates(processed)
        processed = preprocessTimes(processed)
        processed = preprocessPhoneNumbers(processed)
        processed = preprocessNumbers(processed)
        processed = preprocessSpecialCharacters(processed)
        processed = preprocessAbbreviations(processed)
        processed = cleanupSpacing(processed)

        return processed
    }

    /**
     * Converts URLs to spoken form with proper pronunciation.
     * Examples:
     * - "www.google.com" → "w w w dot google dot com"
     * - "https://example.com/path" → "h t t p s colon slash slash example dot com slash path"
     * - "google.com" → "google dot com"
     */
    private fun preprocessUrls(text: String): String {
        var result = text

        // Full URLs with protocol - these are clearly URLs
        result = result.replace(
            Regex(
                "https?://([\\w.-]+(?:/[\\w.-]*)*)",
                RegexOption.IGNORE_CASE
            )
        ) { match ->
            val fullUrl = match.value
            val protocol = if (fullUrl.startsWith("https")) "h t t p s" else "h t t p"
            val domain = match.groupValues[1]
            val processedDomain = domain.replace(".", " dot ").replace("/", " slash ")
            "$protocol colon slash slash $processedDomain"
        }

        // Only process domains that are clearly websites (www. prefix or common TLD patterns)
        // Avoid processing random words with dots that might be end of sentences
        result =
            result.replace(Regex("\\b(?:www\\.|([a-zA-Z0-9-]+\\.(?:com|org|net|edu|gov|io|co|uk|de|fr|jp|au|ca|us|info|biz)(?:\\.[a-zA-Z]{2})?))\\b")) { match ->
                val domain = match.value
                if (domain.startsWith("www.")) {
                    domain.replace("www.", "w w w dot ").replace(".", " dot ")
                } else {
                    domain.replace(".", " dot ")
                }
            }

        return result
    }

    /**
     * Converts email addresses to spoken form with proper pauses.
     * Examples:
     * - "user@example.com" → "user, at, example dot com"
     * - "name.lastname@company.org" → "name dot lastname, at, company dot org"
     */
    private fun preprocessEmails(text: String): String {
        var result = text

        result = result.replace(Regex("([\\w.-]+)@([\\w.-]+\\.[a-zA-Z]{2,})")) { match ->
            val localPart = match.groupValues[1].replace(".", " dot ")
            val domainPart = match.groupValues[2].replace(".", " dot ")
            "$localPart, at, $domainPart"
        }

        return result
    }

    /**
     * Handles structured labels for better readability.
     */
    private fun preprocessStructuredLabels(text: String): String {
        // Regex to find labels like "Address:", "Phone:", "Email:", etc.
        val labelRegex = Regex("""\b([A-Za-z\s]+:)\s*""")

        // First, handle multiple labels on the same line by replacing the comma separator with a period.
        // This creates a pause between items like "Email: ..., Phone: ..."
        var processedText = text.replace(Regex(""",\s*(\b[A-Za-z\s]+:)"""), ". $1")

        // Second, ensure that any line containing a label ends with a sentence-terminating punctuation.
        // This handles cases where a line has one label and no trailing punctuation.
        return processedText.lines().joinToString("\n") { line ->
            val trimmedLine = line.trim()
            // Check if the line contains a label and doesn't already end with punctuation.
            if (labelRegex.containsMatchIn(trimmedLine) && !trimmedLine.matches(Regex(".*[.!?]$"))) {
                trimmedLine + "."
            } else {
                line // Return original line to preserve original whitespace if no changes are made
            }
        }
    }

    /**
     * Converts currency formats to spoken form.
     * Examples:
     * - "$15.99" → "15 dollars and 99 cents"
     * - "€25.50" → "25 euros and 50 cents"
     * - "$5" → "5 dollars"
     */
    private fun preprocessCurrency(text: String): String {
        var result = text

        // US Dollars with cents
        result = result.replace(Regex("\\$([0-9]+)\\.([0-9]{2})")) { match ->
            val dollars = match.groupValues[1]
            val cents = match.groupValues[2]
            "$dollars dollars and $cents cents"
        }

        // US Dollars without cents
        result = result.replace(Regex("\\$([0-9]+)(?![0-9.])")) { match ->
            val dollars = match.groupValues[1]
            "$dollars dollars"
        }

        // Euros with cents
        result = result.replace(Regex("€([0-9]+)\\.([0-9]{2})")) { match ->
            val euros = match.groupValues[1]
            val cents = match.groupValues[2]
            "$euros euros and $cents cents"
        }

        // Euros without cents
        result = result.replace(Regex("€([0-9]+)(?![0-9.])")) { match ->
            val euros = match.groupValues[1]
            "$euros euros"
        }

        return result
    }

    /**
     * Converts date formats to spoken form.
     * Examples:
     * - "12/25/2023" → "December 25th, 2023"
     * - "25/12/2023" → "25th of December, 2023"
     * - "2023-12-25" → "December 25th, 2023"
     */
    private fun preprocessDates(text: String): String {
        var result = text

        // MM/DD/YYYY format (US)
        result = result.replace(Regex("([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})")) { match ->
            val month = match.groupValues[1].toIntOrNull()
            val day = match.groupValues[2].toIntOrNull()
            val year = match.groupValues[3]

            if (month != null && day != null && month in 1..12 && day in 1..31) {
                val monthName = getMonthName(month)
                val dayOrdinal = getDayOrdinal(day)
                "$monthName $dayOrdinal, $year"
            } else {
                match.value // Keep original if invalid
            }
        }

        // DD/MM/YYYY format (European)
        result = result.replace(Regex("([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})")) { match ->
            val day = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toIntOrNull()
            val year = match.groupValues[3]

            if (day != null && month != null && day in 1..31 && month in 1..12) {
                val monthName = getMonthName(month)
                val dayOrdinal = getDayOrdinal(day)
                "$dayOrdinal of $monthName, $year"
            } else {
                match.value // Keep original if invalid
            }
        }

        // YYYY-MM-DD format (ISO)
        result = result.replace(Regex("([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})")) { match ->
            val year = match.groupValues[1]
            val month = match.groupValues[2].toIntOrNull()
            val day = match.groupValues[3].toIntOrNull()

            if (month != null && day != null && month in 1..12 && day in 1..31) {
                val monthName = getMonthName(month)
                val dayOrdinal = getDayOrdinal(day)
                "$monthName $dayOrdinal, $year"
            } else {
                match.value // Keep original if invalid
            }
        }

        return result
    }

    /**
     * Converts time formats to spoken form.
     * Examples:
     * - "3:30 PM" → "3:30 P M"
     * - "15:45" → "15:45"
     * - "9:00 AM" → "9:00 A M"
     */
    private fun preprocessTimes(text: String): String {
        var result = text

        // Times with AM/PM
        result = result.replace(Regex("([0-9]{1,2}):([0-9]{2})\\s*(AM|PM)")) { match ->
            val time = "${match.groupValues[1]}:${match.groupValues[2]}"
            val period = match.groupValues[3].map { "$it " }.joinToString("").trim()
            "$time $period"
        }

        return result
    }

    /**
     * Converts phone numbers to spoken form.
     * Examples:
     * - "555-123-4567" → "5 5 5, 1 2 3, 4 5 6 7"
     * - "(555) 123-4567" → "5 5 5, 1 2 3, 4 5 6 7"
     */
    private fun preprocessPhoneNumbers(text: String): String {
        var result = text

        // Standard US phone number formats
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
     * Examples:
     * - "Item #12345" → "Item number 1 2 3 4 5"
     * - "SKU: ABC123" → "S K U: A B C 1 2 3"
     */
    private fun preprocessNumbers(text: String): String {
        var result = text

        // Item numbers, SKUs, etc.
        result = result.replace(Regex("#([0-9A-Z]{3,})")) { match ->
            val number = match.groupValues[1].map { "$it " }.joinToString("").trim()
            "number $number"
        }

        // Long number sequences (like receipt numbers)
        result = result.replace(Regex("\\b([0-9]{6,})\\b")) { match ->
            val number = match.groupValues[1].map { "$it " }.joinToString("").trim()
            number
        }

        return result
    }

    /**
     * Converts special characters to spoken form.
     * Examples:
     * - "&" → "and"
     * - "@" → "at"
     * - "%" → "percent"
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
     * Examples:
     * - "Dr." → "Doctor"
     * - "St." → "Street"
     * - "Str," → "Street,"
     * - "Inc." → "Incorporated"
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
            // Match abbreviation followed by punctuation or end of word
            result = result.replace(
                Regex("\\b$abbrev(?=[.,;:!?\\s]|$)", RegexOption.IGNORE_CASE),
                expansion
            )
        }

        return result
    }

    /**
     * Cleans up extra spacing and formatting issues.
     */
    private fun cleanupSpacing(text: String): String {
        return text
            .replace(Regex("\\s+"), " ") // Multiple spaces to single space
            .replace(Regex("\\s*,\\s*"), ", ") // Normalize comma spacing
            .replace(Regex("\\s*\\.\\s*"), ". ") // Normalize period spacing
            .trim()
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
