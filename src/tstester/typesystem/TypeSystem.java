package tstester.typesystem;

import java.util.*;
import tstester.grammar.*;
import tstester.grammar.Node;
import tstester.sablecc.rgrammar.analysis.*;
import tstester.sablecc.rgrammar.node.*;

public class TypeSystem
{
    public final Grammar grammar;
    public final Variable environment;
    public final List<Rule> rules;

    public final Map<Value.Type, List<Rule>> valueSources;
    public final Map<Value.Type, List<Rule>> literalSources;

    private final List<Pair<Variable, Rule>> rawValueSources;
    private final List<Pair<Variable, Rule>> rawLiteralSources;

    // TODO 'not implemented' tags (options, context, scope, conditions, functions)
    // TODO testing

    public interface SourceSelector
    {
        Source selectVariableSource(Statement stmt, Variable var, List<Source> sources);

        Rule selectTypedValueSource(Statement stmt, Value.Type type, List<Rule> sources);

        Rule selectTypeLiteralSource(Statement stmt, Value.Type type, List<Rule> sources);
    }

    // TODO
    public static class ShortSourceSelector implements SourceSelector
    {
        public Source selectVariableSource(Statement stmt, Variable var, List<Source> sources)
        {
            return null;
        }

        public Rule selectTypedValueSource(Statement stmt, Value.Type type, List<Rule> sources)
        {
            return null;
        }

        public Rule selectTypeLiteralSource(Statement stmt, Value.Type type, List<Rule> sources)
        {
            return null;
        }
    }

    public TypeSystem(Grammar grammar)
    {
        this.grammar = grammar;
        this.environment = new Variable("env");
        this.rules = new ArrayList<>();

        this.valueSources = new IdentityHashMap<>();
        this.literalSources = new IdentityHashMap<>();

        this.rawValueSources = new ArrayList<>();
        this.rawLiteralSources = new ArrayList<>();
    }

    public static TypeSystem fromSableCCTypeSystem(ATypeSystemSpec spec, Grammar grammar)
    {
        TypeSystem t = new TypeSystem(grammar);

        // set up options
        for (POption option : spec.getOption())
            t.readOption((AOption)(option));

        // create env.types (if it doesn't exist, which is likely at this stage)
        Variable types = t.environment.get("types");
        if (types == null)
            t.environment.put("types", (types = new Variable("types")));

        // add basic types into env.types
        for (TIdentifier id : spec.getBasicTypes()) {
            // quick duplicate check
            String name = id.getText();
            if (types.get(name) != null)
                throw new RuntimeException(
                    "Duplicate basic type '" + name + "'"
                );

            // basic types have a corresponding pre-made value
            types.put(name, new Variable(name, new Value.Type(name, true)));
        }

        // add rules (rules) into TypeSystem.rules
        for (PRule rule : spec.getRule())
            t.readRule(rule);

        // resolve typed value sources and type literal sources
        t.resolveTypeSources(t.rawValueSources, t.valueSources);
        t.resolveTypeSources(t.rawLiteralSources, t.literalSources);

        return t;
    }

    public List<Subtree> generate(Rule rule)
    {
        return generate(rule, new ShortSourceSelector());
    }

    public List<Subtree> generate(Rule rule, SourceSelector selector)
    {
        // create the matching rule template (Statement)
        Statement stmt = createTemplate(rule, selector);
        if (stmt == null) return Collections.emptyList();

        // bind the input values and generate the dependencies & final subtrees
        bindValues(stmt, selector);
        generate(stmt);

        // add up both dependencies and final to make the final subtree list
        List<Subtree> subtrees = new ArrayList<>(stmt.dependencies);
        subtrees.add(stmt.generated);

        return subtrees;
    }

