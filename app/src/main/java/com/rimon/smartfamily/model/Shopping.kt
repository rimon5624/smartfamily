package com.rimon.smartfamily.model

import androidx.annotation.StringRes
import com.rimon.smartfamily.R

/**
 * Store categories in a typical Israeli supermarket walking order (SHP-11).
 * Keywords drive automatic categorisation of new items in Hebrew and English.
 */
enum class ShopCategory(val key: String, @StringRes val title: Int, val keywords: List<String>) {
    PRODUCE("produce", R.string.cat_produce, listOf(
        "עגבני", "מלפפון", "בצל", "שום", "תפוח", "בננ", "תפוז", "לימון", "גזר", "חסה", "פלפל", "תפו\"א", "תפוחי אדמה",
        "בטטה", "קישוא", "חציל", "פטרוזיליה", "כוסברה", "שמיר", "נענע", "אבוקדו", "ענב", "אבטיח", "מלון", "אגס", "תות",
        "פטריות", "כרוב", "ברוקולי", "כרובית", "סלק", "ירק", "פרי", "tomato", "cucumber", "onion", "garlic", "apple",
        "banana", "orange", "lemon", "carrot", "lettuce", "pepper", "potato", "avocado", "grape", "berry", "mushroom",
        "cabbage", "broccoli", "fruit", "vegetable", "herb",
    )),
    BAKERY("bakery", R.string.cat_bakery, listOf(
        "לחם", "חלה", "חלות", "פיתה", "פיתות", "לחמני", "באגט", "טורטי", "עוגה", "עוגיות", "מאפה", "bread", "challah",
        "pita", "roll", "bagel", "tortilla", "cake", "cookie", "bun",
    )),
    DAIRY("dairy", R.string.cat_dairy, listOf(
        "חלב", "גבינ", "יוגורט", "קוטג", "שמנת", "חמאה", "לבן", "מעדן", "ביצים", "ביצה", "בולגרית", "צהובה", "מוצרלה",
        "milk", "cheese", "yogurt", "yoghurt", "cream", "butter", "egg", "cottage", "mozzarella",
    )),
    MEAT_FISH("meat_fish", R.string.cat_meat, listOf(
        "עוף", "חזה", "שניצל", "בשר", "טחון", "כבד", "פרגית", "כרעיים", "נקניק", "סלמון", "דג", "טונה", "הודו", "קבב",
        "chicken", "beef", "meat", "steak", "mince", "turkey", "salmon", "fish", "tuna", "sausage", "schnitzel",
    )),
    DRY("dry", R.string.cat_dry, listOf(
        "אורז", "פסטה", "ספגטי", "קמח", "סוכר", "מלח", "שמן", "קטניות", "עדשים", "חומוס", "שימורים", "רסק", "קפה",
        "תה", "דגני", "קורנפלקס", "שוקולד", "חטיף", "ביסלי", "במבה", "תבלין", "פפריקה", "כמון", "טחינה", "דבש",
        "ריבה", "ממרח", "קוסקוס", "פתיתים", "rice", "pasta", "flour", "sugar", "salt", "oil", "lentil", "bean", "coffee",
        "tea", "cereal", "chocolate", "snack", "spice", "honey", "jam", "sauce", "can",
    )),
    FROZEN("frozen", R.string.cat_frozen, listOf(
        "קפוא", "גלידה", "שלגון", "בורקס", "מלאווח", "ג'חנון", "פיצה קפואה", "אפונה", "frozen", "ice cream", "popsicle",
    )),
    DRINKS("drinks", R.string.cat_drinks, listOf(
        "מים", "סודה", "מיץ", "קולה", "שתייה", "שתיה", "בירה", "יין", "ענבים מיץ", "תירוש", "פטל", "water", "soda",
        "juice", "cola", "drink", "beer", "wine",
    )),
    CLEANING("cleaning", R.string.cat_cleaning, listOf(
        "נוזל", "כביסה", "אקונומיקה", "סבון כלים", "מרכך", "ספוג", "שקיות", "שקית", "נייר טואלט", "מגבונים", "נייר סופג",
        "אלומיניום", "ניילון", "טבליות", "מטהר", "detergent", "bleach", "softener", "sponge", "bags", "toilet paper",
        "paper towel", "wipes", "foil", "cleaner",
    )),
    PERSONAL("personal", R.string.cat_personal, listOf(
        "שמפו", "מרכך שיער", "סבון", "משחת שיניים", "מברשת", "דאודורנט", "קרם", "תחבושות", "צמר גפן", "גילוח",
        "shampoo", "soap", "toothpaste", "toothbrush", "deodorant", "lotion", "razor",
    )),
    BABY("baby", R.string.cat_baby, listOf(
        "חיתול", "טיטול", "מטרנה", "סימילאק", "מוצץ", "בקבוק תינוק", "diaper", "nappy", "formula", "pacifier",
    )),
    OTHER("other", R.string.cat_other, emptyList());

    companion object {
        fun fromKey(key: String): ShopCategory = entries.firstOrNull { it.key == key } ?: OTHER

        /** Picks a category from the item name; the most specific (longest) keyword wins. */
        fun guess(name: String): ShopCategory {
            val n = name.lowercase()
            var best: ShopCategory = OTHER
            var bestLen = 0
            entries.forEach { c ->
                c.keywords.forEach { k ->
                    if (k.length > bestLen && n.contains(k.lowercase())) {
                        best = c
                        bestLen = k.length
                    }
                }
            }
            return best
        }
    }
}

enum class QtyUnit(val key: String, @StringRes val title: Int) {
    NONE("", R.string.unit_none),
    PCS("pcs", R.string.unit_pcs),
    PACK("pack", R.string.unit_pack),
    KG("kg", R.string.unit_kg),
    G("g", R.string.unit_g),
    L("l", R.string.unit_l),
    ML("ml", R.string.unit_ml);

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: NONE
    }
}

enum class StorageLocation(val key: String, @StringRes val title: Int) {
    FRIDGE("fridge", R.string.loc_fridge),
    FREEZER("freezer", R.string.loc_freezer),
    PANTRY("pantry", R.string.loc_pantry),
    CLEANING("cleaning", R.string.loc_cleaning),
    BATHROOM("bathroom", R.string.loc_bathroom);

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: PANTRY

        /** A sensible default storage place for a shop category. */
        fun forCategory(c: ShopCategory) = when (c) {
            ShopCategory.DAIRY, ShopCategory.MEAT_FISH, ShopCategory.PRODUCE -> FRIDGE
            ShopCategory.FROZEN -> FREEZER
            ShopCategory.CLEANING -> CLEANING
            ShopCategory.PERSONAL, ShopCategory.BABY -> BATHROOM
            else -> PANTRY
        }
    }
}

/** One parsed item from typed text, speech or AI. */
data class ItemDraft(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "",
    val brand: String = "",
    val category: String = ShopCategory.guess(name).key,
    val barcode: String = "",
    val expiresAt: Long? = null,
)

/** Formats 1.0 as "1" and 1.5 as "1.5". */
fun Double.qty(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
