package compile

import compile.err.*
import compile.parse.parseModule
import u.*
import n.*
import java.util.*


/**
Loads modules and produces a linear compilation order.
https://en.wikipedia.org/wiki/Topological_sorting#Depth-first_search
 */
internal data class LinearizedModule(val module: Module, val ast: ast.Module, val imports: Arr<LinearizedModule>)

internal fun linearizeModuleDependencies(io: FileInput, startPath: Path): LinearizedModule =
	Linearizer(io).go(null, null, RelPath(0, startPath))

//TODO:NAME
private class Linearizer(private val io: FileInput) {
	// This also functions as `visited`
	private val map = HashSet<Path>()

	fun go(fromPath: Path?, fromLoc: Loc?, rel: RelPath): LinearizedModule { //TODO:name
		val path = (fromPath ?: Path.empty).resolve(rel) // Logical path
		if (path in map) {
			raiseWithPath(fromPath!!, fromLoc!!, Err.CircularDepdendency(path))
		}
		map.add(path)

		val (fullPath, source) = resolve(io, fromPath, fromLoc, rel)
		val module = Module(path, fullPath, source)
		val moduleAst =
			try {
				parseModule(source, rel.last)
			} catch (error: CompileError) {
				error.module = module
				throw error
			}

		// Calculate dependencies
		val imports =
			moduleAst.imports.map { import ->
				when (import.path) {
					is ast.Module.Import.ImportPath.Global ->
						TODO()
					is ast.Module.Import.ImportPath.Relative ->
						SingleImport(import.loc, import.path.path)
				}
			}

		// Mark this node as visited
		map.add(path)
		//TODO: do this in parallel
		val importModules = imports.map { go(fullPath, it.loc, it.rel) }
		// We are linearizing: write out this module after all of its dependencies are written out.
		return LinearizedModule(module, moduleAst, importModules)
	}
}

//TODO: move this code into compileSingleModule, so we have the module in context and don't need raiseWithPath
private fun raiseWithPath(_path: Path, loc: Loc, kind: Err): Nothing =
	throw CompileError(loc, kind)



//TODO:NAME
private class ModuleWithImports(val imports: Arr<SingleImport>, val ast: ast.Module)
private class SingleImport(val loc: Loc, val rel: RelPath)

private val extension = ".nz"
private val mainNz = "main$extension".sym

private fun attempt(io: FileInput, path: Path): Resolved? =
	try {
		Resolved(path, io.read(path))
	} catch (_: FileNotFound) {
		null
	}

private data class Resolved(val fullPath: Path, val source: String)

/**
Returns the resolved path, with '.nz' included.
Normally we just add a '.nz' extension, but for directories we try a 'main.nz' inside of it.
 */
//TODO: absolutely forbid unnecessary specification of 'main', because we want modules to only have 1 valid path.
private fun resolve(io: FileInput, from: Path?, fromLoc: Loc?, rel: RelPath): Resolved {
	fun raiseErr(message: Err): Nothing =
		if (from == null)
			raiseWithPath(Path.empty, Loc.zero, message)
		else
			raiseWithPath(from, fromLoc!!, message)

	fun attempt(path: Path): Resolved? = attempt(io, path)
	val base = (from ?: Path.empty).resolve(rel)
	val mainPath = base.add(mainNz)
	fun attemptMain() = attempt(mainPath)
	return if (rel.isParentsOnly)
		attemptMain() ?: raiseErr(Err.CantFindLocalModuleFromDirectory(rel, mainPath))
	else {
		val (pre, last) = base.directoryAndBasename()
		val regularPath = pre.add((last.str + extension).sym)
		attempt(regularPath) ?: attemptMain() ?:
			raiseErr(Err.CantFindLocalModuleFromFileOrDirectory(rel, regularPath, mainPath))
	}
}

