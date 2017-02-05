package compile.check

import compile.err.*
import u.*
import n.*


internal fun makeClass(imported: Arr<Module>, ast: ast.Klass): Klass {
	val baseScope = BaseScope(imported)
	val klass = makeEmptyClass(baseScope, ast)
	fillInClass(baseScope, ast, klass)
	return klass
}

private class BaseScope(imported: Arr<Module>) {
	private val imports: Lookup<Sym, Klass> = Lookup.buildFrom(imported) { i -> i.name to i.klass }

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

	private fun accessTy(loc: Loc, name: Sym): Ty =
		imports[name] ?: Builtin.all[name] ?: raise(loc, Err.CantBind(name))
}

private fun makeEmptyClass(scope: BaseScope, ast: ast.Klass): Klass =
	EmptyClassMaker(scope).emptyClass(ast)
private class EmptyClassMaker(private val scope: BaseScope) {
	fun emptyClass(classAst: ast.Klass): Klass {
		val (loc, name, headAst, memberAsts) = classAst
		val klass = Klass(loc, name)
		val head = when (headAst) {
			is ast.Klass.Head.Slots -> {
				Klass.Head.Slots(headAst.loc, headAst.vars.map {
					val (slotLoc, mutable, ty, slotName) = it
					Slot(klass, slotLoc, mutable, getTy(ty), slotName)
				})
			}
		}
		klass.head = head
		klass.setMembers(Lookup.beeld<Sym, Member> {
			fun add(member: Member) {
				addOrFail(member.name, member) { raise(member.loc, Err.DuplicateMember(member.name)) }
			}

			for (slot in head.slots) {
				add(slot)
			}

			for (memberAst in memberAsts) {
				add(emptyMember(klass, memberAst))
			}
		})
		return klass
	}

	private fun emptyMember(klass: Klass, m: ast.Member) =
		when (m) {
			is ast.Method -> {
				val (loc, isStatic, returnTy, name, parameters) = m
				MethodWithBody(klass, loc, isStatic, getTy(returnTy), name, parameters.map {
					val (pLoc, pTy, pName) = it
					NzMethod.Parameter(pLoc, getTy(pTy), pName)
				})
			}
		}

	private fun getTy(ty: ast.Ty): Ty =
		scope.getTy(ty)
}

private fun fillInClass(baseScope: BaseScope, ast: ast.Klass, klass: Klass) {
	for (memberAst in ast.members) {
		val member = klass.getMember(memberAst.name)!!
		when (memberAst) {
			is ast.Method -> {
				val method = member as MethodWithBody
				method.body = MethodChecker(baseScope, method).checkMethod(method, memberAst)
			}
			else -> TODO()
		}
	}
}


private sealed class Expected {
	object Void : Expected()
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



private class MethodChecker(private val baseScope: BaseScope, method: NzMethod) {
	//Track scope
	//Note that the Access stored here will be copied to have its loc changed.
	private val scope = HashMap<Sym, Access>()

	init {
		for (p in method.parameters) {
			addToScope(Access.Parameter(p.loc, p))
		}
	}

	fun checkMethod(method: NzMethod, ast: ast.Method): Expr =
		check(Expected.SubTypeOf(method.returnTy), ast.body)

	private fun get(loc: Loc, name: Sym): Access {
		val v = scope[name] ?: raise(loc, Err.CantBind(name))
		return when (v) {
			is Access.Local -> v.copy(loc = loc)
			is Access.Parameter -> v.copy(loc = loc)
		}
	}

	private fun getProperty(loc: Loc, targetAst: ast.Expr, propertyName: Sym): Pair<Expr, Member> {
		val (targetTy, target) = checkAndInfer(targetAst)
		return Pair(target, getMember(loc, targetTy, propertyName))
	}

	private fun getProperty(exprAst: ast.GetProperty): Pair<Expr, Member> {
		val (loc, targetAst, propertyName) = exprAst
		return getProperty(loc, targetAst, propertyName)
	}

	private fun checkCall(callLoc: Loc, method: NzMethod, argAsts: Arr<ast.Expr>): Arr<Expr> {
		if (method.arity != argAsts.size)
			raise(callLoc, Err.WrongNumberOfArguments(method, argAsts.size))
		return method.parameters.zip(argAsts) { parameter, argAst ->
			check(Expected.SubTypeOf(parameter.ty), argAst)
		}
	}

	private fun callMethod(loc: Loc, targetAst: ast.Expr, methodName: Sym, argAsts: Arr<ast.Expr>): MethodCall {
		val (target, member) = getProperty(loc, targetAst, methodName)
		return when (member) {
			is NzMethod -> {
				val args = checkCall(loc, member, argAsts)
				MethodCall(loc, target, member, args)
			}
			else ->
				TODO()
		}
	}

	private fun check(expected: Expected, exprAst: ast.Expr): Expr =
		when (exprAst) {
			is ast.Access -> {
				val (loc, name) = exprAst
				returning(get(loc, name)) { checkAny(loc, expected, it.ty) }
			}

			is ast.OperatorCall -> {
				val (loc, left, op, right) = exprAst
				callMethod(loc, left, op, Arr.of(right))
			}

			is ast.Call -> {
				val (loc, targetAst, argAsts) = exprAst
				val x = when (targetAst) {
					is ast.GetProperty -> {
						val (loc2, targetAst2, propertyName) = targetAst //TODO:names
						callMethod(loc2, targetAst2, propertyName, argAsts)
					}
					is ast.Access ->
						TODO()
					else ->
						TODO() // Don't think anything else is allowed here.
				}
				returning(x) { checkAny(loc, expected, x.ty) }
			}

			// If we got here, assume 'Call' did not handle it specially.
			is ast.GetProperty -> {
				val loc = exprAst.loc
				val (target, member) = getProperty(exprAst)
				val x = when (member) {
					is Slot ->
						GetSlot(loc, target, member)
					is NzMethod ->
						//GetMethod ast. When compiling this we'll have to create a class subclassing Function.
						TODO()
					else ->
						TODO()
				}
				returning(x) { checkAny(loc, expected, it.ty) }
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
				val first = check(Expected.Void, firstAst)
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
		is ClassLike -> {
			ty.getMember(name) ?: raise(loc, Err.NoSuchMember(ty, name))
		}
		//is GenInst ->
		//	TODO()
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
