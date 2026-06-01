package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.model.HistoryItem
import com.example.data.repository.HistoryRepository
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TranslationUiState {
    object Idle : TranslationUiState
    object Loading : TranslationUiState
    data class Success(val resultText: String) : TranslationUiState
    data class Error(val errorMessage: String) : TranslationUiState
}

class TranslationViewModel(private val repository: HistoryRepository) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _sourceLang = MutableStateFlow("Hindi")
    val sourceLang: StateFlow<String> = _sourceLang.asStateFlow()

    private val _targetLang = MutableStateFlow("Tamil")
    val targetLang: StateFlow<String> = _targetLang.asStateFlow()

    private val _mode = MutableStateFlow("translate") // translate, detect, romanize
    val mode: StateFlow<String> = _mode.asStateFlow()

    private val _uiState = MutableStateFlow<TranslationUiState>(TranslationUiState.Idle)
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    // Observe local Room history database reactively
    val allHistory: StateFlow<List<HistoryItem>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favorites: StateFlow<List<HistoryItem>> = repository.favorites
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun setSourceLang(lang: String) {
        _sourceLang.value = lang
    }

    fun setTargetLang(lang: String) {
        _targetLang.value = lang
    }

    fun setMode(modeValue: String) {
        _mode.value = modeValue
    }

    fun swapLanguages() {
        val currentSource = _sourceLang.value
        val currentTarget = _targetLang.value
        // Swap if both are valid languages.
        // If source is Auto-detect, default to another.
        if (currentSource != "Auto-detect") {
            _sourceLang.value = currentTarget
            _targetLang.value = currentSource
        }
    }

    fun isApiKeyValid(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    fun translate() {
        val textToTranslate = _inputText.value.trim()
        if (textToTranslate.isEmpty()) {
            _uiState.value = TranslationUiState.Idle
            return
        }

        if (!isApiKeyValid()) {
            _uiState.value = TranslationUiState.Error(
                "API Key is not configured correctly. Please enter your GEMINI_API_KEY into the Secrets Panel in Google AI Studio to run translations."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = TranslationUiState.Loading

            try {
                val systemPrompt = """
                    You are BhashaBridge, an expert offline translator specializing exclusively in bidirectional translation between Hindi and South Indian languages: Tamil, Telugu, Kannada, and Malayalam.

                    You run entirely on-device using Gemma 4. You have no internet access. Do not attempt to fetch, reference, or suggest any external resource, API, or online service. All translation must happen locally using your trained knowledge only.

                    STRICT RULES — follow every time:
                    1. LANGUAGE DETECTION: Automatically detect the source language from the user's input. Do not ask the user to specify the language unless the script is completely ambiguous.
                    2. TARGET LANGUAGE: The user selects the target language in the app UI. It is passed to you in the [TARGET] tag. Always translate into that language. (e.g. Hindi, Tamil, Telugu, Kannada, Malayalam)
                    3. OUTPUT FORMAT: Return ONLY the translated text. No explanations, no transliteration, no English unless the target language is explicitly English. No preambles like "Here is the translation:" or "Translation:".
                    4. SCRIPT FIDELITY: Always output in the native script of the target language.
                       - Tamil → Tamil script (தமிழ்)
                       - Telugu → Telugu script (తెలుగు)
                       - Kannada → Kannada script (ಕನ್ನಡ)
                       - Malayalam → Malayalam script (മലയാളം)
                       - Hindi → Devanagari script (हिन्दी)
                    5. REGISTER & TONE: Preserve the formality, tone, and register of the source text. Casual inputs stay casual; formal inputs stay formal.
                    6. CULTURAL CONTEXT: When a term has no direct equivalent, use the closest culturally appropriate phrase in the target language. Never transliterate blindly.
                    7. NAMES & PROPER NOUNS: Transliterate proper nouns phonetically into the target script. Do not translate their meaning.
                    8. NUMBERS: Convert numbers to the numeral system conventional for the target language's regional usage (Arabic numerals acceptable for all).
                    9. NO HALLUCINATION: If the input text is too ambiguous, too corrupt, or contains an unknown script, respond only with: "⚠ Input unclear. Please retype."
                    10. OFFLINE CONSTRAINT: Never reference the internet, real-time data, or suggest searching anything online.

                    Mode Behaviour:
                    - translate (default) -> Output: only the translated text in target script.
                    - detect -> Output: only the detected language name and script name. Format: "Language: {name} | Script: {script name}"
                    - romanize -> Output: romanized (Latin-script) transliteration of the source text using standard phonetic conventions (IAST or ISO 15919). Do NOT translate meaning — only transliterate sound.

                    Error & Edge-Case Handling:
                    - Mixed-language input (code-switching): Translate the dominant language portions; for minor inclusions within the text, translate inline.
                    - English words within Indian text: Keep common loanwords in their transliterated form in the target script (e.g., "phone", "bus" -> phonetic equivalent in target script).
                    - Empty input: Respond with nothing. Do not prompt the user.
                    - Offensive / harmful text: Translate faithfully. You are a translation engine, not a content filter. The app layer handles moderation.
                    - Very long text (>500 words): Translate fully. Do not summarize or truncate.
                    - Dialectal variation: Default to the most widely understood standard dialect of the target language.
                """.trimIndent()

                val formattedUserQuery = """
                    [SOURCE_LANG]: ${_sourceLang.value}
                    [TARGET_LANG]: ${_targetLang.value}
                    [MODE]: ${_mode.value}
                    [TEXT]: $textToTranslate
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = formattedUserQuery)))
                    ),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                )

                val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                val rawResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

                val cleanResultText = rawResult.trim()
                if (cleanResultText.isNotEmpty()) {
                    _uiState.value = TranslationUiState.Success(cleanResultText)

                    // Insert into local Room database history asynchronously
                    repository.insert(
                        HistoryItem(
                            sourceText = textToTranslate,
                            translatedText = cleanResultText,
                            sourceLang = _sourceLang.value,
                            targetLang = _targetLang.value,
                            mode = _mode.value
                        )
                    )
                } else {
                    _uiState.value = TranslationUiState.Error("An empty response was received. Please try again.")
                }

            } catch (e: Exception) {
                _uiState.value = TranslationUiState.Error(e.localizedMessage ?: "Network or API compilation error occurred.")
            }
        }
    }

    fun makeHistoryActive(item: HistoryItem) {
        _inputText.value = item.sourceText
        _sourceLang.value = item.sourceLang
        _targetLang.value = item.targetLang
        _mode.value = item.mode
        _uiState.value = TranslationUiState.Success(item.translatedText)
    }

    fun deleteHistoryItem(item: HistoryItem) {
        viewModelScope.launch {
            repository.deleteById(item.id)
        }
    }

    fun toggleFavorite(item: HistoryItem) {
        viewModelScope.launch {
            repository.updateFavorite(item.id, !item.isFavorite)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}

class TranslationViewModelFactory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TranslationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TranslationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
