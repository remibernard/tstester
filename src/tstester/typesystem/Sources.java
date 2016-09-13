package tstester.typesystem;

import java.util.*;

public class Sources
{
    private Sources() { }

    public static List<Source> expand(Source base)
    {
        List<Source> sources = new ArrayList<>();

        // fetch the source's underlying value
        Variable var = (base instanceof Source.Variable)
            ? ((Source.Variable)(base)).var
            : null;

        Value value = (base instanceof Source.Value)
            ? ((Source.Value)(base)).value
            : null;

        // if the source points to a junction variable,
        // use the junction's sources instead
        if (var != null && var.junction) {
            for (Source source : var.sources)
                sources.addAll(expand(source));

        // if the source points to the contents of something,
        // consider each field as an independent source
        } else if (base.contents) {
            if (value instanceof Value.Compound)
                for (Value field : ((Value.Compound)(value)).fields.values())
                    sources.addAll(expand(Source.create(field)));

            else if (var != null)
                for (Variable field : var.fields.values())
                    sources.addAll(expand(Source.create(field)));

            else
                sources.add(base);

        // otherwise, the source is just by itself
        } else {
            sources.add(base);
        }

        return sources;
    }

    public static List<Source> expand(List<Source> sources)
    {
        List<Source> all = new ArrayList<>();

        // combine the expansion of every source in the list
        for (Source source : sources)
            all.addAll(expand(source));

        return all;
    }

    public static List<Source> find(Variable var)
    {
        return find(
            null,
            new ArrayDeque<Variable>(),
            var,
            Collections.newSetFromMap(new IdentityHashMap<Variable, Boolean>())
        );
    }

    public static List<Source> find(Variable var, Statement stmt)
    {
        return find(
            stmt,
            new ArrayDeque<Variable>(),
            var,
            Collections.newSetFromMap(new IdentityHashMap<Variable, Boolean>())
        );
    }

    private static List<Source> find(
        Statement stmt,
        Deque<Variable> path,
        Variable var,
        Set<Variable> visited
    )
    {
        // prevent infinite recursion by looking up each variable only once
        if (visited.contains(var)) return Collections.emptyList();
        visited.add(var);

        // no statement means no fixed sources; use the variable's own sources
        // as base sources for the search
        List<Source> base;
        if (stmt == null)
            base = expand(var.sources);

        // in statement context, use the fixed source set in the statement
        else if (stmt.vars.containsKey(var))
            base = Collections.singletonList(stmt.vars.get(var));

        // otherwise, the variable has no sources
        else
            base = Collections.emptyList();

        // if sources are available; follow each source to try and
        // find value sources at the end of the path
        List<Source> found = new ArrayList<>();
        for (Source source : base) {
            // directly follow variable sources
            if (source instanceof Source.Variable)
                found.addAll(find(
                    source.stmt,
                    new ArrayDeque<>(path),
                    ((Source.Variable)(source)).var,
                    visited
                ));

            // and add value sources to the output list
            // (if at the end of the path)
            else if (path.isEmpty())
                found.add(source);
        }

        // if the current variable has the right field, try finding sources down
        // the field tree, following the path (and removing the saved key placed
        // there earlier)
        Variable field = path.isEmpty() ? null : var.get(path.peekFirst());
        if (field != null) {
            Deque<Variable> copy = new ArrayDeque<>(path);
            copy.removeFirst();

            found.addAll(find(stmt, copy, field, visited));
        }

        // if a parent is available, try finding sources up the field tree, saving
        // the field's key on the path (as moving down will be required later)
        Variable key = (var.parent == null) ? null : var.parent.getKey(var);
        if (key != null) {
            path.addFirst(key);

            found.addAll(find(stmt, path, var.parent, visited));
        }

        return found;
    }
}
