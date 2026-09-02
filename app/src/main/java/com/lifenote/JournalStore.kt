package com.lifenote

import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * CRUD over one .md file per entry, format contract in docs/06.
 * Files are the source of truth; parsing is lenient — malformed files
 * are surfaced as-is and never dropped.
 */
class JournalStore(internal val directory: File) {

    data class Meta(
        val id: String,
        val created: String,
        val updated: String,
        val device: String,
        val title: String,
        val deleted: Boolean
    )

    data class Entry(val meta: Meta, val body: String, val raw: String)

    sealed class WriteResult {
        object Ok : WriteResult()
        data class Conflict(val existingUpdated: String) : WriteResult()
        data class Invalid(val reason: String) : WriteResult()
    }

    private val dir get() = directory

    init { dir.mkdirs() }

    @Synchronized
    fun index(): List<Meta> {
        purgeOldTombstones()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.mapNotNull { parse(it.readText(Charsets.UTF_8))?.meta }
            ?.sortedByDescending { it.updated }
            ?: emptyList()
    }

    @Synchronized
    fun read(id: String): String? {
        val f = fileForId(id) ?: return null
        return f.readText(Charsets.UTF_8)
    }

    /**
     * Stores the raw file text verbatim. Last-write-wins on [updated];
     * a strictly newer existing copy rejects with Conflict (doc 05).
     */
    @Synchronized
    fun write(rawText: String, expectedId: String): WriteResult {
        return writeInternal(rawText, expectedId)
    }

    private fun writeInternal(rawText: String, expectedId: String): WriteResult {
        val entry = parse(rawText)
            ?: return WriteResult.Invalid("no front matter")
        if (entry.meta.id != expectedId) {
            return WriteResult.Invalid("id mismatch: path=$expectedId file=${entry.meta.id}")
        }
        val existingFile = fileForId(expectedId)
        if (existingFile != null) {
            val existing = parse(existingFile.readText(Charsets.UTF_8))
            if (existing != null && isNewer(existing.meta.updated, entry.meta.updated)) {
                return WriteResult.Conflict(existing.meta.updated)
            }
        }
        atomicWrite(existingFile ?: fileFor(entry), rawText)
        return WriteResult.Ok
    }

    /** Lenient parser: missing keys get defaults, unknown keys stay in raw text. */
    private fun parse(raw: String): Entry? {
        val m = Regex("^---\\n([\\s\\S]*?)\\n---\\n\\n?([\\s\\S]*)$").find(raw) ?: return null
        val meta = mutableMapOf<String, String>()
        m.groupValues[1].split('\n').forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) meta[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        val id = meta["id"]
            ?: return null // identity is the one non-negotiable field
        return Entry(
            meta = Meta(
                id = id,
                created = meta["created"] ?: "",
                updated = meta["updated"] ?: "",
                device = meta["device"] ?: "?",
                title = meta["title"] ?: "",
                deleted = meta["deleted"] == "true"
            ),
            body = m.groupValues[2].removeSuffix("\n"),
            raw = raw
        )
    }

    private fun fileForId(id: String): File? {
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.firstOrNull { f -> parse(f.readText(Charsets.UTF_8))?.meta?.id == id }
    }

    /** `YYYY-MM-DD_HHmmss_<id6>.md` from the entry's created instant (doc 06). */
    private fun fileFor(entry: Entry): File {
        val stamp = try {
            val created = OffsetDateTime.parse(entry.meta.created)
                .atZoneSameInstant(ZoneId.systemDefault())
            created.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
        } catch (_: Exception) {
            "0000-00-00_000000"
        }
        val suffix = entry.meta.id.substringAfterLast('-').takeLast(6)
        return File(dir, "${stamp}_${suffix}.md")
    }

