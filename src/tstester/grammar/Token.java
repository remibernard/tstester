package tstester.grammar;

import java.util.*;

public class Token extends Node
{
    public String value;

    public Token(String name, String value)
    {
        super(name);

        this.value = value;
        this.length = 1;
    }

    public Token(Symbol sym, String value)
    {
        super(sym);

        this.value = value;
        this.length = 1;
    }

    public Token(Token other)
    {
        super(other);

        this.value = other.value;
    }

    public List<String> generate()
    {
        List<String> values = new LinkedList<>();
        values.add(value);

        return values;
    }

    public String toString()
    {
        return value;
    }

    public Token clone()
    {
        return new Token(this);
    }

    public int hashCode()
    {
        return (
            23 * super.hashCode() +
            29 * value.hashCode()
        );
    }

    public boolean equals(Object other)
    {
        if (other == null || !getClass().equals(other.getClass()))
            return false;

        Token that = (Token)(other);
        return (
            name.equals(that.name) &&
            value.equals(that.value) &&
            edges.equals(that.edges) &&
            (parent == null ? that.parent == null : parent.equals(that.parent)) &&
            (sym == null ? that.sym == null : sym.equals(that.sym))
        );
    }
}
