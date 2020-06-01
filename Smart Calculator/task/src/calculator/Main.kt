package calculator

import java.math.BigInteger

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
    val variables = mutableMapOf<String, BigInteger>()

    private const val HELP: String = """
        This program evaluates expressions containing integers and the plus, minus, times, divide and power operators.
        Unary minus is supported, and multiple identical operators in succession are evaluated correctly. It also
        provides for the assignment and use of variables.
    """

    fun parseCommand(command: String): Boolean {
        return when (command) {
            "/exit" -> true
            "/help" -> {
                println(HELP.trimIndent()); false
            }
            else -> {
                println("Unknown command"); false
            }
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

    class Statement(text: String) {
        private val tokens = Tokenizer.scan(text)

        /**
         * Executes this statement, returning an integer if the statement is an expression, otherwise null.
         * Throws the appropriate exception if an error is detected.
         */
        fun execute(): BigInteger? {
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

        /**
         * Executes this assignment. Throws an exception if the token list specified in the constructor was not of
         * the form identifier = number, where whitespace is optional.
         */
        fun execute() {
            if (tokens.size != 3) throw InvalidAssignmentException()
            val identifier = tokens.component1()
            if (identifier !is Token.Identifier) throw InvalidAssignmentException()
            val assignment = tokens.component2()
            if (assignment !is Token.Symbol || assignment.char != '=') throw InvalidAssignmentException()
            val number = tokens.component3()
            if (number !is Token.Integer) throw InvalidAssignmentException()
            variables[identifier.name] = number.value
            return
        }
    }
}
