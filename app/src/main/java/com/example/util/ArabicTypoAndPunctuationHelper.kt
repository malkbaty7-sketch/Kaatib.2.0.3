package com.example.util

/**
 * ArabicTypoAndPunctuationHelper:
 * Professional Arabic typography cleaner and formatting helper.
 * Solves common Arabic typing errors:
 * 1. Stray spaces before punctuation marks (e.g. "قال ، وذهب" -> "قال، وذهب")
 * 2. Detached conjunction Waw ("و ذهب" -> "وذهب")
 * 3. Double spaces consolidation
 * 4. English quotation marks to traditional Arabic brackets (« »)
 * 5. Ellipsis normalization (... / . . . -> …)
 */
object ArabicTypoAndPunctuationHelper {

    fun cleanArabicText(
        input: String,
        convertQuotesToArabicBrackets: Boolean = true,
        fixWawSpacing: Boolean = true,
        fixPunctuationSpacing: Boolean = true
    ): Pair<String, Int> {
        var text = input
        var fixesCount = 0

        // 1. Fix spaces before Arabic punctuation: ، ؛ ؟ ! : .
        if (fixPunctuationSpacing) {
            val punctRegex = Regex("""\s+([،؛؟!:\.\,\?])""")
            val matches = punctRegex.findAll(text).count()
            if (matches > 0) {
                text = punctRegex.replace(text, "$1")
                fixesCount += matches
            }

            // Ensure single space after punctuation if followed by non-space and non-punctuation
            val spaceAfterRegex = Regex("""([،؛؟!:\.])([^\s،؛؟!:\.\)»”])""")
            val afterMatches = spaceAfterRegex.findAll(text).count()
            if (afterMatches > 0) {
                text = spaceAfterRegex.replace(text, "$1 $2")
                fixesCount += afterMatches
            }
        }

        // 2. Fix detached Waw (e.g. "و كتب" -> "وكتب")
        if (fixWawSpacing) {
            // Waw preceded by whitespace or line start, followed by space then an Arabic letter
            val wawRegex = Regex("""(^|\s)و\s+([\u0621-\u064A])""")
            val wawMatches = wawRegex.findAll(text).count()
            if (wawMatches > 0) {
                text = wawRegex.replace(text, "$1و$2")
                fixesCount += wawMatches
            }
        }

        // 3. Convert English quotes to Arabic brackets
        if (convertQuotesToArabicBrackets) {
            val doubleQuotesRegex = Regex(""""([^"]+)"""")
            val quoteMatches = doubleQuotesRegex.findAll(text).count()
            if (quoteMatches > 0) {
                text = doubleQuotesRegex.replace(text, "«$1»")
                fixesCount += quoteMatches
            }
        }

        // 4. Normalize multiple spaces (excluding newlines)
        val multiSpaceRegex = Regex("""[^\S\r\n]{2,}""")
        val spaceMatches = multiSpaceRegex.findAll(text).count()
        if (spaceMatches > 0) {
            text = multiSpaceRegex.replace(text, " ")
            fixesCount += spaceMatches
        }

        // 5. Normalize ellipsis: "..." or "...." -> "…"
        val dotsRegex = Regex("""\.{3,}""")
        val dotMatches = dotsRegex.findAll(text).count()
        if (dotMatches > 0) {
            text = dotsRegex.replace(text, "…")
            fixesCount += dotMatches
        }

        return Pair(text, fixesCount)
    }

    /**
     * Normalize Arabic string for fuzzy matching (disregarding harakat, hamza types, taa marbuta)
     */
    fun normalizeArabicForSearch(input: String): String {
        return input
            // Remove diacritics / Tashkeel
            .replace(Regex("""[\u064B-\u065F\u0670]"""), "")
            // Normalize Alef
            .replace(Regex("""[إأآاٱ]"""), "ا")
            // Normalize Taa Marbuta and Haa
            .replace("ة", "ه")
            // Normalize Yaa / Alef Maksura
            .replace("ى", "ي")
            // Remove Tatweel (Kashida)
            .replace("ـ", "")
            .trim()
    }
}
