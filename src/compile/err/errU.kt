package compile.err

import u.*

fun<T> raise(loc: Loc, kind: Err): T =
	throw CompileError(loc, kind)

fun<T> raiseWithPath(path: Path, loc: Loc, kind: Err): T =
	throw CompileError(loc, kind, path)

fun must(cond: Bool, loc: Loc, kind: Err) {
	if (!cond)
		raise<Unit>(loc, kind)
}

