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

import org.mmtk.plan.Trace;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.Space;
import org.mmtk.policy.rcimmix.RCImmixObjectHeader;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;

import static org.mmtk.policy.rcimmix.RCImmixConstants.MARK_LINE_AT_SCAN_TIME;

/**
 * This class implements the thread-local functionality for a transitive
 * closure over an immix space.
 */
@Uninterruptible
public final class RCImmixConcurrentBTTraceLocal extends TraceLocal {

  /**
   * Constructor
   *
   * @param trace The trace associated with this trace local.
   * @param modBuffer The modified objects buffer associated with this trace local.  Possibly null.
   */
  public RCImmixConcurrentBTTraceLocal(Trace trace) {
    super(trace);
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    else if (Space.isInSpace(RCImmixConcurrent.REF_COUNT, object)) {
      return RCImmixConcurrent.rcSpace.fastIsLive(object);
    } else return RCImmixObjectHeader.isMarked(object);
  }

  /**
   * {@inheritDoc}<p>
   *
   * In this instance, we refer objects in the mark-sweep space to the
   * immixSpace for tracing, and defer to the superclass for all others.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (RCImmixConcurrent.isRCObject(object)) {
      if (Space.isInSpace(RCImmixConcurrent.REF_COUNT, object)) {
        return RCImmixConcurrent.rcSpace.fastTraceObjectAndLine(this, object);
      } else {
        return RCImmixConcurrent.rcSpace.fastTraceObject(this, object);
      }
    } else return object;
  }

  @Override
  @Inline
  protected void scanObject(ObjectReference object) {
    super.scanObject(object);
    if (MARK_LINE_AT_SCAN_TIME && Space.isInSpace(RCImmixConcurrent.REF_COUNT, object))
      RCImmixObjectHeader.testAndMarkLines(object);
  }

  /**
   * Ensure that the referenced object will not move from this point through
   * to the end of the collection. This can involve forwarding the object
   * if necessary.
   */
  @Override
  @Inline
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!RCImmixConcurrent.rcSpace.inImmixDefragCollection());
    return true;
  }
}
