package tstester.grammar;

import java.util.*;

public class Node implements Iterable<Map.Entry<Edge, Node>>
{
    public static final Node ANY;

    public final String name;
    public final NavigableMap<Edge, Node> edges;
    public Node parent;
    public Symbol sym;

    public transient int length;

    static
    {
        ANY = new Node();
    }

    private Node()
    {
        this.name = null;
        this.edges = null;
        this.parent = null;
        this.sym = null;

        this.length = 0;
    }

    public Node(String name)
    {
        this.name = name;
        this.edges = new TreeMap<>();
        this.parent = null;
        this.sym = null;

        this.length = 0;
    }

    public Node(Symbol sym)
    {
        this(sym.name);

        this.sym = sym;
    }

    public Node(String name, Map<Edge, Node> edges)
    {
        this(name);

        for (Map.Entry<Edge, Node> entry : edges.entrySet())
            put(entry.getKey(), entry.getValue());
    }

    public Node(String name, List<Edge> edges)
    {
        this(name);

        for (Edge edge : edges)
            put(edge, Node.ANY);
    }

    public Node(String name, Symbol sym)
    {
        this(name);

        this.sym = sym;
    }

    public Node(Node other)
    {
        this.name = other.name;
        this.edges = new TreeMap<>();
        this.parent = other.parent;
        this.sym = other.sym;

        this.length = other.length;

        for (Map.Entry<Edge, Node> entry : other.edges.entrySet())
            put(entry.getKey(), entry.getValue().clone());
    }

    public List<String> generate()
    {
        List<String> values = new LinkedList<>();

        for (Node node : edges.values()) {
            if (node == Node.ANY)
                throw new RuntimeException("Missing syntax node element");

            values.addAll(node.generate());
        }

        return values;
    }

    public Node get(Edge edge)
    {
        return edges.get(edge);
    }

    public Edge getKey(Node node)
    {
        for (Map.Entry<Edge, Node> entry : edges.entrySet())
            if (node == entry.getValue())
                return entry.getKey();

        return null;
    }

    public void put(Edge edge, Node node)
    {
        if (sym == null) sym = edge.src;

        edges.put(edge, node);

        if (node != Node.ANY) node.parent = this;
    }

    public Node remove(Edge edge)
    {
        Node node = edges.remove(edge);

        if (node != null && node != Node.ANY)
            node.parent = null;

        return node;
    }

    public Node remove(Node node)
    {
        return remove(getKey(node));
    }

    public int size()
    {
        return edges.size();
    }

    public Iterator<Map.Entry<Edge, Node>> iterator()
    {
        return edges.entrySet().iterator();
    }

    public String toString()
    {
        return name;
    }

    public Node clone()
    {
        return new Node(this);
    }

    public int hashCode()
    {
        return (
            7 * name.hashCode() +
            11 * edges.hashCode() +
            17 * (sym == null ? 0 : sym.hashCode())
        );
    }

    public boolean equals(Object other)
    {
        if (other == null || !getClass().equals(other.getClass()))
            return false;

        Node that = (Node)(other);
        return (
            name.equals(that.name) &&
            edges.equals(that.edges) &&
            (sym == null ? that.sym == null : sym.equals(that.sym))
        );
    }
}
