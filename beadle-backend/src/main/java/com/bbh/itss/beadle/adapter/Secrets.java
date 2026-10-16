package com.bbh.itss.beadle.adapter;

import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static lombok.AccessLevel.PRIVATE;

/**
 * Keeps secrets out of the audit trail: every property of a JSON tree whose name looks like a secret (a token, a
 * password, a client secret, an {@code Authorization} header, a credential or an API key) is replaced by
 * {@value #MASK}, at every depth. The audit interceptor masks the arguments of a use case with it, and the API the
 * body of a request it refused before the use case ran.
 */
@NoArgsConstructor(access = PRIVATE)
public final class Secrets {

    public static final String MASK = "*****";
    public static final Pattern SECRET =
            Pattern.compile("token|password|passwd|secret|authorization|credential|api-?key", CASE_INSENSITIVE);

    /** The node with every property that looks like a secret masked; the node itself when its name looks like one. */
    public static JsonNode masked(String name, JsonNode node) {
        if (SECRET.matcher(name).find() && !node.isNull()) {
            return StringNode.valueOf(MASK);
        }
        if (node instanceof ObjectNode object) {
            for (Map.Entry<String, JsonNode> property : new ArrayList<>(object.properties())) {
                object.set(property.getKey(), masked(property.getKey(), property.getValue()));
            }
        } else if (node.isArray()) {
            node.forEach(element -> masked("", element));
        }
        return node;
    }
}
