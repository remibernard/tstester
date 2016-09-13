package tstester.typesystem;

public abstract class Source implements Comparable<Source>
{
    public boolean contents;
    public transient Statement stmt;

    public static class Variable extends Source
    {
        public tstester.typesystem.Variable var;

        public Variable(tstester.typesystem.Variable var, boolean contents)
        {
            this.var = var;
            this.contents = contents;
        }

        public Variable(Variable other)
        {
            super(other);

            this.var = other.var;
        }

        public int compareTo(Source other)
        {
            int c = super.compareTo(other);
            if (c != 0) return c;

            Variable that = (Variable)(other);
            return var.compareTo(that.var);
        }

        public String toString()
        {
            return "<" + var + ">";
        }

        public Variable clone()
        {
            return new Variable(this);
        }

        public int hashCode()
        {
            return (
                7 * super.hashCode() +
                11 * var.hashCode()
            );
        }

        public boolean equals(Object other)
        {
            if (other == null || !getClass().equals(other.getClass()))
                return false;

            Variable that = (Variable)(other);
            return (
                contents == that.contents &&
                var.equals(that.var)
            );
        }
    }

    public static class Value extends Source
    {
        public tstester.typesystem.Value value;

        public Value(tstester.typesystem.Value value, boolean contents)
        {
            this.value = value;
            this.contents = contents;
        }

        public Value(Value other)
        {
            super(other);

            this.value = other.value;
        }

        public int compareTo(Source other)
        {
            int c = super.compareTo(other);
            if (c != 0) return c;

            Value that = (Value)(other);
            return value.compareTo(that.value);
        }

        public String toString()
        {
            return "<" + value + ">";
        }

        public Value clone()
        {
            return new Value(this);
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

            Value that = (Value)(other);
            return (
                contents == that.contents &&
                value.equals(that.value)
            );
        }
    }

    public static Source create(tstester.typesystem.Variable var)
    {
        return new Variable(var, false);
    }

    public static Source create(tstester.typesystem.Variable var, boolean contents)
    {
        return new Variable(var, contents);
    }

    public static Source create(tstester.typesystem.Value value)
    {
        return new Value(value, false);
    }

    public static Source create(tstester.typesystem.Value value, boolean contents)
    {
        return new Value(value, contents);
    }

    private Source()
    {
        this.contents = false;
        this.stmt = null;
    }

    public Source(Source other)
    {
        this.contents = other.contents;
        this.stmt = other.stmt;
    }

    public int compareTo(Source other)
    {
        return hashCode() - other.hashCode();
    }

    public String toString()
    {
        return "<?>";
    }

    public abstract Source clone();

    public int hashCode()
    {
        return contents ? 1 : 0;
    }

    public boolean equals(Object other)
    {
        if (other == null || !getClass().equals(other.getClass()))
            return false;

        Source that = (Source)(other);
        return contents == that.contents;
    }
}
