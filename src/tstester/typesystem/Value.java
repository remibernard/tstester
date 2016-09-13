package tstester.typesystem;

import java.util.*;
import tstester.grammar.*;

public abstract class Value implements Comparable<Value>
{
    public final String name;

    public static int unnamed = 0;

    public static class Basic extends Value
    {
        public final String value;

        public Basic(String value)
        {
            this(value, value);
        }

        public Basic(String name, String value)
        {
            super(name);

            this.value = value;
        }

        public Basic(Basic other)
        {
            super(other);

            this.value = other.value;
        }

        public int compareTo(Value other)
        {
            int c = super.compareTo(other);
            if (c != 0) return c;

            Basic that = (Basic)(other);
            return value.compareTo(that.value);
        }

        public String toString()
        {
            return "[" + name + "]:" + value;
        }

        public Basic clone()
        {
            return new Basic(this);
        }

        public int hashCode()
        {
            return (
                7 * super.hashCode() +
                11 * value.hashCode()
            );
        }

        public boolean equals(Object other)
        {
            if (other == null || !getClass().equals(other.getClass()))
                return false;

            Basic that = (Basic)(other);
            return (
                name.equals(that.name) &&
                value.equals(that.value)
            );
        }
    }

    public static class Compound extends Value
    {
        public final Map<Variable, Value> fields;

        public Compound()
        {
            this((String)(null));
        }

        public Compound(String name)
        {
            super(name);

            this.fields = new HashMap<>();
        }

        public Compound(Compound other)
        {
            super(other);

            this.fields = new HashMap<>();
            for (Map.Entry<Variable, Value> entry : other.fields.entrySet())
                this.fields.put(entry.getKey(), entry.getValue().clone());
        }

        public int compareTo(Value other)
        {
            int c = super.compareTo(other);
            if (c != 0) return c;

            Compound that = (Compound)(other);

            return compareFields(fields, that.fields);
        }

        public String toString()
        {
            return "[" + name + "]:(" + fields.size() + ")";
        }

        public Compound clone()
        {
            return new Compound(this);
        }

        public int hashCode()
        {
            return (
                7 * super.hashCode() +
                11 * fields.hashCode()
            );
        }

        public boolean equals(Object other)
        {
            if (other == null || !getClass().equals(other.getClass()))
                return false;

            Compound that = (Compound)(other);
            return (
                name.equals(that.name) &&
                fields.equals(that.fields)
            );
        }
    }

    public static class Symbol extends Value
    {
        public final tstester.grammar.Symbol sym;
        public Node node;

        public Symbol(tstester.grammar.Symbol sym)
        {
            this(sym.name, sym);
        }

        public Symbol(String name, tstester.grammar.Symbol sym)
        {
            super(name);

            this.sym = sym;
            this.node = null;
        }

        public Symbol(Symbol other)
        {
            super(other);

            this.sym = other.sym;
            this.node = null;
        }

        public int compareTo(Value other)
        {
            int c = super.compareTo(other);
            if (c != 0) return c;

            Symbol that = (Symbol)(other);
            return sym.name.compareTo(that.sym.name);
        }

        public String toString()
        {
            return "[" + name + "]:{" + sym.name + "}";
        }

        public Symbol clone()
        {
            return new Symbol(this);
        }

        public int hashCode()
        {
            return (
                7 * super.hashCode() +
                11 * sym.hashCode() +
                13 * (node == null ? 0 : node.hashCode())
            );
        }

        public boolean equals(Object other)
        {
            if (other == null || !getClass().equals(other.getClass()))
                return false;

            Symbol that = (Symbol)(other);
            return (
                name.equals(that.name) &&
                sym.equals(that.sym) &&
                (node == null ? that.node == null : node.equals(that.node))
            );
        }
    }

    public static class Type extends Value
    {
        public final boolean basic;
        public final Map<Variable, Value> fields;

        public Type()
        {
            this(null, false);
        }

        public Type(String name)
        {
            this(name, false);
        }

        public Type(String name, boolean basic)
        {
            super(name);

            this.basic = basic;
            this.fields = new HashMap<>();
        }

        public Type(Type other)
        {
            super(other);

            this.basic = other.basic;

            this.fields = new HashMap<>();
            for (Map.Entry<Variable, Value> entry : other.fields.entrySet())
                this.fields.put(entry.getKey(), entry.getValue().clone());
        }

        public int compareTo(Value other)
        {
            int c = super.compareTo(other);
            if (c != 0) return c;

            Type that = (Type)(other);

            if (basic != that.basic) return basic ? 1 : -1;

            return compareFields(fields, that.fields);
        }

        public String toString()
        {
            if (basic) return "[" + name + "]";

            return "[" + name + "]:(" + fields.size() + ")";
        }

        public Type clone()
        {
            return new Type(this);
        }

        public int hashCode()
        {
            return (
                7 * super.hashCode() +
                11 * fields.hashCode() +
                13 * (basic ? 1 : 0)
            );
        }

        public boolean equals(Object other)
        {
            if (other == null || !getClass().equals(other.getClass()))
                return false;

            Type that = (Type)(other);
            return (
                name.equals(that.name) &&
                fields.equals(that.fields) &&
                basic == that.basic
            );
        }
    }

    public Value(String name)
    {
        this.name = (name == null ? "@" + unnamed++ : name);
    }

    public Value(Value other)
    {
        this(other.name);
    }

    public int compareTo(Value other)
    {
        if (!getClass().equals(other.getClass()))
            return getClass().hashCode() - other.getClass().hashCode();

        return name.compareTo(other.name);
    }

    public String toString()
    {
        return "[" + name + "]:?";
    }

    public abstract Value clone();

    public int hashCode()
    {
        return name.hashCode();
    }

    public boolean equals(Object other)
    {
        if (other == null || !getClass().equals(other.getClass()))
            return false;

        Value that = (Value)(other);
        return name.equals(that.name);
    }

    protected static int compareFields(Map<Variable, Value> a, Map<Variable, Value> b)
    {
        int c = a.size() - b.size();
        if (c != 0) return c;

        List<Variable> aKeys = new ArrayList<>(a.keySet());
        Collections.sort(aKeys);

        List<Variable> bKeys = new ArrayList<>(b.keySet());
        Collections.sort(bKeys);

        for (int i = 0; i < aKeys.size(); ++i) {
            Variable aKey = aKeys.get(i), bKey = bKeys.get(i);
            Value aVal = a.get(aKey), bVal = b.get(bKey);

            if ((c = aKey.compareTo(bKey)) != 0)
                return c;

            if (aVal == null || bVal == null)
                return aVal == null ? 1 : -1;

            if ((c = aVal.compareTo(bVal)) != 0)
                return c;
        }

        return 0;
    }
}
