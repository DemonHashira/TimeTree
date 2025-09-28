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
import java.nio.file.Paths

/**
 * The add command that adds file contents to the staging index.
 */
class AddCmd(
    // Provider function for repository layout, defaults to the current working directory
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "add") {
    // Accept multiple file paths as command line arguments
    private val inputs by argument(name = "path").multiple()

    override fun help(context: Context) = "Add file contents to the index"

    override fun run() {
        val repo = repoProvider()

        // Exit early if no files specified
        if (inputs.isEmpty()) {
            echo("Nothing specified, nothing added.")
            return
        }

        // Get normalized absolute paths for the repository root and metadata directory
        val root = repo.root.toAbsolutePath().normalize()
        val meta = repo.meta.toAbsolutePath().normalize()

        // Process each input file path
        for (raw in inputs) {
            val p = Paths.get(raw)
            val abs = p.toAbsolutePath().normalize()

            // Validation 1: File must be inside the repository root directory
            if (!abs.startsWith(root)) {
                echo("skip: $raw (outside repository)")
                continue
            }

            // Validation 2: Don't allow staging internal metadata files
            if (abs.startsWith(meta)) {
                echo("skip: $raw (internal .timetree directory)")
                continue
            }

            // Validation 3: Only regular files can be staged (no directories, symlinks, etc.)
            if (!Files.isRegularFile(abs)) {
                echo("skip: $raw (not a regular file)")
                continue
            }

            // Convert absolute path to a repository-relative path with forward slashes
            // This ensures a consistent path format regardless of OS
            val rel = root.relativize(abs).toString().replace(File.separatorChar, '/')

            // Store file content as a blob object and get its hash ID
            val id = FsObjectStore.writeBlob(repo, abs)

            // Update the index with the file path and blob ID mapping
            Index.update(repo, rel, id)

            // Confirm successful staging to user
            echo("staged: $rel -> ${id.toHex()}")
        }
    }
}
