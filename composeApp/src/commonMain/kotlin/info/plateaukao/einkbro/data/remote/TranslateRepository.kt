package info.plateaukao.einkbro.data.remote

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ktor port of Android's TranslateRepository, trimmed to Google's unofficial
 * gtx endpoint. The Android original also carries DeepL (free jsonrpc), Papago
 * text, and Papago image OCR; all three are dropped on iOS — they depend on
 * scraped auth keys and signed requests against endpoints that are not public
 * APIs. Paragraph translation now goes through Google or an LLM provider.
 */
class TranslateRepository {
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
}