    public List<Subtree> generate(final Statement stmt)
    {
        // generate the statement's dependencies
        generateDependencies(stmt);

        // and the node list, starting with the fixed tokens
        List<Node> nodes = new ArrayList<>();
        for (Symbol sym : stmt.rule.tokens)
            nodes.add(sym != null ? grammar.shortGenerate(sym) : null);

        // and ending with the slots
        for (Rule.Slot slot : stmt.rule.slots) {
            Node node;

            // if the slot is a symbol-type slot, no need for an inset node;
            // just generate the matching node from the symbol in the slot's value
            if (slot.type == Rule.Slot.Type.SYMBOL) {
                // fetch the slot's bound value
                Value value = stmt.values.get(slot.var);
                if (value == null)
                    throw new RuntimeException(
                        "Missing value for slot " + slot
                    );

                // fetch the value's symbol
                if (!(value instanceof Value.Symbol))
                    throw new RuntimeException(
                        "Invalid value type, expected 'Value.Symbol', got " +
                        "'" + value.getClass().getName() + "'"
                    );

                Value.Symbol sym = (Value.Symbol)(value);

                // generate the corresponding node (assuming it doesn't already exist)
                if (sym.node == null) sym.node = grammar.shortGenerate(sym.sym);
                node = sym.node.clone();

            // otherwise, use the slot's inset statement
            // as an inset node (via its generated subtree)
            } else {
                Statement inset = stmt.insets.get(slot);
                if (inset == null)
                    throw new RuntimeException(
                        "Missing inset node for slot " + slot.ix
                    );

                node = inset.generated.root;
            }

            // once the slot's node is ready, place it at the appropriate
            // location in the node list
            nodes.set(slot.ix, node);
        }

        // at this point the node list is complete; locate it in the grammar
        // and filter the results to match the rule's hint (if applicable)
        List<Subtree> located = grammar.locateChain(nodes);
        if (stmt.rule.sym != null)
            located = filter(located, new Predicate<Subtree>() {
                public boolean apply(Subtree subtree)
                {
                    return subtree.root.sym == stmt.rule.sym;
                }
            });

        // cannot locate? error out
        if (located.isEmpty())
            throw new RuntimeException(
                "Cannot locate rule in grammar"
            );

        // once the node list has been correctly located,
        // pick the shallowest subtree as this statement's subtree
        Grammar.depthSort(located);
        stmt.generated = located.get(0);

        // and compose the output list from the dependencies and the
        // statement's own subtree
        List<Subtree> subtrees = new ArrayList<>(stmt.dependencies);
        subtrees.add(stmt.generated);

        return subtrees;
    }

    public Statement createTemplate(Rule rule, SourceSelector selector)
    {
        return createTemplate(rule, null, selector);
    }

    public Statement createTemplate(Rule rule, Statement parent, SourceSelector selector)
    {
        // as statements are rule 'instances', no rule means no statement
        if (rule == null) return null;
        Statement stmt = new Statement(rule, parent);

        // from each slot variable, find a parent variable with at least one source value
        List<Variable> sourced = new ArrayList<>();
        for (Rule.Slot slot : rule.slots) {
            // find the sourced parent
            Variable var = slot.var;
            while (var != null && var.sources.isEmpty())
                var = var.parent;

            // error out if none can be found
            if (var == null)
                throw new RuntimeException(
                    "Cannot find a source value for slot " + slot
                );

            // add it to the sourced variables list
            sourced.add(var);
        }

        // for all of the sourced variables found above,
        // pick a source and create a template from that source's statement
        for (Variable var : sourced) {
            // skip duplicate variables
            if (stmt.vars.containsKey(var))
                continue;

            // select a source from the variable's source list
            List<Source> all = Sources.expand(var.sources);
            Source source = (all.size() > 1)
                ? selector.selectVariableSource(stmt, var, all).clone()
                : all.get(0).clone();

            // add the variable and its source to the statement,
            // and create a new template if the source is another variable
            // (from possibly a different statement)
            stmt.vars.put(var, source);
            if (source instanceof Source.Variable)
                source.stmt = createTemplate(
                    ((Source.Variable)(source)).var.rule,
                    stmt,
                    selector
                );
        }

        return stmt;
    }

    public void bindValues(Statement stmt, SourceSelector selector)
    {
        // find the final source value for every slot and bind them to it
        for (Rule.Slot slot : stmt.rule.slots) {
            // find the final source, which corresponds to the value the slot
            // variable must take according to the current source selection
            List<Source> sources = Sources.find(slot.var, stmt);
            Source source = sources.isEmpty() ? null : sources.get(0);

            if (!(source instanceof Source.Value))
                throw new RuntimeException(
                    "Cannot find a valid source for slot " + slot
                );

            // and bind the slot variable to that value
            bindValue(stmt, slot.var, ((Source.Value)(source)).value);
        }

        // recursively bind dependencies to make sure every variable is bound
        // (not just slot variables)
        for (Source source : stmt.vars.values())
            if (source.stmt != null)
                bindValues(source.stmt, selector);

        // create the required inset statements for type slots
        for (Rule.Slot slot : stmt.rule.slots)
            if (slot.type != Rule.Slot.Type.SYMBOL)
                stmt.insets.put(slot, createInset(stmt, slot, selector));
    }

