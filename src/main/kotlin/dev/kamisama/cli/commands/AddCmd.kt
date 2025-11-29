package dev.kamisama.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.index.Index
import dev.kamisama.core.objects.DeltaStore
import dev.kamisama.core.objects.FsObjectStore
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

/**
 * Stages files for the next commit with optional delta compression.
 */
class AddCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "add") {
    private val inputs by argument(name = "path").multiple()

    override fun help(context: Context) = "Stage files for commit"

    override fun run() {
        val repo = repoProvider()

        if (inputs.isEmpty()) {
            echo("Nothing specified, nothing added.")
            return
        }

        val root = repo.root.toAbsolutePath().normalize()
        val meta = repo.meta.toAbsolutePath().normalize()

        val filesToAdd = mutableSetOf<Path>()

        for (raw in inputs) {
            val p =
                if (Paths.get(raw).isAbsolute) {
                    Paths.get(raw).toAbsolutePath().normalize()
                } else {
                    root.resolve(raw).toAbsolutePath().normalize()
                }

            if (raw.contains('*') || raw.contains('?')) {
                val matcher = root.fileSystem.getPathMatcher("glob:$raw")
                Files
                    .walk(root)
                    .asSequence()
                    .filter { it.isRegularFile() && matcher.matches(root.relativize(it)) }
                    .filter { !it.startsWith(meta) }
                    .forEach { filesToAdd.add(it) }
                continue
            }

            if (raw == ".") {
                Files
                    .walk(root)
                    .asSequence()
                    .filter { it.isRegularFile() && !it.startsWith(meta) }
                    .forEach { filesToAdd.add(it) }
                continue
            }

            if (Files.isDirectory(p)) {
                Files
                    .walk(p)
                    .asSequence()
                    .filter { it.isRegularFile() }
                    .filter { it.startsWith(root) && !it.startsWith(meta) }
                    .forEach { filesToAdd.add(it) }
                continue
            }

            if (Files.isRegularFile(p)) {
                if (!p.startsWith(meta)) {
                    filesToAdd.add(p)
                } else {
                    echo("skip: $raw (internal .timetree directory)")
                }
            } else {
                echo("skip: $raw (not found or not a regular file)")
            }
        }

        val currentIndex = Index.load(repo)

        val added = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        var deltasUsed = 0

        for (abs in filesToAdd) {
            if (!abs.startsWith(root)) {
                echo("warning: '$abs' is outside repository")
                continue
            }

            if (abs.startsWith(meta)) {
                continue
            }

            val rel = root.relativize(abs).toString().replace(File.separatorChar, '/')

            // Use delta compression for files >= 64KB when a previous version exists
            val existingId = currentIndex[rel]
            val (id, usedDelta) =
                if (existingId != null && Files.size(abs) >= 64 * 1024) {
                    DeltaStore.storeBlobWithDelta(repo, abs, existingId)
                } else {
                    Pair(FsObjectStore.writeBlob(repo, abs), false)
                }

            if (usedDelta) deltasUsed++

            when {
                existingId == null -> {
                    // New file - not previously in index
                    Index.update(repo, rel, id)
                    added.add(rel)
                }

                existingId != id -> {
                    // File exists in index but content changed
                    Index.update(repo, rel, id)
                    updated.add(rel)
                }

                else -> {
                    // File already staged with identical content
                    unchanged.add(rel)
                }
            }
        }

        if (added.isNotEmpty()) {
            added.sorted().forEach { echo("add '$it'") }
        }

        if (updated.isNotEmpty()) {
            updated.sorted().forEach { echo("update '$it'") }
        }

        if (unchanged.isNotEmpty() && (added.isEmpty() && updated.isEmpty())) {
            echo("All ${unchanged.size} file(s) already staged and up-to-date")
        }

        val totalChanged = added.size + updated.size
        if (totalChanged == 0 && filesToAdd.isEmpty()) {
            echo("Nothing specified, nothing added.")
        } else if (totalChanged > 0) {
            echo("Staged $totalChanged file(s) for commit")
            if (deltasUsed > 0) {
                echo("  ($deltasUsed stored as delta)")
            }
        }
    }
}
