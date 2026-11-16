#include "../app/src/main/cpp/rsi_kernels.h"
#include <cassert>
#include <cmath>
#include <vector>

using namespace girassol::rsi;

int main() {
    {
        std::vector<float> x{0.2f, -0.1f}, anchor{0.0f, 0.0f};
        std::vector<float> w(4, 0.0f), b(2, 0.0f), gate(2, 12.0f), out;
        assert(rdt_refine(x, anchor, w, w, b, gate, 3, out));
        assert(std::abs(out[0]-x[0]) < 0.001f && std::abs(out[1]-x[1]) < 0.001f);
    }
    {
        std::vector<float> x{0.2f, 0.4f}, state(2, 0.0f), memory(4, 0.0f);
        assert(modusx_step(x, state, memory, 0.5f, 0.1f));
        assert(std::isfinite(state[0]) && std::isfinite(state[1]));
        assert(std::abs(memory[0]) + std::abs(memory[1]) + std::abs(memory[2]) + std::abs(memory[3]) > 0.0f);
        assert(!modusx_step(x, state, memory, 1.5f, 0.1f));
    }
    {
        const int d=2, e=2;
        std::vector<float> x{0.1f, 0.2f}, rw(e*d, 0.1f), rb(e, 0.0f);
        std::vector<float> ew(e*d*d, 0.01f), eb(e*d, 0.0f);
        std::vector<float> mw(d*d, 0.01f), mb(d, 0.0f), fw(d*d, 0.01f), fb(d, 0.0f), out;
        assert(lunaris_moc(x, rw, rb, ew, eb, mw, mb, fw, fb, e, 1, 2, out));
        assert(out.size()==2 && std::isfinite(out[0]) && std::isfinite(out[1]));
        assert(!lunaris_moc(x, rw, rb, ew, eb, mw, mb, fw, fb, e, 3, 2, out));
    }
    return 0;
}
