// Dangerfile.df.kts
/*
 * Use external dependencies using the following annotations:
 */
@file:Repository("https://repo.maven.apache.org")
@file:DependsOn("org.apache.commons:commons-text:1.6")

import org.apache.commons.text.WordUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import systems.danger.kotlin.*
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory


// register plugin MyDangerPlugin

danger(args) {

    val allSourceFiles = git.modifiedFiles + git.createdFiles
    // val changelogChanged = allSourceFiles.contains("CHANGELOG.md")
    // val sourceChanges = allSourceFiles.firstOrNull { it.contains("src") }

    onGitHub {
        val lintersActivationTag = "#linters"
        //val lintersActivated = pullRequest.title.contains(lintersActivationTag)


        val xmlFile: File = File("detekt-hint-report.xml")
        //allSourceFiles.forEach { println("File changed or updated ${it.toString()}") }
        val xmlDoc: Document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        xmlDoc.documentElement.normalize()
        val fileList: NodeList = xmlDoc.getElementsByTagName("file")

        // Method to populate the map of modified files and lines.
        var fileNameWithModifiedLines = populateModifiedFileLineMap(git.headSha, git.baseSha)

        print("Lines modified $fileNameWithModifiedLines")

        for (i in 0 until fileList.length) {
            var fileNode = fileList.item(i) as Element

            val fileName = fileNode.getAttribute("name")
            //println("Filename: $fileName")

            for (k in 0 until fileNode.getElementsByTagName("error").length) {
                val error = fileNode.getElementsByTagName("error").item(k) as Element
                //println("Error")

                val line = error.getAttribute("line").toInt()
                //println("Line: $line")
                val message = error.getAttribute("message")
                //println("Message: $message")

                if (allSourceFiles.any { fileName.trim().contains(it.trim()) }) {

                    println("Message: $message")

                    val absoluteFileName = fileName.split("/").last()

                    val linesModifiedInFile = fileNameWithModifiedLines[absoluteFileName]

                    print("Absolute file name $absoluteFileName lines modified $linesModifiedInFile")

                    if (linesModifiedInFile.isNullOrEmpty() || linesModifiedInFile.contains(line)) {
                        // Find the sourcefile without the /github/workflow prefix to its path.
                        val file = allSourceFiles.find { fileName.trim().contains(it.trim()) } ?: fileName

                        // Only notify about the warning if the file has been modified in this PR
                        println("Adds warning for $fileName")
                        warn(message, file, line)
                    }
                }
            }
        }
    }

    onGit {
        //No Java files check
        createdFiles.filter {
            it.endsWith(".java")
        }.forEach {
            // Using apache commons-text dependency to be sure the dependency resolution always works
            warn(WordUtils.capitalize("please consider to create new files in Kotlin"), it, 1)
        }
    }
}

fun populateFileLineMap(commandRawOutput: String) : MutableMap<String, MutableSet<Int>> {

    val fileNameLineMap = mutableMapOf<String, MutableSet<Int>>()

    try {
        val lines = commandRawOutput.split("\n")

        var fileName = ""
        lines.forEach {
            if(it.contains("-")) {
                val pair = it.split("-")
                val start = pair[0].trim().toInt()
                val end = pair[1].trim().toInt()
                val set = fileNameLineMap.getOrDefault(fileName, mutableSetOf())

                for (i in start - 1..end + 1) {
                    set.add(i)
                }
                fileNameLineMap[fileName] = set
            } else {
                fileName = it.split("/").last()
            }
        }
    } catch(e : Exception) {
        println("error while parsing the data ${e.message}")
    }
    return fileNameLineMap
}

/**
 * Method to populate the modified lines in the files.
 *
 */
fun populateModifiedFileLineMap(headSHA: String?, baseSHA: String?) : MutableMap<String, MutableSet<Int>> {
    return if (headSHA == null || baseSHA == null) {
        println("Not data")
        mutableMapOf<String, MutableSet<Int>>()
    } else {

        // Reference: https://stackoverflow.com/questions/61426894/find-out-changed-line-numbers-from-git-diff/61429395#61429395
        val command = "git diff -U0 $headSHA $baseSHA | \\\n" +
                "grep -v -e '^[+-]' -e '^index' | \\\n" +
                "sed 's/diff --git a.* b\\//\\//g; s/.*@@\\(.*\\)@@.*/\\1/g; s/^ -//g; s/,[0-9]*//g; s/\\(^[0-9]*\\) +/\\1-/g;'"

        println("Git command $command")

        val process = ProcessBuilder().command("/bin/bash", "-c", command).start()

        process.waitFor()

        val commandRawOutput = process.inputStream.bufferedReader().readText()

        println("common row output $commandRawOutput")

        populateFileLineMap(commandRawOutput)
    }
}

