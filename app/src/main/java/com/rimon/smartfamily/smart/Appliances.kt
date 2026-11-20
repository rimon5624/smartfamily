package com.rimon.smartfamily.smart

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * APL-7: the structured form of an appliance manual. Every fact keeps the manual page it came
 * from (APL-8). Version 1 of the schema; the same text is given to AI chats (APL-5).
 */
@Serializable
data class ApplianceProfile(
    val schemaVersion: Int = 1,
    val brand: String = "",
    val model: String = "",
    val programs: List<Program> = emptyList(),
    val options: List<Option> = emptyList(),
    val symbols: List<Symbol> = emptyList(),
    val errors: List<ErrorCode> = emptyList(),
    val maintenance: List<Maintenance> = emptyList(),
    val safety: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
) {
    @Serializable
    data class Program(
        val name: String,
        val temperature: String = "",       // e.g. "cold–60°C"
        val spin: String = "",              // e.g. "up to 1400"
        val durationMinutes: Int = 0,
        val maxLoadKg: Double = 0.0,
        val fabrics: List<String> = emptyList(), // cotton, synthetics, wool, delicates, mixed, baby, sport, shirts, bedding, jeans, towels, dark
        val notes: String = "",
        val page: Int = 0,
    )

    @Serializable
    data class Option(val name: String, val description: String = "", val page: Int = 0)

    @Serializable
    data class Symbol(val symbol: String, val meaning: String, val page: Int = 0)

    @Serializable
    data class ErrorCode(val code: String, val meaning: String, val fix: String = "", val page: Int = 0)

    @Serializable
    data class Maintenance(val task: String, val intervalDays: Int = 30, val howTo: String = "", val page: Int = 0)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true }

        fun parse(text: String): ApplianceProfile? {
            val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return runCatching { json.decodeFromString(serializer(), cleaned.substring(start, end + 1)) }.getOrNull()
                ?.takeIf { it.programs.isNotEmpty() || it.errors.isNotEmpty() || it.maintenance.isNotEmpty() || it.symbols.isNotEmpty() }
        }

        fun toJson(p: ApplianceProfile): String = json.encodeToString(serializer(), p)

        /** The prompt for turning a manual (or any text about the appliance) into this structure. */
        fun prompt(type: String, brand: String, model: String, language: String): String = """
            You are reading the user manual of a home appliance: type=$type, brand=$brand, model=$model.
            Turn it into JSON that follows exactly this shape (schemaVersion 1). Write the text values in $language,
            but keep program and button names exactly as printed on the appliance panel.
            {"schemaVersion":1,"brand":string,"model":string,
             "programs":[{"name":string,"temperature":string,"spin":string,"durationMinutes":number,"maxLoadKg":number,
               "fabrics":[one or more of "cotton","synthetics","wool","delicates","mixed","baby","sport","shirts","bedding","jeans","towels","dark","quick"],
               "notes":string,"page":number}],
             "options":[{"name":string,"description":string,"page":number}],
             "symbols":[{"symbol":string,"meaning":string,"page":number}],
             "errors":[{"code":string,"meaning":string,"fix":string,"page":number}],
             "maintenance":[{"task":string,"intervalDays":number,"howTo":string,"page":number}],
             "safety":[string],"tips":[string]}
            "page" is the manual page the fact comes from (0 if unknown). For an air conditioner use "programs" for its modes,
            for a car use "symbols" for dashboard warning lights and "maintenance" for service intervals.
            Answer with the JSON only.
        """.trimIndent()
    }
}

/** What the user is washing (APL-9). */
data class LaundryLoad(
    val fabric: String,          // cotton, synthetics, wool, delicates, mixed, baby, sport, shirts, bedding, jeans, towels
    val color: String,           // white, light, dark, new
    val soil: String,            // light, normal, heavy
    val labelMaxTemp: Int? = null, // from the care label, °C
)

data class LaundryAdvice(val program: ApplianceProfile.Program?, val temperature: String, val spin: String, val extras: List<String>, val reason: String)

