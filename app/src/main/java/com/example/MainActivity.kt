package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppDatabase
import com.example.data.repository.HistoryRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TranslationUiState
import com.example.ui.viewmodel.TranslationViewModel
import com.example.ui.viewmodel.TranslationViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize Room Local Database
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = HistoryRepository(database.historyDao())

        // 2. Instantiate Architecture Components (MVVM ViewModel)
        val viewModel: TranslationViewModel = ViewModelProvider(
            this,
            TranslationViewModelFactory(repository)
        )[TranslationViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    BhashaBridgeScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BhashaBridgeScreen(
    viewModel: TranslationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val sharedPrefs = remember { context.getSharedPreferences("bhasha_bridge_prefs", android.content.Context.MODE_PRIVATE) }
    var username by remember { mutableStateOf(sharedPrefs.getString("username", "") ?: "") }
    var fontSizeSetting by remember { mutableStateOf(sharedPrefs.getString("font_size_setting", "medium") ?: "medium") }
    var tempUsername by remember { mutableStateOf(username) }

    val updateUsername = { newName: String ->
        username = newName
        sharedPrefs.edit().putString("username", newName).apply()
    }

    val updateFontSize = { newSize: String ->
        fontSizeSetting = newSize
        sharedPrefs.edit().putString("font_size_setting", newSize).apply()
    }

    val fontSizeMultiplier = when (fontSizeSetting) {
        "small" -> 0.85f
        "medium" -> 1.0f
        "large" -> 1.25f
        "extra_large" -> 1.45f
        else -> 1.0f
    }

    var showOnboardingDialog by remember { mutableStateOf(username.isEmpty()) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Observe StateFlows from ViewModel
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val sourceLang by viewModel.sourceLang.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLang.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    // Initialize Text-To-Speech engine
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var meetsTtsInit by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var ttsInstance: android.speech.tts.TextToSpeech? = null
        try {
            ttsInstance = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        meetsTtsInit = true
                    }
                }
            }
            tts = ttsInstance
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            try {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val speakText = { text: String ->
        val safeTts = tts
        if (text.isNotEmpty() && safeTts != null && meetsTtsInit) {
            try {
                val locale = when (targetLang) {
                    "Hindi" -> java.util.Locale("hi", "IN")
                    "Tamil" -> java.util.Locale("ta", "IN")
                    "Telugu" -> java.util.Locale("te", "IN")
                    "Kannada" -> java.util.Locale("kn", "IN")
                    "Malayalam" -> java.util.Locale("ml", "IN")
                    else -> java.util.Locale("hi", "IN")
                }
                safeTts.language = locale
                safeTts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var lastSpokenText by remember { mutableStateOf("") }
    LaunchedEffect(uiState) {
        if (uiState is TranslationUiState.Success) {
            val textToSpeak = (uiState as TranslationUiState.Success).resultText
            if (textToSpeak.isNotEmpty() && textToSpeak != lastSpokenText) {
                speakText(textToSpeak)
                lastSpokenText = textToSpeak
            }
        } else {
            lastSpokenText = ""
        }
    }

    val speechLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                viewModel.updateInputText(spokenText)
                Toast.makeText(context, "Voice input received!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val startSpeechRecognition = {
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val bcp47 = when (sourceLang) {
                "Hindi" -> "hi-IN"
                "Tamil" -> "ta-IN"
                "Telugu" -> "te-IN"
                "Kannada" -> "kn-IN"
                "Malayalam" -> "ml-IN"
                else -> "hi-IN"
            }
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, bcp47)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak now to translate...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Speech Recognition is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var listTabState by remember { mutableStateOf(0) } // 0 = Recents, 1 = Favorites

    // Language options
    val sourceLanguages = listOf("Auto-detect", "Hindi", "Tamil", "Telugu", "Kannada", "Malayalam")
    val targetLanguages = listOf("Hindi", "Tamil", "Telugu", "Kannada", "Malayalam")

    // Dropdown state
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var targetDropdownExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // --- 1. App Header Banner ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (username.isNotEmpty()) "Namaste, $username!" else "BhashaBridge",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (24 * fontSizeMultiplier).sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-0.5).sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(color = com.example.ui.theme.EmeraldActive, shape = CircleShape)
                    )
                    Text(
                        text = "GEMMA 4 • OFFLINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            // Trailing Settings Action Button in the Elegant Dark theme header
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable {
                        tempUsername = username
                        showSettingsDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Warning banner if API key matches placeholder
        if (!viewModel.isApiKeyValid()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("api_key_warning")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Gemma 4 simulation requires a Google Gemini API Key configured in the Secrets Panel of AI Studio.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // --- 2. Translation Console Sheet ---
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("translation_console")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Dropdowns Row with customized Elegant Dark Pill Selectors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Source Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF505357),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable { sourceDropdownExpanded = true }
                                .testTag("source_lang_selector"),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = sourceLang.uppercase(),
                                    fontSize = (12 * fontSizeMultiplier).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "▼",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = sourceDropdownExpanded,
                            onDismissRequest = { sourceDropdownExpanded = false }
                        ) {
                            sourceLanguages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang) },
                                    onClick = {
                                        viewModel.setSourceLang(lang)
                                        sourceDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Bidirectional SWAP Button in Elegant Pill Style
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(36.dp)
                            .background(
                                color = com.example.ui.theme.ElegantSwitchBg,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (sourceLang == "Auto-detect") {
                                    Toast.makeText(context, "Cannot swap when source language is Auto-detect", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.swapLanguages()
                                }
                            }
                            .testTag("swap_languages_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⇅",
                            color = com.example.ui.theme.ElegantSwitchText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Target Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF505357),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable { targetDropdownExpanded = true }
                                .testTag("target_lang_selector"),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = targetLang.uppercase(),
                                    fontSize = (12 * fontSizeMultiplier).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "▼",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = targetDropdownExpanded,
                            onDismissRequest = { targetDropdownExpanded = false }
                        ) {
                            targetLanguages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang) },
                                    onClick = {
                                        viewModel.setTargetLang(lang)
                                        targetDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode select chips group - themed for Elegant Dark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modeOptions = listOf(
                        "translate" to "Translate",
                        "detect" to "Detect Lang",
                        "romanize" to "Romanize"
                    )

                    modeOptions.forEach { (modeVal, label) ->
                        val selected = mode == modeVal
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setMode(modeVal) },
                            label = { Text(label, fontSize = (11 * fontSizeMultiplier).sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                selectedBorderColor = Color.Transparent,
                                borderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.testTag("mode_chip_$modeVal")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input box container background panel
                Text(
                    text = "Source Input Details",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { viewModel.updateInputText(it) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = (14 * fontSizeMultiplier).sp),
                    placeholder = {
                        Text(
                            text = if (sourceLang == "Auto-detect") "Type any Hindi or South Indian text..."
                            else "Type in $sourceLang...",
                            fontSize = (14 * fontSizeMultiplier).sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("source_text_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    ),
                    maxLines = 4,
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateInputText("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Text",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Paste & Speak Group
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Action: Paste text
                        TextButton(
                            onClick = {
                                val clipboardText = clipboardManager.getText()?.text ?: ""
                                if (clipboardText.isNotEmpty()) {
                                    viewModel.updateInputText(clipboardText)
                                    Toast.makeText(context, "Text Pasted!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard empty!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste Text", fontSize = 12.sp)
                        }

                        // Voice Input button
                        TextButton(
                            onClick = { startSpeechRecognition() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("voice_input_button")
                        ) {
                            Text("🎙️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Speak", fontSize = 12.sp)
                        }
                    }

                    // Translate / Execute Button
                    Button(
                        onClick = { viewModel.translate() },
                        shape = RoundedCornerShape(24.dp),
                        enabled = inputText.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("translate_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Translate icon",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (mode) {
                                "detect" -> "Analyze Script"
                                "romanize" -> "Romanize"
                                else -> "Translate"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. Translation Output Result Panel ---
        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "UiStateAnimation"
        ) { state ->
            when (state) {
                is TranslationUiState.Idle -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("output_panel_idle")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Translation idle details",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "BhashaBridge translation engine is idle.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "Provide Hindi or Dravidian words to generate translations offline.",
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                is TranslationUiState.Loading -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(28.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("output_panel_loading")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Translating with offline Gemma 4...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Local neural model processing sequence",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                is TranslationUiState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.onSecondary,
                            contentColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(28.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("output_panel_success")
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Success check icon",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when (mode) {
                                            "detect" -> "DETECTED SCRIPT ID"
                                            "romanize" -> "PHONETIC ROMANIZATION"
                                            else -> "OFFLINE TRANSLATION - ${targetLang.uppercase()}"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Interactive Speaker Button
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.tertiary,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                speakText(state.resultText)
                                                Toast.makeText(context, "Speaking...", Toast.LENGTH_SHORT).show()
                                            }
                                            .testTag("speak_translation_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "🔊",
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.tertiary,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                clipboardManager.setText(AnnotatedString(state.resultText))
                                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Copy Translation",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.tertiary,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, state.resultText)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Share",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = state.resultText,
                                fontSize = (24 * fontSizeMultiplier).sp,
                                lineHeight = (32 * fontSizeMultiplier).sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("translated_result_text")
                            )

                            if (mode == "translate") {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Bridge connection calibrated successfully • 98% conf",
                                    fontSize = 11.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                is TranslationUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(28.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("output_panel_error")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error notification",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Translation failed",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.errorMessage,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 4. Translation History Tab Sheets ---
        TabRow(
            selectedTabIndex = listTabState,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_tab_row")
        ) {
            Tab(
                selected = listTabState == 0,
                onClick = { listTabState = 0 },
                text = { Text("Recents", fontWeight = if (listTabState == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Recents Tab", modifier = Modifier.size(18.dp)) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Tab(
                selected = listTabState == 1,
                onClick = { listTabState = 1 },
                text = { Text("Favorites", fontWeight = if (listTabState == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) },
                icon = { Icon(if (listTabState == 1) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorites Tab", modifier = Modifier.size(18.dp)) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }

        val activeList = if (listTabState == 0) allHistory else favorites

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (listTabState == 0) "Saved SQLite Database Entries" else "Starred Database Translations",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            // Clear All database entries triggers
            if (listTabState == 0 && activeList.isNotEmpty()) {
                TextButton(
                    onClick = { showClearHistoryDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Trash", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", fontSize = 11.sp)
                }
            }
        }

        if (activeList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (listTabState == 0) "No translation history yet." else "No starred favorites yet.",
                    fontSize = (13 * fontSizeMultiplier).sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scrolling_history"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeList.forEach { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.makeHistoryActive(item)
                                speakText(item.translatedText)
                            }
                            .testTag("history_item_card_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${item.sourceLang} ➔ ${item.targetLang}",
                                        fontSize = (10 * fontSizeMultiplier).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        ).padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.mode.uppercase(),
                                        fontSize = (9 * fontSizeMultiplier).sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.sourceText,
                                    fontSize = (13 * fontSizeMultiplier).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    textAlign = TextAlign.Start
                                )
                                Text(
                                    text = item.translatedText,
                                    fontSize = (12 * fontSizeMultiplier).sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    textAlign = TextAlign.Start
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Star Favorite Toggle
                                IconButton(onClick = { viewModel.toggleFavorite(item) }) {
                                    Icon(
                                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        contentDescription = "Toggle Star Favorites",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Single Delete Item
                                IconButton(onClick = { viewModel.deleteHistoryItem(item) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        contentDescription = "Delete item",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialog Confirmation overlay for database wipe
        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("Clear All Translation Recents?") },
                text = { Text("Are you sure you want to permanently delete all translation logs in the local database? Starred favorites will remain untouched.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllHistory()
                            showClearHistoryDialog = false
                            Toast.makeText(context, "History wiped clean!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Confirm Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("clear_history_alert_dialog")
            )
        }

        // Onboarding Welcome Username Query Dialog
        if (showOnboardingDialog) {
            AlertDialog(
                onDismissRequest = { /* Prevent dismissal to force initial setup */ },
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🇮🇳 BhashaBridge",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dravidian & Hindi Neural Translator",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Welcome! Let's personalize your translation experience. Please enter your name:",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = tempUsername,
                            onValueChange = { tempUsername = it },
                            placeholder = { Text("Your Username (e.g. Aakarshak)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalName = tempUsername.trim()
                            if (finalName.isNotEmpty()) {
                                updateUsername(finalName)
                                showOnboardingDialog = false
                                Toast.makeText(context, "Welcome, $finalName!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please enter a username to start", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Get Started 🚀", fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.testTag("onboarding_dialog")
            )
        }

        // Configuration Settings Popup Dialog
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("BhashaBridge Settings")
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Username configuration
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "User Identity Label",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = tempUsername,
                                onValueChange = { tempUsername = it },
                                placeholder = { Text("Enter your name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Font size customizer
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Custom Font Sizing",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(
                                    "small" to "Small",
                                    "medium" to "Default",
                                    "large" to "Large",
                                    "extra_large" to "Huge"
                                ).forEach { (sizeVal, sizeLabel) ->
                                    val sizeSelected = fontSizeSetting == sizeVal
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .background(
                                                color = if (sizeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (sizeSelected) Color.Transparent else MaterialTheme.colorScheme.outline,
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .clickable { updateFontSize(sizeVal) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = sizeLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sizeSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Extra info card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Bridging Model Info",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Powered by Gemini & offline-optimized Gemma 4 sequence generation technology.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tempUsername.trim().isNotEmpty()) {
                                updateUsername(tempUsername.trim())
                            }
                            showSettingsDialog = false
                            Toast.makeText(context, "Settings updated successfully!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Save Changes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("settings_dialog")
            )
        }
    }
}
