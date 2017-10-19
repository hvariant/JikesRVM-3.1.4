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


import org.mmtk.plan.*;
import org.mmtk.policy.Space;
import org.mmtk.policy.rcimmix.RCImmixCollectorLocal;
import org.mmtk.policy.rcimmix.RCImmixObjectHeader;
import org.mmtk.utility.ForwardingWord;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.RCImmixAllocator;
import org.mmtk.utility.deque.AddressDeque;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Word;

/**
 * This class implements the collector context for RCImmixConcurrent collector.
 */
@Uninterruptible
public class RCImmixConcurrentCollector extends SimpleCollector {

  /************************************************************************
   * Initialization
   */
  protected final AddressDeque newRootPointerBuffer;
  protected final AddressDeque newRootPointerBackBuffer;
  public TraceLocal backupTrace;
  private final RCImmixConcurrentBTTraceLocal backTrace;
  private final RCImmixConcurrentBTDefragTraceLocal defragTrace;
  private final ObjectReferenceDeque modBuffer;
  private final ObjectReferenceDeque oldRootBuffer;

  //MYNOTE:
  private final RCImmixConcurrentDecBuffer decBuffer0;
  private final RCImmixConcurrentDecBuffer decBuffer1;
  private RCImmixConcurrentDecBuffer decBuffer;

  public final RCImmixConcurrentZero zero;
  protected RCImmixCollectorLocal rc;
  protected final RCImmixAllocator copy;
  protected final RCImmixAllocator young;
  private final RCImmixConcurrentRootSetTraceLocal rootTrace;
  private final RCImmixConcurrentModifiedProcessor modProcessor;

  //MYNOTE:
  private static volatile boolean performCycleCollection = false;

  /**
   * Constructor.
   */
  public RCImmixConcurrentCollector() {
    newRootPointerBuffer = new AddressDeque("new-root", global().newRootPool);
    newRootPointerBackBuffer = new AddressDeque("new-root-back", global().newRootBackPool);
    oldRootBuffer = new ObjectReferenceDeque("old-root", global().oldRootPool);

    //MYNOTE:
    decBuffer0 = new RCImmixConcurrentDecBuffer(global().decPool0);
    decBuffer1 = new RCImmixConcurrentDecBuffer(global().decPool1);

    modBuffer = new ObjectReferenceDeque("mod buf", global().modPool);
    backTrace = new RCImmixConcurrentBTTraceLocal(global().backupTrace);
    defragTrace = new RCImmixConcurrentBTDefragTraceLocal(global().backupTrace);
    zero = new RCImmixConcurrentZero();
    rc = new RCImmixCollectorLocal(RCImmixConcurrent.rcSpace);
    copy = new RCImmixAllocator(RCImmixConcurrent.rcSpace, true, true);
    young = new RCImmixAllocator(RCImmixConcurrent.rcSpace, true, false);
    rootTrace = new RCImmixConcurrentRootSetTraceLocal(global().rootTrace, newRootPointerBuffer);
    modProcessor = new RCImmixConcurrentModifiedProcessor(this);
  }

  /**
   * Get the modified processor to use.
   */
  protected final TransitiveClosure getModifiedProcessor() {
    return modProcessor;
  }

  /**
   * Get the root trace to use.
   */
  protected final TraceLocal getRootTrace() {
    return rootTrace;
  }

  /****************************************************************************
   *
   * Concurrent Collection
   */

