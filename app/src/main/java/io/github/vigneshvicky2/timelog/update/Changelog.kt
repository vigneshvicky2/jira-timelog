package io.github.vigneshvicky2.timelog.update

/** Notes shown once after the app updates to a version. Newest first; keep in sync with GitHub releases. */
data class ChangelogEntry(val version: String, val title: String, val items: List<String>)

val CHANGELOG = listOf(
    ChangelogEntry(
        "1.3.0", "A fresh new look",
        listOf(
            "New Stint design: charcoal and cream, bold numbers, cobalt for actions.",
            "Home shows today's ring, tickets that need attention, and your sprint with time left on each.",
            "Move a To Do ticket to In Progress right from Stint.",
            "Pending entries are tagged when they're over estimate or on a To Do ticket.",
            "Time buttons sit in a grid, so nothing gets cut off.",
        ),
    ),
    ChangelogEntry(
        "1.2.1", "Cleaner ticket cards",
        listOf(
            "Ticket progress now uses the full width of the card, so long times no longer wrap.",
            "Logged time reads as \"25h 30m of 21h 15m\" on one line.",
        ),
    ),
    ChangelogEntry(
        "1.2.0", "Stint: progress at a glance",
        listOf(
            "Timelog is now Stint, with a new tally-mark icon.",
            "Every sprint ticket shows its status, time left and a progress bar.",
            "Long-press a ticket to see logged vs estimate, remaining time and warnings when it's over-logged or still To Do.",
            "You're warned before logging time that would take a ticket past its estimate.",
            "In-app updates: new versions download and install from inside the app.",
        ),
    ),
    ChangelogEntry(
        "1.1.0", "Ticket previews",
        listOf("Long-press a ticket to preview its description."),
    ),
)
