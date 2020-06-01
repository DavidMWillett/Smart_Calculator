package calculator

import java.math.BigInteger
import java.util.ArrayDeque
import java.util.Deque

/**
 * Represents an arithmetic expression.
 *
 * This class represents the arithmetic expression specified by the list of tokens passed in the constructor. Its
 * only public method is evaluate(), which returns the result of the expression or throws an exception.
 */
class Expression(tokens: List<Token>) {
    private val elements = parse(tokens)
    private var parenthesisCount = 0

    private fun parse(tokens: List<Token>): List<Element> {
        val elements = mutableListOf<Element>()
        var previous: Token? = null
        tokens.forEach {
            elements.add(when (it) {
                is Token.Integer -> Number(it.value)
                is Token.Identifier -> Variable(it.name)
                is Token.Symbol -> symbol(it.char, previous)
            })
            previous = it
        }
        if (parenthesisCount != 0) throw InvalidExpressionException()
        return elements
    }

    private fun symbol(symbol: Char, previous: Token?): Element {
        val isBinary = previous is Token.Integer || previous is Token.Identifier ||
                previous is Token.Symbol && previous.char == ')'
        return when (symbol) {
            '+' -> if (isBinary) BinaryPlusOperator() else UnaryPlusOperator()
            '-' -> if (isBinary) BinaryMinusOperator() else UnaryMinusOperator()
            '*' -> if (isBinary) TimesOperator() else throw InvalidExpressionException()
            '/' -> if (isBinary) DivOperator() else throw InvalidExpressionException()
            '^' -> if (isBinary) PowerOperator() else throw InvalidExpressionException()
            '(' -> {
                parenthesisCount++; LeftParenthesis()
            }
            ')' -> {
                parenthesisCount--; RightParenthesis()
            }
            else -> throw InvalidExpressionException()
        }
    }

    fun evaluate() = PostfixExpression(elements).evaluate()

    /**
     * An arithmetic expression in postfix form.
     *
     * Nested class representing a postfix arithmetic expression. Instantiated and initialized by the outer
     * Expression object in order to evaluate the expression represented by that Expression object.
     */
    class PostfixExpression(infixElements: List<Element>) {
        private val operatorStack = ArrayDeque<Operator>()
        private val postfixElements = scanInfix(infixElements)

        private fun scanInfix(infixElements: List<Element>): Deque<Element> {
            val postfix = ArrayDeque<Element>()
            infixElements.forEach {
                when (it) {
                    is Operand -> postfix.add(it)
                    is ArithmeticOperator -> processOperator(postfix, it)
                    is LeftParenthesis -> operatorStack.push(it)
                    is RightParenthesis -> {
                        while (operatorStack.peek() !is LeftParenthesis) {
                            postfix.add(operatorStack.pop())
                        }
                        operatorStack.pop()
                    }
                }
            }
            addRemainingOperators(postfix)
            return postfix
        }

        private fun processOperator(postfix: Deque<Element>, op: ArithmeticOperator) {
            loop@ while (!operatorStack.isEmpty()) {
                when (val topOfStack = operatorStack.peek()) {
                    is LeftParenthesis -> break@loop
                    is ArithmeticOperator -> {
                        if (op.precedence <= topOfStack.precedence) {
                            postfix.add(operatorStack.pop())
                        } else {
                            break@loop
                        }
                    }
                }
            }
            operatorStack.push(op)
        }

        private fun addRemainingOperators(postfix: Deque<Element>) {
            while (!operatorStack.isEmpty()) {
                if (operatorStack.peek() is Parenthesis) {
                    throw InvalidExpressionException()
                } else {
                    postfix.add(operatorStack.pop())
                }
            }
        }

        fun evaluate(): BigInteger {
            val operandStack = ArrayDeque<Operand>()
            postfixElements.forEach {
                when (it) {
                    is Operand -> operandStack.push(it)
                    is ArithmeticOperator -> it.apply(operandStack)
                }
            }
            return operandStack.pop().value
        }
    }

    // Subclasses of Element representing all the different types of Element than can be part of the list
    // representing an infix or postfix arithmetic expression.

    class Number(override val value: BigInteger) : Operand

    class Variable(private val name: String) : Operand {
        override val value: BigInteger
            get() = Calculator.variables[name] ?: throw UnknownVariableException()
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

    class UnaryPlusOperator : UnaryOperator(precedence = 4) {
        override fun execute(op: BigInteger) = op
    }

    class UnaryMinusOperator : UnaryOperator(precedence = 4) {
        override fun execute(op: BigInteger) = -op
    }

    class LeftParenthesis : Parenthesis

    class RightParenthesis : Parenthesis

    abstract class BinaryOperator(precedence: Int) : ArithmeticOperator(precedence) {
        override fun apply(stack: Deque<Operand>) {
            val op2 = stack.pop()
            val op1 = stack.pop()
            stack.push(Number(execute(op1.value, op2.value)))
        }

        abstract fun execute(op1: BigInteger, op2: BigInteger): BigInteger
    }

    abstract class UnaryOperator(precedence: Int) : ArithmeticOperator(precedence) {
        override fun apply(stack: Deque<Operand>) {
            val op = stack.pop()
            stack.push(Number(execute(op.value)))
        }

        abstract fun execute(op: BigInteger): BigInteger
    }

    abstract class ArithmeticOperator(val precedence: Int) : Operator {
        abstract fun apply(stack: Deque<Operand>)
    }

    interface Operand : Element {
        val value: BigInteger
    }

    interface Parenthesis : Operator

    interface Operator : Element

    interface Element
}
