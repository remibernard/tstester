package tstester.grammar;

import java.util.*;

public abstract class Symbol
{
    public final String name;
    public Edge[] parents;

    public Symbol(String name)
    {
        this.name = name;
        this.parents = new Edge[0];
    }

    public Symbol(String name, List<Edge> parents)
    {
        this.name = name;
        this.parents = parents.toArray(new Edge[0]);
    }

    public Symbol(Symbol other)
    {
        this(other.name, Arrays.asList(other.parents));
    }

    public String toString()
    {
        return name;
    }
}
