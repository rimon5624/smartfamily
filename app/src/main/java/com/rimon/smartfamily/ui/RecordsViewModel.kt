package com.rimon.smartfamily.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.GoalEntity
import com.rimon.smartfamily.data.db.GrowthEntryEntity
import com.rimon.smartfamily.data.db.KitItemEntity
import com.rimon.smartfamily.data.db.MeterReadingEntity
import com.rimon.smartfamily.data.db.PocketEntryEntity
import com.rimon.smartfamily.data.db.RecurringPaymentEntity
import com.rimon.smartfamily.data.db.RepairEntity
import com.rimon.smartfamily.data.db.RoutineCheckEntity
import com.rimon.smartfamily.data.db.RoutineStepEntity
import com.rimon.smartfamily.data.db.SplitExpenseEntity
import com.rimon.smartfamily.data.db.TreePersonEntity
import com.rimon.smartfamily.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 2.0: one view model for the smaller family records (bills, pocket money, goals, shared costs,
 * growth, repairs, meters, kits, kids' routines, the family tree). Every save is local first and
 * synced like everything else.
 */
class RecordsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartFamilyApp).container
    private val db = c.db
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private fun now() = System.currentTimeMillis()
    fun id() = UUID.randomUUID().toString()

    /** 2.2: a birthday (both calendars) or a yahrzeit (Hebrew) from a person in the family tree. */
    fun addTreeDate(p: com.rimon.smartfamily.data.db.TreePersonEntity, date: String, yahrzeit: Boolean) = viewModelScope.launch {
        val d = com.rimon.smartfamily.smart.DateText.parse(date) ?: return@launch
        val h = com.rimon.smartfamily.smart.DateText.hebrew(d, com.rimon.smartfamily.smart.DateText.isAfterSunset(date))
        val exists = c.homeLife.allDatesForReminders().any { it.familyId == p.familyId && it.personName == p.name && it.type == (if (yahrzeit) "YAHRZEIT" else "BIRTHDAY") }
        if (exists) return@launch
        c.homeLife.saveDate(com.rimon.smartfamily.data.db.FamilyDateEntity(
            id = id(), familyId = p.familyId, title = p.name, personName = p.name, type = if (yahrzeit) "YAHRZEIT" else "BIRTHDAY",
            gregorian = d.toString(), hebrewDay = h.day, hebrewMonth = h.month, hebrewYear = h.year,
            drive = if (yahrzeit) "HEBREW" else "BOTH", afterSunset = com.rimon.smartfamily.smart.DateText.isAfterSunset(date),
        ))
    }
    private fun changed() = SyncScheduler.requestSoon(getApplication())
    private fun go(block: suspend () -> Unit) = viewModelScope.launch {
        try { block(); changed() } catch (e: Exception) { _messages.tryEmit(UiMessage.Text(e.message ?: e.javaClass.simpleName)) }
    }
    fun members(familyId: String) = c.families.observeMembers(familyId)

    fun payments(f: String): Flow<List<RecurringPaymentEntity>> = db.recurringPayments().observeForFamily(f)
    fun save(x: RecurringPaymentEntity) = go { db.recurringPayments().upsert(x.copy(updatedAt = now(), dirty = true)) }
    fun delete(x: RecurringPaymentEntity) = go { db.recurringPayments().upsert(x.copy(deletedAt = now(), updatedAt = now(), dirty = true)) }

    fun pocket(f: String): Flow<List<PocketEntryEntity>> = db.pocketEntrys().observeForFamily(f)
    fun save(x: PocketEntryEntity) = go { db.pocketEntrys().upsert(x.copy(updatedAt = now(), dirty = true)) }
    fun delete(x: PocketEntryEntity) = go { db.pocketEntrys().upsert(x.copy(deletedAt = now(), updatedAt = now(), dirty = true)) }

    fun goals(f: String): Flow<List<GoalEntity>> = db.goals().observeForFamily(f)
    fun save(x: GoalEntity) = go { db.goals().upsert(x.copy(updatedAt = now(), dirty = true)) }
    fun delete(x: GoalEntity) = go { db.goals().upsert(x.copy(deletedAt = now(), updatedAt = now(), dirty = true)) }

    fun splits(f: String): Flow<List<SplitExpenseEntity>> = db.splitExpenses().observeForFamily(f)
    fun save(x: SplitExpenseEntity) = go { db.splitExpenses().upsert(x.copy(updatedAt = now(), dirty = true)) }
    fun delete(x: SplitExpenseEntity) = go { db.splitExpenses().upsert(x.copy(deletedAt = now(), updatedAt = now(), dirty = true)) }

    fun growth(f: String): Flow<List<GrowthEntryEntity>> = db.growthEntrys().observeForFamily(f)
    fun save(x: GrowthEntryEntity) = go { db.growthEntrys().upsert(x.copy(updatedAt = now(), dirty = true)) }
    fun delete(x: GrowthEntryEntity) = go { db.growthEntrys().upsert(x.copy(deletedAt = now(), updatedAt = now(), dirty = true)) }

    fun repairs(f: String): Flow<List<RepairEntity>> = db.repairs().observeForFamily(f)
    fun save(x: RepairEntity) = go { db.repairs().upsert(x.copy(updatedAt = now(), dirty = true)) }
    fun delete(x: RepairEntity) = go { db.repairs().upsert(x.copy(deletedAt = now(), updatedAt = now(), dirty = true)) }

    fun meters(f: String): Flow<List<MeterReadingEntity>> = db.meterReadings().observeForFamily(f)
    fun save(x: MeterReadingEntity) = go { db.meterReadings().upsert(x.copy(updatedAt = now(), dirty = true)) }
    fun delete(x: MeterReadingEntity) = go { db.meterReadings().upsert(x.copy(deletedAt = now(), updatedAt = now(), dirty = true)) }

    fun kits(f: String): Flow<List<KitItemEntity>> = db.kitItems().observeForFamily(f)
    fun save(x: KitItemEntity) = go { db.kitItems().upsert(x.copy(updatedAt = now(), dirty = true)) }
    fun delete(x: KitItemEntity) = go { db.kitItems().upsert(x.copy(deletedAt = now(), updatedAt = now(), dirty = true)) }
    fun resetKit(f: String, kit: String, items: List<KitItemEntity>) = go {
        items.filter { it.kit == kit && it.checked }.forEach { db.kitItems().upsert(it.copy(checked = false, updatedAt = now(), dirty = true)) }
    }
    fun addKitTemplate(f: String, kit: String, titles: List<String>) = go {
        titles.forEachIndexed { i, t -> db.kitItems().upsert(KitItemEntity(id = id(), familyId = f, kit = kit, title = t, sort = i)) }
    }

    fun routine(f: String): Flow<List<RoutineStepEntity>> = db.routineSteps().observeForFamily(f)
    fun save(x: RoutineStepEntity) = go { db.routineSteps().upsert(x.copy(updatedAt = now(), dirty = true)) }
    fun delete(x: RoutineStepEntity) = go { db.routineSteps().upsert(x.copy(deletedAt = now(), updatedAt = now(), dirty = true)) }
    fun checks(f: String): Flow<List<RoutineCheckEntity>> = db.routineChecks().observeForFamily(f)
    fun toggleCheck(step: RoutineStepEntity, date: String, current: RoutineCheckEntity?) = go {
        val idd = "${step.id}|$date"
        db.routineChecks().upsert(
            RoutineCheckEntity(id = idd, familyId = step.familyId, stepId = step.id, userId = step.userId, date = date, done = !(current?.done == true && current.deletedAt == null))
        )
    }
    fun addRoutineTemplate(f: String, userId: String, part: String, steps: List<Pair<String, String>>) = go {
        steps.forEachIndexed { i, (e, t) -> db.routineSteps().upsert(RoutineStepEntity(id = id(), familyId = f, userId = userId, title = t, emoji = e, part = part, sort = i)) }
    }

    /**
     * CHR-5 holiday packs: one tap creates the shopping list, the tasks scheduled backwards from
     * the holiday, and its checklist. Everything is editable afterwards.
     */
    fun createHolidayPack(f: String, holiday: HolidayPack, hebrew: Boolean, on: java.time.LocalDate) = go {
        val uid = c.settings.current().userId
        val city = com.rimon.smartfamily.smart.Cities.byKey(c.settings.current().zmanimCity)
        val list = c.shopping.createList(f, if (hebrew) holiday.he else holiday.en, uid)
        c.shopping.addItems(list.id, (if (hebrew) holiday.shoppingHe else holiday.shoppingEn).map { com.rimon.smartfamily.model.ItemDraft(name = it) }, uid)
        (if (hebrew) holiday.tasksHe else holiday.tasksEn).forEach { (daysBefore, title) ->
            val ch = c.homeLife.newChore(f, title, uid, com.rimon.smartfamily.data.repo.Schedule.ONCE, city)
            val due = com.rimon.smartfamily.smart.JewishDays.startMs(on.minusDays(daysBefore.toLong())) + 18 * 3_600_000L
            c.db.chores().get(ch.id)?.let { c.homeLife.saveChore(it.copy(nextDue = due), city) }
        }
        _messages.tryEmit(UiMessage.Res(com.rimon.smartfamily.R.string.shabbat_pack_created))
    }

    fun tree(f: String): Flow<List<TreePersonEntity>> = db.treePersons().observeForFamily(f)
    fun save(x: TreePersonEntity) = go { db.treePersons().upsert(x.copy(updatedAt = now(), dirty = true)) }
    fun delete(x: TreePersonEntity) = go { db.treePersons().upsert(x.copy(deletedAt = now(), updatedAt = now(), dirty = true)) }
}


