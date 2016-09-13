package tstester.grammar;

import java.util.*;

public class Path implements Iterable<Edge>
{
    public final List<Edge> edges;

    public static Path combine(Path a, Path b)
    {
        Path c = a.clone();

        c.edges.addAll(b.edges);

        return c;
    }

    public Path()
    {
        this.edges = new ArrayList<>();
    }

    public Path(List<Edge> edges)
    {
        this();

        this.edges.addAll(edges);
    }

    public Path(Path other)
    {
        this(other.edges);
    }

    public Edge first()
    {
        return edges.get(0);
    }

    public Edge last()
    {
        return edges.get(edges.size() - 1);
    }

    public void unshift(Edge edge)
    {
        edges.add(0, edge);
    }

    public Edge shift()
    {
        return edges.remove(0);
    }

    public void push(Edge edge)
    {
        edges.add(edge);
    }

    public Edge pop()
    {
        return edges.remove(edges.size() - 1);
    }

    public Edge get(int ix)
    {
        return edges.get(ix);
    }

    public int size()
    {
        return edges.size();
    }

    public void add(Edge edge)
    {
        push(edge);
    }

    public Path subPath(int from, int to)
    {
        return new Path(edges.subList(from, to));
    }

    public Iterator<Edge> iterator()
    {
        return edges.iterator();
    }

    public String toString()
    {
        StringBuilder repr = new StringBuilder();

        for (Edge edge : edges)
            repr.append('/').append(edge.toString());

        if (!edges.isEmpty()) {
            repr.append('/').append(last().dst.name);

            repr.deleteCharAt(0);
        }

        return repr.toString();
    }

    public Path clone()
    {
        return new Path(this);
    }

    public int hashCode()
    {
        return edges.hashCode();
    }

    public boolean equals(Object other)
    {
        if (other == null || !getClass().equals(other.getClass()))
            return false;

        Path that = (Path)(other);
        return edges.equals(that.edges);
    }
}
