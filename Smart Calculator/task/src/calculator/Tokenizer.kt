package calculator

import java.math.BigInteger

object Tokenizer {
    /**
     * Converts input [text] into a list of tokens representing the integers, identifiers (words) and symbols.
     * No semantic checking is performed here.
     */
    fun scan(text: String): List<Token> {
        val iterator = text.toList().listIterator()
        val tokens = mutableListOf<Token>()
        while (iterator.hasNext()) {
            val char = iterator.next()
            if (char.isWhitespace()) continue
            val thisToken = when {
                char.isDigit() -> Token.Integer(scanInteger(iterator, char))
                char.isLetter() -> Token.Identifier(scanVariable(iterator, char))
                else -> Token.Symbol(char)
            }
            tokens.add(thisToken)
        }
        return tokens
    }

    private fun scanInteger(iterator: ListIterator<Char>, firstDigit: Char): BigInteger {
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
        return result.toBigInteger()
    }

    private fun scanVariable(iterator: ListIterator<Char>, firstLetter: Char): String {
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
}

sealed class Token {
    class Integer(val value: BigInteger) : Token()
    class Identifier(val name: String) : Token()
    class Symbol(val char: Char) : Token()
}
