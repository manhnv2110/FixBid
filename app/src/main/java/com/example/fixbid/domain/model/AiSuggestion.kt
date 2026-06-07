package com.example.fixbid.domain.model

/**
 * One actionable AI shortcut surfaced inline on a domain screen
 * (booking detail, worker profile, history, …). The user taps a chip and
 * either opens the chatbot pre-filled with the [prompt], runs an inline
 * single-turn analysis whose answer renders directly on the screen, or
 * navigates somewhere relevant.
 *
 * Suggestions are produced by [com.example.fixbid.domain.usecase.shared.AiSuggestionEngine]
 * from a small [AiContext] payload — no network call until the user actually
 * opts in. This keeps the surface free of always-on AI traffic.
 */
data class AiSuggestion(
    /** Stable id used for keyed Compose composition + analytics. */
    val id: String,
    /** Short label shown on the chip — keep ≤ 28 chars. */
    val label: String,
    /** What kind of behaviour the chip triggers. */
    val kind: AiSuggestionKind,
    /**
     * For [AiSuggestionKind.PREFILL_CHAT] / [AiSuggestionKind.INLINE_ANALYZE]:
     * the exact text the agent will receive. Should be a self-contained
     * question because the model has no other screen context.
     */
    val prompt: String = "",
    /** For [AiSuggestionKind.NAVIGATE]: route to push onto the back stack. */
    val route: String? = null,
    /** Optional sub-line shown under the label in compact card mode. */
    val helper: String? = null,
    /** Compose Material icon vector reference key — see AiSuggestionIcons.kt. */
    val iconKey: AiSuggestionIcon = AiSuggestionIcon.Sparkle
)

enum class AiSuggestionKind {
    /** Open the chatbot screen with the prompt pre-filled (user can tweak then send). */
    PREFILL_CHAT,
    /** Run one round of analysis right on the current screen and show the result inline. */
    INLINE_ANALYZE,
    /** Navigate to a specific route — used for "open shortcut" type chips. */
    NAVIGATE
}

/**
 * Icon choices for an [AiSuggestion]. Mapped to actual ImageVectors in the
 * presentation layer so domain stays UI-framework-free.
 */
enum class AiSuggestionIcon {
    Sparkle,        // generic AI spark
    Question,       // "explain"/"what should I do"
    Compare,        // compare prices/workers
    Edit,           // help drafting text
    Check,          // pre-flight checklist
    Insights,       // analytics/summary
    Warning         // risk/anomaly hint
}

/**
 * The data the suggestion engine needs to decide which chips to show on a
 * given screen. Kept intentionally generic so screens can pass whatever they
 * already have without spinning up a feature-specific facade.
 */
data class AiContext(
    val screen: AiContextScreen,
    /** Active user role at composition time. */
    val userRole: UserRole,
    /**
     * Domain-specific payload. Common keys:
     *  - `bookingId`, `bookingStatus`, `bookingType`, `category`,
     *    `quotedPrice`, `agreedPrice`, `description`, `address`,
     *    `scheduledAt`, `workerName`, `workerId`, `bidsCount`, `lowestBid`,
     *    `averageBid`, `competitorBidsCount`.
     *
     * Values may be String / Long / Double / Boolean / null. The engine
     * inspects only what it needs and ignores the rest.
     */
    val data: Map<String, Any?> = emptyMap()
)

enum class AiContextScreen {
    CUSTOMER_BOOKING_DETAIL,
    CUSTOMER_BOOKING_HISTORY,
    CUSTOMER_WORKER_PROFILE,
    WORKER_JOB_DETAIL,
    WORKER_HOME,
    WORKER_MY_BIDS
}
