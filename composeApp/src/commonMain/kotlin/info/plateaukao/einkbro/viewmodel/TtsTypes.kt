package info.plateaukao.einkbro.viewmodel

import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import org.jetbrains.compose.resources.StringResource

enum class TtsType {
    SYSTEM, GPT, ETTS
}

enum class TtsReadingState {
    PREPARING, PLAYING, PAUSED, IDLE
}

fun TtsType.toStringResId(): StringResource {
    return when (this) {
        TtsType.GPT -> Res.string.tts_type_gpt
        TtsType.ETTS -> Res.string.tts_type_etts
        TtsType.SYSTEM -> Res.string.tts_type_system
    }
}

data class ReadProgress(val index: Int, val total: Int, val articleLeftCount: Int) {
    override fun toString(): String {
        if (total == 0) return "($articleLeftCount)"

        return "$index/$total " +
                if (articleLeftCount > 0) {
                    "($articleLeftCount)"
                } else {
                    ""
                }
    }
}
