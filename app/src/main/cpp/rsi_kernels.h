#pragma once

#include <cstddef>
#include <vector>

namespace girassol::rsi {

// Hidden-state kernels for experimental model adapters. These are real tensor
// operations, not a claim that an untrained kernel is a pretrained language model.
bool rdt_refine(const std::vector<float>& input, const std::vector<float>& anchor,
                const std::vector<float>& input_weights, const std::vector<float>& recurrent_weights,
                const std::vector<float>& bias, const std::vector<float>& gate,
                int loops, std::vector<float>& output);

bool modusx_step(const std::vector<float>& input, std::vector<float>& state,
                 std::vector<float>& memory, float decay, float learning_rate);

bool lunaris_moc(const std::vector<float>& input,
                 const std::vector<float>& router_weights, const std::vector<float>& router_bias,
                 const std::vector<float>& expert_weights, const std::vector<float>& expert_bias,
                 const std::vector<float>& mediator_weights, const std::vector<float>& mediator_bias,
                 const std::vector<float>& fusion_weights, const std::vector<float>& fusion_bias,
                 int experts, int top_k, int reasoning_steps, std::vector<float>& output);

}
