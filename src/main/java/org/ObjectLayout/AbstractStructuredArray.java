/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * This class contains the intrinsifiable portions of StructuredArray behavior. JDK implementations
 * that choose to intrinsify StructuredArray are expected to replace the implementation of this
 * base class.
 *
 * @param <T> the element type in the array
 */

abstract class AbstractStructuredArray<T> {

    // the existence of this field (not it's value) indicates that the class should be intrinsified:
    // (This allows the JVM to make this determination at load time, and not wait for initialization)
    private static final boolean existenceIndicatesIntrinsic = true;

    // Track initialization state:
    private boolean isInitialized = false;
    volatile boolean constructionCompleted = false;

    //
    //
    // Constructor:
    //
    //

    /**
     * Optimization NOTE: Optimized JDK implementations may choose to replace the logic of this
     * constructor, along with the way some of the internal fields are represented (see note
     * in "Internal fields" section farther below.
     */

    AbstractStructuredArray() {
        checkConstructorMagic();
        ConstructorMagic constructorMagic = getConstructorMagic();

        @SuppressWarnings("unchecked")
        final AbstractStructuredArrayModel<AbstractStructuredArray<T>, T> arrayModel =
                constructorMagic.getArrayModel();

        // Finish consuming constructMagic arguments:
        constructorMagic.setActive(false);

        if (arrayModel._getLength() < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }

        // Calculate values used to populate relevant fields:
        final long bodySize = (int) Unsafes.getInstanceSize(this.getClass());
        final long length = arrayModel._getLength();
        final long elementSize = elementFootprint(arrayModel);
        final long paddingSize = Unsafes.getPrePaddingInObjectFootprint(elementSize);
        final Class<T> elementClass = arrayModel._getElementClass();

        // initialize hidden fields:
        initBodySize((int) Unsafes.getInstanceSize(this.getClass()));
        initLength(length);
        initElementSize(elementSize);
        initPaddingSize(paddingSize);
        initElementClass(elementClass);

        // TODO: replace "vanilla" internal storage with flat representation:
        allocateInternalStorage(getLength());

        // Indicate construction is complete, such that further calls to initX() initializers of
        // hidden fields will fail from this point on.
        isInitialized = true;

        // follow update of internal boolean indication with a modification of a package-visible volatile
        // to ensure ordering (this way isInitialized does not have to be volatile and normal
        // accessor actions do not take the penalty of a volatile read barrier):
        constructionCompleted = true;
    }

    @Override
    public String toString() {
        final StringBuilder output = new StringBuilder("StructuredArrayIntrinsifiableBase<");
        output.append(getElementClass().getName()).append(">").append(getLength());
        output.append(": elementSize = ").append(getElementSize()).
                append(", padding = ").append(getPaddingSize()).
                append(", bodySize = ").append(getBodySize());

        return new String(output);
    }

