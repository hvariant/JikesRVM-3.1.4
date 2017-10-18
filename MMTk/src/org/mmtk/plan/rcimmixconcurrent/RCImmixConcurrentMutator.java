/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.rcimmixconcurrent;


import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.LargeObjectLocal;
import org.mmtk.policy.Space;
import org.mmtk.policy.rcimmix.RCImmixMutatorLocal;
import org.mmtk.policy.rcimmix.RCImmixObjectHeader;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

import static org.mmtk.policy.rcimmix.RCImmixConstants.BYTES_IN_LINE;

/**
 * This class implements the mutator context for RCImmixConcurrent collector.
 */
@Uninterruptible
public class RCImmixConcurrentMutator extends StopTheWorldMutator {

  /************************************************************************
   * Instance fields
   */
  protected final RCImmixMutatorLocal rc;
  private final LargeObjectLocal rclos;
  private final ObjectReferenceDeque modBuffer;

  //MYNOTE:
  private final RCImmixConcurrentDecBuffer decBuffer0;
  private final RCImmixConcurrentDecBuffer decBuffer1;
  private RCImmixConcurrentDecBuffer decBuffer;

  /************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor. One instance is created per physical processor.
   */
  public RCImmixConcurrentMutator() {
    rc = new RCImmixMutatorLocal(RCImmixConcurrent.rcSpace, false);
    rclos = new LargeObjectLocal(RCImmixConcurrent.rcloSpace);
    modBuffer = new ObjectReferenceDeque("mod", global().modPool);

    //MYNOTE:
    decBuffer0 = new RCImmixConcurrentDecBuffer(global().decPool0);
    decBuffer1 = new RCImmixConcurrentDecBuffer(global().decPool1);
    decBuffer = global().currentDecPool == 0 ? decBuffer0 : decBuffer1;
  }

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    switch (allocator) {
      case RCImmixConcurrent.ALLOC_DEFAULT:
//        return rc.alloc(bytes, align, offset);
        Address ret = rc.alloc(bytes, align, offset);
//        Log.write("address allocated:"); Log.writeln(ret);
        return ret;
      case RCImmixConcurrent.ALLOC_LOS:
      case RCImmixConcurrent.ALLOC_PRIMITIVE_LOS:
      case RCImmixConcurrent.ALLOC_LARGE_CODE:
        return rclos.alloc(bytes, align, offset);
      case RCImmixConcurrent.ALLOC_NON_MOVING:
      case RCImmixConcurrent.ALLOC_CODE:
      case RCImmixConcurrent.ALLOC_IMMORTAL:
        return super.alloc(bytes, align, offset, allocator, site);
      default:
        VM.assertions.fail("Allocator not understood by RC");
        return Address.zero();
    }
  }

  @Override
  @Inline
  public void postAlloc(ObjectReference ref, ObjectReference typeRef, int bytes, int allocator) {
    switch (allocator) {
      case RCImmixConcurrent.ALLOC_DEFAULT:
        if (bytes > BYTES_IN_LINE) RCImmixObjectHeader.initializeHeader(ref);
        break;
      case RCImmixConcurrent.ALLOC_LOS:
      case RCImmixConcurrent.ALLOC_PRIMITIVE_LOS:
      case RCImmixConcurrent.ALLOC_LARGE_CODE:
        decBuffer.push(ref);
        RCImmixConcurrent.rcloSpace.initializeHeader(ref, true);
        RCImmixObjectHeader.initializeHeaderOther(ref, true);
        return;
      case RCImmixConcurrent.ALLOC_NON_MOVING:
      case RCImmixConcurrent.ALLOC_CODE:
      case RCImmixConcurrent.ALLOC_IMMORTAL:
        decBuffer.push(ref);
        RCImmixObjectHeader.initializeHeaderOther(ref, true);
        return;
      default:
        VM.assertions.fail("Allocator not understood by RC");
        return;
    }
  }

  @Override
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == RCImmixConcurrent.rcSpace) return rc;
    if (space == RCImmixConcurrent.rcloSpace) return rclos;
    return super.getAllocatorFromSpace(space);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Override
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == RCImmixConcurrent.PREPARE) {
      rc.prepare();
      return;
    }

    if (phaseId == RCImmixConcurrent.PROCESS_MODBUFFER) {
      modBuffer.flushLocal();
      return;
    }

    if (phaseId == RCImmixConcurrent.PROCESS_DECBUFFER) {
      decBuffer.flushLocal();
      return;
    }

    if (phaseId == RCImmixConcurrent.RELEASE) {
      rc.release();
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(modBuffer.isEmpty());
//      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(decBuffer.isEmpty());
      return;
    }

    //MYNOTE:
    if(phaseId == RCImmixConcurrent.SWITCH_DECPOOL){
      if(Options.verbose.getValue() > 0){
        Log.write("[MUT] switching to pool ");
        Log.writeln(global().currentDecPool);
      }
      decBuffer = global().currentDecPool == 0 ? decBuffer0 : decBuffer1;
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  @Override
  public final void flushRememberedSets() {
    decBuffer.flushLocal();
    modBuffer.flushLocal();
    assertRemsetsFlushed();
  }

  @Override
  public final void assertRemsetsFlushed() {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(decBuffer.isFlushed());
      VM.assertions._assert(modBuffer.isFlushed());
    }
  }

  @Override
  public void flush() {
    super.flush();
  }

  /****************************************************************************
   *
   * Write barriers.
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Inline
  public void objectReferenceWrite(ObjectReference src, Address slot,
                           ObjectReference tgt, Word metaDataA,
                           Word metaDataB, int mode) {
    if (RCImmixObjectHeader.logRequired(src)) {
      coalescingWriteBarrierSlow(src);
    }
    VM.barriers.objectReferenceWrite(src,tgt,metaDataA, metaDataB, mode);
  }

  @Override
  @Inline
  public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot,
                                               ObjectReference old, ObjectReference tgt, Word metaDataA,
                                               Word metaDataB, int mode) {
    if (RCImmixObjectHeader.logRequired(src)) {
      coalescingWriteBarrierSlow(src);
    }
    return VM.barriers.objectReferenceTryCompareAndSwap(src,old,tgt,metaDataA,metaDataB,mode);
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source of the values to be copied
   * @param srcOffset The offset of the first source address, in
   * bytes, relative to <code>src</code> (in principle, this could be
   * negative).
   * @param dst The mutated object, i.e. the destination of the copy.
   * @param dstOffset The offset of the first destination address, in
   * bytes relative to <code>tgt</code> (in principle, this could be
   * negative).
   * @param bytes The size of the region being copied, in bytes.
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  @Override
  @Inline
  public boolean objectReferenceBulkCopy(ObjectReference src, Offset srcOffset,
                              ObjectReference dst, Offset dstOffset, int bytes) {
    if (RCImmixObjectHeader.logRequired(dst)) {
      coalescingWriteBarrierSlow(dst);
    }
    return false;
  }

  /**
   * Slow path of the coalescing write barrier.
   *
   * <p> Attempt to log the source object. If successful in racing for
   * the log bit, push an entry into the modified buffer and add a
   * decrement buffer entry for each referent object (in the RC space)
   * before setting the header bit to indicate that it has finished
   * logging (allowing others in the race to continue).
   *
   * @param srcObj The object being mutated
   */
  @NoInline
  private void coalescingWriteBarrierSlow(ObjectReference srcObj) {
    if (RCImmixObjectHeader.attemptToLog(srcObj)) {
      modBuffer.push(srcObj);
      decBuffer.processChildren(srcObj);
      RCImmixObjectHeader.makeLogged(srcObj);
    }
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>RC</code> instance. */
  @Inline
  private static RCImmixConcurrent global() {
    return (RCImmixConcurrent) VM.activePlan.global();
  }
}
