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
}
