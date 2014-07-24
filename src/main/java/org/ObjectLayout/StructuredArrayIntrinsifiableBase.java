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
 * @param <T>
 */
abstract class StructuredArrayIntrinsifiableBase<T> {

    static final boolean existenceIndicatesIntrinsic = true;

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

    StructuredArrayIntrinsifiableBase() {
        checkConstructorMagic();
        ConstructorMagic constructorMagic = getConstructorMagic();

        @SuppressWarnings("unchecked")
        final CtorAndArgsProvider<T> ctorAndArgsProvider = constructorMagic.getCtorAndArgsProvider();
        final long[] lengths = constructorMagic.getLengths();
        final int dimensionCount = lengths.length;
        final Class<T> elementClass = ctorAndArgsProvider.getElementClass();

        if (dimensionCount < 1) {
            throw new IllegalArgumentException("dimensionCount must be at least 1");
        }

        if (lengths[0] < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }

        // Calculate and populate ElementSizes:
        // TODO: Take page padding into account
        final long[] elementSizes = new long[dimensionCount];
        elementSizes[dimensionCount - 1] = getInstanceSizeWhenContained(elementClass);
        for (int dim = dimensionCount - 2; dim >= 0; dim--) {
            long thisDimArrayBodySize = lengths[dim] * elementSizes[dim + 1];
            elementSizes[dim] = thisDimArrayBodySize + getInstanceSizeWhenContained(this.getClass());
        }

        setBodySize((int) getInstanceSize(this.getClass()));
        setDimensionCount(dimensionCount);

        // Populate cached lengths and elementh size values:
        setDim0Length(lengths[0]);
        setDim0ElementSize(elementSizes[0]);
        if (dimensionCount > 1) {
            setDim1Length(lengths[1]);
            setDim1ElementSize(elementSizes[1]);
        }
        if (dimensionCount > 2) {
            setDim1Length(lengths[2]);
            setDim1ElementSize(elementSizes[2]);
        }
        if (dimensionCount > 3) {
            setDim1Length(lengths[3]);
            setDim1ElementSize(elementSizes[3]);
        }

        setLengths(lengths);
        setElementSizes(elementSizes);

        setElementClass(elementClass);

//        this.dimensionCount = dimensionCount;
//        this.lengths = lengths;
//        this.length = lengths[0];
//
//        this.elementClass = ctorAndArgsProvider.getElementClass();

        allocateInternalStorage(dimensionCount, getLength());
    }

    @Override
    public String toString() {
        return "Hey!";
    }

    /**
     * Instantiate a StructuredArray of arrayClass with member elements of elementClass, and the
     * set of lengths (one length per dimension in the lengths[] array), using the given constructor
     * and arguments.
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with one that
     * allocates room for the entire StructuredArray and all it's elements.
     */
    static <T, S extends StructuredArray<T>> S instantiateStructuredArray(
            final CtorAndArgs<S> arrayCtorAndArgs,
            final CtorAndArgsProvider<T> ctorAndArgsProvider,
            final long[] lengths) {

        // For implementations that need the array class and the element class,
        // this is how
        // how to get them:
        //
        // Class<? extends StructuredArray<T>> arrayClass =
        // arrayConstructor.getDeclaringClass();
        // Class<T> elementClass = ctorAndArgsProvider.getElementClass();

        ConstructorMagic constructorMagic = getConstructorMagic();
        constructorMagic.setArrayConstructionArgs(new ArrayConstructionArgs(
                arrayCtorAndArgs, ctorAndArgsProvider, lengths, null));
        constructorMagic.setActive(true);
        try {
            Constructor<S> arrayConstructor = arrayCtorAndArgs.getConstructor();
            arrayConstructor.setAccessible(true);
            return arrayConstructor.newInstance(arrayCtorAndArgs.getArgs());

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
     * Construct a fresh element intended to occupy a given index in the given array, using the
     * supplied constructor and arguments.
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * construction-in-place call on a previously allocated memory location associated with the given index.
     */
    void constructElementAtIndex(final long index0, final Constructor<T> constructor, Object... args)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        T element = constructor.newInstance(args);
        storeElementInLocalStorageAtIndex(element, index0);
    }

    /**
     * Construct a fresh sub-array intended to occupy a given index in the given array, using the
     * supplied constructor.
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * construction-in-place call on a previously allocated memory location associated with the given index.
     */
    void constructSubArrayAtIndex(long index0, final Constructor<StructuredArray<T>> constructor,
                                  ArrayConstructionArgs arrayConstructionArgs)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        getConstructorMagic().setArrayConstructionArgs(arrayConstructionArgs);
        StructuredArray<T> subArray = constructor.newInstance();
        storeSubArrayInLocalStorageAtIndex(subArray, index0);
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
        if (getDimensionCount() != 1) {
            throw new IllegalArgumentException("number of index parameters to get() must match array dimension count");
        }

        return intAddressableElements[index];
    }

