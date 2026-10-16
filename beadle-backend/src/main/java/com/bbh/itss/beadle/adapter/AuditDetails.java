package com.bbh.itss.beadle.adapter;

import com.bbh.itss.beadle.domain.audit.ActionRecord;
import lombok.NoArgsConstructor;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PRIVATE;

/**
 * Writes the {@code DETAILS} of an audit row as JSON of at most {@link ActionRecord#DETAILS_MAX} bytes that stays
 * JSON however long the request was: the tree is trimmed, never the text. While the text is too long, the longest
 * strings are cut to a common length, each ending in {@value #CUT}, down to {@value #SHORTEST} characters; then the
 * longest arrays keep their first elements, followed by {@code "… 12 more"}; and when even that is too long, the
 * details give way to {@code {"truncated": true, "bytes": 123456}} (the length of the whole JSON) and those of their
 * top-level properties that still fit. The {@value ActionRecord#RESULT} property, last, is kept whole throughout. The
 * audit interceptor writes the arguments of a use case with it, and the API the body of a request it refused.
 */
@NoArgsConstructor(access = PRIVATE)
public final class AuditDetails {

    /** The end of a string that was cut, and the start of the element that stands for the elements left out. */
    public static final String CUT = "…";
    /** The property that says the details were too long to show even trimmed, first in their place. */
    public static final String TRUNCATED = "truncated";
    /** The property, after {@value #TRUNCATED}, with the length in bytes of the details as JSON before trimming. */
    public static final String BYTES = "bytes";
    /** The fewest characters a string is cut to before arrays lose elements. */
    static final int SHORTEST = 32;

    private static final int WHOLE = Integer.MAX_VALUE;

    /** The details as JSON of at most {@link ActionRecord#DETAILS_MAX} bytes; {@code null} for none. */
    public static String write(JsonMapper json, ObjectNode details) {
        return write(json, details, ActionRecord.DETAILS_MAX);
    }

    static String write(JsonMapper json, ObjectNode details, int maxBytes) {
        if (details == null) {
            return null;
        }
        byte[] whole = json.writeValueAsBytes(details);
        if (whole.length <= maxBytes) {
            return new String(whole, UTF_8);
        }
        Trimming trimming = new Trimming(json, maxBytes);
        Longest longest = new Longest();
        details.properties().stream().filter(property -> !ActionRecord.RESULT.equals(property.getKey()))
                .forEach(property -> longest.measure(property.getValue()));
        byte[] trimmed = largest(SHORTEST, Math.min(longest.string, maxBytes),
                strings -> trimming.written(details, strings, WHOLE))
                .or(() -> largest(0, longest.array, elements -> trimming.written(details, SHORTEST, elements)))
                .orElseGet(() -> trimming.truncated(details, whole.length));
        return new String(trimmed, UTF_8);
    }

    /**
     * What the largest limit from {@code least} to {@code most} writes within the bytes allowed, found by halving the
     * range; nothing when even {@code least} writes too much.
     */
    private static Optional<byte[]> largest(int least, int most, IntFunction<byte[]> writing) {
        byte[] fitting = writing.apply(least);
        if (fitting == null) {
            return Optional.empty();
        }
        int low = least;
        int high = most;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            byte[] written = writing.apply(middle);
            if (written == null) {
                high = middle - 1;
            } else {
                low = middle;
                fitting = written;
            }
        }
        return Optional.of(fitting);
    }

    /** The string cut to {@code length} characters and marked with {@value #CUT}, or whole when it is no longer. */
    static String cut(String text, int length) {
        if (text.length() <= length) {
            return text;
        }
        int end = length > 0 && Character.isHighSurrogate(text.charAt(length - 1)) ? length - 1 : length;
        return text.substring(0, end) + CUT;
    }

    /** The string that stands, last in an array, for the elements left out of it. */
    static String leftOut(int elements) {
        return CUT + " " + elements + " more";
    }

    /** The lengths of the longest string and the longest array of a tree. */
    private static final class Longest {

        private int string;
        private int array;

        private void measure(JsonNode node) {
            if (node.isString()) {
                string = Math.max(string, node.stringValue().length());
            } else if (node.isArray()) {
                array = Math.max(array, node.size());
            }
            node.values().forEach(this::measure);
        }
    }

    private record Trimming(JsonMapper json, int maxBytes) {

        /**
         * The details written with every string cut to {@code strings} characters and every array to {@code elements}
         * elements, the result whole; {@code null} as soon as they take more than the bytes allowed.
         */
        private byte[] written(ObjectNode details, int strings, int elements) {
            Bounded out = new Bounded(maxBytes);
            try (JsonGenerator generator = json.createGenerator(out)) {
                generator.writeStartObject();
                for (Map.Entry<String, JsonNode> property : details.properties()) {
                    generator.writeName(property.getKey());
                    if (ActionRecord.RESULT.equals(property.getKey())) {
                        generator.writeTree(property.getValue());
                    } else {
                        write(generator, property.getValue(), strings, elements);
                    }
                }
                generator.writeEndObject();
            } catch (TooLong e) {
                return null;
            }
            return out.toByteArray();
        }

        /**
         * {@code {"truncated": true, "bytes": 123456}}, then each top-level property of the details that still fits
         * trimmed as far as it goes, and the result last; without the result only when even it does not fit.
         */
        private byte[] truncated(ObjectNode details, int bytes) {
            JsonNode result = details.get(ActionRecord.RESULT);
            ObjectNode shown = json.createObjectNode().put(TRUNCATED, true).put(BYTES, bytes);
            for (Map.Entry<String, JsonNode> property : details.properties()) {
                String name = property.getKey();
                if (!shown.has(name) && !ActionRecord.RESULT.equals(name)) {
                    shown.set(name, property.getValue());
                    if (written(withResult(shown, result), SHORTEST, 0) == null) {
                        shown.remove(name);
                    }
                }
            }
            byte[] written = written(withResult(shown, result), SHORTEST, 0);
            return written != null ? written
                    : json.writeValueAsBytes(json.createObjectNode().put(TRUNCATED, true).put(BYTES, bytes));
        }

        private ObjectNode withResult(ObjectNode shown, JsonNode result) {
            ObjectNode details = shown.deepCopy();
            if (result != null) {
                details.set(ActionRecord.RESULT, result);
            }
            return details;
        }

        private static void write(JsonGenerator out, JsonNode node, int strings, int elements) {
            if (node.isObject()) {
                out.writeStartObject();
                for (Map.Entry<String, JsonNode> property : node.properties()) {
                    out.writeName(property.getKey());
                    write(out, property.getValue(), strings, elements);
                }
                out.writeEndObject();
            } else if (node.isArray()) {
                out.writeStartArray();
                int kept = Math.min(node.size(), elements);
                for (int index = 0; index < kept; index++) {
                    write(out, node.get(index), strings, elements);
                }
                if (kept < node.size()) {
                    out.writeString(leftOut(node.size() - kept));
                }
                out.writeEndArray();
            } else if (node.isString()) {
                out.writeString(cut(node.stringValue(), strings));
            } else {
                out.writeTree(node);
            }
        }
    }

    /** The bytes written, until there are more than allowed: then it says so once and takes no more. */
    private static final class Bounded extends ByteArrayOutputStream {

        private final int maxBytes;
        private boolean over;

        private Bounded(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int b) {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (over) {
                return;
            }
            if (count + length > maxBytes) {
                over = true;
                throw new TooLong();
            }
            super.write(bytes, offset, length);
        }
    }

    private static final class TooLong extends RuntimeException {

        private TooLong() {
            super(null, null, false, false);
        }
    }
}
