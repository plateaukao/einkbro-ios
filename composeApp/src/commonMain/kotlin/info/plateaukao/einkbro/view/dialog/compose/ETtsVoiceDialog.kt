package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.tts.entity.VoiceItem
import info.plateaukao.einkbro.tts.entity.VoiceTag
import info.plateaukao.einkbro.tts.entity.defaultVoiceItem
import info.plateaukao.einkbro.util.Locale
import info.plateaukao.einkbro.view.compose.ThemedDivider

/**
 * Entry composable; was ETtsVoiceDialogFragment.Content().
 * The Android original parses assets/eVoiceList.json (hundreds of Edge-TTS
 * voices); the catalog ships a representative sample list instead.
 */
@Composable
fun ETtsVoiceDialogContent(
    selectedAction: (VoiceItem) -> Unit = {},
) {
    val config = AppServices.config
    val selectedVoice = remember { mutableStateOf(config.tts.ettsVoice) }
    LanguageListScreen(
        selectedVoiceItem = selectedVoice.value,
        voices = sampleVoiceItems,
    ) {
        config.tts.ettsVoice = it
        selectedVoice.value = it
        selectedAction(it)
    }
}

private fun sampleVoice(shortName: String, gender: String, personality: String): VoiceItem {
    val locale = shortName.split("-").take(2).joinToString("-")
    val plainName = shortName.substringAfterLast("-")
    return VoiceItem(
        friendlyName = "Microsoft $plainName Online (Natural) - $locale",
        gender = gender,
        locale = locale,
        name = "Microsoft Server Speech Text to Speech Voice ($locale, $plainName)",
        shortName = shortName,
        status = "GA",
        suggestedCodec = "audio-24khz-48kbitrate-mono-mp3",
        voiceTag = VoiceTag(
            contentCategories = listOf("General"),
            voicePersonalities = listOf(personality),
        ),
    )
}

val sampleVoiceItems: List<VoiceItem> = listOf(
    defaultVoiceItem,
    sampleVoice("en-US-GuyNeural", "Male", "Passion"),
    sampleVoice("en-GB-SoniaNeural", "Female", "Warm"),
    sampleVoice("zh-TW-HsiaoChenNeural", "Female", "Warm"),
    sampleVoice("zh-CN-XiaoxiaoNeural", "Female", "Lively"),
    sampleVoice("ja-JP-NanamiNeural", "Female", "Bright"),
    sampleVoice("ko-KR-SunHiNeural", "Female", "Bright"),
    sampleVoice("fr-FR-DeniseNeural", "Female", "Cheerful"),
)

@Composable
fun LanguageListScreen(
    selectedVoiceItem: VoiceItem,
    voices: List<VoiceItem>,
    selectedAction: (VoiceItem) -> Unit = {},
) {
    // create language list based on voices' first segment by - separator
    val languageList = voices.map { it.getLanguageCode() }.distinct().toMutableList()
        .apply {
            remove("en")
            remove("zh")
            remove("ja")
            remove("ko")
            remove("fr")
            add(0, "fr")
            add(0, "ko")
            add(0, "ja")
            add(0, "zh")
            add(0, "en")
        }

    LazyColumn(
        modifier = Modifier.width(400.dp)
    ) {
        languageList.forEach { language ->
            item {
                val isExpanded = remember { mutableStateOf(false) }
                Text(
                    text = Locale(language).displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable {
                            isExpanded.value = !isExpanded.value
                        },
                    color = MaterialTheme.colors.onBackground
                )
                ThemedDivider()
                if (isExpanded.value) {
                    voices.filter { it.getLanguageCode() == language }
                        .forEach { voice ->
                            VoiceItemRow(
                                voice = voice,
                                selected = voice == selectedVoiceItem,
                                onClick = {
                                    selectedAction(voice)
                                }
                            )
                        }
                }
            }
        }
    }
}

@Composable
fun VoiceItemRow(
    voice: VoiceItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val country = displayCountry(voice.getCountryCode())
    val role = voice.getVoiceRole()
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp, 16.dp, 16.dp, 16.dp)
            .clickable(onClick = onClick),
        text = "$country - $role",
        color = MaterialTheme.colors.onBackground
    )
    ThemedDivider()
}

/**
 * The shared Locale shim has no displayCountry (java.util.Locale does); map
 * common country codes locally instead of editing the central stub.
 */
private fun displayCountry(countryCode: String): String = when (countryCode) {
    "US" -> "United States"
    "GB" -> "United Kingdom"
    "AU" -> "Australia"
    "CA" -> "Canada"
    "TW" -> "Taiwan"
    "CN" -> "China"
    "HK" -> "Hong Kong"
    "JP" -> "Japan"
    "KR" -> "South Korea"
    "FR" -> "France"
    "DE" -> "Germany"
    "ES" -> "Spain"
    "IT" -> "Italy"
    else -> countryCode
}
