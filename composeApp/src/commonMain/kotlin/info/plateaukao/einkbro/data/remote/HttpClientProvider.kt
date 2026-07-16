package info.plateaukao.einkbro.data.remote

import io.ktor.client.HttpClient

/** Platform HTTP client (Darwin engine on iOS). One shared instance. */
expect fun createHttpClient(): HttpClient

object HttpClientProvider {
    val client: HttpClient by lazy { createHttpClient() }
}
