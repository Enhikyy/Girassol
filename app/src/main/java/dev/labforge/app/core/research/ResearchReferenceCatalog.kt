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
        ResearchReference(
            "nanoKimi",
            "https://github.com/dishant2009/nanoKimi",
            "Implementação educacional de Kimi-K2, MoE, Muon e atenção latente.",
            "Licença não declarada", setOf("kimi", "moe", "muon", "latent attention", "attention", "treinamento"),
            "Somente referência de arquitetura; não importar nem redistribuir sem licença confirmada.", false,
        ),
        ResearchReference(
            "Kimi MoE reference",
            "https://github.com/mandeep0405/kimi",
            "Exemplo de MoE e MuonClip inspirado na família Kimi.",
            "Licença não declarada", setOf("kimi", "moe", "muon", "muonclip", "optimizer"),
            "Somente leitura técnica enquanto a licença não for declarada.", false,
        ),
        ResearchReference(
            "AgenticART",
            "https://github.com/GitSolved/AgenticART",
            "Referência de aprendizado agentivo citada pelo usuário; o repositório não pôde ser verificado na auditoria.",
            "Indisponível na API pública (404)", setOf("agenticart", "agent", "agente", "learning", "aprendizado", "failure"),
            "Link preservado para acompanhamento; nenhum código ou README é baixado.", false,
        ),
        ResearchReference(
            "BYRD AI",
            "https://huggingface.co/omoplatapus/byrd-ai",
            "Referência de iniciativa e curiosidade agentiva indicada pelo usuário.",
            "Licença/model card não verificados", setOf("byrd", "initiative", "curiosity", "iniciativa", "curiosidade", "agent"),
            "Link preservado apenas como referência; não baixar pesos nem código sem verificar licença e formato.", false,
        ),
        ResearchReference(
            "SRC-Merging paper",
            "https://neurips.cc/",
            "Referência de fusão por subespaço indicada pelo usuário; falta o título/URL do paper específico.",
            "Referência bibliográfica incompleta", setOf("src merging", "merging", "model merge", "fusao", "anti forgetting", "esquecimento"),
            "Não integra pesos nem implementação; aguarda identificação da publicação exata.", false,
        ),

    )

    fun matching(theme: String): List<ResearchReference> {
        val normalized = theme.lowercase()
        val query = normalized.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }.toSet()
        if (query.isEmpty()) return emptyList()

        val scored = references.mapNotNull { ref ->
            val name = ref.name.lowercase()
            val description = ref.description.lowercase()
            val topicTokens = ref.topics.flatMap { it.lowercase().split(' ') }.toSet()
            val nameTokens = name.split(Regex("[^\\p{L}\\p{N}]+")).toSet()
            val score = (query intersect nameTokens).size * 4.0 +
                (query intersect topicTokens).size * 2.0 +
                query.count { it.length >= 4 && description.contains(it) } * 0.5 +
                if (name in normalized || normalized.contains(name)) 5.0 else 0.0
            if (score > 0.0) ref to score else null
        }.sortedWith(compareByDescending<Pair<ResearchReference, Double>> { it.second }
            .thenByDescending { it.first.ingestReadme })

        if (scored.isEmpty()) return emptyList()
        // Retain the best compatible README and the best reference-only entry when both
        // match, preventing metadata-only entries from crowding out usable documentation.
        val mustKeep = listOfNotNull(
            scored.firstOrNull { it.first.ingestReadme },
            scored.firstOrNull { !it.first.ingestReadme },
        )
        return (mustKeep + scored.filterNot { candidate -> mustKeep.any { it.first.name == candidate.first.name } })
            .distinctBy { it.first.name }
            .take(6)
            .map { it.first }
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
