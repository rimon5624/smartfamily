package com.rimon.smartfamily.smart

import com.rimon.smartfamily.model.ItemDraft

/**
 * Turns a spoken or typed sentence into list items (SHP-3), in Hebrew or English:
 * "חלב, שתי חבילות ביצים ונוזל כביסה" -> milk; 2 packs of eggs; laundry detergent.
 */
object SpeechParser {

    private val numberWords = mapOf(
        "אחד" to 1.0, "אחת" to 1.0, "שניים" to 2.0, "שתיים" to 2.0, "שני" to 2.0, "שתי" to 2.0,
        "שלוש" to 3.0, "שלושה" to 3.0, "ארבע" to 4.0, "ארבעה" to 4.0, "חמש" to 5.0, "חמישה" to 5.0,
        "שש" to 6.0, "שישה" to 6.0, "שבע" to 7.0, "שבעה" to 7.0, "שמונה" to 8.0, "תשע" to 9.0,
        "תשעה" to 9.0, "עשר" to 10.0, "עשרה" to 10.0, "חצי" to 0.5,
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
        "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "half" to 0.5,
    )

    private val unitWords = mapOf(
        "חבילה" to "pack", "חבילות" to "pack", "מארז" to "pack", "מארזים" to "pack",
        "קילו" to "kg", "ק\"ג" to "kg", "קג" to "kg", "גרם" to "g", "ליטר" to "l", "ליטרים" to "l", "מ\"ל" to "ml",
        "יחידה" to "pcs", "יחידות" to "pcs", "בקבוק" to "pcs", "בקבוקים" to "pcs", "קופסה" to "pcs", "קופסאות" to "pcs",
        "pack" to "pack", "packs" to "pack", "package" to "pack", "packages" to "pack", "kg" to "kg", "kilo" to "kg",
        "kilos" to "kg", "gram" to "g", "grams" to "g", "g" to "g", "liter" to "l", "liters" to "l", "litre" to "l",
        "litres" to "l", "ml" to "ml", "bottle" to "pcs", "bottles" to "pcs", "box" to "pcs", "boxes" to "pcs",
        "can" to "pcs", "cans" to "pcs", "piece" to "pcs", "pieces" to "pcs",
    )

    private val fillers = setOf("תוסיף", "תוסיפי", "להוסיף", "הוסף", "תקנה", "צריך", "צריכים", "גם", "add", "buy", "need", "some", "and", "of", "של")

    fun parse(text: String): List<ItemDraft> {
        val cleaned = text.trim().replace('\n', ',')
        if (cleaned.isEmpty()) return emptyList()
        var parts = cleaned.split(Regex("[,;،]|\\s+(?:and|וגם|גם)\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        // Speech often arrives with no commas: split on the Hebrew "and" prefix (" ו") before a word,
        // but not on a double vav, which usually starts the word itself (וופל).
        if (parts.size == 1) {
            parts = parts[0].split(Regex("\\s+ו(?!ו)(?=[\\u0590-\\u05FF]{2,})")).map { it.trim() }.filter { it.isNotEmpty() }
        }
        return parts.mapNotNull { parsePart(it) }
    }

    private fun parsePart(part: String): ItemDraft? {
        val words = part.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        while (words.isNotEmpty() && words.first().lowercase() in fillers) words.removeAt(0)
        if (words.isEmpty()) return null
        var quantity = 1.0
        var unit = ""
        words.first().replace(',', '.').toDoubleOrNull()?.let { quantity = it; words.removeAt(0) }
            ?: numberWords[words.first().lowercase()]?.let { quantity = it; words.removeAt(0) }
        if (words.isNotEmpty()) unitWords[words.first().lowercase()]?.let { unit = it; words.removeAt(0) }
        while (words.isNotEmpty() && words.first().lowercase() in fillers) words.removeAt(0)
        // "milk 2" or "חלב 3"
        if (words.size > 1) words.last().toDoubleOrNull()?.let { quantity = it; words.removeAt(words.lastIndex) }
        val name = words.joinToString(" ").trim()
        if (name.isEmpty()) return null
        return ItemDraft(name = name, quantity = quantity, unit = unit)
    }
}
