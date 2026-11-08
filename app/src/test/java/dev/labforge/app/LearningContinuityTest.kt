package dev.labforge.app

import dev.labforge.app.core.learning.LearningContinuity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LearningContinuityTest {
    @Test fun curriculumPersistsAndCreatesReviewableSkillNodes() {
        val dir = Files.createTempDirectory("girassol-learning").toFile()
        val file = File(dir, "learning.json")
        val engine = LearningContinuity(file)
        val first = engine.syncCurriculum("Kotlin", listOf("Coroutines", "Compose"))
        assertEquals(2, first.skills.size)
        assertEquals(2, LearningContinuity(file).snapshot().skills.size)
    }

    @Test fun successfulReviewIncreasesMasteryAndSchedulesNextReview() {
        val file = File(Files.createTempDirectory("girassol-review").toFile(), "learning.json")
        val engine = LearningContinuity(file)
        val skill = engine.syncCurriculum("Android", listOf("JNI")).skills.single()
        val reviewed = engine.review(skill.id, 5, now = 1_000L).skills.single()
        assertTrue(reviewed.mastery > skill.mastery)
        assertEquals(1, reviewed.intervalDays)
        assertTrue(reviewed.dueAt > 1_000L)
    }

    @Test fun failureExperiencesBecomeDreamTilesAndSurviveReload() {
        val file = File(Files.createTempDirectory("girassol-dream").toFile(), "learning.json")
        val engine = LearningContinuity(file)
        engine.record("failure", "JNI", "Thread-local JNIEnv mismatch", 0.95f)
        val dream = engine.dream(now = 2_000L)
        assertEquals("failure-review", dream.dreams.single().kind)
        assertTrue(dream.dreams.single().insight.contains("JNIEnv"))
        assertEquals(1, LearningContinuity(file).snapshot().dreams.size)
    } 
    @Test fun repeatedDreamDoesNotDuplicateProcessedExperiences() {
        val file = File(Files.createTempDirectory("girassol-dream-dedupe").toFile(), "learning.json")
        val engine = LearningContinuity(file)
        engine.record("cycle", "Kotlin", "Pesquisa e dataset construídos", 0.8f)
        engine.dream(now = 1_000L)
        engine.record("cycle", "Kotlin", "Segundo ciclo reforçou a mesma competência", 0.9f)
        val second = engine.dream(now = 2_000L)
        assertEquals(1, second.dreams.size)
        val repeated = engine.dream(now = 3_000L)
        assertEquals("dream calls without new experiences must be idempotent", 1, repeated.dreams.size)
    }

    @Test fun curriculumBuildsPrerequisiteEdgesAndDueReviewsRespectThem() {
        val file = File(Files.createTempDirectory("girassol-skill-tree").toFile(), "learning.json")
        val engine = LearningContinuity(file)
        val skills = engine.syncCurriculum("Kotlin", listOf("Sintaxe", "Coroutines", "Compose")).skills
        assertEquals(3, skills.size)
        assertTrue(skills[0].prerequisites.isEmpty())
        assertEquals(listOf(skills[0].id), skills[1].prerequisites)
        assertEquals(listOf(skills[1].id), skills[2].prerequisites)
        assertEquals(listOf(skills[0].id), engine.due(now = 0L).map { it.id })
        repeat(6) { engine.review(skills[0].id, 5, now = 0L) }
        assertTrue(engine.snapshot().skills.first().mastery >= 0.6f)
        assertTrue(engine.due(now = 0L).any { it.id == skills[1].id })
    }

}
