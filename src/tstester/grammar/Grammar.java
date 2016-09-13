package tstester.grammar;

import java.util.*;
import org.sablecc.sablecc.node.*;

public class Grammar
{
    public Production root;
    public final Map<String, Production> productions;
    public final Map<String, Terminal> terminals;
    public final Map<String, Terminal> ignored;
    public final List<Symbol> blacklist;

    // TODO documentation
    // TODO token/prod blacklist feature to avoid generating identifiers

    private static class Ancestor extends Path
    {
        public Path leaf;
        public int base;

        public Ancestor()
        {
            this(new Path(), 0);
        }

        public Ancestor(Path leaf, int base)
        {
            super();

            this.leaf = leaf;
            this.base = base;
        }

        public Ancestor(List<Edge> edges)
        {
            this(edges, new Path(), 0);
        }

        public Ancestor(List<Edge> edges, Path leaf, int base)
        {
            super(edges);

            this.leaf = leaf;
            this.base = base;
        }

        public Ancestor(Ancestor other)
        {
            super(other);

            this.leaf = other.leaf;
            this.base = other.base;
        }

        public String toString()
        {
            return super.toString() + "(" + base + ")";
        }

        public Ancestor clone()
        {
            return new Ancestor(this);
        }
    }

    public Grammar()
    {
        this.root = null;
        this.productions = new HashMap<>();
        this.terminals = new HashMap<>();
        this.ignored = new HashMap<>();
        this.blacklist = new ArrayList<>();
    }

    public static Grammar fromSableCCGrammar(AGrammar grammar)
    {
        Map<String, ARegExp> help = new HashMap<>();
        Grammar g = new Grammar();

        // incrementally build a map of helper regexes to use when generating
        // non-SableCC regexes later on
        List<PHelperDef> helpers = ((AHelpers)(grammar.getHelpers())).getHelperDefs();
        for (PHelperDef helper : helpers)
            help.put(
                ((AHelperDef)(helper)).getId().getText(),
                (ARegExp)(((AHelperDef)(helper)).getRegExp())
            );

        // add tokens (Terminals) into Grammar.terminals
        List<PTokenDef> tokens = ((ATokens)(grammar.getTokens())).getTokenDefs();
        for (PTokenDef token : tokens) {
            String name = ((ATokenDef)(token)).getId().getText();
            ARegExp regexp = (ARegExp)(((ATokenDef)(token)).getRegExp());

            // is the token's value a simple string?
            String value = Regexp.asSimpleString(regexp);
            if (value != null) {
                g.terminals.put(name, new Terminal(name, value, false));

            // or a full regexp?
            } else {
                value = Regexp.fromSableCCRegexp(regexp, help);
                g.terminals.put(name, new Terminal(name, value, true));
            }
        }

        // move ignored tokens from the above to the ignored map instead
        List<TId> ignored = ((AIgnTokens)(grammar.getIgnTokens())).getListId();
        for (TId id : ignored)
            g.ignored.put(id.getText(), g.terminals.remove(id.getText()));

        // add blank productions (Production) into Grammar.productions
        List<PProd> productions = ((AProductions)(grammar.getProductions())).getProds();
        for (PProd production : productions) {
            String name = ((AProd)(production)).getId().getText();
            g.productions.put(name, new Production(name));
        }

        // build each production's symbols
        for (PProd production : productions) {
            Production base = g.productions.get(((AProd)(production)).getId().getText());
            List<Edge[]> symbols = new ArrayList<>();

            // alternative by alternative
            List<PAlt> alternatives = ((AProd)(production)).getAlts();
            for (int i = 0; i < alternatives.size(); ++i) {
                List<Edge> edges = new ArrayList<>();

                // symbol by symbol, each represented as an Edge
                List<PElem> elems = ((AAlt)(alternatives.get(i))).getElems();
                for (int j = 0; j < elems.size(); ++j) {
                    AElem elem = (AElem)(elems.get(j));

                    PSpecifier spec = elem.getSpecifier();
                    String ref = elem.getId().getText();
                    PUnOp op = elem.getUnOp();

                    // fetch a reference to what the symbol represents
                    Symbol sym;
                    if (spec instanceof AProductionSpecifier)
                        sym = g.productions.get(ref);
                    else if (spec instanceof ATokenSpecifier)
                        sym = g.terminals.get(ref);
                    else
                        sym = g.getSymbol(ref);

                    // and create an edge binding the production and the symbol
                    edges.add(new Edge(
                        base, sym, i, j,
                        (op instanceof AQMarkUnOp || op instanceof AStarUnOp),
                        (op instanceof APlusUnOp || op instanceof AStarUnOp)
                    ));
                }

                symbols.add(edges.toArray(new Edge[0]));
            }

            base.symbols = symbols.toArray(new Edge[0][]);
        }

        // tally up all the edges according to their destination
        Map<Symbol, List<Edge>> parents = new HashMap<>();
        for (Production production : g.productions.values()) {
            for (int i = 0; i < production.symbols.length; ++i) {
                for (int j = 0; j < production.symbols[i].length; ++j) {
                    Edge edge = production.symbols[i][j];

                    List<Edge> edges = parents.get(edge.dst);
                    if (edges == null) parents.put(edge.dst, (edges = new ArrayList<>()));

                    edges.add(edge);
                }
            }
        }

        // and use that tally to set up parent edges
        for (Map.Entry<Symbol, List<Edge>> entry : parents.entrySet())
            entry.getKey().parents = entry.getValue().toArray(new Edge[0]);

        // finally, assign the first production as the root node
        if (!productions.isEmpty())
            g.root = g.productions.get(((AProd)(productions.get(0))).getId().getText());

        return g;
    }

