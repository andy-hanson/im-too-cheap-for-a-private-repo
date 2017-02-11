package compile.parse

import u.*
import ast.*
import compile.err.*

internal fun parseModule(source: String, name: Sym): Module =
	Parser(source).parseModule(name)

sealed class Either<T, U> {
	class Left<T>(val value: T) : Either<T, Nothing>()
	class Right<U>(val value: U) : Either<Nothing, U>()
}

private class Parser(source: String) : Lexer(source) {
	fun parseModule(name: Sym): Module {
		val start = curPos()
		val kw = takeKeyword()
		val (imports, classStart, nextKw) =
			if (kw == Token.Import)
				Triple(buildUntilNull(this::parseImport), curPos(), takeKeyword())
			else
				Triple(Arr.empty(), start, kw)
		val klass = parseClass(name, classStart, nextKw)
		return Module(locFrom(start), imports, klass)
	}

	private fun parseImport(): Module.Import? {
		if (tryTakeNewline()) {
			return null
		}

		takeSpace()

		val start = curPos()

		var leadingDots = 0
		while (tryTakeDot()) {
			leadingDots += 1
		}

		val pathParts = build<Sym> {
			add(takeName())
			while (tryTakeDot()) {
				add(takeName())
			}
		}

		val path = Path(pathParts)
		val importPath =
			if (leadingDots == 0)
				Module.Import.ImportPath.Global(path)
			else
				Module.Import.ImportPath.Relative(RelPath(leadingDots, path))

		return Module.Import(locFrom(start), importPath)
	}

	private fun parseClass(name: Sym, start: Pos, kw: Token.Kw): Klass {
		val head = parseHead(start, kw)
		val members = buildUntilNull(this::parseMember)
		return Klass(locFrom(start), name, head, members)
	}

	private fun parseHead(start: Pos, kw: Token.Kw): Klass.Head {
		when (kw) {
			Token.Slots -> {
				takeIndent()
				val vars = buildUntilNull(this::parseSlot)
				return Klass.Head.Slots(locFrom(start), vars)
			}
			Token.Enum -> {
				TODO("")
			}
			else -> {
				unexpected(start, kw)
			}
		}
	}

	private fun parseSlot(): Klass.Head.Slots.Slot? {
		val (start, next) = posNext()
		//TODO: 'take' method
		val isMutable = when (next) {
			Token.Var -> true
			Token.Val -> false
			Token.Dedent -> return null
			else -> unexpected(start, next)
		}
		takeSpace()
		val ty = parseTy()
		takeSpace()
		val name = takeName()
		return Klass.Head.Slots.Slot(locFrom(start), isMutable, ty, name)
	}

	private fun parseMember(): Member? {
		val (start, next) = posNext()
		val isStatic = when (next) {
			Token.Fun -> true
			Token.Def -> false
			Token.EOF -> return null
			else -> unexpected(start, next)
		}
		takeSpace()
		val returnTy = parseTy()
		takeSpace()
		val name = takeName()
		takeLparen()
		val parameters =
			if (tryTakeRparen())
				Arr.empty()
			else {
				val first = parseJustParameter()
				buildUntilNullWithFirst(first, this::parseParameter)
			}
		takeIndent()
		val body = parseBlock()
		return Method(locFrom(start), isStatic, returnTy, name, parameters, body)
	}

	private fun parseParameter(): Method.Parameter? {
		if (tryTakeRparen())
			return null
		takeComma()
		takeSpace()
		return parseJustParameter()
	}

	private fun parseJustParameter(): Method.Parameter {
		val start = curPos()
		val ty = parseTy()
		takeSpace()
		val name = takeName()
		return Method.Parameter(locFrom(start), ty, name)
	}

	private fun parseTy(): Ty {
		val start = curPos()
		val name = takeTyName()
		//TODO: generics
		return Ty.Access(locFrom(start), name)
	}





	private enum class Ctx {
		Line,
		/** Like Line, but forbid `=` because we're already in one. */
		ExprOnly,
		/** Parse an expression and expect `)` at the end */
		Paren,
		/** Look for a QuoteEnd */
		Quote,
		CsHead,
		List
	}

	private sealed class Next {
		class NewlineAfterEquals(val pattern: Pattern) : Next()
		object NewlineAfterStatement : Next()
		object EndNestedBlock : Next()
		object CtxEnded : Next()
	}

