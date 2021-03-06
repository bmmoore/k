// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.parser.concrete2kore;

import com.google.common.collect.Sets;
import org.kframework.attributes.Source;
import org.kframework.definition.Module;
import org.kframework.kore.Sort;
import org.kframework.parser.Term;
import org.kframework.parser.concrete2kore.disambiguation.*;
import org.kframework.parser.concrete2kore.kernel.Grammar;
import org.kframework.parser.concrete2kore.kernel.KSyntax2GrammarStatesFilter;
import org.kframework.parser.concrete2kore.kernel.Parser;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.ParseFailedException;
import scala.Tuple2;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * A wrapper that takes a module and one can call the parser
 * for that module in thread safe way.
 * Declarative disambiguation filters are also applied.
 */
public class ParseInModule implements Serializable {
    private final Module seedModule;
    private final Module extensionModule;
    /**
     * The module in which parsing will be done.
     * Note that this module will be used for disambiguation, and the parsing module can be different.
     * This allows for grammar rewriting and more flexibility in the implementation.
     */
    private final Module disambModule;
    /**
     * The exact module used for parsing. This can contain productions and sorts that are not
     * necessarily representable in KORE (sorts like Ne#Ids, to avoid name collisions).
     * In this case the modified production will be annotated with the information from the
     * original production, so disambiguation can be done safely.
     */
    private final Module parsingModule;
    private volatile Grammar grammar = null;
    private final boolean strict;
    ParseInModule(Module seedModule) {
        this(seedModule, seedModule, seedModule, seedModule, true);
    }

    public ParseInModule(Module seedModule, Module extensionModule, Module disambModule, Module parsingModule, boolean strict) {
        this.seedModule = seedModule;
        this.extensionModule = extensionModule;
        this.disambModule = disambModule;
        this.parsingModule = parsingModule;
        this.strict = strict;
    }

    /**
     * The original module, which includes all the marker/flags imports.
     * This can be used to invalidate caches.
     * @return Module given by the user.
     */
    public Module seedModule() {
        return seedModule;
    }

    /**
     * An extension module of the seedModule which includes all productions, unmodified, and in addition,
     * contains extra productions auto-defined, like casts.
     * @return Module with extra productions defined during parser generator.
     */
    public Module getExtensionModule() {
        return extensionModule;
    }

    /**
     * Parse as input the given string and start symbol using the module stored in the object.
     * @param input          the string to parse.
     * @param startSymbol    the start symbol from which to parse.
     * @return the Term representation of the parsed input.
     */
    public Tuple2<Either<Set<ParseFailedException>, Term>, Set<ParseFailedException>>
            parseString(String input, Sort startSymbol, Source source) {
        return parseString(input, startSymbol, source, 1, 1);
    }

    private void getGrammar() {
        Grammar g = grammar;
        if (g == null) {
            g = KSyntax2GrammarStatesFilter.getGrammar(this.parsingModule);
            grammar = g;
        }
    }

    public Tuple2<Either<Set<ParseFailedException>, Term>, Set<ParseFailedException>>
            parseString(String input, Sort startSymbol, Source source, int startLine, int startColumn) {
        getGrammar();

        Grammar.NonTerminal startSymbolNT = grammar.get(startSymbol.name());
        Set<ParseFailedException> warn = new AmbFilter().warningUnit();
        if (startSymbolNT == null) {
            String msg = "Could not find start symbol: " + startSymbol;
            KException kex = new KException(KException.ExceptionType.ERROR, KException.KExceptionGroup.CRITICAL, msg);
            return new Tuple2<>(Left.apply(Sets.newHashSet(new ParseFailedException(kex))), warn);
        }

        Parser parser = new Parser(input, source, startLine, startColumn);
        Term parsed;
        try {
            parsed = parser.parse(startSymbolNT, 0);
        } catch (ParseFailedException e) {
            return Tuple2.apply(Left.apply(Collections.singleton(e)), Collections.emptySet());
        }

        Either<Set<ParseFailedException>, Term> rez = new TreeCleanerVisitor().apply(parsed);
        if (rez.isLeft())
            return new Tuple2<>(rez, warn);
        rez = new CorrectRewritePriorityVisitor().apply(rez.right().get());
        if (rez.isLeft())
            return new Tuple2<>(rez, warn);
        rez = new CorrectKSeqPriorityVisitor().apply(rez.right().get());
        if (rez.isLeft())
            return new Tuple2<>(rez, warn);
        rez = new CorrectCastPriorityVisitor().apply(rez.right().get());
        if (rez.isLeft())
            return new Tuple2<>(rez, warn);
        rez = new ApplyTypeCheckVisitor(disambModule.subsorts()).apply(rez.right().get());
        if (rez.isLeft())
            return new Tuple2<>(rez, warn);
        rez = new PriorityVisitor(disambModule.priorities(), disambModule.leftAssoc(), disambModule.rightAssoc()).apply(rez.right().get());
        if (rez.isLeft())
            return new Tuple2<>(rez, warn);
        Tuple2<Either<Set<ParseFailedException>, Term>, Set<ParseFailedException>> rez2 = new VariableTypeInferenceFilter(disambModule.subsorts(), disambModule.definedSorts(), disambModule.productionsFor(), strict).apply(rez.right().get());
        if (rez2._1().isLeft())
            return rez2;
        warn = rez2._2();

        Term rez3 = new PreferAvoidVisitor().apply(rez2._1().right().get());
        rez2 = new AmbFilter().apply(rez3);
        warn = new AmbFilter().mergeWarnings(rez2._2(), warn);
        rez2 = new AddEmptyLists(disambModule).apply(rez2._1().right().get());
        warn = new AmbFilter().mergeWarnings(rez2._2(), warn);
        rez3 = new RemoveBracketVisitor().apply(rez2._1().right().get());

        return new Tuple2<>(Right.apply(rez3), warn);
    }
}
