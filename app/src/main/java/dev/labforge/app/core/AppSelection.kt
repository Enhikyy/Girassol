package dev.labforge.app.core

/** Cross-screen selection and hand-off between Chat and Codex. */
object AppSelection {
    @Volatile var modelPath: String? = null
    @Volatile var datasetPath: String? = null
    @Volatile var researchTheme: String? = null
    @Volatile var researchBundleId: String? = null
    @Volatile var preset: dev.labforge.app.core.train.TrainingPreset? = null
}

object Handoff {
    @Volatile var toCodex: String? = null
    @Volatile var toChat: String? = null
}