  //MYNOTE:
  /** Perform some concurrent garbage collection */
  @Unpreemptible
  public void concurrentCollect() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!Plan.gcInProgress());
    short phaseId = Phase.getConcurrentPhaseId();
    concurrentCollectionPhase(phaseId);
  }

  //MYNOTE:
  /**
   * Perform some concurrent collection work.
   *
   * @param phaseId The unique phase identifier
   */
  @Unpreemptible
  public void concurrentCollectionPhase(short phaseId) {
    if (phaseId == RCImmixConcurrent.CONCURRENT) {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(!Plan.gcInProgress());
      }

      if(RCImmixConcurrent.VERBOSE && Options.verbose.getValue() > 0){
        Log.write("[CONC("); Log.write(getId()); Log.write(")] In CONCURRENT");
      }

      if(!performCycleCollection) {
        if(RCImmixConcurrent.VERBOSE && Options.verbose.getValue() > 0) {
          Log.write(" doing DecBuf ");
        }
        decBuffer = global().currentDecPool == 0 ? decBuffer1 : decBuffer0;
        processDecBuf(decBuffer);
      }

      if(RCImmixConcurrent.VERBOSE && Options.verbose.getValue() > 0){
        Log.writeln();
      }

      if(RCImmixConcurrent.VERBOSE && Options.verbose.getValue() > 0){
        Log.write("[CONC("); Log.write(getId()); Log.writeln(")] trying to terminate");
      }

      if (rendezvous() == 0) {
        if(RCImmixConcurrent.VERBOSE && Options.verbose.getValue() > 0){
          Log.write("[CONC("); Log.write(getId()); Log.writeln(")] terminating");
        }

        if (VM.VERIFY_ASSERTIONS && !performCycleCollection) {
          if(RCImmixConcurrent.VERBOSE && Options.verbose.getValue() > 0) {
            Log.write("[CONC(");
            Log.write(getId());
            Log.write(")] decBuffer");
            Log.write(decBuffer == decBuffer0 ? 0 : 1);
            Log.writeln(" should be empty");
          }
          VM.assertions._assert(decBuffer.isEmpty());
        }

//        if (!group.isAborted()) {
          /* We are responsible for ensuring termination. */
//          if (Options.verbose.getValue() >= 2) Log.writeln("[CONC]< requesting mutator flush >");
//          VM.collection.requestMutatorFlush();
//
//          if (Options.verbose.getValue() >= 2) Log.writeln("[CONC]< mutators flushed >");
//          Phase.notifyConcurrentPhaseComplete();
//        }
      }
//      rendezvous();
      return;
    }

    Log.write("Concurrent phase "); Log.write(Phase.getName(phaseId));
    Log.writeln(" not handled.");
    VM.assertions.fail("Concurrent phase not handled!");
  }

  /****************************************************************************
   *
   * Collection
   */

  //MYNOTE:
  @Override
  @Unpreemptible
  public void run() {
    while (true) {
      park();
      if (Plan.concurrentWorkers.isMember(this)) {
        concurrentCollect();
      } else {
        collect();
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void collect() {
    Phase.beginNewPhaseStack(Phase.scheduleComplex(global().collection));
  }

  @Override
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == RCImmixConcurrent.PREPARE) {
      rc.prepare(true);
      getRootTrace().prepare();
      if (RCImmixConcurrent.CC_BACKUP_TRACE && RCImmixConcurrent.performCycleCollection) {
        backupTrace = RCImmixConcurrent.rcSpace.inImmixDefragCollection() ? defragTrace : backTrace;
        backupTrace.prepare();
        copy.reset();
      } else {
        young.reset();
      }

      //MYNOTE:
      decBuffer = global().currentDecPool == 0 ? decBuffer0 : decBuffer1;
      performCycleCollection = RCImmixConcurrent.performCycleCollection;

      if(RCImmixConcurrent.VERBOSE && Options.verbose.getValue() > 0){
        Log.write("[COL] using decBuffer ");
        Log.writeln(global().currentDecPool);
      }

      return;
    }

    if (phaseId == RCImmixConcurrent.ROOTS) {
      VM.scanning.computeGlobalRoots(getCurrentTrace());
      VM.scanning.computeStaticRoots(getCurrentTrace());
      if (Plan.SCAN_BOOT_IMAGE && RCImmixConcurrent.performCycleCollection) {
        VM.scanning.computeBootImageRoots(getCurrentTrace());
      }
      return;
    }

    if (phaseId == RCImmixConcurrent.CLOSURE) {
      getRootTrace().completeTrace();
      newRootPointerBuffer.flushLocal();
      return;
    }

    if (phaseId == RCImmixConcurrent.PROCESS_OLDROOTBUFFER) {
      ObjectReference current;

      //boolean wrote = false;

      while(!(current = oldRootBuffer.pop()).isNull()) {
        //wrote = true;
        decBuffer.push(current);
      }

      //MYNOTE:
//      if(wrote && Options.verbose.getValue() > 3){
//        Log.write("+PROCESS_OLDROOTBUFFER: writing to pool=");
//        Log.writeln(decBuffer == decBuffer0 ? 0 : 1);
//      }
      return;
    }

    if (phaseId == RCImmixConcurrent.PROCESS_NEWROOTBUFFER) {
      ObjectReference current;
      Address address;
      while(!newRootPointerBuffer.isEmpty()) {
        address = newRootPointerBuffer.pop();
        current = address.loadObjectReference();
        if (RCImmixConcurrent.isRCObject(current)) {
          if (RCImmixConcurrent.CC_BACKUP_TRACE && RCImmixConcurrent.performCycleCollection) {
            if (RCImmixObjectHeader.incRC(current) == RCImmixObjectHeader.INC_NEW) {
              modBuffer.push(current);
            }
            newRootPointerBackBuffer.push(address);
          } else {
            if (RCImmixConcurrent.RC_SURVIVOR_COPY) {
              survivorCopy(address, current, true);
            } else {
              if (RCImmixObjectHeader.incRC(current) == RCImmixObjectHeader.INC_NEW) {
                if (Space.isInSpace(RCImmixConcurrent.REF_COUNT, current)) {
                  RCImmixObjectHeader.incLines(current);
                }
                modBuffer.push(current);
              }
              oldRootBuffer.push(current);
            }
          }
        }
      }
      modBuffer.flushLocal();
      if (RCImmixConcurrent.CC_BACKUP_TRACE && RCImmixConcurrent.performCycleCollection) {
        newRootPointerBackBuffer.flushLocal();
      } else {
        oldRootBuffer.flushLocal();
      }
      return;
    }

    if (phaseId == RCImmixConcurrent.PROCESS_MODBUFFER) {
      ObjectReference current;
      while(!(current = modBuffer.pop()).isNull()) {
        RCImmixObjectHeader.makeUnlogged(current);
        VM.scanning.scanObject(getModifiedProcessor(), current);
      }
      return;
    }

    //MYNOTE:
    if (phaseId == RCImmixConcurrent.PROCESS_DECBUFFER) {
      if(performCycleCollection) {
        //XXX:
        processDecBuf(decBuffer0);
        processDecBuf(decBuffer1);
      } else {
        decBuffer0.flushLocal();
        decBuffer1.flushLocal();
      }
      return;
    }

    if (phaseId == RCImmixConcurrent.BT_CLOSURE_INIT) {
      if (RCImmixConcurrent.CC_BACKUP_TRACE && RCImmixConcurrent.performCycleCollection) {
        ObjectReference current, newObject;
        Address address;
        while(!newRootPointerBackBuffer.isEmpty()) {
          address = newRootPointerBackBuffer.pop();
          current = address.loadObjectReference();
          if (RCImmixConcurrent.isRCObject(current)) {
            newObject = backupTrace.traceObject(current);
            if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!newObject.isNull());
            address.store(newObject);
          }
        }
      }
      return;
    }

    if (phaseId == RCImmixConcurrent.BT_CLOSURE) {
      if (RCImmixConcurrent.CC_BACKUP_TRACE && RCImmixConcurrent.performCycleCollection) {
        backupTrace.completeTrace();
      }

      return;
    }

    if (phaseId == RCImmixConcurrent.RELEASE) {
      if (RCImmixConcurrent.CC_BACKUP_TRACE && RCImmixConcurrent.performCycleCollection) {
        backupTrace.release();
      }
      getRootTrace().release();
      rc.release(true);
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(newRootPointerBuffer.isEmpty());
        VM.assertions._assert(modBuffer.isEmpty());
        if(performCycleCollection)
          VM.assertions._assert(decBuffer.isEmpty());
      }
      return;
    }

    //MYNOTE: what should we do here
    if (phaseId == RCImmixConcurrent.CONCURRENT_PREEMPT){
      if(RCImmixConcurrent.VERBOSE && Options.verbose.getValue() > 0) {
        Log.write("[CONC(");
        Log.write(getId());
        Log.write(")] In CONCURRENT_PREEMPT");
        Log.writeln();
      }

      if(!performCycleCollection) {
        processDecBuf(decBuffer);
        if (VM.VERIFY_ASSERTIONS) {
          if(RCImmixConcurrent.VERBOSE && Options.verbose.getValue() > 0) {
            Log.write(decBuffer == decBuffer0 ? 0 : 1);
            Log.writeln(" should be empty");
          }
          VM.assertions._assert(decBuffer.isEmpty());
        }
      }

      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  @Inline
  private void processDecCycle(RCImmixConcurrentDecBuffer decBuffer){ // in STW collector thread
    ObjectReference current;
    while (!(current = decBuffer.pop()).isNull()) {
      if (RCImmixObjectHeader.isNew(current)) {
        if (Space.isInSpace(RCImmixConcurrent.REF_COUNT_LOS, current)) {
          RCImmixConcurrent.rcloSpace.free(current);
        } else if (Space.isInSpace(RCImmixConcurrent.IMMORTAL, current)) {
          VM.scanning.scanObject(zero, current);
        }
      }
    }
  }

  @Inline
  private void processDecBuf(RCImmixConcurrentDecBuffer decBuffer){ // in concurrent collector thread
    SharedDeque curDecPool = decBuffer == decBuffer0 ? global().decPool0 : global().decPool1;

    if (RCImmixConcurrent.VERBOSE && Options.verbose.getValue() > 0) {
      Log.write("processDecBuf using pool=");
      Log.write(decBuffer == decBuffer0 ? 0 : 1);
      Log.write(" with enqueued page:");
      Log.write(curDecPool.enqueuedPages());
      Log.write(" performCycleCollection=");
      Log.write(performCycleCollection);
      Log.write(" RCImmixConcurrent.performCycleCollection=");
      Log.writeln(RCImmixConcurrent.performCycleCollection);
    }

    ObjectReference current;
    if(RCImmixConcurrent.CC_BACKUP_TRACE && performCycleCollection) {
      while (!(current = decBuffer.pop()).isNull()) {
        //MYNOTE: this will cause issues!!!
//      while (curDecPool.enqueuedPages() > 0 && !(current = decBuffer.pop()).isNull()) {
        if (RCImmixObjectHeader.isNew(current)) {
          if (Space.isInSpace(RCImmixConcurrent.REF_COUNT_LOS, current)) {
            RCImmixConcurrent.rcloSpace.free(current);
          } else if (Space.isInSpace(RCImmixConcurrent.IMMORTAL, current)) {
            VM.scanning.scanObject(zero, current);
          }
        }
      }
    } else {
      while (!(current = decBuffer.pop()).isNull()) {
        //MYNOTE: this will cause issues!!!
//      while (curDecPool.enqueuedPages() > 0 && !(current = decBuffer.pop()).isNull()) {
        if (RCImmixObjectHeader.isNew(current)) {
          if (Space.isInSpace(RCImmixConcurrent.REF_COUNT_LOS, current)) {
            RCImmixConcurrent.rcloSpace.free(current);
          } else if (Space.isInSpace(RCImmixConcurrent.IMMORTAL, current)) {
            VM.scanning.scanObject(zero, current);
          }
        } else {
          if (RCImmixObjectHeader.decRC(current) == RCImmixObjectHeader.DEC_KILL) {
            decBuffer.processChildren(current);
            if (Space.isInSpace(RCImmixConcurrent.REF_COUNT, current)) {
              RCImmixObjectHeader.decLines(current);
            } else if (Space.isInSpace(RCImmixConcurrent.REF_COUNT_LOS, current)) {
              RCImmixConcurrent.rcloSpace.free(current);
            } else if (Space.isInSpace(RCImmixConcurrent.IMMORTAL, current)) {
              VM.scanning.scanObject(zero, current);
            }
          }
        }
      }
    }
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>RC</code> instance. */
  @Inline
  protected static RCImmixConcurrent global() {
    return (RCImmixConcurrent) VM.activePlan.global();
  }

  @Override
  public final TraceLocal getCurrentTrace() {
    return getRootTrace();
  }

  /** @return The current modBuffer instance. */
  @Inline
  public final ObjectReferenceDeque getModBuffer() {
    return modBuffer;
  }

   /****************************************************************************
   *
   * Collection-time allocation
   */

   /**
    * {@inheritDoc}
    */
   @Override
   @Inline
   public Address allocCopy(ObjectReference original, int bytes,
       int align, int offset, int allocator) {
     if (VM.VERIFY_ASSERTIONS) {
       VM.assertions._assert(bytes <= Plan.MAX_NON_LOS_COPY_BYTES);
       VM.assertions._assert(allocator == RCImmixConcurrent.ALLOC_DEFAULT);
     }
     if (performCycleCollection && RCImmixConcurrent.rcSpace.inImmixDefragCollection()) {
       return copy.alloc(bytes, align, offset);
     } else return young.alloc(bytes, align, offset);
   }


   @Override
   @Inline
   public void postCopy(ObjectReference object, ObjectReference typeRef,
       int bytes, int allocator) {
     if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(allocator == RCImmixConcurrent.ALLOC_DEFAULT);
     if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Space.isInSpace(RCImmixConcurrent.REF_COUNT, object));
     if (performCycleCollection && RCImmixConcurrent.rcSpace.inImmixDefragCollection()) {
       RCImmixConcurrent.rcSpace.postCopy(object, bytes);

       if (VM.VERIFY_ASSERTIONS) {
         VM.assertions._assert(backupTrace.isLive(object));
         VM.assertions._assert(backupTrace.willNotMoveInCurrentCollection(object));
       }
     } else {
       RCImmixConcurrent.rcSpace.postCopyYoungObject(object, bytes);
     }
   }

   @Inline
   public void survivorCopy(Address slot, ObjectReference object, boolean root) {
     if (Space.isInSpace(RCImmixConcurrent.REF_COUNT, object)) {
       // Race to be the (potential) forwarder
       Word priorStatusWord = ForwardingWord.attemptToForward(object);
       if (ForwardingWord.stateIsForwardedOrBeingForwarded(priorStatusWord)) {
         // We lost the race; the object is either forwarded or being forwarded by another thread.
         ObjectReference rtn = ForwardingWord.spinAndGetForwardedObject(object, priorStatusWord);
         RCImmixObjectHeader.incRCOld(rtn);
         slot.store(rtn);
         if (root) oldRootBuffer.push(rtn);
       } else {
         byte priorState = (byte) (priorStatusWord.toInt() & 0xFF);
         // the object is unforwarded, either because this is the first thread to reach it, or because the object can't be forwarded
         if (!RCImmixObjectHeader.isHeaderNew(priorStatusWord)) {
           // the object has not been forwarded, but has the correct new state; unlock and return unmoved object
           RCImmixObjectHeader.returnToPriorState(object, priorState); // return to uncontested state
           RCImmixObjectHeader.incRCOld(object);
           if (root) oldRootBuffer.push(object);
         } else {
           if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(RCImmixObjectHeader.isNew(object));
           // we are the first to reach the object; forward it
           if (RCImmixObjectHeader.incRC(object) == RCImmixObjectHeader.INC_NEW) {
             // forward
             ObjectReference newObject;
             if (RCImmixConcurrent.rcSpace.exhaustedCopySpace || RCImmixObjectHeader.isPinnedObject(object)) {
               RCImmixObjectHeader.clearStateYoungObject(object);
               newObject = object;
             } else {
               newObject = ForwardingWord.forwardObject(object, Plan.ALLOC_DEFAULT);
             }
             slot.store(newObject);
             RCImmixObjectHeader.incLines(newObject);
             modBuffer.push(newObject);
             if (root) oldRootBuffer.push(newObject);
           }
         }
       }
     } else {
       if (RCImmixObjectHeader.incRC(object) == RCImmixObjectHeader.INC_NEW) {
         modBuffer.push(object);
       }
       if (root) oldRootBuffer.push(object);
     }
   }
}