    /**
     * Get an element at a supplied [index0, index1] in a StructuredArray
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * faster access form (e.g. they may be able to derive the element reference directly from the
     * structuredArray reference without requiring a de-reference).
     */
    T get(final int index0, final int index1)
            throws IllegalArgumentException {
        if (getDimensionCount() != 2) {
            throw new IllegalArgumentException("number of index parameters to get() must match array dimension count");
        }

        return getSubArray(index0).get(index1);
    }

    /**
     * Get an element at a supplied [index0, index1, index2] in a StructuredArray
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * faster access form (e.g. they may be able to derive the element reference directly from the
     * structuredArray reference without requiring a de-reference).
     */
    T get(final int index0, final int index1, final int index2)
            throws IllegalArgumentException {
        if (getDimensionCount() != 3) {
            throw new IllegalArgumentException("number of index parameters to get() must match array dimension count");
        }

        return getSubArray(index0).getSubArray(index1).get(index2);
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
        if (index < Integer.MAX_VALUE) {
            return get((int) index);
        }
        if (getDimensionCount() != 1) {
            throw new IllegalArgumentException("number of index parameters to get() must match array dimension count");
        }

        // Calculate index into long-addressable-only partitions:
        final long longIndex = (index - Integer.MAX_VALUE);
        final int partitionIndex = (int)(longIndex >>> StructuredArray.MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT);
        final int partitionOffset = (int)longIndex & StructuredArray.PARTITION_MASK;

        return longAddressableElements[partitionIndex][partitionOffset];
    }

    /**
     * Get an element at a supplied [index0, index1] in a StructuredArray
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * faster access form (e.g. they may be able to derive the element reference directly from the
     * structuredArray reference without requiring a de-reference).
     */
    T get(final long index0, final long index1)
            throws IllegalArgumentException {
        if (getDimensionCount() != 2) {
            throw new IllegalArgumentException("number of index parameters to get() must match array dimension count");
        }

        return getSubArray(index0).get(index1);
    }

    /**
     * Get an element at a supplied [index0, index1, index2] in a StructuredArray
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * faster access form (e.g. they may be able to derive the element reference directly from the
     * structuredArray reference without requiring a de-reference).
     */
    T get(final long index0, final long index1, final long index2)
            throws IllegalArgumentException {
        if (getDimensionCount() != 3) {
            throw new IllegalArgumentException("number of index parameters to get() must match array dimension count");
        }

        return getSubArray(index0).getSubArray(index1).get(index2);
    }

    /**
     * Get a reference to an element in an N dimensional array, using indices supplied in a
     * <code>long[N + indexOffset]</code> array.
     * indexOffset indicates the starting point in the array at which the first index should be found.
     * This form is useful when passing index arrays through multiple levels to avoid construction of
     * temporary varargs containers or construction of new shorter index arrays.
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * faster access form (e.g. they may be able to derive the element reference directly from the
     * structuredArray reference without requiring a de-reference).
     */
    T get(final long[] indices, final int indexOffset) throws IllegalArgumentException {
        int dimensionCount = getDimensionCount();
        if ((indices.length - indexOffset) != dimensionCount) {
            throw new IllegalArgumentException("number of relevant elements in indices must match array dimension count");
        }

        if (dimensionCount == 1) {
            return get(indices[indexOffset]);
        } else {
            StructuredArray<T> subArray = getSubArray(indices[indexOffset]);
            return subArray.get(indices, indexOffset + 1);
        }
    }

