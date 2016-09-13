package tstester.grammar;

import java.util.*;

public class Subtree
{
    public Node root;

    public transient Path lastAdded;

    public Subtree(Node root)
    {
        this.root = root;

        this.lastAdded = null;
    }

    public Subtree(Node root, List<Path> paths)
    {
        this(root);

        for (Path path : paths)
            add(path);
    }

    public Subtree(Subtree other)
    {
        this(other.root.clone());
    }

    public void add(Path path)
    {
        add(root, path, Node.ANY);
    }

    public void add(Path path, Node node)
    {
        add(root, path, node);
    }

    public void add(Node base, Path path)
    {
        add(base, path, Node.ANY);
    }

    public void add(Node base, Path path, Node node)
    {
        if (path.size() == 0) return;

        // make sure the base (path root) is proper
        if (base.sym == null)
            base.sym = path.first().src;
        else if (base.sym != path.first().src)
            throw new IllegalArgumentException("Path/subtree root mismatch");

        // build down the path, creating new nodes as required
        Node cur, prev = base;
        Edge edge;

        for (int i = 0; i != path.size() - 1; ++i) {
            edge = path.get(i);

            // only create a new node if it wasn't already in the subtree
            if ((cur = prev.get(edge)) == null)
                prev.put(edge, (cur = new Node(edge.dst)));

            prev = cur;
        }

        // there is nothing already at the path's end;
        // add the provided node if valid, otherwise add a blank
        edge = path.last();
        if ((cur = prev.get(edge)) == null) {
            if (node == Node.ANY || node == null)
                node = new Node(edge.dst);

            prev.put(edge, node);

        // a node already exists at this location;
        // replace it with the provided node (if valid)
        } else if (node != Node.ANY && node != null) {
            cur.parent = null;
            prev.put(edge, node);
        }

        // rebuild the just-added path back up to the root
        // and save it to keep track of the last node added
        Path rooted = path.clone();
        for (prev = base; prev.parent != null; prev = prev.parent)
            rooted.unshift(prev.parent.getKey(prev));

        lastAdded = rooted;
    }

    public Node find(Path path)
    {
        Node node = root;

        for (Edge edge : path)
            if ((node = node.get(edge)) == null)
                throw new IllegalArgumentException(
                    "No such edge: " + edge.toString()
                );

        return node;
    }

    public int depth()
    {
        return root == null ? 0 : depth(root);
    }

    private int depth(Node node)
    {
        int d = 0, m = 0;

        for (Node sub : node.edges.values())
            if ((d = depth(sub) + 1) > m)
                m = d;

        return m;
    }

    public Subtree clone()
    {
        return new Subtree(this);
    }

    public int hashCode()
    {
        return root.hashCode();
    }

    public boolean equals(Object other)
    {
        if (other == null || !getClass().equals(other.getClass()))
            return false;

        Subtree that = (Subtree)(other);
        return root.equals(that.root);
    }
}
