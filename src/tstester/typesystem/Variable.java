package tstester.typesystem;

import java.util.*;

public class Variable implements Comparable<Variable>
{
    public final String name;
    public final List<Source> sources;
    public final NavigableMap<String, Variable> fixed;
    public final NavigableMap<Variable, Variable> fields;
    public final boolean junction;

    public transient Variable parent;
    public transient Rule rule;

    public static MatchComparator matcher = new MatchComparator();
    public static int unnamed = 0;

    public static class MatchComparator implements Comparator<Variable>
    {
        public int compare(Variable a, Variable b)
        {
            int c;

            if (a == b) return 0;

            if (a == null || b == null)
                return a == null ? 1 : -1;

            if ((c = compareFields(a.fields, b.fields)) != 0)
                return c;

            if ((c = a.sources.size() - b.sources.size()) != 0)
                return c;

            for (int i = 0; i != a.sources.size(); ++i)
                if ((c = a.sources.get(i).compareTo(b.sources.get(i))) != 0)
                    return c;

            return 0;
        }

        private int compareFields(
            NavigableMap<Variable, Variable> a,
            NavigableMap<Variable, Variable> b
        )
        {
            int c;

            Iterator<Variable> iter = b.navigableKeySet().iterator();
            for (Variable v : a.navigableKeySet()) {
                if (!iter.hasNext()) return 1;
                Variable u = iter.next();

                if (
                    (c = compare(v, u)) != 0 ||
                    (c = compare(a.get(v), b.get(u))) != 0
                )
                    return c;
            }

            return iter.hasNext() ? -1 : 0;
        }
    }

    public static Variable junction()
    {
        return new Variable(null, Collections.<Source>emptyList(), true);
    }

    public Variable()
    {
        this("@" + unnamed++, Collections.<Source>emptyList());
    }

    public Variable(String name)
    {
        this(name, Collections.<Source>emptyList());
    }

    public Variable(String name, Variable source)
    {
        this(name, Collections.singletonList(Source.create(source)));
    }

    public Variable(String name, Value source)
    {
        this(name, Collections.singletonList(Source.create(source)));
    }

    public Variable(String name, List<Source> sources)
    {
        this(name, sources, false);
    }

    private Variable(String name, List<Source> sources, boolean junction)
    {
        this.name = name;
        this.sources = new ArrayList<>(sources);
        this.junction = junction;

        this.fixed = new TreeMap<>();
        this.fields = new TreeMap<>();

        this.parent = null;
        this.rule = null;
    }

    public Variable(Variable other)
    {
        this.name = other.name;
        this.sources = new ArrayList<>(other.sources);
        this.junction = other.junction;

        this.fixed = new TreeMap<>();
        for (String key : other.fixed.navigableKeySet())
            this.fixed.put(key, other.fixed.get(key).clone());

        this.fields = new TreeMap<>(matcher);
        for (Variable key : other.fields.navigableKeySet())
            this.fields.put(key.clone(), other.fields.get(key).clone());

        this.parent = other.parent;
        this.rule = other.rule;
    }

    public Variable get(String key)
    {
        if (key == null) return null;

        return get(fixed.get(key));
    }

    public Variable get(String... keys)
    {
        Variable var = this;

        for (String key : keys)
            if ((var = var.get(key)) == null)
                return null;

        return var;
    }

    public Variable get(Variable key)
    {
        if (key == null) return null;

        return fields.get(key);
    }

    public Variable get(Variable... keys)
    {
        Variable var = this;

        for (Variable key : keys)
            if ((var = var.get(key)) == null)
                return null;

        return var;
    }

    public Variable getKey(Variable value)
    {
        for (Map.Entry<Variable, Variable> entry : fields.entrySet())
            if (entry.getValue() == value)
                return entry.getKey();

        return null;
    }

    public void put(String key, Variable value)
    {
        put(new Variable(key, new Value.Basic(key)), value);
    }

    public void put(Variable key, Variable value)
    {
        fields.put(key, value);

        key.parent = this;
        value.parent = this;

        if (key.name != null && !fixed.containsKey(key.name))
            fixed.put(key.name, key);
    }

    public Variable remove(String key)
    {
        return remove(fixed.get(key));
    }

    public Variable remove(Variable key)
    {
        if (key == null) return null;
        key.parent = null;

        if (key.name != null && key == fixed.get(key.name))
            fixed.remove(key.name);

        Variable value = fields.remove(key);

        if (value == null) return null;
        value.parent = null;

        return value;
    }

    public int compareTo(Variable other)
    {
        int c;

        if (name == null || other.name == null) {
            if (name != other.name)
                return name == null ? 1 : -1;

        } else if ((c = name.compareTo(other.name)) != 0) {
            return c;
        }

        return matcher.compare(this, other);
    }

    public String toString()
    {
        return name == null ? "?" : name;
    }

    public Variable clone()
    {
        return new Variable(this);
    }

    public int hashCode()
    {
        return (
            7 * (name != null ? name.hashCode() : 0) +
            11 * sources.hashCode() +
            13 * fields.hashCode()
        );
    }

    public boolean equals(Object other)
    {
        return (
            other != null &&
            getClass().equals(other.getClass()) &&
            compareTo((Variable)(other)) == 0
        );
    }
}
