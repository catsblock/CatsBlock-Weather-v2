package com.catsblock.weather.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AgentGate { Checking, Open, Unavailable, WorkersLimit }

data class AgentMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String = "",
    val cards: List<AgentCard> = emptyList(),
    val pending: Boolean = false,
    val notice: Boolean = false,
    val steps: List<AgentStep> = emptyList(),
    val error: String? = null
)

private data class QueuedRequest(
    val userId: Long,
    val pendingId: Long,
    val text: String,
    val city: SavedCity?,
    val settings: DesktopSettings
)

private const val SpamWindowMs = 15L * 60L * 1000L
private const val SpamThreshold = 30
private const val SpamLockMs = 30L * 60L * 1000L

fun clampAgentInput(raw: String): String {
    val flat = raw.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
    if (flat.codePointCount(0, flat.length) <= AgentLimits.MAX_CHARS) return flat
    return flat.substring(0, flat.offsetByCodePoints(0, AgentLimits.MAX_CHARS))
}

private fun List<AgentStep>.mergeStep(step: AgentStep): List<AgentStep> {
    val index = indexOfLast { it.id == step.id && (step.command.isEmpty() || it.command == step.command) }
    if (index < 0) return this + step
    val merged = step.copy(command = step.command.ifEmpty { this[index].command })
    return toMutableList().also { it[index] = merged }
}

