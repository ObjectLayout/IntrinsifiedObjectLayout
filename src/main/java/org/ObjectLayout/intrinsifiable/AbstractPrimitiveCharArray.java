/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout.intrinsifiable;

/**
 * This class contains the intrinsifiable portions of PrimitiveCharArray behavior. JDK implementations
 * that choose to intrinsify PrimitiveCharArray are expected to replace the implementation of this
 * base class.
 */

public abstract class AbstractPrimitiveCharArray extends PrimitiveArray {

    private final char[] array;

    protected final char[] _getArray() {
        // TODO replace with direct access logic, along the lines of:
        // return (char[]) deriveContainedObjectAtOffset(this, getInstanceSize(this.class));
        //
        // getInstanceSize(this.class) can hopefully be optimized either statically (e.g. in
        // CHA-like form by proving that the type known at the call site is a lead class), or
        // dynamically (e.g. in inline-cache form by checking the class against a cached type,
        // and using the cache size associated with that type in the fastpath).
        return array;
    }

    protected AbstractPrimitiveCharArray() {
        // TODO: replace new with constructObjectAtOffset() call:
        array = new char[getLength()];
    }

    protected AbstractPrimitiveCharArray(AbstractPrimitiveCharArray sourceArray) {
        // TODO: replace clone() call with constructObjectAtOffset() call, followed by call to copy contents:
        array = sourceArray._getArray().clone();
    }
}
