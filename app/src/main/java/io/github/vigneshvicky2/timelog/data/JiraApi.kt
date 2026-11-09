package io.github.vigneshvicky2.timelog.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class JiraException(val code: Int, message: String) : Exception(message)

/** Minimal Jira Cloud REST v3 client (basic auth with email + API token). */
class JiraApi(private val site: String, email: String, token: String) {
    private val auth = "Basic " + Base64.encodeToString("$email:$token".toByteArray(), Base64.NO_WRAP)

    suspend fun myself(): Pair<String, String> = io {
        val o = JSONObject(request("GET", "/rest/api/3/myself"))
        o.getString("accountId") to o.optString("displayName")
    }

    suspend fun searchIssues(jql: String, limit: Int = 100): List<Issue> = io {
        val out = mutableListOf<Issue>()
        var next: String? = null
        do {
            val body = JSONObject()
                .put("jql", jql)
                .put("fields", JSONArray().put("summary"))
                .put("maxResults", minOf(100, limit - out.size))
            next?.let { body.put("nextPageToken", it) }
            val res = JSONObject(request("POST", "/rest/api/3/search/jql", body))
            val arr = res.optJSONArray("issues") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                out += Issue(it.getString("key"), it.optJSONObject("fields")?.optString("summary").orEmpty())
            }
            next = res.optString("nextPageToken").takeIf { it.isNotEmpty() && !res.optBoolean("isLast", true) }
        } while (next != null && out.size < limit)
        out
    }

    /** All of the user's worklogs between [from] and [to] (inclusive), in the phone's time zone. */
    suspend fun worklogs(accountId: String, from: LocalDate, to: LocalDate, zone: ZoneId): List<Worklog> {
        val issues = searchIssues(
            "worklogAuthor = currentUser() AND worklogDate >= \"$from\" AND worklogDate <= \"$to\"", 300
        )
        val after = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val before = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val gate = Semaphore(6)
        return coroutineScope {
            issues.map { issue ->
                async { gate.withPermit { issueWorklogs(issue, accountId, after, before, zone) } }
            }.awaitAll().flatten()
        }.filter { it.date in from..to }
    }

    private suspend fun issueWorklogs(issue: Issue, accountId: String, after: Long, before: Long, zone: ZoneId) = io {
        val res = JSONObject(
            request("GET", "/rest/api/3/issue/${issue.key}/worklog?startedAfter=$after&startedBefore=$before&maxResults=5000")
        )
        val arr = res.optJSONArray("worklogs") ?: JSONArray()
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optJSONObject("author")?.optString("accountId") == accountId }
            .map { w ->
                val started = OffsetDateTime.parse(w.getString("started"), STARTED).atZoneSameInstant(zone)
                Worklog(
                    id = w.getString("id"),
                    issueKey = issue.key,
                    issueSummary = issue.summary,
                    date = started.toLocalDate(),
                    start = started.toLocalTime(),
                    seconds = w.optLong("timeSpentSeconds"),
                    comment = adfText(w.opt("comment")).replace(Regex("\\s+"), " ").trim(),
                )
            }
    }

    private var sprintField: String? = null

    /** Jira Cloud stores sprints in a custom field whose id differs per site; look it up once. */
    private fun findSprintField(): String? {
        sprintField?.let { return it }
        val arr = JSONArray(request("GET", "/rest/api/3/field"))
        for (i in 0 until arr.length()) {
            val f = arr.getJSONObject(i)
            if (f.optJSONObject("schema")?.optString("custom") == "com.pyxis.greenhopper.jira:gh-sprint") {
                sprintField = f.getString("id")
                return sprintField
            }
        }
        return null
    }

    /** Tickets assigned to the user in the currently active sprint(s), with the sprint name. */
    suspend fun currentSprintIssues(): List<Issue> = io {
        val field = runCatching { findSprintField() }.getOrNull()
        val fields = JSONArray().put("summary").also { a -> field?.let { a.put(it) } }
        val body = JSONObject()
            .put("jql", "sprint in openSprints() AND assignee = currentUser() ORDER BY updated DESC")
            .put("fields", fields)
            .put("maxResults", 100)
        val res = JSONObject(request("POST", "/rest/api/3/search/jql", body))
        val arr = res.optJSONArray("issues") ?: JSONArray()
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            val f = o.optJSONObject("fields")
            val sprints = field?.let { f?.optJSONArray(it) }
            var name: String? = null
            if (sprints != null) for (j in 0 until sprints.length()) {
                val s = sprints.optJSONObject(j) ?: continue
                if (s.optString("state") == "active") name = s.optString("name")
            }
            Issue(o.getString("key"), f?.optString("summary").orEmpty(), name)
        }
    }

    /**
     * Each sprint has one unassigned "Time off" ticket that everyone logs leave against.
     * Prefer the one in the open sprint; fall back to the newest one that isn't done.
     */
    suspend fun timeOffIssue(projects: Collection<String>): Issue? {
        val scope = if (projects.isEmpty()) "" else "project in (${projects.joinToString(",")}) AND "
        val match = "(summary ~ \"\\\"time off\\\"\" OR summary ~ \"timeoff\")"
        return runCatching { searchIssues("${scope}sprint in openSprints() AND $match ORDER BY created DESC", 1) }
            .getOrNull()?.firstOrNull()
            ?: runCatching { searchIssues("${scope}$match AND statusCategory != Done ORDER BY created DESC", 1) }
                .getOrNull()?.firstOrNull()
    }

    suspend fun pickIssues(query: String): List<Issue> = io {
        val q = URLEncoder.encode(query, "UTF-8")
        val res = JSONObject(request("GET", "/rest/api/3/issue/picker?query=$q&showSubTasks=true&showSubTaskParent=true"))
        val out = mutableListOf<Issue>()
        val sections = res.optJSONArray("sections") ?: JSONArray()
        for (s in 0 until sections.length()) {
            val issues = sections.getJSONObject(s).optJSONArray("issues") ?: continue
            for (i in 0 until issues.length()) {
                val o = issues.getJSONObject(i)
                out += Issue(o.getString("key"), o.optString("summaryText"))
            }
        }
        out.distinctBy { it.key }
    }

    /** Full details for the long-press preview: status, people and the formatted description. */
    suspend fun issueDetails(key: String): IssueDetails = io {
        val o = JSONObject(
            request("GET", "/rest/api/3/issue/$key?fields=summary,description,status,priority,issuetype,assignee,reporter,labels")
        )
        val f = o.optJSONObject("fields") ?: JSONObject()
        val status = f.optJSONObject("status")
        val labels = f.optJSONArray("labels")
        IssueDetails(
            key = o.getString("key"),
            summary = f.optString("summary"),
            type = f.optJSONObject("issuetype")?.optString("name").orEmpty(),
            status = status?.optString("name").orEmpty(),
            statusCategory = status?.optJSONObject("statusCategory")?.optString("key").orEmpty(),
            priority = f.optJSONObject("priority")?.optString("name")?.takeIf { it.isNotBlank() },
            assignee = f.optJSONObject("assignee")?.optString("displayName")?.takeIf { it.isNotBlank() },
            reporter = f.optJSONObject("reporter")?.optString("displayName")?.takeIf { it.isNotBlank() },
            labels = if (labels == null) emptyList() else (0 until labels.length()).map { labels.getString(it) },
            description = f.optJSONObject("description")?.let { adfBlocks(it) } ?: emptyList(),
            url = "https://$site/browse/${o.getString("key")}",
        )
    }

    suspend fun issue(key: String): Issue = io {
        val o = JSONObject(request("GET", "/rest/api/3/issue/$key?fields=summary"))
        Issue(o.getString("key"), o.optJSONObject("fields")?.optString("summary").orEmpty())
    }

    suspend fun addWorklog(d: Draft, zone: ZoneId) = io {
        val started = d.date.atTime(d.start).atZone(zone).format(STARTED)
        val body = JSONObject()
            .put("timeSpentSeconds", d.seconds)
            .put("started", started)
        if (d.comment.isNotBlank()) body.put("comment", adf(d.comment))
        request("POST", "/rest/api/3/issue/${d.issue.key}/worklog", body)
        Unit
    }

    suspend fun deleteWorklog(issueKey: String, id: String) = io {
        request("DELETE", "/rest/api/3/issue/$issueKey/worklog/$id")
        Unit
    }

    // --- plumbing ---

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun request(method: String, path: String, body: JSONObject? = null): String {
        val conn = (URL("https://$site$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", auth)
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw JiraException(code, errorMessage(code, text))
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(code: Int, text: String): String {
        if (code == 401) return "That API key didn't work. Check it and try again."
        if (code == 403) return "Jira says you don't have permission for this."
        val parsed = runCatching {
            val o = JSONObject(text)
            val msgs = mutableListOf<String>()
            o.optJSONArray("errorMessages")?.let { a -> for (i in 0 until a.length()) msgs += a.getString(i) }
            o.optJSONObject("errors")?.let { e -> e.keys().forEach { k -> msgs += e.getString(k) } }
            msgs.joinToString(" ")
        }.getOrNull()
        return parsed?.takeIf { it.isNotBlank() } ?: "Jira returned an error ($code)."
    }

    companion object {
        val STARTED: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

        private fun adf(text: String) = JSONObject()
            .put("type", "doc").put("version", 1)
            .put("content", JSONArray().apply {
                text.trim().lines().forEach { line ->
                    put(JSONObject().put("type", "paragraph").put("content", JSONArray().apply {
                        if (line.isNotBlank()) put(JSONObject().put("type", "text").put("text", line))
                    }))
                }
            })

        // ---- ADF (Atlassian Document Format) -> simple blocks ----

        private fun JSONObject.children(): List<JSONObject> {
            val a = optJSONArray("content") ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }

        private fun hasText(s: List<Span>) = s.any { it.text.isNotBlank() }

        /** Inline nodes -> styled spans. */
        private fun spans(node: JSONObject): List<Span> = node.children().flatMap { n ->
            val attrs = n.optJSONObject("attrs")
            when (n.optString("type")) {
                "text" -> {
                    val marks = n.optJSONArray("marks")
                    val types = if (marks == null) emptyList() else (0 until marks.length()).map { marks.getJSONObject(it) }
                    fun has(t: String) = types.any { it.optString("type") == t }
                    listOf(
                        Span(
                            text = n.optString("text"),
                            bold = has("strong"),
                            italic = has("em"),
                            code = has("code"),
                            strike = has("strike"),
                            link = types.firstOrNull { it.optString("type") == "link" }?.optJSONObject("attrs")?.optString("href"),
                        )
                    )
                }
                "hardBreak" -> listOf(Span("\n"))
                "mention" -> listOf(Span(attrs?.optString("text")?.ifBlank { null } ?: "@someone", bold = true))
                "emoji" -> listOf(Span(attrs?.let { it.optString("text").ifBlank { it.optString("shortName") } }.orEmpty()))
                "inlineCard", "blockCard" -> attrs?.optString("url")?.takeIf { it.isNotBlank() }?.let { listOf(Span(it, link = it)) } ?: emptyList()
                "status" -> listOf(Span(attrs?.optString("text").orEmpty().uppercase(), bold = true))
                else -> spans(n)
            }
        }

        private fun plain(node: JSONObject): String = spans(node).joinToString("") { it.text }

        fun adfBlocks(doc: JSONObject): List<DescBlock> {
            val out = mutableListOf<DescBlock>()
            fun walk(node: JSONObject, depth: Int) {
                for (n in node.children()) when (n.optString("type")) {
                    "paragraph" -> spans(n).takeIf(::hasText)?.let { out += DescBlock.Paragraph(it) }
                    "heading" -> out += DescBlock.Heading(spans(n), n.optJSONObject("attrs")?.optInt("level", 3) ?: 3)
                    "bulletList", "orderedList" -> {
                        val ordered = n.optString("type") == "orderedList"
                        var i = n.optJSONObject("attrs")?.optInt("order", 1) ?: 1
                        for (item in n.children()) {
                            val marker = if (ordered) "${i++}." else if (depth == 0) "•" else "◦"
                            val parts = item.children()
                            val first = parts.firstOrNull { it.optString("type") == "paragraph" }
                            out += DescBlock.Item(first?.let { spans(it) } ?: emptyList(), marker, depth)
                            for (p in parts) if (p !== first) walk(JSONObject().put("content", JSONArray().put(p)), depth + 1)
                        }
                    }
                    "taskList" -> for (item in n.children()) {
                        val done = item.optJSONObject("attrs")?.optString("state") == "DONE"
                        out += DescBlock.Item(spans(item), if (done) "☑" else "☐", depth)
                    }
                    "codeBlock" -> out += DescBlock.Code(plain(n))
                    "blockquote", "panel" -> for (c in n.children()) spans(c).takeIf(::hasText)?.let { out += DescBlock.Quote(it) }
                    "rule" -> out += DescBlock.Rule
                    "mediaSingle", "mediaGroup" -> out += DescBlock.Paragraph(listOf(Span("📎 Attachment: open in Jira to view", italic = true)))
                    "table" -> for (row in n.children()) {
                        val cells = row.children().joinToString("  |  ") { cell -> cell.children().joinToString(" ") { plain(it) }.trim() }
                        if (cells.isNotBlank()) out += DescBlock.Paragraph(listOf(Span(cells)))
                    }
                    "expand", "nestedExpand", "layoutSection", "layoutColumn" -> walk(n, depth)
                    else -> spans(n).takeIf(::hasText)?.let { out += DescBlock.Paragraph(it) }
                }
            }
            walk(doc, 0)
            return out
        }

        private fun adfText(node: Any?): String = when (node) {
            is JSONObject -> if (node.optString("type") == "text") node.optString("text") else adfText(node.optJSONArray("content"))
            is JSONArray -> (0 until node.length()).joinToString(" ") { adfText(node.get(it)) }
            is String -> node
            else -> ""
        }
    }
}