/** Ready-made holiday preparation packs (2.0). */
enum class HolidayPack(val he: String, val en: String, val keyHe: String, val shoppingHe: List<String>, val shoppingEn: List<String>, val tasksHe: List<Pair<Int, String>>, val tasksEn: List<Pair<Int, String>>) {
    PESACH("פסח", "Pesach", "פסח",
        listOf("מצות", "יין לארבע כוסות", "מרור / חסה", "חזרת", "כרפס", "ביצים", "שוק עוף לזרוע", "חרוסת: תפוחים, אגוזים, תמרים", "קמח מצה", "שמן כשר לפסח", "נייר כסף", "כלים חד פעמיים"),
        listOf("Matza", "Wine for four cups", "Maror / lettuce", "Horseradish", "Karpas", "Eggs", "Chicken neck for zroa", "Charoset: apples, nuts, dates", "Matza meal", "Kosher-for-Pesach oil", "Foil", "Disposable dishes"),
        listOf(21 to "ניקיון חדרי שינה", 18 to "ניקיון סלון וספרים", 14 to "ניקיון ארונות מטבח", 10 to "ניקיון רכב", 7 to "מקרר ומקפיא", 4 to "תנור וכיריים – הכשרה", 2 to "החלפת כלים לפסח", 1 to "בדיקת חמץ", 0 to "ביעור חמץ ומכירת חמץ"),
        listOf(21 to "Clean bedrooms", 18 to "Clean the living room and books", 14 to "Clean kitchen cupboards", 10 to "Clean the car", 7 to "Fridge and freezer", 4 to "Kasher oven and stove", 2 to "Switch to Pesach dishes", 1 to "Search for chametz", 0 to "Burn and sell chametz")),
    SUKKOT("סוכות", "Sukkot", "סוכות",
        listOf("ארבעת המינים", "קישוטי סוכה", "סכך", "תאורה לסוכה", "כלים חד פעמיים"),
        listOf("Four species", "Sukkah decorations", "Schach", "Sukkah lights", "Disposable dishes"),
        listOf(5 to "בניית הסוכה", 3 to "קניית ארבעת המינים", 2 to "קישוט הסוכה עם הילדים", 1 to "בישולים לחג"),
        listOf(5 to "Build the sukkah", 3 to "Buy the four species", 2 to "Decorate with the kids", 1 to "Holiday cooking")),
    ROSH_HASHANA("ראש השנה", "Rosh Hashana", "ראש השנה",
        listOf("תפוחים", "דבש", "רימון", "תמרים", "ראש דג", "סילק", "כרתי", "דלעת", "חלות עגולות"),
        listOf("Apples", "Honey", "Pomegranate", "Dates", "Fish head", "Beets", "Leeks", "Pumpkin", "Round challot"),
        listOf(7 to "ברכות שנה טובה למשפחה", 3 to "קניות לסימנים", 1 to "בישולים לחג", 0 to "הכנת שולחן הסימנים"),
        listOf(7 to "New-year greetings to the family", 3 to "Shop for the simanim", 1 to "Holiday cooking", 0 to "Set the simanim table")),
    CHANUKAH("חנוכה", "Chanukah", "חנוכה",
        listOf("נרות חנוכה", "שמן זית לחנוכייה", "פתילות", "סופגניות", "תפוחי אדמה ללביבות", "מטבעות שוקולד", "סביבונים"),
        listOf("Chanukah candles", "Olive oil for the menorah", "Wicks", "Sufganiyot", "Potatoes for latkes", "Chocolate coins", "Dreidels"),
        listOf(3 to "הכנת החנוכייה", 1 to "מתנות / דמי חנוכה לילדים"),
        listOf(3 to "Prepare the menorah", 1 to "Gifts / Chanukah gelt")),
    PURIM("פורים", "Purim", "פורים",
        listOf("משלוחי מנות", "מתנות לאביונים", "תחפושות", "רעשנים", "יין לסעודה"),
        listOf("Mishloach manot", "Gifts to the poor", "Costumes", "Groggers", "Wine for the feast"),
        listOf(10 to "תחפושות לילדים", 4 to "הכנת משלוחי מנות", 1 to "חלוקת משלוחים", 0 to "סעודת פורים"),
        listOf(10 to "Costumes for the kids", 4 to "Prepare mishloach manot", 1 to "Deliver mishloach manot", 0 to "Purim feast")),
    SHAVUOT("שבועות", "Shavuot", "שבועות",
        listOf("גבינות", "שמנת", "בצק עלים", "פירות העונה", "פרחים"),
        listOf("Cheeses", "Cream", "Puff pastry", "Seasonal fruit", "Flowers"),
        listOf(2 to "אפיית עוגת גבינה", 1 to "בישולים חלביים לחג"),
        listOf(2 to "Bake a cheesecake", 1 to "Dairy holiday cooking")),
}
