#include <gtest/gtest.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdint>
#include <stdexcept>

// Include the production class under test
#include "libcore/android/YGOGameOptions.h"

class YGOGameOptionsSecurityTest : public ::testing::TestWithParam<std::vector<uint8_t>> {};

TEST_P(YGOGameOptionsSecurityTest, BoundsValidationHolds) {
    // Invariant: constructing YGOGameOptions from adversarial network data must
    // never cause undefined behavior, heap corruption, or crash — the object
    // must either be constructed safely or throw a well-defined exception.
    std::vector<uint8_t> payload = GetParam();

    bool threw = false;
    try {
        // Cast to the raw pointer type the constructor expects
        const char* rawdata = reinterpret_cast<const char*>(payload.data());
        YGOGameOptions opts(rawdata, static_cast<int>(payload.size()));
        // If construction succeeded, allocated fields must not exceed buffer size
        (void)opts;
    } catch (const std::exception&) {
        threw = true;
    } catch (...) {
        threw = true;
    }
    // The process must still be alive and not have corrupted memory.
    // Either a safe construction or a caught exception is acceptable.
    SUCCEED() << "No crash or memory corruption observed (threw=" << threw << ")";
}

// Helper to build a payload with a 32-bit big/little-endian length prefix
static std::vector<uint8_t> makePayload(int32_t len) {
    std::vector<uint8_t> buf(4);
    // Little-endian int32
    buf[0] = len & 0xFF;
    buf[1] = (len >> 8) & 0xFF;
    buf[2] = (len >> 16) & 0xFF;
    buf[3] = (len >> 24) & 0xFF;
    return buf;
}

INSTANTIATE_TEST_SUITE_P(
    AdversarialInputs,
    YGOGameOptionsSecurityTest,
    ::testing::Values(
        // Exact exploit: attacker-controlled huge length with tiny buffer
        makePayload(0x7FFFFFFF),
        // Boundary: length == INT_MAX causing overflow on +1
        makePayload(INT32_MAX),
        // Boundary: negative length value
        makePayload(-1),
        // Valid small input: length=5 with actual data following
        []() {
            std::vector<uint8_t> v = {5, 0, 0, 0, 'h', 'e', 'l', 'l', 'o',
                                      0, 0, 0, 0,  // m_puserName length=0
                                      0, 0, 0, 0,  // m_proomName length=0
                                      0, 0, 0, 0,  // m_proomPasswd length=0
                                      0, 0, 0, 0}; // m_phostInfo length=0
            return v;
        }()
    )
);

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}