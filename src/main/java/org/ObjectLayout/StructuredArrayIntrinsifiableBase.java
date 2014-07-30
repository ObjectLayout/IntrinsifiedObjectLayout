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

    // the existence of this field (not it's value) indicates that the class should be intrinsified:
    // (This allows the JVM to make this determination at load time, and not wait for initialization)
    private static final boolean existenceIndicatesIntrinsic = true;

    // Track initialization state:
    private boolean isInitialized = false;
    public volatile boolean constructionCompleted = false;

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

        // Finish consuming constructMagic arguments:
        constructorMagic.setActive(false);

        if (dimensionCount < 1) {
            throw new IllegalArgumentException("dimensionCount must be at least 1");
        }

        if (lengths[0] < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }

        // Calculate and populate elementSizes and paddingSizes:
        final long[] elementSizes = new long[dimensionCount];
        final long[] paddingSizes = new long[dimensionCount];
        long elementSizeWhenContained = getInstanceSizeWhenContained(elementClass);
        long elementSize = getInstanceSizeWhenContained(elementClass);
        elementSizes[dimensionCount - 1] = elementSizeWhenContained;
        paddingSizes[dimensionCount - 1] = getPrePaddingInObjectSize(elementSizeWhenContained) +
                elementSizeWhenContained - elementSize;
        for (int dim = dimensionCount - 2; dim >= 0; dim--) {
            long sizeWhenContained = getContainingObjectFootprintWhenContained(this.getClass(),
                    elementSizes[dim + 1], lengths[dim]);
            long size = getContainingObjectFootprint(this.getClass(), elementSizes[dim + 1], lengths[dim]);
            elementSizes[dim] = sizeWhenContained;
            // padding size is the sum of whatever pre padding this object size has and the difference
            // between it's WhenContained and it's un-contained sizes:
            paddingSizes[dim] = getPrePaddingInObjectSize(sizeWhenContained) + sizeWhenContained - size;
        }

        // initialize hidden fields:
        initBodySize((int) getInstanceSize(this.getClass()));
        initDimensionCount(dimensionCount);

        initDim0Length(lengths[0]);
        initDim0ElementSize(elementSizes[0]);
        initDim0PaddingSize(paddingSizes[0]);

        if (dimensionCount > 1) {
            initDim1Length(lengths[1]);
            initDim1ElementSize(elementSizes[1]);
            initDim1PaddingSize(paddingSizes[1]);
        }

        if (dimensionCount > 2) {
            initDim2Length(lengths[2]);
            initDim2ElementSize(elementSizes[2]);
            initDim2PaddingSize(paddingSizes[2]);
        }

        initLengths(lengths);
        initElementSizes(elementSizes);
        initPaddingizes(paddingSizes);

        initElementClass(elementClass);

        // TODO: replace "vanilla" internal storage with flat representation:
        allocateInternalStorage(dimensionCount, getLength());

        // Indicate construction is complete, such that further calls to initX() initializers of
        // hidden fields will fail from this point on.
        isInitialized = true;

        // follow update of internal boolean indication with a modification of a public volatile
        // to ensure ordering:
        constructionCompleted = true;
    }

    @Override
    public String toString() {
        final StringBuilder lengthsString = new StringBuilder("[");
        final StringBuilder elementSizesString = new StringBuilder("[");
        final StringBuilder paddingSizesString = new StringBuilder("[");

        lengthsString.append(getDim0Length());
        elementSizesString.append(getDim0ElementSize());
        paddingSizesString.append(getDim0PaddingSize());
        if (getDimensionCount() > 1) {
            lengthsString.append(", ").append(getDim1Length());
            elementSizesString.append(", ").append(getDim1ElementSize());
            paddingSizesString.append(", ").append(getDim1PaddingSize());
        }
        if (getDimensionCount() > 2) {
            lengthsString.append(", ").append(getDim2Length());
            elementSizesString.append(", ").append(getDim2ElementSize());
            paddingSizesString.append(", ").append(getDim2PaddingSize());
        }
        for (int i = 3; i < getDimensionCount(); i++) {
            lengthsString.append(", ").append(getLengths()[i]);
            elementSizesString.append(", ").append(getElementSizes()[i]);
            paddingSizesString.append(", ").append(getPaddingSizes()[i]);
        }
        lengthsString.append("]");
        elementSizesString.append("]");
        paddingSizesString.append("]");

        final StringBuilder output = new StringBuilder("StructuredArrayIntrinsifiableBase<");
        output.append(getElementClass().getName()).append(">").append(lengthsString);
        output.append(": sizes = ").append(elementSizesString).
                append(", paddings = ").append(paddingSizesString).
                append(", bodySize = ").append(getBodySize());

        return new String(output);
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
        constructorMagic.setConstructionArgs(ctorAndArgsProvider, lengths);

        // Calculate array size in the heap:
        final Class arrayClass = arrayCtorAndArgs.getConstructor().getDeclaringClass();
        long elementSize = getInstanceSizeWhenContained(ctorAndArgsProvider.getElementClass());
        for (int dimIndex = lengths.length - 2; dimIndex >= 0; dimIndex--) {
            elementSize = getContainingObjectFootprintWhenContained(arrayClass, elementSize, lengths[dimIndex]);
        }
        long size = getContainingObjectFootprint(arrayClass, elementSize, lengths[0]);

        try {
            constructorMagic.setActive(true);
            Constructor<S> arrayConstructor = arrayCtorAndArgs.getConstructor();
            arrayConstructor.setAccessible(true);
            // TODO: use allocateHeapForClass(arrayConstructor.getDeclaringClass(), size) to allocate room for array
            // TODO: replace constructor.newInstance() call with constructObjectAtOffset() call:
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
            throws InstantiationException, IllegalAccessException, InvocationTargetException
    {
        // TODO: replace constructor.newInstance() with constructObjectAtOffset() call:
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
        ConstructorMagic constructorMagic = getConstructorMagic();
        constructorMagic.setConstructionArgs(
                arrayConstructionArgs.ctorAndArgsProvider, arrayConstructionArgs.lengths);
        try {
            constructorMagic.setActive(true);
            // TODO: replace constructor.newInstance() with constructObjectAtOffset() call:
            StructuredArray<T> subArray = constructor.newInstance();
            storeSubArrayInLocalStorageAtIndex(subArray, index0);
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
        if (getDimensionCount() != 1) {
            throw new IllegalArgumentException("number of index parameters to get() must match array dimension count");
        }
        if ((index < 0) || (index > getDim0Length())) {
            throw new ArrayIndexOutOfBoundsException();
        }

        // TODO replace with direct access logic, along the lines of:
        // long offset = getBodySize() + (getDim0PaddingSize() + (index * getDim0ElementSize()));
        // return (T) deriveContainedObjectAtOffset(this, offset);

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
        if ((index0 < 0) || (index0 > getDim0Length()) ||
                (index1 < 0) || (index1 > getDim1Length())) {
            throw new ArrayIndexOutOfBoundsException();
        }
        // TODO replace with direct access logic, along the lines of:
        // long offset = getBodySize() +
        //      (getDim0PaddingSize() + (index0 * getDim0ElementSize())) +
        //      (getDim1PaddingSize() + (index1 * getDim1ElementSize()));
        // return (T) deriveContainedObjectAtOffset(this, offset);

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
        if ((index0 < 0) || (index0 > getDim0Length()) ||
                (index1 < 0) || (index1 > getDim1Length()) ||
                (index2 < 0) || (index2 > getDim2Length())) {
            throw new ArrayIndexOutOfBoundsException();
        }
        // TODO replace with direct access logic, along the lines of:
        // long offset = getBodySize() +
        //      (getDim0PaddingSize() + (index0 * getDim0ElementSize())) +
        //      (getDim1PaddingSize() + (index1 * getDim1ElementSize()));
        // return (T) deriveContainedObjectAtOffset(this, offset);

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
        if (getDimensionCount() != 1) {
            throw new IllegalArgumentException("number of index parameters to get() must match array dimension count");
        }
        if ((index < 0) || (index > getDim0Length())) {
            throw new ArrayIndexOutOfBoundsException();
        }

        // TODO replace with direct access logic, along the lines of:
        // long offset = getBodySize() + (getDim0PaddingSize() + (index * getDim0ElementSize()));
        // return (T) deriveContainedObjectAtOffset(this, offset);

        if (index < Integer.MAX_VALUE) {
            return get((int) index);
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
        if ((index0 < 0) || (index0 > getDim0Length()) ||
                (index1 < 0) || (index1 > getDim1Length())) {
            throw new ArrayIndexOutOfBoundsException();
        }

        // TODO replace with direct access logic, along the lines of:
        // long offset = getBodySize() +
        //      (getDim0PaddingSize() + (index0 * getDim0ElementSize())) +
        //      (getDim1PaddingSize() + (index1 * getDim1ElementSize()));
        // return (T) deriveContainedObjectAtOffset(this, offset);

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
        if ((index0 < 0) || (index0 > getDim0Length()) ||
                (index1 < 0) || (index1 > getDim1Length()) ||
                (index2 < 0) || (index2 > getDim2Length())) {
            throw new ArrayIndexOutOfBoundsException();
        }

        // TODO replace with direct access logic, along the lines of:
        // long offset = getBodySize() +
        //      (getDim0PaddingSize() + (index0 * getDim0ElementSize())) +
        //      (getDim1PaddingSize() + (index1 * getDim1ElementSize())) +
        //      (getDim2PaddingSize() + (index1 * getDim2ElementSize()));
        // return (T) deriveContainedObjectAtOffset(this, offset);

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
        if (getDimensionCount() < 2) {
            throw new IllegalArgumentException("cannot call getSubArrayL() on single dimensional array");
        }
        if ((index < 0) || (index > getDim0Length())) {
            throw new ArrayIndexOutOfBoundsException();
        }

        // TODO replace with direct access logic, along the lines of:
        // long offset = getBodySize() + (getDim0PaddingSize() + (index * getDim0ElementSize()));
        // return (StructuredArray<T>) deriveContainedObjectAtOffset(this, offset);

        if (index < Integer.MAX_VALUE) {
            return getSubArray((int) index);
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
        if ((index < 0) || (index > getDim0Length())) {
            throw new ArrayIndexOutOfBoundsException();
        }

        // TODO replace with direct access logic, along the lines of:
        // long offset = getBodySize() + (getDim0PaddingSize() + (index * getDim0ElementSize()));
        // return (StructuredArray<T>) deriveContainedObjectAtOffset(this, offset);

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

    private void initBodySize(int bodySize) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putInt(this, bodySizeOffset, bodySize);
    }

    int getDimensionCount() {
        return unsafe.getInt(this, dimensionCountOffset);
    }

    private void initDimensionCount(int dimensionCount) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putInt(this, dimensionCountOffset, dimensionCount);
    }

    private long getDim0Length() {
        return unsafe.getLong(this, dim0LengthOffset);
    }

    private void initDim0Length(long dim0Length) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, dim0LengthOffset, dim0Length);
    }

    private long getDim1Length() {
        return unsafe.getLong(this, dim1LengthOffset);
    }

    private void initDim1Length(long dim1Length) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, dim1LengthOffset, dim1Length);
    }

    private long getDim2Length() {
        return unsafe.getLong(this, dim2LengthOffset);
    }

    private void initDim2Length(long dim2Length) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, dim2LengthOffset, dim2Length);
    }

    private long getDim0ElementSize() {
        return unsafe.getLong(this, dim0ElementSizeOffset);
    }

    private void initDim0ElementSize(long dim0ElementSize) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, dim0ElementSizeOffset, dim0ElementSize);
    }

    private long getDim1ElementSize() {
        return unsafe.getLong(this, dim1ElementSizeOffset);
    }

    private void initDim1ElementSize(long dim1ElementSize) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, dim1ElementSizeOffset, dim1ElementSize);
    }

    private long getDim2ElementSize() {
        return unsafe.getLong(this, dim2ElementSizeOffset);
    }

    private long getDim0PaddingSize() {
        return unsafe.getLong(this, dim0PaddingSizeOffset);
    }

    private void initDim0PaddingSize(long dim0PaddingSize) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, dim0PaddingSizeOffset, dim0PaddingSize);
    }

    private long getDim1PaddingSize() {
        return unsafe.getLong(this, dim1PaddingSizeOffset);
    }

    private void initDim1PaddingSize(long dim1PaddingSize) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, dim1PaddingSizeOffset, dim1PaddingSize);
    }

    private void initDim2ElementSize(long dim2ElementSize) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, dim2ElementSizeOffset, dim2ElementSize);
    }

    private long getDim2PaddingSize() {
        return unsafe.getLong(this, dim2PaddingSizeOffset);
    }

    private void initDim2PaddingSize(long dim2PaddingSize) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putLong(this, dim2PaddingSizeOffset, dim2PaddingSize);
    }

    long[] getLengths() {
        return (long[]) unsafe.getObject(this, lengthsOffset);
    }

    private void initLengths(long[] lengths) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putObject(this, lengthsOffset, lengths);
    }

    private long[] getElementSizes() {
        return (long[]) unsafe.getObject(this, elementSizesOffset);
    }

    private void initElementSizes(long[] elementSizes) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putObject(this, elementSizesOffset, elementSizes);
    }

    private long[] getPaddingSizes() {
        return (long[]) unsafe.getObject(this, paddingSizesOffset);
    }

    private void initPaddingizes(long[] paddingSizes) {
        if (isInitialized) {
            throw new IllegalArgumentException("cannot change value after construction");
        }
        unsafe.putObject(this, paddingSizesOffset, paddingSizes);
    }

    @SuppressWarnings("unchecked")
    Class<T> getElementClass() {
        return (Class<T>) unsafe.getObject(this, elementClassOffset);
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
    private static final long dimensionCountOffset  = offset += 8;
    private static final long dim0LengthOffset      = offset += 8;
    private static final long dim0ElementSizeOffset = offset += 8;
    private static final long dim0PaddingSizeOffset = offset += 8;
    private static final long dim1LengthOffset      = offset += 8;
    private static final long dim1ElementSizeOffset = offset += 8;
    private static final long dim1PaddingSizeOffset = offset += 8;
    private static final long dim2LengthOffset      = offset += 8;
    private static final long dim2ElementSizeOffset = offset += 8;
    private static final long dim2PaddingSizeOffset = offset += 8;
    private static final long lengthsOffset         = offset += 8;
    private static final long elementSizesOffset    = offset += 8;
    private static final long paddingSizesOffset    = offset += 8;
    private static final long elementClassOffset    = offset += 8;

    // These are our hidden fields, supported by the JVM's special handling of this
    // class: They are only accessible via offsets and unsafe:

    //    private int bodySize;
    //    private int dimensionCount;
    //    private long dim0Length;
    //    private long dim0ElementSize;
    //    private long dim0PaddingSize;
    //    private long dim1Length;
    //    private long dim1ElementSize;
    //    private long dim1PaddingSize;
    //    private long dim2Length;
    //    private long dim2ElementSize;
    //    private long dim2PaddingSize;
    //    private long[] lengths;
    //    private long[] elementSizes;
    //    private long[] paddingSizes;
    //    private Class<T> elementClass;


    //  Removed private java fields and getters (from original vanilla version):

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

        public void setConstructionArgs(final CtorAndArgsProvider ctorAndArgsProvider, final long[] lengths) {
            this.ctorAndArgsProvider = ctorAndArgsProvider;
            this.lengths = lengths;
        }

        public CtorAndArgsProvider getCtorAndArgsProvider() {
            return ctorAndArgsProvider;
        }

        public long[] getLengths() {
            return lengths;
        }

        private boolean active = false;

        private CtorAndArgsProvider ctorAndArgsProvider = null;
        private long[] lengths = null;
    }

    private static final ThreadLocal<ConstructorMagic> threadLocalConstructorMagic = new ThreadLocal<ConstructorMagic>();

    @SuppressWarnings("unchecked")
    private static ConstructorMagic getConstructorMagic() {
        ConstructorMagic constructorMagic = threadLocalConstructorMagic.get();
        if (constructorMagic == null) {
            constructorMagic = new ConstructorMagic();
            threadLocalConstructorMagic.set(constructorMagic);
        }
        return constructorMagic;
    }

    @SuppressWarnings("unchecked")
    private static void checkConstructorMagic() {
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

    static long getInstanceSizeWhenContained(Class instanceClass) {
        // TODO: implement with something like:
        // return unsafe.getInstanceSizeWhenContained(instanceClass);
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
        return getInstanceSizeWhenContained(containerClass) + (numberOfElements * containedElementSize);
    }

    static long getContainingObjectFootprint(Class containerClass, long containedElementSize, long numberOfElements) {
        // TODO: implement with something like:
        // return unsafe.getStructuredArrayFootPrint(this.getClass(), containedElementSize, numberOfElements);
        return getInstanceSize(containerClass) + (numberOfElements * containedElementSize);
    }

    static long getPrePaddingInObjectSize(long objectSize) {
        // objectSize is inclusive of any padding.
        // TODO: implement with something like:
        // return unsafe.getPrePaddingInObjectSize(objectSize);
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

    static void constructObjectAtOffset(Object containingObject, long offset, Constructor c, Object... args) {
        // TODO: implement with something like:
        // unsafe.constructObjectAtOffset(containingObject, offset, c, args)
    }
 }
