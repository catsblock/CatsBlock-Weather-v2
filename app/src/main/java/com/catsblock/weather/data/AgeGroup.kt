package com.catsblock.weather.data

val AgentLimitOptions: List<Int> = listOf(0, 15, 30, 60, 120, 180)

enum class AgeGroup(val agentAllowed: Boolean, val familyControls: Boolean) {
    Unknown(false, false),
    Child(false, false),
    Teen(true, true),
    Adult(true, false);

    companion object {
        const val MIN_AGE = 6
        const val MAX_AGE = 100
        const val AGENT_MIN_AGE = 13
        const val FAMILY_MAX_AGE = 18

        fun isValid(age: Int): Boolean = age in MIN_AGE..MAX_AGE

        fun of(age: Int): AgeGroup = when {
            !isValid(age) -> Unknown
            age < AGENT_MIN_AGE -> Child
            age <= FAMILY_MAX_AGE -> Teen
            else -> Adult
        }

        fun from(name: String?): AgeGroup = entries.firstOrNull { it.name == name } ?: Unknown
    }
}
