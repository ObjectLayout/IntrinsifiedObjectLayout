/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout.intrinsifiable;

/**
 * This class contains the intrinsifiable portions of PrimitiveDoubleArray behavior. JDK implementations
 * that choose to intrinsify PrimitiveDoubleArray are expected to replace the implementation of this
 * base class.
 */

public abstract class AbstractPrimitiveDoubleArray extends PrimitiveArray {

    private final double[] array;

    protected final double[] _getArray() {
        // TODO replace with direct access logic, along the lines of:
        // return (double[]) deriveContainedObjectAtOffset(this, getInstanceSize(this.class));
        //
        // getInstanceSize(this.class) can hopefully be optimized either statically (e.g. in
        // CHA-like form by proving that the type known at the call site is a lead class), or
        // dynamically (e.g. in inline-cache form by checking the class against a cached type,
        // and using the cache size associated with that type in the fastpath).
        return array;
    }

    protected AbstractPrimitiveDoubleArray() {
        // TODO: replace new with constructObjectAtOffset() call:
        array = new double[getLength()];
    }

    protected AbstractPrimitiveDoubleArray(AbstractPrimitiveDoubleArray sourceArray) {
        // TODO: replace clone() call with constructObjectAtOffset() call, followed by call to copy contents:
        array = sourceArray._getArray().clone();
    }
}
