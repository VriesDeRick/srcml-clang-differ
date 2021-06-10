package nl.utwente.astdiff

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import nl.utwente.astdiff.ast.TreeNode
import nl.utwente.astdiff.clang.ClangParser
import nl.utwente.astdiff.srcml.SrcMLParser
import java.io.*
import java.util.stream.Collectors
import kotlin.system.exitProcess

enum class ExecutableParser{
    SRCML,
    CLANG
}

class ClIArgs(parser: ArgParser) {
    val srcMLPath by parser.storing("--srcml", help = "Path to srcML executable").default("srcml")

    val clangPath by parser.storing("--clang", help = "Path to Clang(++) executable").default("clang++")

    val clangOptions by parser.storing("--clang-options", help = "Options to pass to Clang. Can be wrapped by \"-symbols if this contains spaces.").default("-I .")

    val source by parser.positional("C++ input file path")

    val writeOutputs by parser.flagging("--write-tools", help = "Whether or not to write tool outputs to local storage. Useful for debugging purposes")
}

fun getToolOutput(producer: ExecutableParser, args: ClIArgs): String {
    val clangArgs = args.clangOptions.split(" ")
    // First get stream of pre-processed code
    val preprocessor = ProcessBuilder(listOf(args.clangPath, "-x", "c++") + clangArgs + listOf("-E", "-")).start()
    val ppInput = preprocessor.outputStream.bufferedWriter()
    ppInput.write(File(args.source).readText())
    ppInput.close()
    val preprocessed = BufferedReader(InputStreamReader(preprocessor.inputStream)).lines().collect(Collectors.toList())
    preprocessor.waitFor()

    val executable = when(producer) {
        ExecutableParser.SRCML -> listOf(args.srcMLPath, "--language=C++")
        ExecutableParser.CLANG -> listOf(args.clangPath, "-x", "c++") + clangArgs + listOf("-Xclang", "-ast-dump=json", "-fsyntax-only", "-")
    }
    if (producer == ExecutableParser.SRCML) {
        // Strip header files from input
        val lineIterator = preprocessed.iterator()
        var isInSystemFile = false
        while (lineIterator.hasNext()) {
            val line = lineIterator.next()
            val match = Regex("# \\d+ \"([^\"]+)\" .*").find(line)
            if (match == null) {
                // No header information: preserve if not in system file
                if (isInSystemFile) {
                    lineIterator.remove()
                }
            } else {
                // Header information: update isInSystemFile and preserve line
                val path = match.groups[1]!!.value
                isInSystemFile = listOf("/usr", "__", "include").any { path.contains(it, ignoreCase = true) }
            }
        }
    }
    val builder = ProcessBuilder(executable)
    val process = builder.start()
    val toolInput = process.outputStream.bufferedWriter()
    val stripped = preprocessed.joinToString(System.lineSeparator())
    toolInput.write(stripped)
    toolInput.close()
    // Uncomment for debugging purposes
//    if (producer == ExecutableParser.SRCML) {
//        File("stripped.cpp").writeText(stripped)
//    }
    // Transfer program output to a new inputstream to prevent buffer getting full
    val output = BufferedReader(InputStreamReader(process.inputStream)).lines().collect(Collectors.joining(System.lineSeparator()))
    val exit = process.waitFor()
    if (exit != 0) {
        // TODO: More clearly define non-compiling exit codes
        System.err.println(BufferedReader(InputStreamReader(process.errorStream)).lines().collect(Collectors.joining(System.lineSeparator())))
    }
    if (args.writeOutputs) {
        val filename = when (producer) {
            ExecutableParser.SRCML -> "tool_srcml.xml"
            ExecutableParser.CLANG -> "tool_clang.json"
        }
        File(filename).writeText(output)
    }
    return output
}

fun parseToolTree(producer: ExecutableParser, toolOutput: String): TreeNode {
    return when(producer) {
        ExecutableParser.SRCML -> SrcMLParser.parseSrcML(toolOutput)
        ExecutableParser.CLANG -> ClangParser.parseClang(toolOutput)
    }
}

fun produceToolTree(producer: ExecutableParser, args: ClIArgs): TreeNode {
    return parseToolTree(producer, getToolOutput(producer, args))
}


fun main(args: Array<String>) = mainBody {
    val cliArgs = ArgParser(args).parseInto(::ClIArgs)
    println("Starting analysis of ${cliArgs.source}")
    val srcMLTree = produceToolTree(ExecutableParser.SRCML, cliArgs)
    println("Finished first tree construction")
    val clangTree = produceToolTree(ExecutableParser.CLANG, cliArgs)
    println("Finished second tree construction")
    val report = srcMLTree.compareWith(clangTree)
    println(report.asPrettyPrint(0))
}