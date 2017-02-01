package compile.parse

import u.*
import ast.*
import compile.err.*

fun parseModule(source: Input, name: Sym): Module =
	Parser(source).parseModule(name)

private class Parser(source: Input) : Lexer(source) {
	fun parseModule(name: Sym): Module {
		val start = curPos()
		val imports = Arr.empty<Import>() //TODO
		val klass = parseClass(name)
		return Module(locFrom(start), imports, klass)
	}

	private fun parseClass(name: Sym): Class {
		val start = curPos()
		val head = parseHead()
		val members = buildUntilNull(this::parseMember)
		return Class(locFrom(start), name, head, members)
	}

	private fun parseHead(): Class.Head {
		val (start, next) = posNext()
		when (next) {
			Token.Slots -> {
				takeIndent()
				val vars = buildUntilNull(this::parseVar)
				return Class.Head.Record(locFrom(start), vars)
			}
			Token.Enum -> {
				TODO("")
			}
			else -> {
				unexpected(start, next)
			}
		}
	}

	private fun parseVar(): Class.Head.Record.Var? {
		val (start, next) = posNext()
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
		return Class.Head.Record.Var(locFrom(start), isMutable, ty, name)
	}

	private fun parseMember(): Member? {
		val (start, next) = posNext()
		val isStatic = when (next) {
			Token.Fun -> true
			Token.Def -> false
			Token.EOF -> return null
			else -> unexpected(start, next)
		}
		val returnTy = parseTy()
		val name = takeName()
		takeLparen()
		val parameters = if (tryTakeRparen()) Arr.empty() else buildUntilNull(this::parseParameter)
		val body = parseBlock()
		return Method(locFrom(start), isStatic, returnTy, name, parameters, body)
	}

	private fun parseParameter(): Method.Parameter? {
		if (tryTakeRparen())
			return null
		takeComma()
		takeSpace()
		val start = curPos()
		val ty = parseTy()
		takeSpace()
		val name = takeName()
		return Method.Parameter(locFrom(start), ty, name)
	}

	private fun parseTy(): Ty {
		val start = curPos()
		val name = takeName()
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
		fun<T> notExpected(): T = unexpected(loopStart, loopNext)
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
						notExpected<Unit>()
					val pattern = partsToPattern(loc, parts)
					val (expr, next) = parseExpr(Ctx.ExprOnly)
					must(next !== Next.CtxEnded, locFrom(loopStart), Err.BlockCantEndInDeclare)
					assert(next === Next.NewlineAfterStatement)
					return ExprRes(expr, Next.NewlineAfterEquals(pattern))
				}

				is Token.Operator -> {
					val op = Access(locFrom(loopStart), loopCurrentToken.name)
					return if (anySoFar()) {
						val left = finishRegular()
						val (right, next) = parseExpr(ctx)
						ExprRes(Call(locFrom(exprStart), op, Arr.of(left, right)), next)
					} else {
						TODO("?")
					}
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
	fun<T> fail(): T =
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
