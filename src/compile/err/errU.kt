package compile.err

import u.*

fun raise(loc: Loc, kind: Err): Nothing =
	throw CompileError(loc, kind)

fun must(cond: Boolean, loc: Loc, kind: Err) {
	if (!cond)
		raise(loc, kind)
}