    public static void depthSort(List<Subtree> trees)
    {
        final Map<Subtree, Integer> depths = new IdentityHashMap<>();

        // precompute the max depth of all subtrees
        for (Subtree tree : trees)
            depths.put(tree, tree.depth());

        // sort by the max depth computed above
        Collections.sort(trees, new Comparator<Subtree>() {
            public int compare(Subtree a, Subtree b)
            {
                return depths.get(a) - depths.get(b);
            }
        });
    }

    public Symbol getSymbol(String name)
    {
        Symbol sym = productions.get(name);

        return sym == null ? terminals.get(name) : sym;
    }

    public List<Path> shallowFind(Symbol src, Symbol dst)
    {
        return shallowFind(src, dst, new HashSet<Symbol>(), new HashMap<Symbol, List<Path>>());
    }

    public List<Subtree> locateChain(List<Node> nodes)
    {
        return locateChain(nodes, new HashSet<Symbol>());
    }

    public List<Subtree> locateChain(List<Node> nodes, Set<Symbol> skip)
    {
        List<Subtree> empty = new ArrayList<>();
        if (nodes.isEmpty()) return empty;

        // start off with only one candidate; a subtree with only the first node
        List<Subtree> candidates = new ArrayList<>();
        candidates.add(new Subtree(nodes.get(0)));

        // traverse the tree to lookup every node after the first
        for (int i = 1; i < nodes.size(); ++i) {
            List<Subtree> found = new ArrayList<>();

            // for each candidate, find all valid subtrees
            // containing the next node on the right side
            for (Subtree candidate : candidates)
                found.addAll(findRightOf(
                    candidate,
                    nodes.get(i),
                    nodes.get(i - 1),
                    skip
                ));

            // and repeat the process, starting with the found
            // subtrees for the next node in the chain
            candidates = found;
            if (candidates.isEmpty()) return empty;
        }

        return candidates;
    }

    public List<Subtree> locateDisjoint(List<Node> nodes)
    {
        // make a set of all distinct symbols in nodes
        Set<Symbol> alive = new HashSet<>();
        for (Node node : nodes) alive.add(node.sym);

        // use that set to compute the 'dead' set (the set of symbols that
        // cannot contain the symbols in nodes), then use that set with
        // locateChain to locate the disjoint nodes
        return locateChain(nodes, findDeadSymbols(alive));
    }