/** APL-10: picks a program of THIS appliance by fabric, then adjusts temperature for colour, soil and the label. */
object LaundryAdvisor {
    fun advise(profile: ApplianceProfile, load: LaundryLoad): LaundryAdvice {
        val wanted = when (load.fabric) {
            "baby" -> listOf("baby", "cotton")
            "sport" -> listOf("sport", "synthetics")
            "shirts" -> listOf("shirts", "synthetics", "mixed")
            "bedding", "towels" -> listOf(load.fabric, "cotton")
            "jeans" -> listOf("jeans", "dark", "cotton")
            else -> listOf(load.fabric)
        } + if (load.color == "dark") listOf("dark") else emptyList()
        val program = profile.programs
            .map { p -> p to wanted.withIndex().sumOf { (i, f) -> if (f in p.fabrics || p.name.contains(f, ignoreCase = true)) 10 - i else 0 } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first
            ?: profile.programs.firstOrNull { "mixed" in it.fabrics }
        var temp = when {
            load.fabric in setOf("wool", "delicates") -> 30
            load.color == "new" || load.color == "dark" -> 30
            load.fabric == "baby" || load.fabric == "towels" || load.fabric == "bedding" -> 60
            load.color == "white" && load.soil == "heavy" -> 60
            load.soil == "heavy" -> 40
            else -> 40
        }
        load.labelMaxTemp?.let { temp = minOf(temp, it) }
        val spin = when (load.fabric) {
            "wool", "delicates" -> "600"
            "synthetics", "shirts", "sport" -> "800"
            else -> "1200"
        }
        val extras = buildList {
            if (load.soil == "heavy") add("prewash / stains")
            if (load.fabric == "baby") add("extra rinse")
            if (load.color == "new") add("wash separately")
        }
        val reason = listOfNotNull(
            program?.let { "${it.name}${if (it.page > 0) " (p. ${it.page})" else ""}" },
            "${temp}°C",
            load.labelMaxTemp?.let { "label ≤ ${it}°C" },
        ).joinToString(" · ")
        return LaundryAdvice(program, "${temp}°C", spin, extras, reason)
    }

    /** A usable default profile when there is no manual yet: the common programs most washers have. */
    fun genericWasher(lang: String): ApplianceProfile {
        val he = lang == "Hebrew"
        fun n(h: String, e: String) = if (he) h else e
        return ApplianceProfile(
            programs = listOf(
                ApplianceProfile.Program(n("כותנה", "Cotton"), "20–90°C", "1400", 180, 8.0, listOf("cotton", "towels", "bedding")),
                ApplianceProfile.Program(n("סינטטי", "Synthetics"), "20–60°C", "1200", 120, 4.0, listOf("synthetics", "shirts")),
                ApplianceProfile.Program(n("מעורב", "Mixed"), "20–40°C", "1400", 90, 4.0, listOf("mixed")),
                ApplianceProfile.Program(n("עדין / משי", "Delicates / Silk"), "cold–30°C", "800", 40, 2.0, listOf("delicates")),
                ApplianceProfile.Program(n("צמר", "Wool"), "cold–40°C", "800", 40, 2.0, listOf("wool")),
                ApplianceProfile.Program(n("כהים / ג'ינס", "Dark / Jeans"), "20–40°C", "1200", 90, 4.0, listOf("dark", "jeans")),
                ApplianceProfile.Program(n("ספורט", "Sportswear"), "20–40°C", "1200", 80, 3.0, listOf("sport")),
                ApplianceProfile.Program(n("מהיר 15'", "Quick 15'"), "cold–40°C", "800", 15, 2.0, listOf("quick")),
            ),
            maintenance = listOf(
                ApplianceProfile.Maintenance(n("ניקוי תוף (תוכנית ניקוי ב-90°)", "Drum clean (90° cleaning programme)"), 30),
                ApplianceProfile.Maintenance(n("ניקוי מסנן משאבה", "Clean the pump filter"), 90),
                ApplianceProfile.Maintenance(n("ניקוי מגירת אבקה", "Clean the detergent drawer"), 30),
            ),
        )
    }
}

/** 2.2: what is being dried. */
data class DryLoad(val fabric: String, val dryness: String, val size: String, val tumbleAllowed: Boolean?)
data class DryAdvice(val program: ApplianceProfile.Program?, val dryness: String, val heat: String, val extras: List<String>, val warning: String?, val reason: String)

/**
 * 2.2 dryer advice: a dry programme of THIS appliance by fabric, dryness level and heat; never recommends the dryer for
 * items whose care label forbids tumble drying, and warns for wool / delicates without a dedicated programme.
 */
object DryAdvisor {
    private val dryWords = listOf("dry", "ייבוש", "יבש", "dryer", "trocken", "séch")
    fun isDryProgram(p: ApplianceProfile.Program) = dryWords.any { p.name.contains(it, ignoreCase = true) }

