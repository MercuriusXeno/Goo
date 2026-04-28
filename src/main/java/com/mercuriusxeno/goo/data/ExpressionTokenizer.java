package com.mercuriusxeno.goo.data;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizes GooValue expression strings into operator/operand token lists.
 * Recognizes namespaced IDs (with optional .type suffix), $constants,
 * numeric literals, and arithmetic operators.
 */
final class ExpressionTokenizer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Log: unexpected character in expression.
     */
    private static final String LOG_UNEXPECTED_CHAR = "Unexpected character '{}' in GooValue expression: {}";

    /**
     * Utility class, not instantiable.
     */
    private ExpressionTokenizer() {
    }

    /**
     * Tokenizes an expression, recognizing namespaced IDs (with optional .type suffix).
     *
     * @param expr the expression string to tokenize
     * @return ordered list of tokens
     */
    static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            i = scanNextToken(expr, i, tokens);
        }
        return tokens;
    }

    /**
     * Classifies the character at position i and scans the appropriate token type.
     *
     * @param expr   the full expression string
     * @param i      current scan position
     * @param tokens list to append the scanned token to
     * @return the position after the scanned token
     */
    private static int scanNextToken(String expr, int i, List<String> tokens) {
        char c = expr.charAt(i);
        if (Character.isWhitespace(c) || isOperatorOrParen(c)) {
            return scanSingleChar(c, i, tokens);
        }
        return scanValueToken(expr, i, c, tokens);
    }

    /**
     * Appends a single-character operator/paren token (skips whitespace) and advances past it.
     *
     * @param c      the character to possibly add
     * @param i      current scan position
     * @param tokens list to append the token to (whitespace is not appended)
     * @return the position after the character
     */
    private static int scanSingleChar(char c, int i, List<String> tokens) {
        if (!Character.isWhitespace(c)) {
            tokens.add(String.valueOf(c));
        }
        return i + 1;
    }

    /**
     * Scans a value-bearing token: $constant, numeric literal, or namespaced ID.
     * Logs a warning and skips unrecognized characters.
     *
     * @param expr   the full expression string
     * @param i      current scan position
     * @param c      the character at position i
     * @param tokens list to append the scanned token to
     * @return the position after the scanned token
     */
    private static int scanValueToken(String expr, int i, char c, List<String> tokens) {
        if (c == '$') {
            return scanConstant(expr, i, tokens);
        }
        if (Character.isDigit(c)) {
            return scanNumber(expr, i, tokens);
        }
        return scanWordOrSkip(expr, i, c, tokens);
    }

    /**
     * Scans a namespaced ID if the character is a letter, otherwise logs a warning
     * for the unrecognized character and advances past it.
     *
     * @param expr   the full expression string
     * @param i      current scan position
     * @param c      the character at position i
     * @param tokens list to append the scanned token to
     * @return the position after the scanned token or skipped character
     */
    private static int scanWordOrSkip(String expr, int i, char c, List<String> tokens) {
        if (Character.isLetter(c)) {
            return scanNamespacedId(expr, i, tokens);
        }
        LOGGER.warn(LOG_UNEXPECTED_CHAR, c, expr);
        return i + 1;
    }

    /**
     * Returns true if the character is an operator or parenthesis token.
     *
     * @param c the character to test
     * @return true if c is one of ( ) + - * /
     */
    private static boolean isOperatorOrParen(char c) {
        return isParen(c) || isArithmeticOp(c);
    }

    /**
     * Returns true if the character is an opening or closing parenthesis.
     *
     * @param c the character to test
     * @return true if c is ( or )
     */
    private static boolean isParen(char c) {
        return c == '(' || c == ')';
    }

    /**
     * Returns true if the character is an arithmetic operator.
     *
     * @param c the character to test
     * @return true if c is one of + - * /
     */
    private static boolean isArithmeticOp(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    /**
     * Scans a $constant token starting at position i (the dollar sign).
     * Allows dots for type extraction (e.g. $log.leaf).
     *
     * @param expr   the full expression string
     * @param i      current position (at the '$')
     * @param tokens list to append the scanned token to
     * @return the position after the constant token
     */
    private static int scanConstant(String expr, int i, List<String> tokens) {
        int pos = i;
        pos++;
        while (pos < expr.length() && (isIdentChar(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
            pos++;
        }
        tokens.add(expr.substring(i, pos));
        return pos;
    }

    /**
     * Scans a numeric literal starting at position i.
     *
     * @param expr   the full expression string
     * @param i      current position (at the first digit)
     * @param tokens list to append the scanned token to
     * @return the position after the number
     */
    private static int scanNumber(String expr, int i, List<String> tokens) {
        int pos = i;
        pos++;
        while (pos < expr.length() && Character.isDigit(expr.charAt(pos))) {
            pos++;
        }
        tokens.add(expr.substring(i, pos));
        return pos;
    }

    /**
     * Scans a namespaced ID token (letters/digits/underscore, colon, path chars, optional .type).
     *
     * @param expr   the full expression string
     * @param i      current position (at the first letter)
     * @param tokens list to append the scanned token to
     * @return the position after the namespaced ID
     */
    private static int scanNamespacedId(String expr, int i, List<String> tokens) {
        int pos = i;
        pos++;
        while (pos < expr.length() && isNamespacedIdChar(expr.charAt(pos))) {
            pos++;
        }
        tokens.add(expr.substring(i, pos));
        return pos;
    }

    /**
     * Returns true if the character is valid in a $constant identifier.
     *
     * @param c the character to test
     * @return true if alphanumeric or underscore
     */
    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Characters valid in a namespaced ID token: namespace:path.type
     *
     * @param c the character to test
     * @return true if valid in a namespaced ID
     */
    private static boolean isNamespacedIdChar(char c) {
        return Character.isLetterOrDigit(c) || isIdPunctuation(c);
    }

    /**
     * Returns true if the character is a punctuation mark valid in namespaced IDs.
     *
     * @param c the character to test
     * @return true if c is one of _ : . / -
     */
    private static boolean isIdPunctuation(char c) {
        return c == '_' || c == ':' || c == '.' || isIdSeparator(c);
    }

    /**
     * Returns true if the character is a path separator or hyphen used in namespaced IDs.
     *
     * @param c the character to test
     * @return true if c is / or -
     */
    private static boolean isIdSeparator(char c) {
        return c == '/' || c == '-';
    }
}
