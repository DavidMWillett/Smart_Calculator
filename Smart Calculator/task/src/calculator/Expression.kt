package calculator

import java.math.BigInteger

/**
 * Represents an arithmetic expression.
 *
 * This class represents the arithmetic expression specified by the list of tokens passed in the constructor. Its
 * only public method is evaluate(), which returns the result of the expression or throws an exception.
 */
class Expression(tokens: List<Token>) {
    val elements = parse(tokens)
    private var parenthesisCount = 0

    private fun parse(tokens: List<Token>): List<Element> {
        val tokenIterator = tokens.listIterator()
        val elements = mutableListOf<Element>()
        var lastToken: Token? = null
        while (tokenIterator.hasNext()) {
            val token = tokenIterator.next()
            val thisElement = when (token) {
                is Token.Integer -> Number(token.value)
                is Token.Identifier -> Variable(token.name)
                is Token.Symbol -> symbol(token.char, lastToken)
            }
            elements.add(thisElement)
            lastToken = token
        }
        if (parenthesisCount != 0) throw InvalidExpressionException()
        return elements
    }

    private fun symbol(symbol: Char, lastToken: Token?): Element {
        val isBinary = lastToken is Token.Integer || lastToken is Token.Identifier ||
                lastToken is Token.Symbol && lastToken.char == ')'
        return when (symbol) {
            '+' -> if (isBinary) BinaryPlusOperator() else UnaryPlusOperator()
            '-' -> if (isBinary) BinaryMinusOperator() else UnaryMinusOperator()
            '*' -> if (isBinary) TimesOperator() else throw InvalidExpressionException()
            '/' -> if (isBinary) DivOperator() else throw InvalidExpressionException()
            '^' -> if (isBinary) PowerOperator() else throw InvalidExpressionException()
            '(' -> { parenthesisCount++; LeftParenthesis() }
            ')' -> { parenthesisCount--; RightParenthesis() }
            else -> throw InvalidExpressionException()
        }
    }

    fun evaluate(): BigInteger {
        val postfixExpression = PostfixExpression(this)
        return postfixExpression.evaluate()
    }
}

class PostfixExpression(infixExpression: Expression) {
    private val postfixElements = mutableListOf<Element>()
    private val operatorStack = MutableStack<Operator>()

    init {
        scanInfix(infixExpression)
        emptyOperatorStack()
    }

    private fun scanInfix(infixExpression: Expression) {
        infixExpression.elements.forEach {
            when (it) {
                is Operand -> postfixElements += it
                is ArithmeticOperator -> processOperator(it)
                is LeftParenthesis -> operatorStack.push(it)
                is RightParenthesis -> {
                    while (operatorStack.peek() !is LeftParenthesis) {
                        postfixElements += operatorStack.pop()
                    }
                    operatorStack.pop()
                }
            }
        }
    }

    private fun processOperator(op: ArithmeticOperator) {
        loop@ while (!operatorStack.isEmpty()) {
            when (val topOfStack = operatorStack.peek()) {
                is LeftParenthesis -> break@loop
                is ArithmeticOperator -> {
                    if (op.precedence <= topOfStack.precedence) {
                        postfixElements += operatorStack.pop()
                    } else {
                        break@loop
                    }
                }
            }
        }
        operatorStack.push(op)
    }

    private fun emptyOperatorStack() {
        while (!operatorStack.isEmpty()) {
            if (operatorStack.peek() is Parenthesis) {
                throw InvalidExpressionException()
            } else {
                postfixElements += operatorStack.pop()
            }
        }
    }

    fun evaluate(): BigInteger {
        val operandStack = MutableStack<Operand>()
        postfixElements.forEach {
            when (it) {
                is Operand -> operandStack.push(it)
                is ArithmeticOperator -> it.apply(operandStack)
            }
        }
        return operandStack.pop().value
    }
}

class BinaryPlusOperator : BinaryOperator(precedence = 1) {
    override fun execute(op1: BigInteger, op2: BigInteger) = op1 + op2
}

class BinaryMinusOperator : BinaryOperator(precedence = 1) {
    override fun execute(op1: BigInteger, op2: BigInteger) = op1 - op2
}

class TimesOperator : BinaryOperator(precedence = 2) {
    override fun execute(op1: BigInteger, op2: BigInteger) = op1 * op2
}

class DivOperator : BinaryOperator(precedence = 2) {
    override fun execute(op1: BigInteger, op2: BigInteger) = op1 / op2
}

class PowerOperator : BinaryOperator(precedence = 3) {
    override fun execute(op1: BigInteger, op2: BigInteger): BigInteger = op1.pow(op2.toInt())
}

abstract class BinaryOperator(precedence: Int) : ArithmeticOperator(precedence) {
    override fun apply(stack: MutableStack<Operand>) {
        val op2 = stack.pop()
        val op1 = stack.pop()
        stack.push(Number(execute(op1.value, op2.value)))
    }
    abstract fun execute(op1: BigInteger, op2: BigInteger): BigInteger
}

class UnaryPlusOperator : UnaryOperator(precedence = 3) {
    override fun execute(op: BigInteger) = op
}

class UnaryMinusOperator : UnaryOperator(precedence = 3) {
    override fun execute(op: BigInteger) = -op
}

abstract class UnaryOperator(precedence: Int) : ArithmeticOperator(precedence) {
    override fun apply(stack: MutableStack<Operand>) {
        val op = stack.pop()
        stack.push(Number(execute(op.value)))
    }
    abstract fun execute(op: BigInteger): BigInteger
}

abstract class ArithmeticOperator(val precedence: Int) : Operator {
    abstract fun apply(stack: MutableStack<Operand>)
}

class LeftParenthesis : Parenthesis

class RightParenthesis : Parenthesis

interface Parenthesis : Operator

interface Operator : Element

class Number(override val value: BigInteger) : Operand

class Variable(private val name: String) : Operand {
    override val value: BigInteger
        get() = Calculator.variables[name] ?: throw UnknownVariableException()
}

interface Operand : Element {
    val value: BigInteger
}

interface Element

class InvalidExpressionException : Exception()
class InvalidIdentifierException : Exception()
class InvalidAssignmentException : Exception()
class UnknownVariableException : Exception()

class MutableStack<E> {
    private val elements = mutableListOf<E>()

    fun push(element: E) = elements.add(element)
    fun peek(): E = elements.last()
    fun pop(): E = elements.removeAt(elements.size - 1)
    fun isEmpty() = elements.isEmpty()
}
