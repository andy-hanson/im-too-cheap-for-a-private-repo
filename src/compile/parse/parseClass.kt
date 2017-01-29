package compile.parse

import u.*
import ast.*
import compile.lex.*

fun parseClass(source: Input): Class =
	Lexer(source).parseClass()