    public int shortFill(Node node)
    {
        Map<Symbol, Node> known = new HashMap<>();

        // it makes no sense to try and fill a token
        if (node instanceof Token)
            return node.length = 1;

        // if the node about to be filled already contains child nodes,
        // recursively fill them, generating new nodes for the empty slots
        if (node.size() != 0) {
            // fill out existing slots recursively
            for (Node sub : node.edges.values())
                shortFill(sub);

            // and generate new (short) nodes for the missing slots,
            // using the first child's alternative
            Edge first = node.edges.firstKey();
            for (Edge edge : first.src.symbols[first.alt])
                if (node.get(edge) == null)
                    node.put(edge, shortGenerate(edge.dst, known));

        // otherwise, generate the shortest node and move over the child nodes
        // (as the shortest node directly corresponds)
        } else {
            Node shortest = shortGenerate(node.sym, known);

            for (Map.Entry<Edge, Node> entry : shortest)
                node.put(entry.getKey(), entry.getValue());
        }

        // finally, compute the newly filled node's length
        node.length = 0;
        for (Node sub : node.edges.values()) {
            if (sub.length == Integer.MAX_VALUE)
                node.length = Integer.MAX_VALUE;

            else if (node.length != Integer.MAX_VALUE)
                node.length += sub.length;
        }

        return node.length;
    }

    public Node shortGenerate(Symbol sym)
    {
        return shortGenerate(sym, new HashMap<Symbol, Node>());
    }

    private List<Path> shallowFind(Symbol src, Symbol dst, Set<Symbol> open, Map<Symbol, List<Path>> cache)
    {
        List<Path> paths = new ArrayList<>();

        // check if the source already matches the destination
        if (src == dst)
            return single(new Path());

        // if the node is terminal (no children) or is already
        // being visited, bail out immediately
        if (src instanceof Terminal || open.contains(src))
            return paths;

        // apply some memoization to avoid re-traversing
        if (cache.containsKey(src)) {
            for (Path path : cache.get(src))
                paths.add(path.clone());

            return paths;
        }

        // mark the node as being under visit
        Production prod = (Production)(src);
        open.add(src);

        // go through every edge and recurse
        for (int i = 0; i < prod.symbols.length; ++i) {
            for (int j = 0; j < prod.symbols[i].length; ++j) {
                Edge next = prod.symbols[i][j];

                // if a path is found, add the current edge to the path(s) found
                for (Path found : shallowFind(next.dst, dst, open, cache)) {
                    found.unshift(next);
                    paths.add(found);
                }
            }
        }

        // finally, unmark and record findings in the memoization cache
        open.remove(src);
        cache.put(src, paths);

        return paths;
    }

    private List<Subtree> findRightOf(Subtree base, Node node, Node prev, Set<Symbol> skip)
    {
        List<Subtree> found = new ArrayList<>();

        // find the ancestors to the right of the previously located node
        // (or the first node, if no nodes have been located yet)
        Path last = base.lastAdded;
        if (last == null) last = new Path();

        List<Ancestor> ancestors = (last.size() == 0)
            ? findAncestors(prev.sym, skip)
            : findAncestors(last, skip);

        for (Ancestor ancestor : ancestors) {
            // merge the ancestor's path in the subtree, creating (if required)
            // a new subtree containing the ancestor path and figuring out the
            // ancestor extension required to be rooted at the subtree root
            Path extension = new Path();
            Subtree merged = mergeAncestorPath(base, last, ancestor, extension, !skip.isEmpty());
            if (merged == null) continue;

            // move the ancestor's path to the next node(s) in line
            for (Path next : findNextNodes(ancestor, skip)) {
                // extend the next node paths to be rooted at the subtree's root
                if (extension.size() != 0)
                    next = Path.combine(extension, next);

                // find all the descendants of the next in line matching the
                // current node, then add them to the base subtree to
                // form the final subtree list
                for (Path desc : findDescendants(next, node.sym, skip)) {
                    Subtree tree = merged.clone();
                    tree.add(desc, node.clone());

                    found.add(tree);
                }
            }
        }

        return found;
    }

