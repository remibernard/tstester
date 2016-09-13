package tstester.grammar;

import java.util.*;
import org.sablecc.sablecc.analysis.*;
import org.sablecc.sablecc.node.*;
import nl.flotsam.xeger.*;

public class Regexp
{
    public static final List<Character> RESERVED_CHARS =
        Collections.unmodifiableList(Arrays.asList(
            '|', '&', '?', '*', '+', '{', ',', '}',
            '[', '^', ']', '-', '.', '#', '@', '"',
            '(', '"', ')', '<', '\\', '>'
        ));

    private static final Map<String, Xeger> cache = new HashMap<>();
    private static final Random random = new Random();

    public static String fromSableCCRegexp(ARegExp regexp)
    {
        return fromSableCCRegexp(regexp, new HashMap<String, ARegExp>());
    }

    public static String fromSableCCRegexp(ARegExp regexp, final Map<String, ARegExp> helpers)
    {
        // first, do a quick check to see if the regexp
        // isn't just a simple string or char
        String basic = asSimpleString(regexp);
        if (basic != null) return basic;

        final StringBuilder buffer = new StringBuilder();

        // handle regexp nodes using SableCC's tree traversal API
        regexp.apply(new AnalysisAdapter() {
            public void caseARegExp(ARegExp node)
            {
                // there must be at least one PConcat node
                Iterator<PConcat> iter = node.getConcats().iterator();
                iter.next().apply(this);

                // further PConcat nodes are unioned (| operator) together
                while (iter.hasNext()) {
                    buffer.append('|');
                    iter.next().apply(this);
                }
            }

            public void caseAConcat(AConcat node)
            {
                // AConcat child nodes are concatenated together
                for (PUnExp child : node.getUnExps())
                    child.apply(this);
            }

            public void caseAUnExp(AUnExp node)
            {
                char op = '\0';

                // check out which unary operator is present
                if (node.getUnOp() instanceof AQMarkUnOp)
                    op = '?';

                else if (node.getUnOp() instanceof APlusUnOp)
                    op = '+';

                else if (node.getUnOp() instanceof AStarUnOp)
                    op = '*';

                // if there no operator present, just output the basic node as-is
                if (op == '\0') {
                    node.getBasic().apply(this);

                // otherwise, wrap the basic node in parens and add the operator
                } else {
                    buffer.append('(');
                    node.getBasic().apply(this);
                    buffer.append(')').append(op);
                }
            }

            public void casePChar(PChar node)
            {
                // determine what kind of character encoding
                // (text, dec or hex) is in use
                char chr = '\0';

                // as a single character in single quotes
                if (node instanceof ACharChar)
                    chr = ((ACharChar)(node)).getChar().getText().charAt(1);

                // as a hexadecimal number
                else if (node instanceof AHexChar)
                    chr = (char)(Integer.parseInt(
                        ((AHexChar)(node)).getHexChar().getText().substring(2)
                    ));

                // as a regular decimal number
                else if (node instanceof ADecChar)
                    chr = (char)(Integer.parseInt(
                        ((ADecChar)(node)).getDecChar().getText()
                    ));

                // escape if reserved
                if (RESERVED_CHARS.contains(chr))
                    buffer.append('\\');

                buffer.append(chr);
            }

            public void caseAStringBasic(AStringBasic node)
            {
                // a simple string in single quotes
                String str = node.getString().getText();
                str = str.substring(1, str.length() - 1);

                // split the string on double quotes and individually
                // quote each piece to avoid quoting issues
                for (String chk : str.split("\""))
                    buffer.append('"').append(chk).append('"');
            }

            public void caseAIdBasic(AIdBasic node)
            {
                // resolve the id and re-invoke this handler
                String id = node.getId().getText();

                ARegExp resolved = helpers.get(id);
                if (resolved == null)
                    throw new RuntimeException("Unknown helper '" + id + "'");

                resolved.apply(this);
            }

            public void caseARegExpBasic(ARegExpBasic node)
            {
                // add parens and recurse over sub-regexps
                buffer.append('(');
                node.getRegExp().apply(this);
                buffer.append(')');
            }

            public void caseAOperationSet(AOperationSet node)
            {
                String op = null;

                // '+' operator corresponds to union
                if (node.getBinOp() instanceof APlusBinOp)
                    op = "|";

                // '-' operator corresponds to subtraction,
                // which is in turn intersection with complement
                else if (node.getBinOp() instanceof AMinusBinOp)
                    op = "&~";

                // stitch everything together and wrap with parens to avoid
                // interactions with other operators
                buffer.append('(');
                node.getLeft().apply(this);

                buffer.append(op);

                node.getRight().apply(this);
                buffer.append(')');
            }

            public void caseAIntervalSet(AIntervalSet node)
            {
                // corresponds directly to a character class;
                // just add brackets and set both ends of the range
                buffer.append('[');
                casePChar(node.getLeft());

                buffer.append('-');

                casePChar(node.getRight());
                buffer.append(']');
            }

            public void caseACharBasic(ACharBasic node)
            {
                casePChar(node.getChar());
            }

            public void caseASetBasic(ASetBasic node)
            {
                node.getSet().apply(this);
            }
        });

        return buffer.toString();
    }

    public static String generateString(String regexp)
    {
        // check if a generator for this string hasn't been created already
        Xeger xeger = cache.get(regexp);
        if (xeger == null)
            cache.put(regexp, (xeger = new Xeger(regexp, random)));

        // generate a string using the generator
        return xeger.generate();
    }

    public static String asSimpleString(ARegExp regexp)
    {
        String raw = null;

        // a simple string has exactly one AConcat
        List<PConcat> concats = regexp.getConcats();
        if (concats.size() != 1) return null;

        // one AUnExp
        List<PUnExp> unexps = ((AConcat)(concats.get(0))).getUnExps();
        if (unexps.size() != 1) return null;

        // no unary operator
        AUnExp unexp = (AUnExp)(unexps.get(0));
        if (unexp.getUnOp() != null) return null;

        // and is either an AStringBasic
        PBasic basic = unexp.getBasic();
        if (basic instanceof AStringBasic) {
            raw = ((AStringBasic)(basic)).getString().getText();

        // or an ACharBasic containing an ACharChar
        } else if (basic instanceof ACharBasic) {
            PChar pchar = ((ACharBasic)(basic)).getChar();

            if (pchar instanceof ACharChar)
                raw = ((ACharChar)(pchar)).getChar().getText();
        }

        // in all other cases; not a simple string
        if (raw == null) return null;

        // a simple string was found; strip off the quotes and return
        return raw.substring(1, raw.length() - 1);
    }
}
