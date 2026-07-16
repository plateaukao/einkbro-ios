package info.plateaukao.einkbro.data.remote

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.util.encodeBase64

/** Outcome of an Instapaper request (parity Phase J). */
sealed class InstapaperResult {
    data class Success(val message: String) : InstapaperResult()
    data class Error(val message: String) : InstapaperResult()
}

/**
 * Instapaper "Simple API" client — POST https://www.instapaper.com/api/add with
 * HTTP Basic auth (a faithful port of the Android OkHttp InstapaperRepository).
 */
class InstapaperRepository {

    private val client = HttpClientProvider.client

    suspend fun addUrl(
        url: String,
        username: String,
        password: String,
        title: String? = null,
    ): InstapaperResult {
        if (url.isBlank()) return InstapaperResult.Error("URL is empty")
        if (username.isBlank() || password.isBlank()) {
            return InstapaperResult.Error("Instapaper credentials not set")
        }
        return try {
            val response = client.submitForm(
                url = ADD_URL_ENDPOINT,
                formParameters = Parameters.build {
                    append("url", url)
                    if (!title.isNullOrBlank()) append("title", title)
                },
            ) {
                header(HttpHeaders.Authorization, basicAuth(username, password))
                header(HttpHeaders.UserAgent, "EinkBro/1.0")
            }
            when (response.status.value) {
                200, 201 -> InstapaperResult.Success("Added to Instapaper")
                400 -> InstapaperResult.Error("Invalid URL or parameters")
                403 -> InstapaperResult.Error("Invalid username or password")
                500 -> InstapaperResult.Error("Service temporarily unavailable")
                else -> InstapaperResult.Error("Failed (${response.status.value})")
            }
        } catch (e: Exception) {
            InstapaperResult.Error("Network error: ${e.message}")
        }
    }

    private fun basicAuth(username: String, password: String): String =
        "Basic " + "$username:$password".encodeToByteArray().encodeBase64()

    private companion object {
        const val ADD_URL_ENDPOINT = "https://www.instapaper.com/api/add"
    }
}
