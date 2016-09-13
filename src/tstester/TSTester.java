package tstester;

import java.io.*;
import java.util.*;
import tstester.grammar.*;
import tstester.typesystem.*;

public class TSTester
{
    public static String grammarPath, rulesPath, outputPath;
    public static Grammar grammar = null;
    public static TypeSystem typeSystem = null;

    static
    {
        grammarPath = rulesPath = outputPath = null;
    }

    public static void usage()
    {
        usage(null);
    }

    public static void usage(String msg, Object... args)
    {
        System.err.println(
            "TSTester [] " +
            "[-o <output directory>] " +
            "<grammar file> <type rules file>"
        );

        if (msg != null)
            System.err.println(String.format(msg, args));

        System.exit(-1);
    }

    public static void parseArguments(String[] args)
    {
        int n = args.length;
        if (n < 2) usage();

        grammarPath = args[n - 2];
        rulesPath = args[n - 1];
        n -= 2;

        for (int i = 0; i != n; ++i) {
            String arg = args[i];

            if (arg.charAt(0) != '-')
                usage("unknown argument '%s'", arg);

            for (int j = 1; j != arg.length(); ++j) {
                switch (arg.charAt(j)) {
                case 'o':
                    if (i >= n - 1)
                        usage("missing output directory (value for 'o')");

                    outputPath = args[i++];
                    break;

                // ...

                default:
                    usage("unknown option '%c'", arg.charAt(j));
                    break;
                }
            }
        }

        if (outputPath == null) {
            String name = (new File(rulesPath)).getName();
            int ix = name.lastIndexOf('.');

            outputPath = ix == -1 ? name : name.substring(0, ix);
        }
    }

    public static void parseRules() throws Exception
    {
        PushbackReader in = null;

        try {
            in = new PushbackReader(new FileReader(rulesPath));

            tstester.sablecc.rgrammar.lexer.Lexer lexer =
                new tstester.sablecc.rgrammar.lexer.Lexer(in);

            tstester.sablecc.rgrammar.parser.Parser parser =
                new tstester.sablecc.rgrammar.parser.Parser(lexer);

            typeSystem = TypeSystem.fromSableCCTypeSystem(
                (tstester.sablecc.rgrammar.node.ATypeSystemSpec)
                    (parser.parse().getPTypeSystemSpec()),
                grammar
            );

        } finally {
            if (in != null) in.close();
        }
    }

    public static void parseGrammar() throws Exception
    {
        PushbackReader in = null;

        try {
            in = new PushbackReader(new FileReader(grammarPath));

            org.sablecc.sablecc.lexer.Lexer lexer =
                new org.sablecc.sablecc.lexer.Lexer(in);

            org.sablecc.sablecc.parser.Parser parser =
                new org.sablecc.sablecc.parser.Parser(lexer);

            grammar = Grammar.fromSableCCGrammar(
                (org.sablecc.sablecc.node.AGrammar)
                    (parser.parse().getPGrammar())
            );

        } finally {
            if (in != null) in.close();
        }
    }

    public static void main(String[] args) throws Exception
    {
        parseArguments(args);
        parseGrammar();
        parseRules();

        // ---
        final Random r = new Random();
        List<Subtree> s = typeSystem.generate(
            typeSystem.rules.get(3),
            new TypeSystem.SourceSelector() {
                int k = 5;

                public Source selectVariableSource(Statement stmt, Variable var, List<Source> sources)
                {
                    return sources.get(r.nextInt(sources.size()));
                }

                public Rule selectTypedValueSource(Statement stmt, Value.Type type, List<Rule> sources)
                {
                    if (k-- > 0) return sources.get(r.nextInt(sources.size()));

                    Collections.sort(sources, new Comparator<Rule>() {
                        public int compare(Rule a, Rule b)
                        {
                            return a.slots.size() - b.slots.size();
                        }
                    });

                    return sources.get(0);
                }

                public Rule selectTypeLiteralSource(Statement stmt, Value.Type type, List<Rule> sources)
                {
                    if (k-- > 0) return sources.get(r.nextInt(sources.size()));

                    Collections.sort(sources, new Comparator<Rule>() {
                        public int compare(Rule a, Rule b)
                        {
                            return a.slots.size() - b.slots.size();
                        }
                    });

                    return sources.get(0);
                }
            }
        );

        for (Subtree t : s) {
            grammar.shortFill(t.root);
            for (String ss : t.root.generate())
                System.out.print(ss + " ");
            System.out.println();
        }
        // ---

        System.exit(0);
    }
}