    public boolean bindValue(Statement stmt, Variable var, Value value)
    {
        // set up the original binding in the given statement,
        // bailing out if there is already another binding in place
        if (stmt.values.containsKey(var)) return false;
        stmt.values.put(var, value);

        // given a compound value and variable,
        // follow down and bind every matching field
        if (!var.fields.isEmpty() && (value instanceof Value.Compound)) {
            Value.Compound compound = (Value.Compound)(value);
            for (Map.Entry<Variable, Value> entry : compound.fields.entrySet()) {
                Variable field = var.fields.get(entry.getKey());
                if (field != null) bindValue(stmt, field, entry.getValue());
            }
        }

        // if the variable has an unbound parent,
        // create a new compound value and bind it
        if (var.parent != null && !stmt.values.containsKey(var.parent)) {
            Value.Compound compound = new Value.Compound();
            compound.fields.put(var, value);

            bindValue(stmt, var.parent, compound);
        }

        // finally, if the variable is sourced,
        // follow the source link and bind the source in its own statement
        Source source = stmt.vars.get(var);
        if ((source instanceof Source.Variable) && source.stmt != null)
            bindValue(source.stmt, ((Source.Variable)(source)).var, value);

        return true;
    }

    private void readOption(AOption option)
    {
        throw new RuntimeException(
            "Not implemented (options)"
        ); // TODO
    }

    private void readRule(PRule prule)
    {
        // create a blank rule to be filled below
        final Rule rule = new Rule();
        rule.vars.put("env", environment);

        // fill the rule with the contents of the SableCC rule
        prule.apply(new AnalysisAdapter() {
            public void caseATypeRuleRule(ATypeRuleRule node)
            {
                // generic filling procedure
                fillRule(
                    rule,
                    node.getContext(),
                    node.getToken(),
                    node.getQuantifier(),
                    node.getModifier()
                );

                // set up the rule symbol hint, if available
                TIdentifier hint = ((AResult)(node.getResult())).getToken();
                if (hint != null && (rule.sym = grammar.getSymbol(hint.getText())) == null)
                    throw new RuntimeException(
                        "Unknown symbol '" + hint + "'"
                    );

                // if there is a result type, add the rule to the raw value sources
                // list to be resolved after all rules have been read
                PExpr type = ((AResult)(node.getResult())).getType();
                if (type != null)
                    rawValueSources.add(new Pair<>(
                        readExpr(type, rule.vars),
                        rule
                    ));
            }

            public void caseATypeLiteralRule(ATypeLiteralRule node)
            {
                // generic filling procedure
                fillRule(
                    rule,
                    node.getContext(),
                    node.getToken(),
                    node.getQuantifier(),
                    node.getModifier()
                );

                // add the rule to the raw type literal sources
                // list to be resolved after all rules have been read
                rawLiteralSources.add(new Pair<>(
                    readExpr(node.getType(), rule.vars),
                    rule
                ));
            }
        });

        // remove the temporary inclusion of env,
        // and bind the remaining variables to the rule
        rule.vars.remove("env");
        for (Variable var : rule.vars.values())
            var.rule = rule;

        // finally, add the rule to the type system
        rules.add(rule);
    }

    private void fillRule(
        Rule rule,
        PContext context,
        List<PToken> tokens,
        List<PQuantifier> quantifiers,
        List<PModifier> modifiers
    )
    {
        // make sure the rule has a reference to the global environment
        // (for filling purposes only)
        if (!rule.vars.containsKey("env"))
            throw new RuntimeException(
                "Missing global environment reference ('env' variable)"
            );

        // set up the rule's context variables
        if (context != null)
            readContext((AContext)(context), rule);

        // set up the variable sources by reading the existential quantifier list
        for (PQuantifier quantifier : quantifiers)
            readQuantifier((AQuantifier)(quantifier), rule);

        // set up tokens and slots by reading the rule's token list
        rule.tokens = new Symbol[tokens.size()];
        for (int i = 0; i < tokens.size(); ++i)
            readToken(tokens.get(i), i, rule);

        // set up external bindings by reading the modifier list
        for (PModifier modifier : modifiers)
            readModifier(modifier, rule);
    }

    private void readContext(AContext context, Rule rule)
    {
        throw new RuntimeException(
            "Not implemented (rule context)"
        ); // TODO
    }