    /**
     * Instantiate a StructuredArray of arrayClass with the given array model, and the supplied
     * array constructor and arguments.
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with one that
     * allocates room for the entire StructuredArray and all it's elements.
     */
    static <S extends AbstractStructuredArray<T>, T> S instantiateStructuredArray(
            AbstractStructuredArrayModel<S, T> arrayModel, Constructor<S> arrayConstructor, Object... args) {

        // For implementations that need the array class and the element class,
        // this is how
        // how to get them:
        //
        // Class<? extends StructuredArray<T>> arrayClass =
        // arrayConstructor.getDeclaringClass();
        // Class<T> elementClass = arrayModel.getElementClass();

        ConstructorMagic constructorMagic = getConstructorMagic();
        constructorMagic.setConstructionArgs(arrayModel);

        // Calculate array size in the heap:
        long size = arrayFootprint(arrayModel, false /* not contained */);

        try {
            constructorMagic.setActive(true);
            arrayConstructor.setAccessible(true);
            // TODO: use allocateHeapForElementArrayClass(arrayConstructor.getDeclaringClass(),
            //     instanceClasses, elementCounts) to allocate array.
            // TODO: replace constructor.newInstance() call with Unsafes.constructObjectAtOffset() call:
            return arrayConstructor.newInstance(args);
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

    private static long elementFootprint(AbstractStructuredArrayModel arrayModel) {
        long footprint;
        if (arrayModel._getStructuredSubArrayModel() != null) {
            footprint = arrayFootprint(arrayModel._getStructuredSubArrayModel(), true /* contained */);
        } else if (arrayModel._getPrimitiveSubArrayModel() != null) {
            AbstractPrimitiveArrayModel subArrayModel = arrayModel._getPrimitiveSubArrayModel();
            @SuppressWarnings("unchecked")
            Class<? extends AbstractPrimitiveArray> subArrayClass = subArrayModel._getArrayClass();
            footprint = AbstractPrimitiveArray.primitiveArrayFootprint(
                    subArrayClass, arrayModel._getLength(), true /* contained */);
        } else {
            // Regular element
            footprint = Unsafes.getInstanceFootprintWhenContained(arrayModel._getElementClass());
        }
        return footprint;
    }

    private static long arrayFootprint(AbstractStructuredArrayModel arrayModel, boolean contained) {
        long footprint = contained ?
                Unsafes.getContainingObjectFootprintWhenContained(
                        arrayModel._getArrayClass(),
                        elementFootprint(arrayModel),
                        arrayModel._getLength()
                ) :
                Unsafes.getContainingObjectFootprint(
                        arrayModel._getArrayClass(),
                        elementFootprint(arrayModel),
                        arrayModel._getLength()
                );
        return footprint;
    }

    /**
     * Construct a fresh element intended to occupy a given index in the given array, using the
     * supplied constructor and arguments.
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * construction-in-place call on a previously allocated memory location associated with the given index.
     */
    void constructElementAtIndex(
            final long index,
            final Constructor<T> constructor,
            Object... args) {
        try {
            if ((index < 0) || (index >= getLength())) {
                throw new ArrayIndexOutOfBoundsException();
            }
            // TODO: replace constructor.newInstance() with constructObjectAtOffset() call:
            T element = constructor.newInstance(args);
            storeElementInLocalStorageAtIndex(element, index);
        } catch (InstantiationException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Construct a fresh primitive sub-array intended to occupy a given index in the given array, using the
     * supplied constructor and arguments.
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * construction-in-place call on a previously allocated memory location associated with the given index.
     */
    void constructPrimitiveSubArrayAtIndex(
            final long index,
            AbstractPrimitiveArrayModel primitiveSubArrayModel,
            final Constructor<T> constructor,
            final Object... args) {
        if ((index < 0) || (index >= getLength())) {
            throw new ArrayIndexOutOfBoundsException();
        }
        int length = (int) primitiveSubArrayModel._getLength();
        @SuppressWarnings("unchecked")
        Constructor<? extends AbstractPrimitiveArray> c = (Constructor<? extends AbstractPrimitiveArray>) constructor;
        // TODO: replace PrimitiveArray.newInstance() with constructObjectAtOffset() call:
        @SuppressWarnings("unchecked")
        T element = (T) AbstractPrimitiveArray._newInstance(length, c, args);
        storeElementInLocalStorageAtIndex(element, index);
    }

    /**
     * Construct a fresh sub-array intended to occupy a given index in the given array, using the
     * supplied constructor.
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * construction-in-place call on a previously allocated memory location associated with the given index.
     */
    void constructSubArrayAtIndex(
            long index,
            AbstractStructuredArrayModel subArrayModel,
            final Constructor<T> subArrayConstructor,
            final Object... args) {
        if ((index < 0) || (index >= getLength())) {
            throw new ArrayIndexOutOfBoundsException();
        }
        ConstructorMagic constructorMagic = getConstructorMagic();
        constructorMagic.setConstructionArgs(subArrayModel);
        try {
            constructorMagic.setActive(true);
            subArrayConstructor.setAccessible(true);
            // TODO: replace subArrayConstructor.newInstance() with constructObjectAtOffset() call:
            T subArray = subArrayConstructor.newInstance(args);
            storeElementInLocalStorageAtIndex(subArray, index);
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

    /**
     * Construct a fresh StructuredArray intended to occupy a a given intrinsic field in the containing object,
     * at the field described by the supplied intrinsicObjectModel, using the supplied constructor and arguments.
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * construction-in-place call on a previously allocated memory location associated with the given index.
     */
    static <T> T constructStructuredArrayWithin(
            final Object containingObject,
            final AbstractIntrinsicObjectModel<T> intrinsicObjectModel,
            AbstractStructuredArrayModel subArrayModel,
            final Constructor<T> subArrayConstructor,
            final Object... args) {
        ConstructorMagic constructorMagic = getConstructorMagic();
        constructorMagic.setConstructionArgs(subArrayModel);
        try {
            constructorMagic.setActive(true);
            subArrayConstructor.setAccessible(true);
            // TODO: replace subArrayConstructor.newInstance() with constructObjectAtOffset() call:
            T array = subArrayConstructor.newInstance(args);
            intrinsicObjectModel.directlyInitializeTargetField(containingObject, array);
            return array;
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


    /**
     * Get an element at a supplied [index] in a StructuredArray
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * faster access form (e.g. they may be able to derive the element reference directly from the
     * structuredArray reference without requiring a de-reference).
     */
    T get(final int index)
            throws IllegalArgumentException {
        if ((index < 0) || (index >= getLength())) {
            throw new ArrayIndexOutOfBoundsException();
        }

        // TODO replace with direct access logic, along the lines of:
        // long offset = getBodySize() + (getPaddingSize() + (index * getElementSize()));
        // return (T) deriveContainedObjectAtOffset(this, offset);

        return intAddressableElements[index];
    }

    /**
     * Get an element at a supplied [index] in a StructuredArray
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * faster access form (e.g. they may be able to derive the element reference directly from the
     * structuredArray reference without requiring a de-reference).
     */
    T get(final long index)
            throws IllegalArgumentException {
        if ((index < 0) || (index >= getLength())) {
            throw new ArrayIndexOutOfBoundsException();
        }

        // TODO replace with direct access logic, along the lines of:
        // long offset = getBodySize() + (getPaddingSize() + (index * getElementSize()));
        // return (T) deriveContainedObjectAtOffset(this, offset);

        if (index < Integer.MAX_VALUE) {
            return get((int) index);
        }

        // Calculate index into long-addressable-only partitions:
        final long longIndex = (index - Integer.MAX_VALUE);
        final int partitionIndex = (int)(longIndex >>> MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT);
        final int partitionOffset = (int)longIndex & PARTITION_MASK;

        return longAddressableElements[partitionIndex][partitionOffset];
    }

    //
    //
    // Accessor methods for instance state:
    //
    //

    @SuppressWarnings("unchecked")
    Class<T> getElementClass() {
        return (Class<T>) unsafe.getObject(this, elementClassOffset);
    }

    private long getLength() {
        return unsafe.getLong(this, lengthOffset);
    }


    //
    //
    // Other internal fields accessor and initializer methods:
    //
    //

    private int getBodySize() {
        return unsafe.getInt(this, bodySizeOffset);
    }

    private void initBodySize(int bodySize) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putInt(this, bodySizeOffset, bodySize);
    }

    private void initLength(long length) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, lengthOffset, length);
    }

    private long getElementSize() {
        return unsafe.getLong(this, elementSizeOffset);
    }

    private void initElementSize(long elementSize) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, elementSizeOffset, elementSize);
    }

    private long getPaddingSize() {
        return unsafe.getLong(this, paddingSizeOffset);
    }

    private void initPaddingSize(long paddingSize) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, paddingSizeOffset, paddingSize);
    }

    private void initElementClass(Class<T> elementClass) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putObject(this, elementClassOffset, elementClass);
    }

