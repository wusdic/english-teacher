package com.englishteacher.core.domain.model

/** Incremental events emitted while a [com.englishteacher.core.domain.port.TutorEngine] streams a reply. */
sealed interface TutorStreamEvent {
    /** A newly-confirmed chunk of the tutor's spoken reply, safe to speak/display immediately. */
    data class ReplyDelta(val text: String) : TutorStreamEvent

    /** The turn is complete; [turn] carries the full reply plus corrections/repeat target. */
    data class Done(val turn: TutorTurn) : TutorStreamEvent
}
