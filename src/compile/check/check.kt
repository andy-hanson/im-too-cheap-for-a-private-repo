package compile.check

import compile.err.*
import u.*
import n.*


internal fun makeClass(imported: Arr<Module>, ast: ast.Klass): NzClass {
	val baseScope = BaseScope(imported)
	val klass = makeEmptyClass(baseScope, ast)
	fillInClass(baseScope, ast, klass)
	return klass
}

private class BaseScope(imported: Arr<Module>) {
	private val imports: Lookup<Sym, NzClass> = Lookup.buildFromArr(imported) { _, i -> Pair(i.name, i.klass) }

	fun getTy(ast: ast.Ty): Ty =
		when (ast) {
			is ast.Ty.Access -> {
				accessTy(ast.loc, ast.name)
			}
			is ast.Ty.Inst -> {
				accessTy(ast.instantiated.loc, ast.instantiated.name)
				ast.tyArgs.map(this::getTy)
				TODO("!!")
			}
		}

	private fun accessTy(loc: Loc, name: Sym): Ty {
		val klass = imports[name]
		if (klass != null)
			return klass

		val builtin = Builtin.allMembers[name] ?: raise(loc, Err.CantBind(name))
		return when (builtin) {
			is Ty -> builtin
			else -> TODO("err")
		}
	}
}

private fun makeEmptyClass(scope: BaseScope, ast: ast.Klass): NzClass =
	EmptyClassMaker(scope).emptyClass(ast)
private class EmptyClassMaker(private val scope: BaseScope) {
	fun emptyClass(k: ast.Klass): NzClass {
		val head = when (k.head) {
			is ast.Klass.Head.Record -> {
				NzClass.Head.Record(k.head.loc, k.head.vars.map {
					val (loc, mutable, ty, name) = it
					Slot(loc, mutable, getTy(ty), name)
				})
			}
		}
		val members = Lookup.beeld<Sym, Member> {
			for (memberAst in k.members) {
				val name = memberAst.name
				addOrFail(name, emptyMember(memberAst)) { Error("Duplicate member name $name") }
			}
		}
		return NzClass(k.name, head, members)
	}

	private fun emptyMember(m: ast.Member) =
		when (m) {
			is ast.Method -> {
				val (loc, isStatic, returnTy, name, parameters) = m
				Method(loc, name, isStatic, getTy(returnTy), parameters.map {
					val (pLoc, pTy, pName) = it
					Method.Parameter(pLoc, getTy(pTy), pName)
				})
			}
		}

	private fun getTy(ty: ast.Ty): Ty =
		scope.getTy(ty)
}

private fun fillInClass(baseScope: BaseScope, ast: ast.Klass, klass: NzClass) {
	for (memberAst in ast.members) {
		val member = klass.members.get(memberAst.name)
		when (memberAst) {
			is ast.Method -> {
				checkMethod(baseScope, memberAst, member as Method)
			}
			else -> TODO()
		}
	}
}

private fun checkMethod(baseScope: BaseScope, ast: ast.Method, method: Method) {
	MethodChecker(baseScope, method).checkMethod(method, ast)
}

private sealed class Expected {
	class SubTypeOf(val ty: Ty): Expected()
	class Infer : Expected() {
		/** Holds the inferred type. Multiple assignments must match. */
		private var ty: Ty? = null

		fun get(): Ty =
			ty!!

		fun set(loc: Loc, inferredTy: Ty) {
			val t = ty
			if (t == null)
				ty = inferredTy
			else
				// In a cs or ts, we pass the same Infer to multiple branches.
				// Every branch must have the same type.
				must(t == inferredTy, loc, Err.CombineTypes(t, inferredTy))
		}
	}
}



private class MethodChecker(private val baseScope: BaseScope, private val method: Method) {
	//Track scope
	//Note that the Access stored here will be copied to have its loc changed.
	private val scope = HashMap<Sym, Access>()

	init {
		for (p in method.parameters) {
			addToScope(Access.Parameter(p.loc, p))
		}
	}

	fun checkMethod(method: Method, ast: ast.Method) {
		check(Expected.SubTypeOf(method.returnTy), ast.body)
	}

	private fun get(loc: Loc, name: Sym): Access {
		val v = scope[name] ?: raise(loc, Err.CantBind(name))
		return when (v) {
			is Access.Local -> v.copy(loc = loc)
			is Access.Parameter -> v.copy(loc = loc)
		}
	}

	private fun getProperty(exprAst: ast.GetProperty): Pair<Expr, Member> {
		val (loc, targetAst, propertyName) = exprAst
		val (targetTy, target) = checkAndInfer(targetAst)
		return Pair(target, getMember(loc, targetTy, propertyName))
	}

