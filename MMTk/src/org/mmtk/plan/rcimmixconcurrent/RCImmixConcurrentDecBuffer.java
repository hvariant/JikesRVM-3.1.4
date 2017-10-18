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

import org.mmtk.utility.deque.ObjectReferenceBuffer;
import org.mmtk.utility.deque.SharedDeque;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements a dec-buffer for RCImmixConcurrent collector
 *
 * @see org.mmtk.plan.TransitiveClosure
 */
@Uninterruptible
public final class RCImmixConcurrentDecBuffer extends ObjectReferenceBuffer {
  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param queue The shared deque that is used.
   */
  public RCImmixConcurrentDecBuffer(SharedDeque queue) {
    super("dec", queue);
  }

  @Override
  @Inline
  protected void process(ObjectReference object) {
    if (RCImmixConcurrent.isRCObject(object)) {
      push(object);
    }
  }
}
