package tstester.grammar;

import java.util.*;

public class Production extends Symbol
{
    public Edge[][] symbols;

    public Production(String name)
    {
        this(name, new ArrayList<List<Edge>>(), new ArrayList<Edge>());
    }

    public Production(String name, List<List<Edge>> symbols)
    {
        this(name, symbols, new ArrayList<Edge>());
    }

    public Production(String name, List<List<Edge>> symbols, List<Edge> parents)
    {
        super(name, parents);
        this.symbols = new Edge[symbols.size()][];

        for (int i = 0; i != symbols.size(); ++i)
            this.symbols[i] = symbols.get(i).toArray(new Edge[0]);
    }

    public Production(Production other)
    {
        super(other.name, Arrays.asList(other.parents));
        this.symbols = new Edge[other.symbols.length][];

        for (int i = 0; i != other.symbols.length; ++i)
            this.symbols[i] = other.symbols[i].clone();
    }

    public Edge get(int alt, int ix)
    {
        return symbols[alt][ix];
    }

    public Production clone()
    {
        return new Production(this);
    }
}