    /**
      * OPTIMIZATION NOTE: Optimized JDK implementations may choose to hide these fields in non-Java-accessible
      * internal instance fields (much like array.length is hidden), in order to ensure that no uncontrolled
      * modification of the fields can be made by any Java program (not even via reflection getting at private
      * fields and bypassing their finality). This is an important security concern because optimization
      * may need to make strong assumptions about the true finality of some of these fields.
      */

    private static int offset = 0;
    private static final long bodySizeOffset        = offset += 8;
    private static final long lengthOffset          = offset += 8;
    private static final long elementSizeOffset     = offset += 8;
    private static final long paddingSizeOffset     = offset += 8;
    private static final long elementClassOffset    = offset += 8;

    // These are our hidden fields, supported by the JVM's special handling of this
    // class: They are only accessible via offsets and unsafe:

    //    private int bodySize;
    //    private long length;
    //    private long elementSize;
    //    private long paddingSize;
    //    private Class<T> elementClass;

    //
    //
    // Internal Storage support:
    //
    //

    /**
     * OPTIMIZATION NOTE: Optimized JDK implementations may choose to replace this vanilla-Java internal
     * storage representation with one that is more intrinsically understood by the JDK and JVM.
     */

    static final int MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT = 30;
    static final int MAX_EXTRA_PARTITION_SIZE = 1 << MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT;
    static final int PARTITION_MASK = MAX_EXTRA_PARTITION_SIZE - 1;

