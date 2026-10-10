package se.partee71.dagboken.core.cli

/**
 * Kommandoradens argument för `:core`-verktygen (`convertLegacyBackup`, `matchMedicines`): flaggor med
 * ett värde (`--in fil`) och brytare utan (`--force`). En gemensam tolkning, så att verktygen godtar och
 * nekar likadant.
 */
class CliArgs private constructor(val values: Map<String, String>, val switches: Set<String>) {

    fun value(flag: String): String = values.getValue(flag)

    companion object {
        /**
         * [args] med flaggorna i [flags] och brytarna i [switches]; `null` vid en okänd flagga, en flagga
         * eller brytare två gånger, eller en flagga utan värde (nästa argument börjar med `--`). Vilka
         * flaggor som krävs avgör anroparen.
         */
        fun parse(args: Array<String>, flags: Set<String>, switches: Set<String> = emptySet()): CliArgs? {
            val values = mutableMapOf<String, String>()
            val seen = mutableSetOf<String>()
            var i = 0
            while (i < args.size) {
                val arg = args[i]
                when (arg) {
                    in switches -> {
                        if (!seen.add(arg)) return null
                        i += 1
                    }
                    in flags -> {
                        val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") } ?: return null
                        if (values.put(arg, value) != null) return null
                        i += 2
                    }
                    else -> return null
                }
            }
            return CliArgs(values, seen)
        }
    }
}
