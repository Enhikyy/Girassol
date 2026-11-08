package dev.labforge.app

import dev.labforge.app.core.research.ResearchReferenceCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchReferenceCatalogTest {
    @Test
    fun kimiQuerySuggestsNativePortResearchAndLicensedDocs() {
        val matches = ResearchReferenceCatalog.matching("Kimi Delta Attention KDA")
        val names = matches.map { it.name.lowercase() }
        assertTrue(names.any { "kimi linear" in it || "kda" in it })
        val ingestable = matches.filter { it.ingestReadme }
        assertTrue(ingestable.isNotEmpty())
        assertTrue(ingestable.all { it.license == "MIT" || it.license == "Apache-2.0" })
        assertTrue(matches.any { !it.ingestReadme })
    }

    @Test
    fun restrictedOrUnlicensedReferencesAreNeverAddedToReadmeIngestion() {
        val all = ResearchReferenceCatalog.references
        val humanSkillTree = all.single { it.name == "Human Skill Tree" }
        val androidGuru = all.single { it.name == "Android Guru Agent" }
        val svc = all.single { it.name.startsWith("SVC") }
        assertFalse(humanSkillTree.ingestReadme)
        assertFalse(androidGuru.ingestReadme)
        assertFalse(svc.ingestReadme)
        assertTrue(ResearchReferenceCatalog.researchDocs("skill tree human learning").none { it.title == humanSkillTree.name })
    }

    @Test
    fun onlyPermissivelyLicensedGithubReferencesBecomeResearchDocuments() {
        val docs = ResearchReferenceCatalog.researchDocs("LoRA fine tuning training")
        assertTrue(docs.isNotEmpty())
        assertTrue(docs.all { it.source == "GitHub" && it.url.startsWith("https://github.com/") })
    }
}
