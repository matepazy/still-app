package app.still.data.themes

import java.security.MessageDigest
import java.time.LocalDate
import java.time.MonthDay

data class ThemeRequest(val source: String, val id: String, val type: String, val reason: String) {
    val description: String get() = ThemeCompose.requests.getValue(source).second
}
data class ThemeValue(val text: String, val expression: ThemeExpression? = null) {
    fun resolve(values: Map<String, Any?>): String = expression?.evaluate(values) as? String ?: text
    fun outputs(): Set<String> = expression?.outputs() ?: setOf(text)
}
data class ThemeDateRange(val mode: String, val from: String, val to: String) {
    fun contains(date: LocalDate): Boolean = if (mode == "once") date >= LocalDate.parse(from) && date <= LocalDate.parse(to)
    else {
        val day = MonthDay.from(date); val start = MonthDay.parse("--$from"); val end = MonthDay.parse("--$to")
        if (start <= end) day >= start && day <= end else day >= start || day <= end
    }
}
data class CommunityTheme(
    val id: String, val version: String, val title: String, val author: String,
    val description: String, val license: String, val requests: List<ThemeRequest>,
    val light: Map<String, ThemeValue>, val dark: Map<String, ThemeValue>,
    val images: Map<String, ThemeValue>, val wordmark: ThemeValue?, val availability: ThemeDateRange?, val updatable: Boolean,
) {
    fun available(date: LocalDate = LocalDate.now()): Boolean = availability?.contains(date) ?: true
}

object ThemeCompose {
    const val MAX_SOURCE = 1024 * 1024
    val requests = mapOf(
        "sysLightMode" to ("string" to "System appearance (light or dark)"),
        "sysReducedMotion" to ("boolean" to "System preference for reduced animation"),
        "sysFontScale" to ("number" to "System text size multiplier"),
    )
    val roles = setOf("primary", "on-primary", "background", "on-background", "surface", "on-surface")
    private val keyPattern = Regex("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")
    private val colorPattern = Regex("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?")
    val assetPattern = Regex("assets/[a-zA-Z0-9][a-zA-Z0-9_./-]*\\.(png|jpg|jpeg|webp)", RegexOption.IGNORE_CASE)
    private data class Node(val line: Int, val value: String? = null, val children: MutableMap<String, Node> = linkedMapOf())

