/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

//-#if RVM_WITH_OPT_COMPILER
import instructionFormats.*;
//-#endif

/**
 * Defines the JavaHeader portion of the object header for the 
 * default JikesRVM object model.
 * The default object model uses a two word header. <p>
 * 
 * One word holds a TIB pointer. <p>
 * 
 * The other word ("status word") contains an inline thin lock,
 * either the hash code or hash code state, and a few unallocated 
 * bits that can be used for other purposes. 
 * If {@link VM_JavaHeaderConstants#ADDRESS_BASED_HASHING} is false, 
 * then to implement default hashcodes, Jikes RVM uses a 10 bit hash code 
 * that is completely stored in the status word, which is laid out as 
 * shown below:
 * <pre>
 *      TTTT TTTT TTTT TTTT TTTT HHHH HHHH HHAA
 * T = thin lock bits
 * H = hash code 
 * A = available for use by GCHeader and/or MiscHeader.
 * </pre>
 * 
 * If {@link VM_JavaHeaderConstants#ADDRESS_BASED_HASHING ADDRESS_BASED_HASHING} is true, 
 * then Jikes RVM uses two bits of the status word to record the hash code state in
 * a typical three state scheme ({@link #HASH_STATE_UNHASHED}, {@link #HASH_STATE_HASHED},
 * and {@link #HASH_STATE_HASHED_AND_MOVED}). In this case, the status word is laid
 * out as shown below:
 * <pre>
 *      TTTT TTTT TTTT TTTT TTTT TTHH AAAA AAAA
 * T = thin lock bits
 * H = hash code state bits
 * A = available for use by GCHeader and/or MiscHeader.
 * </pre>
 * 
 * @author Bowen Alpern
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_JavaHeader implements VM_JavaHeaderConstants, 
					    VM_Uninterruptible 
					    //-#if RVM_WITH_OPT_COMPILER
					    ,OPT_Operators
					    //-#endif
{

  private static final int OTHER_HEADER_BYTES = VM_AllocatorHeader.NUM_BYTES_HEADER + VM_MiscHeader.NUM_BYTES_HEADER;
  // TIB + STATUS + OTHER_HEADER_BYTES
  private static final int SCALAR_HEADER_SIZE = 8 + OTHER_HEADER_BYTES;
  // SCALAR_HEADER + ARRAY LENGTH;
  private static final int ARRAY_HEADER_SIZE = SCALAR_HEADER_SIZE + 4;

  // note that the pointer to a scalar actually points 4 bytes above the
  // scalar object.
  private static final int SCALAR_PADDING_BYTES = 4;

  private static final int STATUS_OFFSET  = -8;
  private static final int TIB_OFFSET     = -12;

  private static final int AVAILABLE_BITS_OFFSET = VM.LITTLE_ENDIAN ? (STATUS_OFFSET) : (STATUS_OFFSET + 3);

  /*
   * Stuff for 10 bit header hash code in header
   */
  private static final int HASH_CODE_MASK  = 0x00000ffc;
  private static final int HASH_CODE_SHIFT = 2;
  private static int hashCodeGenerator; // seed for generating hash codes with copying collectors.

  /*
   * Stuff for address based hashing
   */
  private static final int HASH_STATE_UNHASHED         = 0x00000000;
  private static final int HASH_STATE_HASHED           = 0x00000100;
  private static final int HASH_STATE_HASHED_AND_MOVED = 0x00000300;
  private static final int HASH_STATE_MASK             = HASH_STATE_UNHASHED | HASH_STATE_HASHED | HASH_STATE_HASHED_AND_MOVED;
  private static final int HASHCODE_SCALAR_OFFSET      = -4; // in "phantom word"
  private static final int HASHCODE_ARRAY_OFFSET       = JAVA_HEADER_END - OTHER_HEADER_BYTES - 4; // to left of header
  private static final int HASHCODE_BYTES              = 4;

  
  /** How many bits are allocated to a thin lock? */
  public static final int NUM_THIN_LOCK_BITS = ADDRESS_BASED_HASHING ? 22 : 20;
  /** How many bits to shift to get the thin lock? */
  public static final int THIN_LOCK_SHIFT    = ADDRESS_BASED_HASHING ? 10 : 12;

  /**
   * How small is the minimum object header size? 
   * Used to pick chunk sizes for mark-sweep based collectors.
   */
  public static final int MINIMUM_HEADER_SIZE = SCALAR_HEADER_SIZE;

  static {
    if (VM.VerifyAssertions) {
      VM.assert(VM_MiscHeader.REQUESTED_BITS + VM_AllocatorHeader.REQUESTED_BITS <= NUM_AVAILABLE_BITS);
    }
  }

  /**
   * What is the offset of the 'last' byte in the class?
   * For use by VM_ObjectModel.layoutInstanceFields
   */
  public static int objectEndOffset(VM_Class klass) {
    return - klass.getInstanceSizeInternal() - SCALAR_PADDING_BYTES;
  }

  /**
   * Given a reference, return an address which is guaranteed to be inside
   * the memory region allocated to the object.
   */
  public static VM_Address getPointerInMemoryRegion(VM_Address ref) {
    return ref.add(TIB_OFFSET);
  }

  /**
   * Get the TIB for an object.
   */
  public static Object[] getTIB(Object o) { 
    return VM_Magic.getObjectArrayAtOffset(o, TIB_OFFSET);
  }
  
  /**
   * Set the TIB for an object.
   */
  public static void setTIB(Object ref, Object[] tib) {
    VM_Magic.setObjectAtOffset(ref, TIB_OFFSET, tib);
  }

  /**
   * Set the TIB for an object.
   */
  public static void setTIB(BootImageInterface bootImage, int refOffset, int tibAddr, VM_Type type) {
    bootImage.setAddressWord(refOffset + TIB_OFFSET, tibAddr);
  }

  /**
   * Process the TIB field during copyingGC
   */
  public static void gcProcessTIB(VM_Address ref) {
    VM_Allocator.processPtrField(ref.add(TIB_OFFSET));
  }

  /**
   * how many bytes are needed when the scalar object is copied by GC?
   */
  public static int bytesRequiredWhenCopied(Object fromObj, VM_Class type) {
    int size = type.getInstanceSize();
    if (ADDRESS_BASED_HASHING) {
      int hashState = VM_Magic.getIntAtOffset(fromObj, STATUS_OFFSET) & HASH_STATE_MASK;
      if (hashState != HASH_STATE_UNHASHED) {
	size += HASHCODE_BYTES;
      }
    }
    return size;
  }

  /**
   * how many bytes are needed when the array object is copied by GC?
   */
  public static int bytesRequiredWhenCopied(Object fromObj, VM_Array type, int numElements) {
    int size = (type.getInstanceSize(numElements) + 3) & ~3;
    if (ADDRESS_BASED_HASHING) {
      int hashState = VM_Magic.getIntAtOffset(fromObj, STATUS_OFFSET) & HASH_STATE_MASK;
      if (hashState != HASH_STATE_UNHASHED) {
	size += HASHCODE_BYTES;
      }
    }
    return size;
  }

  /**
   * Copy an object to the given raw storage address
   */
  public static Object moveObject(VM_Address toAddress, Object fromObj, 
				  int numBytes, VM_Class type, int availBitsWord) {
    VM_Magic.pragmaInline();
    if (ADDRESS_BASED_HASHING) {
      int hashState = availBitsWord & HASH_STATE_MASK;
      if (hashState == HASH_STATE_UNHASHED) {
	VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(numBytes + SCALAR_PADDING_BYTES);
	VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes); 
	Object toObj = VM_Magic.addressAsObject(toAddress.add(numBytes + SCALAR_PADDING_BYTES));
	VM_Magic.setIntAtOffset(toObj, STATUS_OFFSET, availBitsWord);
	return toObj;
      } else if (hashState == HASH_STATE_HASHED) {
	int data = numBytes - HASHCODE_BYTES;
	VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(data + SCALAR_PADDING_BYTES);
	VM_Memory.aligned32Copy(toAddress, fromAddress, data); 
	Object toObj = VM_Magic.addressAsObject(toAddress.add(data + SCALAR_PADDING_BYTES));
	VM_Magic.setIntAtOffset(toObj, HASHCODE_SCALAR_OFFSET, VM_Magic.objectAsAddress(fromObj).toInt());
	VM_Magic.setIntAtOffset(toObj, STATUS_OFFSET, availBitsWord | HASH_STATE_HASHED_AND_MOVED);
	if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition2++;
	return toObj;
      } else { // HASHED_AND_MOVED; 'phanton word' contains hash code.
	int offset = numBytes - HASHCODE_BYTES + SCALAR_PADDING_BYTES;
	VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(offset);
	VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes); 
	Object toObj = VM_Magic.addressAsObject(toAddress.add(offset));
	VM_Magic.setIntAtOffset(toObj, STATUS_OFFSET, availBitsWord);
	return toObj;
      }
    } else {
      int offset = numBytes + SCALAR_PADDING_BYTES;
      VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(offset);
      VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes);
      Object toObj = VM_Magic.addressAsObject(toAddress.add(numBytes + SCALAR_PADDING_BYTES));
      VM_Magic.setIntAtOffset(toObj, STATUS_OFFSET, availBitsWord);
      return toObj;
    }
  }

  /**
   * Copy an object to the given raw storage address
   */
  public static Object moveObject(VM_Address toAddress, Object fromObj, int numBytes, 
				  VM_Array type, int availBitsWord) {
    VM_Magic.pragmaInline();
    if (ADDRESS_BASED_HASHING) {
      int hashState = availBitsWord & HASH_STATE_MASK;
      if (hashState == HASH_STATE_UNHASHED) {
	VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(ARRAY_HEADER_SIZE);
	VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes); 
	Object toObj = VM_Magic.addressAsObject(toAddress.add(ARRAY_HEADER_SIZE));
	VM_Magic.setIntAtOffset(toObj, STATUS_OFFSET, availBitsWord);
	return toObj;
      } else if (hashState == HASH_STATE_HASHED) {
	VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(ARRAY_HEADER_SIZE);
	VM_Memory.aligned32Copy(toAddress.add(HASHCODE_BYTES), fromAddress, numBytes - HASHCODE_BYTES); 
	Object toObj = VM_Magic.addressAsObject(toAddress.add(ARRAY_HEADER_SIZE + HASHCODE_BYTES));
	VM_Magic.setIntAtOffset(toObj, HASHCODE_ARRAY_OFFSET, VM_Magic.objectAsAddress(fromObj).toInt());
	VM_Magic.setIntAtOffset(toObj, STATUS_OFFSET, availBitsWord | HASH_STATE_HASHED_AND_MOVED);
	if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition2++;
	return toObj;
      } else { // HASHED_AND_MOVED
	int offset = ARRAY_HEADER_SIZE + HASHCODE_BYTES;
	VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(offset);
	VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes); 
	Object toObj = VM_Magic.addressAsObject(toAddress.add(offset));
	VM_Magic.setIntAtOffset(toObj, STATUS_OFFSET, availBitsWord);
	return toObj;
      }
    } else {
      VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(ARRAY_HEADER_SIZE);
      VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes);
      Object toObj = VM_Magic.addressAsObject(toAddress.add(ARRAY_HEADER_SIZE));
      VM_Magic.setIntAtOffset(toObj, STATUS_OFFSET, availBitsWord);
      return toObj;
    }
  }

  /**
   * Get a reference to the TIB for an object.
   *
   * @param jdpService
   * @param address address of the object
   */
  public static VM_Address getTIB(JDPServiceInterface jdpService, VM_Address ptr) {
    return VM_Address.fromInt(jdpService.readMemory(ptr.add(TIB_OFFSET).toInt()));
  }

  /**
   * Get the hash code of an object.
   */
  public static int getObjectHashCode(Object o) { 
    if (ADDRESS_BASED_HASHING) {
      if (VM_Collector.MOVES_OBJECTS) {
	int hashState = VM_Magic.getIntAtOffset(o, STATUS_OFFSET) & HASH_STATE_MASK;
	if (hashState == HASH_STATE_HASHED) {
	  return VM_Magic.objectAsAddress(o).toInt() >>> 2;
	} else if (hashState == HASH_STATE_HASHED_AND_MOVED) {
	  VM_Type t = VM_Magic.getObjectType(o);
	  if (t.isArrayType()) {
	    return VM_Magic.getIntAtOffset(o, HASHCODE_ARRAY_OFFSET) >>> 2;
	  } else {
	    return VM_Magic.getIntAtOffset(o, HASHCODE_SCALAR_OFFSET) >>> 2;
	  }
	} else {
	  int tmp;
	  do {
	    tmp = VM_Magic.prepare(o, STATUS_OFFSET);
	  } while (!VM_Magic.attempt(o, STATUS_OFFSET, tmp, tmp | HASH_STATE_HASHED));
	  if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition1++;
	  return getObjectHashCode(o);
	}
      } else {
	return VM_Magic.objectAsAddress(o).toInt() >>> 2;
      }
    } else { // 10 bit hash code in status word
      int hashCode = (VM_Magic.getIntAtOffset(o, STATUS_OFFSET) & HASH_CODE_MASK) >> HASH_CODE_SHIFT;
      if (hashCode != 0) 
	return hashCode; 
      return installHashCode(o);
    }
  }
  
  /** Install a new hashcode (only used if !ADDRESS_BASED_HASHING) */
  private static int installHashCode(Object o) {
    VM_Magic.pragmaNoInline();
    int hashCode;
    do {
      hashCodeGenerator += (1 << HASH_CODE_SHIFT);
      hashCode = hashCodeGenerator & HASH_CODE_MASK;
    } while (hashCode == 0);
    while (true) {
      int statusWord = VM_Magic.prepare(o, STATUS_OFFSET);
      if ((statusWord & HASH_CODE_MASK) != 0) // some other thread installed a hashcode
	return (statusWord & HASH_CODE_MASK) >> HASH_CODE_SHIFT;
      if (VM_Magic.attempt(o, STATUS_OFFSET, statusWord, statusWord | hashCode))
	return hashCode >> HASH_CODE_SHIFT;  // we installed the hash code
    }
  }

  /**
   * Get the offset of the thin lock word in this object
   */
  public static int getThinLockOffset(Object o) {
    return STATUS_OFFSET;
  }

  /**
   * what is the default offset for a thin lock?
   */
  public static int defaultThinLockOffset() {
    return STATUS_OFFSET;
  }

  /**
   * Allocate a thin lock word for instances of the type
   * (if they already have one, then has no effect).
   */
  public static void allocateThinLock(VM_Type t) {
    // nothing to do (all objects have thin locks in this object model);
  }

  /**
   * Generic lock
   */
  public static void genericLock(Object o) { 
    VM_ThinLock.lock(o, STATUS_OFFSET);
  }

  /**
   * Generic unlock
   */
  public static void genericUnlock(Object o) {
    VM_ThinLock.unlock(o, STATUS_OFFSET);
  }

  /**
   * Obtains the heavy-weight lock, if there is one, associated with the
   * indicated object.  Returns <code>null</code>, if there is no
   * heavy-weight lock associated with the object.
   *
   * @param o the object from which a lock is desired
   * @param create if true, create heavy lock if none found
   * @return the heavy-weight lock on the object (if any)
   */
  public static VM_Lock getHeavyLock(Object o, boolean create) {
    return VM_ThinLock.getHeavyLock(o, STATUS_OFFSET, create);
  }

  /**
   * Non-atomic read of word containing available bits
   */
  public static int readAvailableBitsWord(Object o) {
    return VM_Magic.getIntAtOffset(o, STATUS_OFFSET);
  }

  /**
   * Non-atomic read of byte containing available bits
   */
  public static byte readAvailableBitsByte(Object o) {
    return VM_Magic.getByteAtOffset(o, AVAILABLE_BITS_OFFSET);
  }

  /**
   * Non-atomic write of word containing available bits
   */
  public static void writeAvailableBitsWord(Object o, int val) {
    VM_Magic.setIntAtOffset(o, STATUS_OFFSET, val);
  }

  /**
   * Non-atomic write of byte containing available bits
   */
  public static void writeAvailableBitsByte(Object o, byte val) {
    VM_Magic.setByteAtOffset(o, AVAILABLE_BITS_OFFSET, val);
  }

  /**
   * Return true if argument bit is 1, false if it is 0
   */
  public static boolean testAvailableBit(Object o, int idx) {
    return ((1 << idx) & VM_Magic.getIntAtOffset(o, STATUS_OFFSET)) != 0;
  }

  /**
   * Set argument bit to 1 if value is true, 0 if value is false
   */
  public static void setAvailableBit(Object o, int idx, boolean flag) {
    int status = VM_Magic.getIntAtOffset(o, STATUS_OFFSET);
    if (flag) {
      VM_Magic.setIntAtOffset(o, STATUS_OFFSET, status | (1 << idx));
    } else {
      VM_Magic.setIntAtOffset(o, STATUS_OFFSET, status & ~(1 << idx));
    }
  }

  /**
   * Freeze the other bits in the byte containing the available bits
   * so that it is safe to update them using setAvailableBits.
   */
  public static void initializeAvailableByte(Object o) {
    if (!ADDRESS_BASED_HASHING) getObjectHashCode(o);
  }

  /**
   * A prepare on the word containing the available bits
   */
  public static int prepareAvailableBits(Object o) {
    return VM_Magic.prepare(o, STATUS_OFFSET);
  }
  
  /**
   * An attempt on the word containing the available bits
   */
  public static boolean attemptAvailableBits(Object o, int oldVal, int newVal) {
    return VM_Magic.attempt(o, STATUS_OFFSET, oldVal, newVal);
  }
  
  /**
   * Given the smallest base address in a region, return the smallest
   * object reference that could refer to an object in the region.
   */
  public static VM_Address minimumObjectRef (VM_Address regionBaseAddr) {
    return regionBaseAddr.add(ARRAY_HEADER_SIZE);
  }

  /**
   * Given the largest base address in a region, return the largest
   * object reference that could refer to an object in the region.
   */
  public static VM_Address maximumObjectRef (VM_Address regionHighAddr) {
    return regionHighAddr.add(SCALAR_PADDING_BYTES);
  }

  /**
   * Compute the header size of an instance of the given type.
   */
  public static int computeScalarHeaderSize(VM_Class type) {
    return SCALAR_HEADER_SIZE;
  }

  /**
   * Compute the header size of an instance of the given type.
   */
  public static int computeArrayHeaderSize(VM_Array type) {
    return ARRAY_HEADER_SIZE;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static Object initializeScalarHeader(VM_Address ptr, Object[] tib, int size) {
    // (TIB set by VM_ObjectModel)
    Object ref = VM_Magic.addressAsObject(ptr.add(size + SCALAR_PADDING_BYTES));
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param bootImage the bootimage being written
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static int initializeScalarHeader(BootImageInterface bootImage, int ptr, 
					   Object[] tib, int size) {
    int ref = ptr + size + SCALAR_PADDING_BYTES;
    // (TIB set by BootImageWriter2)

    if (VM_Collector.NEEDS_WRITE_BARRIER) {
      // must set barrier bit for bootimage objects
      if (ADDRESS_BASED_HASHING) {
	bootImage.setFullWord(ref + STATUS_OFFSET, VM_AllocatorHeader.GC_BARRIER_BIT_MASK);
      } else {
	// Since the write barrier accesses the available bits bytes 
	// non-atomically we also need to initialize the hash code
	// to freeze the rest of the bits in the byte.
	int hashCode;
	do {
	  hashCodeGenerator += (1 << HASH_CODE_SHIFT);
	  hashCode = hashCodeGenerator & HASH_CODE_MASK;
	} while (hashCode == 0);
	bootImage.setFullWord(ref + STATUS_OFFSET, hashCode | VM_AllocatorHeader.GC_BARRIER_BIT_MASK);
      }
    }

    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static Object initializeArrayHeader(VM_Address ptr, Object[] tib, int size) {
    // (TIB and array length set by VM_ObjectModel)
    Object ref = VM_Magic.addressAsObject(ptr.add(ARRAY_HEADER_SIZE));
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param bootImage the bootimage being written
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static int initializeArrayHeader(BootImageInterface bootImage, int ptr, 
					  Object[] tib, int size) {
    int ref = ptr + ARRAY_HEADER_SIZE;
    // (TIB set by BootImageWriter2; array length set by VM_ObjectModel)

    if (VM_Collector.NEEDS_WRITE_BARRIER) {
      // must set barrier bit for bootimage objects
      if (ADDRESS_BASED_HASHING) {
	bootImage.setFullWord(ref + STATUS_OFFSET, VM_AllocatorHeader.GC_BARRIER_BIT_MASK);
      } else {
	// Since the write barrier accesses the available bits bytes 
	// non-atomically we also need to initialize the hash code
	// to freeze the rest of the bits in the byte.
	int hashCode;
	do {
	  hashCodeGenerator += (1 << HASH_CODE_SHIFT);
	  hashCode = hashCodeGenerator & HASH_CODE_MASK;
	} while (hashCode == 0);
	bootImage.setFullWord(ref + STATUS_OFFSET, hashCode | VM_AllocatorHeader.GC_BARRIER_BIT_MASK);
      }
    }

    return ref;
  }

  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(Object ref) {
    // TIB dumped in VM_ObjectModel
    VM.sysWrite(" STATUS=");
    VM.sysWriteHex(VM_Magic.getIntAtOffset(ref, STATUS_OFFSET));
  }


  /**
   * The following method will emit code that moves a reference to an
   * object's TIB into a destination register.
   *
   * @param asm the assembler object to emit code with
   * @param dest the number of the destination register
   * @param object the number of the register holding the object reference
   */
  //-#if RVM_FOR_POWERPC
  public static void baselineEmitLoadTIB(VM_Assembler asm, int dest, 
                                         int object) {
    asm.emitL(dest, TIB_OFFSET, object);
  }
  //-#elif RVM_FOR_IA32
  public static void baselineEmitLoadTIB(VM_Assembler asm, byte dest, 
                                         byte object) {
    asm.emitMOV_Reg_RegDisp(dest, object, TIB_OFFSET);
  }
  //-#endif

  //-#if RVM_WITH_OPT_COMPILER
  /**
   * Mutate a GET_OBJ_TIB instruction to the LIR
   * instructions required to implement it.
   * 
   * @param s the GET_OBJ_TIB instruction to lower
   * @param ir the enclosing OPT_IR
   */
  public static void lowerGET_OBJ_TIB(OPT_Instruction s, OPT_IR ir) {
    // TODO: valid location operand.
    OPT_Operand address = GuardedUnary.getClearVal(s);
    Load.mutate(s, INT_LOAD, GuardedUnary.getClearResult(s), 
                address, new OPT_IntConstantOperand(TIB_OFFSET), 
                null, GuardedUnary.getClearGuard(s));
  }
  //-#endif
}