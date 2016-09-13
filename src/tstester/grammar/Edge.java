package tstester.grammar;

public class Edge implements Comparable<Edge>
{
    public final Production src;
    public final Symbol dst;
    public final int alt, ix, shw;
    public final boolean opt, list;

    public Edge(Production src, Symbol dst, int alt, int ix)
    {
        this(src, dst, alt, ix, 0, false, false);
    }

    public Edge(Production src, Symbol dst, int alt, int ix, boolean opt, boolean list)
    {
        this(src, dst, alt, ix, 0, opt, list);
    }

    public Edge(Production src, Symbol dst, int alt, int ix, int shw, boolean opt, boolean list)
    {
        this.src = src;
        this.dst = dst;
        this.alt = alt;
        this.ix = ix;
        this.shw = shw;
        this.opt = opt;
        this.list = list;
    }

    public Edge(Edge other)
    {
        this(
            other.src,
            other.dst,
            other.alt,
            other.ix,
            other.shw,
            other.opt,
            other.list
        );
    }

    public Edge shadow()
    {
        return shadow(shw + 1);
    }

    public Edge shadow(int s)
    {
        return new Edge(src, dst, alt, ix, s, opt, list);
    }

    public int compareTo(Edge other)
    {
        if (!src.name.equals(other.src.name))
            return src.name.compareTo(other.src.name);

        if (alt != other.alt)
            return alt - other.alt;

        if (ix != other.ix)
            return ix - other.ix;

        if (shw != other.shw)
            return shw - other.shw;

        if (!dst.name.equals(other.dst.name))
            return dst.name.compareTo(other.dst.name);

        if (opt != other.opt)
            return opt ? 1 : -1;

        if (list != other.list)
            return list ? 1 : -1;

        return 0;
    }

    public String toString()
    {
        StringBuilder repr = new StringBuilder();

        repr.append(src.name);
        if (shw != 0) repr.append('~').append(shw);

        repr.append('[').append(alt).append(':').append(ix).append(']');
        return repr.toString();
    }

    public Edge clone()
    {
        return new Edge(this);
    }

    public int hashCode()
    {
        return (
            7 * src.hashCode() +
            11 * dst.hashCode() +
            13 * alt +
            17 * ix +
            19 * shw +
            23 * (opt ? 0 : 1) +
            37 * (list ? 0 : 1)
        );
    }

    public boolean equals(Object other)
    {
        if (other == null || !getClass().equals(other.getClass()))
            return false;

        Edge that = (Edge)(other);
        return (
            src.equals(that.src) &&
            dst.equals(that.dst) &&
            alt == that.alt &&
            ix == that.ix &&
            shw == that.shw &&
            opt == that.opt &&
            list == that.list
        );
    }
}
