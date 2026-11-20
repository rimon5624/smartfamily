package com.rimon.smartfamily.smart

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Where each chain publishes its price files under the Israeli Food Law price-transparency rules.
 * All sources are public and free; nothing goes through our server.
 * Endpoints follow the open-source OpenIsraeliSupermarkets scrapers.
 */
enum class Engine { CERBERUS, BINA, PUBLISH_PRICE, SHUFERSAL, LAIB }

data class Chain(val key: String, val he: String, val en: String, val engine: Engine, val param: String, val chainId: String = "")

object Chains {
    val all = listOf(
        Chain("shufersal", "שופרסל", "Shufersal", Engine.SHUFERSAL, "https://prices.shufersal.co.il/"),
        Chain("rami_levy", "רמי לוי", "Rami Levy", Engine.CERBERUS, "RamiLevi"),
        Chain("osher_ad", "אושר עד", "Osher Ad", Engine.CERBERUS, "osherad"),
        Chain("yohananof", "יוחננוף", "Yohananof", Engine.CERBERUS, "yohananof"),
        Chain("carrefour", "קרפור / יינות ביתן", "Carrefour / Yeinot Bitan", Engine.PUBLISH_PRICE, "carrefour"),
        Chain("tiv_taam", "טיב טעם", "Tiv Taam", Engine.CERBERUS, "TivTaam"),
        Chain("keshet", "קשת טעמים", "Keshet Teamim", Engine.CERBERUS, "Keshet"),
        Chain("shuk_hayir", "שוק העיר", "Shuk Hayir", Engine.BINA, "shuk-hayir", "7290058148776"),
        Chain("zol_vebegadol", "זול ובגדול", "Zol Vebegadol", Engine.BINA, "zolvebegadol", "7290058173198"),
        Chain("super_sapir", "סופר ספיר / נטו חיסכון", "Super Sapir", Engine.BINA, "supersapir", "7290058156016"),
        Chain("king_store", "קינג סטור", "King Store", Engine.BINA, "kingstore", "7290058108879"),
        Chain("maayan2000", "מעיין 2000", "Maayan 2000", Engine.BINA, "maayan2000", "7290058159628"),
        Chain("shefa", "שפע ברכת השם", "Shefa Birkat Hashem", Engine.BINA, "shefabirkathashem", "7290058134977"),
        Chain("bareket", "סופר ברקת", "Super Bareket", Engine.BINA, "superbareket", "7290875100001"),
        Chain("politzer", "פוליצר", "Politzer", Engine.CERBERUS, "politzer"),
        Chain("fresh_market", "פרש מרקט / סופר דוש", "Fresh Market / Super Dosh", Engine.CERBERUS, "freshmarket"),
        Chain("stop_market", "סטופ מרקט", "Stop Market", Engine.CERBERUS, "Stop_Market"),
        Chain("super_yuda", "סופר יודה", "Super Yuda", Engine.CERBERUS, "yuda_ho"),
        Chain("dor_alon", "אלונית (דור אלון)", "Alonit (Dor Alon)", Engine.CERBERUS, "doralon"),
        Chain("yellow", "Yellow", "Yellow", Engine.CERBERUS, "Paz_bo"),
        Chain("salach_dabach", "סאלח דבאח", "Salach Dabach", Engine.CERBERUS, "SalachD"),
        Chain("cofix", "סופר קופיקס", "Super Cofix", Engine.CERBERUS, "SuperCofixApp"),
        Chain("quik", "קוויק", "Quik", Engine.PUBLISH_PRICE, "quik"),
        Chain("victory", "ויקטורי", "Victory", Engine.LAIB, "7290696200003", "7290696200003"),
        Chain("mahsanei_hashuk", "מחסני השוק", "Mahsanei HaShuk", Engine.LAIB, "7290661400001", "7290661400001"),
        Chain("het_cohen", "ח. כהן", "H. Cohen", Engine.LAIB, "7290455000004", "7290455000004"),
    )
    fun byKey(k: String) = all.firstOrNull { it.key == k }
}

