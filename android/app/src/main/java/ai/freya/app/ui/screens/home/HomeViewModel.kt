package ai.freya.app.ui.screens.home

import ai.freya.app.FreyaApplication
import ai.freya.app.domain.CoreState
import ai.freya.app.domain.Persona
import ai.freya.app.domain.SessionGreeting
import ai.freya.app.domain.Suggestion
import ai.freya.app.domain.Suggestions
import ai.freya.app.domain.model.MessageKind
import ai.freya.app.ui.core.Presence
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime

/** Thin presentation layer over the application-scoped [ai.freya.app.session.FreyaSession]. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as FreyaApplication).container
    val session = container.session
    val voice = container.voiceController
    private val dataStore = container.dataStore

    val messages = session.messages
    val inFlight = session.inFlight
    val coreState: StateFlow<CoreState> = session.coreState
    val error = session.error
    val activeTool = session.activeTool
    val receivedChars = session.receivedChars
    val acknowledgeTick = session.acknowledgeTick
    val providerConfigured = session.providerConfigured
    val backendReachable = session.backendReachable

    val partial = voice.partial
    val inputLevel = voice.inputLevel
    val wordTick = voice.wordTick
    val wordSynced = voice.wordSynced
    val audible = voice.audible

    val presence: StateFlow<Presence> = dataStore.presence
        .map { if (it == "core") Presence.CORE else Presence.AVATAR }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Presence.AVATAR)
    val haptics = dataStore.haptics.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val speechRate = dataStore.speechRate.stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    val suggestions: StateFlow<List<Suggestion>> = combine(
        session.messages, session.inFlight, session.providerConfigured, voice.state,
    ) { msgs, busy, provider, v ->
        Suggestions.forContext(
            providerConfigured = provider,
            conversationEmpty = msgs.isEmpty(),
            busy = busy || v.name == "LISTENING" || v.name == "SPEAKING",
            lastReply = msgs.lastOrNull { !it.isUser && it.kind == MessageKind.TEXT }?.content,
            hour = LocalTime.now().hour,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sttAvailable: Boolean get() = voice.sttAvailable
    val ttsAvailable: Boolean get() = voice.ttsAvailable

    val greeting: String = Persona.greetingBody()
    val showGreeting: Boolean get() = !SessionGreeting.shown

    init {
        // The session is created before sign-in completes; re-check provider status on entry.
        session.refreshProviderStatus()
    }

    fun markGreetingShown() {
        SessionGreeting.shown = true
    }

    fun send(text: String) = session.send(text)
    fun stop() = session.stop()
    fun retry(id: Long) = session.retry(id)
    fun readAloud(id: Long) = session.readAloud(id)
    fun feedback(id: Long, value: Int) = session.feedback(id, value)
    fun newConversation() = session.newConversation()
    fun dismissError() = session.dismissError()
    fun refreshStatus() = session.refreshProviderStatus()

    fun startListening() = voice.startListening()
    fun finishListening() = voice.stopListening()
    fun cancelListening() = voice.cancelListening()
    fun stopSpeaking() = voice.stopSpeaking()
}
