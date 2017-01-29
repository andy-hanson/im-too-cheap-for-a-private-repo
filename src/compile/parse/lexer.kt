package compile.parse

import u.*

abstract class Lexer(private val source: Input) {
	private var peek: Char = source.readChar()
	private var pos: Pos = startPos
	private var indent: Int = 0
	// Number of Token.Dedent we have to output before continuing to read
	private var dedenting: Int = 0

	init {
		skipNewlines()
	}

	//Don't use?
	protected fun curPos(): Pos =
		pos

	protected fun locFrom(start: Pos): Loc =
		Loc(start, pos)

	private fun incrPos() {
		pos = pos.incr()
	}

	private fun read(): Char =
		source.readChar()

	private fun readChar(): Char =
		returning(peek) {
			peek = read()
			incrPos()
		}

	private fun skip() {
		readChar()
	}

	private inline fun skipWhile(pred: Pred<Char>) {
		require(!pred(Input.EOF)) // Else this will be an infinite loop
		if (pred(peek)) {
			peek = run {
				var ch: Char
				do {
					ch = source.readChar()
					incrPos()
				} while (pred(ch))
				ch
			}
		}
	}

	private inline fun bufferWhile(addChar: Action<Char>, pred: Pred<Char>) {
		if (pred(peek)) {
			addChar(peek);
			// Returns the first char that's not skipped.
			peek = run {
				//TODO:NEATER?
				var ch: Char
				while (true) {
					ch = source.readChar()
					incrPos()
					if (!pred(ch))
						break
					addChar(ch)
				}
				ch
			}
		}
	}

	private fun skipNewlines() =
		skipWhile { it == '\n' }

	data class QuotePart(val text: String, val isEndOfQuote: Bool)
	protected fun nextQuotePart(): QuotePart {
		val (text, isEnd) = buildStringFromCharsAndReturn { addChar ->
			var isEnd: Bool
			outer@ while (true) {
				val ch = readChar()
				when (ch) {
					'"' -> {
						isEnd = true
						break@outer
					}
					'{' -> {
						isEnd = false
						break@outer
					}
					'\n' ->
						TODO("Compile error: unterminated quote")
					'\\' ->
						addChar(escape(readChar()))
					else ->
						addChar(ch)
				}
			}
			isEnd
		}
		return QuotePart(text, isEnd)
	}

	private fun takeNumber(negate: Bool, fst: Char): Token {
		val (str, isFloat) = buildStringFromCharsAndReturn { addChar ->
			addChar(fst)
			bufferWhile(addChar, ::isDigit)
			returning (peek == '.') { isFloat ->
				if (isFloat) {
					skip()
					addChar('.')
					must(isDigit(peek), Loc.singleChar(pos), Err.TooMuchIndent)
					bufferWhile(addChar, ::isDigit)
				}
			}
		}
		val value =
			if (isFloat) {
				val f = str.toDouble()
				ast.LiteralValue.Float(if (negate) -f else f)
			}
			else {
				val i = str.toLong()
				ast.LiteralValue.Int(if (negate) -i else i)
			}
		return Token.Literal(value)
	}

	private inline fun takeSymbol(first: Char, pred: Pred<Char>, makeToken: (Sym) -> Token): Token {
		val text = buildStringFromChars { addChar ->
			addChar(first)
			bufferWhile(addChar, pred)
		}
		val name = text.sym
		return Token.Kw.opKeyword(name) ?: makeToken(name)
	}

	private fun takeOperator(ch: Char): Token =
		takeSymbol(ch, ::isOperatorChar) { Token.Operator(it) }

	private inline fun countWhile(pred: Pred<Char>): Int {
		var count = 0
		while (pred(peek)) {
			skip()
			count++
		}
		return count
	}

	private fun lexIndent(): Int {
		val start = pos
		return returning(countWhile { it == '\t' }) {
			must(peek != ' ', locFrom(start), Err.LeadingSpace)
		}
	}

	private fun handleNewline(indentOnly: Bool): Token {
		skipNewlines()
		val oldIndent = indent
		indent = lexIndent()
		return when {
			indent > oldIndent -> {
				must(indent == oldIndent + 1, Loc.singleChar(pos), Err.TooMuchIndent)
				Token.Indent
			}
			indent == oldIndent ->
				if (indentOnly)
					nextToken()
				else
					Token.Newline
			else -> {
				dedenting = oldIndent - indent - 1
				Token.Dedent
			}
		}
	}

	protected fun nextToken(): Token {
		if (dedenting != 0) {
			dedenting = dedenting - 1
			return Token.Dedent
		} else
			return takeNext()
	}

	private fun takeNext(): Token {
		val ch = readChar()
		return when (ch) {
			Input.EOF -> {
				// Remember to dedent before finishing
				if (indent != 0) {
					indent--
					Token.Dedent
				} else
					Token.EOF
			}

			' ' -> {
				must(peek != '\n', Loc.singleChar(pos), Err.TrailingSpace)
				takeNext()
			}

			'\n' ->
				handleNewline(false)

			'|' -> {
				skipWhile { it != '\n' }
				handleNewline(true)
			}

			'\\' -> Token.Backslash
			':' -> Token.Colon
			'(' -> Token.Lparen
			')' -> Token.Rparen
			'[' -> Token.Lbracket
			']' -> Token.Rbracket
			'{' -> Token.LCurly
			'}' -> Token.RCurly
			'_' -> Token.Underscore

			'-' -> {
				val next = readChar()
				if (isDigit(next))
					takeNumber(true, next)
				else
					takeOperator(ch)
			}

			'.' ->
				when (peek) {
					'.' -> {
						skip()
						Token.DotDot
					}
					else ->
						Token.Dot
				}

			'"' -> {
				val (str, isDone) = nextQuotePart()
				if (isDone)
					Token.Literal(LiteralValue.Str(str))
				else
					Token.QuoteStart(str)
			}

			in '0' .. '9' ->
				takeNumber(false, ch)
			in 'a' .. 'z' ->
				takeSymbol(ch, ::isNameChar) { Token.Name(it) }
			in 'A' .. 'Z' ->
				takeSymbol(ch, ::isNameChar) { Token.TyName(it) }
			'@', '+', '*', '/', '^', '?', '<', '>', '=' ->
				takeOperator(ch)

			else ->
				raise(Loc.singleChar(pos), Err.UnrecognizedCharacter(ch))
		}
	}

	data class PosNext(val pos: Pos, val next: Token)
	protected fun posNext(): PosNext {
		val p = pos
		return PosNext(p, nextToken())
	}

	fun escape(escaped: Char) =
		when (escaped) {
			'"', '{' -> escaped
			'n' -> '\n'
			't' -> '\t'
			else -> TODO("Compile error: bad escape")
		}

}
