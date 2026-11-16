#include "rsi_kernels.h"

#include <algorithm>
#include <cmath>
#include <utility>

namespace girassol::rsi {
namespace {
float sigmoid(float x) { return 1.0f / (1.0f + std::exp(-std::clamp(x, -18.0f, 18.0f))); }
bool finite(const std::vector<float>& v) {
    return std::all_of(v.begin(), v.end(), [](float x) { return std::isfinite(x); });
}
}

bool rdt_refine(const std::vector<float>& input, const std::vector<float>& anchor,
                const std::vector<float>& wi, const std::vector<float>& wr,
                const std::vector<float>& bias, const std::vector<float>& gate,
                int loops, std::vector<float>& output) {
    const size_t d = input.size();
    if (d == 0 || d > 2048 || anchor.size() != d || bias.size() != d || gate.size() != d ||
        wi.size() != d*d || wr.size() != d*d || loops < 1 || loops > 16 ||
        !finite(input) || !finite(anchor) || !finite(wi) || !finite(wr) || !finite(bias) || !finite(gate)) return false;
    output = input;
    std::vector<float> next(d);
    for (int step = 0; step < loops; ++step) {
        for (size_t i = 0; i < d; ++i) {
            float z = bias[i] + anchor[i];
            const size_t row = i*d;
            for (size_t j = 0; j < d; ++j) z += wi[row+j] * input[j] + wr[row+j] * output[j];
            const float g = sigmoid(gate[i]);
            next[i] = g * output[i] + (1.0f-g) * std::tanh(z);
        }
        output.swap(next);
    }
    return finite(output);
}

bool modusx_step(const std::vector<float>& input, std::vector<float>& state,
                 std::vector<float>& memory, float decay, float learning_rate) {
    const size_t d = input.size();
    if (d == 0 || d > 512 || state.size() != d || memory.size() != d*d ||
        !std::isfinite(decay) || decay < 0.0f || decay > 1.0f ||
        !std::isfinite(learning_rate) || learning_rate < 0.0f || learning_rate > 1.0f ||
        !finite(input) || !finite(state) || !finite(memory)) return false;
    std::vector<float> retrieved(d, 0.0f);
    float norm = 1e-5f;
    for (size_t j = 0; j < d; ++j) norm += input[j]*input[j];
    for (size_t i = 0; i < d; ++i) {
        const size_t row = i*d;
        for (size_t j = 0; j < d; ++j) retrieved[i] += memory[row+j] * input[j];
    }
    std::vector<float> next(d);
    for (size_t i = 0; i < d; ++i) {
        next[i] = decay * state[i] + (1.0f-decay) * std::tanh(input[i] + retrieved[i]);
    }
    if (learning_rate > 0.0f) {
        const float scale = learning_rate / norm;
        for (size_t i = 0; i < d; ++i) {
            const float error = next[i] - retrieved[i];
            const size_t row = i*d;
            for (size_t j = 0; j < d; ++j) memory[row+j] += scale * error * input[j];
        }
    }
    state.swap(next);
    return finite(state) && finite(memory);
}

bool lunaris_moc(const std::vector<float>& input,
                 const std::vector<float>& rw, const std::vector<float>& rb,
                 const std::vector<float>& ew, const std::vector<float>& eb,
                 const std::vector<float>& mw, const std::vector<float>& mb,
                 const std::vector<float>& fw, const std::vector<float>& fb,
                 int experts, int top_k, int reasoning_steps, std::vector<float>& output) {
    const size_t d = input.size();
    if (d == 0 || d > 512 || experts < 2 || experts > 16 || top_k < 1 || top_k > experts ||
        reasoning_steps < 1 || reasoning_steps > 8 || rw.size() != (size_t)experts*d ||
        rb.size() != (size_t)experts || ew.size() != (size_t)experts*d*d ||
        eb.size() != (size_t)experts*d || mw.size() != d*d || mb.size() != d ||
        fw.size() != d*d || fb.size() != d || !finite(input) || !finite(rw) || !finite(rb) ||
        !finite(ew) || !finite(eb) || !finite(mw) || !finite(mb) || !finite(fw) || !finite(fb)) return false;

    std::vector<std::pair<float,int>> route;
    route.reserve(experts);
    for (int e = 0; e < experts; ++e) {
        float logit = rb[(size_t)e];
        for (size_t j = 0; j < d; ++j) logit += rw[(size_t)e*d+j] * input[j];
        route.emplace_back(logit, e);
    }
    std::partial_sort(route.begin(), route.begin()+top_k, route.end(),
        [](const auto& a, const auto& b) { return a.first > b.first; });
    float max_logit = route.front().first, denom = 0.0f;
    for (int k = 0; k < top_k; ++k) {
        route[(size_t)k].first = std::exp(std::clamp(route[(size_t)k].first-max_logit, -18.0f, 0.0f));
        denom += route[(size_t)k].first;
    }
    std::vector<float> aggregate(d, 0.0f);
    const float alpha = 1.0f / std::sqrt((float)reasoning_steps);
    for (int k = 0; k < top_k; ++k) {
        const int e = route[(size_t)k].second;
        const float weight = route[(size_t)k].first / std::max(denom, 1e-8f);
        std::vector<float> h = input, next(d);
        const size_t base = (size_t)e*d*d;
        for (int step = 0; step < reasoning_steps; ++step) {
            for (size_t i = 0; i < d; ++i) {
                float z = eb[(size_t)e*d+i] + input[i];
                const size_t row = base + i*d;
                for (size_t j = 0; j < d; ++j) z += ew[row+j] * h[j];
                next[i] = h[i] + alpha * std::tanh(z);
            }
            h.swap(next);
        }
        for (size_t i = 0; i < d; ++i) aggregate[i] += weight*h[i];
    }
    std::vector<float> mediator(d), fused(d);
    for (size_t i = 0; i < d; ++i) {
        float m = mb[i], g = fb[i];
        const size_t row = i*d;
        for (size_t j = 0; j < d; ++j) {
            m += mw[row+j] * aggregate[j];
            g += fw[row+j] * input[j];
        }
        mediator[i] = std::tanh(m);
        const float gamma = sigmoid(g);
        fused[i] = gamma*mediator[i] + (1.0f-gamma)*aggregate[i];
    }
    output.swap(fused);
    return finite(output);
}
}
