package calculator

import kotlin.math.pow

fun String.isCommand() = this.first() == '/'

fun main() {
    var finished = false
    do {
        val input = readLine()!!
        if (input.isEmpty()) continue
        if (input.isCommand()) {
            finished = Calculator.parseCommand(input)
            continue
        }
        Calculator.parseStatement(input)
    } while (!finished)
    println("Bye!")
}

object Calculator {
    val variables = mutableMapOf<String, Int>()

    private const val HELP: String = """
        This program evaluates expressions containing numbers and plus/minus operators. Unary minus is supported,
        and multiple identical operators in succession are evaluated correctly. It also provides for the assignment
        and use of variables.
    """

    fun parseCommand(command: String): Boolean {
        return when (command) {
            "/exit" -> true
            "/help" -> { println(HELP.trimIndent()); false }
            else -> { println("Unknown command"); false }
        }
    }

    fun parseStatement(input: String) {
        try {
            val statement = Statement(input)
            val result = statement.execute()
            if (result != null) println(result)
        } catch (e: InvalidExpressionException) {
            println("Invalid expression")
        } catch (e: InvalidIdentifierException) {
            println("Invalid identifier")
        } catch (e: InvalidAssignmentException) {
            println("Invalid assignment")
        } catch (e: UnknownVariableException) {
            println("Unknown variable")
        }
    }
}

class Statement(text: String) {
    private val tokens = Tokenizer.scan(text)

    /**
     * Executes the statement, returning an integer if the statement is an expression, otherwise null.
     * Throws the appropriate exception if an error is detected.
     */
    fun execute(): Int? {
        return if (tokens.any { it is Token.Symbol && it.char == '=' }) {
            val assignment = Assignment(tokens)
            assignment.execute()
            null
        } else {
            val expression = Expression(tokens)
            expression.evaluate()
        }
    }
}

class Assignment(private val tokens: List<Token>) {

    fun execute() {
        if (tokens.size != 3) throw InvalidAssignmentException()
        val identifier = tokens.component1()
        if (identifier !is Token.Identifier) throw InvalidAssignmentException()
        val assignment = tokens.component2()
        if (assignment !is Token.Symbol || assignment.char != '=') throw InvalidAssignmentException()
        val number = tokens.component3()
        if (number !is Token.Integer) throw InvalidAssignmentException()
        Calculator.variables[identifier.name] = number.value
        return
    }
}

class Expression(tokens: List<Token>) {
    private val elements = parse(tokens)
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

    fun evaluate(): Int {
        val postfixTokens = RPNConverter.convert(elements)
        return RPNCalculator.calculate(postfixTokens)
    }
}

object RPNConverter {
    private val postfixTokens = mutableListOf<Element>()
    private val operatorStack = MutableStack<Operator>()

    fun convert(infixElements: List<Element>): List<Element> {
        postfixTokens.clear()
        operatorStack.clear()
        scanInfix(infixElements)
        emptyOperatorStack()
        return postfixTokens
    }

    private fun scanInfix(infixElements: List<Element>) {
        infixElements.forEach {
            when (it) {
                is Operand -> postfixTokens += it
                is ArithmeticOperator -> processOperator(it)
                is LeftParenthesis -> operatorStack.push(it)
                is RightParenthesis -> {
                    while (operatorStack.peek() !is LeftParenthesis) {
                        postfixTokens += operatorStack.pop()
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
                        postfixTokens += operatorStack.pop()
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
                postfixTokens += operatorStack.pop()
            }
        }
    }
}

object RPNCalculator {
    fun calculate(postfixElements: List<Element>): Int {
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
    override fun execute(op1: Int, op2: Int) = op1 + op2
}

class BinaryMinusOperator : BinaryOperator(precedence = 1) {
    override fun execute(op1: Int, op2: Int) = op1 - op2
}

class TimesOperator : BinaryOperator(precedence = 2) {
    override fun execute(op1: Int, op2: Int) = op1 * op2
}

class DivOperator : BinaryOperator(precedence = 2) {
    override fun execute(op1: Int, op2: Int) = op1 / op2
}

class PowerOperator : BinaryOperator(precedence = 3) {
    override fun execute(op1: Int, op2: Int) = op1.toDouble().pow(op2).toInt()
}

abstract class BinaryOperator(precedence: Int) : ArithmeticOperator(precedence) {
    override fun apply(stack: MutableStack<Operand>) {
        val op2 = stack.pop()
        val op1 = stack.pop()
        stack.push(Number(execute(op1.value, op2.value)))
    }
    abstract fun execute(op1: Int, op2: Int): Int
}

class UnaryPlusOperator : UnaryOperator(precedence = 3) {
    override fun execute(op: Int) = op
}

class UnaryMinusOperator : UnaryOperator(precedence = 3) {
    override fun execute(op: Int) = -op
}

abstract class UnaryOperator(precedence: Int) : ArithmeticOperator(precedence) {
    override fun apply(stack: MutableStack<Operand>) {
        val op = stack.pop()
        stack.push(Number(execute(op.value)))
    }
    abstract fun execute(op: Int): Int
}

abstract class ArithmeticOperator(val precedence: Int) : Operator {
    abstract fun apply(stack: MutableStack<Operand>)
}

class LeftParenthesis : Parenthesis

class RightParenthesis : Parenthesis

interface Parenthesis : Operator

interface Operator : Element

class Number(override val value: Int) : Operand

class Variable(private val name: String) : Operand {
    override val value: Int
        get() = Calculator.variables[name] ?: throw UnknownVariableException()
}

interface Operand : Element {
    val value: Int
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
    fun clear() = elements.clear()
    fun isEmpty() = elements.isEmpty()
}
