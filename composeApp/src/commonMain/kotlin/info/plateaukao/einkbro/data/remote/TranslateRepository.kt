package info.plateaukao.einkbro.data.remote

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.util.Crypto
import info.plateaukao.einkbro.util.System
import info.plateaukao.einkbro.util.TranslationLanguage
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Ktor port of Android's TranslateRepository: Google (unofficial gtx), DeepL
 * (free jsonrpc), and Papago text translation (scraped auth key + HMAC-MD5
 * signature). Papago image OCR translation needs bitmap plumbing — Phase 7+.
 */
class TranslateRepository(
    private val config: ConfigManager = AppServices.config,
) {
    private val client = HttpClientProvider.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun gTranslateWithApi(
        text: String,
        targetLanguage: String = "en",
        sourceLanguage: String = "auto",
    ): String? = try {
        val response = client.get("https://translate.googleapis.com/translate_a/single") {
            parameter("client", "gtx")
            parameter("tl", targetLanguage)
            parameter("sl", sourceLanguage)
            parameter("dt", "t")
            parameter("q", text)
            header("User-Agent", "Mozilla/5.0")
            header("Referer", "https://translate.google.com/")
        }
        if (response.status.value != 200) null
        else {
            val root = json.parseToJsonElement(response.bodyAsText()).jsonArray
            (root[0] as JsonArray).joinToString("") { item ->
                item.jsonArray[0].jsonPrimitive.content
            }
        }
    } catch (e: Exception) {
        null
    }

    suspend fun deepLTranslate(
        text: String,
        targetLanguage: TranslationLanguage,
    ): String? {
        val target = when (targetLanguage) {
            TranslationLanguage.ZH_TW, TranslationLanguage.ZH_CN -> "zh"
            else -> config.translation.translationLanguage.value
        }
        val id = (Random.nextInt(99999) + 200000) * 1000
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "LMT_handle_texts")
            put("id", id)
            put("params", buildJsonObject {
                put("splitting", "newlines")
                put("lang", buildJsonObject {
                    put("source_lang_user_selected", "AUTO")
                    put("target_lang", target.uppercase())
                })
                put("texts", buildJsonArray {
                    add(buildJsonObject {
                        put("text", text)
                        put("requestAlternatives", 1)
                    })
                })
                put("timestamp", deepLTimestamp(text))
            })
        }
        // DeepL fingerprints the method-key spacing by request id (from the web client).
        val body = payload.toString().let {
            if ((id + 5) % 29 == 0 || (id + 3) % 13 == 0) it.replace("\"method\":\"", "\"method\" : \"")
            else it.replace("\"method\":\"", "\"method\": \"")
        }
        return try {
            val response = client.post("https://www2.deepl.com/jsonrpc") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.value != 200) null
            else {
                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                root["result"]!!.jsonObject["texts"]!!.jsonArray[0]
                    .jsonObject["text"]!!.jsonPrimitive.content
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun deepLTimestamp(text: String): Long {
        val iCount = text.split("i").size - 1
        val ts = System.currentTimeMillis()
        return if (iCount != 0) {
            val adjusted = iCount + 1
            ts - (ts % adjusted) + adjusted
        } else ts
    }

    // ── Papago ────────────────────────────────────────────────────────────

    private var authKey: String? = null

    private suspend fun getAuthKey(): String? {
        val html = client.get("https://papago.naver.com").bodyAsText()
        val path = Regex("/vendors~main[^\"']*chunk\\.js").find(html)?.value ?: return null
        val js = client.get("https://papago.naver.com$path").bodyAsText()
        return Regex("AUTH_KEY:\\s*\"([\\w.]+)\"").find(js)?.groupValues?.get(1)
    }

    @OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)
    suspend fun pTranslate(
        text: String,
        targetLanguage: String = "en",
        sourceLanguage: String = "auto",
    ): String? {
        if (authKey == null) {
            authKey = try {
                getAuthKey()
            } catch (e: Exception) {
                null
            } ?: return ""
        }
        val key = authKey!!.encodeToByteArray()
        val guid = Uuid.random().toString()
        val timestamp = System.currentTimeMillis()
        val code = "$guid\n$PAPAGO_API_URL\n$timestamp".encodeToByteArray()
        val token = Base64.encode(Crypto.hmacMd5(key, code))

        return try {
            val response = client.post(PAPAGO_API_URL) {
                header("device-type", "pc")
                header("x-apigw-partnerid", "papago")
                header("Origin", "https://papago.naver.com")
                header("Sec-Fetch-Site", "same-origin")
                header("Sec-Fetch-Mode", "cors")
                header("Sec-Fetch-Dest", "empty")
                header("Authorization", "PPG $guid:$token")
                header("Timestamp", timestamp.toString())
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("source", sourceLanguage)
                            append("target", targetLanguage)
                            append("text", text)
                        }
                    )
                )
            }
            if (response.status.value != 200) null
            else json.parseToJsonElement(response.bodyAsText())
                .jsonObject["translatedText"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val PAPAGO_API_URL = "https://papago.naver.com/apis/n2mt/translate"
    }
}
