package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.BrainBuddyAiService
import com.example.data.firebase.configureAppCheck
import com.example.data.model.StudyDeck
import com.example.data.model.UiState
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.StudyRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutUser
import com.example.ui.components.BrainBuddySplashScreen
import com.example.ui.components.ProfileAndAboutSheet
import com.example.ui.screens.BuddyAndFocusScreen
import com.example.ui.screens.CreateNotesSheet
import com.example.ui.screens.DeckDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.theme.BrainBuddyTheme
import com.example.ui.viewmodel.BrainBuddyViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MainTab { LIBRARY, INSIGHTS, STUDIO }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        setContent {
            val context = LocalContext.current
            val themePrefs = remember(context) { ThemePreferences(context.applicationContext) }
            val savedDarkPref by themePrefs.isDarkModeFlow.collectAsStateWithLifecycle(initialValue = null)
            val systemDark = isSystemInDarkTheme()
            val isDark = savedDarkPref ?: systemDark
            val scope = rememberCoroutineScope()

            BrainBuddyTheme(darkTheme = isDark) {
                BrainBuddyRoot(
                    isDarkMode = isDark,
                    onToggleDarkMode = { enabled ->
                        scope.launch { themePrefs.setDarkMode(enabled) }
                    }
                )
            }
        }
    }
}

