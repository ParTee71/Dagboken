package se.partee71.dagboken.consistency

/**
 * Det som kan innehålla `//` eller en blockkommentars början utan att vara en kommentar – strängar (`"""…"""`, `"…"`) och tecken – och
 * kommentarerna själva. Ett tecken räknas där det börjar, så `"//"` är en sträng och `// "x"` en kommentar.
 */
private val TOKENS = Regex(
    listOf(
        "\"\"\"[\\s\\S]*?\"{3,}", // """…""" (även med citattecken sist)
        "\"(?:\\\\.|[^\"\\\\\\n])*\"", // "…" med escape
        "'(?:\\\\.|[^'\\\\\\n])+'", // 'x', '\n'
        "//[^\\n]*",
        "/\\*[\\s\\S]*?\\*/",
    ).joinToString("|"),
)

/**
 * Källtext utan kommentarer, för strukturkontroller som letar efter kod: kommentarerna ersätts med blanksteg
 * (radbrytningar kvar), så att radnumren stämmer och text i en kommentar inte räknas. Strängar och tecken lämnas
 * orörda, så att `//` eller en blockkommentars början i en sträng inte blankar koden efter den.
 */
fun stripComments(text: String): String = TOKENS.replace(text) { token ->
    val value = token.value
    if (value.startsWith("//") || value.startsWith("/*")) value.replace(Regex("[^\n]"), " ") else value
}
