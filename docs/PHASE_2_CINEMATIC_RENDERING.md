# Fase 2 — Renderização cinematográfica móvel

## Estado da implementação

- O mundo continua usando OpenGL ES 3 como renderer de cena. O probe Vulkan consulta extensões e feature bits reais de Ray Query e Acceleration Structures; detectar suporte não significa que o renderer já foi migrado para Vulkan.
- CinematicPostProcessor adiciona um passe de pós-processamento GLES 3 com curva tonal, vinheta, grain fino, halation quente e flare horizontal.
- O passe renderiza para framebuffer off-screen com resolução limitada por borda e orçamento de memória. O alvo padrão usa no máximo 1600 px no maior eixo e 12 MiB estimados para color + depth.
- Se shader, framebuffer ou alocação falhar, o mundo volta ao desenho direto na superfície GLES; a falha estética não bloqueia a simulação.
- Os parâmetros iniciais são conservadores: grain 0.018, halation 0.22 e flare 0.10. O ajuste visual fino será exposto na fase de interface.

## Compatibilidade e limites

- Vulkan Ray Query depende de VK_KHR_ray_query, VK_KHR_acceleration_structure, VK_KHR_deferred_host_operations, VK_KHR_buffer_device_address e dos respectivos feature bits. Vulkan 1.x isoladamente não comprova suporte.
- O Galaxy A22 5G é tratado como dispositivo de fallback até a consulta real em runtime; nenhuma capacidade de ray tracing é presumida.
- Este commit não declara um renderer Vulkan nem ray tracing funcional. O caminho efetivamente usado para desenhar a cena permanece GLES 3. Um backend Vulkan completo exigirá implementação de device/queues, swapchain, render passes, acceleration structures, shaders SPIR-V e validação física.
- O passe cinematográfico é um filtro de pós-processamento de cena; não substitui iluminação física, path tracing ou bloom HDR multi-pass.

## Testes

CinematicRenderBudgetTest verifica limites de resolução, orçamento de memória e dimensões inválidas sem depender de GPU física.