	private fun parseBlock(): Expr {
		val (start, next) = posNext()
		return parseBlockWithStart(start, next)
	}

	private fun parseBlockWithStart(start: Pos, first: Token): Expr {
		val (expr, next) = parseExprWithNext(start, first, Ctx.Line)

		return when (next) {
			is Next.NewlineAfterEquals -> {
				val rest = parseBlock()
				Let(locFrom(start), next.pattern, expr, rest)
			}
			Next.NewlineAfterStatement -> {
				val rest = parseBlock()
				Seq(locFrom(start), expr, rest)
			}
			Next.EndNestedBlock -> {
				val (start2, first2) = posNext()
				when (first) {
					Token.Dedent -> expr
					else -> {
						val rest = parseBlockWithStart(start2, first2)
						Seq(locFrom(start2), expr, rest)
					}
				}
			}
			Next.CtxEnded ->
				expr
		}
	}

	private fun parseExpr(ctx: Ctx): ExprRes {
		val (start, next) = posNext()
		return parseExprWithNext(start, next, ctx)
	}

	private data class ExprRes(val expr: Expr, val next: Next)
	private fun parseExprWithNext(exprStart: Pos, startToken: Token, ctx: Ctx): ExprRes {
		val parts = mutableListOf<Expr>()
		fun addPart(part: Expr) { parts.add(part) }
		fun anySoFar() = !parts.isEmpty()
		fun finishLoc() = locFrom(exprStart)
		fun finishRegular(): Expr {
			val loc = finishLoc()
			return when (parts.size) {
				0 -> raise(loc, Err.EmptyExpression)
				1 -> parts[0]
				else -> {
					val head = parts[0]
					val tail = Arr.tail(parts)
					Call(loc, head, tail)
				}
			}
		}

		var loopStart = exprStart
		var loopNext = startToken
		fun notExpected(): Nothing =
			unexpected(loopStart, loopNext)
		fun readAndLoop() {
			loopStart = curPos()
			loopNext = nextToken()
		}

		while (true) {
			// Need to make it a val so smart casts will work
			val loopCurrentToken = loopNext
			when (loopCurrentToken) {
				Token.Equals -> {
					val loc = locFrom(loopStart)
					if (ctx != Ctx.Line)
						notExpected()
					val pattern = partsToPattern(loc, parts)
					val (expr, next) = parseExpr(Ctx.ExprOnly)
					must(next !== Next.CtxEnded, locFrom(loopStart), Err.BlockCantEndInDeclare)
					assert(next === Next.NewlineAfterStatement)
					return ExprRes(expr, Next.NewlineAfterEquals(pattern))
				}

				is Token.Operator -> {
					return if (anySoFar()) {
						val left = finishRegular()
						val (right, next) = parseExpr(ctx)
						ExprRes(OperatorCall(locFrom(exprStart), left, loopCurrentToken.name, right), next)
					} else {
						TODO("?")
					}
				}

				Token.Newline, Token.Dedent -> {
					val next = when (ctx) {
						Ctx.Line, Ctx.ExprOnly ->
							when (loopCurrentToken) {
								Token.Newline -> Next.NewlineAfterStatement
								Token.Dedent -> Next.CtxEnded
								else -> TODO() //should be unreachable
							}
						else -> notExpected()
					}
					return ExprRes(finishRegular(), next)
				}

				is Token.TyName -> {
					val className = loopCurrentToken.name
					takeDot()
					val staticMethodName = takeName()
					addPart(StaticAccess(locFrom(loopStart), className, staticMethodName))
					readAndLoop()
				}

				else -> {
					//single token should have expression meaning
					val loc = locFrom(loopStart)
					val e = when (loopCurrentToken) {
						is Token.Name -> Access(loc, loopCurrentToken.name)
						is Token.Literal -> Literal(loc, loopCurrentToken.value)
						else -> notExpected()
					}
					addPart(e)
					readAndLoop()
				}
			}
		}
	}
}



private fun partsToPattern(loc: Loc, parts: MutableList<Expr>): Pattern {
	fun fail(): Nothing =
		raise(loc, Err.PrecedingEquals)
	fun partToPattern(part: Expr): Pattern =
		when (part) {
			is Access -> Pattern.Single(part.loc, part.name)
			else -> fail()
		}
	return when (parts.size) {
		0 -> fail()
		1 -> partToPattern(parts[0])
		else -> Pattern.Destruct(loc, Arr.fromMapped(parts, ::partToPattern))
	}
}
