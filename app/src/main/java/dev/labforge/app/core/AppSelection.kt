package dev.labforge.app.core

/** Cross-screen selection and hand-off between Chat and Codex. */
object AppSelection {
    var modelPath: String? = null
    var datasetPath: String? = null
    var researchTheme: String? = null
    var researchBundleId: String? = null
    var preset: dev.labforge.app.core.train.TrainingPreset? = null
}

object Handoff {
    var toCodex: String? = null
    var toChat: String? = null
}
