package com.catsblock.weather.ui

import com.catsblock.weather.data.AgentCard
import com.catsblock.weather.data.AgentClient
import com.catsblock.weather.data.AgentLimits
import com.catsblock.weather.data.AgentMessages
import com.catsblock.weather.data.AgentOutcome
import com.catsblock.weather.data.AgentStep
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.City
import com.catsblock.weather.data.StepStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgentMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String = "",
    val cards: List<AgentCard> = emptyList(),
    val pending: Boolean = false,
    val notice: Boolean = false,
    val steps: List<AgentStep> = emptyList()
)

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

    val messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val sending = MutableStateFlow(false)

    fun send(raw: String, city: City?, settings: AppSettings): Boolean {
        val text = clampAgentInput(raw).trim()
        if (text.isEmpty() || sending.value) return false
        val userMessage = AgentMessage(nextId++, fromUser = true, text = text)
        val pendingId = nextId++
        messages.value = messages.value + userMessage + AgentMessage(pendingId, fromUser = false, pending = true)
        sending.value = true
        scope.launch {
            val outcome = try {
                if (city == null) {
                    AgentOutcome.Failure(AgentMessages.FAILED)
                } else {
                    client.ask(text, city, settings) { step ->
                        messages.update { list ->
                            list.map { if (it.id == pendingId) it.copy(steps = it.steps.mergeStep(step)) else it }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AgentOutcome.Failure(AgentMessages.FAILED)
            }
            val replied = outcome is AgentOutcome.Reply
            val steps = messages.value.firstOrNull { it.id == pendingId }?.steps.orEmpty().map {
                if (it.status == StepStatus.Run) it.copy(status = if (replied) StepStatus.Done else StepStatus.Fail) else it
            }
            val resolved = when (outcome) {
                is AgentOutcome.Reply -> AgentMessage(
                    id = pendingId,
                    fromUser = false,
                    text = outcome.text,
                    cards = outcome.cards,
                    notice = outcome.text == AgentMessages.REFUSED,
                    steps = steps
                )
                is AgentOutcome.Failure -> AgentMessage(
                    id = pendingId,
                    fromUser = false,
                    text = outcome.message,
                    notice = true,
                    steps = steps
                )
            }
            messages.update { list -> list.map { if (it.id == pendingId) resolved else it } }
            sending.value = false
        }
        return true
    }
}