    fun parse(source: String): CommunityTheme {
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_SOURCE) { "Theme source exceeds 1 MB" }
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        require(lines.size <= 10000) { "Too many source lines" }
        require(lines.first() == "compose-version: 1") { "First line must be compose-version: 1" }
        val versioned = lines.any { it.startsWith("version:") }
        val root = Node(1); val sections = linkedMapOf<String, Node>(); val declarations = mutableListOf<Pair<String, Node>>()
        var section = root; var seenSection = false; var count = 0
        val stack = mutableListOf(-1 to root)
        lines.forEachIndexed { index, raw ->
            val line = index + 1; val content = raw.trim(); val depth = raw.length - raw.trimStart().length
            fun check(test: Boolean, reason: String) = require(test) { "Line $line: $reason" }
            if (content.isEmpty() || (content.startsWith('#') && !content.startsWith("#request"))) return@forEachIndexed
            check(!raw.contains('\t') && depth % 2 == 0 && depth <= 64, "Use two-space indentation, at most 32 levels")
            if (content.startsWith("#request")) {
                val match = Regex("#request ([A-Za-z][A-Za-z0-9]*):").matchEntire(content)
                check(depth == 0 && match != null && !seenSection && declarations.size < 32, "Invalid #request declaration")
                section = Node(line); declarations += match!!.groupValues[1] to section
                stack.clear(); stack += 0 to section; return@forEachIndexed
            }
            if (content.startsWith('@')) {
                check(depth == 0 && content.substring(1).matches(keyPattern), "Invalid decorator")
                check(content !in sections, "Duplicate decorator")
                section = Node(line); sections[content] = section; seenSection = true
                stack.clear(); stack += -1 to section; return@forEachIndexed
            }
            if (!seenSection && depth == 0) { section = root; stack.clear(); stack += -1 to root }
            val inRequest = declarations.any { it.second === section }
            val match = (if (inRequest) Regex("(id|as|reason):?\\s+(.*)") else Regex("([a-z][a-z0-9]*(?:-[a-z0-9]+)*):(?:\\s+(.*))?")).matchEntire(content)
            check(match != null, "Expected a key and value")
            val key = match!!.groupValues[1]
            while (stack.size > 1 && stack.last().first >= depth) stack.removeAt(stack.lastIndex)
            val (parentDepth, parent) = stack.last()
            check(depth == parentDepth + 2 || (depth == 0 && parentDepth == -1), "Indentation must advance by two spaces")
            check(parent.value == null && key !in parent.children, "Duplicate key or nested scalar")
            check(++count <= 4096, "Too many fields")
            val rawValue = match.groups[2]?.value
            val value = rawValue?.let {
                when {
                    it.startsWith("= ") -> it
                    it.startsWith('"') -> decodeThemeString(it)
                    it.startsWith('\'') -> { check(it.endsWith('\'') && it.length > 1, "Invalid quoted string"); it.substring(1, it.lastIndex).replace("''", "'") }
                    else -> { check(!Regex("[\\[\\]{}]|(^|\\s)#|:\\s").containsMatchIn(it), "Quote this value"); it.trim() }
                }
            }
            val child = Node(line, value); parent.children[key] = child; stack += depth to child
        }
        fun text(parent: Node, key: String, required: Boolean = false, max: Int = 300): String {
            val value = parent.children[key]?.value.orEmpty()
            require((!required || value.isNotBlank()) && value.length <= max && !value.startsWith("= ")) { "Invalid $key metadata" }
            require(value.none { it.code < 32 || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' }) { "Control characters are not allowed in $key" }
            return value
        }
        require(root.children.keys.all { it in setOf("compose-version", "target", "id", "version", "title", "author", "description", "license", "index-image", "available") }) { "Unknown core metadata" }
        root.children["target"]?.let { require(it.line == 2 && it.value?.matches(keyPattern) == true) { "target must be a product id on line 2" } }
        val id = text(root, "id", versioned, 64).ifEmpty { "local-" + digest(source.toByteArray()).take(24) }
        require(id.matches(keyPattern)) { "Invalid theme id" }
        val version = text(root, "version", versioned, 30).ifEmpty { "0.0.0" }
        require(version.matches(Regex("(0|[1-9]\\d{0,5})\\.(0|[1-9]\\d{0,5})\\.(0|[1-9]\\d{0,5})"))) { "version must be numeric major.minor.patch" }
        val requestList = declarations.map { (name, node) ->
            val definition = requests[name] ?: error("Unsupported request $name")
            val requestId = text(node, "id", true, 64)
            require(requestId.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) && requestId !in listOf("true", "false", "null")) { "Invalid request id" }
            require(text(node, "as", true) == definition.first && node.children.size == 3) { "Invalid request type or fields" }
            ThemeRequest(name, requestId, definition.first, text(node, "reason", true))
        }
        require(requestList.distinctBy { it.id }.size == requestList.size && requestList.distinctBy { it.source }.size == requestList.size) { "Duplicate request or request id" }
        val ids = requestList.associate { it.id to it.type }
        fun field(node: Node, pattern: Regex): ThemeValue {
            val raw = requireNotNull(node.value) { "Line ${node.line}: Missing value" }
            val expression = if (raw.startsWith("= ")) { ThemeExpression.parse(raw.substring(2)) } else null
            require(expression == null || expression.types(ids) == setOf("string")) { "Visual fields require strings" }
            val value = ThemeValue(raw, expression)
            require(value.outputs().all { pattern.matches(it) }) { "Line ${node.line}: Invalid visual value or expression branch" }
            return value
        }
        val common = requireNotNull(sections["@common"]) { "Missing @common" }
        require(common.children.keys == setOf("colors")) { "Unknown @common fields" }
        val colors = requireNotNull(common.children["colors"])
        require(colors.children.keys == setOf("light", "dark")) { "Both light and dark palettes are required" }
        fun palette(name: String): Map<String, ThemeValue> {
            val node = colors.children.getValue(name)
            require(node.children.keys == roles) { "$name requires exactly the six common roles" }
            return node.children.mapValues { field(it.value, colorPattern) }
        }
        val images = linkedMapOf<String, ThemeValue>()
        fun image(path: String, node: Node) {
            val value = field(node, assetPattern)
            require(value.outputs().all(::safeAssetPath)) { "Unsafe asset path" }
            images[path] = value
        }
        root.children["index-image"]?.let { image("index-image", it) }
        var wordmark: ThemeValue? = null
        fun visit(node: Node, prefix: String = "") {
            node.children.forEach { (key, child) ->
                val path = if (prefix.isEmpty()) key else "$prefix.$key"
                if (child.value == null) visit(child, path)
                else when (path) {
                    "branding.wordmark-color" -> wordmark = field(child, colorPattern)
                    "today.background", "widgets.screen-time-background", "widgets.dayline-background", "branding.launcher-icon" -> image(path, child)
                    else -> error("Unsupported Still field $path")
                }
            }
        }
        sections["@still-app"]?.let { visit(it) }
        val available = root.children["available"]?.let { node ->
            require(node.children.keys == setOf("mode", "from", "to")) { "Invalid availability fields" }
            val mode = text(node, "mode", true); val from = text(node, "from", true); val to = text(node, "to", true)
            require(mode in listOf("annual", "once")) { "Invalid availability mode" }
            if (mode == "annual") { MonthDay.parse("--$from"); MonthDay.parse("--$to") }
            else require(LocalDate.parse(from) <= LocalDate.parse(to)) { "Availability end precedes start" }
            ThemeDateRange(mode, from, to)
        }
        return CommunityTheme(id, version, text(root, "title", true, 80), text(root, "author", true, 80), text(root, "description"), text(root, "license"), requestList, palette("light"), palette("dark"), images, wordmark, available, root.children["id"] != null && root.children["version"] != null)
    }

    fun safeArchiveAssetPath(path: String): Boolean = Regex("assets/[a-zA-Z0-9][a-zA-Z0-9_./-]*\\.(png|jpg|jpeg|webp|svg)", RegexOption.IGNORE_CASE).matches(path) && ".." !in path && "//" !in path
    fun safeAssetPath(path: String): Boolean = assetPattern.matches(path) && ".." !in path && "//" !in path
    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun newer(candidate: String, installed: String): Boolean {
        val a = candidate.split('.').map(String::toInt); val b = installed.split('.').map(String::toInt)
        return a.indices.firstOrNull { a[it] != b[it] }?.let { a[it] > b[it] } ?: false
    }
}