    private List<Ancestor> findAncestors(Path base, Set<Symbol> skip)
    {
        Map<Symbol, List<Ancestor>> cache = new HashMap<>();
        Map<Edge, Ancestor> ancestors = new HashMap<>();

        // find the leaf node's ancestors
        for (Ancestor ancestor : findAncestors(base.last().dst, skip, cache))
            ancestors.put(
                ancestor.first(),
                new Ancestor(ancestor.edges, base, base.size())
            );

        // and shorten them, if possible, by measuring them
        // against the ancestors of the other nodes in the path
        for (int i = base.size() - 1; i >= 0; --i) {
            for (Ancestor ancestor : findAncestors(base.get(i).src, skip, cache)) {
                Ancestor old = ancestors.get(ancestor.first());

                // only use a new ancestor path if it
                // is a shorter version of the current one
                if (
                    old != null &&
                    old.size() > ancestor.size() &&
                    Collections.indexOfSubList(old.edges, ancestor.edges) != -1
                )
                    ancestors.put(
                        ancestor.first(),
                        new Ancestor(ancestor.edges, base, i)
                    );
            }
        }

        return new ArrayList<>(ancestors.values());
    }

    private List<Ancestor> findAncestors(Symbol base, Set<Symbol> skip)
    {
        return findAncestors(base, skip, new HashMap<Symbol, List<Ancestor>>(), new HashSet<Symbol>());
    }

    private List<Ancestor> findAncestors(Symbol base, Set<Symbol> skip, Map<Symbol, List<Ancestor>> cache)
    {
        return findAncestors(base, skip, cache, new HashSet<Symbol>());
    }

    private List<Ancestor> findAncestors(
        Symbol base,
        Set<Symbol> skip,
        Map<Symbol, List<Ancestor>> cache,
        Set<Symbol> open
    )
    {
        // if the node is the root (no parents), is already being visited or
        // should be skipped, bail out right away
        if (base.parents.length == 0 || open.contains(base) || skip.contains(base))
            return new ArrayList<>();

        // apply some memoization to avoid re-traversing
        List<Ancestor> cached = cache.get(base);
        if (cached != null) return cached;

        // mark the node as being under visit
        Map<Edge, Ancestor> shortest = new HashMap<>();
        open.add(base);

        for (Edge parent : base.parents) {
            Edge[] alternative = parent.src.symbols[parent.alt];

            // check how many optional/skipped nodes do the parent ends with
            int t = alternative.length;
            while (--t >= 0) {
                Edge edge = alternative[t];
                if (!edge.opt && !skip.contains(edge.dst))
                    break;
            }

            // if the node is not last in its parent's token list,
            // then the parent is the shortest valid ancestor (1 node)
            if (parent.ix != alternative.length - 1)
                shortest.put(parent, new Ancestor(
                    single(parent),
                    new Path(),
                    0
                ));

            // if the node is within the last nodes the parent could end up with,
            // recurse and add the corresponding edge to the parent's ancestors
            if (parent.ix >= t) {
                for (Ancestor ancestor : findAncestors(parent.src, skip, cache, open)) {
                    // avoid modifying ancestors; it will mess up the caching mechanism
                    Ancestor cur = ancestor.clone();
                    cur.push(parent);

                    // only add the path to the current ancestor set
                    // if its shorter than the previous one
                    Path old = shortest.get(cur.first());
                    if (old == null || cur.size() < old.size())
                        shortest.put(cur.first(), cur);
                }
            }
        }

        // finally, unmark the node, record findings in the cache
        // and return the ancestors found
        open.remove(base);

        List<Ancestor> paths = new ArrayList<>(shortest.values());
        cache.put(base, paths);

        return paths;
    }

