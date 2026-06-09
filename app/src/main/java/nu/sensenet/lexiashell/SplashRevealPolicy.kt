package nu.sensenet.lexiashell

data class SplashRevealDecision(
    val revealNow: Boolean,
    val scheduleDelayMs: Long?,
)

class SplashRevealPolicy {
    private var pageFinishedAtMs: Long? = null
    private var lastConsoleOutputAtMs: Long? = null
    private var hasConsoleOutput = false
    private var hasRevealed = false

    fun reset() {
        pageFinishedAtMs = null
        lastConsoleOutputAtMs = null
        hasConsoleOutput = false
        hasRevealed = false
    }

    fun onPageFinished(nowMs: Long): SplashRevealDecision {
        pageFinishedAtMs = nowMs
        return decision(nowMs)
    }

    fun onConsoleOutput(nowMs: Long): SplashRevealDecision {
        hasConsoleOutput = true
        lastConsoleOutputAtMs = nowMs
        return decision(nowMs)
    }

    fun decision(nowMs: Long): SplashRevealDecision {
        if (hasRevealed) {
            return SplashRevealDecision(revealNow = false, scheduleDelayMs = null)
        }

        val finishedAtMs = pageFinishedAtMs
            ?: return SplashRevealDecision(revealNow = false, scheduleDelayMs = null)
        val revealAtMs =
            if (hasConsoleOutput) {
                requireNotNull(lastConsoleOutputAtMs) + CONSOLE_IDLE_REVEAL_DELAY_MS
            } else {
                finishedAtMs + NO_CONSOLE_REVEAL_TIMEOUT_MS
            }

        if (nowMs >= revealAtMs) {
            hasRevealed = true
            return SplashRevealDecision(revealNow = true, scheduleDelayMs = null)
        }

        return SplashRevealDecision(revealNow = false, scheduleDelayMs = revealAtMs - nowMs)
    }

    companion object {
        const val CONSOLE_IDLE_REVEAL_DELAY_MS = 1_000L
        const val NO_CONSOLE_REVEAL_TIMEOUT_MS = 3_000L
    }
}
