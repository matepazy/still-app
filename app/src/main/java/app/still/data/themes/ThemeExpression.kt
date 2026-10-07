package app.still.data.themes

/** A closed, bounded expression interpreter. Never executes host code. */
sealed interface ThemeExpression {
    data class Literal(val value: Any?) : ThemeExpression
    data class Id(val name: String) : ThemeExpression
    data class Not(val value: ThemeExpression) : ThemeExpression
    data class Binary(val op: String, val left: ThemeExpression, val right: ThemeExpression) : ThemeExpression
    data class Choose(val condition: ThemeExpression, val yes: ThemeExpression, val no: ThemeExpression) : ThemeExpression

    fun evaluate(values: Map<String, Any?>): Any? = when (this) {
        is Literal -> value
        is Id -> values[name]
        is Not -> !boolean(value.evaluate(values))
        is Choose -> (if (boolean(condition.evaluate(values))) yes else no).evaluate(values)
        is Binary -> {
            val a = left.evaluate(values)
            when (op) {
                "??" -> a ?: right.evaluate(values)
                "&&" -> boolean(a) && boolean(right.evaluate(values))
                "||" -> boolean(a) || boolean(right.evaluate(values))
                "==" -> a == right.evaluate(values)
                "!=" -> a != right.evaluate(values)
                else -> {
                    val b = right.evaluate(values)
                    require(a is Double && b is Double) { "Ordering requires numbers" }
                    when (op) { "<" -> a < b; "<=" -> a <= b; ">" -> a > b; else -> a >= b }
                }
            }
        }
    }

    fun types(ids: Map<String, String>): Set<String> = when (this) {
        is Literal -> setOf(when (value) { null -> "null"; is String -> "string"; is Boolean -> "boolean"; else -> "number" })
        is Id -> setOf(requireNotNull(ids[name]) { "Undeclared request id $name" }, "null")
        is Not -> { require(value.types(ids) == setOf("boolean")) { "Use ?? to handle denied requests" }; setOf("boolean") }
        is Choose -> {
            require(condition.types(ids) == setOf("boolean")) { "Condition requires a boolean; use ?? for denied requests" }
            yes.types(ids) + no.types(ids)
        }
        is Binary -> {
            val a = left.types(ids); val b = right.types(ids)
            when (op) {
                "??" -> (a - "null") + b
                else -> {
                    if (op in listOf("&&", "||")) require(a == setOf("boolean") && b == a) { "Logical operators require booleans; use ??" }
                    if (op in listOf("<", "<=", ">", ">=")) require(a == setOf("number") && b == a) { "Ordering requires numbers; use ??" }
                    setOf("boolean")
                }
            }
        }
    }

    fun outputs(): Set<String> = when (this) {
        is Literal -> setOf(value as? String ?: error("Visual expressions must return literal strings"))
        is Choose -> yes.outputs() + no.outputs()
        else -> error("Visual expressions must return literal strings in every branch")
    }

    companion object {
        private fun boolean(value: Any?): Boolean = value as? Boolean ?: error("Condition requires a boolean")
        fun parse(source: String): ThemeExpression = ExpressionParser(source).parse()
    }
}

private class ExpressionParser(source: String) {
    private val tokenPattern = Regex("\"(?:[^\"\\\\\\r\\n]|\\\\[\"\\\\/bfnrt]|\\\\u[0-9a-fA-F]{4})*\"|-?(?:0|[1-9]\\d*)(?:\\.\\d+)?|[A-Za-z][A-Za-z0-9_]*|\\?\\?|&&|\\|\\||==|!=|<=|>=|[!<>()?:]")
    private val tokens = mutableListOf<String>()
    private var index = 0
    private var depth = 0
    init {
        require(source.length <= 2048) { "Expression exceeds 2048 characters" }
        var rest = source.trim()
        while (rest.isNotEmpty()) {
            val match = tokenPattern.find(rest)
            require(match != null && match.range.first == 0 && tokens.size < 256) { "Invalid or excessive expression tokens" }
            tokens += match.value; rest = rest.substring(match.value.length).trimStart()
        }
    }
    private fun take(token: String): Boolean = if (tokens.getOrNull(index) == token) { index++; true } else false
    private fun expect(token: String) { require(take(token)) { "Expected $token" } }
    fun parse(): ThemeExpression = choose().also { require(index == tokens.size) { "Unexpected expression token" } }
    private fun choose(): ThemeExpression {
        require(++depth <= 32) { "Expression nesting exceeds 32" }
        val condition = binary(0)
        val result = if (take("?")) { val yes = choose(); expect(":"); ThemeExpression.Choose(condition, yes, choose()) } else condition
        depth--; return result
    }
    private val operators = listOf(listOf("??"), listOf("||"), listOf("&&"), listOf("==", "!="), listOf("<", "<=", ">", ">="))
    private fun binary(level: Int): ThemeExpression {
        if (level == operators.size) return atom()
        var left = binary(level + 1)
        while (tokens.getOrNull(index) in operators[level]) {
            val op = tokens[index++]; left = ThemeExpression.Binary(op, left, binary(level + 1))
        }
        return left
    }
    private fun atom(): ThemeExpression {
        require(++depth <= 32) { "Expression nesting exceeds 32" }
        val result = when {
            take("!") -> ThemeExpression.Not(atom())
            take("(") -> choose().also { expect(")") }
            else -> {
                val token = tokens.getOrNull(index++) ?: error("Expected an expression")
                when {
                    token.startsWith('"') -> ThemeExpression.Literal(decodeThemeString(token))
                    token == "null" -> ThemeExpression.Literal(null)
                    token == "true" || token == "false" -> ThemeExpression.Literal(token == "true")
                    token.first().isDigit() || token.startsWith('-') -> ThemeExpression.Literal(token.toDouble().also { require(it.isFinite()) })
                    token.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) -> ThemeExpression.Id(token)
                    else -> error("Expected literal or request id")
                }
            }
        }
        depth--; return result
    }
}

internal fun decodeThemeString(raw: String): String {
    require(raw.startsWith('"') && raw.endsWith('"') && raw.length >= 2) { "Invalid quoted string" }
    val result = StringBuilder()
    var i = 1
    while (i < raw.lastIndex) {
        val char = raw[i++]
        require(char != '"' && char.code >= 32) { "Invalid quoted string" }
        if (char != '\\') result.append(char)
        else {
            require(i < raw.lastIndex)
            when (val escape = raw[i++]) {
                '"', '\\', '/' -> result.append(escape)
                'b' -> result.append('\b'); 'f' -> result.append('\u000C'); 'n' -> result.append('\n'); 'r' -> result.append('\r'); 't' -> result.append('\t')
                'u' -> { require(i + 4 <= raw.lastIndex); result.append(raw.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                else -> error("Invalid string escape")
            }
        }
    }
    return result.toString()
}
