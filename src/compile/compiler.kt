package compile

import compile.err.*
import compile.parse.*
import compile.check.*
import n.*
import u.*

interface CompilerHost {
	val io: FileInput
}

//move
class Compiler(private val host: CompilerHost) {
	private var modules = HashMap<Path, Module>()

	fun lex(path: Path): Arr<LexedEntry> =
		doWork(path) {
			lexToArray(host.io.read(path))
		}

	fun parse(path: Path): ast.Module =
		doWork(path) {
			parseModule(host.io.read(path), path.last)
		}

	fun compile(path: Path): Module {
		//TODO: might make more sense to linearize *while* we work... or not...
		val linearized = doWork2 { linearizeModuleDependencies(host.io, path) }
		return compileSingleModule(linearized)
	}

	private fun compileSingleModule(linear: LinearizedModule): Module {
		val (logicalPath, fullPath, source, ast, imported) = linear
		val importedModules = imported.map(this::compileSingleModule)
		val klass = makeClass(importedModules, ast.klass)
		return Module(logicalPath, fullPath, source, importedModules, klass)
	}

	private fun<T> doWork(fullPath: Path, f: () -> T): T =
		try {
			f()
		} catch (error: CompileError) {
			error.path = fullPath
			outputError(error)
			throw error
		}

	//TODO:NAME
	private fun<T> doWork2(f: () -> T): T =
		try {
			f()
		} catch (error: CompileError) {
			outputError(error)
			throw error
		}

	private fun outputError(error: CompileError) {
		val message = error.output(this::translateLoc)
		System.err.println(message)
	}

	private fun translateLoc(fullPath: Path, loc: Loc): LcLoc =
		//TODO: Don't do I/O again, cache it. But can't keep it in the module since that won't exist untio after we've parsed...
		//But if there's a parse error, we know what file we came from...
		//For a checker error we should know the module of the file...
		LcLoc.from(host.io.read(fullPath), loc)
}
