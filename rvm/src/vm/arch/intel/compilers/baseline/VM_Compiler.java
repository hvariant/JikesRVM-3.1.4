/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * 
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 */
public class VM_Compiler extends VM_BaselineCompiler implements VM_BaselineConstants {

  private final int parameterWords;
  private int firstLocalOffset;

  /**
   * Create a VM_Compiler object for the compilation of method.
   */
  VM_Compiler(VM_BaselineCompiledMethod cm) {
    super(cm);
    stackHeights = new int[bytecodes.length];
    parameterWords = method.getParameterWords() + (method.isStatic() ? 0 : 1); // add 1 for this pointer
  }

  /**
   * The last true local
   */
  static int getEmptyStackOffset (VM_Method m) {
    return getFirstLocalOffset(m) - (m.getLocalWords()<<LG_WORDSIZE) + WORDSIZE;
  }

  /**
   * This is misnamed.  It should be getFirstParameterOffset.
   * It will not work as a base to access true locals.
   * TODO!! make sure it is not being used incorrectly
   */
  static int getFirstLocalOffset (VM_Method method) {
    if (method.getDeclaringClass().isBridgeFromNative())
      return STACKFRAME_BODY_OFFSET - (VM_JNICompiler.SAVED_GPRS_FOR_JNI << LG_WORDSIZE);
    else
      return STACKFRAME_BODY_OFFSET - (SAVED_GPRS << LG_WORDSIZE);
  }
  

  /*
   * implementation of abstract methods of VM_BaselineCompiler
   */

  /*
   * Misc routines not directly tied to a particular bytecode
   */

  /**
   * Emit the prologue for the method
   */
  protected final void emit_prologue() {
    genPrologue();
  }

