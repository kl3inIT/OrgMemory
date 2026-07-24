package com.orgmemory.core.authorization;

import java.util.List;
import java.util.Objects;

/**
 * One node of a relationship expansion, in provider-neutral form.
 *
 * <p>An expansion answers "which usersets grant this relation on this object", which is the
 * shape an administrator needs to be told <em>why</em> a decision came out the way it did. It
 * is not itself a decision: a node names the ways access could be reached, and the caller
 * still has to walk it against the actor's own relationships to find the one that applies.
 *
 * <p>The variants mirror the authorization model. {@link Direct} is a relation written as
 * tuples, {@link Computed} is a relation rewritten to another relation on the same object,
 * and {@link TupleToUserset} is the {@code X from Y} hop that makes inheritance work. The
 * remaining three are the set operations a relation definition composes them with.
 */
public sealed interface ExpansionNode {

    String name();

    /** A relation populated by tuples; {@code users} are the subjects written against it. */
    record Direct(String name, List<String> users) implements ExpansionNode {

        public Direct {
            name = requireName(name);
            users = List.copyOf(Objects.requireNonNull(users, "users"));
        }
    }

    /** A relation rewritten to another relation on the same object. */
    record Computed(String name, String userset) implements ExpansionNode {

        public Computed {
            name = requireName(name);
            userset = requireText(userset, "userset");
        }
    }

    /** The {@code computed from tupleset} hop: follow {@code tupleset}, then ask {@code computed}. */
    record TupleToUserset(String name, String tupleset, List<String> computed) implements ExpansionNode {

        public TupleToUserset {
            name = requireName(name);
            tupleset = requireText(tupleset, "tupleset");
            computed = List.copyOf(Objects.requireNonNull(computed, "computed"));
        }
    }

    record Union(String name, List<ExpansionNode> children) implements ExpansionNode {

        public Union {
            name = requireName(name);
            children = requireChildren(children);
        }
    }

    record Intersection(String name, List<ExpansionNode> children) implements ExpansionNode {

        public Intersection {
            name = requireName(name);
            children = requireChildren(children);
        }
    }

    record Difference(String name, ExpansionNode base, ExpansionNode subtract) implements ExpansionNode {

        public Difference {
            name = requireName(name);
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(subtract, "subtract");
        }
    }

    private static String requireName(String value) {
        return requireText(value, "name");
    }

    private static List<ExpansionNode> requireChildren(List<ExpansionNode> children) {
        List<ExpansionNode> copy = List.copyOf(Objects.requireNonNull(children, "children"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("A composed expansion node must have children");
        }
        return copy;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
