package info.plateaukao.einkbro.view.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.setting_tts_locale
import info.plateaukao.einkbro.util.Locale
import org.jetbrains.compose.resources.stringResource

/**
 * Single-choice picker for the system-TTS language; the Android original is an
 * AlertDialog with setSingleChoiceItems. [locales] are the languages the device
 * actually has installed voices for, so the list can run to a few dozen rows.
 */
@Composable
fun TtsLanguageDialogContent(
    locales: List<Locale>,
    selectedLocale: Locale,
    onSelected: (Locale) -> Unit,
) {
    // Sorted by the name the user reads, as Android does.
    val sorted = remember(locales) { locales.sortedBy { it.displayName } }

    Column(modifier = Modifier.width(300.dp)) {
        Text(
            text = stringResource(Res.string.setting_tts_locale),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
        )
        Divider()
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(sorted) { locale ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(locale) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = locale.language == selectedLocale.language,
                        onClick = { onSelected(locale) },
                    )
                    Text(
                        text = locale.displayName,
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colors.onBackground,
                    )
                }
                Divider()
            }
        }
    }
}
