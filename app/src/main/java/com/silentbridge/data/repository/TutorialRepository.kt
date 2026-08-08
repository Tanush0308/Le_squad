package com.silentbridge.data.repository

import com.silentbridge.domain.model.TutorialItem

/**
 * Static repository of ASL sign language tutorials for the 13-word SilentBridge vocabulary.
 *
 * Video Sources (all freely accessible on YouTube):
 *  - Primary: ASLUniversity / Dr. Bill Vicars (lifeprint.com) — @ASLUniversity channel
 *  - Secondary: SignwithRobert, Signing Naturally community uploads
 *
 * YouTube IDs were verified for accuracy as of August 2026.
 * Each description follows the Lifeprint.com movement notation conventions.
 */
object TutorialRepository {

    val tutorials: List<TutorialItem> = listOf(

        TutorialItem(
            id = 1,
            word = "Hello",
            emoji = "👋",
            youtubeId = "ianGHPr_4eQ",  // ASLUniversity — "HELLO in ASL"
            description = "Start with a flat, open hand (B handshape) near your right temple. " +
                    "Swing your hand outward and forward, away from your forehead, " +
                    "ending with your palm facing out — like a casual salute or wave.",
            handshape = "Open B (flat hand)"
        ),

        TutorialItem(
            id = 2,
            word = "Thank You",
            emoji = "🙏",
            youtubeId = "c7DtReAXNow",  // ASLUniversity — "THANK YOU in ASL"
            description = "Touch the fingertips of your flat, open hand to your chin " +
                    "(or just below your lips). Move your hand forward and slightly down, " +
                    "as if tossing a kiss or extending gratitude outward toward the person.",
            handshape = "Open B (flat hand)"
        ),

        TutorialItem(
            id = 3,
            word = "I / Me",
            emoji = "👤",
            youtubeId = "yLhKtAJR7ok",  // ASLUniversity — "Pronouns in ASL (I, YOU, HE, SHE)"
            description = "Simply point your index finger directly at the centre of your own chest. " +
                    "Keep the motion small and deliberate. This also works for 'Me'.",
            handshape = "1 (index finger pointing)"
        ),

        TutorialItem(
            id = 4,
            word = "You",
            emoji = "🫵",
            youtubeId = "yLhKtAJR7ok",  // Same video covers I/You/He/She pronouns
            description = "Point your index finger directly toward the person you are addressing. " +
                    "If signing to a group, sweep your pointing finger across the audience.",
            handshape = "1 (index finger pointing)"
        ),

        TutorialItem(
            id = 5,
            word = "Want",
            emoji = "🤲",
            youtubeId = "OcLJ3uMKBM0",  // ASLUniversity — "WANT in ASL"
            description = "Hold both hands out in front of you, palms facing up, fingers spread " +
                    "slightly open. Pull both hands back toward your body while simultaneously " +
                    "curling your fingers into a claw/hook shape — as if pulling something toward you.",
            handshape = "Bent 5 (claw hands, palms up)"
        ),

        TutorialItem(
            id = 6,
            word = "Need",
            emoji = "✊",
            youtubeId = "3gAD3C8FKOQ",  // ASLUniversity — "NEED / MUST / HAVE-TO in ASL"
            description = "Form an X handshape (hook your index finger while keeping other fingers " +
                    "closed). Hold it in front of you and make a sharp, firm downward bend at the wrist — " +
                    "like a single strong nod of the hand.",
            handshape = "X (bent index finger)"
        ),

        TutorialItem(
            id = 7,
            word = "Help",
            emoji = "🆘",
            youtubeId = "C5xdPPLReXs",  // ASLUniversity — "HELP in ASL"
            description = "Make a thumbs-up with your dominant hand and place it on your " +
                    "non-dominant open palm. Lift both hands together upward — the open palm " +
                    "is 'lifting' or supporting the thumbs-up hand.",
            handshape = "A (thumbs-up) on flat B palm"
        ),

        TutorialItem(
            id = 8,
            word = "Food",
            emoji = "🍽️",
            youtubeId = "7_c-w9-7fPQ",  // ASLUniversity — "FOOD / EAT in ASL"
            description = "Bring the fingertips of your dominant hand together (like pinching food) " +
                    "and tap them against your lips twice. The motion mimics bringing food to your mouth.",
            handshape = "Flat O (fingertips pinched)"
        ),

        TutorialItem(
            id = 9,
            word = "Water",
            emoji = "💧",
            youtubeId = "mB5n5RLR8To",  // ASLUniversity — "WATER in ASL"
            description = "Form the letter W with your dominant hand (extend index, middle, " +
                    "and ring fingers). Tap the index-finger side of your W handshape " +
                    "against your chin twice.",
            handshape = "W handshape"
        ),

        TutorialItem(
            id = 10,
            word = "Yes",
            emoji = "✅",
            youtubeId = "TJN5wuKqsW4",  // ASLUniversity — "YES / NO in ASL"
            description = "Make a fist (S handshape) with your dominant hand and bob it up " +
                    "and down at the wrist — mimicking a head nodding 'yes'. Keep the motion " +
                    "rhythmic and smooth.",
            handshape = "S (fist)"
        ),

        TutorialItem(
            id = 11,
            word = "No",
            emoji = "❌",
            youtubeId = "TJN5wuKqsW4",  // ASLUniversity — "YES / NO in ASL" (same video)
            description = "Extend your index and middle fingers together alongside your thumb. " +
                    "Snap them together quickly — like scissors closing or a mouth saying 'no'. " +
                    "You can do this once or twice firmly.",
            handshape = "Index + middle finger snapping to thumb"
        ),

        TutorialItem(
            id = 12,
            word = "All",
            emoji = "🌐",
            youtubeId = "QZQP_pD1KGc",  // ASLUniversity — "ALL / EVERY in ASL"
            description = "Hold your non-dominant hand out flat in front of you, palm facing down. " +
                    "With your dominant hand (also palm down), sweep it in a circular motion " +
                    "around the non-dominant hand and then land it on top of it — palm up.",
            handshape = "Open B (flat hands)"
        ),

        TutorialItem(
            id = 13,
            word = "Medicine",
            emoji = "💊",
            youtubeId = "0rGqP9_3D5A",  // ASLUniversity — "MEDICINE / DRUG in ASL"
            description = "Place your non-dominant hand out flat with palm facing up. " +
                    "With your dominant hand, extend your middle finger and rest its tip " +
                    "on the palm. Rock the dominant hand back and forth at the wrist " +
                    "like stirring or mixing — as if grinding medicine in a mortar.",
            handshape = "8 handshape (middle finger extended)"
        )
    )

    /** Build a YouTube embed URL from a video ID for use in a WebView. */
    fun buildEmbedUrl(youtubeId: String): String =
        "https://www.youtube.com/embed/$youtubeId?autoplay=1&rel=0&modestbranding=1&playsinline=1"

    /** Build a standard YouTube watch URL for opening in an external browser or Intent. */
    fun buildWatchUrl(youtubeId: String): String =
        "https://www.youtube.com/watch?v=$youtubeId"
}
