package com.example.invoice.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * invoice-service keys duplicate detection on this hash and import-service uses
 * it to spot unchanged rows, so a change in its output silently reprocesses
 * every event and re-imports every invoice.
 */
class HashUtilTest {

    @Test
    @DisplayName("matches the published SHA-256 of a known input")
    void matchesKnownVector() {
        // The standard test vector, not a value captured from this
        // implementation. It pins the algorithm, the UTF-8 encoding and the
        // lowercase hex format together.
        assertThat(HashUtil.computeSHA256("abc")).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("the empty string hashes rather than throwing")
    void hashesEmptyString() {
        assertThat(HashUtil.computeSHA256("")).isEqualTo(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("output is always 64 lowercase hex characters")
    void outputIsFixedWidthHex() {
        // %02x is easy to write as %x, which drops the leading zero on any byte
        // below 0x10 and yields a short, collision-prone string.
        assertThat(HashUtil.computeSHA256("x")).matches("[0-9a-f]{64}");
        assertThat(HashUtil.computeSHA256("a longer input string")).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("the same input always hashes the same")
    void isDeterministic() {
        assertThat(HashUtil.computeSHA256("invoice-1001"))
                .isEqualTo(HashUtil.computeSHA256("invoice-1001"));
    }

    @Test
    @DisplayName("different inputs hash differently")
    void differsOnDifferentInput() {
        assertThat(HashUtil.computeSHA256("invoice-1001"))
                .isNotEqualTo(HashUtil.computeSHA256("invoice-1002"));
    }
}