package com.lifenote

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class HistoryStore(private val root: File, private val journal: JournalStore) {
    data class Revision(val key: String, val updated: String, val title: String)

    init { root.mkdirs() }

    @Synchronized
    fun snapshot(id: String, raw: String): Boolean {
        val folder = folderFor(id) ?: return false
        folder.mkdirs()
        val latest = revisionFiles(folder).lastOrNull()
        if (latest?.readText(Charsets.UTF_8) == raw) return false
        var stamp = System.currentTimeMillis()
        while (File(folder, "$stamp.md").exists()) stamp++
        atomicWrite(File(folder, "$stamp.md"), raw)
        prune(folder)
        return true
    }

    @Synchronized
    fun list(id: String): List<Revision> {
        val folder = folderFor(id) ?: return emptyList()
        return revisionFiles(folder).asReversed().mapNotNull { file ->
            val meta = journal.metadata(file.readText(Charsets.UTF_8)) ?: return@mapNotNull null
            Revision(file.nameWithoutExtension, meta.updated, meta.title)
        }
    }

    @Synchronized
    fun read(id: String, key: String): String? {
        if (!key.matches(Regex("\\d+"))) return null
        val folder = folderFor(id) ?: return null
        val file = File(folder, "$key.md")
        if (!file.isFile || file.canonicalFile.parentFile != folder.canonicalFile) return null
        return file.readText(Charsets.UTF_8)
    }

    private fun folderFor(id: String): File? {
        if (!id.matches(Regex("[A-Za-z0-9._-]+"))) return null
        val folder = File(root, id)
        return if (folder.canonicalFile.parentFile == root.canonicalFile) folder else null
    }

    private fun revisionFiles(folder: File): List<File> =
        folder.listFiles { file -> file.isFile && file.name.matches(Regex("\\d+\\.md")) }
            ?.sortedBy { it.nameWithoutExtension.toLongOrNull() ?: 0L } ?: emptyList()

    private fun prune(folder: File) {
        val files = revisionFiles(folder)
        files.take((files.size - MAX_REVISIONS).coerceAtLeast(0)).forEach { it.delete() }
    }

    private fun atomicWrite(target: File, text: String) {
        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.writeText(text, Charsets.UTF_8)
        try {
            Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (error: Exception) {
            temp.delete()
            throw IllegalStateException("Could not save revision", error)
        }
    }

    companion object { private const val MAX_REVISIONS = 20 }
}