  /**
   * Emit code to complete the dynamic linking of a
   * prematurely resolved VM_Type.
   * @param dictionaryId of type to link (if necessary)
   */
  protected final void emit_initializeClassIfNeccessary(int dictionaryId) {
    asm.emitMOV_Reg_Imm (T0, dictionaryId);
    asm.emitPUSH_Reg    (T0);
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.initializeClassIfNecessaryMethod.getOffset());
  }

  /**
   * Emit the code for a threadswitch tests (aka a yieldpoint).
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  protected final void emit_threadSwitchTest(int whereFrom) {
    genThreadSwitchTest(whereFrom);
  }

  /**
   * Emit the code to implement the spcified magic.
   * @param magicMethod desired magic
   */
  protected final void emit_Magic(VM_Method magicMethod) {
    genMagic(magicMethod);
  }


  /*
   * Loading constants
   */


  /**
   * Emit code to load the null constant.
   */
  protected final void emit_aconst_null() {
    asm.emitPUSH_Imm(0);
  }

  /**
   * Emit code to load an int constant.
   * @param val the int constant to load
   */
  protected final void emit_iconst(int val) {
    asm.emitPUSH_Imm(val);
  }

  /**
   * Emit code to load a long constant
   * @param val the lower 32 bits of long constant (upper32 are 0).
   */
  protected final void emit_lconst(int val) {
    asm.emitPUSH_Imm(0);  // high part
    asm.emitPUSH_Imm(val);  //  low part
  }

  /**
   * Emit code to load 0.0f
   */
  protected final void emit_fconst_0() {
    asm.emitPUSH_Imm(0);
  }

  /**
   * Emit code to load 1.0f
   */
  protected final void emit_fconst_1() {
    asm.emitPUSH_Imm(0x3f800000);
  }

  /**
   * Emit code to load 2.0f
   */
  protected final void emit_fconst_2() {
    asm.emitPUSH_Imm(0x40000000);
  }

  /**
   * Emit code to load 0.0d
   */
  protected final void emit_dconst_0() {
    asm.emitPUSH_Imm(0x00000000);
    asm.emitPUSH_Imm(0x00000000);
  }

  /**
   * Emit code to load 1.0d
   */
  protected final void emit_dconst_1() {
    asm.emitPUSH_Imm(0x3ff00000);
    asm.emitPUSH_Imm(0x00000000);
  }

  /**
   * Emit code to load a 32 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected final void emit_ldc(int offset) {
    asm.emitPUSH_RegDisp(JTOC, offset);   
  }

  /**
   * Emit code to load a 64 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected final void emit_ldc2(int offset) {
    asm.emitPUSH_RegDisp(JTOC, offset+4); // high 32 bits 
    asm.emitPUSH_RegDisp(JTOC, offset);   // low 32 bits
  }


  /*
   * loading local variables
   */


  /**
   * Emit code to load an int local variable
   * @param index the local index to load
   */
  protected final void emit_iload(int index) {
    int offset = localOffset(index);
    asm.emitPUSH_RegDisp(ESP,offset);
  }

  /**
   * Emit code to load a long local variable
   * @param index the local index to load
   */
  protected final void emit_lload(int index) {
    int offset = localOffset(index);
    asm.emitPUSH_RegDisp(ESP, offset); // high part
    asm.emitPUSH_RegDisp(ESP, offset); // low part (ESP has moved by 4!!)
  }

  /**
   * Emit code to local a float local variable
   * @param index the local index to load
   */
  protected final void emit_fload(int index) {
    int offset = localOffset(index);
    asm.emitPUSH_RegDisp (ESP, offset);
  }

  /**
   * Emit code to load a double local variable
   * @param index the local index to load
   */
  protected final void emit_dload(int index) {
    int offset = localOffset(index);
    asm.emitPUSH_RegDisp(ESP, offset); // high part
    asm.emitPUSH_RegDisp(ESP, offset); // low part (ESP has moved by 4!!)
  }

  /**
   * Emit code to load a reference local variable
   * @param index the local index to load
   */
  protected final void emit_aload(int index) {
    int offset = localOffset(index);
    asm.emitPUSH_RegDisp(ESP, offset);
  }


  /*
   * storing local variables
   */


  /**
   * Emit code to store an int to a local variable
   * @param index the local index to load
   */
  protected final void emit_istore(int index) {
    int offset = localOffset(index) - 4; // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp (ESP, offset); 
  }

  /**
   * Emit code to store a long to a local variable
   * @param index the local index to load
   */
  protected final void emit_lstore(int index) {
    int offset = localOffset(index+1) - 4; // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp(ESP, offset); // high part
    asm.emitPOP_RegDisp(ESP, offset); //  low part (ESP has moved by 4!!)
  }

  /**
   * Emit code to store a float to a local variable
   * @param index the local index to load
   */
  protected final void emit_fstore(int index) {
    int offset = localOffset(index) - 4; // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp (ESP, offset);
  }

  /**
   * Emit code to store an double  to a local variable
   * @param index the local index to load
   */
  protected final void emit_dstore(int index) {
    int offset = localOffset(index+1) - 4; // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp(ESP, offset); // high part
    asm.emitPOP_RegDisp(ESP, offset); //  low part (ESP has moved by 4!!)
  }

  /**
   * Emit code to store a reference to a local variable
   * @param index the local index to load
   */
  protected final void emit_astore(int index) {
    int offset = localOffset(index) - 4; // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp (ESP, offset);
  }


  /*
   * array loads
   */


  /**
   * Emit code to load from an int array
   */
  protected final void emit_iaload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);       // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);       // S0 is the array ref
    genBoundsCheck(asm, T0, S0);              // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);      // complete popping the 2 args
    asm.emitPUSH_RegIdx(S0, T0, asm.WORD, 0); // push desired int array element
  }

  /**
   * Emit code to load from a long array
   */
  protected final void emit_laload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);              // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);              // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);             // complete popping the 2 args
    asm.emitPUSH_RegIdx(S0, T0, asm.LONG, WORDSIZE); // load high part of desired long array element
    asm.emitPUSH_RegIdx(S0, T0, asm.LONG, 0);        // load low part of desired long array element
  }

  /**
   * Emit code to load from a float array
   */
  protected final void emit_faload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);       // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);       // S0 is the array ref
    genBoundsCheck(asm, T0, S0);              // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);      // complete popping the 2 args
    asm.emitPUSH_RegIdx(S0, T0, asm.WORD, 0); // push desired float array element
  }

  /**
   * Emit code to load from a double array
   */
  protected final void emit_daload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);              // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);              // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);             // complete popping the 2 args
    asm.emitPUSH_RegIdx(S0, T0, asm.LONG, WORDSIZE); // load high part of double
    asm.emitPUSH_RegIdx(S0, T0, asm.LONG, 0);        // load low part of double
  }

  /**
   * Emit code to load from a reference array
   */
  protected final void emit_aaload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);       // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);       // S0 is the array ref
    genBoundsCheck(asm, T0, S0);              // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);      // complete popping the 2 args
    asm.emitPUSH_RegIdx(S0, T0, asm.WORD, 0); // push desired object array element
  }

  /**
   * Emit code to load from a byte/boolean array
   */
  protected final void emit_baload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);                     // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);                     // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                            // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                    // complete popping the 2 args
    asm.emitMOVSX_Reg_RegIdx_Byte(T1, S0, T0, asm.BYTE, 0); // load byte and sign extend to a 32 bit word
    asm.emitPUSH_Reg(T1);                                   // push sign extended byte onto stack
  }

  /**
   * Emit code to load from a char array
   */
  protected final void emit_caload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);                      // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);                      // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                             // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                     // complete popping the 2 args
    asm.emitMOVZX_Reg_RegIdx_Word(T1, S0, T0, asm.SHORT, 0); // load halfword without sign extend to a 32 bit word
    asm.emitPUSH_Reg(T1);                                    // push char onto stack
  }

  /**
   * Emit code to load from a short array
   */
  protected final void emit_saload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);                      // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);                      // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                             // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                     // complete popping the 2 args
    asm.emitMOVSX_Reg_RegIdx_Word(T1, S0, T0, asm.SHORT, 0); // load halfword sign extend to a 32 bit word
    asm.emitPUSH_Reg(T1);                                    // push sign extended short onto stack
  }


  /*
   * array stores
   */


  /**
   * Emit code to store to an int array
   */
  protected final void emit_iastore() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);              // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);              // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);              // T1 is the int value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.WORD, 0, T1); // [S0 + T0<<2] <- T1
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);             // complete popping the 3 args
  }

  /**
   * Emit code to store to a long array
   */
  protected final void emit_lastore() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 8);                     // T0 is the array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 12);                    // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                            // T0 is index, S0 is address of array
    asm.emitPOP_Reg(T1);                                    // low part of long value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, 0, T1);        // [S0 + T0<<3 + 0] <- T1 store low part into array i.e.  
    asm.emitPOP_Reg(T1);                                    // high part of long value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, WORDSIZE, T1); // [S0 + T0<<3 + 4] <- T1 store high part into array i.e. 
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                    // remove index and ref from the stack
  }

  /**
   * Emit code to store to a float array
   */
  protected final void emit_fastore() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);              // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);              // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);              // T1 is the float value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.WORD, 0, T1); // [S0 + T0<<2] <- T1
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);             // complete popping the 3 args
  }

  /**
   * Emit code to store to a double array
   */
  protected final void emit_dastore() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 8);                     // T0 is the array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 12);                    // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                            // T0 is index, S0 is address of array
    asm.emitPOP_Reg(T1);                                    // low part of double value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, 0, T1);        // [S0 + T0<<3 + 0] <- T1 store low part into array i.e.  
    asm.emitPOP_Reg(T1);                                    // high part of double value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, WORDSIZE, T1); // [S0 + T0<<3 + 4] <- T1 store high part into array i.e. 
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                    // remove index and ref from the stack
  }

  /**
   * Emit code to store to a reference array
   */
  protected final void emit_aastore() {
    asm.emitPUSH_RegDisp(SP, 2<<LG_WORDSIZE);        // duplicate array ref
    asm.emitPUSH_RegDisp(SP, 1<<LG_WORDSIZE);        // duplicate object value
    genParameterRegisterLoad(2);                     // pass 2 parameter
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.checkstoreMethod.getOffset()); // checkstore(array ref, value)
    if (VM_Collector.NEEDS_WRITE_BARRIER) 
      VM_Barriers.compileArrayStoreBarrier(asm);
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);              // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);              // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);              // T1 is the object value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.WORD, 0, T1); // [S0 + T0<<2] <- T1
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);             // complete popping the 3 args
  }

  /**
   * Emit code to store to a byte/boolean array
   */
  protected final void emit_bastore() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);                   // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);                   // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                          // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);                   // T1 is the byte value
    asm.emitMOV_RegIdx_Reg_Byte(S0, T0, asm.BYTE, 0, T1); // [S0 + T0<<2] <- T1
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                  // complete popping the 3 args
  }

  /**
   * Emit code to store to a char array
   */
  protected final void emit_castore() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);                   // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);                   // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                          // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);                   // T1 is the char value
    asm.emitMOV_RegIdx_Reg_Word(S0, T0, asm.SHORT, 0, T1);// store halfword element into array i.e. [S0 +T0] <- T1 (halfword)
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                  // complete popping the 3 args
  }

  /**
   * Emit code to store to a short array
   */
  protected final void emit_sastore() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);                   // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);                   // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                          // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);                   // T1 is the short value
    asm.emitMOV_RegIdx_Reg_Word(S0, T0, asm.SHORT, 0, T1);// store halfword element into array i.e. [S0 +T0] <- T1 (halfword)
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                  // complete popping the 3 args
  }


  /*
   * expression stack manipulation
   */


  /**
   * Emit code to implement the pop bytecode
   */
  protected final void emit_pop() {
    asm.emitPOP_Reg(T0);
  }

  /**
   * Emit code to implement the pop2 bytecode
   */
  protected final void emit_pop2() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(T0);
  }

  /**
   * Emit code to implement the dup bytecode
   */
  protected final void emit_dup() {
    asm.emitMOV_Reg_RegInd (T0, SP);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the dup_x1 bytecode
   */
  protected final void emit_dup_x1() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the dup_x2 bytecode
   */
  protected final void emit_dup_x2() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T1);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the dup2 bytecode
   */
  protected final void emit_dup2() {
    asm.emitMOV_Reg_RegDisp (T0, SP, 4);
    asm.emitMOV_Reg_RegInd (S0, SP);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(S0);
  }

  /**
   * Emit code to implement the dup2_x1 bytecode
   */
  protected final void emit_dup2_x1() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the dup2_x2 bytecode
   */
  protected final void emit_dup2_x2() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T1);
    asm.emitPOP_Reg(JTOC);                  // JTOC is scratch register
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(JTOC);
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
    // restore JTOC register
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the swap bytecode
   */
  protected final void emit_swap() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(S0);
  }


  /*
   * int ALU
   */


  /**
   * Emit code to implement the iadd bytecode
   */
  protected final void emit_iadd() {
    asm.emitPOP_Reg(T0);
    asm.emitADD_RegInd_Reg(SP, T0);
  }

  /**
   * Emit code to implement the isub bytecode
   */
  protected final void emit_isub() {
    asm.emitPOP_Reg(T0);
    asm.emitSUB_RegInd_Reg(SP, T0);
  }

  /**
   * Emit code to implement the imul bytecode
   */
  protected final void emit_imul() {
    asm.emitPOP_Reg (T0);
    asm.emitIMUL2_Reg_RegInd(T0, SP);
    asm.emitMOV_RegInd_Reg (SP, T0);
  }

  /**
   * Emit code to implement the idiv bytecode
   */
  protected final void emit_idiv() {
    asm.emitMOV_Reg_RegDisp(ECX, SP, 0); // ECX is divisor; NOTE: can't use symbolic registers because of intel hardware requirements
    asm.emitMOV_Reg_RegDisp(EAX, SP, 4); // EAX is dividend
    asm.emitCDQ ();                      // sign extend EAX into EDX
    asm.emitIDIV_Reg_Reg(EAX, ECX);      // compute EAX/ECX - Quotient in EAX, remainder in EDX
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2); // complete popping the 2 values
    asm.emitPUSH_Reg(EAX);               // push result
  }

  /**
   * Emit code to implement the irem bytecode
   */
  protected final void emit_irem() {
    asm.emitMOV_Reg_RegDisp(ECX, SP, 0); // ECX is divisor; NOTE: can't use symbolic registers because of intel hardware requirements
    asm.emitMOV_Reg_RegDisp(EAX, SP, 4); // EAX is dividend
    asm.emitCDQ ();                      // sign extend EAX into EDX
    asm.emitIDIV_Reg_Reg(EAX, ECX);      // compute EAX/ECX - Quotient in EAX, remainder in EDX
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2); // complete popping the 2 values
    asm.emitPUSH_Reg(EDX);               // push remainder
  }

  /**
   * Emit code to implement the ineg bytecode
   */
  protected final void emit_ineg() {
    asm.emitNEG_RegInd(SP); // [SP] <- -[SP]
  }

  /**
   * Emit code to implement the ishl bytecode
   */
  protected final void emit_ishl() {
    asm.emitPOP_Reg(ECX);
    asm.emitSHL_RegInd_Reg(SP, ECX);   
  }

  /**
   * Emit code to implement the ishr bytecode
   */
  protected final void emit_ishr() {
    asm.emitPOP_Reg (ECX);
    asm.emitSAR_RegInd_Reg (SP, ECX);  
  }

  /**
   * Emit code to implement the iushr bytecode
   */
  protected final void emit_iushr() {
    asm.emitPOP_Reg (ECX);
    asm.emitSHR_RegInd_Reg(SP, ECX); 
  }

  /**
   * Emit code to implement the iand bytecode
   */
  protected final void emit_iand() {
    asm.emitPOP_Reg(T0);
    asm.emitAND_RegInd_Reg(SP, T0);
  }

  /**
   * Emit code to implement the ior bytecode
   */
  protected final void emit_ior() {
    asm.emitPOP_Reg(T0);
    asm.emitOR_RegInd_Reg (SP, T0);
  }

  /**
   * Emit code to implement the ixor bytecode
   */
  protected final void emit_ixor() {
    asm.emitPOP_Reg(T0);
    asm.emitXOR_RegInd_Reg(SP, T0);
  }

  /**
   * Emit code to implement the iinc bytecode
   * @param index index of local
   * @param val value to increment it by
   */
  protected final void emit_iinc(int index, int val) {
    int offset = localOffset(index);
    asm.emitADD_RegDisp_Imm(ESP, offset, val);
  }


  /*
   * long ALU
   */


  /**
   * Emit code to implement the ladd bytecode
   */
  protected final void emit_ladd() {
    asm.emitPOP_Reg(T0);                 // the low half of one long
    asm.emitPOP_Reg(S0);                 // the high half
    asm.emitADD_RegInd_Reg(SP, T0);          // add low halves
    asm.emitADC_RegDisp_Reg(SP, WORDSIZE, S0);   // add high halves with carry
  }

  /**
   * Emit code to implement the lsub bytecode
   */
  protected final void emit_lsub() {
    asm.emitPOP_Reg(T0);                 // the low half of one long
    asm.emitPOP_Reg(S0);                 // the high half
    asm.emitSUB_RegInd_Reg(SP, T0);          // subtract low halves
    asm.emitSBB_RegDisp_Reg(SP, WORDSIZE, S0);   // subtract high halves with borrow
  }

  /**
   * Emit code to implement the lmul bytecode
   */
  protected final void emit_lmul() {
    // 0: JTOC is used as scratch registers (see 14)
    // 1: load value1.low temp0, i.e., save value1.low
    // 2: eax <- temp0 eax is value1.low
    // 3: edx:eax <- eax * value2.low (product of the two low halves)
    // 4: store eax which is  result.low into place --> value1.low is destroyed
    // 5: temp1 <- edx which is the carry of the product of the low halves
    // aex and edx now free of results
    // 6: aex <- temp0 which is still value1.low
    // 7: pop into aex aex <- value2.low  --> value2.low is sort of destroyed
    // 8: edx:eax <- eax * value1.hi  (value2.low * value1.hi)
    // 9: temp1 += aex
    // 10: pop into eax; eax <- value2.hi -> value2.hi is sort of destroyed
    // 11: edx:eax <- eax * temp0 (value2.hi * value1.low)
    // 12: temp1 += eax  temp1 is now result.hi
    // 13: store result.hi
    // 14: restore JTOC
    if (VM.VerifyAssertions) VM.assert(S0 != EAX);
    if (VM.VerifyAssertions) VM.assert(S0 != EDX);
    asm.emitMOV_Reg_RegDisp (JTOC, SP, 8);          // step 1: JTOC is temp0
    asm.emitMOV_Reg_Reg (EAX, JTOC);            // step 2
    asm.emitMUL_Reg_RegInd(EAX, SP);    // step 3
    asm.emitMOV_RegDisp_Reg (SP, 8, EAX);           // step 4
    asm.emitMOV_Reg_Reg (S0, EDX);              // step 5: S0 is temp1
    asm.emitMOV_Reg_Reg (EAX, JTOC);            // step 6
    asm.emitPOP_Reg (EAX);                  // step 7: SP changed!
    asm.emitIMUL1_Reg_RegDisp(EAX, SP, 8);// step 8
    asm.emitADD_Reg_Reg (S0, EAX);      // step 9
    asm.emitPOP_Reg (EAX);                  // step 10: SP changed!
    asm.emitIMUL1_Reg_Reg(EAX, JTOC);    // step 11
    asm.emitADD_Reg_Reg (S0, EAX);      // step 12
    asm.emitMOV_RegDisp_Reg (SP, 4, S0);            // step 13
    // restore JTOC register
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the ldiv bytecode
   */
  protected final void emit_ldiv() {
    // (1) zero check
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);
    asm.emitOR_Reg_RegDisp(T0, SP, 4);
    VM_ForwardReference fr1 = asm.forwardJcc(asm.NE);
    asm.emitINT_Imm(VM_Runtime.TRAP_DIVIDE_BY_ZERO + RVM_TRAP_BASE);	// trap if divisor is 0
    fr1.resolve(asm);
    // (2) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (3) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+20);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+20);
    // (4) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysLongDivideIPField.getOffset());
    // (5) pop space for arguments
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);
    // (6) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (7) pop expression stack
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);
    // (8) push results
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the lrem bytecode
   */
  protected final void emit_lrem() {
    // (1) zero check
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);
    asm.emitOR_Reg_RegDisp(T0, SP, 4);
    VM_ForwardReference fr1 = asm.forwardJcc(asm.NE);
    asm.emitINT_Imm(VM_Runtime.TRAP_DIVIDE_BY_ZERO + RVM_TRAP_BASE);	// trap if divisor is 0
    fr1.resolve(asm);
    // (2) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (3) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+20);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+20);
    // (4) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysLongRemainderIPField.getOffset());
    // (5) pop space for arguments
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);
    // (6) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (7) pop expression stack
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);
    // (8) push results
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the lneg bytecode
   */
  protected final void emit_lneg() {
    asm.emitNEG_RegDisp(SP, 4);    // [SP+4] <- -[SP+4] or high <- -high
    asm.emitNEG_RegInd(SP);    // [SP] <- -[SP] or low <- -low
    asm.emitSBB_RegDisp_Imm(SP, 4, 0); // [SP+4] += borrow or high += borrow
  }

  /**
   * Emit code to implement the lshsl bytecode
   */
  protected final void emit_lshl() {
    if (VM.VerifyAssertions) VM.assert (ECX != T0); // ECX is constrained to be the shift count
    if (VM.VerifyAssertions) VM.assert (ECX != T1);
    if (VM.VerifyAssertions) VM.assert (ECX != JTOC);
    // 1: pop shift amount into JTOC (JTOC must be restored at the end)
    // 2: pop low half into T0
    // 3: pop high half into T1
    // 4: ECX <- JTOC, copy the shift count
    // 5: JTOC <- JTOC & 32 --> if 0 then shift amount is less than 32
    // 6: branch to step 12 if results is zero
    // the result is not zero --> the shift amount is greater than 32
    // 7: ECX <- ECX XOR JTOC   --> ECX is orginal shift amount minus 32
    // 8: T1 <- T0, or replace the high half with the low half.  This accounts for the 32 bit shift
    // 9: shift T1 left by ECX bits
    // 10: T0 <- 0
    // 11: branch to step 14
    // 12: shift left double from T0 into T1 by ECX bits.  T0 is unaltered
    // 13: shift left T0, the low half, also by ECX bits
    // 14: push high half from T1
    // 15: push the low half from T0
    // 16: restore the JTOC
    asm.emitPOP_Reg (JTOC);                 // original shift amount 6 bits
    asm.emitPOP_Reg (T0);                   // pop low half 
    asm.emitPOP_Reg (T1);                   // pop high half
    asm.emitMOV_Reg_Reg (ECX, JTOC);
    asm.emitAND_Reg_Imm (JTOC, 32);
    VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);
    asm.emitXOR_Reg_Reg (ECX, JTOC);
    asm.emitMOV_Reg_Reg (T1, T0);               // low replaces high
    asm.emitSHL_Reg_Reg (T1, ECX);
    asm.emitXOR_Reg_Reg (T0, T0);
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitSHLD_Reg_Reg_Reg(T1, T0, ECX);          // shift high half (step 12)
    asm.emitSHL_Reg_Reg (T0, ECX);                   // shift low half
    fr2.resolve(asm);
    asm.emitPUSH_Reg(T1);                   // push high half (step 14)
    asm.emitPUSH_Reg(T0);                   // push low half
    // restore JTOC
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the lshr bytecode
   */
  protected final void emit_lshr() {
    if (VM.VerifyAssertions) VM.assert (ECX != T0); // ECX is constrained to be the shift count
    if (VM.VerifyAssertions) VM.assert (ECX != T1);
    if (VM.VerifyAssertions) VM.assert (ECX != JTOC);
    // 1: pop shift amount into JTOC (JTOC must be restored at the end)
    // 2: pop low half into T0
    // 3: pop high half into T1
    // 4: ECX <- JTOC, copy the shift count
    // 5: JTOC <- JTOC & 32 --> if 0 then shift amount is less than 32
    // 6: branch to step 13 if results is zero
    // the result is not zero --> the shift amount is greater than 32
    // 7: ECX <- ECX XOR JTOC   --> ECX is orginal shift amount minus 32
    // 8: T0 <- T1, or replace the low half with the high half.  This accounts for the 32 bit shift
    // 9: shift T0 right arithmetic by ECX bits
    // 10: ECX <- 31
    // 11: shift T1 right arithmetic by ECX=31 bits, thus exending the sigh
    // 12: branch to step 15
    // 13: shift right double from T1 into T0 by ECX bits.  T1 is unaltered
    // 14: shift right arithmetic T1, the high half, also by ECX bits
    // 15: push high half from T1
    // 16: push the low half from T0
    // 17: restore JTOC
    asm.emitPOP_Reg (JTOC);                 // original shift amount 6 bits
    asm.emitPOP_Reg (T0);                   // pop low half 
    asm.emitPOP_Reg (T1);                   // pop high
    asm.emitMOV_Reg_Reg (ECX, JTOC);
    asm.emitAND_Reg_Imm (JTOC, 32);
    VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);
    asm.emitXOR_Reg_Reg (ECX, JTOC);
    asm.emitMOV_Reg_Reg (T0, T1);               // replace low with high
    asm.emitSAR_Reg_Reg (T0, ECX);                   // and shift it
    asm.emitMOV_Reg_Imm (ECX, 31);
    asm.emitSAR_Reg_Reg (T1, ECX);                   // set high half
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitSHRD_Reg_Reg_Reg(T0, T1, ECX);          // shift low half (step 13)
    asm.emitSAR_Reg_Reg (T1, ECX);                   // shift high half
    fr2.resolve(asm);
    asm.emitPUSH_Reg(T1);                   // push high half (step 15)
    asm.emitPUSH_Reg(T0);                   // push low half
    // restore JTOC
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the lushr bytecode
   */
  protected final void emit_lushr() {
    if (VM.VerifyAssertions) VM.assert (ECX != T0); // ECX is constrained to be the shift count
    if (VM.VerifyAssertions) VM.assert (ECX != T1);
    if (VM.VerifyAssertions) VM.assert (ECX != JTOC);
    // 1: pop shift amount into JTOC (JTOC must be restored at the end)
    // 2: pop low half into T0
    // 3: ECX <- JTOC, copy the shift count
    // 4: JTOC <- JTOC & 32 --> if 0 then shift amount is less than 32
    // 5: branch to step 11 if results is zero
    // the result is not zero --> the shift amount is greater than 32
    // 6: ECX <- ECX XOR JTOC   --> ECX is orginal shift amount minus 32
    // 7: pop high half into T0 replace the low half with the high 
    //        half.  This accounts for the 32 bit shift
    // 8: shift T0 right logical by ECX bits
    // 9: T1 <- 0                        T1 is the high half
    // 10: branch to step 14
    // 11: pop high half into T1
    // 12: shift right double from T1 into T0 by ECX bits.  T1 is unaltered
    // 13: shift right logical T1, the high half, also by ECX bits
    // 14: push high half from T1
    // 15: push the low half from T0
    // 16: restore JTOC
    asm.emitPOP_Reg(JTOC);                // original shift amount 6 bits
    asm.emitPOP_Reg(T0);                  // pop low half 
    asm.emitMOV_Reg_Reg(ECX, JTOC);
    asm.emitAND_Reg_Imm(JTOC, 32);
    VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);
    asm.emitXOR_Reg_Reg (ECX, JTOC);
    asm.emitPOP_Reg (T0);                   // replace low with high
    asm.emitSHR_Reg_Reg (T0, ECX);      // and shift it (count - 32)
    asm.emitXOR_Reg_Reg (T1, T1);               // high <- 0
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitPOP_Reg (T1);                   // high half (step 11)
    asm.emitSHRD_Reg_Reg_Reg(T0, T1, ECX);          // shift low half
    asm.emitSHR_Reg_Reg (T1, ECX);                   // shift high half
    fr2.resolve(asm);
    asm.emitPUSH_Reg(T1);                   // push high half (step 14)
    asm.emitPUSH_Reg(T0);                   // push low half
    // restore JTOC
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the land bytecode
   */
  protected final void emit_land() {
    asm.emitPOP_Reg(T0);        // low
    asm.emitPOP_Reg(S0);        // high
    asm.emitAND_RegInd_Reg(SP, T0);
    asm.emitAND_RegDisp_Reg(SP, 4, S0);
  }

  /**
   * Emit code to implement the lor bytecode
   */
  protected final void emit_lor() {
    asm.emitPOP_Reg(T0);        // low
    asm.emitPOP_Reg(S0);        // high
    asm.emitOR_RegInd_Reg(SP, T0);
    asm.emitOR_RegDisp_Reg(SP, 4, S0);
  }

  /**
   * Emit code to implement the lxor bytecode
   */
  protected final void emit_lxor() {
    asm.emitPOP_Reg(T0);        // low
    asm.emitPOP_Reg(S0);        // high
    asm.emitXOR_RegInd_Reg(SP, T0);
    asm.emitXOR_RegDisp_Reg(SP, 4, S0);
  }


  /*
   * float ALU
   */


  /**
   * Emit code to implement the fadd bytecode
   */
  protected final void emit_fadd() {
    asm.emitFLD_Reg_RegInd (FP0, SP);        // FPU reg. stack <- value2
    asm.emitFADD_Reg_RegDisp(FP0, SP, WORDSIZE); // FPU reg. stack += value1
    asm.emitPOP_Reg   (T0);           // discard 
    asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the fsub bytecode
   */
  protected final void emit_fsub() {
    asm.emitFLD_Reg_RegDisp (FP0, SP, WORDSIZE); // FPU reg. stack <- value1
    asm.emitFSUB_Reg_RegDisp(FP0, SP, 0);        // FPU reg. stack -= value2
    asm.emitPOP_Reg   (T0);           // discard 
    asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the fmul bytecode
   */
  protected final void emit_fmul() {
    asm.emitFLD_Reg_RegInd (FP0, SP);        // FPU reg. stack <- value2
    asm.emitFMUL_Reg_RegDisp(FP0, SP, WORDSIZE); // FPU reg. stack *= value1
    asm.emitPOP_Reg   (T0);           // discard 
    asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the fdiv bytecode
   */
  protected final void emit_fdiv() {
    asm.emitFLD_Reg_RegDisp (FP0, SP, WORDSIZE); // FPU reg. stack <- value1
    asm.emitFDIV_Reg_RegDisp(FP0, SP, 0);        // FPU reg. stack /= value2
    asm.emitPOP_Reg   (T0);           // discard 
    asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the frem bytecode
   */
  protected final void emit_frem() {
    asm.emitFLD_Reg_RegInd (FP0, SP);        // FPU reg. stack <- value2, or a
    asm.emitFLD_Reg_RegDisp (FP0, SP, WORDSIZE); // FPU reg. stack <- value1, or b
    asm.emitFPREM ();             // FPU reg. stack <- a%b
    asm.emitFSTP_RegDisp_Reg(SP, WORDSIZE, FP0); // POP FPU reg. stack (results) onto java stack
    asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto java stack
    asm.emitPOP_Reg   (T0);           // shrink the stack (T0 discarded)
  }

  /**
   * Emit code to implement the fneg bytecode
   */
  protected final void emit_fneg() {
    asm.emitFLD_Reg_RegInd (FP0, SP); // FPU reg. stack <- value1
    asm.emitFCHS  ();      // change sign to stop of FPU stack
    asm.emitFSTP_RegInd_Reg(SP, FP0); // POP FPU reg. stack onto stack
  }


  /*
   * double ALU
   */


  /**
   * Emit code to implement the dadd bytecode
   */
  protected final void emit_dadd() {
    asm.emitFLD_Reg_RegInd_Quad (FP0, SP);        // FPU reg. stack <- value2
    asm.emitFADD_Reg_RegDisp_Quad(FP0, SP, 8);        // FPU reg. stack += value1
    asm.emitADD_Reg_Imm(SP, 2*WORDSIZE);  // shrink the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);        // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the dsub bytecode
   */
  protected final void emit_dsub() {
    asm.emitFLD_Reg_RegDisp_Quad (FP0, SP, 8);          // FPU reg. stack <- value1
    asm.emitFSUB_Reg_RegDisp_Quad(FP0, SP, 0);          // FPU reg. stack -= value2
    asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);          // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the dmul bytecode
   */
  protected final void emit_dmul() {
    asm.emitFLD_Reg_RegInd_Quad (FP0, SP);          // FPU reg. stack <- value2
    asm.emitFMUL_Reg_RegDisp_Quad(FP0, SP, 8);          // FPU reg. stack *= value1
    asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);          // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the ddiv bytecode
   */
  protected final void emit_ddiv() {
    asm.emitFLD_Reg_RegDisp_Quad (FP0, SP, 8);          // FPU reg. stack <- value1
    asm.emitFDIV_Reg_RegInd_Quad(FP0, SP);          // FPU reg. stack /= value2
    asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);          // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the drem bytecode
   */
  protected final void emit_drem() {
    asm.emitFLD_Reg_RegInd_Quad (FP0, SP);          // FPU reg. stack <- value2, or a
    asm.emitFLD_Reg_RegDisp_Quad (FP0, SP, 2*WORDSIZE); // FPU reg. stack <- value1, or b
    asm.emitFPREM ();               // FPU reg. stack <- a%b
    asm.emitFSTP_RegDisp_Reg_Quad(SP, 2*WORDSIZE, FP0); // POP FPU reg. stack (result) onto java stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);         // POP FPU reg. stack onto java stack
    asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
  }

  /**
   * Emit code to implement the dneg bytecode
   */
  protected final void emit_dneg() {
    asm.emitFLD_Reg_RegInd_Quad (FP0, SP); // FPU reg. stack <- value1
    asm.emitFCHS  ();      // change sign to stop of FPU stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0); // POP FPU reg. stack onto stack
  }


  /*
   * conversion ops
   */


  /**
   * Emit code to implement the i2l bytecode
   */
  protected final void emit_i2l() {
    asm.emitPOP_Reg (EAX);
    asm.emitCDQ ();
    asm.emitPUSH_Reg(EDX);
    asm.emitPUSH_Reg(EAX);
  }

  /**
   * Emit code to implement the i2f bytecode
   */
  protected final void emit_i2f() {
    asm.emitFILD_Reg_RegInd(FP0, SP);
    asm.emitFSTP_RegInd_Reg(SP, FP0);
  }

  /**
   * Emit code to implement the i2d bytecode
   */
  protected final void emit_i2d() {
    asm.emitFILD_Reg_RegInd(FP0, SP);
    asm.emitPUSH_Reg(T0);             // grow the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
  }

  /**
   * Emit code to implement the l2i bytecode
   */
  protected final void emit_l2i() {
    asm.emitPOP_Reg (T0); // low half of the long
    asm.emitPOP_Reg (S0); // high half of the long
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the l2f bytecode
   */
  protected final void emit_l2f() {
    asm.emitFILD_Reg_RegInd_Quad(FP0, SP);
    asm.emitADD_Reg_Imm(SP, WORDSIZE);                // shrink the stack
    asm.emitFSTP_RegInd_Reg(SP, FP0);
  }

  /**
   * Emit code to implement the l2d bytecode
   */
  protected final void emit_l2d() {
    asm.emitFILD_Reg_RegInd_Quad(FP0, SP);
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
  }

  /**
   * Emit code to implement the f2i bytecode
   */
  protected final void emit_f2i() {
    // (1) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (2) Push arg to C function 
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE);
    // (3) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysFloatToIntIPField.getOffset());
    // (4) pop argument;
    asm.emitPOP_Reg(S0);
    // (5) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (6) put result on expression stack
    asm.emitMOV_RegDisp_Reg(SP, 0, T0);
  }

  /**
   * Emit code to implement the f2l bytecode
   */
  protected final void emit_f2l() {
    // (1) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (2) Push arg to C function 
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE);
    // (3) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysFloatToLongIPField.getOffset());
    // (4) pop argument;
    asm.emitPOP_Reg(S0);
    // (5) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (6) put result on expression stack
    asm.emitMOV_RegDisp_Reg(SP, 0, T1);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the f2d bytecode
   */
  protected final void emit_f2d() {
    asm.emitFLD_Reg_RegInd(FP0, SP);
    asm.emitSUB_Reg_Imm(SP, WORDSIZE);                // grow the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
  }

  /**
   * Emit code to implement the d2i bytecode
   */
  protected final void emit_d2i() {
    // (1) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (2) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    // (3) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysDoubleToIntIPField.getOffset());
    // (4) pop arguments
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(S0);
    // (5) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (6) put result on expression stack
    asm.emitPOP_Reg(S0); // shrink stack by 1 word
    asm.emitMOV_RegDisp_Reg(SP, 0, T0);
  }

  /**
   * Emit code to implement the d2l bytecode
   */
  protected final void emit_d2l() {
    // (1) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (2) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    // (3) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysDoubleToLongIPField.getOffset());
    // (4) pop arguments
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(S0);
    // (5) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (6) put result on expression stack
    asm.emitMOV_RegDisp_Reg(SP, 4, T1);
    asm.emitMOV_RegDisp_Reg(SP, 0, T0);
  }

  /**
   * Emit code to implement the d2f bytecode
   */
  protected final void emit_d2f() {
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
    asm.emitADD_Reg_Imm(SP, WORDSIZE);                // shrink the stack
    asm.emitFSTP_RegInd_Reg(SP, FP0);
  }

  /**
   * Emit code to implement the i2b bytecode
   */
  protected final void emit_i2b() {
    asm.emitPOP_Reg   (T0);
    asm.emitMOVSX_Reg_Reg_Byte(T0, T0);
    asm.emitPUSH_Reg  (T0);
  }

  /**
   * Emit code to implement the i2c bytecode
   */
  protected final void emit_i2c() {
    asm.emitPOP_Reg   (T0);
    asm.emitMOVZX_Reg_Reg_Word(T0, T0);
    asm.emitPUSH_Reg  (T0);
  }

  /**
   * Emit code to implement the i2s bytecode
   */
  protected final void emit_i2s() {
    asm.emitPOP_Reg   (T0);
    asm.emitMOVSX_Reg_Reg_Word(T0, T0);
    asm.emitPUSH_Reg  (T0);
  }


  /*
   * comparision ops
   */


  /**
   * Emit code to implement the lcmp bytecode
   */
  protected final void emit_lcmp() {
    asm.emitPOP_Reg(T0);        // the low half of value2
    asm.emitPOP_Reg(S0);        // the high half of value2
    asm.emitPOP_Reg(T1);        // the low half of value1
    asm.emitSUB_Reg_Reg(T1, T0);        // subtract the low half of value2 from
                                // low half of value1, result into T1
    asm.emitPOP_Reg(T0);        // the high half of value 1
    //  pop does not alter the carry register
    asm.emitSBB_Reg_Reg(T0, S0);        // subtract the high half of value2 plus
                                // borrow from the high half of value 1,
                                // result in T0
    asm.emitMOV_Reg_Imm(S0, -1);        // load -1 into S0
    VM_ForwardReference fr1 = asm.forwardJcc(asm.LT); // result negative --> branch to end
    asm.emitMOV_Reg_Imm(S0, 0);        // load 0 into S0
    asm.emitOR_Reg_Reg(T0, T1);        // result 0 
    VM_ForwardReference fr2 = asm.forwardJcc(asm.EQ); // result 0 --> branch to end
    asm.emitMOV_Reg_Imm(S0, 1);        // load 1 into S0
    fr1.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Reg(S0);        // push result on stack
  }

  /**
   * Emit code to implement the fcmpl bytecode
   */
  protected final void emit_fcmpl() {
    VM_ForwardReference fr1,fr2,fr3;
    asm.emitFLD_Reg_RegDisp(FP0, SP, WORDSIZE);          // copy value1 into FPU
    asm.emitFLD_Reg_RegInd(FP0, SP);                        // copy value2 into FPU
    asm.emitADD_Reg_Imm(SP, 2*WORDSIZE);                // popping the stack
    if (VM.VerifyAssertions) VM.assert(S0 != EAX);                        // eax is used by FNSTSW
    asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
    asm.emitFUCOMPP();                        // compare and pop FPU *2
    asm.emitFNSTSW();                     // move FPU flags into (E)AX
    asm.emitSAHF();                       // store AH into flags
    fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
    // ZF not set ->  neither equal nor unordered
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr1.resolve(asm);                        // ZF set (equal or unordered)
    fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr3.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Reg(S0);                        // push result on stack
  }

  /**
   * Emit code to implement the fcmpg bytecode
   */
  protected final void emit_fcmpg() {
    VM_ForwardReference fr1,fr2,fr3;
    asm.emitFLD_Reg_RegDisp(FP0, SP, WORDSIZE);          // copy value1 into FPU
    asm.emitFLD_Reg_RegInd(FP0, SP);                        // copy value2 into FPU
    asm.emitADD_Reg_Imm(SP, 2*WORDSIZE);                // popping the stack
    if (VM.VerifyAssertions) VM.assert(S0 != EAX);                        // eax is used by FNSTSW
    asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
    asm.emitFUCOMPP();                        // compare and pop FPU *2
    asm.emitFNSTSW();                     // move FPU flags into (E)AX
    asm.emitSAHF();                       // store AH into flags
    fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
    // ZF not set ->  neither equal nor unordered
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr1.resolve(asm);                        // ZF set (equal or unordered)
    fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr3.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Reg(S0);                        // push result on stack
  }

  /**
   * Emit code to implement the dcmpl bytecode
   */
  protected final void emit_dcmpl() {
    VM_ForwardReference fr1,fr2,fr3;
    asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, WORDSIZE*2);        // copy value1 into FPU
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);                        // copy value2 into FPU
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);                // popping the stack
    if (VM.VerifyAssertions) VM.assert(S0 != EAX);                        // eax is used by FNSTSW
    asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
    asm.emitFUCOMPP();                        // compare and pop FPU *2
    asm.emitFNSTSW();                     // move FPU flags into (E)AX
    asm.emitSAHF();                       // store AH into flags
    fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
    // ZF not set ->  neither equal nor unordered
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr1.resolve(asm);                        // ZF set (equal or unordered)
    fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr3.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Reg(S0);                        // push result on stack
  }

  /**
   * Emit code to implement the dcmpg bytecode
   */
  protected final void emit_dcmpg() {
    VM_ForwardReference fr1,fr2,fr3;
    asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, WORDSIZE*2);        // copy value1 into FPU
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);                        // copy value2 into FPU
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);                // popping the stack
    if (VM.VerifyAssertions) VM.assert(S0 != EAX);                        // eax is used by FNSTSW
    asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
    asm.emitFUCOMPP();                        // compare and pop FPU *2
    asm.emitFNSTSW();                     // move FPU flags into (E)AX
    asm.emitSAHF();                       // store AH into flags
    fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
    // ZF not set ->  neither equal nor unordered
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr1.resolve(asm);                        // ZF set (equal or unordered)
    fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr3.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Reg(S0);                        // push result on stack
  }


  /*
   * branching
   */


  /**
   * Emit code to implement the ifeg bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifeq(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
  }

  /**
   * Emit code to implement the ifne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifne(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    asm.emitJCC_Cond_ImmOrLabel(asm.NE, mTarget, bTarget);
  }

  /**
   * Emit code to implement the iflt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_iflt(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    asm.emitJCC_Cond_ImmOrLabel(asm.LT, mTarget, bTarget);
  }

  /**
   * Emit code to implement the ifge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifge(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    asm.emitJCC_Cond_ImmOrLabel(asm.GE, mTarget, bTarget);
  }

  /**
   * Emit code to implement the ifgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifgt(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    asm.emitJCC_Cond_ImmOrLabel(asm.GT, mTarget, bTarget);
  }

  /**
   * Emit code to implement the ifle bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifle(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    asm.emitJCC_Cond_ImmOrLabel(asm.LE, mTarget, bTarget);
  }

  /**
   * Emit code to implement the if_icmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpeq(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
  }

  /**
   * Emit code to implement the if_icmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpne(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    asm.emitJCC_Cond_ImmOrLabel(asm.NE, mTarget, bTarget);
  }

  /**
   * Emit code to implement the if_icmplt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmplt(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    asm.emitJCC_Cond_ImmOrLabel(asm.LT, mTarget, bTarget);
  }

  /**
   * Emit code to implement the if_icmpge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpge(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    asm.emitJCC_Cond_ImmOrLabel(asm.GE, mTarget, bTarget);
  }

  /**
   * Emit code to implement the if_icmpgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpgt(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    asm.emitJCC_Cond_ImmOrLabel(asm.GT, mTarget, bTarget);
  }

  /**
   * Emit code to implement the if_icmple bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmple(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    asm.emitJCC_Cond_ImmOrLabel(asm.LE, mTarget, bTarget);
  }

  /**
   * Emit code to implement the if_acmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpeq(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
  }

  /**
   * Emit code to implement the if_acmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpne(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    asm.emitJCC_Cond_ImmOrLabel(asm.NE, mTarget, bTarget);
  }

  /**
   * Emit code to implement the ifnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnull(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
  }

  /**
   * Emit code to implement the ifnonnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnonnull(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    asm.emitJCC_Cond_ImmOrLabel(asm.NE, mTarget, bTarget);
  }

  /**
   * Emit code to implement the goto and gotow bytecodes
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_goto(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitJMP_ImmOrLabel(mTarget, bTarget);
  }

  /**
   * Emit code to implement the jsr and jsrw bytecode
   * @param bTarget target bytecode of the jsr
   */
  protected final void emit_jsr(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitCALL_ImmOrLabel(mTarget, bTarget);
  }

  /**
   * Emit code to implement the ret bytecode
   * @param index local variable containing the return address
   */
  protected final void emit_ret(int index) {
    int offset = localOffset(index);
    asm.emitJMP_RegDisp(ESP, offset); 
  }

  /**
   * Emit code to implement the tableswitch bytecode
   * @param defaultval bcIndex of the default target
   * @param low low value of switch
   * @param high high value of switch
   */
  protected final void emit_tableswitch(int defaultval, int low, int high) {
    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    int n = high-low+1;                        // n = number of normal cases (0..n-1)
    asm.emitPOP_Reg (T0);                          // T0 is index of desired case
    asm.emitSUB_Reg_Imm(T0, low);                     // relativize T0
    asm.emitCMP_Reg_Imm(T0, n);                       // 0 <= relative index < n
    asm.emitJCC_Cond_ImmOrLabel (asm.LGE, mTarget, bTarget);   // if not, goto default case
    asm.emitCALL_Imm(asm.getMachineCodeIndex() + 5 + (n<<LG_WORDSIZE) ); 
    // jump around table, pushing address of 0th delta
    for (int i=0; i<n; i++) {                  // create table of deltas
      int offset = fetch4BytesSigned();
      bTarget = biStart + offset;
      mTarget = bytecodeMap[bTarget];
      // delta i: difference between address of case i and of delta 0
      asm.emitOFFSET_Imm_ImmOrLabel(i, mTarget, bTarget );
    }
    asm.emitPOP_Reg (S0);                          // S0 = address of 0th delta 
    asm.emitADD_Reg_RegIdx (S0, S0, T0, asm.WORD, 0);     // S0 += [S0 + T0<<2]
    asm.emitPUSH_Reg(S0);                          // push computed case address
    asm.emitRET ();                            // goto case
  }

  /**
   * Emit code to implement the lookupswitch bytecode.
   * Uses linear search, one could use a binary search tree instead,
   * but this is the baseline compiler, so don't worry about it.
   * 
   * @param defaultval bcIndex of the default target
   * @param npairs number of pairs in the lookup switch
   */
  protected final void emit_lookupswitch(int defaultval, int npairs) {
    asm.emitPOP_Reg(T0);
    for (int i=0; i<npairs; i++) {
      int match   = fetch4BytesSigned();
      asm.emitCMP_Reg_Imm(T0, match);
      int offset  = fetch4BytesSigned();
      int bTarget = biStart + offset;
      int mTarget = bytecodeMap[bTarget];
      asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
    }
    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    asm.emitJMP_ImmOrLabel(mTarget, bTarget);
  }


  /*
   * returns (from function; NOT ret)
   */


  /**
   * Emit code to implement the ireturn bytecode
   */
  protected final void emit_ireturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitPOP_Reg(T0);
    genEpilogue(4); 
  }

  /**
   * Emit code to implement the lreturn bytecode
   */
  protected final void emit_lreturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitPOP_Reg(T1); // low half
    asm.emitPOP_Reg(T0); // high half
    genEpilogue(8);
  }

  /**
   * Emit code to implement the freturn bytecode
   */
  protected final void emit_freturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitFLD_Reg_RegInd(FP0, SP);
    asm.emitADD_Reg_Imm(SP, WORDSIZE); // pop the stack
    genEpilogue(4);
  }

  /**
   * Emit code to implement the dreturn bytecode
   */
  protected final void emit_dreturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
    asm.emitADD_Reg_Imm(SP, WORDSIZE<<1); // pop the stack
    genEpilogue(8);
  }

  /**
   * Emit code to implement the areturn bytecode
   */
  protected final void emit_areturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitPOP_Reg(T0);
    genEpilogue(4); 
  }

  /**
   * Emit code to implement the return bytecode
   */
  protected final void emit_return() {
    if (method.isSynchronized()) genMonitorExit();
    genEpilogue(0); 
  }


  /*
   * field access
   */


  /**
   * Emit code to implement a dynamically linked getstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_getstatic(VM_Field fieldRef) {
    emitDynamicLinkingSequence(T0, fieldRef); 
    if (fieldRef.getSize() == 4) { 
      asm.emitPUSH_RegIdx (JTOC, T0, asm.BYTE, 0);        // get static field
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
      asm.emitPUSH_RegIdx (JTOC, T0, asm.BYTE, WORDSIZE); // get high part
      asm.emitPUSH_RegIdx (JTOC, T0, asm.BYTE, 0);        // get low part
    }
  }

  /**
   * Emit code to implement a getstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getstatic(VM_Field fieldRef) {
    int fieldOffset = fieldRef.getOffset();
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitPUSH_RegDisp(JTOC, fieldOffset);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
      if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexField.getOffset());
	asm.emitPUSH_Reg        (T0);
	VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
	asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorLockMethod.getOffset());
      }
      asm.emitPUSH_RegDisp(JTOC, fieldOffset+WORDSIZE); // get high part
      asm.emitPUSH_RegDisp(JTOC, fieldOffset);          // get low part
      if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexField.getOffset());
	asm.emitPUSH_Reg        (T0);
	VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
	asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorUnlockMethod.getOffset());
      }
    }
  }


  /**
   * Emit code to implement a dynamically linked putstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putstatic(VM_Field fieldRef) {
    if (VM_Collector.NEEDS_WRITE_BARRIER && !fieldRef.getType().isPrimitiveType()) {
      VM_Barriers.compileUnresolvedPutstaticBarrier(asm, fieldRef.getDictionaryId());
    }
    emitDynamicLinkingSequence(T0, fieldRef);
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitPOP_RegIdx(JTOC, T0, asm.BYTE, 0);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
      asm.emitPOP_RegIdx(JTOC, T0, asm.BYTE, 0);        // store low part
      asm.emitPOP_RegIdx(JTOC, T0, asm.BYTE, WORDSIZE); // store high part
    }
  }

  /**
   * Emit code to implement a putstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putstatic(VM_Field fieldRef) {
    int fieldOffset = fieldRef.getOffset();
    if (VM_Collector.NEEDS_WRITE_BARRIER && !fieldRef.getType().isPrimitiveType()) {
      VM_Barriers.compilePutstaticBarrier(asm, fieldOffset);
    }
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitPOP_RegDisp(JTOC, fieldOffset);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
      if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexField.getOffset());
	asm.emitPUSH_Reg        (T0);
	VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
	asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorLockMethod.getOffset());
      }
      asm.emitPOP_RegDisp(JTOC, fieldOffset);          // store low part
      asm.emitPOP_RegDisp(JTOC, fieldOffset+WORDSIZE); // store high part
      if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexField.getOffset());
	asm.emitPUSH_Reg        (T0);
	VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
	asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorUnlockMethod.getOffset());
      }
    }
  }


  /**
   * Emit code to implement a dynamically linked getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_getfield(VM_Field fieldRef) {
    emitDynamicLinkingSequence(T0, fieldRef);
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitMOV_Reg_RegDisp(S0, SP, 0);              // S0 is object reference
      asm.emitMOV_Reg_RegIdx(S0, S0, T0, asm.BYTE, 0); // S0 is field value
      asm.emitMOV_RegDisp_Reg(SP, 0, S0);              // replace reference with value on stack
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
      asm.emitMOV_Reg_RegDisp(S0, SP, 0);                     // S0 is object reference
      asm.emitMOV_Reg_RegIdx(T1, S0, T0, asm.BYTE, WORDSIZE); // T1 is high part of field value
      asm.emitMOV_RegDisp_Reg(SP, 0, T1);                     // replace reference with value on stack
      asm.emitPUSH_RegIdx(S0, T0, asm.BYTE, 0);               // push the low part of field value
    }
  }

  /**
   * Emit code to implement a getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getfield(VM_Field fieldRef) {
    int fieldOffset = fieldRef.getOffset();
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitMOV_Reg_RegDisp(T0, SP, 0);           // T0 is object reference
      asm.emitMOV_Reg_RegDisp(T0, T0, fieldOffset); // T0 is field value
      asm.emitMOV_RegDisp_Reg(SP, 0, T0);           // replace reference with value on stack
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
      if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexField.getOffset());
	asm.emitPUSH_Reg        (T0);
	VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
	asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorLockMethod.getOffset());
      }
      asm.emitMOV_Reg_RegDisp(T0, SP, 0);                    // T0 is object reference
      asm.emitMOV_Reg_RegDisp(T1, T0, fieldOffset+WORDSIZE); // T1 is high part of field value
      asm.emitMOV_RegDisp_Reg(SP, 0, T1);                    // replace reference with high part of value on stack
      asm.emitPUSH_RegDisp(T0, fieldOffset);                 // push low part of field value
      if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexField.getOffset());
	asm.emitPUSH_Reg        (T0);
	VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
	asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorUnlockMethod.getOffset());
      }
    }
  }


  /**
   * Emit code to implement a dynamically linked putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putfield(VM_Field fieldRef) {
    if (VM_Collector.NEEDS_WRITE_BARRIER && !fieldRef.getType().isPrimitiveType()) {
      VM_Barriers.compileUnresolvedPutfieldBarrier(asm, fieldRef.getDictionaryId());
    }
    emitDynamicLinkingSequence(T0, fieldRef);
    if (fieldRef.getSize() == 4) {// field is one word
      asm.emitMOV_Reg_RegDisp(T1, SP, 0);               // T1 is the value to be stored
      asm.emitMOV_Reg_RegDisp(S0, SP, 4);               // S0 is the object reference
      asm.emitMOV_RegIdx_Reg (S0, T0, asm.BYTE, 0, T1); // [S0+T0] <- T1
      asm.emitADD_Reg_Imm(SP, WORDSIZE*2);              // complete popping the value and reference
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
      asm.emitMOV_Reg_RegDisp(JTOC, SP, 0);                          // JTOC is low part of the value to be stored
      asm.emitMOV_Reg_RegDisp(T1, SP, 4);                            // T1 is high part of the value to be stored
      asm.emitMOV_Reg_RegDisp(S0, SP, 8);                            // S0 is the object reference
      asm.emitMOV_RegIdx_Reg (S0, T0, asm.BYTE, 0, JTOC);            // [S0+T0] <- JTOC
      asm.emitMOV_RegIdx_Reg (S0, T0, asm.BYTE, WORDSIZE, T1);       // [S0+T0+4] <- T1
      asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                           // complete popping the values and reference
      // restore JTOC
      VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
    }
  }

  /**
   * Emit code to implement a putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putfield(VM_Field fieldRef) {
    if (VM_Collector.NEEDS_WRITE_BARRIER && !fieldRef.getType().isPrimitiveType()) {
      VM_Barriers.compilePutfieldBarrier(asm, fieldRef.getOffset());
    }
    int fieldOffset = fieldRef.getOffset();
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitMOV_Reg_RegDisp(T0, SP, 0);           // T0 is the value to be stored
      asm.emitMOV_Reg_RegDisp(S0, SP, 4);           // S0 is the object reference
      asm.emitMOV_RegDisp_Reg(S0, fieldOffset, T0); // [S0+fieldOffset] <- T0
      asm.emitADD_Reg_Imm(SP, WORDSIZE*2);          // complete popping the value and reference
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM.assert(fieldRef.getSize() == 8);
      if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexField.getOffset());
	asm.emitPUSH_Reg        (T0);
	VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
	asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorLockMethod.getOffset());
      }
      // TODO!! use 8-byte move if possible
      asm.emitMOV_Reg_RegDisp(T0, SP, 0);                    // T0 is low part of the value to be stored
      asm.emitMOV_Reg_RegDisp(T1, SP, 4);                    // T1 is high part of the value to be stored
      asm.emitMOV_Reg_RegDisp(S0, SP, 8);                    // S0 is the object reference
      asm.emitMOV_RegDisp_Reg(S0, fieldOffset, T0);          // store low part
      asm.emitMOV_RegDisp_Reg(S0, fieldOffset+WORDSIZE, T1); // store high part
      if (fieldRef.isVolatile() && VM.BuildForStrongVolatileSemantics) {
	asm.emitMOV_Reg_RegDisp (T0, JTOC, VM_Entrypoints.doublewordVolatileMutexField.getOffset());
	asm.emitPUSH_Reg        (T0);
	VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
	asm.emitCALL_RegDisp    (S0, VM_Entrypoints.processorUnlockMethod.getOffset());
      }
      asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                   // complete popping the values and reference
    }
  }


  /*
   * method invocation
   */

  /**
   * Emit code to implement a dynamically linked invokevirtual
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokevirtual(VM_Method methodRef) {
    emitDynamicLinkingSequence(T0, methodRef);
    int methodRefparameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    int objectOffset = (methodRefparameterWords << 2) - 4;           // object offset into stack
    asm.emitMOV_Reg_RegDisp (T1, SP, objectOffset);                  // S0 has "this" parameter
    VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T1);
    asm.emitMOV_Reg_RegIdx (S0, S0, T0, asm.BYTE, 0);                // S0 has address of virtual method
    genParameterRegisterLoad(methodRef, true);
    asm.emitCALL_Reg(S0);                                      // call virtual method
    genResultRegisterUnload(methodRef);                    // push return value, if any
  }

  /**
   * Emit code to implement invokevirtual
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokevirtual(VM_Method methodRef) {
    int methodRefparameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    int methodRefOffset = methodRef.getOffset();
    int objectOffset = (methodRefparameterWords << 2) - WORDSIZE; // object offset into stack
    asm.emitMOV_Reg_RegDisp (T1, SP, objectOffset);
    VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T1);
    genParameterRegisterLoad(methodRef, true);
    asm.emitCALL_RegDisp(S0, methodRefOffset);
    genResultRegisterUnload(methodRef);
  }


  /**
   * Emit code to implement a dynamically linked invokespecial
   * @param methodRef the referenced method
   * @param targetRef the method to invoke
   */
  protected final void emit_resolved_invokespecial(VM_Method methodRef, VM_Method target) {
    if (target.isObjectInitializer()) {
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(JTOC, target.getOffset());
      genResultRegisterUnload(target);
    } else {
      if (VM.VerifyAssertions) VM.assert(!target.isStatic());
      // invoke via class's tib slot
      int methodRefOffset = target.getOffset();
      asm.emitMOV_Reg_RegDisp (S0, JTOC, target.getDeclaringClass().getTibOffset());
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(S0, methodRefOffset);
      genResultRegisterUnload(methodRef);
    }
  }

  /**
   * Emit code to implement invokespecial
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokespecial(VM_Method methodRef) {
    emitDynamicLinkingSequence(S0, methodRef);
    genParameterRegisterLoad(methodRef, true);
    asm.emitCALL_RegIdx(JTOC, S0, asm.BYTE, 0);  // call static method
    genResultRegisterUnload(methodRef);
  }


  /**
   * Emit code to implement a dynamically linked invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokestatic(VM_Method methodRef) {
    emitDynamicLinkingSequence(S0, methodRef);
    genParameterRegisterLoad(methodRef, false);          
    asm.emitCALL_RegIdx(JTOC, S0, asm.BYTE, 0); 
    genResultRegisterUnload(methodRef);
  }

  /**
   * Emit code to implement invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokestatic(VM_Method methodRef) {
    int methodOffset = methodRef.getOffset();
    genParameterRegisterLoad(methodRef, false);
    asm.emitCALL_RegDisp(JTOC, methodOffset);
    genResultRegisterUnload(methodRef);
  }


  /**
   * Emit code to implement the invokeinterface bytecode
   * @param methodRef the referenced method
   * @param count number of parameter words (see invokeinterface bytecode)
   */
  protected final void emit_invokeinterface(VM_Method methodRef, int count) {
    // (1) Emit dynamic type checking sequence if required to do so inline.
    if (VM.BuildForIMTInterfaceInvocation || 
	(VM.BuildForITableInterfaceInvocation && VM.DirectlyIndexedITables)) {
      VM_Method resolvedMethodRef = null;
      try {
	resolvedMethodRef = methodRef.resolveInterfaceMethod(false);
      } catch (VM_ResolutionException e) {
	// actually can't be thrown when we pass false for canLoad.
      }
      if (resolvedMethodRef == null) {
	// might be a ghost ref. Call uncommon case typechecking routine to deal with this
	asm.emitMOV_Reg_RegDisp (T1, SP, (count-1) << 2);                       // "this" object
	asm.emitPUSH_Imm(methodRef.getDictionaryId());                          // dict id of target
	VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T1);
	asm.emitPUSH_Reg(S0);
	genParameterRegisterLoad(2);                                            // pass 2 parameter word
	asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unresolvedInvokeinterfaceImplementsTestMethod.getOffset());// check that "this" class implements the interface
      } else {
	asm.emitMOV_Reg_RegDisp (T0, JTOC, methodRef.getDeclaringClass().getTibOffset()); // tib of the interface method
	asm.emitMOV_Reg_RegDisp (T1, SP, (count-1) << 2);                                 // "this" object
	asm.emitPUSH_RegDisp(T0, TIB_TYPE_INDEX << 2);                                // type of the interface method
	VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T1);
	asm.emitPUSH_Reg(S0);
	genParameterRegisterLoad(2);                                          // pass 2 parameter word
	asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.invokeinterfaceImplementsTestMethod.getOffset());// check that "this" class implements the interface
      }
    }

    // (2) Emit interface invocation sequence.
    if (VM.BuildForIMTInterfaceInvocation) {
      int signatureId = VM_ClassLoader.findOrCreateInterfaceMethodSignatureId(methodRef.getName(), methodRef.getDescriptor());
      int offset      = VM_InterfaceInvocation.getIMTOffset(signatureId);
          
      // squirrel away signature ID
      VM_ProcessorLocalState.emitMoveImmToField(asm, 
						VM_Entrypoints.hiddenSignatureIdField.getOffset(),
						signatureId);

      asm.emitMOV_Reg_RegDisp (T1, SP, (count-1) << 2);                                  // "this" object
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T1);
      if (VM.BuildForIndirectIMT) {
	// Load the IMT Base into S0
	asm.emitMOV_Reg_RegDisp(S0, S0, TIB_IMT_TIB_INDEX << 2);
      }
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(S0, offset);                                             // the interface call
    } else if (VM.BuildForITableInterfaceInvocation && 
	       VM.DirectlyIndexedITables && 
	       methodRef.getDeclaringClass().isResolved()) {
      methodRef = methodRef.resolve();
      VM_Class I = methodRef.getDeclaringClass();
      asm.emitMOV_Reg_RegDisp (T1, SP, (count-1) << 2);                                 // "this" object
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T1);
      asm.emitMOV_Reg_RegDisp (S0, S0, TIB_ITABLES_TIB_INDEX << 2);                     // iTables
      asm.emitMOV_Reg_RegDisp (S0, S0, I.getInterfaceId() << 2);                        // iTable
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(S0, VM_InterfaceInvocation.getITableIndex(I, methodRef) << 2); // the interface call
    } else {
      VM_Class I = methodRef.getDeclaringClass();
      int itableIndex = -1;
      if (false && VM.BuildForITableInterfaceInvocation) {
	// get the index of the method in the Itable
	if (I.isLoaded()) {
	  itableIndex = VM_InterfaceInvocation.getITableIndex(I, methodRef);
	}
      }
      if (itableIndex == -1) {
	// itable index is not known at compile-time.
	// call "invokeInterface" to resolve object + method id into 
	// method address
	int methodRefId = methodRef.getDictionaryId();
	asm.emitPUSH_RegDisp(SP, (count-1)<<LG_WORDSIZE);  // "this" parameter is obj
	asm.emitPUSH_Imm(methodRefId);                 // id of method to call
	genParameterRegisterLoad(2);               // pass 2 parameter words
	asm.emitCALL_RegDisp(JTOC,  VM_Entrypoints.invokeInterfaceMethod.getOffset()); // invokeinterface(obj, id) returns address to call
	asm.emitMOV_Reg_Reg (S0, T0);                      // S0 has address of method
	genParameterRegisterLoad(methodRef, true);
	asm.emitCALL_Reg(S0);                          // the interface method (its parameters are on stack)
      } else {
	// itable index is known at compile-time.
	// call "findITable" to resolve object + interface id into 
	// itable address
	asm.emitMOV_Reg_RegDisp (T0, SP, (count-1) << 2);             // "this" object
	VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T0);
	asm.emitPUSH_Reg(S0);
	asm.emitPUSH_Imm        (I.getInterfaceId());                // interface id
	genParameterRegisterLoad(2);                                  // pass 2 parameter words
	asm.emitCALL_RegDisp    (JTOC,  VM_Entrypoints.findItableMethod.getOffset()); // findItableOffset(tib, id) returns iTable
	asm.emitMOV_Reg_Reg     (S0, T0);                             // S0 has iTable
	genParameterRegisterLoad(methodRef, true);
	asm.emitCALL_RegDisp    (S0, itableIndex << 2);               // the interface call
      }
    }
    genResultRegisterUnload(methodRef);
  }
 

  /*
   * other object model functions
   */ 


  /**
   * Emit code to allocate a scalar object
   * @param typeRef the VM_Class to instantiate
   */
  protected final void emit_resolved_new(VM_Class typeRef) {
    int instanceSize = typeRef.getInstanceSize();
    int tibOffset = typeRef.getOffset();
    asm.emitPUSH_Imm(instanceSize);            
    asm.emitPUSH_RegDisp (JTOC, tibOffset);       // put tib on stack    
    asm.emitPUSH_Imm(typeRef.hasFinalizer()?1:0); // does the class have a finalizer?
    genParameterRegisterLoad(3);                  // pass 3 parameter words
    asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.quickNewScalarMethod.getOffset());
    asm.emitPUSH_Reg (T0);
  }

  /**
   * Emit code to dynamically link and allocate a scalar object
   * @param the dictionaryId of the VM_Class to dynamically link & instantiate
   */
  protected final void emit_unresolved_new(int dictionaryId) {
    asm.emitPUSH_Imm(dictionaryId);
    genParameterRegisterLoad(1);           // pass 1 parameter word
    asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.newScalarMethod.getOffset());
    asm.emitPUSH_Reg (T0);
  }

  /**
   * Emit code to allocate an array
   * @param array the VM_Array to instantiate
   */
  protected final void emit_newarray(VM_Array array) {
    int width      = array.getLogElementSize();
    int tibOffset  = array.getOffset();
    int headerSize = VM_ObjectModel.computeHeaderSize(array);
    // count is already on stack- nothing required
    asm.emitMOV_Reg_RegInd (T0, SP);               // get number of elements
    asm.emitSHL_Reg_Imm (T0, width);              // compute array size
    asm.emitADD_Reg_Imm(T0, headerSize);
    asm.emitPUSH_Reg(T0);      
    asm.emitPUSH_RegDisp(JTOC, tibOffset);        // put tib on stack    
    genParameterRegisterLoad(3);          // pass 3 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.quickNewArrayMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to allocate a multi-dimensional array
   * @param typeRef the VM_Array to instantiate
   * @param dimensions the number of dimensions
   * @param dictionaryId, the dictionaryId of typeRef
   */
  protected final void emit_multianewarray(VM_Array typeRef, int dimensions, int dictionaryId) {
    // setup parameters for newarrayarray routine
    asm.emitPUSH_Imm (dimensions);                     // dimension of arays
    asm.emitPUSH_Imm (dictionaryId);                   // type of array elements               
    asm.emitPUSH_Imm ((dimensions + 5)<<LG_WORDSIZE);  // offset to dimensions from FP on entry to newarray 
    // NOTE: 5 extra words- 3 for parameters, 1 for return address on stack, 1 for code technique in VM_Linker
    genParameterRegisterLoad(3);                   // pass 3 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.newArrayArrayMethod.getOffset()); 
    for (int i = 0; i < dimensions ; i++) asm.emitPOP_Reg(S0); // clear stack of dimensions (todo use and add immediate to do this)
    asm.emitPUSH_Reg(T0);                              // push array ref on stack
  }

  /**
   * Emit code to implement the arraylength bytecode
   */
  protected final void emit_arraylength() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);                   // T0 is array reference
    asm.emitMOV_Reg_RegDisp(T0, T0, VM_ObjectModel.getArrayLengthOffset()); // T0 is array length
    asm.emitMOV_RegDisp_Reg(SP, 0, T0);                   // replace reference with length on stack
  }

  /**
   * Emit code to implement the athrow bytecode
   */
  protected final void emit_athrow() {
    genParameterRegisterLoad(1);          // pass 1 parameter word
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.athrowMethod.getOffset());
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param typeRef the LHS type
   * @param target the method to invoke to implement this checkcast
   */
  protected final void emit_checkcast(VM_Type typeRef, VM_Method target) {
    asm.emitPUSH_RegInd (SP);                        // duplicate the object ref on the stack
    asm.emitPUSH_Imm(typeRef.getTibOffset());        // JTOC index that identifies klass  
    genParameterRegisterLoad(2);                     // pass 2 parameter words
    asm.emitCALL_RegDisp (JTOC, target.getOffset()); // checkcast(obj, klass-identifier)
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   * @param target the method to invoke to implement this instanceof
   */
  protected final void emit_instanceof(VM_Type typeRef, VM_Method target) {
    asm.emitPUSH_Imm(typeRef.getTibOffset());  
    genParameterRegisterLoad(2);          // pass 2 parameter words
    asm.emitCALL_RegDisp(JTOC, target.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the monitorenter bytecode
   */
  protected final void emit_monitorenter() {
    genParameterRegisterLoad(1);          // pass 1 parameter word
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.lockMethod.getOffset());
  }

  /**
   * Emit code to implement the monitorexit bytecode
   */
  protected final void emit_monitorexit() {
    genParameterRegisterLoad(1);          // pass 1 parameter word
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unlockMethod.getOffset());  
  }


  //----------------//
  // implementation //
  //----------------//
  
  private final void genPrologue () {
    if (shouldPrint) asm.comment("prologue for " + method);
    if (klass.isBridgeFromNative()) {
      // replace the normal prologue with a special prolog
      VM_JNICompiler.generateGlueCodeForJNIMethod (asm, method, compiledMethod.getId());
      // set some constants for the code generation of the rest of the method
      // firstLocalOffset is shifted down because more registers are saved
      firstLocalOffset = STACKFRAME_BODY_OFFSET - (VM_JNICompiler.SAVED_GPRS_FOR_JNI<<LG_WORDSIZE) ;
    } else {
      /* paramaters are on the stack and/or in registers;  There is space
       * on the stack for all the paramaters;  Parameter slots in the
       * stack are such that the first paramater has the higher address,
       * i.e., it pushed below all the other paramaters;  The return
       * address is the topmost entry on the stack.  The frame pointer
       * still addresses the previous frame.
       * The first word of the header, currently addressed by the stack
       * pointer, contains the return address.
       */

      /* establish a new frame:
       * push the caller's frame pointer in the stack, and
       * reset the frame pointer to the current stack top,
       * ie, the frame pointer addresses directly the word
       * that contains the previous frame pointer.
       * The second word of the header contains the frame
       * point of the caller.
       * The third word of the header contains the compiled method id of the called method.
       */
      asm.emitPUSH_RegDisp   (PR, VM_Entrypoints.framePointerField.getOffset());	// store caller's frame pointer
      VM_ProcessorLocalState.emitMoveRegToField(asm, VM_Entrypoints.framePointerField.getOffset(), SP); // establish new frame
      /*
       * NOTE: until the end of the prologue SP holds the framepointer.
       */
      asm.emitMOV_RegDisp_Imm(SP, STACKFRAME_METHOD_ID_OFFSET, compiledMethod.getId());	// 3rd word of header
      
      /*
       * save registers
       */
      asm.emitMOV_RegDisp_Reg (SP, JTOC_SAVE_OFFSET, JTOC);          // save nonvolatile JTOC register
    
      // establish the JTOC register
      VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());

      int savedRegistersSize   = SAVED_GPRS<<LG_WORDSIZE;	// default
      /* handle "dynamic brige" methods:
       * save all registers except FP, SP, PR, S0 (scratch), and
       * JTOC saved above.
       */
      // TODO: (SJF): When I try to reclaim ESI, I may have to save it here?
      if (klass.isDynamicBridge()) {
	savedRegistersSize += 3 << LG_WORDSIZE;
	asm.emitMOV_RegDisp_Reg (SP, T0_SAVE_OFFSET,  T0); 
	asm.emitMOV_RegDisp_Reg (SP, T1_SAVE_OFFSET,  T1); 
	asm.emitMOV_RegDisp_Reg (SP, EBX_SAVE_OFFSET, EBX); 
	asm.emitFNSAVE_RegDisp  (SP, FPU_SAVE_OFFSET);
	savedRegistersSize += FPU_STATE_SIZE;
      } 

      // copy registers to callee's stackframe
      firstLocalOffset         = STACKFRAME_BODY_OFFSET - savedRegistersSize;
      int firstParameterOffset = (parameterWords << LG_WORDSIZE) + WORDSIZE;
      genParameterCopy(firstParameterOffset, firstLocalOffset);

      int emptyStackOffset = firstLocalOffset - (method.getLocalWords() << LG_WORDSIZE) + WORDSIZE;
      asm.emitADD_Reg_Imm (SP, emptyStackOffset);		// set aside room for non parameter locals
      /*
       * generate stacklimit check
       */
      if (isInterruptible) {
	// S0<-limit
	VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
						  VM_Entrypoints.activeThreadStackLimitField.getOffset());

	asm.emitSUB_Reg_Reg (S0, SP);                                   	// space left
	asm.emitADD_Reg_Imm (S0, method.getOperandWords() << LG_WORDSIZE); 	// space left after this expression stack
	VM_ForwardReference fr = asm.forwardJcc(asm.LT);	// Jmp around trap if OK
	asm.emitINT_Imm ( VM_Runtime.TRAP_STACK_OVERFLOW + RVM_TRAP_BASE );	// trap
	fr.resolve(asm);
      } else {
	// TODO!! make sure stackframe of uninterruptible method doesn't overflow guard page
      }

      if (method.isSynchronized()) genMonitorEnter();

      genThreadSwitchTest(VM_Thread.PROLOGUE);

      asm.emitNOP();                                      // mark end of prologue for JDP
    }
  }
  
  private final void genEpilogue (int bytesPopped) {
    if (klass.isBridgeFromNative()) {
      // pop locals and parameters, get to saved GPR's
      asm.emitADD_Reg_Imm(SP, (this.method.getLocalWords() << LG_WORDSIZE));
      VM_JNICompiler.generateEpilogForJNIMethod(asm, this.method);
    } else if (klass.isDynamicBridge()) {
      // we never return from a DynamicBridge frame
      asm.emitINT_Imm(0xFF);
    } else {
      // normal method
      asm.emitADD_Reg_Imm     (SP, fp2spOffset(0) - bytesPopped);      // SP becomes frame pointer
      asm.emitMOV_Reg_RegDisp (JTOC, SP, JTOC_SAVE_OFFSET);            // restore nonvolatile JTOC register
      asm.emitPOP_RegDisp     (PR, VM_Entrypoints.framePointerField.getOffset()); // discard frame
      asm.emitRET_Imm(parameterWords << LG_WORDSIZE);	 // return to caller- pop parameters from stack
    }
  }
   
  private final void genMonitorEnter () {
    if (method.isStatic()) {
      if (VM.writingBootImage) {
	VM.deferClassObjectCreation(klass);
      } else {
	klass.getClassForType();
      }
      int tibOffset = klass.getTibOffset();
      asm.emitMOV_Reg_RegDisp (T0, JTOC, tibOffset);	           // T0 = tib for klass
      asm.emitMOV_Reg_RegInd (T0, T0);		                   // T0 = VM_Class for klass
      asm.emitPUSH_RegDisp(T0, VM_Entrypoints.classForTypeField.getOffset()); // push java.lang.Class object for klass
    } else {
      asm.emitPUSH_RegDisp(ESP, localOffset(0));	                   // push "this" object
    }
    genParameterRegisterLoad(1);			           // pass 1 parameter
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.lockMethod.getOffset());  
    lockOffset = asm.getMachineCodeIndex() - 1;                    // after this instruction, the method has the monitor
  }
  
  private final void genMonitorExit () {
    if (method.isStatic()) {
      int tibOffset = klass.getTibOffset();
      asm.emitMOV_Reg_RegDisp (T0, JTOC, tibOffset);                   // T0 = tib for klass
      asm.emitMOV_Reg_RegInd (T0, T0);                             // T0 = VM_Class for klass
      asm.emitPUSH_RegDisp(T0, VM_Entrypoints.classForTypeField.getOffset()); // push java.lang.Class object for klass
    } else {
      asm.emitPUSH_RegDisp(ESP, localOffset(0));                    // push "this" object
    }
    genParameterRegisterLoad(1); // pass 1 parameter
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unlockMethod.getOffset());  
  }
  
  private final void genBoundsCheck (VM_Assembler asm, byte indexReg, byte arrayRefReg ) { 
    if (options.ANNOTATIONS &&
	method.queryAnnotationForBytecode(biStart, VM_Method.annotationBoundsCheck)) {
      return;
    }
    asm.emitCMP_RegDisp_Reg(arrayRefReg,
                            VM_ObjectModel.getArrayLengthOffset(), indexReg);  // compare index to array length
    VM_ForwardReference fr = asm.forwardJcc(asm.LGT);                     // Jmp around trap if index is OK
    
    // "pass" index param to C trap handler
    VM_ProcessorLocalState.emitMoveRegToField(asm, 
                                              VM_Entrypoints.arrayIndexTrapParamField.getOffset(),
                                              indexReg);

    asm.emitINT_Imm(VM_Runtime.TRAP_ARRAY_BOUNDS + RVM_TRAP_BASE );	  // trap
    fr.resolve(asm);
  }

  /** 
   * Copy a single floating-point double parameter from operand stack into fp register stack.
   * Assumption: method to be called has exactly one parameter.
   * Assumption: parameter type is double.
   * Assumption: parameter is on top of stack.
   * Also, this method is only called before generation of a call
   * to doubleToInt() or doubleToLong()
   */
  private final void genParameterRegisterLoad () {
    if (0 < NUM_PARAMETER_FPRS) {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
    }
  }
   
  /** 
   * Copy parameters from operand stack into registers.
   * Assumption: parameters are layed out on the stack in order
   * with SP pointing to the last parameter.
   * Also, this method is called before the generation of a "helper" method call.
   * Assumption: no floating-point parameters.
   * @param params number of parameter words (including "this" if any).
   */
  private final void genParameterRegisterLoad (int params) {
    if (VM.VerifyAssertions) VM.assert(0 < params);
    if (0 < NUM_PARAMETER_GPRS) {
      asm.emitMOV_Reg_RegDisp(T0, SP, (params-1) << LG_WORDSIZE);
    }
    if (1 < params && 1 < NUM_PARAMETER_GPRS) {
      asm.emitMOV_Reg_RegDisp(T1, SP, (params-2) << LG_WORDSIZE);
    }
  }
   
  /** 
   * Copy parameters from operand stack into registers.
   * Assumption: parameters are layed out on the stack in order
   * with SP pointing to the last parameter.
   * Also, this method is called before the generation of an explicit method call.
   * @param method is the method to be called.
   * @param hasThisParameter is the method virtual?
   */
  private final void genParameterRegisterLoad (VM_Method method, boolean hasThisParam) {
    int max = NUM_PARAMETER_GPRS + NUM_PARAMETER_FPRS;
    if (max == 0) return; // quit looking when all registers are full
    int gpr = 0;  // number of general purpose registers filled
    int fpr = 0;  // number of floating point  registers filled
    byte  T = T0; // next GPR to get a parameter
    int params = method.getParameterWords() + (hasThisParam ? 1 : 0);
    int offset = (params-1) << LG_WORDSIZE; // stack offset of first parameter word
    if (hasThisParam) {
      if (gpr < NUM_PARAMETER_GPRS) {
	asm.emitMOV_Reg_RegDisp(T, SP, offset);
	T = T1; // at most 2 parameters can be passed in general purpose registers
	gpr++;
	max--;
      }
      offset -= WORDSIZE;
    }
    VM_Type [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++) {
      if (max == 0) return; // quit looking when all registers are full
      VM_Type t = types[i];
      if (t.isLongType()) {
        if (gpr < NUM_PARAMETER_GPRS) {
	  asm.emitMOV_Reg_RegDisp(T, SP, offset); // lo register := hi mem (== hi order word)
	  T = T1; // at most 2 parameters can be passed in general purpose registers
	  gpr++;
	  max--;
	  if (gpr < NUM_PARAMETER_GPRS) {
	    asm.emitMOV_Reg_RegDisp(T, SP, offset - WORDSIZE);  // hi register := lo mem (== lo order word)
	    gpr++;
	    max--;
	  }
	}
	offset -= 2*WORDSIZE;
      } else if (t.isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
	  asm.emitFLD_Reg_RegDisp(FP0, SP, offset);
	  fpr++;
	  max--;
	}
	offset -= WORDSIZE;
      } else if (t.isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
	  asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, offset - WORDSIZE);
	  fpr++;
	  max--;
	}
	offset -= 2*WORDSIZE;
      } else { // t is object, int, short, char, byte, or boolean
        if (gpr < NUM_PARAMETER_GPRS) {
	  asm.emitMOV_Reg_RegDisp(T, SP, offset);
	  T = T1; // at most 2 parameters can be passed in general purpose registers
	  gpr++;
	  max--;
	}
	offset -= WORDSIZE;
      }
    }
    if (VM.VerifyAssertions) VM.assert(offset == - WORDSIZE);
  }
   
  /** 
   * Store parameters into local space of the callee's stackframe.
   * Taken: srcOffset - offset from frame pointer of first parameter in caller's stackframe.
   *        dstOffset - offset from frame pointer of first local in callee's stackframe
   * Assumption: although some parameters may be passed in registers,
   * space for all parameters is layed out in order on the caller's stackframe.
   */
  private final void genParameterCopy (int srcOffset, int dstOffset) {
    int gpr = 0;  // number of general purpose registers unloaded
    int fpr = 0;  // number of floating point registers unloaded
    byte  T = T0; // next GPR to get a parameter
    if (!method.isStatic()) { // handle "this" parameter
      if (gpr < NUM_PARAMETER_GPRS) {
	asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);
	T = T1; // at most 2 parameters can be passed in general purpose registers
	gpr++;
      } else { // no parameters passed in registers
	asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);
	asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
      }
      srcOffset -= WORDSIZE;
      dstOffset -= WORDSIZE;
    }
    VM_Type [] types     = method.getParameterTypes();
    int     [] fprOffset = new     int [NUM_PARAMETER_FPRS]; // to handle floating point parameters in registers
    boolean [] is32bit   = new boolean [NUM_PARAMETER_FPRS]; // to handle floating point parameters in registers
    for (int i=0; i<types.length; i++) {
      VM_Type t = types[i];
      if (t.isLongType()) {
        if (gpr < NUM_PARAMETER_GPRS) {
	  asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);    // hi mem := lo register (== hi order word)
	  T = T1;                                       // at most 2 parameters can be passed in general purpose registers
	  gpr++;
	  srcOffset -= WORDSIZE;
	  dstOffset -= WORDSIZE;
	  if (gpr < NUM_PARAMETER_GPRS) {
	    asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);  // lo mem := hi register (== lo order word)
	    gpr++;
	  } else {
	    asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset); // lo mem from caller's stackframe
	    asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
	  }
	} else {
	  asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // hi mem from caller's stackframe
	  asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
	  srcOffset -= WORDSIZE;
	  dstOffset -= WORDSIZE;
	  asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // lo mem from caller's stackframe
	  asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
	}
	srcOffset -= WORDSIZE;
	dstOffset -= WORDSIZE;
      } else if (t.isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
	  fprOffset[fpr] = dstOffset;
	  is32bit[fpr]   = true;
	  fpr++;
	} else {
	  asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);
	  asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
	}
	srcOffset -= WORDSIZE;
	dstOffset -= WORDSIZE;
      } else if (t.isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
	  srcOffset -= WORDSIZE;
	  dstOffset -= WORDSIZE;
	  fprOffset[fpr] = dstOffset;
	  is32bit[fpr]   = false;
	  fpr++;
	} else {
	  asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // hi mem from caller's stackframe
	  asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
	  srcOffset -= WORDSIZE;
	  dstOffset -= WORDSIZE;
	  asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // lo mem from caller's stackframe
	  asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
	}
	srcOffset -= WORDSIZE;
	dstOffset -= WORDSIZE;
      } else { // t is object, int, short, char, byte, or boolean
        if (gpr < NUM_PARAMETER_GPRS) {
	  asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);
	  T = T1; // at most 2 parameters can be passed in general purpose registers
	  gpr++;
	} else {
	  asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);
	  asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
	}
	srcOffset -= WORDSIZE;
	dstOffset -= WORDSIZE;
      }
    }
    for (int i=fpr-1; 0<=i; i--) { // unload the floating point register stack (backwards)
      if (is32bit[i]) {
	asm.emitFSTP_RegDisp_Reg(SP, fprOffset[i], FP0);
      } else {
	asm.emitFSTP_RegDisp_Reg_Quad(SP, fprOffset[i], FP0);
      }
    }
  }
   
  /** 
   * Push return value of method from register to operand stack.
   */
  private final void genResultRegisterUnload (VM_Method method) {
    VM_Type t = method.getReturnType();
    if (t.isVoidType()) return;
    if (t.isLongType()) {
      asm.emitPUSH_Reg(T0); // high half
      asm.emitPUSH_Reg(T1); // low half
    } else if (t.isFloatType()) {
      asm.emitSUB_Reg_Imm  (SP, 4);
      asm.emitFSTP_RegInd_Reg(SP, FP0);
    } else if (t.isDoubleType()) {
      asm.emitSUB_Reg_Imm  (SP, 8);
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
    } else { // t is object, int, short, char, byte, or boolean
      asm.emitPUSH_Reg(T0);
    }
  }
  
  /**
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  private final void genThreadSwitchTest (int whereFrom) {
    if (!isInterruptible) {
      return;
    } else if (VM.BuildForDeterministicThreadSwitching) {
      // decrement the deterministic thread switch count field in the
      // processor object
      VM_ProcessorLocalState.emitDecrementField(asm, 
                                                VM_Entrypoints.deterministicThreadSwitchCountField.getOffset());
      VM_ForwardReference fr1 = asm.forwardJcc(asm.NE);                  // if not, skip
      
      // reset the count.
      VM_ProcessorLocalState.emitMoveImmToField(asm,VM_Entrypoints.deterministicThreadSwitchCountField.getOffset(),
						VM.deterministicThreadSwitchInterval);

      if (whereFrom == VM_Thread.PROLOGUE) {
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromPrologueMethod.getOffset()); 
      } else if (whereFrom == VM_Thread.BACKEDGE) {
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromBackedgeMethod.getOffset()); 
      } else { // EPILOGUE
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromEpilogueMethod.getOffset()); 
      }
      fr1.resolve(asm);
    } else {
      // thread switch requested ??
      VM_ProcessorLocalState.emitCompareFieldWithImm(asm, 
                                                     VM_Entrypoints.threadSwitchRequestedField.getOffset(),
                                                     0);
      VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);                    // if not, skip
      if (whereFrom == VM_Thread.PROLOGUE) {
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromPrologueMethod.getOffset()); 
      } else if (whereFrom == VM_Thread.BACKEDGE) {
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromBackedgeMethod.getOffset()); 
      } else { // EPILOGUE
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.threadSwitchFromEpilogueMethod.getOffset()); 
      }
      fr1.resolve(asm);
    }
  }

  private final void genMagic (VM_Method m) {
    VM_Atom methodName = m.getName();

    if (methodName == VM_MagicNames.attempt) {
      // attempt gets called with four arguments
      //   base
      //   offset
      //   oldVal
      //   newVal
      // returns ([base+offset] == oldVal)
      // if ([base+offset] == oldVal) [base+offset] := newVal
      // (operation on memory is atomic)
      asm.emitPOP_Reg (T1);            // newVal
      asm.emitPOP_Reg (EAX);           // oldVal (EAX is implicit arg to LCMPXCNG
      asm.emitPOP_Reg (S0);            // S0 = offset
      asm.emitADD_Reg_RegInd(S0, SP);  // S0 += base
      if (VM.BuildForSingleVirtualProcessor) {
	asm.emitMOV_RegInd_Reg (S0, T1);       // simply a store on uniprocessor (need not be atomic or cmp/xchg)
	asm.emitMOV_RegInd_Imm (SP, 1);        // 'push' true (overwriting base)
      } else {
	asm.emitLockNextInstruction();
	asm.emitCMPXCHG_RegInd_Reg (S0, T1);   // atomic compare-and-exchange
	asm.emitMOV_RegInd_Imm (SP, 0);        // 'push' false (overwriting base)
	VM_ForwardReference fr = asm.forwardJcc(asm.NE); // skip if compare fails
	asm.emitMOV_RegInd_Imm (SP, 1);        // 'push' true (overwriting base)
	fr.resolve(asm);
      }
      return;
    }
    
    if (methodName == VM_MagicNames.invokeMain) {
      // invokeMain gets "called" with two arguments:
      //   String[] mainArgs       // the arguments to the main method
      //   INSTRUCTION[] mainCode  // the code for the main method
      asm.emitPOP_Reg (S0);            // 
      genParameterRegisterLoad(1); // pass 1 parameter word	
      asm.emitCALL_Reg(S0);            // branches to mainCode with mainArgs on the stack
      return;
    }
    
    if (methodName == VM_MagicNames.saveThreadState) {
      int offset = VM_Entrypoints.saveThreadStateInstructionsField.getOffset();
      genParameterRegisterLoad(1); // pass 1 parameter word
      asm.emitCALL_RegDisp(JTOC, offset);
      return;
    }

    if (methodName == VM_MagicNames.threadSwitch) {
      int offset = VM_Entrypoints.threadSwitchInstructionsField.getOffset();
      genParameterRegisterLoad(2); // pass 2 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      return;
    }         
         
    if (methodName == VM_MagicNames.restoreHardwareExceptionState) {
      int offset = VM_Entrypoints.restoreHardwareExceptionStateInstructionsField.getOffset();
      genParameterRegisterLoad(1); // pass 1 parameter word
      asm.emitCALL_RegDisp(JTOC, offset);
      return;
    }

    if (methodName == VM_MagicNames.invokeClassInitializer) {
      asm.emitPOP_Reg (S0);
      asm.emitCALL_Reg(S0); // call address just popped
      return;
    }
    
    /*
     * sysCall0, sysCall1, sysCall2, sysCall3 and sysCall4 return
     * an integer (32 bits).
     *
     *	hi mem
     *	  branch address	<- SP
     *
     * before call to C
     *  hi mem
     *	  branch address
     *	  saved ebx
     *	  saved pr
     *	  saved jtoc		<- SP
     */
    if (methodName == VM_MagicNames.sysCall0) {
      asm.emitMOV_Reg_Reg(T0, SP);	// T0 <- SP
      asm.emitPUSH_Reg(EBX);	// save three nonvolatiles: EBX
       asm.emitPUSH_Reg(ESI);	
      asm.emitPUSH_Reg(JTOC);	// JTOC aka EDI
      asm.emitCALL_RegInd(T0);	// branch to C code
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);	// store return value
      return;
    }
    
    if (methodName == VM_MagicNames.sysCall1) {
      asm.emitPOP_Reg(S0);	// first and only argument
      asm.emitMOV_Reg_Reg(T0, SP);	// T0 <- SP
      asm.emitPUSH_Reg(EBX);	// save three nonvolatiles: EBX
      asm.emitPUSH_Reg(ESI);	
      asm.emitPUSH_Reg(JTOC);	// JTOC aka EDI
      asm.emitPUSH_Reg(S0);	// push arg on stack
      asm.emitCALL_RegInd(T0);	// branch to C code
      asm.emitPOP_Reg(S0);	// pop the argument 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);	// store return value
      return;
    }
    
    if (methodName == VM_MagicNames.sysCall2) {
      // C require its arguments reversed
      asm.emitPOP_Reg(T1);	// second arg
      asm.emitPOP_Reg(S0);	// first arg
      asm.emitMOV_Reg_Reg(T0, SP);	// T0 <- SP
      asm.emitPUSH_Reg(EBX);	// save three nonvolatiles: EBX
      asm.emitPUSH_Reg(ESI);	
      asm.emitPUSH_Reg(JTOC);	// JTOC aka EDI
      asm.emitPUSH_Reg(T1);	// reorder arguments for C 
      asm.emitPUSH_Reg(S0);	// reorder arguments for C
      asm.emitCALL_RegInd(T0);	// branch to C code
      asm.emitADD_Reg_Imm(SP, WORDSIZE*2);	// pop the arguments 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);	// store return value
      return;
    }
    
    if (methodName == VM_MagicNames.sysCall3) {
      // C require its arguments reversed
      asm.emitMOV_Reg_RegInd(T0, SP);			// load 3rd arg
      asm.emitMOV_RegDisp_Reg(SP, -1*WORDSIZE, T0);	// store 3rd arg
      asm.emitMOV_Reg_RegDisp(T0, SP, WORDSIZE);	// load 2nd arg
      asm.emitMOV_RegDisp_Reg(SP, -2*WORDSIZE, T0);	// store 2nd arg
      asm.emitMOV_Reg_RegDisp(T0, SP, 2*WORDSIZE);	// load 1st arg
      asm.emitMOV_RegDisp_Reg(SP, -3*WORDSIZE, T0);	// store 1st arg
      asm.emitMOV_Reg_Reg(T0, SP);			// T0 <- SP
      asm.emitMOV_RegDisp_Reg(SP, 2*WORDSIZE, EBX);	// save three nonvolatiles: EBX
      asm.emitMOV_RegDisp_Reg(SP, 1*WORDSIZE, ESI);
      asm.emitMOV_RegInd_Reg(SP, JTOC);			// JTOC aka EDI
      asm.emitADD_Reg_Imm(SP, -3*WORDSIZE);		// grow the stack
      asm.emitCALL_RegDisp(T0, 3*WORDSIZE); // fourth arg on stack is address to call
      asm.emitADD_Reg_Imm(SP, WORDSIZE*3);		// pop the arguments 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);			// store return value
      return;
    }
    
    if (methodName == VM_MagicNames.sysCall4) {
      // C require its arguments reversed
      asm.emitMOV_Reg_RegDisp(T0, SP, WORDSIZE);	// load 3rd arg
      asm.emitMOV_RegDisp_Reg(SP, -1*WORDSIZE, T0);	// store 3th arg
      asm.emitMOV_Reg_RegDisp(T0, SP, 2*WORDSIZE);	// load 2nd arg
      asm.emitMOV_RegDisp_Reg(SP, -2*WORDSIZE, T0);	// store 2nd arg
      asm.emitMOV_Reg_RegDisp(T0, SP, 3*WORDSIZE);	// load 1st arg
      asm.emitMOV_RegDisp_Reg(SP, -3*WORDSIZE, T0);	// store 1st arg
      asm.emitMOV_Reg_Reg(T0, SP);			// T0 <- SP
      asm.emitMOV_RegDisp_Reg(SP, 3*WORDSIZE, EBX);	// save three nonvolatiles: EBX
      asm.emitMOV_RegDisp_Reg(SP, 2*WORDSIZE, ESI);	
      asm.emitMOV_RegDisp_Reg(SP, 1*WORDSIZE, JTOC);	// JTOC aka EDI
      asm.emitADD_Reg_Imm(SP, -3*WORDSIZE);		// grow the stack
      asm.emitCALL_RegDisp(T0, 4*WORDSIZE); // fifth arg on stack is address to call
      asm.emitADD_Reg_Imm(SP, WORDSIZE*4);		// pop the arguments 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);			// store return value
      return;
    }
    
    /*
     * sysCall_L_0  returns a long and takes no arguments
     */
    if (methodName == VM_MagicNames.sysCall_L_0) {
      asm.emitMOV_Reg_Reg(T0, SP);
      asm.emitPUSH_Reg(EBX);	// save three nonvolatiles: EBX
      asm.emitPUSH_Reg(ESI);	
      asm.emitPUSH_Reg(JTOC);	// JTOC aka EDI
      asm.emitCALL_RegInd(T0);	// first arg on stack is address to call
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T1);	// store return value: hi half
      asm.emitPUSH_Reg(T0);	// low half
      return;
    }
    
    /*
     * sysCall_L_I  returns a long and takes an integer argument
     */
    if (methodName == VM_MagicNames.sysCall_L_I) {
      asm.emitPOP_Reg(S0);	// the one integer argument
      asm.emitMOV_Reg_Reg(T0, SP);	// T0 <- SP
      asm.emitPUSH_Reg(EBX);	// save three nonvolatiles: EBX
      asm.emitPUSH_Reg(ESI);	
      asm.emitPUSH_Reg(JTOC);	// JTOC aka EDI
      asm.emitPUSH_Reg(S0);	// push arg on stack
      asm.emitCALL_RegInd(T0);	// branch to C code
      asm.emitPOP_Reg(S0);	// pop the argument 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T1);	// store return value: hi half
      asm.emitPUSH_Reg(T0);	// low half
      return;
    }
    
    if (methodName == VM_MagicNames.sysCallAD) {  // address, double
      // C require its arguments reversed
      asm.emitMOV_Reg_RegInd(T0, SP);			// load 2nd arg
      asm.emitMOV_RegDisp_Reg(SP, -2*WORDSIZE, T0);	// store 2nd arg
      asm.emitMOV_Reg_RegDisp(T0, SP, WORDSIZE);	// load 2nd arg
      asm.emitMOV_RegDisp_Reg(SP, -1*WORDSIZE, T0);	// store 2nd arg
      asm.emitMOV_Reg_RegDisp(T0, SP, 2*WORDSIZE);	// load 1st arg
      asm.emitMOV_RegDisp_Reg(SP, -3*WORDSIZE, T0);	// store 1st arg
      asm.emitMOV_Reg_Reg(T0, SP);			// T0 <- SP
      asm.emitMOV_RegDisp_Reg(SP, 2*WORDSIZE, EBX);	// save three nonvolatiles: EBX
      asm.emitMOV_RegDisp_Reg(SP, 1*WORDSIZE, ESI);	
      asm.emitMOV_RegInd_Reg(SP, JTOC);			// JTOC aka EDI
      asm.emitADD_Reg_Imm(SP, -3*WORDSIZE);		// grow the stack
      asm.emitCALL_RegDisp(T0, 3*WORDSIZE); // 4th word on orig. stack is address to call
      asm.emitADD_Reg_Imm(SP, WORDSIZE*3);		// pop the arguments 
      asm.emitPOP_Reg(JTOC);	// restore the three nonvolatiles
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);
      asm.emitMOV_RegInd_Reg(SP, T0);			// store return value
      return;
    }

    /*
     * A special version of sysCall2, for invoking sigWait and allowing
     * collection of the frame making the call.  The signature of the
     * magic is shared between powerPC and intel to simplify the caller.
     * The signature is
     * address to "call"
     * toc or undefined for intel
     * address of a lock/barrier which will be passed to sigWait
     * value to store into the lock/barrier, also passed to sigWait.  
     * the VM_Register object of the executing thread.
     *
     * The magic stores the current ip/fp into the VM_Register, to
     * allow collection of this thread from the current frame and below.
     * It then reverses the order of the two parameters on the stack to
     * conform to C calling convention, and finally invokes sigwait.
     *
     * stack:
     *	low memory
     *		ip -- address of sysPthreadSigWait in sys.C
     *		toc --
     *          p1 -- address of lockword
     *		p2 -- value to store in lockword
     *          address of VM_Register object for this thread
     *	high mem
     * This to be invoked from baseline code only.
     */
    if (methodName == VM_MagicNames.sysCallSigWait) {

      int   fpOffset = VM_Entrypoints.registersFPField.getOffset();
      int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
      int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();

      asm.emitMOV_Reg_RegInd(T0, SP);	                // T0 <- context register obj @
      asm.emitLEA_Reg_RegDisp(S0, SP, fp2spOffset(0));  // compute FP
      asm.emitMOV_RegDisp_Reg(T0, fpOffset, S0);	// store fp in context
      asm.emitCALL_Imm (asm.getMachineCodeIndex() + 5);
      asm.emitPOP_Reg(T1);				// T1 <- IP
      asm.emitMOV_RegDisp_Reg(T0, ipOffset, T1);	// store ip in context
      asm.emitMOV_Reg_RegDisp(T0, T0, gprsOffset);	// T0 <- grps array @
      asm.emitMOV_Reg_RegDisp(T1, SP, WORDSIZE);	// second arg
      asm.emitMOV_Reg_RegDisp(S0, SP, 2*WORDSIZE);	// first arg
      asm.emitMOV_Reg_Reg(T0, SP);	// T0 <- [sysPthreadSigWait @]
      asm.emitADD_Reg_Imm(T0, 4*WORDSIZE);
      asm.emitPUSH_Reg(JTOC);	// save JTOC aka EDI
      asm.emitPUSH_Reg(T1);	// reorder arguments for C 
      asm.emitPUSH_Reg(S0);	// reorder arguments for C
      asm.emitCALL_RegInd(T0);	// branch to C code
      asm.emitADD_Reg_Imm(SP, WORDSIZE*2);	// pop the arguments 
      asm.emitPOP_Reg(JTOC);	// restore JTOC
      asm.emitADD_Reg_Imm(SP, WORDSIZE*4);	// pop all but last
      asm.emitMOV_RegInd_Reg(SP, T0);	// overwrite last with return value

      return;
    }
    
    if (methodName == VM_MagicNames.getFramePointer) {
      asm.emitLEA_Reg_RegDisp(S0, SP, fp2spOffset(0));
      asm.emitPUSH_Reg       (S0);
      return;
    }
    
    if (methodName == VM_MagicNames.getCallerFramePointer) {
      asm.emitPOP_Reg(T0);                                       // Callee FP
      asm.emitPUSH_RegDisp(T0, STACKFRAME_FRAME_POINTER_OFFSET); // Caller FP
      return;
    }

    if (methodName == VM_MagicNames.setCallerFramePointer) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // fp
      asm.emitMOV_RegDisp_Reg(S0, STACKFRAME_FRAME_POINTER_OFFSET, T0); // [S0+SFPO] <- T0
      return;
    }

    if (methodName == VM_MagicNames.getCompiledMethodID) {
      asm.emitPOP_Reg(T0);                                   // Callee FP
      asm.emitPUSH_RegDisp(T0, STACKFRAME_METHOD_ID_OFFSET); // Callee CMID
      return;
    }

    if (methodName == VM_MagicNames.setCompiledMethodID) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // fp
      asm.emitMOV_RegDisp_Reg(S0, STACKFRAME_METHOD_ID_OFFSET, T0); // [S0+SMIO] <- T0
      return;
    }

    if (methodName == VM_MagicNames.getReturnAddress) {
      asm.emitPOP_Reg(T0);                                        // Callee FP
      asm.emitPUSH_RegDisp(T0, STACKFRAME_RETURN_ADDRESS_OFFSET); // Callee return address
      return;
    }
    
    if (methodName == VM_MagicNames.setReturnAddress) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // fp
      asm.emitMOV_RegDisp_Reg(S0, STACKFRAME_RETURN_ADDRESS_OFFSET, T0); // [S0+SRAO] <- T0
      return;
    }

    if (methodName == VM_MagicNames.getTocPointer ||
	methodName == VM_MagicNames.getJTOC ) {
      asm.emitPUSH_Reg(JTOC);
      return;
    }
    
    if (methodName == VM_MagicNames.getThreadId) {
      VM_ProcessorLocalState.emitPushField(asm,VM_Entrypoints.threadIdField.getOffset());
      return;
    }
       
    // set the Thread id register (not really a register)
    if (methodName == VM_MagicNames.setThreadId) {
      VM_ProcessorLocalState.emitPopField(asm,VM_Entrypoints.threadIdField.getOffset()); 
      return;
    }
    
    // get the processor register (PR)
    if (methodName == VM_MagicNames.getProcessorRegister) {
      asm.emitPUSH_Reg(PR);
      return;
    }  

    // set the processor register (PR)
    if (methodName == VM_MagicNames.setProcessorRegister) {
      asm.emitPOP_Reg(PR);
      return;
    }
    
    // Get the value in ESI 
    if (methodName == VM_MagicNames.getESIAsProcessor) {
      asm.emitPUSH_Reg(ESI);
      return;
    }  

    // Set the value in ESI
    if (methodName == VM_MagicNames.setESIAsProcessor) {
      asm.emitPOP_Reg(ESI);
      return;
    }
  
    if (methodName == VM_MagicNames.getIntAtOffset ||
	methodName == VM_MagicNames.getObjectAtOffset ||
	methodName == VM_MagicNames.getObjectArrayAtOffset ||
	methodName == VM_MagicNames.prepare) {
      asm.emitPOP_Reg (T0);                  // object ref
      asm.emitPOP_Reg (S0);                  // offset
      asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 0); // pushes [T0+S0]
      return;
    }
    
    if (methodName == VM_MagicNames.getByteAtOffset) {
      asm.emitPOP_Reg (T0);                  // object ref
      asm.emitPOP_Reg (S0);                  // offset
      asm.emitMOV_Reg_RegIdx_Byte(T0, T0, S0, asm.BYTE, 0); // load and zero extend byte [T0+S0]
      asm.emitPUSH_Reg (T0);
      return;
    }
    
    if (methodName == VM_MagicNames.setIntAtOffset ||
	methodName == VM_MagicNames.setObjectAtOffset ) {
      asm.emitPOP_Reg(T0);                   // value
      asm.emitPOP_Reg(S0);                   // offset
      asm.emitPOP_Reg(T1);                   // obj ref
      asm.emitMOV_RegIdx_Reg(T1, S0, asm.BYTE, 0, T0); // [T1+S0] <- T0
      return;
    }
    
    if (methodName == VM_MagicNames.setByteAtOffset) {
      asm.emitPOP_Reg(T0);                   // value
      asm.emitPOP_Reg(S0);                   // offset
      asm.emitPOP_Reg(T1);                   // obj ref
      asm.emitMOV_RegIdx_Reg_Byte(T1, S0, asm.BYTE, 0, T0); // [T1+S0] <- (byte) T0
      return;
    }
    
    if (methodName == VM_MagicNames.getLongAtOffset) {
      asm.emitPOP_Reg (T0);                  // object ref
      asm.emitPOP_Reg (S0);                  // offset
      asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 4); // pushes [T0+S0+4]
      asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 0); // pushes [T0+S0]
      return;
    }
    
    if (methodName == VM_MagicNames.setLongAtOffset) {
      asm.emitMOV_Reg_RegInd (T0, SP);		// value high
      asm.emitMOV_Reg_RegDisp(S0, SP, +8 );	// offset
      asm.emitMOV_Reg_RegDisp(T1, SP, +12);	// obj ref
      asm.emitMOV_RegIdx_Reg (T1, S0, asm.BYTE, 0, T0); // [T1+S0] <- T0
      asm.emitMOV_Reg_RegDisp(T0, SP, +4 );	// value low
      asm.emitMOV_RegIdx_Reg (T1, S0, asm.BYTE, 4, T0); // [T1+S0+4] <- T0
      asm.emitADD_Reg_Imm    (SP, WORDSIZE * 4); // pop stack locations
      return;
    }
    
    if (methodName == VM_MagicNames.getMemoryWord) {
      asm.emitPOP_Reg(T0);	// address
      asm.emitPUSH_RegInd(T0); // pushes [T0+0]
      return;
    }

    if (methodName == VM_MagicNames.getMemoryAddress) {
      asm.emitPOP_Reg(T0);	// address
      asm.emitPUSH_RegInd(T0); // pushes [T0+0]
      return;
    }
    
    if (methodName == VM_MagicNames.setMemoryWord) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // address
      asm.emitMOV_RegInd_Reg(S0,T0); // [S0+0] <- T0
      return;
    }

    if (methodName == VM_MagicNames.setMemoryAddress) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // address
      asm.emitMOV_RegInd_Reg(S0,T0); // [S0+0] <- T0
      return;
    }
    
    if (methodName == VM_MagicNames.objectAsAddress         ||
	methodName == VM_MagicNames.addressAsByteArray      ||
	methodName == VM_MagicNames.addressAsIntArray       ||
	methodName == VM_MagicNames.addressAsObject         ||
	methodName == VM_MagicNames.addressAsObjectArray    ||
	methodName == VM_MagicNames.addressAsType           ||
	methodName == VM_MagicNames.objectAsType            ||
	methodName == VM_MagicNames.objectAsShortArray      ||
	methodName == VM_MagicNames.objectAsByteArray       ||
	methodName == VM_MagicNames.objectAsIntArray       ||
	methodName == VM_MagicNames.pragmaNoOptCompile      ||
	methodName == VM_MagicNames.addressAsThread         ||
	methodName == VM_MagicNames.objectAsThread          ||
	methodName == VM_MagicNames.objectAsProcessor       ||
//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
	methodName == VM_MagicNames.addressAsBlockControl   ||
	methodName == VM_MagicNames.addressAsSizeControl    ||
	methodName == VM_MagicNames.addressAsSizeControlArray   ||
//-#if RVM_WITH_CONCURRENT_GC
	methodName == VM_MagicNames.threadAsRCCollectorThread ||
//-#endif
//-#endif
	methodName == VM_MagicNames.threadAsCollectorThread ||
	methodName == VM_MagicNames.addressAsRegisters      ||
	methodName == VM_MagicNames.addressAsStack          ||
	methodName == VM_MagicNames.floatAsIntBits          ||
	methodName == VM_MagicNames.intBitsAsFloat          ||
	methodName == VM_MagicNames.doubleAsLongBits        ||
	methodName == VM_MagicNames.pragmaInline            ||
	methodName == VM_MagicNames.pragmaNoInline          ||
	methodName == VM_MagicNames.longBitsAsDouble)
      {
	// no-op (a type change, not a representation change)
	return;
      }
    
    // code for      VM_Type VM_Magic.getObjectType(Object object)
    if (methodName == VM_MagicNames.getObjectType) {
      asm.emitPOP_Reg (T0);			          // object ref
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
      asm.emitPUSH_RegDisp(S0, TIB_TYPE_INDEX<<LG_WORDSIZE); // push VM_Type slot of TIB
      return;
    }
    
    if (methodName == VM_MagicNames.getArrayLength) {
      asm.emitPOP_Reg(T0);			// object ref
      asm.emitPUSH_RegDisp(T0, VM_ObjectModel.getArrayLengthOffset()); 
      return;
    }
    
    if (methodName == VM_MagicNames.sync) {  // nothing required on IA32
      return;
    }
    
    if (methodName == VM_MagicNames.isync) { // nothing required on IA32
      return;
    }
    
    // baseline compiled invocation only: all paramaters on the stack
    // hi mem
    //      Code
    //      GPRs
    //      FPRs
    //      Spills
    // low-mem
    if (methodName == VM_MagicNames.invokeMethodReturningVoid) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      return;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningInt) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0);
      return;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningLong) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0); // high half
      asm.emitPUSH_Reg(T1); // low half
      return;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningFloat) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitSUB_Reg_Imm  (SP, 4);
      asm.emitFSTP_RegInd_Reg(SP, FP0);
      return;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningDouble) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitSUB_Reg_Imm  (SP, 8);
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
      return;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningObject) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0);
      return;
    }                 

    // baseline invocation
    // one paramater, on the stack  -- actual code
    if (methodName == VM_MagicNames.dynamicBridgeTo) {
      if (VM.VerifyAssertions) VM.assert(klass.isDynamicBridge());

      // save the branch address for later
      asm.emitPOP_Reg (S0);		// S0<-code address

      asm.emitADD_Reg_Imm(SP, fp2spOffset(0) - 4); // just popped 4 bytes above.

      // restore FPU state
      asm.emitFRSTOR_RegDisp(SP, FPU_SAVE_OFFSET);

      // restore GPRs
      asm.emitMOV_Reg_RegDisp (T0,  SP, T0_SAVE_OFFSET); 
      asm.emitMOV_Reg_RegDisp (T1,  SP, T1_SAVE_OFFSET); 
      asm.emitMOV_Reg_RegDisp (EBX, SP, EBX_SAVE_OFFSET); 
      asm.emitMOV_Reg_RegDisp (JTOC,  SP, JTOC_SAVE_OFFSET); 

      // pop frame
      asm.emitPOP_RegDisp (PR, VM_Entrypoints.framePointerField.getOffset()); // FP<-previous FP 

      // branch
      asm.emitJMP_Reg (S0);
      return;
    }
                                                  
    if (methodName == VM_MagicNames.returnToNewStack) {
      // SP gets frame pointer for new stack
      asm.emitPOP_Reg (SP);	

      // restore nonvolatile JTOC register
      asm.emitMOV_Reg_RegDisp (JTOC, SP, JTOC_SAVE_OFFSET);

      // discard current stack frame
      asm.emitPOP_RegDisp (PR, VM_Entrypoints.framePointerField.getOffset());

      // return to caller- pop parameters from stack
      asm.emitRET_Imm(parameterWords << LG_WORDSIZE);	 
      return;
    }

    if (methodName == VM_MagicNames.roundToZero) {
      // Store the FPU Control Word to a JTOC slot
      asm.emitFNSTCW_RegDisp(JTOC, VM_Entrypoints.FPUControlWordField.getOffset());
      // Set the bits in the status word that control round to zero.
      // Note that we use a 32-bit OR, even though we only care about the
      // low-order 16 bits
      asm.emitOR_RegDisp_Imm(JTOC,VM_Entrypoints.FPUControlWordField.getOffset(), 0x00000c00);
      // Now store the result back into the FPU Control Word
      asm.emitFLDCW_RegDisp(JTOC,VM_Entrypoints.FPUControlWordField.getOffset());
      return;
    }
    if (methodName == VM_MagicNames.clearFloatingPointState) {
      // Clear the hardware floating-point state
      asm.emitFNINIT();
      return;
    }
    
    if (methodName == VM_MagicNames.clearThreadSwitchBit) { // nothing to do
      // ignore obsolete magic
      return;
    }

    if (methodName == VM_MagicNames.getTime) {
      VM.sysWrite("WARNING: VM_Compiler compiling unimplemented magic: getTime in " + method + "\n");
      asm.emitMOV_RegInd_Imm(SP, 0);  // TEMP!! for now, return 0
      return;
    }

    if (methodName == VM_MagicNames.getTimeBase) {
      VM.sysWrite("WARNING: VM_Compiler compiling unimplemented magic: getTimeBase in " + method + "\n");
      asm.emitMOV_RegInd_Imm(SP, 0);  // TEMP!! for now, return 0
      return;
    }


    if (methodName == VM_MagicNames.addressFromInt ||
	methodName == VM_MagicNames.addressToInt) {
	// no-op
	return;
    }

    if (methodName == VM_MagicNames.addressAdd) {
	asm.emitPOP_Reg(T0);
	asm.emitADD_RegInd_Reg(SP, T0);
	return;
    }

    if (methodName == VM_MagicNames.addressSub ||
	methodName == VM_MagicNames.addressDiff) {
	asm.emitPOP_Reg(T0);
	asm.emitSUB_RegInd_Reg(SP, T0);
	return;
    }

    if (methodName == VM_MagicNames.addressZero) {
	asm.emitPUSH_Imm(0);
	return;
    }

    if (methodName == VM_MagicNames.addressMax) {
	asm.emitPUSH_Imm(-1);
	return;
    }

    if (methodName == VM_MagicNames.addressLT) {
	generateAddrComparison(asm.LT);
	return;
    }
    if (methodName == VM_MagicNames.addressLE) {
	generateAddrComparison(asm.LE);
	return;
    }
    if (methodName == VM_MagicNames.addressGT) {
	generateAddrComparison(asm.GT);
	return;
    }
    if (methodName == VM_MagicNames.addressGE) {
	generateAddrComparison(asm.GE);
	return;
    }
    if (methodName == VM_MagicNames.addressEQ) {
	generateAddrComparison(asm.EQ);
	return;
    }
    if (methodName == VM_MagicNames.addressNE) {
	generateAddrComparison(asm.NE);
	return;
    }
    if (methodName == VM_MagicNames.addressIsZero) {
	asm.emitPUSH_Imm(0);
	generateAddrComparison(asm.EQ);
	return;
    }
    if (methodName == VM_MagicNames.addressIsMax) {
	asm.emitPUSH_Imm(-1);
	generateAddrComparison(asm.EQ);
	return;
    }
						     
    VM.sysWrite("WARNING: VM_Compiler compiling unimplemented magic: " + methodName + " in " + method + "\n");
    asm.emitINT_Imm(0xFF); // trap
    
  }

  private void generateAddrComparison(byte comparator) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    asm.emitSET_Cond_Reg_Byte(comparator, T0);
    asm.emitMOVZX_Reg_Reg_Byte(T0, T0);   // Clear upper 3 bytes
    asm.emitPUSH_Reg(T0);
  }

  // Offset of Java local variable (off stack pointer)
  // assuming ESP is still positioned as it was at the 
  // start of the current bytecode (biStart)
  private final int localOffset  (int local) {
    return (stackHeights[biStart] - local)<<LG_WORDSIZE;
  }

  // Translate a FP offset into an SP offset 
  // assuming ESP is still positioned as it was at the 
  // start of the current bytecode (biStart)
  private final int fp2spOffset(int offset) {
    int offsetToFrameHead = (stackHeights[biStart] << LG_WORDSIZE) - firstLocalOffset;
    return offsetToFrameHead + offset;
  }
  
  private void emitDynamicLinkingSequence(byte reg, VM_Field fieldRef) {
    emitDynamicLinkingSequence(reg, fieldRef.getDictionaryId(), 
			       VM_Entrypoints.fieldOffsetsField.getOffset(),
			       VM_Entrypoints.resolveFieldMethod.getOffset());
  }

  private void emitDynamicLinkingSequence(byte reg, VM_Method methodRef) {
    emitDynamicLinkingSequence(reg, methodRef.getDictionaryId(), 
			       VM_Entrypoints.methodOffsetsField.getOffset(),
			       VM_Entrypoints.resolveMethodMethod.getOffset());
  }

  private void emitDynamicLinkingSequence(byte reg, int memberId,
					  int tableOffset,
					  int resolverOffset) {
    int memberOffset = memberId << 2;
    int retryLabel = asm.getMachineCodeIndex();            // branch here after dynamic class loading
    asm.emitMOV_Reg_RegDisp (reg, JTOC, tableOffset);      // reg is offsets table
    asm.emitMOV_Reg_RegDisp (reg, reg, memberOffset);      // reg is offset of member, or 0 if member's class isn't loaded
    asm.emitTEST_Reg_Reg    (reg, reg);                    // reg ?= 0, is field's class loaded?
    VM_ForwardReference fr = asm.forwardJcc(asm.NE);       // if so, skip call instructions
    asm.emitPUSH_Imm(memberId);                            // pass member's dictId
    genParameterRegisterLoad(1);                           // pass 1 parameter word
    asm.emitCALL_RegDisp(JTOC, resolverOffset);            // does class loading as sideffect
    asm.emitJMP_Imm (retryLabel);                          // reload reg with valid value
    fr.resolve(asm);                                       // come from Jcc above.
  }
}