    private T[][] longAddressableElements; // Used to store elements at indexes above Integer.MAX_VALUE
    private T[] intAddressableElements;

    @SuppressWarnings("unchecked")
    private void allocateInternalStorage(final long length) {
        // Allocate internal storage:

        // Size int-addressable sub arrays:
        final int intLength = (int) Math.min(length, Integer.MAX_VALUE);
        // Size Subsequent partitions hold long-addressable-only sub arrays:
        final long extraLength = length - intLength;
        final int numFullPartitions = (int) (extraLength >>> MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT);
        final int lastPartitionSize = (int) extraLength & PARTITION_MASK;

        intAddressableElements = (T[]) new Object[intLength];
        longAddressableElements = (T[][]) new Object[numFullPartitions + 1][];
        // full long-addressable-only partitions:
        for (int i = 0; i < numFullPartitions; i++) {
            longAddressableElements[i] = (T[]) new Object[MAX_EXTRA_PARTITION_SIZE];
        }
        // Last partition with leftover long-addressable-only size:
        longAddressableElements[numFullPartitions] = (T[]) new Object[lastPartitionSize];
    }

    private void storeElementInLocalStorageAtIndex(T element, long index0) {
        // place in proper internal storage location:
        if (index0 < Integer.MAX_VALUE) {
            intAddressableElements[(int) index0] = element;
            return;
        }

        // Calculate index into long-addressable-only partitions:
        final long longIndex0 = (index0 - Integer.MAX_VALUE);
        final int partitionIndex = (int) (longIndex0 >>> MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT);
        final int partitionOffset = (int) longIndex0 & PARTITION_MASK;

        longAddressableElements[partitionIndex][partitionOffset] = element;
    }

    //
    //
    // ConstructorMagic support:
    //
    //

    /**
     * OPTIMIZATION NOTE: The ConstructorMagic will likely not need to be modified in any way even for
     * optimized JDK implementations. It resides in this class for scoping reasons.
     */

    private static class ConstructorMagic {
        private boolean isActive() {
            return active;
        }

        private void setActive(boolean active) {
            this.active = active;
        }

        private void setConstructionArgs(AbstractStructuredArrayModel arrayModel) {
            this.arrayModel = arrayModel;
        }

        private AbstractStructuredArrayModel getArrayModel() {
            return arrayModel;
        }

        private boolean active = false;
        AbstractStructuredArrayModel arrayModel;
    }

    private static final ThreadLocal<ConstructorMagic> threadLocalConstructorMagic =
            new ThreadLocal<ConstructorMagic>() {
                @Override protected ConstructorMagic initialValue() {
                    return new ConstructorMagic();
                }
            };

    private static ConstructorMagic getConstructorMagic() {
        return threadLocalConstructorMagic.get();
    }

    private static void checkConstructorMagic() {
        if (!getConstructorMagic().isActive()) {
            throw new IllegalArgumentException(
                    "StructuredArray<> must not be directly instantiated with a constructor. Use newInstance(...) instead.");
        }
    }

    //
    // Local unsafe support
    //

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
 }