    private Subtree mergeAncestorPath(
        Subtree tree,
        Path last,
        Ancestor ancestor,
        Path extension,
        boolean skipping
    )
    {
        // is the original subtree is still in use?
        boolean original = true;

        // no ancestor, nothing to merge
        if (ancestor.size() == 0)
            return tree;

        // check if the ancestor contains the root node
        for (int i = 0; i < ancestor.size(); ++i) {
            if (ancestor.get(i).dst == tree.root.sym) {
                Path ext = ancestor.subPath(0, i + 1);
                Node old = tree.root;

                // if it does, re-root the subtree to the ancestor's root
                tree = new Subtree(new Node(ancestor.first().src));
                tree.add(ext, old.clone());
                original = false;

                // and extend the last leaf path accordingly
                last = Path.combine(ext, last);
                break;
            }
        }

        // check if the ancestor path is fully contained in the last leaf path;
        // if it is, no need to relocate the leaf, just compute the ancestor
        // extension path and return
        int ix = Collections.indexOfSubList(last.edges, ancestor.edges);
        if (ix != -1) {
            extension.edges.clear();
            extension.edges.addAll(last.edges.subList(0, ix));

            return tree;
        }

        // find the source path; the path in the tree corresponding to where
        // the ancestor's last edge is pointing
        int o = last.size() - ancestor.leaf.size();
        Path source = last.subPath(0, o + ancestor.base);

        // find the destination path; the path in the tree corresponding to where
        // the ancestor can be rooted (source of the first edge)
        Path destination;
        for (destination = source.clone(); destination.size() > 0; destination.pop())
            if (destination.last().dst == ancestor.first().src)
                break;

        // validate the source & destination paths for merging
        if (!validateMergePaths(destination, source, ancestor, tree))
            return null;

        // re-add the source to the tree with the ancestor's path
        if (original) tree = tree.clone();

        Node child = tree.find(source);
        Node base = child.parent;

        base.remove(source.last());
        tree.add(Path.combine(destination, ancestor), child);

        // in some cases, when skipping nodes (using locateDisjoint, for instance)
        // the old path to the source node is left behind and needs to be cleaned out
        if (skipping && base.size() == 0) {
            // go back up the tree, stopping at the first parent to be kept
            while (base.parent != null && base.parent.size() == 1)
                base = base.parent;

            // clean out the old path
            if (base.parent != null)
                base.parent.remove(base);
        }

        // the destination path directly corresponds to what's required as the
        // extension path; copy the destination path over
        extension.edges.clear();
        extension.edges.addAll(destination.edges);

        return tree;
    }

    private boolean validateMergePaths(Path destination, Path source, Path ancestor, Subtree tree)
    {
        // no destination means no way to add the ancestor path to the subtree
        if (destination.size() == 0 && ancestor.first().src != tree.root.sym)
            return false;

        // since the destination path comes from the source path, if both path are of
        // the same length, both are equal, which means the entire branch below
        // the destination gets replaced and there is no possibility of an incorrect
        // alternative or source
        if (destination.size() == source.size())
            return true;

        // otherwise, the source path is longer than the destination and some of the
        // nodes below the destination are kept; iterate down the source and ancestor
        // paths for the first mismatched edge
        Edge cur = null, old = null;
        int o, c;

        for (o = destination.size(), c = 0; o < source.size() && c < ancestor.size(); ++o, ++c)
            if ((old = source.get(o)) != (cur = ancestor.get(c)))
                break;

        // if for some obscure reason the source or destination is empty and
        // cur & old end up null, no need to proceed further
        if (cur == null || old == null)
            return cur == old;

        // special case; the ancestor edge will never be shadowed, but the source
        // edge might be, which will lead to issues later on when merging and
        // finding out the appropriate path to the next node
        // if the source edge is shadowed, shadow the ancestor edge as well
        if (old.shw != 0)
            ancestor.edges.set(c, (cur = cur.shadow(old.shw)));

        // the merge is valid only if the ancestor edge has the correct endpoint
        // node and alternative and comes after the source edge, as the ancestor
        // must be inserted towards the right side of the subtre
        return (cur == old || (
            cur.src == old.src &&
            cur.alt == old.alt &&
            cur.compareTo(old) >= 0
        ));
    }

    private List<Path> findNextNodes(Path path, Set<Symbol> skip)
    {
        Edge cur = path.first();
        Production parent = cur.src;

        // if the current node is a list, the next node can be a copy of itself
        List<Path> paths = new ArrayList<>();
        if (cur.list) paths.add(new Path(single(cur.shadow())));

        // otherwise the next node can be any (non-skipped) node following the
        // current one in the parent, up to the first non-optional node
        for (int i = cur.ix + 1; i < parent.symbols[cur.alt].length; ++i) {
            Edge next = parent.get(cur.alt, i);

            if (skip.contains(next.dst)) continue;

            paths.add(new Path(single(next)));

            if (!next.opt) break;
        }

        return paths;
    }

    private List<Path> findDescendants(Path base, Symbol key, Set<Symbol> skip)
    {
        // find descendants from the bottom of the base path
        List<Path> descendants = findDescendants(base.last().dst, key, skip);
        for (int i = 0; i < descendants.size(); ++i)
            descendants.set(i, Path.combine(base, descendants.get(i)));

        return descendants;
    }