    /**
     * Get a reference to an element in an N dimensional array, using indices supplied in a
     * <code>int[N + indexOffset]</code> array.
     * indexOffset indicates the starting point in the array at which the first index should be found.
     * This form is useful when passing index arrays through multiple levels to avoid construction of
     * temporary varargs containers or construction of new shorter index arrays.
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * faster access form (e.g. they may be able to derive the element reference directly from the
     * structuredArray reference without requiring a de-reference).
     */
    T get(final int[] indices, final int indexOffset) throws IllegalArgumentException {
        int dimensionCount = getDimensionCount();
        if ((indices.length - indexOffset) != dimensionCount) {
            throw new IllegalArgumentException("number of relevant elements in indices must match array dimension count");
        }

        if (dimensionCount == 1) {
            return get(indices[indexOffset]);
        } else {
            StructuredArray<T> subArray = getSubArray(indices[indexOffset]);
            return subArray.get(indices, indexOffset + 1);
        }
    }
    
    /**
     * Get a StructuredArray Sub array at a supplied index in a StructuredArray
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * faster access form (e.g. they may be able to derive the element reference directly from the
     * structuredArray reference without requiring a de-reference).
     */
    @SuppressWarnings("unchecked")
    StructuredArray<T> getSubArray(final long index) throws IllegalArgumentException {
        if (index < Integer.MAX_VALUE) {
            return getSubArray((int) index);
        }

        if (getDimensionCount() < 2) {
            throw new IllegalArgumentException("cannot call getSubArrayL() on single dimensional array");
        }

        // Calculate index into long-addressable-only partitions:
        final long longIndex = (index - Integer.MAX_VALUE);
        final int partitionIndex = (int)(longIndex >>> StructuredArray.MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT);
        final int partitionOffset = (int)longIndex & StructuredArray.PARTITION_MASK;

        return longAddressableSubArrays[partitionIndex][partitionOffset];
    }

    /**
     * Get a StructuredArray Sub array at a supplied index in a StructuredArray
     *
     * OPTIMIZATION NOTE: Optimized JDK implementations may replace this implementation with a
     * faster access form (e.g. they may be able to derive the element reference directly from the
     * structuredArray reference without requiring a de-reference).
     */
    @SuppressWarnings("unchecked")
    StructuredArray<T> getSubArray(final int index) throws IllegalArgumentException {
        if (getDimensionCount() < 2) {
            throw new IllegalArgumentException("cannot call getSubArray() on single dimensional array");
        }

        return intAddressableSubArrays[index];
    }

    //
    //
    // Internal fields:
    //
    //

    private int getBodySize() {
        return unsafe.getInt(this, bodySizeOffset);
    }

    private void setBodySize(int bodySize) {
        unsafe.putInt(this, bodySizeOffset, bodySize);
    }

    int getDimensionCount() {
        return unsafe.getInt(this, dimensionCountOffset);
    }

    private void setDimensionCount(int dimensionCount) {
        unsafe.putInt(this, dimensionCountOffset, dimensionCount);
    }

    private long getDim0Length() {
        return unsafe.getLong(this, dim0LengthOffset);
    }

    private void setDim0Length(long dim0Length) {
        unsafe.putLong(this, dim0LengthOffset, dim0Length);
    }

    private long getDim1Length() {
        return unsafe.getLong(this, dim1LengthOffset);
    }

    private void setDim1Length(long dim1Length) {
        unsafe.putLong(this, dim1LengthOffset, dim1Length);
    }

    private long getDim2Length() {
        return unsafe.getLong(this, dim2LengthOffset);
    }

    private void setDim2Length(long dim2Length) {
        unsafe.putLong(this, dim2LengthOffset, dim2Length);
    }

    private long getDim3Length() {
        return unsafe.getLong(this, dim3LengthOffset);
    }

    private void setDim3Length(long dim3Length) {
        unsafe.putLong(this, dim3LengthOffset, dim3Length);
    }

    private long getDim0ElementSize() {
        return unsafe.getLong(this, dim0ElementSizeOffset);
    }

    private void setDim0ElementSize(long dim0ElementSize) {
        unsafe.putLong(this, dim0ElementSizeOffset, dim0ElementSize);
    }

