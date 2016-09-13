package tstester.typesystem;

import java.util.*;
import tstester.grammar.*;

public class Rule
{
    public Symbol[] tokens;
    public final List<Slot> slots;
    public final Map<String, Variable> vars;
    public Symbol sym;

    public static class Slot {
        public enum Type { SYMBOL, TYPE_LITERAL, TYPED_VALUE }

        public final int ix;
        public final Type type;
        public final Variable var;

        public Slot(int ix, Type type, Variable var)
        {
            this.ix = ix;
            this.type = type;
            this.var = var;
        }

        public String toString()
        {
            switch (type) {
            case SYMBOL:
                return "($" + ix + ")";

            case TYPE_LITERAL:
                return "[$" + ix + "]";

            case TYPED_VALUE:
                return "<$" + ix + ">";

            default:
                return "$" + ix;
            }
        }

        public Slot clone()
        {
            throw new UnsupportedOperationException();
        }
    }

    public Rule()
    {
        this(new Symbol[0]);
    }

    public Rule(Symbol[] tokens)
    {
        this(tokens, Collections.<Slot>emptyList());
    }

    public Rule(Symbol[] tokens, List<Slot> slots)
    {
        this.tokens = tokens.clone();
        this.slots = new ArrayList<>(slots);
        this.vars = new HashMap<>();
        this.sym = null;
    }

    public String toString()
    {
        StringBuilder repr = new StringBuilder();
        repr.append('|');

        for (int i = 0; i != tokens.length; ++i) {
            if (i > 0) repr.append(' ');

            repr.append(tokens[i] == null ? "$" + i : tokens[i].name);
        }

        repr.append('|');
        return repr.toString();
    }

    public Rule clone()
    {
        throw new UnsupportedOperationException();
    }
}