    private List<Path> findDescendants(Symbol base, Symbol key, Set<Symbol> skip)
    {
        return findDescendants(base, key, skip, new HashSet<Symbol>());
    }

    private List<Path> findDescendants(Symbol base, Symbol key, Set<Symbol> skip, Set<Symbol> open)
    {
        // if the node is to be skipped, bail out right away
        if (skip.contains(base))
            return new ArrayList<>();

        // check if the key already matches the base node
        if (base == key)
            return single(new Path());

        // if the node is terminal (no children) or is already being visited,
        // no need to go further
        if (base instanceof Terminal || open.contains(base))
            return new ArrayList<>();

        // mark the node as being under visit
        List<Path> paths = new ArrayList<>();
        Production prod = (Production)(base);
        open.add(base);

        for (Edge[] alternative : prod.symbols) {
            for (Edge edge : alternative) {
                // outright ignore the node if it is to be skipped
                if (skip.contains(edge.dst)) continue;

                // recursively try to find the key from the leftmost node, prepending
                // the current edge to the path(s) found
                for (Path descendant : findDescendants(edge.dst, key, open)) {
                    descendant.unshift(edge);
                    paths.add(descendant);
                }

                // stopping at the first non-optional node, as in no case would the
                // next node be leftmost then
                if (!edge.opt) break;
            }
        }

        // finally, unmark and return the descendants found
        open.remove(base);
        return paths;
    }

    private Set<Symbol> findDeadSymbols(Set<Symbol> base)
    {
        // traverse the tree and collect the alive set; the set of all
        // nodes that can contain a node in the base set
        Set<Symbol> alive = new HashSet<>();
        for (Symbol sym : base)
            collectLiveSymbols(sym, alive);

        // compose a set with all possible symbols, then remove the alive
        // ones from the set above to form the 'dead' set
        Set<Symbol> dead = new HashSet<>();
        dead.addAll(productions.values());
        dead.addAll(terminals.values());

        dead.removeAll(alive);
        return dead;
    }

    private void collectLiveSymbols(Symbol base, Set<Symbol> alive)
    {
        // if the node is already marked as alive,
        // there is no other live nodes to collect behind it
        if (alive.contains(base)) return;

        // the current node is obviously alive
        alive.add(base);

        // recursively mark the current node's parents as alive as well
        for (Edge edge : base.parents)
            collectLiveSymbols(edge.src, alive);
    }

    private Node shortGenerate(Symbol sym, Map<Symbol, Node> known)
    {
        // apply some memoization to avoid regenerating a node
        Node shortest;
        if ((shortest = known.get(sym)) != null)
            return shortest.clone();

        // if the template is of a production rule, fully generate each
        // alternative to find the shortest one, ignoring optional symbols
        if (sym instanceof Production) {
            // as a node may self-recurse, immediately add it to the memoization cache
            // with the maximal length to avoid picking it as the shortest child
            shortest = new Node(sym);
            shortest.length = Integer.MAX_VALUE;
            known.put(sym, shortest);

            Production prod = (Production)(sym);
            for (Edge[] alt : prod.symbols) {
                // generate a new candidate node for each alternative
                Node node = new Node(sym);
                node.length = 0;

                for (Edge edge : alt) {
                    if (edge.opt) continue;

                    // generate each child (sub) node recursively
                    Node sub = shortGenerate(edge.dst, known);
                    node.put(edge, sub);

                    // and compute the candidate node's length after the
                    // generation of each child node
                    if (sub.length == Integer.MAX_VALUE)
                        node.length = Integer.MAX_VALUE;

                    else if (node.length != Integer.MAX_VALUE)
                        node.length += sub.length;
                }

                if (shortest.length > node.length)
                    shortest = node;
            }

        // otherwise the template is of a terminal, and generate
        // the corresponding Token from the terminal's value
        } else {
            Terminal terminal = (Terminal)(sym);
            String value = terminal.regex
                ? Regexp.generateString(terminal.value)
                : terminal.value;

            shortest = new Token(terminal, value);
        }

        // save the freshly generated node in the memoization cache
        known.put(shortest.sym, shortest);
        return shortest;
    }

    private static <T> List<T> single(T item)
    {
        List<T> list = new ArrayList<>();

        list.add(item);

        return list;
    }
}