    private long getDim1ElementSize() {
        return unsafe.getLong(this, dim1ElementSizeOffset);
    }

    private void setDim1ElementSize(long dim1ElementSize) {
        unsafe.putLong(this, dim1ElementSizeOffset, dim1ElementSize);
    }

    private long getDim2ElementSize() {
        return unsafe.getLong(this, dim2ElementSizeOffset);
    }

    private void setDim2ElementSize(long dim2ElementSize) {
        unsafe.putLong(this, dim2ElementSizeOffset, dim2ElementSize);
    }

    private long getDim3ElementSize() {
        return unsafe.getLong(this, dim3ElementSizeOffset);
    }

    private void setDim3ElementSize(long dim3ElementSize) {
        unsafe.putLong(this, dim3ElementSizeOffset, dim3ElementSize);
    }

    long[] getLengths() {
        return (long[]) unsafe.getObject(this, lengthsOffset);
    }

    private void setLengths(long[] lengths) {
        unsafe.putObject(this, lengthsOffset, lengths);
    }

    private long[] getElementSizes() {
        return (long[]) unsafe.getObject(this, elementSizesOffset);
    }

    private void setElementSizes(long[] elementSizes) {
        unsafe.putObject(this, elementSizesOffset, elementSizes);
    }

    @SuppressWarnings("unchecked")
    Class<T> getElementClass() {
        return (Class<T>) unsafe.getObject(this, elementClassOffset);
    }