    fun advise(profile: ApplianceProfile, load: DryLoad, he: Boolean, combo: Boolean): DryAdvice {
        fun t(h: String, e: String) = if (he) h else e
        val programs = if (combo) profile.programs.filter { isDryProgram(it) }.ifEmpty { profile.programs } else profile.programs
        val wanted = when (load.fabric) {
            "baby" -> listOf("baby", "cotton"); "sport" -> listOf("sport", "synthetics"); "shirts" -> listOf("shirts", "synthetics")
            "bedding", "towels" -> listOf(load.fabric, "cotton"); "jeans" -> listOf("jeans", "cotton", "dark"); else -> listOf(load.fabric)
        }
        val names = mapOf("cotton" to listOf("cotton", "כותנה"), "synthetics" to listOf("synthetic", "סינט"), "wool" to listOf("wool", "צמר"),
            "delicates" to listOf("delicate", "עדין"), "towels" to listOf("towel", "מגבות"), "bedding" to listOf("bedding", "מצעים"),
            "shirts" to listOf("shirt", "חולצ"), "jeans" to listOf("jeans", "ג'ינס"), "mixed" to listOf("mixed", "מעורב"), "sport" to listOf("sport", "ספורט"), "baby" to listOf("baby", "תינוק"))
        val program = programs.map { p -> p to wanted.withIndex().sumOf { (i, f) ->
            if (f in p.fabrics || names[f].orEmpty().any { p.name.contains(it, true) }) 10 - i else 0 } }
            .filter { it.second > 0 }.maxByOrNull { it.second }?.first
            ?: programs.firstOrNull { "mixed" in it.fabrics || it.name.contains("mixed", true) || it.name.contains("מעורב") }
        val gentle = load.fabric in setOf("wool", "delicates", "synthetics", "shirts", "sport")
        val warning = when {
            load.tumbleAllowed == false -> t("לפי התווית אסור לייבש במייבש: לייבש על מתקן או שטוח.", "The care label says no tumble drying: dry on a rack or flat.")
            load.fabric == "wool" && program?.let { "wool" in it.fabrics || it.name.contains("wool", true) || it.name.contains("צמר") } != true ->
                t("אין במייבש תוכנית צמר: עדיף לייבש שטוח באוויר.", "This dryer has no wool programme: better to dry flat in the air.")
            load.fabric == "delicates" && program == null -> t("בגדים עדינים: עדיף ייבוש באוויר.", "Delicates: better to air-dry.")
            else -> null
        }
        val dryness = when (load.dryness) {
            "iron" -> t("לגיהוץ", "Iron dry"); "extra" -> t("יבש במיוחד", "Extra dry"); else -> t("לארון", "Cupboard dry")
        }
        val heat = if (gentle || load.fabric == "baby") t("נמוך", "Low") else t("רגיל", "Normal")
        val extras = buildList {
            if (load.fabric in setOf("shirts", "synthetics", "mixed") || load.dryness == "iron") add(t("נגד קמטים", "Anti-crease"))
            if (load.size == "small") add(t("ייבוש לפי זמן קצר", "Short timed dry"))
            if (load.fabric == "towels" || load.fabric == "bedding") add(t("לנער לפני הכנסה", "Shake out before loading"))
            if (load.fabric == "jeans") add(t("להפוך על הצד ההפוך", "Turn inside out"))
        }
        val reason = listOfNotNull(program?.let { it.name + if (it.page > 0) " (p. ${it.page})" else "" }, dryness, heat).joinToString(" · ")
        return DryAdvice(if (load.tumbleAllowed == false) null else program, dryness, heat, extras, warning, reason)
    }

    fun genericDryer(lang: String): ApplianceProfile {
        val he = lang == "Hebrew"
        fun n(h: String, e: String) = if (he) h else e
        return ApplianceProfile(
            programs = listOf(
                ApplianceProfile.Program(n("כותנה", "Cotton"), durationMinutes = 150, maxLoadKg = 9.0, fabrics = listOf("cotton", "towels", "bedding")),
                ApplianceProfile.Program(n("סינטטי", "Synthetics"), durationMinutes = 90, maxLoadKg = 4.0, fabrics = listOf("synthetics", "shirts", "sport")),
                ApplianceProfile.Program(n("מעורב", "Mixed"), durationMinutes = 100, maxLoadKg = 4.0, fabrics = listOf("mixed")),
                ApplianceProfile.Program(n("עדין", "Delicates"), durationMinutes = 60, maxLoadKg = 2.0, fabrics = listOf("delicates")),
                ApplianceProfile.Program(n("מגבות", "Towels"), durationMinutes = 120, maxLoadKg = 6.0, fabrics = listOf("towels")),
                ApplianceProfile.Program(n("מצעים", "Bedding"), durationMinutes = 120, maxLoadKg = 4.0, fabrics = listOf("bedding")),
                ApplianceProfile.Program(n("מהיר 35'", "Quick 35'"), durationMinutes = 35, maxLoadKg = 1.0, fabrics = listOf("quick")),
            ),
            maintenance = listOf(
                ApplianceProfile.Maintenance(n("ניקוי מסנן מוך (אחרי כל ייבוש)", "Clean the lint filter (after every load)"), 1),
                ApplianceProfile.Maintenance(n("ריקון מיכל מים / ניקוי מסנן מחליף חום", "Empty the water tank / clean the heat-exchanger filter"), 30),
            ),
        )
    }
}