data class StoreInfo(val id: String, val name: String, val city: String, val address: String)
data class PriceRow(val code: String, val name: String, val price: Double, val unitPrice: Double, val unit: String, val manufacturer: String, val updated: String)

/** A source problem the app can explain in plain words. */
class PriceSourceException(val why: Why) : IOException(why.name) {
    enum class Why { NO_FILES, OVERNIGHT, TIMEOUT }
}

class PriceFetcher {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------------- listing ----------------

    /** Names of files available for a chain (newest last), optionally only those containing [contains]. */
    private suspend fun list(chain: Chain, kind: String, storeId: String?): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        when (chain.engine) {
            Engine.CERBERUS -> ftp(chain.param) { c ->
                c.listNames().orEmpty().filter { n -> kindMatches(n, kind) && (storeId == null || n.contains("-${storeId.padStart(3, '0')}-") || n.contains("-$storeId-")) }
                    .sorted().map { it to it }
            }
            Engine.BINA -> {
                val type = when (kind) { "Stores" -> 1; "PriceFull" -> 4; else -> 2 }
                val url = "http://${chain.param}.binaprojects.com/MainIO_Hok.aspx?_=${chain.chainId}&wReshet=" +
                    URLEncoder.encode("הכל", "UTF-8") + "&WFileType=$type&WDate=&WStore=${storeId.orEmpty()}"
                json.parseToJsonElement(text(url)).jsonArray.mapNotNull { it.jsonObject["FileNm"]?.jsonPrimitive?.contentOrNull }
                    .filter { kindMatches(it, kind) }.sorted()
                    .map { it to "http://${chain.param}.binaprojects.com/Download.aspx?FileNm=$it" }
            }
            Engine.PUBLISH_PRICE -> {
                val base = "https://prices.${chain.param}.co.il"
                val page = text("$base/")
                val path = Regex("const path = ['\"]([^'\"]*)['\"]").find(page)?.groupValues?.get(1).orEmpty()
                val files = Regex("const files = (\\[.*?]);?\\s*\n").find(page)?.groupValues?.get(1) ?: "[]"
                json.parseToJsonElement(files).jsonArray.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                    .filter { kindMatches(it, kind) && (storeId == null || it.contains("-${storeId.padStart(3, '0')}-") || it.contains("-$storeId-")) }
                    .sorted().map { it to (if (path.isBlank()) "$base/$it" else "$base/${path.trim('/')}/$it") }
            }
            Engine.LAIB -> {
                val base = "https://laibcatalog.co.il/webapi"
                val url = "$base/api/getfiles?edi=${chain.param}" + (storeId?.let { "&branchNumber=$it" } ?: "")
                json.parseToJsonElement(text(url)).jsonArray.mapNotNull { it.jsonObject["fileName"]?.jsonPrimitive?.contentOrNull }
                    .filter { kindMatches(it, kind) && (storeId == null || it.contains("-${storeId.padStart(3, '0')}-") || it.contains("-$storeId-")) }
                    .sorted().map { it to "$base/${chain.param}/$it" }
            }
            Engine.SHUFERSAL -> {
                val cat = if (kind == "Stores") 5 else if (kind == "PriceFull") 2 else if (kind == "PromoFull") 4 else 1
                val page = text(chain.param + "FileObject/UpdateCategory?catID=$cat&storeId=${storeId ?: "0"}")
                Regex("href=\"(https://[^\"]+?\\.(?:gz|xml)[^\"]*)\"").findAll(page).map { it.groupValues[1].replace("&amp;", "&") }
                    .map { u -> u.substringBefore('?').substringAfterLast('/') to u }
                    .filter { kindMatches(it.first, kind) }.toList().sortedBy { it.first }
            }
        }
    }

    // ---------------- download ----------------

    private suspend fun download(chain: Chain, file: Pair<String, String>): ByteArray = withContext(Dispatchers.IO) {
        when (chain.engine) {
            Engine.CERBERUS -> ftp(chain.param) { c ->
                c.setFileType(FTP.BINARY_FILE_TYPE)
                c.retrieveFileStream(file.first)?.use { it.readBytes() }?.also { c.completePendingCommand() } ?: throw IOException("FTP: ${c.replyString}")
            }
            Engine.BINA -> {
                val spath = json.parseToJsonElement(text(file.second)).jsonArray.first().jsonObject["SPath"]!!.jsonPrimitive.content
                bytes(spath)
            }
            else -> bytes(file.second)
        }
    }

    private fun text(url: String): String = http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
        if (!r.isSuccessful) throw IOException("HTTP ${r.code}: $url")
        r.body!!.string()
    }

    /** 2.2: files are capped (a price file is a few MB) so a broken or hostile server cannot fill the phone. */
    private fun bytes(url: String): ByteArray = http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
        val len = r.body!!.contentLength()
        if (len > MAX_FILE) throw IOException("file too large")
        val out = java.io.ByteArrayOutputStream()
        r.body!!.byteStream().use { input ->
            val buf = ByteArray(64 * 1024); var total = 0L
            while (true) { val n = input.read(buf); if (n < 0) break; total += n; if (total > MAX_FILE) throw IOException("file too large"); out.write(buf, 0, n) }
        }
        out.toByteArray()
    }

    /**
     * 2.2 fix: the Cerberus server (Rami Levy, Osher Ad, Yohananof, Tiv Taam, Keshet…) now accepts only explicit
     * FTP over TLS for the login (like the OpenIsraeliSupermarkets scraper's FTP_TLS); plain FTP was refused, so
     * these chains' branches never downloaded. The data channel stays as the server offers it.
     */
    private fun <T> ftp(user: String, block: (FTPClient) -> T): T {
        val c: FTPClient = org.apache.commons.net.ftp.FTPSClient(false)
        c.connectTimeout = 20_000
        c.controlEncoding = "UTF-8"
        try {
            c.connect("url.retail.publishedprices.co.il")
            if (!c.login(user, "")) throw IOException("FTP login refused for $user: ${c.replyString?.trim()}")
            c.enterLocalPassiveMode()
            c.setDataTimeout(java.time.Duration.ofSeconds(90))
            return block(c)
        } finally {
            runCatching { c.logout() }; runCatching { c.disconnect() }
        }
    }

    /** Files are gzip, zip or plain XML depending on the chain. */
    private fun open(bytes: ByteArray): InputStream = when {
        bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte() -> GZIPInputStream(ByteArrayInputStream(bytes))
        bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> ZipInputStream(ByteArrayInputStream(bytes)).also { it.nextEntry }
        else -> ByteArrayInputStream(bytes)
    }

    // ---------------- parsing (tag names vary between chains, so match case-insensitively) ----------------

    private fun parse(input: InputStream, record: Set<String>, onRecord: (Map<String, String>) -> Unit) = try {
        parseRaw(input, record, onRecord)
    } catch (e: org.xmlpull.v1.XmlPullParserException) {
        throw IOException("The chain's file is damaged (${e.message?.take(60)})")
    }

    private fun parseRaw(input: InputStream, record: Set<String>, onRecord: (Map<String, String>) -> Unit) {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        var current: MutableMap<String, String>? = null
        var tag = ""
        while (true) {
            when (p.next()) {
                XmlPullParser.START_TAG -> {
                    val n = p.name.lowercase()
                    if (n in record) current = HashMap() else tag = n
                }
                XmlPullParser.TEXT -> current?.let { if (tag.isNotEmpty() && !p.isWhitespace) it[tag] = (it[tag].orEmpty() + p.text).trim() }
                XmlPullParser.END_TAG -> {
                    val n = p.name.lowercase()
                    if (n in record) { current?.let(onRecord); current = null }
                    tag = ""
                }
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** File kinds: "Stores" matches any name with "store" (Stores, StoresFull, Store…); price/promo kinds match by prefix. */
    private fun kindMatches(name: String, kind: String): Boolean {
        val n = name.lowercase(); val k = kind.lowercase()
        return if (k == "stores") "store" in n else n.startsWith(k)
    }

    /**
     * The chain's branches, from the best source available:
     * 1. laibcatalog's branch list (Victory, Mahsanei HaShuk, H. Cohen) — available day and night;
     * 2. the chain's Stores file;
     * 3. store numbers taken from the price-file names (chains that publish no Stores file, e.g. Yellow, Quik).
     */
    suspend fun stores(chain: Chain): List<StoreInfo> = withContext(Dispatchers.IO) {
        if (chain.engine == Engine.LAIB) runCatching { laibBranches(chain) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val file = runCatching { list(chain, "Stores", null).lastOrNull() }.getOrNull()
        if (file != null) {
            val out = LinkedHashMap<String, StoreInfo>()
            runCatching {
                open(download(chain, file)).use { input ->
                    parse(input, setOf("store", "branch", "sub_store")) { m ->
                        val id = (m["storeid"] ?: m["store_id"] ?: m["storeidentifier"] ?: m["branchid"])?.trimStart('0')?.ifEmpty { "0" } ?: return@parse
                        out[id] = StoreInfo(id, m["storename"] ?: m["store_name"] ?: m["name"] ?: m["branchname"] ?: id, m["city"].orEmpty(), m["address"].orEmpty())
                    }
                }
            }
            if (out.isNotEmpty()) return@withContext out.values.toList()
        }
        val fromNames = (list(chain, "PriceFull", null) + list(chain, "Price", null)).mapNotNull { storeIdFromName(it.first) }.distinct()
        if (fromNames.isNotEmpty()) return@withContext fromNames.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }.map { StoreInfo(it, "#$it", "", "") }
        throw PriceSourceException(if (chain.engine == Engine.LAIB && quietHours()) PriceSourceException.Why.OVERNIGHT else PriceSourceException.Why.NO_FILES)
    }

    /** PriceFull7290058140886-001-202611200500.gz → "1" */
    private fun storeIdFromName(n: String): String? =
        Regex("^[A-Za-z]+\\d{13}-(\\d{1,4})-", RegexOption.IGNORE_CASE).find(n)?.groupValues?.get(1)?.trimStart('0')?.ifEmpty { "0" }

    /** laibcatalog clears its file list from midnight until ~08:00 (and Mahsanei HaShuk on Saturday). */
    private fun quietHours(): Boolean {
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Jerusalem"))
        return now.hour < 8 || now.dayOfWeek == java.time.DayOfWeek.SATURDAY
    }

    private fun laibBranches(chain: Chain): List<StoreInfo> {
        val arr = json.parseToJsonElement(text("https://laibcatalog.co.il/webapi/api/getbranches?edi=${chain.param}")).jsonArray
        return arr.mapNotNull { e ->
            val o = e.jsonObject.mapKeys { it.key.lowercase() }
            fun pick(vararg keys: String) = keys.firstNotNullOfOrNull { k -> o.entries.firstOrNull { it.key.contains(k) }?.value?.jsonPrimitive?.contentOrNull }
            val id = pick("branchnumber", "storeid", "number", "id")?.trimStart('0')?.ifEmpty { "0" } ?: return@mapNotNull null
            StoreInfo(id, pick("branchname", "storename", "name") ?: "#$id", pick("city").orEmpty(), pick("address").orEmpty())
        }
    }

    /** Quick health check of a chain's source (listing only, nothing large is downloaded). */
    suspend fun check(chain: Chain): Int = withContext(Dispatchers.IO) {
        if (chain.engine == Engine.LAIB) runCatching { laibBranches(chain).size }.getOrNull()?.takeIf { it > 0 }?.let { return@withContext it }
        val n = list(chain, "PriceFull", null).size + list(chain, "Stores", null).size
        if (n == 0) throw PriceSourceException(if (chain.engine == Engine.LAIB && quietHours()) PriceSourceException.Why.OVERNIGHT else PriceSourceException.Why.NO_FILES)
        n
    }

    /** Full price list of one store (PriceFull, falling back to the latest Price file). */
    suspend fun prices(chain: Chain, storeId: String, onRow: (PriceRow) -> Unit) = withContext(Dispatchers.IO) {
        val file = list(chain, "PriceFull", storeId).lastOrNull() ?: list(chain, "Price", storeId).lastOrNull()
            ?: throw IOException("No price file for ${chain.en} store $storeId")
        open(download(chain, file)).use { input ->
            parse(input, setOf("item", "product")) { m ->
                val code = m["itemcode"] ?: return@parse
                val price = (m["itemprice"] ?: m["price"])?.toDoubleOrNull()?.takeIf { plausible(it) } ?: return@parse
                if (code.length > 30 || code.isBlank()) return@parse
                onRow(
                    PriceRow(
                        code = code.trimStart('0').ifEmpty { code }, name = m["itemname"] ?: m["itemnm"] ?: m["manufactureritemdescription"] ?: code,
                        price = price, unitPrice = m["unitofmeasureprice"]?.toDoubleOrNull() ?: 0.0,
                        unit = m["unitofmeasure"] ?: m["unitqty"].orEmpty(), manufacturer = m["manufacturername"].orEmpty(),
                        updated = m["priceupdatedate"].orEmpty(),
                    )
                )
            }
        }
    }

    data class Promo(val description: String, val price: Double, val minQty: Double, val until: String, val codes: List<String>)

    /** PRC-3: current promotions of one store (PromoFull file); the price is the promotion price for the minimum quantity. */
    suspend fun promos(chain: Chain, storeId: String, onPromo: (Promo) -> Unit) = withContext(Dispatchers.IO) {
        val file = list(chain, "PromoFull", storeId).lastOrNull() ?: list(chain, "Promo", storeId).lastOrNull() ?: return@withContext
        open(download(chain, file)).use { input ->
            val p = XmlPullParserFactory.newInstance().newPullParser()
            p.setInput(input, null)
            var inPromo = false; var tag = ""
            var desc = ""; var price = 0.0; var minQty = 1.0; var until = ""; val codes = mutableListOf<String>()
            while (true) {
                when (p.next()) {
                    XmlPullParser.START_TAG -> { val n = p.name.lowercase(); if (n == "promotion" || n == "sale") { inPromo = true; desc = ""; price = 0.0; minQty = 1.0; until = ""; codes.clear() } else tag = n }
                    XmlPullParser.TEXT -> if (inPromo && !p.isWhitespace) {
                        val t = p.text.trim()
                        when (tag) {
                            "promotiondescription" -> desc = t
                            "discountedprice" -> price = t.toDoubleOrNull() ?: price
                            "minqty" -> minQty = t.toDoubleOrNull() ?: minQty
                            "promotionenddate" -> until = t.take(10)
                            "itemcode" -> codes += t.trimStart('0').ifEmpty { t }
                        }
                    }
                    XmlPullParser.END_TAG -> { val n = p.name.lowercase(); if ((n == "promotion" || n == "sale") && inPromo) { inPromo = false; if (codes.isNotEmpty() && desc.isNotBlank()) onPromo(Promo(desc, price, minQty, until, codes.toList())) }; tag = "" }
                    XmlPullParser.END_DOCUMENT -> return@withContext
                }
            }
        }
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Android) SmartFamily"
        private const val MAX_FILE = 120L * 1024 * 1024
        /** 2.2 sanity limits for a price row (files over plain HTTP could be altered on the way). */
        fun plausible(price: Double) = price > 0.0 && price < 100_000.0
    }
}