    private void setElementClass(Class<T> elementClass) {
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
    private final long bodySizeOffset        = offset += 8;
    private final long dimensionCountOffset  = offset += 8;
    private final long dim0LengthOffset      = offset += 8;
    private final long dim0ElementSizeOffset = offset += 8;
    private final long dim1LengthOffset      = offset += 8;
    private final long dim1ElementSizeOffset = offset += 8;
    private final long dim2LengthOffset      = offset += 8;
    private final long dim2ElementSizeOffset = offset += 8;
    private final long dim3LengthOffset      = offset += 8;
    private final long dim3ElementSizeOffset = offset += 8;
    private final long lengthsOffset         = offset += 8;
    private final long elementSizesOffset    = offset += 8;
    private final long elementClassOffset    = offset += 8;

    // These are our hidden fields
//    private int bodySize;
//    private int dimensionCount;
//    private long dim0Length;
//    private long dim1Length;
//    private long dim2Length;
//    private long dim3Length;
//    private long dim0ElementSize;
//    private long dim1ElementSize;
//    private long dim2ElementSize;
//    private long dim3ElementSize;
//    private long[] lengths;
//    private long[] elementSizes;
//    private Class<T> elementClass;


//    private final int dimensionCount;
//
//    private final Class<T> elementClass;
//
//    private final long length;            // A cached lengths[0]
//
//    private final long[] lengths;
//
//    int getDimensionCount() {
//        return dimensionCount;
//    }
//
//    Class<T> getElementClass() {
//        return elementClass;
//    }
//
    long getLength() {
        return getDim0Length();
    }
//
//    long[] getLengths() {
//        return lengths;
//    }

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

    // Separated internal storage arrays by type for performance reasons, to avoid casting and checkcast at runtime.
    // Wrong dimension count gets (of the wrong type for the dimension depth) will result in NPEs rather
    // than class cast exceptions.

    private StructuredArray<T>[][] longAddressableSubArrays; // Used to store subArrays at indexes above Integer.MAX_VALUE
    private StructuredArray<T>[] intAddressableSubArrays;

    private T[][] longAddressableElements; // Used to store elements at indexes above Integer.MAX_VALUE
    private T[] intAddressableElements;


    @SuppressWarnings("unchecked")
    private void allocateInternalStorage(final int dimensionCount, final long length) {
        // Allocate internal storage:

        // Size int-addressable sub arrays:
        final int intLength = (int) Math.min(length, Integer.MAX_VALUE);
        // Size Subsequent partitions hold long-addressable-only sub arrays:
        final long extraLength = length - intLength;
        final int numFullPartitions = (int) (extraLength >>> MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT);
        final int lastPartitionSize = (int) extraLength & PARTITION_MASK;

        if (dimensionCount > 1) {
            // We have sub arrays, not elements:
            intAddressableElements = null;
            longAddressableElements = null;

            intAddressableSubArrays = new StructuredArray[intLength];
            longAddressableSubArrays = new StructuredArray[numFullPartitions + 1][];
            // full long-addressable-only partitions:
            for (int i = 0; i < numFullPartitions; i++) {
                longAddressableSubArrays[i] = new StructuredArray[MAX_EXTRA_PARTITION_SIZE];
            }
            // Last partition with leftover long-addressable-only size:
            longAddressableSubArrays[numFullPartitions] = new StructuredArray[lastPartitionSize];

        } else {
            // We have elements, not sub arrays:
            intAddressableSubArrays = null;
            longAddressableSubArrays = null;

            intAddressableElements = (T[]) new Object[intLength];
            longAddressableElements = (T[][]) new Object[numFullPartitions + 1][];
            // full long-addressable-only partitions:
            for (int i = 0; i < numFullPartitions; i++) {
                longAddressableElements[i] = (T[]) new Object[MAX_EXTRA_PARTITION_SIZE];
            }
            // Last partition with leftover long-addressable-only size:
            longAddressableElements[numFullPartitions] = (T[]) new Object[lastPartitionSize];
        }
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

    private void storeSubArrayInLocalStorageAtIndex(StructuredArray<T> subArray, long index0) {
        // place in proper internal storage location:
        if (index0 < Integer.MAX_VALUE) {
            intAddressableSubArrays[(int) index0] = subArray;
            return;
        }

        // Calculate index into long-addressable-only partitions:
        final long longIndex0 = (index0 - Integer.MAX_VALUE);
        final int partitionIndex = (int) (longIndex0 >>> MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT);
        final int partitionOffset = (int) longIndex0 & PARTITION_MASK;

        longAddressableSubArrays[partitionIndex][partitionOffset] = subArray;
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

    static class ConstructorMagic {
        private boolean isActive() {
            return active;
        }

        private void setActive(boolean active) {
            this.active = active;
        }

        public void setArrayConstructionArgs(ArrayConstructionArgs arrayConstructorArgs) {
            this.arrayCtorAndArgs = arrayConstructorArgs.arrayCtorAndArgs;
            this.ctorAndArgsProvider = arrayConstructorArgs.ctorAndArgsProvider;
            this.lengths = arrayConstructorArgs.lengths;
            this.containingIndex = arrayConstructorArgs.containingIndex;
        }

        public CtorAndArgs getArrayCtorAndArgs() {
            return arrayCtorAndArgs;
        }

        public CtorAndArgsProvider getCtorAndArgsProvider() {
            return ctorAndArgsProvider;
        }

        public long[] getLengths() {
            return lengths;
        }

        public long[] getContainingIndex() {
            return containingIndex;
        }

        private boolean active = false;

        private CtorAndArgs arrayCtorAndArgs = null;
        private CtorAndArgsProvider ctorAndArgsProvider = null;
        private long[] lengths = null;
        private long[] containingIndex = null;
    }

    private static final ThreadLocal<ConstructorMagic> threadLocalConstructorMagic = new ThreadLocal<ConstructorMagic>();

    @SuppressWarnings("unchecked")
    static ConstructorMagic getConstructorMagic() {
        ConstructorMagic constructorMagic = threadLocalConstructorMagic.get();
        if (constructorMagic == null) {
            constructorMagic = new ConstructorMagic();
            threadLocalConstructorMagic.set(constructorMagic);
        }
        return constructorMagic;
    }

    @SuppressWarnings("unchecked")
    static void checkConstructorMagic() {
        final ConstructorMagic constructorMagic = threadLocalConstructorMagic.get();
        if ((constructorMagic == null) || !constructorMagic.isActive()) {
            throw new IllegalArgumentException("StructuredArray<> must not be directly instantiated with a constructor. Use newInstance(...) instead.");
        }
    }


    //
    //
    // Unsafe support:
    //
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

    long getInstanceSizeWhenContained(Class instanceClass) {
        // return unsafe.getInstanceSizeWhenContained(instanceClass);
        return 0;
    }

    long getInstanceSize(Class instanceClass) {
        // return unsafe.getInstanceSize(instanceClass);
        return 0;
    }
 }