class AgentChat(
    private val scope: CoroutineScope,
    private val client: AgentClient = AgentClient()
) {
    private var nextId = 0L
    private val queue = ArrayDeque<QueuedRequest>()
    private val recentSends = ArrayDeque<Long>()
    private val history = ArrayDeque<AgentTurn>()
    private var spamUntil = 0L
    private var worker: Job? = null

    val messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val sending = MutableStateFlow(false)
    val blockedUntil = MutableStateFlow(AgentBlockStore.load())
    private val _gate = MutableStateFlow(AgentGate.Checking)
    val gate: StateFlow<AgentGate> = _gate.asStateFlow()

    fun refreshAvailability() {
        scope.launch {
            _gate.value = when (client.availability()) {
                AgentAvailability.Available -> AgentGate.Open
                AgentAvailability.WorkersLimit -> AgentGate.WorkersLimit
                AgentAvailability.Unavailable -> AgentGate.Unavailable
            }
        }
    }

    fun clearBlock() {
        blockedUntil.value = 0L
        AgentBlockStore.save(0L)
    }

    private fun block(seconds: Long) {
        val until = System.currentTimeMillis() + seconds.coerceAtLeast(1L) * 1000L
        blockedUntil.value = until
        AgentBlockStore.save(until)
    }

    private fun addNotice(text: String) {
        messages.update { it + AgentMessage(nextId++, fromUser = false, text = text, notice = true) }
    }

    private fun registerSend(now: Long): Boolean {
        while (recentSends.isNotEmpty() && now - recentSends.first() > SpamWindowMs) recentSends.removeFirst()
        recentSends.addLast(now)
        return recentSends.size >= SpamThreshold
    }

    fun send(raw: String, city: SavedCity?, settings: DesktopSettings): Boolean {
        val text = clampAgentInput(raw).trim()
        if (text.isEmpty()) return false
        val now = System.currentTimeMillis()
        if (blockedUntil.value > now) return false
        if (spamUntil > now) return false
        if (_gate.value != AgentGate.Open) return false
        if (registerSend(now)) {
            spamUntil = now + SpamLockMs
            recentSends.clear()
            rejectQueued(AgentMessages.SPAM)
            addNotice(AgentMessages.SPAM)
            return false
        }
        val userId = nextId++
        val pendingId = nextId++
        messages.update {
            it + AgentMessage(userId, fromUser = true, text = text) + AgentMessage(pendingId, fromUser = false, pending = true)
        }
        queue.addLast(QueuedRequest(userId, pendingId, text, city, settings))
        sending.value = true
        drain()
        return true
    }

    private fun drain() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val request = queue.removeFirstOrNull() ?: break
                process(request)
            }
            sending.value = false
        }
    }

    private fun rejectQueued(error: String) {
        val dropped = queue.toList()
        queue.clear()
        if (dropped.isEmpty()) return
        val pendingIds = dropped.map { it.pendingId }.toSet()
        val users = dropped.associate { it.userId to error }
        messages.update { list ->
            list.filterNot { it.id in pendingIds }.map { if (users.containsKey(it.id)) it.copy(error = error) else it }
        }
    }

    private fun rememberTurns(userText: String, reply: String) {
        history.addLast(AgentTurn(fromUser = true, text = userText))
        history.addLast(AgentTurn(fromUser = false, text = reply.take(AgentLimits.HISTORY_CHARS)))
        while (history.size > AgentLimits.HISTORY_TURNS) history.removeFirst()
    }

    private suspend fun process(request: QueuedRequest) {
        val context = history.toList()
        val outcome = try {
            if (request.city == null) {
                AgentOutcome.Failure(AgentMessages.UNAVAILABLE)
            } else {
                client.ask(request.text, request.city, request.settings, context) { step ->
                    messages.update { list ->
                        list.map { if (it.id == request.pendingId) it.copy(steps = it.steps.mergeStep(step)) else it }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AgentOutcome.Failure(AgentMessages.FAILED)
        }
        val replied = outcome is AgentOutcome.Reply
        val steps = messages.value.firstOrNull { it.id == request.pendingId }?.steps.orEmpty().map {
            if (it.status == StepStatus.Run) it.copy(status = if (replied) StepStatus.Done else StepStatus.Fail) else it
        }
        when (outcome) {
            is AgentOutcome.Reply -> {
                if (outcome.text.isNotEmpty() && outcome.text != AgentMessages.REFUSED) rememberTurns(request.text, outcome.text)
                resolve(
                    request.pendingId,
                    AgentMessage(
                        id = request.pendingId,
                        fromUser = false,
                        text = outcome.text,
                        cards = outcome.cards,
                        notice = outcome.text == AgentMessages.REFUSED,
                        steps = steps
                    )
                )
                if (outcome.remaining == 0) block(outcome.resetSeconds)
            }
            is AgentOutcome.Violation -> {
                messages.update { list ->
                    list.filterNot { it.id == request.pendingId }
                        .map { if (it.id == request.userId) it.copy(error = AgentMessages.VIOLATION) else it }
                }
                if (outcome.remaining == 0) block(outcome.resetSeconds)
            }
            is AgentOutcome.Failure -> when (outcome.message) {
                AgentMessages.LIMIT -> {
                    block(outcome.retryAfterSeconds)
                    messages.update { list -> list.filterNot { it.id == request.pendingId } }
                    rejectQueued(AgentMessages.LIMIT)
                }
                AgentMessages.SPAM -> {
                    spamUntil = System.currentTimeMillis() + outcome.retryAfterSeconds.coerceAtLeast(1L) * 1000L
                    resolve(request.pendingId, AgentMessage(request.pendingId, fromUser = false, text = outcome.message, notice = true, steps = steps))
                    rejectQueued(AgentMessages.SPAM)
                }
                AgentMessages.UNAVAILABLE -> {
                    _gate.value = AgentGate.Unavailable
                    resolve(request.pendingId, AgentMessage(request.pendingId, fromUser = false, text = outcome.message, notice = true, steps = steps))
                    rejectQueued(AgentMessages.UNAVAILABLE)
                }
                AgentMessages.WORKERS_LIMIT -> {
                    _gate.value = AgentGate.WorkersLimit
                    messages.update { list -> list.filterNot { it.id == request.pendingId } }
                    rejectQueued(AgentMessages.WORKERS_LIMIT)
                }
                else -> resolve(
                    request.pendingId,
                    AgentMessage(request.pendingId, fromUser = false, text = outcome.message, notice = true, steps = steps)
                )
            }
        }
    }

    private fun resolve(pendingId: Long, resolved: AgentMessage) {
        messages.update { list -> list.map { if (it.id == pendingId) resolved else it } }
    }
}