	private fun checkCall(callLoc: Loc, method: Method, argAsts: Arr<ast.Expr>): Arr<Expr> {
		if (method.arity != argAsts.size)
			raise(callLoc, Err.WrongNumberOfArguments(method, argAsts.size))
		return method.parameters.zip(argAsts) { parameter, argAst ->
			check(Expected.SubTypeOf(parameter.ty), argAst)
		}
	}

	private fun check(expected: Expected, exprAst: ast.Expr): Expr =
		when (exprAst) {
			is ast.Access -> {
				val (loc, name) = exprAst
				returning(get(loc, name)) { checkAny(loc, expected, it.ty()) }
			}

			is ast.Call -> {
				val (loc, targetAst, argAsts) = exprAst
				val x = when (targetAst) {
					is ast.GetProperty -> {
						val (target, member) = getProperty(targetAst)
						when (member) {
							is Method -> {
								val args = checkCall(loc, method, argAsts)
								MethodCall(loc, target, member, args)
							}
							else ->
								TODO()
						}
					}
					is ast.Access ->
						TODO()
					else ->
						TODO() // Don't think anything else is allowed here.
				}
				returning(x) { checkAny(loc, expected, x.ty()) }
			}

			// If we got here, assume 'Call' did not handle it specially.
			is ast.GetProperty -> {
				val loc = exprAst.loc
				val (target, member) = getProperty(exprAst)
				val x = when (member) {
					is Slot ->
						GetSlot(loc, target, member)
					is Method ->
						//GetMethod ast. When compiling this we'll have to create a class subclassing Function.
						TODO()
					else ->
						TODO()
				}
				returning(x) { checkAny(loc, expected, it.ty()) }
			}

			is ast.Let -> {
				val (loc, patternAst, valueAst, thenAst) = exprAst
				val (valueTy, value) = checkAndInfer(valueAst)
				val (pattern, expr) = checkPattern(valueTy, patternAst) {
					check(expected, thenAst)

				}
				Let(loc, pattern, value, expr)
			}

			is ast.Seq -> {
				val (loc, firstAst, thenAst) = exprAst
				val first = check(Expected.SubTypeOf(Prim.Void), firstAst)
				val then = check(expected, thenAst)
				Seq(loc, first, then)
			}

			is ast.Literal -> {
				TODO()
			}

		}

	private fun addToScope(access: Access): Unit {
		val name = access.name
		scope.addOr(name, access) { raise(access.loc, Err.NameAlreadyBound(name)) }
	}

	private fun<T> checkPattern(ty: Ty, patternAst: ast.Pattern, action: () -> T): Pair<Pattern, T> {
		fun recursivelyAdd(patternAst: ast.Pattern): Pattern =
			when (patternAst) {
				is ast.Pattern.Ignore ->
					Pattern.Ignore(patternAst.loc)
				is ast.Pattern.Single -> {
					val (loc, name) = patternAst
					val s = Pattern.Single(loc, ty, name)
					addToScope(Access.Local(s.loc, s))
					s
				}
				is ast.Pattern.Destruct ->
					TODO()
			}

		val pattern = recursivelyAdd(patternAst)

		val result = action()

		fun recursivelyDelete(pattern: Pattern) {
			when (pattern) {
				is Pattern.Ignore -> {}
				is Pattern.Single ->
					scope.mustRemove(pattern.name)
				is Pattern.Destruct ->
					for (p in pattern.destructedInto)
						recursivelyDelete(p)
			}
		}

		recursivelyDelete(pattern)

		return Pair(pattern, result)
	}

	private fun checkAndInfer(exprAst: ast.Expr): Pair<Ty, Expr> {
		val infer = Expected.Infer()
		val expr = check(infer, exprAst)
		return Pair(infer.get(), expr)
	}

	private fun checkAny(loc: Loc, expected: Expected, actualTy: Ty) {
		when (expected) {
			is Expected.SubTypeOf ->
				checkType(loc, expected.ty, actualTy)
			is Expected.Infer ->
				expected.set(loc, actualTy)
		}
	}
}


private fun getMember(loc: Loc, ty: Ty, name: Sym): Member =
	when (ty) {
		is NzClass -> {
			ty.members[name] ?: raise(loc, Err.NoSuchMember(ty, name))
		}
		is GenInst ->
			TODO()
		is Prim ->
			TODO()
	}


private fun checkType(loc: Loc, expected: Ty, actual: Ty) {
	if (expected != actual) {
		raise(loc, Err.WrongType(expected, actual))
	}
}


class Checker {
	/*
Here's what you can access:

* Local variables to a closure
* Parameters to a closure
* Local variables to a function
* Parameters to a function (incl. type parameters)
* Entities in the current class (incl. its type parameters, slots, methods)
* Imports

Currently, classes cannot define inner types. So we get those early!

*/

	/*
SO:
base scope (constant throughout entire module) is:

* Imports
* Current class members


	 */

}
