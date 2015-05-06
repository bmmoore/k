// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.unparser;

import org.kframework.kil.ASTNode;
import org.kframework.kil.Attributes;
import org.kframework.kil.loader.Context;
import org.kframework.kore.K;
import org.kframework.kore.ToKast;
import org.kframework.kore.convertors.KILtoInnerKORE;
import org.kframework.krun.ColorOptions;
import org.kframework.transformation.Transformation;

import com.google.inject.Inject;
import org.kframework.utils.inject.Main;

public class PrintTerm implements Transformation<ASTNode, String> {

    private final ColorOptions colorOptions;
    private final OutputModes mode;
    private final Context context;
    private boolean first = true;

    @Inject
    public PrintTerm(
            ColorOptions colorOptions,
            OutputModes mode,
            @Main Context context) {
        this.colorOptions = colorOptions;
        this.mode = mode;
        this.context = context;
    }

    @Override
    public String run(ASTNode node, Attributes a) {
        if (mode == OutputModes.KAST && first) {
            first = false;
            return ToKast.apply((K) new KILtoInnerKORE(context, false).apply(node));
        } else {
            return new Unparser(a.typeSafeGet(Context.class),
                    colorOptions.color(), colorOptions.terminalColor(),
                    mode != OutputModes.NO_WRAP, false).print(node);
        }
    }

    @Override
    public String getName() {
        return "print term";
    }

}
