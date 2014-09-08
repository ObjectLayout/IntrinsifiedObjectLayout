/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout.intrinsifiable;

/**
 * This class contains the intrinsifiable portions of PrimitiveByteArray behavior. JDK implementations
 * that choose to intrinsify PrimitiveByteArray are expected to replace the implementation of this
 * base class.
 */

public abstract class AbstractPrimitiveByteArray extends PrimitiveArray {

    private final byte[] array;

    protected final byte[] _getArray() {
        // TODO replace with direct access logic, along the lines of:
        // return (byte[]) deriveContainedObjectAtOffset(this, getInstanceSize(this.class));
        //
        // getInstanceSize(this.class) can hopefully be optimized either statically (e.g. in
        // CHA-like form by proving that the type known at the call site is a lead class), or
        // dynamically (e.g. in inline-cache form by checking the class against a cached type,
        // and using the cache size associated with that type in the fastpath).
        return array;
    }

    protected AbstractPrimitiveByteArray() {
        // TODO: replace new with constructObjectAtOffset() call:
        array = new byte[getLength()];
    }

    protected AbstractPrimitiveByteArray(AbstractPrimitiveByteArray sourceArray) {
        // TODO: replace clone() call with constructObjectAtOffset() call, followed by call to copy contents:
        array = sourceArray._getArray().clone();
    }
}
