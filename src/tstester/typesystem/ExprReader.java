package tstester.typesystem;

import java.util.*;
import tstester.grammar.*;
import tstester.sablecc.rgrammar.analysis.*;
import tstester.sablecc.rgrammar.node.*;

class ExprReader extends AnalysisAdapter
{
    public final Grammar grammar;
    public final Map<String, Variable> vars;
    public final List<String> prods;
    public final Variable env;

    public Variable out;

    public ExprReader(Grammar grammar, Map<String, Variable> vars, List<String> prods)
    {
        this.grammar = grammar;
        this.vars = vars;
        this.prods = prods;
        this.out = null;

        if ((this.env = vars.get("env")) == null)
            throw new RuntimeException(
                "Missing global environment reference ('env' variable)"
            );
    }

    public void caseAIdentifierExpr(AIdentifierExpr node)
    {
        // an identifier: a direct variable or production reference
        String name = node.getIdentifier().getText();

        // if the identifier matches an already defined variable or type,
        // just return the variable/type as-is
        if (
            (out = vars.get(name)) != null ||
            (out = env.get("types", name)) != null
        )
            return;

        // otherwise, it needs to correspond to a production
        int p = prods.indexOf(name);
        if (p == -1)
            throw new RuntimeException(
                "Unknown identifier '" + name + "'"
            );

        // does the production already have a variable?
        String ix = "#" + p;
        if ((out = vars.get(ix)) != null)
            return;

        // is the production duplicated?
        if (Collections.frequency(prods, name) > 1)
            throw new RuntimeException(
                "Ambiguous production reference '" + name + "'"
            );

        // fetch the corresponding symbol, then create the variable
        Symbol sym = grammar.getSymbol(name);
        if (sym == null)
            throw new RuntimeException(
                "Unknown symbol '" + name + "'"
            );

        vars.put(ix, (out = new Variable(ix, new Value.Symbol(sym))));
    }

    public void caseALiteralExpr(ALiteralExpr node)
    {
        // a single value: a variable with just one source
        String raw = node.getIdentifier().getText();
        out = new Variable(raw, new Value.Basic(raw));
    }

    public void caseADirectAccessExpr(ADirectAccessExpr node)
    {
        // single values/literals cannot be indexed
        if (node.getMap() instanceof ALiteralExpr)
            throw new RuntimeException(
                "Cannot index a literal value"
            );

        node.getMap().apply(this);
        Variable map = out;

        // direct access: field access with constant key
        String key = node.getKey().getText();

        Variable var = map.get(key);
        if (var == null)
            map.put(key, (var = new Variable(key)));

        out = var;
    }

    public void caseAIndirectAccessExpr(AIndirectAccessExpr node)
    {
        // single values/literals cannot be indexed
        if (node.getMap() instanceof ALiteralExpr)
            throw new RuntimeException(
                "Cannot index a literal value"
            );

        node.getMap().apply(this);
        Variable map = out;

        node.getKey().apply(this);
        Variable key = out;

        // a literal key makes this equivalent to a direct access
        if (node.getKey() instanceof ALiteralExpr) {
            Source.Value source = (Source.Value)(key.sources.get(0));
            String directKey = ((Value.Basic)(source.value)).value;

            Variable var = map.get(directKey);
            if (var == null)
                map.put(directKey, (var = new Variable(directKey)));

            out = var;

        // otherwise, use the key expression to index the map (indirect access)
        } else {
            Variable var = map.get(key);
            if (var == null)
                map.put(key, (var = new Variable()));

            out = var;
        }
    }

    public void caseAProdIndexExpr(AProdIndexExpr node)
    {
        // a production reference (by index)
        int p = Integer.parseInt(node.getNumber().getText());
        if (p >= prods.size())
            throw new RuntimeException(
                "Production index (" + p + ") is out of bounds"
            );

        // make sure that an actual production is referenced, not a slot
        if (prods.get(p) == null)
            throw new RuntimeException(
                "Invalid production index (" + p + "); found a slot instead"
            );

        // if the production exists, then the same handling as for
        // an AIdentifierExpr applies
        String ix = "#" + p;
        if ((out = vars.get(ix)) != null)
            return;

        // fetch the corresponding symbol, then create the variable
        String name = prods.get(p);
        Symbol sym = grammar.getSymbol(name);
        if (sym == null)
            throw new RuntimeException(
                "Unknown symbol '" + name + "'"
            );

        vars.put(ix, (out = new Variable(ix, new Value.Symbol(sym))));
    }

    public void caseAFunctionCallExpr(AFunctionCallExpr node)
    {
        throw new RuntimeException(
            "Not implemented (function call expressions)"
        ); // TODO
    }
}