    private void readQuantifier(AQuantifier quantifier, Rule rule)
    {
        // TODO quantifier conditions
        if (quantifier.getCondition() != null)
            throw new RuntimeException(
                "Not implemented (ext. quantifier conditions)"
            );

        // for each in-clause, add the matching variable (binding its source)
        for (PInClause clause : quantifier.getInClause()) {
            String name = ((AInClause)(clause)).getVar().getText();
            Variable var = new Variable(
                name,
                Collections.singletonList(Source.create(
                    readExpr(((AInClause)(clause)).getSet(), rule.vars),
                    true
                ))
            );

            var.rule = rule;
            rule.vars.put(name, var);
        }
    }

    private void readToken(PToken token, int ix, Rule rule)
    {
        // simple case; the token is one of the grammar's symbols
        if (token instanceof AProductionToken) {
            String name = ((AProductionToken)(token)).getIdentifier().getText();

            // fetch and validate the symbol
            Symbol sym = grammar.getSymbol(name);
            if (sym == null)
                throw new RuntimeException("Unknown grammar symbol '" + name + "'");

            rule.tokens[ix] = sym;

        // complex case; the token is an expression
        } else {
            // figure out which slot type to use
            Rule.Slot.Type type;
            PExpr expr;

            if (token instanceof AProdRefToken) {
                expr = ((AProdRefToken)(token)).getExpr();
                type = Rule.Slot.Type.SYMBOL;

            } else if (token instanceof ATypedValueToken) {
                expr = ((ATypedValueToken)(token)).getExpr();
                type = Rule.Slot.Type.TYPED_VALUE;

            } else {
                expr = ((ATypeLiteralToken)(token)).getExpr();
                type = Rule.Slot.Type.TYPE_LITERAL;
            }

            // read out the variable's expression and create the corresponding slot
            rule.slots.add(new Rule.Slot(ix, type, readExpr(expr, rule.vars)));
            rule.tokens[ix] = null;
        }
    }

    private void readModifier(PModifier modifier, final Rule rule)
    {
        modifier.apply(new AnalysisAdapter() {
            public void caseAAddModifierModifier(AAddModifierModifier node)
            {
                readModifier(node, rule);
            }

            public void caseAScopeModifierModifier(AScopeModifierModifier node)
            {
                readModifier(node, rule);
            }

            public void caseAContextModifierModifier(AContextModifierModifier node)
            {
                readModifier(node, rule);
            }
        });
    }

    private void readModifier(AAddModifierModifier modifier, Rule rule)
    {
        // gather the list of productions, leaving holes for slots
        List<String> prods = new ArrayList<>();
        for (Symbol sym : rule.tokens)
            prods.add(sym != null ? sym.name : null);

        // fetch/build the involved variables
        Variable map = readExpr(modifier.getMap(), rule.vars, prods);
        Variable value = readExpr(modifier.getKey(), rule.vars, prods);

        Variable key = new Variable(value);
        key.rule = rule;

        for (PProperty prop : modifier.getProperty())
            value.put(
                ((AProperty)(prop)).getKey().getText(),
                readExpr(((AProperty)(prop)).getValue(), rule.vars, prods)
            );

        // and add the pair to the map with the value as one of the possible sources
        // for the key, using a special junction variable to hold the sources
        Variable junc = map.get(key);
        if (junc == null) map.put(key, (junc = Variable.junction()));

        junc.sources.add(Source.create(value));

        // if any production was used in the modifier,
        // its token needs to be converted to a slot
        for (int i = 0; i < prods.size(); ++i) {
            // a hole means there's already a slot
            if (prods.get(i) == null) continue;

            // fetch the variable created from the production
            // when reading modifier expressions
            Variable var = rule.vars.get("#" + i);
            if (var == null) continue;

            // and convert the token to a slot
            rule.slots.add(new Rule.Slot(i, Rule.Slot.Type.SYMBOL, var));
            rule.tokens[i] = null;
        }
    }

    private void readModifier(AScopeModifierModifier modifier, Rule rule)
    {
        throw new RuntimeException(
            "Not implemented (scope modifier)"
        ); // TODO
    }

    private void readModifier(AContextModifierModifier modifier, Rule rule)
    {
        throw new RuntimeException(
            "Not implemented (context modifier)"
        ); // TODO
    }

    private Variable readExpr(PExpr expr, Map<String, Variable> vars)
    {
        return readExpr(expr, vars, new ArrayList<String>());
    }

    private Variable readExpr(PExpr expr, Map<String, Variable> vars, List<String> prods)
    {
        // apply the expression reader and return the resulting variable
        ExprReader reader = new ExprReader(grammar, vars, prods);
        expr.apply(reader);

        return reader.out;
    }

