package com.rimon.smartfamily.smart

import android.app.DownloadManager
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import kotlin.math.max

/**
 * 2.2 offline transcription of voice messages — the same whisper.cpp engine and models as "הרימון המתמלל".
 * The recording never leaves the phone. Models are downloaded only when chosen (free, from Hugging Face).
 */
data class WhisperModel(val id: String, val he: String, val en: String, val heDesc: String, val enDesc: String, val url: String, val sizeBytes: Long, val language: String) {
    val sizeText: String get() = if (sizeBytes >= 1_000_000_000L) String.format(java.util.Locale.US, "%.1fGB", sizeBytes / 1e9) else "${sizeBytes / 1_000_000}MB"
}

object WhisperModels {
    val all = listOf(
        WhisperModel("ivrit-turbo", "ivrit.ai Turbo · מומלץ לעברית", "ivrit.ai Turbo · best for Hebrew",
            "אומן במיוחד על עברית. הכי מדויק. לטלפון עם 8GB זיכרון ומעלה.", "Trained on Hebrew. Most accurate. Needs a phone with 8 GB RAM or more.",
            "https://huggingface.co/ivrit-ai/whisper-large-v3-turbo-ggml/resolve/main/ggml-model.bin", 1_620_000_000L, "he"),
        WhisperModel("turbo-q5", "Whisper Turbo דחוס", "Whisper Turbo (compressed)",
            "רב-לשוני, קטן פי 3. בעברית פחות מדויק מ-ivrit.ai.", "Multilingual, 3x smaller. Less accurate in Hebrew than ivrit.ai.",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin", 574_000_000L, "auto"),
        WhisperModel("small-q5", "Whisper Small · קל ומהיר", "Whisper Small · light and fast",
            "קטן ומהיר מאוד, לטלפונים חלשים. איכות עברית בסיסית.", "Very small and fast, for weaker phones. Basic Hebrew quality.",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin", 190_000_000L, "auto"),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }

    private fun prefs(c: Context) = c.getSharedPreferences("sf_transcribe", Context.MODE_PRIVATE)
    /** "auto" (offline when a model is ready, else cloud), "offline", "cloud", or "off". */
    fun mode(c: Context): String = prefs(c).getString("mode", "auto")!!
    fun setMode(c: Context, m: String) = prefs(c).edit().putString("mode", m).apply()
    fun wifiOnly(c: Context): Boolean = prefs(c).getBoolean("wifi_only", true)
    fun setWifiOnly(c: Context, on: Boolean) = prefs(c).edit().putBoolean("wifi_only", on).apply()
    fun selectedId(c: Context): String = prefs(c).getString("model", "").orEmpty()
    fun select(c: Context, id: String) = prefs(c).edit().putString("model", id).apply()

    private fun dir(c: Context) = (c.getExternalFilesDir("models") ?: File(c.filesDir, "models")).apply { mkdirs() }
    fun file(c: Context, m: WhisperModel) = File(dir(c), "${m.id}.bin")
    private fun part(m: WhisperModel) = "${m.id}.bin.part"
    fun isReady(c: Context, m: WhisperModel) = file(c, m).let { it.exists() && it.length() > m.sizeBytes * 0.9 }
    fun selected(c: Context): WhisperModel? = all.firstOrNull { it.id == selectedId(c) && isReady(c, it) } ?: all.firstOrNull { isReady(c, it) }
    fun freeBytes(c: Context) = dir(c).usableSpace

    /** Starts a download with the phone's download manager; returns null or the reason it cannot. */
    fun download(c: Context, m: WhisperModel): String? {
        if (isReady(c, m)) return null
        if (freeBytes(c) < m.sizeBytes + 200_000_000L) return "space"
        val dm = c.getSystemService(DownloadManager::class.java) ?: return "dm"
        cancel(c, m)
        val req = DownloadManager.Request(Uri.parse(m.url)).setTitle("SmartFamily · ${m.en}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(c, "models", part(m))
            .setAllowedOverMetered(!wifiOnly(c)).setAllowedOverRoaming(false)
        prefs(c).edit().putLong("dl_${m.id}", dm.enqueue(req)).apply()
        return null
    }

    fun cancel(c: Context, m: WhisperModel) {
        val id = prefs(c).getLong("dl_${m.id}", -1L)
        if (id >= 0) c.getSystemService(DownloadManager::class.java)?.remove(id)
        prefs(c).edit().remove("dl_${m.id}").apply()
        File(dir(c), part(m)).delete()
    }

    fun delete(c: Context, m: WhisperModel) { cancel(c, m); LocalWhisper.release(); file(c, m).delete() }

    /** Download progress 0..100, -1 failed, null when not downloading. Moves a finished file into place. */
    fun progress(c: Context, m: WhisperModel): Int? {
        val id = prefs(c).getLong("dl_${m.id}", -1L)
        if (id < 0) return null
        val dm = c.getSystemService(DownloadManager::class.java) ?: return null
        dm.query(DownloadManager.Query().setFilterById(id))?.use { cur ->
            if (!cur.moveToFirst()) { prefs(c).edit().remove("dl_${m.id}").apply(); return null }
            val status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: m.sizeBytes
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val p = File(dir(c), part(m))
                    prefs(c).edit().remove("dl_${m.id}").apply()
                    if (p.exists() && p.length() > m.sizeBytes * 0.9) { file(c, m).delete(); p.renameTo(file(c, m)); if (selectedId(c).isBlank()) select(c, m.id); null }
                    else { p.delete(); -1 }
                }
                DownloadManager.STATUS_FAILED -> { dm.remove(id); prefs(c).edit().remove("dl_${m.id}").apply(); -1 }
                else -> ((done * 100) / total).toInt().coerceIn(0, 99)
            }
        }
        return null
    }
}

object LocalWhisper {
    private var loaded = false
    private var ctx = 0L
    private var path: String? = null

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeFree(ctx: Long)
    private external fun nativeAbort(abort: Boolean)
    private external fun nativeSystemInfo(): String
    private external fun nativeTranscribe(ctx: Long, samples: FloatArray, threads: Int, language: String, prompt: String, listener: Any?): ByteArray?

    @Synchronized private fun lib(): Boolean {
        if (loaded) return true
        return runCatching { System.loadLibrary("sf_whisper"); loaded = true; true }.getOrDefault(false)
    }

    /** Null when this phone can transcribe offline; otherwise a short reason key ("abi", "cpu", "lib"). */
    fun unsupported(): String? {
        val abis = android.os.Build.SUPPORTED_ABIS
        if (!abis.contains("arm64-v8a") && !abis.contains("x86_64")) return "abi"
        if (abis.firstOrNull() == "arm64-v8a") {
            val f = runCatching { File("/proc/cpuinfo").readText().lineSequence().filter { it.startsWith("Features") }.joinToString(" ") }.getOrDefault("")
            if (f.isNotBlank() && !(f.contains("asimddp") && (f.contains("fphp") || f.contains("asimdhp")))) return "cpu"
        }
        if (!lib()) return "lib"
        return null
    }

    @Synchronized fun release() { if (ctx != 0L) nativeFree(ctx); ctx = 0L; path = null }

    /** Transcribes an audio file (any format Android can decode) to text, on this phone. */
    @Synchronized fun transcribe(c: Context, model: WhisperModel, audio: File): String {
        unsupported()?.let { throw IOException("offline:$it") }
        val p = WhisperModels.file(c, model).absolutePath
        if (ctx == 0L || path != p) { release(); ctx = nativeInit(p); if (ctx == 0L) throw IOException("offline:model"); path = p }
        val samples = decode16k(audio) ?: throw IOException("offline:audio")
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = when { cores >= 8 -> 6; cores >= 6 -> 4; else -> max(2, cores - 1) }
        val out = nativeTranscribe(ctx, samples, threads, model.language, "", null) ?: throw IOException("offline:failed")
        return String(out, Charsets.UTF_8).lineSequence().drop(1).mapNotNull { it.split('\t', limit = 3).getOrNull(2)?.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** First audio track → 16 kHz mono floats in [-1, 1] (voice messages are short, so in memory). */
    private fun decode16k(input: File): FloatArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        val out = ArrayList<Float>(16_000 * 60)
        try {
            ex.setDataSource(input.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val cdc = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!); codec = cdc
            cdc.configure(fmt, null, null, 0); cdc.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT); var isFloat = false
            val info = MediaCodec.BufferInfo(); var inputDone = false
            var acc = 0.0; var n = 0; var inIdx = 0L; var written = 0L
            while (true) {
                if (!inputDone) {
                    val i = cdc.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val read = ex.readSampleData(cdc.getInputBuffer(i)!!, 0)
                        if (read < 0) { cdc.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { cdc.queueInputBuffer(i, 0, read, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = cdc.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = cdc.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val buf = cdc.getOutputBuffer(o)!!.apply { position(info.offset); limit(info.offset + info.size) }.slice().order(ByteOrder.LITTLE_ENDIAN)
                    val ch = max(1, channels); val ratio = rate / 16_000.0
                    fun push(v: Double) { acc += v; n++; inIdx++; val target = (inIdx / ratio).toLong(); if (target > written) { val avg = (acc / n).toFloat(); while (written < target) { out += avg; written++ }; acc = 0.0; n = 0 } }
                    if (isFloat) { val fb = buf.asFloatBuffer(); while (fb.remaining() >= ch) { var s = 0.0; repeat(ch) { s += fb.get() }; push(s / ch) } }
                    else { val sb = buf.asShortBuffer(); while (sb.remaining() >= ch) { var s = 0.0; repeat(ch) { s += sb.get() / 32768.0 }; push(s / ch) } }
                    cdc.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            return out.toFloatArray()
        } catch (e: Exception) { return null } finally { runCatching { codec?.stop() }; codec?.release(); ex.release() }
    }
}
