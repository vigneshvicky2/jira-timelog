package io.github.vigneshvicky2.timelog.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * On-device copies of what Stint shows, so it opens instantly and pending entries survive the
 * app being closed. Drafts are the user's unsent work; the rest is a snapshot of Jira that is
 * replaced on every refresh. Nothing here leaves the device.
 */
class Cache(context: Context) {
    private val dir = File(context.filesDir, "cache").apply { mkdirs() }
    private val draftsFile = File(dir, "drafts.json")
    private val snapshotFile = File(dir, "snapshot.json")

    data class Snapshot(
        val worklogs: List<Worklog>,
        val suggestions: List<Issue>,
        val sprintName: String?,
        val timeOff: Issue?,
    )

    fun loadDrafts(): List<Draft> = read(draftsFile) { o ->
        o.optJSONArray("drafts").objects().mapNotNull { runCatching { draft(it) }.getOrNull() }
    } ?: emptyList()

    fun saveDrafts(drafts: List<Draft>) =
        write(draftsFile, JSONObject().put("drafts", JSONArray(drafts.map { draftJson(it) })))

    fun loadSnapshot(): Snapshot? = read(snapshotFile) { o ->
        Snapshot(
            worklogs = o.optJSONArray("worklogs").objects().mapNotNull { runCatching { worklog(it) }.getOrNull() },
            suggestions = o.optJSONArray("suggestions").objects().map { issue(it) },
            sprintName = o.optString("sprintName").ifBlank { null },
            timeOff = o.optJSONObject("timeOff")?.let { issue(it) },
        )
    }

    fun saveSnapshot(s: Snapshot) = write(
        snapshotFile,
        JSONObject()
            .put("worklogs", JSONArray(s.worklogs.map { worklogJson(it) }))
            .put("suggestions", JSONArray(s.suggestions.map { issueJson(it) }))
            .put("sprintName", s.sprintName ?: "")
            .apply { s.timeOff?.let { put("timeOff", issueJson(it)) } },
    )

    fun clear() {
        draftsFile.delete()
        snapshotFile.delete()
    }

    // ---------- json ----------

    private fun <T> read(f: File, parse: (JSONObject) -> T): T? =
        if (!f.exists()) null else runCatching { parse(JSONObject(f.readText())) }.getOrNull()

    /** Writes via a temp file so a crash mid-write never leaves a half-written cache. */
    private fun write(f: File, o: JSONObject) {
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(f)
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    companion object {
        fun issueJson(i: Issue): JSONObject = JSONObject()
            .put("key", i.key).put("summary", i.summary).put("sprint", i.sprint ?: "")
            .put("status", i.status).put("statusCategory", i.statusCategory)
            .apply {
                i.tracking?.let { t ->
                    put("tracking", JSONObject().put("spent", t.spent)
                        .apply { t.estimate?.let { put("estimate", it) }; t.remaining?.let { put("remaining", it) } })
                }
            }

        fun issue(o: JSONObject) = Issue(
            key = o.getString("key"),
            summary = o.optString("summary"),
            sprint = o.optString("sprint").ifBlank { null },
            status = o.optString("status"),
            statusCategory = o.optString("statusCategory"),
            tracking = o.optJSONObject("tracking")?.let { t ->
                TimeTracking(
                    estimate = if (t.has("estimate")) t.getLong("estimate") else null,
                    remaining = if (t.has("remaining")) t.getLong("remaining") else null,
                    spent = t.optLong("spent"),
                )
            },
        )

        private fun draftJson(d: Draft) = JSONObject()
            .put("id", d.id).put("date", d.date.toString()).put("issue", issueJson(d.issue))
            .put("seconds", d.seconds).put("start", d.start.toString()).put("comment", d.comment)
            .apply { d.meetingKey?.let { put("meetingKey", it) }; d.error?.let { put("error", it) } }

        private fun draft(o: JSONObject) = Draft(
            id = o.getLong("id"),
            date = LocalDate.parse(o.getString("date")),
            issue = issue(o.getJSONObject("issue")),
            seconds = o.getLong("seconds"),
            start = LocalTime.parse(o.getString("start")),
            comment = o.optString("comment"),
            meetingKey = o.optString("meetingKey").ifBlank { null },
            error = o.optString("error").ifBlank { null },
        )

        private fun worklogJson(w: Worklog) = JSONObject()
            .put("id", w.id).put("issueKey", w.issueKey).put("issueSummary", w.issueSummary)
            .put("date", w.date.toString()).put("start", w.start.toString())
            .put("seconds", w.seconds).put("comment", w.comment)

        private fun worklog(o: JSONObject) = Worklog(
            id = o.getString("id"),
            issueKey = o.getString("issueKey"),
            issueSummary = o.optString("issueSummary"),
            date = LocalDate.parse(o.getString("date")),
            start = LocalTime.parse(o.getString("start")),
            seconds = o.getLong("seconds"),
            comment = o.optString("comment"),
        )
    }
}
