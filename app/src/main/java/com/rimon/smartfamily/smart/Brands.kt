package com.rimon.smartfamily.smart

/**
 * 2.2: common appliance brands in Israel by appliance type, with Hebrew spellings, for autocomplete (works offline).
 */
object Brands {
    private val he = mapOf(
        "Samsung" to "סמסונג", "LG" to "אל ג'י", "Bosch" to "בוש", "Siemens" to "סימנס", "Electrolux" to "אלקטרולוקס", "AEG" to "איי אי ג'י",
        "Whirlpool" to "וירפול", "Miele" to "מילה", "Beko" to "בקו", "Haier" to "הייר", "Hisense" to "הייסנס", "Candy" to "קנדי",
        "Hoover" to "הובר", "Indesit" to "אינדסיט", "Ariston" to "אריסטון", "Blomberg" to "בלומברג", "Gorenje" to "גורניה", "Sharp" to "שארפ",
        "Tadiran" to "תדיראן", "Electra" to "אלקטרה", "Tornado" to "טורנדו", "Tadiran-Gree" to "תדיראן גרי", "Gree" to "גרי", "Airwell" to "איירוול",
        "Mitsubishi Electric" to "מיצובישי", "Daikin" to "דייקין", "Fujitsu" to "פוג'יטסו", "Toshiba" to "טושיבה", "Midea" to "מידאה",
        "Crystal" to "קריסטל", "Normande" to "נורמנדי", "Amcor" to "אמקור", "Westinghouse" to "וסטינגהאוס", "Fagor" to "פאגור",
        "Smeg" to "סמג", "De'Longhi" to "דלונגי", "Neff" to "נף", "Gaggenau" to "גגנאו", "Kenwood" to "קנווד", "Panasonic" to "פנסוניק",
        "Toyota" to "טויוטה", "Hyundai" to "יונדאי", "Kia" to "קיה", "Mazda" to "מאזדה", "Skoda" to "סקודה", "Tesla" to "טסלה", "BYD" to "בי וויי די",
        "Chevrolet" to "שברולט", "Volkswagen" to "פולקסווגן", "Suzuki" to "סוזוקי", "Nissan" to "ניסאן", "Mitsubishi" to "מיצובישי מוטורס",
        "Chromagen" to "כרומגן", "Ariston" to "אריסטון", "Solaris" to "סולאריס", "Ilan" to "אילן",
    )
    private val byType = mapOf(
        "washer" to listOf("Samsung", "LG", "Bosch", "Siemens", "Electrolux", "AEG", "Whirlpool", "Miele", "Beko", "Haier", "Candy", "Hoover", "Indesit", "Blomberg", "Gorenje", "Sharp", "Hisense"),
        "dryer" to listOf("Samsung", "LG", "Bosch", "Siemens", "Electrolux", "AEG", "Whirlpool", "Miele", "Beko", "Haier", "Candy", "Hoover", "Blomberg", "Crystal", "Midea"),
        "washer_dryer" to listOf("Samsung", "LG", "Bosch", "Siemens", "Electrolux", "AEG", "Haier", "Beko", "Candy", "Hoover", "Miele"),
        "dishwasher" to listOf("Bosch", "Siemens", "Miele", "Electrolux", "AEG", "Samsung", "LG", "Beko", "Whirlpool", "Blomberg", "Smeg", "Neff", "Haier", "Midea", "Fagor"),
        "ac" to listOf("Tadiran", "Electra", "Tornado", "Tadiran-Gree", "Gree", "Airwell", "Mitsubishi Electric", "Daikin", "Fujitsu", "Toshiba", "LG", "Samsung", "Midea", "Haier", "Normande"),
        "oven" to listOf("Bosch", "Siemens", "Electrolux", "AEG", "Smeg", "Miele", "Neff", "Gaggenau", "De'Longhi", "Beko", "Fagor", "Whirlpool", "Samsung", "Crystal", "Normande"),
        "fridge" to listOf("Samsung", "LG", "Bosch", "Siemens", "Sharp", "Hisense", "Haier", "Beko", "Whirlpool", "Electrolux", "Westinghouse", "Amcor", "Normande", "Smeg", "Miele", "Toshiba", "Panasonic"),
        "water_heater" to listOf("Chromagen", "Ariston", "Solaris", "Ilan", "Amcor", "Electra"),
        "car" to listOf("Toyota", "Hyundai", "Kia", "Mazda", "Skoda", "Tesla", "BYD", "Chevrolet", "Volkswagen", "Suzuki", "Nissan", "Mitsubishi"),
    )
    private val all = byType.values.flatten().distinct()

    /** Brands for [type] (all brands for "other") that match [typed] in English or Hebrew; [family] (already used) come first. */
    fun suggest(type: String, typed: String, family: List<String> = emptyList()): List<String> {
        val base = (family + (byType[type] ?: all)).distinct()
        val t = typed.trim()
        if (t.isEmpty()) return base.take(12)
        return (base + all).distinct().filter { b -> b.contains(t, true) || (he[b]?.contains(t) == true) }.take(12)
    }

    /** "סמסונג" → "Samsung", so a Hebrew spelling saves as the brand name used on the sticker. */
    fun canonical(typed: String): String = he.entries.firstOrNull { it.value == typed.trim() }?.key ?: typed.trim()
}
