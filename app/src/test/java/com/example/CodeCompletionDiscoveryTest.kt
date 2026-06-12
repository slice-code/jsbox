package com.example

import org.junit.Test

class CodeCompletionDiscoveryTest {
    @Test
    fun discoverSoraClasses() {
        val results = StringBuilder()
        
        val checkPaths = listOf(
            "io.github.rosemoe.sora.lang.completion.CompletionPublisher",
            "io.github.rosemoe.sora.text.ContentReference",
            "io.github.rosemoe.sora.text.CharPosition"
        )
        results.append("CHECKING ENABLING CLASSES:\n")
        for (path in checkPaths) {
            try {
                val clazz = Class.forName(path)
                results.append("FOUND: $path\n")
                results.append("Methods:\n")
                clazz.methods.forEach { method ->
                    val params = method.parameterTypes.joinToString { it.name }
                    results.append("  - ${method.returnType.name} ${method.name}($params)\n")
                }
            } catch (e: Exception) {
                results.append("NOT FOUND: $path - ${e.message}\n")
            }
        }
        
        throw RuntimeException("\n=== SORA STAGE 4 ===\n$results\n======================")
    }
}
