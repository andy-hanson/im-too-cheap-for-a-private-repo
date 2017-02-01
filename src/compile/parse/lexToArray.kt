package compile.parse

import u.*

data class LexedEntry(val token: Token, val loc: Loc)
fun lexToArray(source: Input): Arr<LexedEntry> =
	ArrayLexer(source).go()

private class ArrayLexer(source: Input) : Lexer(source) {
	var parts = mutableListOf<LexedEntry>()

	fun go(): Arr<LexedEntry> {
		lexPlain()
		return Arr.from(parts)
	}

	private fun add(part: LexedEntry) {
		parts.add(part)
	}

	private fun lexPlain() {
		while (true) {
			val (start, next) = posNext()
			when (next) {
				Token.EOF -> return
				is Token.QuoteStart -> {
					add(LexedEntry(next, locFrom(start)))
					if (lexInQuote())
						return
				}
				else ->
					add(LexedEntry(next, locFrom(start)))
			}
		}
	}

	// Returns 'true' on EOF
	private fun lexInQuote(): Bool {
		while (true) {
			//TODO: locNext?
			val (start, next) = posNext()
			val loc = locFrom(start)
			when (next) {
				Token.EOF ->
					return true
				Token.RCurly -> {
					add(LexedEntry(next, loc))
					if (nextQuotePart().isEndOfQuote)
						return false
				}
				else ->
					add(LexedEntry(next, loc))
			}
		}
	}
}

