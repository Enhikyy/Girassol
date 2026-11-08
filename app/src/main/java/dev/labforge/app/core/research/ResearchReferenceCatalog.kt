package dev.labforge.app.core.research

/**
 * Curated references supplied for Girassol. Compatibility and licence are explicit:
 * README ingestion is allowed only for references whose repository licence permits it.
 * No Python, CUDA, Triton, vLLM, restricted or unlicensed source is bundled into the APK.
 */
data class ResearchReference(
    val name: String,
    val url: String,
    val description: String,
    val license: String,
    val topics: Set<String>,
    val nativePath: String,
    val ingestReadme: Boolean,
)

object ResearchReferenceCatalog {
    val references = listOf(
        ResearchReference(
            "labml annotated deep learning implementations",
            "https://github.com/labmlai/annotated_deep_learning_paper_implementations",
            "Implementações didáticas e anotações de papers: transformers, otimizadores, RL e distilação.",
            "MIT", setOf("deep learning", "transformers", "optimizer", "otimizador", "reinforcement", "paper", "attention"),
            "Base de pesquisa e material de estudo; não importar o runtime PyTorch.", true,
        ),
        ResearchReference(
            "LLaMA Factory",
            "https://github.com/hiyouga/LlamaFactory",
            "Receitas de SFT, LoRA, QLoRA, DPO e formatos de dataset para treinamento de modelos.",
            "Apache-2.0", setOf("lora", "qlora", "fine tuning", "finetuning", "sft", "dpo", "training", "treinamento"),
            "Referência de receitas; o stack Python/PyTorch e os requisitos GPU não são dependências Android.", true,
        ),
        ResearchReference(
            "MuonClip",
            "https://github.com/GAD-cell/muon-clip",
            "Otimizador Muon com QK clipping e ortogonalização Newton-Schulz, implementado em PyTorch.",
            "MIT", setOf("muon", "optimizer", "otimizador", "clip", "training", "treinamento"),
            "Referência algorítmica; requer port C++/ggml antes de ativar no motor nativo.", true,
        ),
        ResearchReference(
            "Kimi Linear / KDA",
            "https://github.com/hkevin01/kimi-linear",
            "Referência de Kimi Delta Attention, estado recorrente e atenção linear híbrida.",
            "MIT", setOf("kimi", "kda", "linear attention", "attention", "long context", "contexto"),
            "Pesquisa de arquitetura; integração real exige suporte de arquitetura e kernels ARM64 no llama.cpp.", true,
        ),
        ResearchReference(
            "KDA educacional",
            "https://github.com/hwilner/kimi-delta-attention",
            "Implementação educacional de Kimi Delta Attention com testes e documentação.",
            "MIT", setOf("kimi", "kda", "linear attention", "attention", "educacional"),
            "Fonte de validação matemática; não é um runtime Android pronto.", true,
        ),
        ResearchReference(
            "Flash Linear Attention",
            "https://github.com/fla-org/flash-linear-attention",
            "Kernels e camadas para arquiteturas de atenção linear, incluindo KDA.",
            "MIT", setOf("kimi", "kda", "flash linear", "linear attention", "kernel", "kernels"),
            "Código depende de PyTorch/Triton e backends específicos; usar como referência para um port ggml/NEON.", true,
        ),
        ResearchReference(
            "lau-agent-dream",
            "https://github.com/SuperInstance/lau-agent-dream",
            "Ciclos locais de consolidação, replay, geração de hipóteses e revisão intensiva de falhas.",
            "MIT", setOf("dream", "memory", "memoria", "sleep", "sono", "consolidation", "consolidacao", "failure", "falhas"),
            "Conceito portado para Kotlin local, sem dependência Rust ou runtime externo.", true,
        ),
        ResearchReference(
            "ZenBrain",
            "https://github.com/zensation-ai/zenbrain",
            "Arquitetura em camadas para memória, confiança, consolidação e revisão espaçada.",
            "Apache-2.0", setOf("memory", "memoria", "spaced repetition", "revisao", "sleep", "sono", "agent", "agente"),
            "Referência arquitetural; a memória Girassol permanece nativa em Kotlin e armazenamento local.", true,
        ),
        ResearchReference(
            "UI-Voyager",
            "https://github.com/ui-voyager/UI-Voyager",
            "Pesquisa de melhoria de agentes GUI por comparação entre trajetórias corretas e falhas.",
            "MIT", setOf("failure", "falha", "gui agent", "android agent", "agent", "agente", "self evolving"),
            "A ideia de replay de falhas é aproveitada; o modelo de 4B e o serviço vLLM não entram no APK.", true,
        ),
        ResearchReference(
            "Human Skill Tree",
            "https://github.com/24kchengYe/human-skill-tree",
            "Referência de organização progressiva de habilidades, diagnóstico de lacunas e revisão espaçada.",
            "AGPL-3.0", setOf("skill tree", "habilidades", "curriculum", "curriculo", "learning", "aprendizado", "spaced repetition"),
            "Inspiração conceitual apenas; conteúdo e código AGPL não são copiados nem empacotados.", false,
        ),
        ResearchReference(
            "SVC — Singular Value Calibration",
            "https://github.com/lyymuwu/SVC",
            "Pesquisa sobre calibração espectral para fusão de modelos e controlo de subespaços partilhados.",
            "Licença não declarada no repositório", setOf("svc", "merge", "merging", "lora merge", "fusao", "anti forgetting", "esquecimento"),
            "Somente referência bibliográfica; não importar ou redistribuir código sem licença confirmada.", false,
        ),
        ResearchReference(
            "Android Guru Agent",
            "https://github.com/Ultra-Guru/Android-Guru-Agent",
            "Referência de arquitetura de agente Android, memória, ferramentas e instrumentação.",
            "UGRL v1.0 — restritiva", setOf("android", "mobile agent", "android agent", "agente android", "tools", "ferramentas"),
            "Não reutilizar código/recursos; a licença restringe redistribuição e derivados.", false,
        ),
        ResearchReference(
            "Termux-AIChain / amv-aichain",
            "https://pypi.org/project/amv-aichain/",
            "Composição de chains, agentes com estado, RAG SQLite e telemetria de hardware móvel.",
            "Apache-2.0", setOf("chain", "aichain", "termux", "android", "rag", "telemetry", "telemetria"),
            "Conceitos portados para Kotlin; o pacote Python não é incluído no APK.", false,
        ),
        ResearchReference(
            "Eidos-X",
            "https://github.com/itsaquestion/eidos-x",
            "Projeto de agente com memória de longo prazo e contexto ambiental.",
            "Apache-2.0", setOf("eidos", "memory", "memoria", "agent", "agente", "environment"),
            "Apenas leitura; os módulos centrais consultados estão incompletos/placeholder.", false,
        ),
    )

    fun matching(theme: String): List<ResearchReference> {
        val query = theme.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
        if (query.isEmpty()) return emptyList()
        return references.mapNotNull { ref ->
            val score = ref.topics.sumOf { topic ->
                val terms = topic.lowercase().split(' ').toSet()
                when {
                    topic.lowercase() in theme.lowercase() -> 2.0
                    terms.any { it in query } -> 1.0
                    else -> 0.0
                }
            }
            if (score > 0) ref to score else null
        }.sortedByDescending { it.second }.map { it.first }.take(6)
    }

    /** Only permissively licensed, documentation-oriented repos are added to ingestion. */
    fun researchDocs(theme: String): List<ResearchDoc> =
        matching(theme).filter { it.ingestReadme }.mapIndexed { index, ref ->
            ResearchDoc(
                title = ref.name,
                url = ref.url,
                snippet = "${ref.description} Licença: ${ref.license}. Compatibilidade: ${ref.nativePath}",
                source = "GitHub",
                score = 3.2 + (6 - index) * 0.08,
            )
        }
}
