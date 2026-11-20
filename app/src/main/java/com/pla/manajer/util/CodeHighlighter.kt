package com.pla.manajer.util

import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

object CodeHighlighter {

    data class Rule(val pattern: Pattern, val color: Int)

    private const val C_KEYWORD = 0xFFCF8EF4.toInt()
    private const val C_STRING  = 0xFFCE9178.toInt()
    private const val C_COMMENT = 0xFF6A9955.toInt()
    private const val C_NUMBER  = 0xFFB5CEA8.toInt()
    private const val C_ANNOT   = 0xFFDCDCAA.toInt()

    fun highlight(code: CharSequence, ext: String): CharSequence {
        val sb = SpannableStringBuilder(code)
        for (rule in rulesFor(ext)) {
            val m = rule.pattern.matcher(sb)
            while (m.find()) sb.setSpan(ForegroundColorSpan(rule.color), m.start(), m.end(), 0)
        }
        return sb
    }

    private fun rulesFor(ext: String): List<Rule> {
        val keywords = when (ext.lowercase()) {
            "kt", "kts" -> "fun|val|var|class|object|interface|data|if|else|when|for|while|return|import|package|private|public|protected|internal|override|suspend|lateinit|companion|init|by|in|is|as|null|true|false"
            "java" -> "class|interface|enum|extends|implements|public|private|protected|static|final|void|int|long|double|float|char|boolean|if|else|for|while|do|return|new|this|super|import|package|null|true|false|try|catch|finally|throw|throws"
            "py" -> "def|class|if|elif|else|for|while|return|import|from|as|try|except|finally|raise|with|lambda|and|or|not|in|is|None|True|False|pass|yield|async|await"
            "js", "ts" -> "function|var|let|const|if|else|for|while|do|return|class|extends|new|this|import|export|from|async|await|try|catch|finally|throw|typeof|instanceof|null|undefined|true|false"
            "json" -> "true|false|null"
            "gradle" -> "plugins|dependencies|implementation|android|apply|id|compileSdk|defaultConfig|buildTypes|namespace"
            else -> ""
        }
        val list = mutableListOf<Rule>()
        if (keywords.isNotEmpty()) list.add(Rule(Pattern.compile("\\b($keywords)\\b"), C_KEYWORD))
        list.add(Rule(Pattern.compile("\"([^\"\\\\]|\\\\.)*\""), C_STRING))
        list.add(Rule(Pattern.compile("'([^'\\\\]|\\\\.)*'"), C_STRING))
        list.add(Rule(Pattern.compile("//[^\\n]*"), C_COMMENT))
        list.add(Rule(Pattern.compile("/\\*[\\s\\S]*?\\*/"), C_COMMENT))
        list.add(Rule(Pattern.compile("#[^\\n]*"), C_COMMENT))
        list.add(Rule(Pattern.compile("\\b\\d+(\\.\\d+)?\\b"), C_NUMBER))
        list.add(Rule(Pattern.compile("@\\w+"), C_ANNOT))
        return list
    }

    fun languageFromName(name: String): String {
        val parts = name.split(".")
        return if (parts.size > 1) parts.last().lowercase() else "txt"
    }
}