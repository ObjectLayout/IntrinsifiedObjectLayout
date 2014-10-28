/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * A abstract base class for subclassable primitive and reference arrays.
 */

public abstract class AbstractPrimitiveArray {

    private final long length;

    static <A extends AbstractPrimitiveArray> A _newInstance(
            final Class<A> arrayClass,
            final long length) {
        try {
            return instantiate(length, arrayClass.getDeclaredConstructor(), (Object[]) null);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    static <A extends AbstractPrimitiveArray> A _newInstance(
            final long length,
            final Constructor<A> arrayConstructor,
            final Object... arrayConstructorArgs) {
        return instantiate(length, arrayConstructor, arrayConstructorArgs);
    }

    static <A extends AbstractPrimitiveArray> A _copyInstance(A source) throws NoSuchMethodException {
        @SuppressWarnings("unchecked")
        final Class<A> sourceArrayClass = (Class<A>) source.getClass();
        Constructor<A> arrayConstructor = sourceArrayClass.getDeclaredConstructor(sourceArrayClass);
        return instantiate(source._getLength(), arrayConstructor, source);
    }

    static <A extends AbstractPrimitiveArray> A constructPrimitiveArrayWithin(
            final Object containingObject,
            final AbstractIntrinsicObjectModel<A> intrinsicObjectModel,
            final long length,
            final Constructor<A> arrayConstructor,
            final Object... arrayConstructorArgs) {
        // TODO: replace the following with in-place instantiation in the containing object:
        A array = instantiate(length, arrayConstructor, arrayConstructorArgs);
        intrinsicObjectModel.directlyInitializeTargetField(containingObject, array);
        return array;
    }

    private static <A extends AbstractPrimitiveArray> A instantiate(
            final long length,
            final Constructor<A> arrayConstructor,
            final Object... arrayConstructorArgs) {
        ConstructorMagic constructorMagic = getConstructorMagic();
        constructorMagic.setArrayConstructorArgs(length);
        // Calculate array size in the heap:
        long size = primitiveArrayFootprint(arrayConstructor.getDeclaringClass(), length, false /* not contained */);
        try {
            constructorMagic.setActive(true);
            arrayConstructor.setAccessible(true);
            // TODO: use allocateHeapForClass(arrayConstructor.getDeclaringClass(), size) to allocate room for array
            // TODO: replace arrayConstructor.newInstance() call with constructObjectAtOffset() call:
            return arrayConstructor.newInstance(arrayConstructorArgs);
        } catch (InstantiationException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex);
        } finally {
            constructorMagic.setActive(false);
        }
    }

    static long primitiveArrayFootprint(
            Class<? extends AbstractPrimitiveArray> arrayClass,
            long length,
            boolean contained) {
        long primitiveElementSize;

        if (AbstractPrimitiveByteArray.class.isAssignableFrom(arrayClass)) {
            primitiveElementSize = 1;
        } else if (AbstractPrimitiveCharArray.class.isAssignableFrom(arrayClass)) {
            primitiveElementSize = 2;
        } else if (AbstractPrimitiveDoubleArray.class.isAssignableFrom(arrayClass)) {
            primitiveElementSize = 8;
        } else if (AbstractPrimitiveFloatArray.class.isAssignableFrom(arrayClass)) {
            primitiveElementSize = 4;
        } else if (AbstractPrimitiveIntArray.class.isAssignableFrom(arrayClass)) {
            primitiveElementSize = 4;
        } else if (AbstractPrimitiveLongArray.class.isAssignableFrom(arrayClass)) {
            primitiveElementSize = 8;
        } else if (AbstractPrimitiveShortArray.class.isAssignableFrom(arrayClass)) {
            primitiveElementSize = 2;
        } else if (AbstractReferenceArray.class.isAssignableFrom(arrayClass)) {
            primitiveElementSize = 8;
        } else {
            throw new IllegalArgumentException("Unrecognized primitive array class");
        }

        long footprint = contained ?
                Unsafes.getContainingObjectFootprintWhenContained(
                        arrayClass,
                        primitiveElementSize,
                        length
                ) :
                Unsafes.getContainingObjectFootprint(
                        arrayClass,
                        primitiveElementSize,
                        length
                );
        return footprint;
    }

    protected AbstractPrimitiveArray() {
        checkConstructorMagic();
        ConstructorMagic constructorMagic = getConstructorMagic();
        length = constructorMagic.getLength();
        constructorMagic.setActive(false);
    }

    protected AbstractPrimitiveArray(AbstractPrimitiveArray source) {
        this();
    }

    final long _getLength() {
        return length;
    }

    // ConstructorMagic support:

    private static class ConstructorMagic {
        private boolean isActive() {
            return active;
        }

        private void setActive(final boolean active) {
            this.active = active;
        }

        private void setArrayConstructorArgs(final long length) {
            this.length = length;
        }

        private long getLength() {
            return length;
        }

        private boolean active = false;
        private long length = 0;
    }

    private static final ThreadLocal<ConstructorMagic> threadLocalConstructorMagic = new ThreadLocal<ConstructorMagic>();

    private static ConstructorMagic getConstructorMagic() {
        ConstructorMagic constructorMagic = threadLocalConstructorMagic.get();
        if (constructorMagic == null) {
            constructorMagic = new ConstructorMagic();
            threadLocalConstructorMagic.set(constructorMagic);
        }
        return constructorMagic;
    }

    private static void checkConstructorMagic() {
        final ConstructorMagic constructorMagic = threadLocalConstructorMagic.get();
        if ((constructorMagic == null) || !constructorMagic.isActive()) {
            throw new IllegalArgumentException(
                    "PrimitiveArray must not be directly instantiated with a constructor." +
                            " Use newInstance(...) instead.");
        }
    }

    static final int MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT = 30;
    static final int MAX_EXTRA_PARTITION_SIZE = 1 << MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT;
    static final int PARTITION_MASK = MAX_EXTRA_PARTITION_SIZE - 1;

    final Object createIntAddressableElements(Class componentClass) {
        long length = _getLength();
        // Size int-addressable sub arrays:
        final int intLength = (int) Math.min(length, Integer.MAX_VALUE);
        return Array.newInstance(componentClass, intLength);
    }

    final Object createLongAddressableElements(Class componentClass) {
        long length = _getLength();
        // Compute size of int-addressable sub array:
        final int intLength = (int) Math.min(length, Integer.MAX_VALUE);
        // Size Subsequent partitions hold long-addressable-only sub arrays:
        final long extraLength = length - intLength;
        final int numFullPartitions = (int) (extraLength >>> MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT);
        final int lastPartitionSize = (int) extraLength & PARTITION_MASK;

        Object lastPartition = Array.newInstance(componentClass, lastPartitionSize);
        Class partitionClass = lastPartition.getClass();
        Object[] longAddressableElements = (Object[]) Array.newInstance(partitionClass, numFullPartitions + 1);

        // longAddressableElements = new long[numFullPartitions + 1][];

        // full long-addressable-only partitions:
        for (int i = 0; i < numFullPartitions; i++) {
            longAddressableElements[i] = Array.newInstance(componentClass, MAX_EXTRA_PARTITION_SIZE);
        }

        // Last partition with leftover long-addressable-only size:
        longAddressableElements[numFullPartitions] = lastPartition;

        return longAddressableElements;
    }
}
