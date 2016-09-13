package tstester.typesystem;

import java.util.*;
import tstester.grammar.*;

public class Statement
{
    public final Rule rule;
    public final Map<Variable, Source> vars;
    public final Map<Variable, Value> values;
    public final Map<Rule.Slot, Statement> insets;
    public Statement parent;

    public transient List<Subtree> dependencies;
    public transient Subtree generated;
    public transient int depth;

    public Statement(Rule rule)
    {
        this(rule, null);
    }

    public Statement(Rule rule, Statement parent)
    {
        this.rule = rule;
        this.vars = new IdentityHashMap<>();
        this.values = new IdentityHashMap<>();
        this.insets = new IdentityHashMap<>();
        this.parent = parent;

        this.dependencies = null;
        this.generated = null;
        this.depth = 0;
    }

    public Statement(Statement other)
    {
        this.rule = other.rule;
        this.parent = other.parent;

        this.vars = new IdentityHashMap<>();
        for (Map.Entry<Variable, Source> entry : other.vars.entrySet())
            this.vars.put(entry.getKey(), entry.getValue().clone());

        this.values = new IdentityHashMap<>();
        for (Map.Entry<Variable, Value> entry : other.values.entrySet())
            this.values.put(entry.getKey(), entry.getValue().clone());

        this.insets = new IdentityHashMap<>();
        for (Map.Entry<Rule.Slot, Statement> entry : other.insets.entrySet())
            this.insets.put(entry.getKey(), entry.getValue().clone());

        this.dependencies = null;
        this.generated = other.generated.clone();
        this.depth = other.depth;

        if (other.dependencies != null) {
            this.dependencies = new ArrayList<>();
            for (Subtree subtree : other.dependencies)
                this.dependencies.add(subtree.clone());
        }
    }

    public String toString()
    {
        return "|" + rule.toString() + "|";
    }

    public Statement clone()
    {
        return new Statement(this);
    }

    public int hashCode()
    {
        return (
            7 * rule.hashCode() +
            11 * vars.hashCode() +
            13 * values.hashCode() +
            17 * insets.hashCode()
        );
    }

    public boolean equals(Object other)
    {
        if (other == null || !getClass().equals(other.getClass()))
            return false;

        Statement that = (Statement)(other);
        return (
            rule == that.rule &&
            vars.equals(that.vars) &&
            values.equals(that.values) &&
            insets.equals(that.insets)
        );
    }
}
