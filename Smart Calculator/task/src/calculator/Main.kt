package calculator

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

    fun parseStatement(statement: String) {
        try {
            val lexer = Lexer(statement)
            val parser = Parser(lexer)
            val result = parser.evaluate()
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

class Lexer(text: String) {
    val iterator = text.toList().listIterator()

    fun scan(): List<Token> {
        val tokens = mutableListOf<Token>()
        loop@ while (iterator.hasNext()) {
            val character = iterator.next()
            when {
                character.isWhitespace() -> continue@loop
                character.isDigit() -> tokens.add(NumberToken(number(character)))
                character.isLetter() -> tokens.add(VariableToken(variable(character)))
                character == '=' -> tokens.add(AssignToken())
                character == '+' -> tokens.add(ArithmeticOperatorToken(ArithmeticOperator.PLUS))
                character == '-' -> tokens.add(ArithmeticOperatorToken(ArithmeticOperator.MINUS))
                else -> throw InvalidExpressionException()
            }
        }
        return tokens
    }

    private fun number(firstDigit: Char): Int {
        var result = firstDigit.toString()
        while (iterator.hasNext()) {
            val character = iterator.next()
            if (character.isDigit()) {
                result += character
            } else {
                iterator.previous()
                break
            }
        }
        return result.toInt()
    }

    private fun variable(firstLetter: Char): String {
        var result = firstLetter.toString()
        while (iterator.hasNext()) {
            val character = iterator.next()
            if (character.isLetter()) {
                result += character
            } else {
                iterator.previous()
                break
            }
        }
        return result
    }

    class NumberToken(val value: Int) : Token()

    class VariableToken(val value: String) : Token()

    class ArithmeticOperatorToken(val operator: ArithmeticOperator) : Token()

    class AssignToken : Token()

    abstract class Token
}

/**
 * Grammar:
 *
 * statement: assignment | expression
 * assignment: variable "=" expression
 * expression: factor (("+" | "-") factor)*
 * factor: ("+" | "-") factor | number | variable
 */

class Parser(lexer: Lexer) {
    private val tokens = lexer.scan()
    val iterator = tokens.iterator()

    fun evaluate(): Int? {
        val tree = if (tokens.any { it is Lexer.AssignToken }) assign() else expr()
        return tree.evaluate()
    }

    private fun assign(): Node {
        val variableToken = iterator.next()
        if (variableToken !is Lexer.VariableToken) throw InvalidIdentifierException()
        val assignToken = iterator.next()
        if (assignToken !is Lexer.AssignToken) throw InvalidIdentifierException()
        try {
            return AssignNode(VariableNode(variableToken.value), expr())
        } catch (e: InvalidExpressionException ) {
            throw InvalidAssignmentException()
        }
    }

    private fun expr(): Node {
        var node = factor()
        while (iterator.hasNext()) {
            val token = iterator.next()
            if (token is Lexer.ArithmeticOperatorToken) {
                node = BinaryArithmeticOperatorNode(token.operator, node, factor())
            } else {
                throw InvalidExpressionException()
            }
        }
        return node
    }

    private fun factor(): Node {
        return when (val token = iterator.next()) {
            is Lexer.NumberToken -> NumberNode(token.value)
            is Lexer.VariableToken -> VariableNode(token.value)
            is Lexer.ArithmeticOperatorToken -> UnaryArithmeticOperatorNode(token.operator, factor())
            else -> throw InvalidExpressionException()
        }
    }
}

class NumberNode(private val value: Int) : Node {
    override fun evaluate(): Int {
        return value
    }
}

class VariableNode(val name: String) : Node {
    override fun evaluate(): Int {
        val value = Calculator.variables[name]
        return value ?: throw UnknownVariableException()
    }
}

class AssignNode(left: Node, right: Node) : BinaryNode(left, right) {
    override fun evaluate(): Int? {
        if (left !is VariableNode) throw InvalidIdentifierException()
        val rightValue = right.evaluate() ?: throw InvalidAssignmentException()
        Calculator.variables[left.name] = rightValue
        return null
    }
}

class BinaryArithmeticOperatorNode(private val operator: ArithmeticOperator, left: Node, right: Node)
    : BinaryNode(left, right) {

    override fun evaluate(): Int? {
        val leftValue = left.evaluate() ?: throw InvalidExpressionException()
        val rightValue = right.evaluate() ?: throw InvalidExpressionException()
        return when (operator) {
            ArithmeticOperator.PLUS -> leftValue + rightValue
            ArithmeticOperator.MINUS -> leftValue - rightValue
        }
    }
}

class UnaryArithmeticOperatorNode(private val operator: ArithmeticOperator, private val operand: Node) : Node {
    override fun evaluate(): Int? {
        val operandValue = operand.evaluate() ?: throw InvalidExpressionException()
        return when (operator) {
            ArithmeticOperator.PLUS -> operandValue
            ArithmeticOperator.MINUS -> -operandValue
        }
    }
}

abstract class BinaryNode(val left: Node, val right: Node) : Node

interface Node {
    fun evaluate(): Int?
}

enum class ArithmeticOperator { PLUS, MINUS }

class InvalidExpressionException : Exception()
class InvalidIdentifierException : Exception()
class InvalidAssignmentException : Exception()
class UnknownVariableException : Exception()
