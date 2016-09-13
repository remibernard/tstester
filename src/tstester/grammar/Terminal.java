package tstester.grammar;

import java.util.*;

public class Terminal extends Symbol
{
    public final String value;
    public final boolean regex;

    public Terminal(String name, String value)
    {
        this(name, value, false, new ArrayList<Edge>());
    }

    public Terminal(String name, String value, boolean regex)
    {
        this(name, value, regex, new ArrayList<Edge>());
    }

    public Terminal(String name, String value, List<Edge> parents)
    {
        this(name, value, false, parents);
    }

    public Terminal(String name, String value, boolean regex, List<Edge> parents)
    {
        super(name, parents);

        this.value = value;
        this.regex = regex;
    }

    public Terminal(Terminal other)
    {
        super(other);

        this.value = other.value;
        this.regex = other.regex;
    }

    public Terminal clone()
    {
        return new Terminal(this);
    }
}
