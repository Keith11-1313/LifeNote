package com.lifenote

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ArchiveManager(private val filesDir: File, private val store: JournalStore) {
    enum class ImportMode { MERGE, REPLACE }
    data class ImportResult(val imported: Int, val kept: Int)

    fun exportTo(output: OutputStream): Int {
        val files = store.directory.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".md") }
            .sortedBy { it.relativeTo(store.directory).invariantSeparatorsPath }
            .toList()
        ZipOutputStream(output.buffered()).use { zip ->
            files.forEach { file ->
                val relative = file.relativeTo(store.directory).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry("journal/$relative"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return files.count { it.parentFile == store.directory }
    }

    fun importFrom(input: InputStream, mode: ImportMode): ImportResult {
        val staging = File(filesDir, ".journal-import-${UUID.randomUUID()}")
        check(staging.mkdirs()) { "Could not prepare import" }
        try {
            val extracted = extract(input, staging)
            pruneHistory(staging)
            return when (mode) {
                ImportMode.MERGE -> {
                    val result = store.mergeFrom(staging, extracted)
                    mergeHistory(File(staging, ".history"))
                    result
                }
                ImportMode.REPLACE -> ImportResult(store.replaceWith(staging), 0)
            }
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun extract(input: InputStream, staging: File): Int {
        var fileCount = 0
        var entryCount = 0
        var totalBytes = 0L
        val buffered = input.buffered()
        buffered.mark(4)
        val signature = ByteArray(4)
        require(buffered.read(signature) == 4 && isZipSignature(signature)) { "Selected file is not a valid zip backup" }
        buffered.reset()
        val names = mutableSetOf<String>()
        ZipInputStream(buffered).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val parts = entry.name.replace('\\', '/').split('/')
                    val isEntry = parts.size == 2 && parts[0] == "journal" &&
                        parts[1].endsWith(".md") && parts[1] != ".history"
                    val isHistory = parts.size == 4 && parts[0] == "journal" && parts[1] == ".history" &&
                        parts[2].matches(Regex("[A-Za-z0-9._-]+")) && parts[3].matches(Regex("\\d+\\.md"))
                    require(isEntry || isHistory) {
                        "Backup has an unexpected file: ${entry.name}"
                    }
                    val relative = parts.drop(1).joinToString("/")
                    require(names.add(relative)) { "Backup contains duplicate file: $relative" }
                    require(++fileCount <= MAX_FILES) { "Backup contains too many files" }
                    if (isEntry) entryCount++
                    val target = File(staging, relative)
                    require(target.canonicalFile.toPath().startsWith(staging.canonicalFile.toPath())) { "Unsafe backup path" }
                    check(target.parentFile?.mkdirs() != false || target.parentFile?.isDirectory == true) {
                        "Could not prepare backup file"
                    }
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            require(totalBytes <= MAX_BYTES) { "Backup is too large" }
                            output.write(buffer, 0, read)
                        }
                    }
                    if (isHistory) {
                        val meta = store.metadata(target.readText(Charsets.UTF_8))
                        require(meta?.id == parts[2]) { "Backup contains invalid history" }
                    }
                }
                zip.closeEntry()
            }
        }
        return entryCount
    }

    private fun mergeHistory(stagedRoot: File) {
        if (!stagedRoot.isDirectory) return
        val liveRoot = File(store.directory, ".history")
        val activeIds = store.index().map { it.id }.toSet()
        stagedRoot.walkTopDown().filter { it.isFile }.forEach { source ->
            val relative = source.relativeTo(stagedRoot)
            val id = relative.invariantSeparatorsPath.substringBefore('/')
            if (id !in activeIds) return@forEach
            val target = File(liveRoot, relative.path)
            if (!target.exists()) {
                check(target.parentFile?.mkdirs() != false || target.parentFile?.isDirectory == true) {
                    "Could not prepare history folder"
                }
                Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE)
            }
        }
        pruneHistory(store.directory)
    }

    private fun pruneHistory(journalRoot: File) {
        val historyRoot = File(journalRoot, ".history")
        historyRoot.listFiles { file -> file.isDirectory }?.forEach { folder ->
            val revisions = folder.listFiles { file ->
                file.isFile && file.name.matches(Regex("\\d+\\.md"))
            }?.sortedByDescending { it.nameWithoutExtension.toLongOrNull() ?: 0L } ?: emptyList()
            revisions.drop(MAX_REVISIONS).forEach { it.delete() }
        }
    }

    private fun isZipSignature(bytes: ByteArray): Boolean =
        bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
            ((bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) ||
             (bytes[2] == 0x05.toByte() && bytes[3] == 0x06.toByte()))

    companion object {
        private const val MAX_FILES = 20_000
        private const val MAX_BYTES = 100L * 1024 * 1024
        private const val MAX_REVISIONS = 20
    }
}
