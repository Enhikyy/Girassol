package com.enhikyy.girassol.school

import kotlin.math.abs

/** Especialidade de um professor da Universidade Girassol. */
enum class Subject(val label: String) {
    MATEMATICA("Matemática"),
    HISTORIA("História"),
    PROGRAMACAO("Programação"),
    SEGURANCA("Segurança"),
}

/** Um nó do grafo de conhecimento de uma matéria. */
data class Concept(
    val id: String,
    val label: String,
    val difficulty: Float,
    val requires: List<String> = emptyList(),
)

/** Estado de um aluno perante um conceito. */
enum class Mastery { UNSEEN, STRUGGLING, ZPD, MASTERED }

/** Um professor com o seu grafo de conhecimento. */
data class Professor(
    val id: Long,
    val name: String,
    val subject: Subject,
    val concepts: List<Concept>,
    val students: Int = 0,
    val zpdLevel: Float = 0.5f,
) {
    /** Conceitos prontos a ensinar: pré-requisitos já dominados pelo aluno. */
    fun teachable(mastered: Set<String>): List<Concept> =
        concepts.filter { concept ->
            concept.id !in mastered && concept.requires.all { it in mastered }
        }.sortedBy { it.difficulty }
}

/**
 * Universidade Girassol.
 *
 * Cada matéria é um grafo dirigido de conceitos. O professor só pode ensinar um
 * conceito quando todos os pré-requisitos estão dominados — essa é a **Zona de
 * Desenvolvimento Proximal**: nem o que o aluno já sabe (aborrecido), nem o que
 * exige pré-requisitos ausentes (impossível).
 */
object AgentSchoolManager {

    /** Grafos de conhecimento das quatro especialidades. */
    val professors: List<Professor> = listOf(
        Professor(
            id = 1,
            name = "Dr. Ada Lovelace",
            subject = Subject.PROGRAMACAO,
            concepts = listOf(
                Concept("prog-variaveis", "Variáveis e tipos", 0.1f),
                Concept("prog-controle", "Fluxo de controlo", 0.2f, listOf("prog-variaveis")),
                Concept("prog-funcoes", "Funções e escopo", 0.35f, listOf("prog-controle")),
                Concept("prog-dados", "Estruturas de dados", 0.5f, listOf("prog-funcoes")),
                Concept("prog-concorrencia", "Corrotinas", 0.7f, listOf("prog-dados")),
            ),
            students = 3,
            zpdLevel = 0.8f,
        ),
        Professor(
            id = 2,
            name = "Prof. Euclides",
            subject = Subject.MATEMATICA,
            concepts = listOf(
                Concept("mat-aritmetica", "Aritmética", 0.1f),
                Concept("mat-algebra", "Álgebra linear", 0.3f, listOf("mat-aritmetica")),
                Concept("mat-calculo", "Cálculo", 0.55f, listOf("mat-algebra")),
                Concept("mat-grafos", "Teoria dos grafos", 0.7f, listOf("mat-algebra")),
                Concept("mat-estatistica", "Estatística", 0.6f, listOf("mat-calculo")),
            ),
            students = 2,
            zpdLevel = 0.65f,
        ),
        Professor(
            id = 3,
            name = "Mestre Herodoto",
            subject = Subject.HISTORIA,
            concepts = listOf(
                Concept("his-antiga", "Civilizações antigas", 0.15f),
                Concept("his-media", "Idade Média", 0.3f, listOf("his-antiga")),
                Concept("his-moderna", "Era moderna", 0.45f, listOf("his-media")),
                Concept("his-fontes", "Análise de fontes", 0.6f, listOf("his-moderna")),
            ),
            students = 4,
            zpdLevel = 0.45f,
        ),
        Professor(
            id = 4,
            name = "Guardião Alan",
            subject = Subject.SEGURANCA,
            concepts = listOf(
                Concept("seg-base", "Fundamentos de segurança", 0.2f),
                Concept("seg-cripto", "Criptografia", 0.45f, listOf("seg-base")),
                Concept("seg-redes", "Segurança de redes", 0.6f, listOf("seg-cripto")),
                Concept("seg-auditoria", "Auditoria de código", 0.75f, listOf("seg-redes")),
            ),
            students = 1,
            zpdLevel = 0.9f,
        ),
    )

    /** Domínio acumulado por agente, por conceito. */
    private val mastered = HashMap<Long, MutableSet<String>>()

    /** Conceitos dominados por um agente. */
    fun masteredBy(agentId: Long): Set<String> =
        mastered.getOrPut(agentId) { mutableSetOf() }.toSet()

    /**
     * Escolhe o próximo conceito a ensinar a um agente.
     *
     * Procura primeiro na especialidade pedida; se o grafo estiver completo,
     * devolve o conceito mais difícil ainda por dominar de qualquer matéria.
     */
    fun nextLesson(agentId: Long, subject: Subject? = null): Pair<Professor, Concept>? {
        val known = masteredBy(agentId)
        val pool = if (subject != null) professors.filter { it.subject == subject } else professors

        for (professor in pool) {
            val candidate = professor.teachable(known).firstOrNull()
            if (candidate != null) return professor to candidate
        }
        // Nenhuma matéria com progressão limpa: oferece o que falta em qualquer uma.
        return professors
            .flatMap { professor -> professor.concepts.map { professor to it } }
            .filter { (_, concept) -> concept.id !in known }
            .minByOrNull { (_, concept) -> concept.difficulty }
    }

    /** Regista que um agente dominou um conceito. */
    fun complete(agentId: Long, conceptId: String) {
        mastered.getOrPut(agentId) { mutableSetOf() } += conceptId
    }

    /** Classifica o estado de um agente perante um conceito. */
    fun masteryOf(agentId: Long, concept: Concept): Mastery {
        val known = masteredBy(agentId)
        return when {
            concept.id in known -> Mastery.MASTERED
            concept.requires.any { it !in known } -> Mastery.UNSEEN
            concept.requires.all { it in known } -> Mastery.ZPD
            else -> Mastery.STRUGGLING
        }
    }

    /** Progresso global de um agente em 0..1, sobre todos os conceitos. */
    fun progressOf(agentId: Long): Float {
        val total = professors.sumOf { it.concepts.size }
        if (total == 0) return 0f
        return (masteredBy(agentId).size.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Nota de uma matéria em 0..1. */
    fun progressOf(agentId: Long, subject: Subject): Float {
        val professor = professors.firstOrNull { it.subject == subject } ?: return 0f
        val known = masteredBy(agentId)
        val ratio = professor.concepts.count { it.id in known }.toFloat() / professor.concepts.size
        return abs(ratio).coerceIn(0f, 1f)
    }

    fun reset() {
        mastered.clear()
    }
}
