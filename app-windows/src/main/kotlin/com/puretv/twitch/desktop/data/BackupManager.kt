package com.puretv.twitch.desktop.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One-file backup of everything you'd miss on a new PC: settings, follows,
 * browsing prefs (saved searches, pins, history, chat filters) and
 * continue-watching positions. Sign-in tokens are deliberately left out; they
 * are encrypted to this machine and you just sign in again.
 *
 * Restoring can't overwrite the files while the app runs, because each store
 * holds its data in memory and would write it back. So a restore is staged in
 * `restore-pending/` and moved into place at the next launch, before any store
 * loads (see main.kt).
 */
class BackupManager(
    private val dir: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch"),
) {
    private val json = Json { prettyPrint = true }
    private val pendingDir get() = File(dir, PENDING_DIR)

    /** Writes a backup to [target]. Returns how many data files it contains. */
    fun export(target: File): Int {
        val present = FILES.map { File(dir, it) }.filter { it.isFile }
        val body = buildJsonObject {
            put("app", APP_TAG)
            put("format", FORMAT_VERSION)
            put("createdAt", System.currentTimeMillis())
            put(
                "files",
                buildJsonObject { present.forEach { f -> put(f.name, f.readText()) } },
            )
        }
        target.parentFile?.mkdirs()
        target.writeText(json.encodeToString(JsonObject.serializer(), body))
        return present.size
    }

    /**
     * Validates [source] and stages its files for the next launch. Only known
     * file names are accepted, and each must itself be valid JSON, so a bad or
     * hand-edited backup can't drop arbitrary files into the data folder.
     */
    fun stageRestore(source: File): Result<Int> = runCatching {
        val root = Json.parseToJsonElement(source.readText()) as? JsonObject
            ?: error("That file isn't a PureTV backup.")
        require((root["app"] as? JsonPrimitive)?.contentOrNull == APP_TAG) { "That file isn't a PureTV backup." }
        val files = root["files"] as? JsonObject ?: error("The backup has no data in it.")
        val accepted = files.entries.mapNotNull { (name, value) ->
            if (name !in FILES) return@mapNotNull null
            val text = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return@mapNotNull null
            name to text
        }
        require(accepted.isNotEmpty()) { "The backup has no data in it." }
        pendingDir.deleteRecursively()
        pendingDir.mkdirs()
        accepted.forEach { (name, text) -> File(pendingDir, name).writeText(text) }
        accepted.size
    }

    val hasPendingRestore: Boolean get() = pendingDir.isDirectory && (pendingDir.listFiles()?.isNotEmpty() == true)

    /** Moves staged files into place. Call before any store is created. Never throws. */
    fun applyPendingRestore() {
        runCatching {
            val staged = pendingDir.listFiles()?.filter { it.isFile && it.name in FILES }.orEmpty()
            staged.forEach { f ->
                Files.move(f.toPath(), File(dir, f.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            pendingDir.deleteRecursively()
        }
    }

    companion object {
        const val APP_TAG = "PureTV for Twitch backup"
        const val FORMAT_VERSION = 1
        const val PENDING_DIR = "restore-pending"
        val FILES = listOf("settings.json", "following.json", ViewPrefsStore.FILE_NAME, "watch-progress.json")
    }
}
