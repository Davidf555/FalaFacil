package br.com.falafacil

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException

private val Application.dataStore by preferencesDataStore(name = "fala_facil")
private val RECENT_PHRASES = stringPreferencesKey("recent_phrases")
private val SPEECH_RATE = floatPreferencesKey("speech_rate")
private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
private val REPEAT_INTERVAL_MS = longPreferencesKey("repeat_interval_ms")
private val LANDSCAPE_COLUMNS = intPreferencesKey("landscape_columns")
private val QUICK_PHRASES = stringPreferencesKey("quick_phrases")

private const val MAX_RECENT_PHRASES = 20
private const val TTS_ISSUE_ID = "fala_facil_speech"
private val DEFAULT_QUICK_PHRASES = listOf(
    "POR FAVOR",
    "OBRIGADA",
    "BOM DIA",
    "BANHO",
    "ÁGUA"
)

data class FalaFacilUiState(
    val text: String = "",
    val recentPhrases: List<String> = emptyList(),
    val speechRate: Float = 1f,
    val vibrationEnabled: Boolean = true,
    val repeatIntervalMs: Long = 0L,
    val landscapeColumns: Int = 11,
    val quickPhrases: List<String> = DEFAULT_QUICK_PHRASES,
    val statusMessage: String = "",
    val isSpeaking: Boolean = false,
    val voiceUnavailable: Boolean = false,
    val showBlockedSpeechWarning: Boolean = false
)

class FalaFacilViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(FalaFacilUiState())
    val uiState = _uiState.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var ptBrAvailable = false
    private var lastKeyId: String? = null
    private var lastKeyTapTime = 0L
    private var blockedSpeechWarningJob: Job? = null
    private val blockedTerms by lazy {
        getApplication<Application>().resources.openRawResource(R.raw.blocked_terms)
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines ->
                lines.map { it.substringBefore('#').trim() }
                    .filter(String::isNotEmpty)
                    .map(::normalizeForBlocklist)
                    .map { it.split(' ').filter(String::isNotEmpty) }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
    }

    init {
        observePreferences()
    }

    fun initializeTextToSpeech() {
        if (textToSpeech != null) return
        textToSpeech = TextToSpeech(getApplication()) { status ->
            val engine = textToSpeech ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                ttsReady = false
                ptBrAvailable = false
                _uiState.value = _uiState.value.copy(
                    statusMessage = getApplication<Application>().getString(R.string.tts_init_error),
                    voiceUnavailable = true
                )
                return@TextToSpeech
            }

            ttsReady = true
            val locale = Locale.forLanguageTag("pt-BR")
            val languageStatus = engine.isLanguageAvailable(locale)
            ptBrAvailable = languageStatus >= TextToSpeech.LANG_COUNTRY_AVAILABLE
            if (ptBrAvailable) {
                val offlineVoice = engine.voices
                    ?.filter { voice ->
                        voice.locale.language == "pt" &&
                            voice.locale.country.equals("BR", ignoreCase = true) &&
                            !voice.isNetworkConnectionRequired
                    }
                    ?.maxByOrNull { it.quality }
                if (offlineVoice != null) engine.voice = offlineVoice else engine.setLanguage(locale)
                engine.setSpeechRate(_uiState.value.speechRate)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _uiState.value = _uiState.value.copy(isSpeaking = true)
                    }

                    override fun onDone(utteranceId: String?) {
                        _uiState.value = _uiState.value.copy(
                            statusMessage = getApplication<Application>().getString(R.string.speech_finished),
                            isSpeaking = false
                        )
                    }

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        updateStatus(R.string.speech_error)
                    }
                })
                _uiState.value = _uiState.value.copy(
                    statusMessage = getApplication<Application>().getString(R.string.voice_ready),
                    voiceUnavailable = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    statusMessage = getApplication<Application>().getString(R.string.tts_missing_title),
                    voiceUnavailable = true
                )
            }
        }
    }

    fun shutdownTextToSpeech() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ttsReady = false
        ptBrAvailable = false
        _uiState.value = _uiState.value.copy(isSpeaking = false)
    }

    fun onTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(text = text)
    }

    fun completeCurrentWord(suggestion: String) {
        val text = _uiState.value.text
        val boundary = text.indexOfLast(Char::isWhitespace) + 1
        val prefix = text.substring(0, boundary)
        val completedWord = if (prefix.isBlank() || prefix.trimEnd().endsWith(".")) {
            suggestion.replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) }
        } else {
            suggestion
        }
        _uiState.value = _uiState.value.copy(text = prefix + completedWord + " ")
    }

    fun appendText(value: String) {
        val current = _uiState.value.text
        val typedValue = if (value.length == 1 && value[0].isLetter()) {
            val shouldCapitalize = current.isEmpty() || current.trimEnd().endsWith(".")
            if (shouldCapitalize) value.uppercase(Locale.forLanguageTag("pt-BR"))
            else value.lowercase(Locale.forLanguageTag("pt-BR"))
        } else {
            value
        }
        _uiState.value = _uiState.value.copy(text = current + typedValue)
    }

    fun deleteLastCharacter() {
        val text = _uiState.value.text
        if (text.isNotEmpty()) _uiState.value = _uiState.value.copy(text = text.dropLast(1))
    }

    fun onKeyTap(keyId: String): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val interval = _uiState.value.repeatIntervalMs
        if (interval > 0L && keyId == lastKeyId && now - lastKeyTapTime < interval) return false
        lastKeyId = keyId
        lastKeyTapTime = now
        return true
    }

    fun onSpeechRateChanged(rate: Float) {
        if (rate !in listOf(0.7f, 1f, 1.3f)) return
        _uiState.value = _uiState.value.copy(speechRate = rate)
        textToSpeech?.setSpeechRate(rate)
        savePreference(SPEECH_RATE, rate)
    }

    fun onVibrationChanged(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(vibrationEnabled = enabled)
        savePreference(VIBRATION_ENABLED, enabled)
    }

    fun onRepeatIntervalChanged(intervalMs: Long) {
        if (intervalMs !in listOf(0L, 300L, 600L)) return
        _uiState.value = _uiState.value.copy(repeatIntervalMs = intervalMs)
        savePreference(REPEAT_INTERVAL_MS, intervalMs)
    }

    fun onLandscapeColumnsChanged(columns: Int) {
        if (columns !in listOf(8, 11)) return
        _uiState.value = _uiState.value.copy(landscapeColumns = columns)
        savePreference(LANDSCAPE_COLUMNS, columns)
    }

    fun onQuickPhrasesChanged(phrases: List<String>) {
        val cleaned = phrases.map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot(::containsBlockedTerm)
            .take(5)
        _uiState.value = _uiState.value.copy(quickPhrases = cleaned)
        viewModelScope.launch {
            try {
                getApplication<Application>().dataStore.edit { preferences ->
                    preferences[QUICK_PHRASES] = JSONArray(cleaned).toString()
                }
            } catch (exception: IOException) {
                updateStatus(R.string.preferences_save_error)
            }
        }
    }

    fun speak() {
        speakPhrase(_uiState.value.text)
    }

    fun speakPhrase(text: String) {
        val phrase = text.trim()
        if (phrase.isNotEmpty() && containsBlockedTerm(phrase)) {
            if (_uiState.value.isSpeaking) stop()
            blockedSpeechWarningJob?.cancel()
            _uiState.value = _uiState.value.copy(showBlockedSpeechWarning = true)
            blockedSpeechWarningJob = viewModelScope.launch {
                delay(3_000L)
                _uiState.value = _uiState.value.copy(showBlockedSpeechWarning = false)
            }
            return
        }
        if (_uiState.value.isSpeaking) {
            stop()
            return
        }
        if (phrase.isEmpty()) return
        blockedSpeechWarningJob?.cancel()
        _uiState.value = _uiState.value.copy(showBlockedSpeechWarning = false)
        if (!ttsReady || !ptBrAvailable) {
            _uiState.value = _uiState.value.copy(
                statusMessage = getApplication<Application>().getString(R.string.tts_missing_title),
                voiceUnavailable = true
            )
            return
        }

        val result = textToSpeech?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, TTS_ISSUE_ID)
        if (result != TextToSpeech.SUCCESS) {
            updateStatus(R.string.speech_error)
            return
        }
        _uiState.value = _uiState.value.copy(
            statusMessage = getApplication<Application>().getString(R.string.speaking),
            isSpeaking = true
        )
        saveRecentPhrase(phrase)
    }

    fun stop() {
        textToSpeech?.stop()
        _uiState.value = _uiState.value.copy(
            statusMessage = getApplication<Application>().getString(R.string.speech_stopped),
            isSpeaking = false
        )
    }

    fun requestVoiceInstall() {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            updateStatus(R.string.tts_install_unavailable)
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        updateStatus(R.string.preferences_load_error)
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .collect { preferences ->
                    val phrases = decodePhrases(preferences[RECENT_PHRASES].orEmpty())
                    _uiState.value = _uiState.value.copy(
                        recentPhrases = phrases,
                        speechRate = preferences[SPEECH_RATE] ?: 1f,
                        vibrationEnabled = preferences[VIBRATION_ENABLED] ?: true,
                        repeatIntervalMs = preferences[REPEAT_INTERVAL_MS] ?: 0L,
                        landscapeColumns = preferences[LANDSCAPE_COLUMNS]?.takeIf { it == 8 || it == 11 } ?: 11,
                        quickPhrases = if (preferences.contains(QUICK_PHRASES)) {
                            decodePhrases(preferences[QUICK_PHRASES].orEmpty())
                                .filterNot(::containsBlockedTerm)
                                .take(5)
                        } else {
                            DEFAULT_QUICK_PHRASES
                        }
                    )
                    textToSpeech?.setSpeechRate(_uiState.value.speechRate)
                }
        }
    }

    private fun <T> savePreference(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            try {
                getApplication<Application>().dataStore.edit { preferences -> preferences[key] = value }
            } catch (exception: IOException) {
                updateStatus(R.string.preferences_save_error)
            }
        }
    }

    private fun saveRecentPhrase(phrase: String) {
        viewModelScope.launch {
            try {
                getApplication<Application>().dataStore.edit { preferences ->
                    val previous = decodePhrases(preferences[RECENT_PHRASES].orEmpty())
                    val updated = (listOf(phrase) + previous.filterNot { it == phrase })
                        .take(MAX_RECENT_PHRASES)
                    preferences[RECENT_PHRASES] = JSONArray(updated).toString()
                }
            } catch (exception: IOException) {
                updateStatus(R.string.preferences_save_error)
            }
        }
    }

    private fun decodePhrases(encoded: String): List<String> {
        if (encoded.isBlank()) return emptyList()
        return try {
            val array = JSONArray(encoded)
            List(array.length()) { index -> array.getString(index) }
                .filter(String::isNotBlank)
                .take(MAX_RECENT_PHRASES)
        } catch (_: JSONException) {
            updateStatus(R.string.history_load_error)
            emptyList()
        }
    }

    private fun updateStatus(messageId: Int) {
        _uiState.value = _uiState.value.copy(
            statusMessage = getApplication<Application>().getString(messageId),
            isSpeaking = false
        )
    }

    fun containsBlockedTerm(text: String): Boolean {
        val words = normalizeForBlocklist(text).split(' ').filter(String::isNotEmpty)
        return blockedTerms.any { term ->
            words.windowed(term.size).any { it == term }
        }
    }

    private fun normalizeForBlocklist(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return buildString(decomposed.length) {
            decomposed.forEachIndexed { index, char ->
                if (Character.getType(char) != Character.NON_SPACING_MARK.toInt()) {
                    val normalized = when (char) {
                        '0' -> 'o'
                        '1' -> 'i'
                        '3' -> 'e'
                        '4' -> 'a'
                        '5' -> 's'
                        '7' -> 't'
                        else -> char
                    }
                    when {
                        normalized.isLetterOrDigit() -> {
                            if (lastOrNull() != normalized) append(normalized)
                        }
                        normalized.isWhitespace() -> {
                            if (isNotEmpty() && last() != ' ') append(' ')
                        }
                        separatesWords(decomposed, index) -> {
                            if (isNotEmpty() && last() != ' ') append(' ')
                        }
                    }
                }
            }
        }.trim()
    }

    private fun separatesWords(text: String, index: Int): Boolean {
        val char = text[index]
        if (char.isLetterOrDigit()) return false
        if (char != '.' && char != '-') return true
        var leftLength = 0
        var cursor = index - 1
        while (cursor >= 0 && text[cursor].isLetterOrDigit()) {
            leftLength++
            cursor--
        }
        var rightLength = 0
        cursor = index + 1
        while (cursor < text.length && text[cursor].isLetterOrDigit()) {
            rightLength++
            cursor++
        }
        return leftLength > 1 || rightLength > 1
    }

    override fun onCleared() {
        shutdownTextToSpeech()
        super.onCleared()
    }
}
