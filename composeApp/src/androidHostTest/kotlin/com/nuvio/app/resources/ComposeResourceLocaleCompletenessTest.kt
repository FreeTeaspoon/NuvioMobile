package com.nuvio.app.resources

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertTrue

class ComposeResourceLocaleCompletenessTest {

    @Test
    fun localizedComposeStringResourcesContainEveryDefaultKey() {
        val resourcesDir = findComposeResourcesDir()
        val defaultNames = parseStringNames(resourcesDir.resolve("values").resolve("strings.xml"))

        Files.list(resourcesDir).use { directories ->
            directories
                .filter { Files.isDirectory(it) && it.name.startsWith("values-") }
                .forEach { localeDir ->
                    val localeNames = parseStringNames(localeDir.resolve("strings.xml"))
                    val missingNames = defaultNames - localeNames
                    val extraNames = localeNames - defaultNames

                    assertTrue(
                        actual = missingNames.isEmpty(),
                        message = "${localeDir.name}/strings.xml is missing default strings: ${missingNames.joinToString()}",
                    )
                    assertTrue(
                        actual = extraNames.isEmpty(),
                        message = "${localeDir.name}/strings.xml defines strings missing from default values: ${extraNames.joinToString()}",
                    )
                }
        }
    }

    private fun parseStringNames(path: Path): Set<String> {
        val text = Files.readAllBytes(path).toString(UTF_8)
        return stringNameRegex.findAll(text)
            .map { it.groupValues[1] }
            .toSet()
    }

    private fun findComposeResourcesDir(): Path {
        val candidates = listOf(
            Path.of("src", "commonMain", "composeResources"),
            Path.of("composeApp", "src", "commonMain", "composeResources"),
        )
        return candidates.firstOrNull { Files.isDirectory(it) }
            ?: error("Could not find composeResources directory from ${Path.of("").toAbsolutePath()}")
    }

    private companion object {
        val stringNameRegex = Regex("""<string\s+name="([^"]+)"""")
    }
}
