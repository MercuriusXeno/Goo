package com.xeno.goo.library;

import java.util.Arrays;
import java.util.List;

public class ComplexEntry
{
    private List<StackComplex> composites;

    public List<StackComplex> composites() { return composites; }

    public ComplexEntry(StackComplex... composites) {
        this.composites = Arrays.asList(composites);
    }
}
