# Timelog

A Material You Android companion app for logging Jira Cloud time, built for people who log two or three days at once.

Pick the days, tap time onto your sprint tickets and meetings, and send everything to Jira with one **Log all**.

## Features

- **Several days at once.** Select any days from the last four weeks. Each day shows an animated 8-hour ring with time remaining (or how far over you are; going past 8h is allowed).
- **Add time in one screen.** Every ticket assigned to you in the active sprint is listed with inline chips: **Custom** and presets from **15m** to **4h**, plus **Fill day**. A pinned header shows how much of the day is left while you scroll. You can copy a day's tickets to the other selected days.
- **Any ticket.** Search all of Jira by key or text to log against tickets outside your sprint.
- **Meetings as suggestions.** Meetings are read from the phone's calendar (Google Calendar syncs there), so there's no Google sign-in. Tap a meeting, choose its ticket, and optionally adjust the duration or add a description. Your last choice for each meeting is shown first.
- **Leave.** The app finds your sprint's shared "Time off" ticket automatically. Log a full day, half day or custom time, with an optional reason, for one day or all selected days.
- **Calendar strip.** Weekends and leave days have their own colours.
- **Daily reminder.** A notification at a time you choose, optionally on weekdays only, with 30 rotating messages.
- **Review before sending.** Pending entries can be edited or deleted. Failed entries stay in the list with the reason.
- **Smooth animations throughout:** press feedback, rolling numbers, staggered entrances, and confetti when everything is logged.

## Setup

1. Install the APK from [Releases](../../releases).
2. Create an API token at <https://id.atlassian.com/manage-profile/security/api-tokens>.
3. Open Timelog and enter your Jira site (e.g. `your-team.atlassian.net`), your Atlassian email, and the API token.
4. Allow calendar access to see meetings. If none show up, turn on **Calendar sync** for your work Google account in Android settings.

## Privacy

- The API token is encrypted on the device with an Android Keystore key.
- The app talks only to your Jira site. Calendar events are read on the device and never uploaded; only the worklogs you submit go to Jira.

## Build

Requirements: JDK 17+ and the Android SDK (compileSdk 36).

```sh
./gradlew assembleRelease
```

Release builds are signed with the key described by the properties file at `$TIMELOG_SIGNING` (default `~/.android-keys/timelog.properties`, containing `storeFile`, `storePassword`, `keyAlias` and `keyPassword`). Without one, the local debug key is used.

Tech: Kotlin, Jetpack Compose, Material 3 (dynamic color), Jira Cloud REST API v3, Android CalendarContract, AlarmManager. Minimum Android 12.
