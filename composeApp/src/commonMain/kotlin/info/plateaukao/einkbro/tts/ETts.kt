package info.plateaukao.einkbro.tts

import info.plateaukao.einkbro.data.remote.HttpClientProvider
import info.plateaukao.einkbro.tts.entity.VoiceItem
import info.plateaukao.einkbro.util.Crypto
import info.plateaukao.einkbro.util.System
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

/**
 * Edge (Microsoft) neural TTS (parity Phase L), ported from Android's ETts /
 * the Edge-TTS-Lib. Streams mp3 audio over a WebSocket to the free Bing
 * read-aloud endpoint: the request is signed with a `Sec-MS-GEC` SHA-256 token,
 * an audio-format config + an SSML payload are sent as text frames, and the
 * server replies with binary frames whose leading header block is stripped and
 * the mp3 tail concatenated. No API key needed.
 */
class ETts {

    suspend fun tts(voice: VoiceItem, speed: Int, content: String): ByteArray? {
        val processed = removeIncompatibleCharacters(content)
        if (processed.isBlank()) return null

        val dateStr = dateToString()
        val reqId = uuid()
        val audioFormat = mkAudioFormat(dateStr, FORMAT)
        val ssml = mkssml(
            voice.locale,
            voice.name,
            processed,
            "+0Hz",
            if (speed < 100) "${speed - 100}%" else "+${speed - 100}%",
            "+0%",
        )
        val ssmlHeaders = ssmlHeadersPlusData(reqId, dateStr, ssml)
        val secMsGec = generateSecMsGec()
        val urlSuffix =
            "&Sec-MS-GEC=$secMsGec&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION&ConnectionId=$reqId"

        val audio = ArrayList<Byte>()
        return try {
            HttpClientProvider.client.webSocket(
                urlString = TTS_URL + urlSuffix,
                request = {
                    headers.append(
                        "Origin",
                        "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold",
                    )
                    headers.append("Pragma", "no-cache")
                    headers.append("Cache-Control", "no-cache")
                    headers.append("Accept-Language", "en-US,en;q=0.9")
                    // Bing's read-aloud WAF accepts the WebSocket but never
                    // synthesizes (so `incoming` hangs) unless a browser
                    // User-Agent is sent — matches Android's ETts headers.
                    headers.append("User-Agent", EDGE_USER_AGENT)
                },
            ) {
                send(Frame.Text(audioFormat))
                send(Frame.Text(ssmlHeaders))
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text ->
                            if (frame.readText().contains("Path:turn.end")) break

                        is Frame.Binary ->
                            extractAudio(frame.readBytes())?.let { audio.addAll(it.asList()) }

                        else -> {}
                    }
                }
                close()
            }
            if (audio.isEmpty()) null else audio.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Edge binary frame = 2-byte big-endian header length, the header text, then
     * the mp3 payload. (Android skipped a fixed 130/142/105 bytes by sniffing the
     * content-type; the length prefix is the robust equivalent.)
     */
    private fun extractAudio(frame: ByteArray): ByteArray? {
        if (frame.size < 2) return null
        val headerLen = ((frame[0].toInt() and 0xff) shl 8) or (frame[1].toInt() and 0xff)
        val start = 2 + headerLen
        if (start >= frame.size) return null
        return frame.copyOfRange(start, frame.size)
    }

    private fun generateSecMsGec(): String {
        val ticks = System.currentTimeMillis() / 1000 + 11644473600L
        val rounded = ticks - (ticks % 300)
        val windowsTicks = rounded * 10000000L
        val data = "$windowsTicks$TRUSTED_CLIENT_TOKEN"
        return Crypto.sha256Hex(data.encodeToByteArray())
    }

    private fun dateToString(): String {
        val dt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        // DayOfWeek.ordinal: MON=0..SUN=6; DAYS is indexed SUN=0.
        val dow = DAYS[(dt.dayOfWeek.ordinal + 1) % 7]
        val mon = MONTHS[dt.monthNumber - 1]
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "$dow $mon ${p(dt.dayOfMonth)} ${dt.year} " +
            "${p(dt.hour)}:${p(dt.minute)}:${p(dt.second)} " +
            "GMT+0000 (Coordinated Universal Time)"
    }

    private fun uuid(): String =
        (0 until 16).joinToString("") { Random.nextInt(0, 256).toString(16).padStart(2, '0') }

    private fun removeIncompatibleCharacters(input: String): String {
        if (input.isBlank()) return ""
        val output = StringBuilder()
        for (element in input) {
            val code = element.code
            if (code in 0..8 || code in 11..12 || code in 14..31) output.append(" ")
            else output.append(element)
        }
        return output.toString()
    }

    private fun mkAudioFormat(dateStr: String, format: String): String =
        "X-Timestamp:" + dateStr + "\r\n" +
            "Content-Type:application/json; charset=utf-8\r\n" +
            "Path:speech.config\r\n\r\n" +
            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
            "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"}," +
            "\"outputFormat\":\"" + format + "\"}}}}\n"

    private fun mkssml(
        locale: String,
        voiceName: String,
        content: String,
        voicePitch: String,
        voiceRate: String,
        voiceVolume: String,
    ): String =
        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='" +
            locale + "'>" +
            "<voice name='" + voiceName + "'><prosody pitch='" + voicePitch +
            "' rate='" + voiceRate + "' volume='" + voiceVolume + "'>" +
            content + "</prosody></voice></speak>"

    private fun ssmlHeadersPlusData(requestId: String, timestamp: String, ssml: String): String =
        "X-RequestId:" + requestId + "\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            "X-Timestamp:" + timestamp + "Z\r\n" +
            "Path:ssml\r\n\r\n" + ssml

    companion object {
        private const val FORMAT = "audio-24khz-48kbitrate-mono-mp3"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
        private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"
        private const val EDGE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
        private const val TTS_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"

        private val DAYS = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        private val MONTHS = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
    }
}
