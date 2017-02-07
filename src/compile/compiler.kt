package compile

import compile.err.*
import compile.parse.*
import compile.check.*
import compile.emit.*
import n.*
import u.*

interface CompilerHost {
	val io: FileInput
}

//move
class Compiler(private val host: CompilerHost) {
	// Key is the *logical* path to the module.
	// Value is *null* to indicate that the module is currently being compiled.
	private var modules = HashMap<Path, Module?>()
	private var classLoader = DynamicClassLoader()

	/** Array of all lexemes in a file. */
	fun lex(path: Path): Arr<LexedEntry> {
		val source = host.io.read(path)!!
		return annotateErrorsWithModule(Module(path, path, source)) {
			lexToArray(source)
		}
	}

	/** Untyped AST for a file. */
	fun parse(path: Path): ast.Module {
		val source = host.io.read(path)!!
		return annotateErrorsWithModule(Module(path, path, source)) {
			parseModule(source, path.last)
		}
	}

	fun compile(path: Path): Module {
		return compileSingle(null, null, RelPath(0, path))
	}

	private fun compileSingle(fromPath: Path?, fromLoc: Loc?, rel: RelPath): Module {
		val logicalPath = if (fromPath == null) rel.relToParent else fromPath.resolve(rel)
		val alreadyCompiled = modules[logicalPath]
		if (alreadyCompiled != null) {
			if (!alreadyCompiled.importsAreResolved) {
				raiseWithPath(logicalPath, fromLoc ?: Loc.zero, Err.CircularDepdendency(logicalPath))
			}

			return alreadyCompiled
		}

		return resolveModule(host.io, fromPath, fromLoc, logicalPath).also { module ->
			annotateErrorsWithModule(module) {
				// First parse an untyped AST.
				val moduleAst = parseModule(module.source, rel.last)

				// Need to recursively compile all imports before doing any type checking.
				// Note: If there is a recursive import, `module.imports` is not yet set, so we will detect the circular dependency.
				val imports = moduleAst.imports.map { import ->
					when (import.path) {
						is ast.Module.Import.ImportPath.Global ->
							TODO()
						is ast.Module.Import.ImportPath.Relative ->
							compileSingle(module.fullPath, import.loc, import.path.path)
					}
				}

				module.imports = imports
				module.klass = typeCheck(imports, moduleAst.klass).also {
					writeClassBytecode(it, module.lineColumnGetter, classLoader)
				}
			}
		}
	}


	private fun<T> annotateErrorsWithModule(module: Module, doWork: () -> T): T =
		try {
			doWork()
		} catch (error: CompileError) {
			error.module = module
			throw error
		}
}

class DynamicClassLoader : ClassLoader() {
	fun define(className: String, bytecode: ByteArray): Class<out Any> =
		super.defineClass(className, bytecode, 0, bytecode.size)
}

private val extension = ".nz"
private val mainNz = "main$extension".sym

private fun resolveModule(io: FileInput, from: Path?, fromLoc: Loc?, logicalPath: Path): Module {
	fun raiseErr(message: Err): Nothing =
		if (from == null)
			raiseWithPath(Path.empty, Loc.zero, message)
		else
			raiseWithPath(from, fromLoc!!, message)

	fun attempt(fullPath: Path): Module? = attempt(io, logicalPath, fullPath)
	val mainPath = logicalPath.add(mainNz)
	fun attemptMain() = attempt(mainPath)
	val regularPath = logicalPath.addExtension(extension)
	return attempt(regularPath) ?: attemptMain() ?:
		raiseErr(Err.CantFindLocalModuleFromFileOrDirectory(logicalPath, regularPath, mainPath))
}

//TODO: move this code into compileSingleModule, so we have the module in context and don't need raiseWithPath
private fun raiseWithPath(_path: Path, loc: Loc, kind: Err): Nothing =
	throw CompileError(loc, kind)

private fun attempt(io: FileInput, logicalPath: Path, fullPath: Path): Module? =
	io.read(fullPath).opMap { Module(logicalPath, fullPath, it) }