    /** Temp file + rename — a crash must never half-save an entry. */
    private fun atomicWrite(target: File, text: String) {
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        try {
            Files.move(tmp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (e: Exception) {
            tmp.delete()
            throw IllegalStateException("atomic rename failed for ${target.name}", e)
        }
    }

    @Synchronized
    fun mergeFrom(staging: File, total: Int): ArchiveManager.ImportResult {
        var imported = 0
        staging.listFiles { file -> file.isFile && file.name.endsWith(".md") }?.forEach { file ->
            val raw = file.readText(Charsets.UTF_8)
            val entry = parse(raw)
            if (entry == null) {
                val target = uniqueMalformedFile(file.name, raw)
                if (target != null) { atomicWrite(target, raw); imported++ }
            } else {
                val existing = fileForId(entry.meta.id)
                val existingEntry = existing?.let { parse(it.readText(Charsets.UTF_8)) }
                if (existingEntry == null || isNewer(entry.meta.updated, existingEntry.meta.updated)) {
                    atomicWrite(existing ?: fileFor(entry), raw)
                    imported++
                }
            }
        }
        return ArchiveManager.ImportResult(imported, total - imported)
    }

    @Synchronized
    fun replaceWith(staging: File): Int {
        val incoming = File(dir.parentFile, ".journal-ready-${System.nanoTime()}")
        val backup = File(dir.parentFile, ".journal-old-${System.nanoTime()}")
        check(staging.renameTo(incoming)) { "Could not prepare replacement" }
        val count = incoming.listFiles { file -> file.isFile && file.name.endsWith(".md") }?.size ?: 0
        try {
            Files.move(dir.toPath(), backup.toPath(), ATOMIC_MOVE)
            try {
                Files.move(incoming.toPath(), dir.toPath(), ATOMIC_MOVE)
            } catch (e: Exception) {
                Files.move(backup.toPath(), dir.toPath(), ATOMIC_MOVE)
                throw e
            }
            backup.deleteRecursively()
            return count
        } finally {
            if (incoming.exists()) incoming.deleteRecursively()
        }
    }

    @Synchronized
    fun restoreRevision(raw: String, id: String, device: String): WriteResult {
        val entry = parse(raw) ?: return WriteResult.Invalid("invalid revision")
        if (entry.meta.id != id) return WriteResult.Invalid("revision id mismatch")
        val now = OffsetDateTime.now()
        val currentStamp = read(id)?.let { parse(it)?.meta?.updated }
            ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        val restoredStamp = if (currentStamp != null && currentStamp >= now) {
            currentStamp.plusNanos(1_000_000)
        } else now
        val lines = raw.lines().toMutableList()
        replaceFrontMatter(lines, "updated", restoredStamp.toString())
        replaceFrontMatter(lines, "device", device)
        atomicWrite(fileForId(id) ?: fileFor(entry), lines.joinToString("\n"))
        return WriteResult.Ok
    }

    fun metadata(raw: String): Meta? = parse(raw)?.meta

    @Synchronized
    fun hasId(id: String): Boolean = fileForId(id) != null

    private fun uniqueMalformedFile(name: String, raw: String): File? {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val direct = File(dir, safeName)
        if (!direct.exists()) return direct
        if (direct.readText(Charsets.UTF_8) == raw) return null
        val base = safeName.removeSuffix(".md")
        var number = 1
        while (File(dir, "${base}_import-$number.md").exists()) number++
        return File(dir, "${base}_import-$number.md")
    }

    private fun replaceFrontMatter(lines: MutableList<String>, key: String, value: String) {
        val index = lines.indexOfFirst { it.startsWith("$key:") }
        if (index >= 0) {
            lines[index] = "$key: $value"
        } else {
            val close = lines.withIndex().firstOrNull { it.index > 0 && it.value == "---" }?.index ?: -1
            if (close > 0) lines.add(close, "$key: $value")
        }
    }

    private fun isNewer(a: String, b: String): Boolean {
        // true when a is strictly newer than b; unparseable stamps lose
        val ta = runCatching { OffsetDateTime.parse(a).toInstant().toEpochMilli() }.getOrNull()
        val tb = runCatching { OffsetDateTime.parse(b).toInstant().toEpochMilli() }.getOrNull()
        if (ta != null && tb != null) return ta > tb
        return a > b
    }

    private fun purgeOldTombstones() {
        val cutoff = System.currentTimeMillis() - TOMBSTONE_DAYS * 24L * 60 * 60 * 1000
        dir.listFiles { f -> f.isFile && f.name.endsWith(".md") }?.forEach { f ->
            runCatching {
                val e = parse(f.readText(Charsets.UTF_8)) ?: return@forEach
                if (e.meta.deleted) {
                    val t = runCatching {
                        OffsetDateTime.parse(e.meta.updated).toInstant().toEpochMilli()
                    }.getOrNull() ?: return@forEach
                    if (t < cutoff) {
                        f.delete()
                        File(dir, ".history/${e.meta.id}").deleteRecursively()
                    }
                }
            }
        }
    }

    companion object {
        private const val TOMBSTONE_DAYS = 30L
    }
}
