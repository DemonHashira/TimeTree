package dev.kamisama.core.diff

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/** Tests for Myers diff algorithm. */
class MyersTest :
    StringSpec({

        "empty lists should produce no edits" {
            val edits = Myers.computeEdits(emptyList(), emptyList())
            edits.shouldBeEmpty()
        }

        "identical lists should produce only Keep edits" {
            val a = listOf("line1", "line2", "line3")
            val b = listOf("line1", "line2", "line3")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 3
            edits.all { it is Myers.Edit.Keep } shouldBe true
        }

        "single insertion at end" {
            val a = listOf("line1", "line2")
            val b = listOf("line1", "line2", "line3")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 3
            edits[0] shouldBe Myers.Edit.Keep("line1")
            edits[1] shouldBe Myers.Edit.Keep("line2")
            edits[2] shouldBe Myers.Edit.Insert("line3")
        }

        "single deletion at end" {
            val a = listOf("line1", "line2", "line3")
            val b = listOf("line1", "line2")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 3
            edits[0] shouldBe Myers.Edit.Keep("line1")
            edits[1] shouldBe Myers.Edit.Keep("line2")
            edits[2] shouldBe Myers.Edit.Delete("line3")
        }

        "single insertion at beginning" {
            val a = listOf("line2", "line3")
            val b = listOf("line1", "line2", "line3")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 3
            edits[0] shouldBe Myers.Edit.Insert("line1")
            edits[1] shouldBe Myers.Edit.Keep("line2")
            edits[2] shouldBe Myers.Edit.Keep("line3")
        }

        "single deletion at beginning" {
            val a = listOf("line1", "line2", "line3")
            val b = listOf("line2", "line3")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 3
            edits[0] shouldBe Myers.Edit.Delete("line1")
            edits[1] shouldBe Myers.Edit.Keep("line2")
            edits[2] shouldBe Myers.Edit.Keep("line3")
        }

        "insertion in middle" {
            val a = listOf("line1", "line3")
            val b = listOf("line1", "line2", "line3")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 3
            edits[0] shouldBe Myers.Edit.Keep("line1")
            edits[1] shouldBe Myers.Edit.Insert("line2")
            edits[2] shouldBe Myers.Edit.Keep("line3")
        }

        "deletion in middle" {
            val a = listOf("line1", "line2", "line3")
            val b = listOf("line1", "line3")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 3
            edits[0] shouldBe Myers.Edit.Keep("line1")
            edits[1] shouldBe Myers.Edit.Delete("line2")
            edits[2] shouldBe Myers.Edit.Keep("line3")
        }

        "replacement (delete and insert)" {
            val a = listOf("line1", "old line", "line3")
            val b = listOf("line1", "new line", "line3")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 4
            edits[0] shouldBe Myers.Edit.Keep("line1")
            edits[1] shouldBe Myers.Edit.Delete("old line")
            edits[2] shouldBe Myers.Edit.Insert("new line")
            edits[3] shouldBe Myers.Edit.Keep("line3")
        }

        "multiple consecutive insertions" {
            val a = listOf("line1", "line5")
            val b = listOf("line1", "line2", "line3", "line4", "line5")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 5
            edits[0] shouldBe Myers.Edit.Keep("line1")
            edits[1] shouldBe Myers.Edit.Insert("line2")
            edits[2] shouldBe Myers.Edit.Insert("line3")
            edits[3] shouldBe Myers.Edit.Insert("line4")
            edits[4] shouldBe Myers.Edit.Keep("line5")
        }

        "multiple consecutive deletions" {
            val a = listOf("line1", "line2", "line3", "line4", "line5")
            val b = listOf("line1", "line5")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 5
            edits[0] shouldBe Myers.Edit.Keep("line1")
            edits[1] shouldBe Myers.Edit.Delete("line2")
            edits[2] shouldBe Myers.Edit.Delete("line3")
            edits[3] shouldBe Myers.Edit.Delete("line4")
            edits[4] shouldBe Myers.Edit.Keep("line5")
        }

        "completely different lists" {
            val a = listOf("a", "b", "c")
            val b = listOf("x", "y", "z")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 6
            edits[0] shouldBe Myers.Edit.Delete("a")
            edits[1] shouldBe Myers.Edit.Delete("b")
            edits[2] shouldBe Myers.Edit.Delete("c")
            edits[3] shouldBe Myers.Edit.Insert("x")
            edits[4] shouldBe Myers.Edit.Insert("y")
            edits[5] shouldBe Myers.Edit.Insert("z")
        }

        "empty to non-empty" {
            val a = emptyList<String>()
            val b = listOf("line1", "line2")
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 2
            edits[0] shouldBe Myers.Edit.Insert("line1")
            edits[1] shouldBe Myers.Edit.Insert("line2")
        }

        "non-empty to empty" {
            val a = listOf("line1", "line2")
            val b = emptyList<String>()
            val edits = Myers.computeEdits(a, b)

            edits.size shouldBe 2
            edits[0] shouldBe Myers.Edit.Delete("line1")
            edits[1] shouldBe Myers.Edit.Delete("line2")
        }

        "complex diff with multiple changes" {
            val a =
                listOf(
                    "function hello() {",
                    "  console.log('old');",
                    "  return 42;",
                    "}",
                )
            val b =
                listOf(
                    "function hello() {",
                    "  console.log('new');",
                    "  console.log('extra');",
                    "  return 42;",
                    "}",
                )
            val edits = Myers.computeEdits(a, b)

            edits[0] shouldBe Myers.Edit.Keep("function hello() {")
            edits[1] shouldBe Myers.Edit.Delete("  console.log('old');")
            edits[2] shouldBe Myers.Edit.Insert("  console.log('new');")
            edits[3] shouldBe Myers.Edit.Insert("  console.log('extra');")
            edits[4] shouldBe Myers.Edit.Keep("  return 42;")
            edits[5] shouldBe Myers.Edit.Keep("}")
        }

        "unified diff format should include headers" {
            val a = listOf("line1", "line2")
            val b = listOf("line1", "modified")
            val edits = Myers.computeEdits(a, b)
            val diff = Myers.formatUnifiedDiff(edits, "file.txt", "file.txt")

            diff shouldContain "--- file.txt"
            diff shouldContain "+++ file.txt"
            diff shouldContain "@@"
        }

        "unified diff format should show insertions with plus" {
            val a = listOf("line1")
            val b = listOf("line1", "line2")
            val edits = Myers.computeEdits(a, b)
            val diff = Myers.formatUnifiedDiff(edits)

            diff shouldContain "+line2"
        }

        "unified diff format should show deletions with minus" {
            val a = listOf("line1", "line2")
            val b = listOf("line1")
            val edits = Myers.computeEdits(a, b)
            val diff = Myers.formatUnifiedDiff(edits)

            diff shouldContain "-line2"
        }

        "unified diff format should show context lines with space" {
            val a = listOf("ctx1", "old", "ctx2")
            val b = listOf("ctx1", "new", "ctx2")
            val edits = Myers.computeEdits(a, b)
            val diff = Myers.formatUnifiedDiff(edits, contextLines = 1)

            diff shouldContain " ctx1"
            diff shouldContain "-old"
            diff shouldContain "+new"
            diff shouldContain " ctx2"
        }

        "unified diff with zero context lines" {
            val a = listOf("line1", "line2", "line3")
            val b = listOf("line1", "modified", "line3")
            val edits = Myers.computeEdits(a, b)
            val diff = Myers.formatUnifiedDiff(edits, contextLines = 0)

            diff shouldContain "-line2"
            diff shouldContain "+modified"
        }

        "unified diff empty hunks for identical content" {
            val a = listOf("same", "lines")
            val b = listOf("same", "lines")
            val edits = Myers.computeEdits(a, b)
            val diff = Myers.formatUnifiedDiff(edits)

            diff shouldContain "--- a"
            diff shouldContain "+++ b"
            diff shouldNotContain "@@"
        }

        "real-world example - code modification" {
            val a =
                listOf(
                    "public class Example {",
                    "    public void oldMethod() {",
                    "        System.out.println(\"old\");",
                    "    }",
                    "}",
                )
            val b =
                listOf(
                    "public class Example {",
                    "    public void newMethod() {",
                    "        System.out.println(\"new\");",
                    "        System.out.println(\"extra\");",
                    "    }",
                    "}",
                )
            val edits = Myers.computeEdits(a, b)

            val keeps = edits.filterIsInstance<Myers.Edit.Keep>()
            val deletes = edits.filterIsInstance<Myers.Edit.Delete>()
            val inserts = edits.filterIsInstance<Myers.Edit.Insert>()

            keeps.map { it.line } shouldBe listOf("public class Example {", "    }", "}")
            deletes.size shouldBe 2
            deletes.any { it.line == "    public void oldMethod() {" } shouldBe true
            deletes.any { it.line == "        System.out.println(\"old\");" } shouldBe true
            inserts.size shouldBe 3
            inserts.any { it.line == "    public void newMethod() {" } shouldBe true
            inserts.any { it.line == "        System.out.println(\"new\");" } shouldBe true
            inserts.any { it.line == "        System.out.println(\"extra\");" } shouldBe true
        }

        "longest common subsequence preserved" {
            val a = listOf("A", "B", "C", "D", "E")
            val b = listOf("A", "X", "B", "Y", "C", "Z", "D", "E")
            val edits = Myers.computeEdits(a, b)

            val keeps = edits.filterIsInstance<Myers.Edit.Keep>()
            keeps.map { it.line } shouldBe listOf("A", "B", "C", "D", "E")
        }
    })
