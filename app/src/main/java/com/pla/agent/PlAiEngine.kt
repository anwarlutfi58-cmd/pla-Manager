package com.pla.agent

object PlAiEngine {

    fun suggestStructure(projectType: String): List<String> = when (projectType.lowercase()) {
        "android" -> listOf(
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "app/src/main/java/com/pla/agent/MainActivity.kt",
            "app/src/main/java/com/pla/agent/PlAiEngine.kt",
            "app/src/main/res/layout/activity_main.xml",
            "app/src/main/res/values/strings.xml",
            "build.gradle.kts",
            "settings.gradle.kts"
        )
        "web" -> listOf("index.html", "css/style.css", "js/app.js", "assets/img/", "README.md")
        "python" -> listOf("src/main.py", "src/utils/__init__.py", "src/utils/helper.py", "requirements.txt", "README.md")
        "kotlin" -> listOf("build.gradle.kts", "settings.gradle.kts", "src/main/kotlin/Main.kt", "README.md")
        else -> listOf("README.md")
    }

    fun guessFileType(name: String): String = when {
        name.endsWith(".kt") -> "Kotlin"
        name.endsWith(".java") -> "Java"
        name.endsWith(".py") -> "Python"
        name.endsWith(".js") -> "JavaScript"
        name.endsWith(".html") -> "HTML"
        name.endsWith(".css") -> "CSS"
        name.endsWith(".json") -> "JSON"
        name.endsWith(".md") -> "Markdown"
        name.endsWith(".xml") -> "XML"
        name.endsWith(".gradle") || name.endsWith(".kts") -> "Gradle"
        else -> "Text"
    }
}