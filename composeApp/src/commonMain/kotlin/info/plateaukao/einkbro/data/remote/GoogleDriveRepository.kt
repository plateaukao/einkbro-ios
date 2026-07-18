package info.plateaukao.einkbro.data.remote

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.util.Crypto
import info.plateaukao.einkbro.util.System
import info.plateaukao.einkbro.util.WebAuth
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.http.parseQueryString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Google Drive backup sync, ported from the Android GoogleDriveRepository:
 * OAuth 2.0 Authorization Code + PKCE with direct Drive REST calls over Ktor.
 * The backup zip lives in the user's Drive appDataFolder — a hidden app-private
 * area of their own Drive, so there is no developer-hosted backend. The
 * interactive part diverges from Android (which runs consent as a browser tab):
 * iOS uses ASWebAuthenticationSession via [WebAuth], so sign-in is a single
 * in-process [signIn] call instead of Android's persisted begin/complete pair.
 */
@Serializable
data class DriveAuthState(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0, // epoch seconds
    val email: String = "",
)

@Serializable
data class DriveFileMeta(val id: String, val name: String, val modifiedTime: String? = null)

/** The stored refresh token was revoked or expired; the user must sign in again. */
class DriveReauthRequiredException : Exception()

@OptIn(ExperimentalEncodingApi::class)
object GoogleDriveRepository {
    private val client get() = HttpClientProvider.client
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs get() = AppServices.sharedPreferences

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val base64UrlLenient = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    private var authState: DriveAuthState?
        get() = prefs.getString(K_AUTH_STATE, null)?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { json.decodeFromString<DriveAuthState>(it) }.getOrNull() }
        set(value) {
            prefs.edit().putString(
                K_AUTH_STATE,
                value?.let { json.encodeToString(DriveAuthState.serializer(), it) } ?: "",
            ).apply()
        }

    val email: String? get() = authState?.email?.takeIf { it.isNotEmpty() }

    fun signOut() {
        authState = null
    }

    // MARK: - OAuth (Authorization Code + PKCE via ASWebAuthenticationSession)

    /** Interactive sign-in; true when tokens were obtained and stored. */
    suspend fun signIn(): Boolean {
        val verifier = base64Url.encode(Crypto.randomBytes(64))
        val state = base64Url.encode(Crypto.randomBytes(64))
        val callback = WebAuth.authenticate(authorizationUrl(verifier, state), REDIRECT_SCHEME)
            ?: return false
        val params = parseQueryString(callback.substringAfter('?', ""))
        val code = params["code"] ?: return false
        if (params["state"] != state) return false
        return exchangeCode(code, verifier)
    }

    private fun authorizationUrl(codeVerifier: String, state: String): String {
        val challenge = base64Url.encode(Crypto.sha256(codeVerifier.encodeToByteArray()))
        return buildString {
            append(AUTH_ENDPOINT)
            append("?client_id=").append(CLIENT_ID.encodeURLParameter())
            append("&redirect_uri=").append(REDIRECT_URI.encodeURLParameter())
            append("&response_type=code")
            append("&scope=").append("$SCOPE openid email".encodeURLParameter())
            append("&code_challenge=").append(challenge)
            append("&code_challenge_method=S256")
            append("&state=").append(state)
            // offline + consent so Google returns a refresh token we can renew
            // silently; select_account so the user can pick a different account
            // even though the auth session shares Safari's Google cookies.
            append("&access_type=offline")
            append("&prompt=").append("consent select_account".encodeURLParameter())
        }
    }

    /** Exchange the redirect's auth code for tokens and persist them. */
    private suspend fun exchangeCode(code: String, codeVerifier: String): Boolean {
        val response = client.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("code_verifier", codeVerifier)
                append("client_id", CLIENT_ID)
                append("redirect_uri", REDIRECT_URI)
            },
        )
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) return false
        val token = runCatching { json.decodeFromString<TokenResponse>(body) }.getOrNull()
            ?: return false
        if (token.accessToken.isEmpty() || token.refreshToken.isEmpty()) return false
        authState = DriveAuthState(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresAt = nowSeconds() + token.expiresIn,
            email = token.idToken?.let { emailFromIdToken(it) }.orEmpty(),
        )
        return true
    }

    /** A valid access token, refreshed through the stored refresh token when
     *  expired. Throws [DriveReauthRequiredException] (after clearing the dead
     *  session) when the refresh token itself is no longer valid. */
    private suspend fun validAccessToken(): String {
        val state = authState ?: throw DriveReauthRequiredException()
        if (state.expiresAt - nowSeconds() > TOKEN_EXPIRY_MARGIN_SECONDS) {
            return state.accessToken
        }
        return refreshAccessToken()
    }

    private suspend fun refreshAccessToken(): String {
        val state = authState ?: throw DriveReauthRequiredException()
        val response = client.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", state.refreshToken)
                append("client_id", CLIENT_ID)
            },
        )
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // invalid_grant means the refresh token was revoked or expired —
            // permanent, so drop the session; other failures are transient.
            if (body.contains("invalid_grant")) {
                signOut()
                throw DriveReauthRequiredException()
            }
            error("Google token refresh failed: HTTP ${response.status.value}")
        }
        val token = json.decodeFromString<TokenResponse>(body)
        authState = state.copy(
            accessToken = token.accessToken,
            expiresAt = nowSeconds() + token.expiresIn,
        )
        return token.accessToken
    }

    // MARK: - Drive REST (appDataFolder)

    private class DriveHttpException(val code: Int) : Exception("Drive request failed: HTTP $code")

    /** Run [block] with a valid token; on 401 (token revoked server-side while
     *  still locally unexpired) force one refresh and retry. */
    private suspend fun <T> withAccessToken(block: suspend (String) -> T): T =
        try {
            block(validAccessToken())
        } catch (e: DriveHttpException) {
            if (e.code != 401) throw e
            block(refreshAccessToken())
        }

    /** The backup zips in the appDataFolder: Android's [BACKUP_FILE_NAME] and/or
     *  this app's [IOS_BACKUP_FILE_NAME]. Both restore; uploads only ever touch
     *  the iOS file so the Android device's backup is never clobbered. */
    suspend fun getRemoteBackups(): List<DriveFileMeta> = withAccessToken { token ->
        val response = client.get(
            "$DRIVE_FILES_ENDPOINT?spaces=appDataFolder&fields=files(id,name,modifiedTime)&pageSize=100"
        ) { header("Authorization", "Bearer $token") }
        if (!response.status.isSuccess()) throw DriveHttpException(response.status.value)
        json.decodeFromString<FileList>(response.bodyAsText())
            .files.filter { it.name == BACKUP_FILE_NAME || it.name == IOS_BACKUP_FILE_NAME }
    }

    suspend fun downloadBackup(fileId: String): ByteArray = withAccessToken { token ->
        val response = client.get("$DRIVE_FILES_ENDPOINT/$fileId?alt=media") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw DriveHttpException(response.status.value)
        response.body<ByteArray>()
    }

    /** Create the backup file (when [existingId] is null) or replace its content. */
    suspend fun uploadBackup(bytes: ByteArray, existingId: String?): Unit = withAccessToken { token ->
        val response = if (existingId == null) {
            // Drive's two-part create is multipart/related, which Ktor's form-data
            // helpers don't produce — the body is small enough to hand-roll.
            val boundary = "einkbro-" + base64Url.encode(Crypto.randomBytes(12))
            val metadata = """{"name":"$IOS_BACKUP_FILE_NAME","parents":["appDataFolder"]}"""
            val head = ("--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                "$metadata\r\n" +
                "--$boundary\r\n" +
                "Content-Type: application/zip\r\n\r\n").encodeToByteArray()
            val tail = "\r\n--$boundary--\r\n".encodeToByteArray()
            client.post("$DRIVE_UPLOAD_ENDPOINT?uploadType=multipart") {
                header("Authorization", "Bearer $token")
                contentType(ContentType("multipart", "related").withParameter("boundary", boundary))
                setBody(head + bytes + tail)
            }
        } else {
            client.patch("$DRIVE_UPLOAD_ENDPOINT/$existingId?uploadType=media") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Zip)
                setBody(bytes)
            }
        }
        if (!response.status.isSuccess()) throw DriveHttpException(response.status.value)
    }

    /** Pull the `email` claim out of the id_token JWT for the settings UI. */
    private fun emailFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split(".")[1]
        val bytes = base64UrlLenient.decode(payload)
        Json.parseToJsonElement(bytes.decodeToString()).jsonObject["email"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String = "",
        @SerialName("refresh_token") val refreshToken: String = "",
        @SerialName("expires_in") val expiresIn: Long = 0,
        @SerialName("id_token") val idToken: String? = null,
    )

    @Serializable
    private data class FileList(val files: List<DriveFileMeta> = emptyList())

    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    /** Written by the Android app (BackupUnit v2 zip); restored here read-only. */
    const val BACKUP_FILE_NAME = "einkbro-backup.zip"
    /** Written by this app (iOS-format zip); the only file uploads replace. */
    const val IOS_BACKUP_FILE_NAME = "einkbro-backup-ios.zip"

    // iOS-type OAuth client in the same GCP project as the Android client, so
    // both platforms share the Drive appDataFolder. iOS clients have no secret;
    // the redirect is the reversed client id, intercepted by the auth session.
    private const val CLIENT_ID =
        "540228800389-j68oo2pvfppt51ht89dd3sn4cm2j1opj.apps.googleusercontent.com"
    private const val REDIRECT_SCHEME =
        "com.googleusercontent.apps.540228800389-j68oo2pvfppt51ht89dd3sn4cm2j1opj"
    private const val REDIRECT_URI = "$REDIRECT_SCHEME:/oauth2redirect"

    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"

    private const val TOKEN_EXPIRY_MARGIN_SECONDS = 60L

    // Deliberately NOT "sp_"-prefixed: exportPrefs("sp_") sweeps sp_ keys into
    // the backup zip that gets uploaded to Drive, and OAuth tokens must stay
    // out of that zip (and out of cross-device restores).
    private const val K_AUTH_STATE = "gdrive_auth_state"
}
