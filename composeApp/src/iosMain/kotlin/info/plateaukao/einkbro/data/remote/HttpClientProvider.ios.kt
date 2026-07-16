package info.plateaukao.einkbro.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        requestTimeoutMillis = 120_000
    }
    // Repositories inspect status codes themselves (matching the OkHttp port).
    expectSuccess = false
}