@Composable
private fun BrainBuddyRoot(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showSplash by remember { mutableStateOf(true) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onUnauthenticated = { },
            scope = scope
        )
        delay(1350L)
        showSplash = false
    }

    AnimatedContent(
        targetState = Pair(showSplash, currentUser),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "root_transition"
    ) { (splashing, user) ->
        if (splashing) {
            BrainBuddySplashScreen()
        } else if (user == null) {
            AuthScreen(
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        } else {
            key(user.uid) {
                AuthenticatedBrainBuddyApp(
                    userId = user.uid,
                    displayName = user.displayName ?: "Learner",
                    userEmail = user.email,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedBrainBuddyApp(
    userId: String,
    displayName: String,
    userEmail: String?,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: BrainBuddyViewModel = viewModel(
        key = userId,
        factory = viewModelFactory {
            initializer {
                val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BrainBuddyViewModel(
                    repository = StudyRepository(db),
                    aiService = BrainBuddyAiService(),
                    currentUserId = userId,
                    displayName = displayName
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val decksState by viewModel.decksState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val flashcardsState by viewModel.selectedDeckFlashcards.collectAsStateWithLifecycle()
    val questionsState by viewModel.selectedDeckQuestions.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val weakAreaState by viewModel.weakAreaState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val tutorMessages by viewModel.tutorMessages.collectAsStateWithLifecycle()
    val tutorMode by viewModel.tutorMode.collectAsStateWithLifecycle()
    val isTutorStreaming by viewModel.isTutorStreaming.collectAsStateWithLifecycle()
    val focusTimerState by viewModel.focusTimerState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.LIBRARY) }
    var activeDeckId by remember { mutableStateOf<String?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionError) {
        val msg = actionError
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionError()
        }
    }

    val decks = (decksState as? UiState.Success<List<StudyDeck>>)?.data.orEmpty()
    val activeDeck = decks.firstOrNull { it.id == activeDeckId }

    if (activeDeck != null) {
        DeckDetailScreen(
            deck = activeDeck,
            flashcardsState = flashcardsState,
            questionsState = questionsState,
            onBack = { activeDeckId = null },
            onUpdateFlashcardMastery = { card, mastery, allCards ->
                viewModel.updateFlashcardMastery(activeDeck.id, card, mastery, allCards)
            },
            onRecordQuizResult = { questions, missedIds, mins ->
                viewModel.recordQuizResult(activeDeck, questions, missedIds, mins)
            },
            onAskBuddyAboutDeck = {
                activeDeckId = null
                currentTab = MainTab.STUDIO
            }
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                floatingActionButton = {
                    if (currentTab == MainTab.LIBRARY) {
                        FloatingActionButton(
                            onClick = {
                                viewModel.clearGenerationState()
                                showCreateSheet = true
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_create_deck")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Convert notes into deck")
                        }
                    }
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            NavigationBarItem(
                                selected = currentTab == MainTab.LIBRARY,
                                onClick = { currentTab = MainTab.LIBRARY },
                                icon = {
                                    Icon(
                                        if (currentTab == MainTab.LIBRARY) Icons.Rounded.MenuBook else Icons.Outlined.MenuBook,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_library)) },
                                modifier = Modifier.testTag("nav_library")
                            )
                            NavigationBarItem(
                                selected = currentTab == MainTab.INSIGHTS,
                                onClick = { currentTab = MainTab.INSIGHTS },
                                icon = {
                                    Icon(
                                        if (currentTab == MainTab.INSIGHTS) Icons.Rounded.Insights else Icons.Outlined.Insights,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_insights)) },
                                modifier = Modifier.testTag("nav_insights")
                            )
                            NavigationBarItem(
                                selected = currentTab == MainTab.STUDIO,
                                onClick = { currentTab = MainTab.STUDIO },
                                icon = {
                                    Icon(
                                        if (currentTab == MainTab.STUDIO) Icons.Rounded.AutoAwesome else Icons.Outlined.AutoAwesome,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_studio)) },
                                modifier = Modifier.testTag("nav_studio")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            NavigationRailItem(
                                selected = currentTab == MainTab.LIBRARY,
                                onClick = { currentTab = MainTab.LIBRARY },
                                icon = {
                                    Icon(
                                        if (currentTab == MainTab.LIBRARY) Icons.Rounded.MenuBook else Icons.Outlined.MenuBook,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_library)) }
                            )
                            NavigationRailItem(
                                selected = currentTab == MainTab.INSIGHTS,
                                onClick = { currentTab = MainTab.INSIGHTS },
                                icon = {
                                    Icon(
                                        if (currentTab == MainTab.INSIGHTS) Icons.Rounded.Insights else Icons.Outlined.Insights,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_insights)) }
                            )
                            NavigationRailItem(
                                selected = currentTab == MainTab.STUDIO,
                                onClick = { currentTab = MainTab.STUDIO },
                                icon = {
                                    Icon(
                                        if (currentTab == MainTab.STUDIO) Icons.Rounded.AutoAwesome else Icons.Outlined.AutoAwesome,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_studio)) }
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                            MainTab.LIBRARY -> HomeScreen(
                                profileState = profileState,
                                decksState = decksState,
                                onOpenCreateNotes = {
                                    viewModel.clearGenerationState()
                                    showCreateSheet = true
                                },
                                onOpenDeck = { deck ->
                                    viewModel.selectDeck(deck.id)
                                    activeDeckId = deck.id
                                },
                                onDeleteDeck = { deckId -> viewModel.deleteDeck(deckId) },
                                onOpenProfileSheet = { showProfileSheet = true }
                            )
                            MainTab.INSIGHTS -> InsightsScreen(
                                profileState = profileState,
                                decksState = decksState,
                                sessionsState = sessionsState,
                                weakAreaState = weakAreaState,
                                generationState = generationState,
                                onRunWeakAreaAnalysis = { viewModel.analyzeWeakAreas() },
                                onGenerateBoosterDrill = {
                                    viewModel.clearGenerationState()
                                    showCreateSheet = true
                                    viewModel.generateWeakAreaBooster()
                                }
                            )
                            MainTab.STUDIO -> BuddyAndFocusScreen(
                                decksState = decksState,
                                tutorMessages = tutorMessages,
                                tutorMode = tutorMode,
                                isTutorStreaming = isTutorStreaming,
                                focusTimerState = focusTimerState,
                                onSelectTutorMode = { viewModel.setTutorMode(it) },
                                onSendTutorMessage = { msg, deck -> viewModel.sendTutorMessage(msg, deck) },
                                onSelectFocusPreset = { viewModel.selectFocusPreset(it) },
                                onUpdateFocusLabel = { viewModel.updateFocusLabel(it) },
                                onToggleFocusTimer = { viewModel.toggleFocusTimer() },
                                onResetFocusTimer = { viewModel.resetFocusTimer() },
                                onLogElapsedFocus = { viewModel.logElapsedFocusNow() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateNotesSheet(
            generationState = generationState,
            onDismiss = {
                showCreateSheet = false
                viewModel.clearGenerationState()
            },
            onGenerate = { notes, subject, image ->
                viewModel.generateStudyPackFromNotes(notes, subject, image)
            },
            onCreatedDeckOpen = { createdId ->
                showCreateSheet = false
                viewModel.clearGenerationState()
                viewModel.selectDeck(createdId)
                activeDeckId = createdId
            }
        )
    }

    if (showProfileSheet) {
        ProfileAndAboutSheet(
            displayName = displayName,
            userEmail = userEmail,
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            onSignOut = {
                showProfileSheet = false
                onSignOut()
            },
            onDismiss = { showProfileSheet = false }
        )
    }
}
