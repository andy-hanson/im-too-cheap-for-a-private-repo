package compile.err

import u.*

fun raise(kind: Err): Nothing =
	throw CompileError(kind)

fun raise(loc: Loc, kind: Err): Nothing =
	throw CompileError(loc, kind)

fun raiseWithPath(path: Path, loc: Loc, kind: Err): Nothing =
	throw CompileError(loc, kind, path)

fun must(cond: Bool, loc: Loc, kind: Err) {
	if (!cond)
		raise(loc, kind)
}

