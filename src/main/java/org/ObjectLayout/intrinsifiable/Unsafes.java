/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout.intrinsifiable;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * This class contains the Unsafe call interfaces used by intrinsifiable portions of
 * StructuredArray and PrimitiveArray abstract base classes.
 *
 */

public abstract class Unsafes {

    private static final Unsafe unsafe;
    static
    {
        try
        {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe)field.get(null);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    static long getInstanceFootprintWhenContained(Class instanceClass) {
        // TODO: implement with something like:
        // return unsafe.getInstanceFootprintWhenContained(instanceClass);
        return 0;
    }

    static long getInstanceSize(Class instanceClass) {
        // TODO: implement with something like:
        // return unsafe.getInstanceSize(instanceClass);
        return 0;
    }

    static long getContainingObjectFootprintWhenContained(Class containerClass, long containedElementSize, long numberOfElements) {
        // TODO: implement with something like:
        // return unsafe.getContainingObjectFootprintWhenContained(this.getClass(), containedElementSize, numberOfElements);
        return getInstanceFootprintWhenContained(containerClass) + (numberOfElements * containedElementSize);
    }

    static long getContainingObjectFootprint(Class containerClass, long containedElementSize, long numberOfElements) {
        // TODO: implement with something like:
        // return unsafe.getStructuredArrayFootPrint(this.getClass(), containedElementSize, numberOfElements);
        return getInstanceSize(containerClass) + (numberOfElements * containedElementSize);
    }

    static long getPrePaddingInObjectFootprint(long objectFootprint) {
        // objectSize is inclusive of any padding.
        // TODO: implement with something like:
        // return unsafe.getPrePaddingInObjectFootprint(objectSize);
        return 0;
    }

    static Object allocateHeapForClass(Class instanceClass, long size) {
        // TODO: implement with something like:
        // return unsafe.allocateHeapForClass(instanceClass, size);
        return null;
    }

    static Object deriveContainedObjectAtOffset(Object o, long offset) {
        // TODO: implement with something like:
        // return unsafe.deriveContainedObjectAtOffset(o, offset);
        return null;
    }

    static void constructObjectAtOffset(Object containingObject, long offset, long objectPrePadding,
                                        boolean isContained, boolean isContainer, long objectFootprint,
                                        Constructor constructor, Object... args)
            throws InstantiationException, IllegalAccessException, InvocationTargetException  {
        // TODO: implement with something like:
        // unsafe.constructObjectAtOffset(containingObject, offset, objectPrePadding, isContained,
        //      isContainer, objectFootprint, constructor, args)
    }
 }
