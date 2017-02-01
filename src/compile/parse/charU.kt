package compile.parse

import u.*

fun inRange(ch: Char, min: Char, max: Char): Bool =
	min <= ch && ch <= max

fun isDigit(ch: Char): Bool =
	inRange(ch, '0', '9')

fun isOperatorChar(ch: Char): Bool =
	when (ch) {
		'@', '+', '-', '*', '/', '^', '?', '<', '>', '=' -> true
		else -> false
	}

fun isNameStartChar(ch: Char): Bool =
	inRange(ch, 'a', 'z') || inRange(ch, 'A', 'Z')

fun isNameChar(ch: Char): Bool =
	isNameStartChar(ch) || isDigit(ch) || isOperatorChar(ch)