    private void resolveTypeSources(
        List<Pair<Variable, Rule>> raw,
        Map<Value.Type, List<Rule>> map
    )
    {
        map.clear();

        // resolve each type source, building up the map
        for (Pair<Variable, Rule> pair : raw) {
            // iterate through the list of all final sources for the raw var
            for (Source source : Sources.find(pair.left)) {
                // to be a valid type source, the source must be a Source.Value
                if (!(source instanceof Source.Value)) continue;
                Source.Value value = (Source.Value)(source);

                // and the value a Value.Type (as it must represent a type)
                if (!(value.value instanceof Value.Type)) continue;
                Value.Type type = (Value.Type)(value.value);

                // once the type value has been fetched and validated,
                // add the corresponding rule to the map
                List<Rule> sources = map.get(type);
                if (sources == null) map.put(type, (sources = new ArrayList<>()));

                sources.add(pair.right);
            }
        }
    }

    private void generateDependencies(Statement base)
    {
        Map<Statement, Boolean> generated = new IdentityHashMap<>();
        base.dependencies = new ArrayList<>();

        // generate inset slot statements first, as the inset
        // subtree itself isn't considered a dependency
        for (Statement stmt : base.insets.values()) {
            // no statement or already generated? nothing to do
            if (stmt == null || generated.containsKey(stmt))
                continue;

            // generate the inset statement and mark it as such
            generate(stmt);
            generated.put(stmt, true);

            // add the inset statement's own dependencies to the base statement
            base.dependencies.addAll(stmt.dependencies);
        }

        // generate regular variable dependencies (same as above)
        for (Source source : base.vars.values()) {
            // no statement or already generated? nothing to do
            if (source.stmt == null || generated.containsKey(source.stmt))
                continue;

            // generate the dependency and mark it as such
            generate(source.stmt);
            generated.put(source.stmt, false);

            // add the dependency itself and its own dependencies to the base statement
            base.dependencies.addAll(source.stmt.dependencies);
            base.dependencies.add(source.stmt.generated);
        }
    }

    private Statement createInset(Statement stmt, Rule.Slot slot, SourceSelector selector)
    {
        // fetch the type value bound to this slot, required to know which
        // type to pick a inset typed value/literal for
        Value value = stmt.values.get(slot.var);
        if (!(value instanceof Value.Type))
            throw new RuntimeException(
                "Invalid (or missing) type value for slot " + slot
            );

        Value.Type type = (Value.Type)(value);
        Rule rule;

        // pick a suitable rule for a typed value slot
        if (slot.type == Rule.Slot.Type.TYPED_VALUE) {
            // fetch the source list and make sure it contains at least one source
            List<Rule> sources = valueSources.get(type);
            if (sources == null || sources.isEmpty())
                throw new RuntimeException(
                    "Missing typed value rule for type " + type
                );

            // select the source rule, using the selector if required
            rule = (sources.size() > 1)
                ? selector.selectTypedValueSource(stmt, type, sources)
                : sources.get(0);

        // or for a type literal slot (same as above with different sources)
        } else {
            // fetch the source list and make sure it contains at least one source
            List<Rule> sources = literalSources.get(type);
            if (sources == null || sources.isEmpty())
                throw new RuntimeException(
                    "Missing literal rule for type " + type
                );

            // select the source rule, using the selector if required
            rule = (sources.size() > 1)
                ? selector.selectTypeLiteralSource(stmt, type, sources)
                : sources.get(0);
        }

        // with the rule selected, create & bind a statement (template) for it
        // to form the inset statement for this slot
        Statement inset = createTemplate(rule, stmt, selector);
        bindValues(inset, selector);

        return inset;
    }

    private interface Predicate<T>
    {
        boolean apply(T value);
    }

    private static class Pair<L, R>
    {
        public L left;
        public R right;

        public Pair(L left, R right)
        {
            this.left = left;
            this.right = right;
        }

        public Pair(Pair<L, R> other)
        {
            this(other.left, other.right);
        }

        public String toString()
        {
            return left.toString() + ":" + right.toString();
        }

        public int hashCode()
        {
            return (
                7 * left.hashCode() +
                13 * right.hashCode()
            );
        }

        public boolean equals(Object other)
        {
            if (other == null || !getClass().equals(other.getClass()))
                return false;

            Pair<?, ?> that = (Pair<?, ?>)(other);
            return (
                left.equals(that.left) &&
                right.equals(that.right)
            );
        }
    }

    private static <T> List<T> filter(List<T> list, Predicate<T> predicate)
    {
        List<T> result = new ArrayList<>();

        for (T item : list)
            if (predicate.apply(item))
                result.add(item);

        return result;
    }
}
