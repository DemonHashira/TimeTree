package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.index.Index
import dev.kamisama.core.objects.FsObjectStore
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

class AddCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "add") {
    private val inputs by argument(name = "path").multiple()

    override fun help(context: Context) = "Add file contents to the index"

    override fun run() {
        val repo = repoProvider()

        if (inputs.isEmpty()) {
            echo("Nothing specified, nothing added.")
            return
        }

        val root = repo.root.toAbsolutePath().normalize()
        val meta = repo.meta.toAbsolutePath().normalize()

        // Collect all resolved file paths
        val filesToAdd = mutableSetOf<Path>()

        for (raw in inputs) {
            val p =
                if (Paths.get(raw).isAbsolute) {
                    Paths.get(raw).toAbsolutePath().normalize()
                } else {
                    root.resolve(raw).toAbsolutePath().normalize()
                }

            // Check if it's a glob pattern
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

            // Handle "." to mean current directory recursively
            if (raw == ".") {
                Files
                    .walk(root)
                    .asSequence()
                    .filter { it.isRegularFile() && !it.startsWith(meta) }
                    .forEach { filesToAdd.add(it) }
                continue
            }

            // Handle directories recursively
            if (Files.isDirectory(p)) {
                Files
                    .walk(p)
                    .asSequence()
                    .filter { it.isRegularFile() }
                    .filter { it.startsWith(root) && !it.startsWith(meta) }
                    .forEach { filesToAdd.add(it) }
                continue
            }

            // Regular file
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

        // Load the current index to check for already-staged files
        val currentIndex = Index.load(repo)

        // Track how many files were actually staged
        var stagedCount = 0

        // Now process all collected files
        for (abs in filesToAdd) {
            if (!abs.startsWith(root)) {
                echo("skip: $abs (outside repository)")
                continue
            }
            if (abs.startsWith(meta)) {
                echo("skip: $abs (internal .timetree directory)")
                continue
            }

            val rel = root.relativize(abs).toString().replace(File.separatorChar, '/')
            val id = FsObjectStore.writeBlob(repo, abs)

            // Check if a file is already staged with the same content
            val existingId = currentIndex[rel]
            if (existingId != null && existingId == id) {
                // File is already staged with the same content, skip it
                continue
            }

            Index.update(repo, rel, id)
            echo("staged: $rel -> ${id.toHex()}")
            stagedCount++
        }

        // If no files were staged, inform the user
        if (stagedCount == 0 && filesToAdd.isNotEmpty()) {
            echo("Nothing to stage - all files are already up to date.")
        }
    }
}
