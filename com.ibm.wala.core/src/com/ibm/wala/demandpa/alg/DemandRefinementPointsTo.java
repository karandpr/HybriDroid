/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 * 
 * This file is a derivative of code released by the University of
 * California under the terms listed below.  
 *
 * Refinement Analysis Tools is Copyright ©2007 The Regents of the
 * University of California (Regents). Provided that this notice and
 * the following two paragraphs are included in any distribution of
 * Refinement Analysis Tools or its derivative work, Regents agrees
 * not to assert any of Regents' copyright rights in Refinement
 * Analysis Tools against recipient for recipientís reproduction,
 * preparation of derivative works, public display, public
 * performance, distribution or sublicensing of Refinement Analysis
 * Tools and derivative works, in source code and object code form.
 * This agreement not to assert does not confer, by implication,
 * estoppel, or otherwise any license or rights in any intellectual
 * property of Regents, including, but not limited to, any patents
 * of Regents or Regentsí employees.
 * 
 * IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT,
 * INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES,
 * INCLUDING LOST PROFITS, ARISING OUT OF THE USE OF THIS SOFTWARE
 * AND ITS DOCUMENTATION, EVEN IF REGENTS HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *   
 * REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE AND FURTHER DISCLAIMS ANY STATUTORY
 * WARRANTY OF NON-INFRINGEMENT. THE SOFTWARE AND ACCOMPANYING
 * DOCUMENTATION, IF ANY, PROVIDED HEREUNDER IS PROVIDED "AS
 * IS". REGENTS HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT,
 * UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 */
package com.ibm.wala.demandpa.alg;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.ProgramCounter;
import com.ibm.wala.demandpa.alg.refinepolicy.NeverRefineCGPolicy;
import com.ibm.wala.demandpa.alg.refinepolicy.NeverRefineFieldsPolicy;
import com.ibm.wala.demandpa.alg.refinepolicy.RefinementPolicy;
import com.ibm.wala.demandpa.alg.refinepolicy.RefinementPolicyFactory;
import com.ibm.wala.demandpa.alg.refinepolicy.SinglePassRefinementPolicy;
import com.ibm.wala.demandpa.alg.statemachine.StateMachine;
import com.ibm.wala.demandpa.alg.statemachine.StateMachineFactory;
import com.ibm.wala.demandpa.alg.statemachine.StatesMergedException;
import com.ibm.wala.demandpa.alg.statemachine.StateMachine.State;
import com.ibm.wala.demandpa.flowgraph.AbstractFlowLabelVisitor;
import com.ibm.wala.demandpa.flowgraph.AssignBarLabel;
import com.ibm.wala.demandpa.flowgraph.AssignGlobalBarLabel;
import com.ibm.wala.demandpa.flowgraph.AssignGlobalLabel;
import com.ibm.wala.demandpa.flowgraph.AssignLabel;
import com.ibm.wala.demandpa.flowgraph.DemandPointerFlowGraph;
import com.ibm.wala.demandpa.flowgraph.GetFieldLabel;
import com.ibm.wala.demandpa.flowgraph.IFlowLabel;
import com.ibm.wala.demandpa.flowgraph.MatchBarLabel;
import com.ibm.wala.demandpa.flowgraph.MatchLabel;
import com.ibm.wala.demandpa.flowgraph.NewLabel;
import com.ibm.wala.demandpa.flowgraph.ParamBarLabel;
import com.ibm.wala.demandpa.flowgraph.ParamLabel;
import com.ibm.wala.demandpa.flowgraph.PutFieldLabel;
import com.ibm.wala.demandpa.flowgraph.ReturnBarLabel;
import com.ibm.wala.demandpa.flowgraph.ReturnLabel;
import com.ibm.wala.demandpa.flowgraph.IFlowLabel.IFlowLabelVisitor;
import com.ibm.wala.demandpa.genericutil.ArraySet;
import com.ibm.wala.demandpa.genericutil.ArraySetMultiMap;
import com.ibm.wala.demandpa.genericutil.HashSetMultiMap;
import com.ibm.wala.demandpa.genericutil.MultiMap;
import com.ibm.wala.demandpa.genericutil.Predicate;
import com.ibm.wala.demandpa.util.CallSiteAndCGNode;
import com.ibm.wala.demandpa.util.MemoryAccessMap;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.propagation.FilteredPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceFieldKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.ReturnValueKey;
import com.ibm.wala.ipa.callgraph.propagation.StaticFieldKey;
import com.ibm.wala.ipa.callgraph.propagation.FilteredPointerKey.SingleClassFilter;
import com.ibm.wala.ipa.callgraph.propagation.FilteredPointerKey.SingleInstanceFilter;
import com.ibm.wala.ipa.callgraph.propagation.FilteredPointerKey.TypeFilter;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ExceptionReturnValueKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.Function;
import com.ibm.wala.util.MapIterator;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Collection;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.Trace;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetAction;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.MutableIntSetFactory;
import com.ibm.wala.util.intset.MutableMapping;
import com.ibm.wala.util.intset.MutableSparseIntSetFactory;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.intset.OrdinalSetMapping;

/**
 * Demand-driven refinement-based points-to analysis.
 * 
 * @author Manu Sridharan
 * 
 */
public class DemandRefinementPointsTo extends AbstractDemandPointsTo {

  private static final boolean DEBUG = false;

  // private static final boolean DEBUG_FULL = DEBUG && false;

  private final DemandPointerFlowGraph g;

  private StateMachineFactory<IFlowLabel> stateMachineFactory;

  /**
   * the state machine for additional filtering of paths
   */
  private StateMachine<IFlowLabel> stateMachine;

  private RefinementPolicy refinementPolicy;

  private RefinementPolicyFactory refinementPolicyFactory;

  /**
   * a {@link HeapModel} that delegates to another except for pointer keys
   * representing <code>this</code> parameters of methods, for which it
   * returns a {@link FilteredPointerKey} for the type of the parameter
   * 
   * @see #getPointerKeyForLocal(CGNode, int)
   * @author manu
   * 
   */
  private static class FilteringHeapModel implements HeapModel {

    private final HeapModel delegate;

    private final ClassHierarchy cha;

    public IClassHierarchy getClassHierarchy() {
      return delegate.getClassHierarchy();
    }

    public FilteredPointerKey getFilteredPointerKeyForLocal(CGNode node, int valueNumber, TypeFilter filter) {
      return delegate.getFilteredPointerKeyForLocal(node, valueNumber, filter);
    }

    public InstanceKey getInstanceKeyForAllocation(CGNode node, NewSiteReference allocation) {
      return delegate.getInstanceKeyForAllocation(node, allocation);
    }

    public InstanceKey getInstanceKeyForClassObject(TypeReference type) {
      return delegate.getInstanceKeyForClassObject(type);
    }

    public InstanceKey getInstanceKeyForConstant(TypeReference type, Object S) {
      return delegate.getInstanceKeyForConstant(type, S);
    }

    public InstanceKey getInstanceKeyForMultiNewArray(CGNode node, NewSiteReference allocation, int dim) {
      return delegate.getInstanceKeyForMultiNewArray(node, allocation, dim);
    }

    public InstanceKey getInstanceKeyForPEI(CGNode node, ProgramCounter instr, TypeReference type) {
      return delegate.getInstanceKeyForPEI(node, instr, type);
    }

    public PointerKey getPointerKeyForArrayContents(InstanceKey I) {
      return delegate.getPointerKeyForArrayContents(I);
    }

    public PointerKey getPointerKeyForExceptionalReturnValue(CGNode node) {
      return delegate.getPointerKeyForExceptionalReturnValue(node);
    }

    public PointerKey getPointerKeyForInstanceField(InstanceKey I, IField field) {
      return delegate.getPointerKeyForInstanceField(I, field);
    }

    public PointerKey getPointerKeyForLocal(CGNode node, int valueNumber) {
      if (!node.getMethod().isStatic() && valueNumber == 1) {
        return delegate.getFilteredPointerKeyForLocal(node, valueNumber, getFilter(node));
      } else {
        return delegate.getPointerKeyForLocal(node, valueNumber);
      }
    }

    private FilteredPointerKey.TypeFilter getFilter(CGNode target) {
      FilteredPointerKey.TypeFilter filter = (FilteredPointerKey.TypeFilter) target.getContext().get(ContextKey.FILTER);

      if (filter != null) {
        return filter;
      } else {
        // the context does not select a particular concrete type for the
        // receiver.
        IClass C = getReceiverClass(target.getMethod());
        return new FilteredPointerKey.SingleClassFilter(C);
      }
    }

    /**
     * @param method
     * @return the receiver class for this method.
     */
    private IClass getReceiverClass(IMethod method) {
      TypeReference formalType = method.getParameterType(0);
      IClass C = cha.lookupClass(formalType);
      if (Assertions.verifyAssertions) {
        if (method.isStatic()) {
          Assertions.UNREACHABLE("asked for receiver of static method " + method);
        }
        if (C == null) {
          Assertions.UNREACHABLE("no class found for " + formalType + " recv of " + method);
        }
      }
      return C;
    }

    public PointerKey getPointerKeyForReturnValue(CGNode node) {
      return delegate.getPointerKeyForReturnValue(node);
    }

    public PointerKey getPointerKeyForStaticField(IField f) {
      return delegate.getPointerKeyForStaticField(f);
    }

    public Iterator<PointerKey> iteratePointerKeys() {
      return delegate.iteratePointerKeys();
    }

    public FilteringHeapModel(HeapModel delegate, ClassHierarchy cha) {
      this.delegate = delegate;
      this.cha = cha;
    }

  }

  public RefinementPolicy getRefinementPolicy() {
    return refinementPolicy;
  }

  public DemandRefinementPointsTo(CallGraph cg, HeapModel model, MemoryAccessMap fam, ClassHierarchy cha, AnalysisOptions options,
      StateMachineFactory<IFlowLabel> stateMachineFactory) {
    super(cg, new FilteringHeapModel(model, cha), fam, cha, options);
    this.stateMachineFactory = stateMachineFactory;
    g = new DemandPointerFlowGraph(cg, this.heapModel, fam, cha);
    this.refinementPolicyFactory = new SinglePassRefinementPolicy.Factory(new NeverRefineFieldsPolicy(), new NeverRefineCGPolicy());
  }

  /**
   * Possible results of a query.
   * 
   * @see DemandRefinementPointsTo#getPointsTo(PointerKey, Predicate)
   * @author manu
   *
   */
  public static enum PointsToResult {
    /**
     * The points-to set result satisfies the supplied {@link Predicate}
     */
    SUCCESS,
    /**
     * The {@link RefinementPolicy} indicated that no more refinement was possible
     */
    NOMOREREFINE,
    /**
     * The budget specified in the {@link RefinementPolicy} was exceeded
     */
    BUDGETEXCEEDED
  };

  /**
   * re-initialize state for a new query
   */
  private void startNewQuery() {
    // re-init the refinement policy
    refinementPolicy = refinementPolicyFactory.make();
    // re-init the state machine
    stateMachine = stateMachineFactory.make();
  }

  /**
   * compute a points-to set for a pointer key, aiming to satisfy some predicate
   * 
   * @param pk
   *            the pointer key
   * @param p2setPred
   *            the desired predicate that the points-to set should ideally
   *            satisfy
   * @return a pair consisting of (1) a {@link PointsToResult} indicating
   *         whether a points-to set satisfying the predicate was computed, and
   *         (2) the last computed points-to set for the variable (possibly
   *         <code>null</code> if no points-to set could be computed in the
   *         budget)
   * @throws IllegalArgumentException
   *             if <code>pk</code> is not a {@link LocalPointerKey}; to
   *             eventually be fixed
   */
  public Pair<PointsToResult, Collection<InstanceKey>> getPointsTo(PointerKey pk, Predicate<Collection<InstanceKey>> p2setPred)
      throws IllegalArgumentException {
    if (!(pk instanceof com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey)) {
      throw new IllegalArgumentException("only locals for now");
    }
    LocalPointerKey queriedPk = (LocalPointerKey) pk;
    if (DEBUG) {
      Trace.println("answering query for " + pk);
    }
    Collection<InstanceKey> lastP2Set = null;
    boolean succeeded = false;
    startNewQuery();
    int numPasses = refinementPolicy.getNumPasses();
    int passNum = 0;
    for (; passNum < numPasses; passNum++) {
      setNumNodesTraversed(0);
      setTraversalBudget(refinementPolicy.getBudgetForPass(passNum));
      Collection<InstanceKey> curP2Set = null;
      try {
        while (true) {
          try {
            PointsToComputer computer = new PointsToComputer(queriedPk);
            computer.compute();
            curP2Set = computer.getP2Set(queriedPk);
            if (DEBUG) {
              Trace.println("traversed " + getNumNodesTraversed() + " nodes");
              Trace.println("POINTS-TO SET " + lastP2Set);
            }
            break;
          } catch (StatesMergedException e) {
            if (DEBUG) {
              Trace.println("restarting...");
            }
          }
        }
      } catch (BudgetExceededException e) {

      }
      if (curP2Set != null) {
        lastP2Set = curP2Set;
        if (p2setPred.test(curP2Set)) {
          // we did it!
          succeeded = true;
          break;
        }
      }
      // if we get here, means either budget for pass was exceeded,
      // or points-to set wasn't good enough
      // so, start new pass, if more refinement to do
      if (!refinementPolicy.nextPass()) {
        break;
      }
    }
    PointsToResult result = null;
    if (succeeded) {
      result = PointsToResult.SUCCESS;
    } else if (passNum == numPasses) {
      // we ran all the passes without succeeding and
      // without the refinement policy giving up
      result = PointsToResult.BUDGETEXCEEDED;
    } else {
      result = PointsToResult.NOMOREREFINE;
    }
    return Pair.make(result, lastP2Set);
  }

  /**
   * @return the points-to set of <code>pk</code>, or <code>null</code> if
   *         the points-to set can't be computed in the allocated budget
   */
  public Collection<InstanceKey> getPointsTo(PointerKey pk) {
    return getPointsTo(pk, Predicate.<Collection<InstanceKey>> falsePred()).snd;
  }

  /**
   * Closure indicating how to handle copies between {@link PointerKey}s.
   * 
   * @author Manu Sridharan
   * 
   */
  private static abstract class CopyHandler {

    abstract void handle(PointerKeyAndState src, PointerKey dst, IFlowLabel label);
  }

  /**
   * Representation of a statement storing a value into a field.
   * 
   * @author Manu Sridharan
   * 
   */
  private static final class StoreEdge {
    // 
    // Represents statement of the form base.field = val

    final PointerKeyAndState base;

    final IField field;

    final PointerKeyAndState val;

    @Override
    public int hashCode() {
      final int PRIME = 31;
      int result = 1;
      result = PRIME * result + val.hashCode();
      result = PRIME * result + field.hashCode();
      result = PRIME * result + base.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      final StoreEdge other = (StoreEdge) obj;
      if (!val.equals(other.val))
        return false;
      if (!field.equals(other.field))
        return false;
      if (!base.equals(other.base))
        return false;
      return true;
    }

    public StoreEdge(final PointerKeyAndState base, final IField field, final PointerKeyAndState val) {
      this.base = base;
      this.field = field;
      this.val = val;
    }

  }

  /**
   * Representation of a field read.
   * 
   * @author Manu Sridharan
   * 
   */
  private static final class LoadEdge {
    // Represents statements of the form val = base.field
    final PointerKeyAndState base;

    final IField field;

    final PointerKeyAndState val;

    @Override
    public String toString() {
      return val + " := " + base + ", field " + field;
    }

    @Override
    public int hashCode() {
      final int PRIME = 31;
      int result = 1;
      result = PRIME * result + val.hashCode();
      result = PRIME * result + field.hashCode();
      result = PRIME * result + base.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      final LoadEdge other = (LoadEdge) obj;
      if (!val.equals(other.val))
        return false;
      if (!field.equals(other.field))
        return false;
      if (!base.equals(other.base))
        return false;
      return true;
    }

    public LoadEdge(final PointerKeyAndState base, final IField field, final PointerKeyAndState val) {
      this.base = base;
      this.field = field;
      this.val = val;
    }

  }

  /**
   * Points-to analysis algorithm code.
   * 
   * Pseudocode in Chapter 5 of Manu Sridharan's dissertation.
   * 
   * @author Manu Sridharan
   * 
   */
  private final class PointsToComputer {

    private final PointerKeyAndState queriedPkAndState;

    /**
     * set of variables whose points-to sets were queried
     */
    private final MultiMap<PointerKey, State> pointsToQueried = HashSetMultiMap.make();

    /**
     * forward worklist: for initially processing points-to queries
     */
    private final Collection<PointerKeyAndState> initWorklist = new LinkedHashSet<PointerKeyAndState>();

    /**
     * worklist for variables whose points-to set has been updated
     */
    private final Collection<PointerKeyAndState> pointsToWorklist = new LinkedHashSet<PointerKeyAndState>();

    /**
     * worklist for variables whose tracked points-to set has been updated
     */
    private final Collection<PointerKeyAndState> trackedPointsToWorklist = new LinkedHashSet<PointerKeyAndState>();

    /**
     * maps a pointer key to those on-the-fly virtual calls for which it is the
     * receiver
     */
    private final MultiMap<PointerKeyAndState, CallSiteAndCGNode> pkToOTFCalls = HashSetMultiMap.make();

    /**
     * cache of the targets discovered for a call site during on-the-fly call
     * graph construction
     */
    private final MultiMap<CallSiteAndCGNode, IMethod> callToOTFTargets = ArraySetMultiMap.make();

    // alloc nodes to the fields we're looking to match on them,
    // matching getfield with putfield
    private final MultiMap<InstanceKeyAndState, IField> forwInstKeyToFields = HashSetMultiMap.make();

    // matching putfield_bar with getfield_bar
    private final MultiMap<InstanceKeyAndState, IField> backInstKeyToFields = HashSetMultiMap.make();

    // points-to sets and tracked points-to sets
    private final Map<PointerKeyAndState, MutableIntSet> pkToP2Set = HashMapFactory.make();

    private final Map<PointerKeyAndState, MutableIntSet> pkToTrackedSet = HashMapFactory.make();

    private final Map<InstanceFieldKeyAndState, MutableIntSet> instFieldKeyToP2Set = HashMapFactory.make();

    private final Map<InstanceFieldKeyAndState, MutableIntSet> instFieldKeyToTrackedSet = HashMapFactory.make();

    /**
     * for numbering {@link InstanceKey}, {@link State} pairs
     */
    private final OrdinalSetMapping<InstanceKeyAndState> ikAndStates = MutableMapping.make();

    private final MutableIntSetFactory intSetFactory = new MutableSparseIntSetFactory(); // new

    // BitVectorIntSetFactory();

    /**
     * tracks all field stores encountered during traversal
     */
    private final HashSet<StoreEdge> encounteredStores = HashSetFactory.make();

    /**
     * tracks all field loads encountered during traversal
     */
    private final HashSet<LoadEdge> encounteredLoads = HashSetFactory.make();

    PointsToComputer(LocalPointerKey pk) {
      queriedPkAndState = new PointerKeyAndState(pk, stateMachine.getStartState());
    }

    private OrdinalSet<InstanceKeyAndState> makeOrdinalSet(IntSet intSet) {
      // make a copy here, to avoid comodification during iteration
      // TODO remove the copying, do it only at necessary call sites
      return new OrdinalSet<InstanceKeyAndState>(intSetFactory.makeCopy(intSet), ikAndStates);
    }

    public Collection<InstanceKey> getP2Set(LocalPointerKey queriedPk) {
      return Iterator2Collection.toCollection(new MapIterator<InstanceKeyAndState, InstanceKey>(makeOrdinalSet(
          find(pkToP2Set, new PointerKeyAndState(queriedPk, stateMachine.getStartState()))).iterator(),
          new Function<InstanceKeyAndState, InstanceKey>() {

            public InstanceKey apply(InstanceKeyAndState object) {
              return object.getInstanceKey();
            }

          }));
    }

    private boolean addAllToP2Set(Map<PointerKeyAndState, MutableIntSet> p2setMap, PointerKeyAndState pkAndState, IntSet vals) {
      final PointerKey pk = pkAndState.getPointerKey();
      if (pk instanceof FilteredPointerKey) {
        if (DEBUG) {
          Trace.println("handling filtered pointer key " + pk);
        }
        final TypeFilter typeFilter = ((FilteredPointerKey) pk).getTypeFilter();
        if (typeFilter instanceof SingleClassFilter) {
          final IClass concreteType = ((SingleClassFilter) typeFilter).getConcreteType();
          final MutableIntSet tmp = intSetFactory.make();
          vals.foreach(new IntSetAction() {

            public void act(int x) {
              InstanceKeyAndState ikAndState = ikAndStates.getMappedObject(x);
              if (cha.isAssignableFrom(concreteType, ikAndState.getInstanceKey().getConcreteType())) {
                tmp.add(x);
              }
            }

          });
          vals = tmp;
        } else if (typeFilter instanceof SingleInstanceFilter) {
          final InstanceKey theOnlyInstanceKey = ((SingleInstanceFilter) typeFilter).getInstance();
          final MutableIntSet tmp = intSetFactory.make();
          vals.foreach(new IntSetAction() {

            public void act(int x) {
              InstanceKeyAndState ikAndState = ikAndStates.getMappedObject(x);
              if (ikAndState.getInstanceKey().equals(theOnlyInstanceKey)) {
                tmp.add(x);
              }
            }

          });
          vals = tmp;
        } else {
          Assertions.UNREACHABLE();
        }
      }
      boolean added = findOrCreate(p2setMap, pkAndState).addAll(vals);
      // final boolean added = p2setMap.putAll(pkAndState, vals);
      if (DEBUG && added) {
        Trace.println("POINTS-TO ADDITION TO PK " + pkAndState + ":");
        for (InstanceKeyAndState ikAndState : makeOrdinalSet(vals)) {
          Trace.println(ikAndState);
        }
        Trace.println("*************");
      }
      return added;

    }

    void compute() {
      final CGNode node = ((LocalPointerKey) queriedPkAndState.getPointerKey()).getNode();
      if (node.getIR() == null) {
        return;
      }
      g.addSubgraphForNode(node);
      addToInitWorklist(queriedPkAndState);
      do {
        while (!initWorklist.isEmpty() || !pointsToWorklist.isEmpty() || !trackedPointsToWorklist.isEmpty()) {
          handleInitWorklist();
          handlePointsToWorklist();
          handleTrackedPointsToWorklist();
        }
        makePassOverFieldStmts();
      } while (!initWorklist.isEmpty() || !pointsToWorklist.isEmpty() || !trackedPointsToWorklist.isEmpty());
    }

    void handleCopy(final PointerKeyAndState curPkAndState, final PointerKey succPk, IFlowLabel label) {
      if (Assertions.verifyAssertions) {
        Assertions._assert(!label.isBarred());
      }
      State curState = curPkAndState.getState();
      doTransition(curState, label, new Function<State, Object>() {

        public Object apply(State nextState) {
          PointerKeyAndState succPkAndState = new PointerKeyAndState(succPk, nextState);
          handleCopy(curPkAndState, succPkAndState);
          return null;
        }

      });
    }

    void handleCopy(PointerKeyAndState curPkAndState, PointerKeyAndState succPkAndState) {
      if (!addToInitWorklist(succPkAndState)) {
        // handle like x = y with Y updated
        if (addAllToP2Set(pkToP2Set, curPkAndState, find(pkToP2Set, succPkAndState))) {
          addToPToWorklist(curPkAndState);
        }
      }

    }

    void handleAllCopies(PointerKeyAndState curPk, Iterator<? extends Object> succNodes, IFlowLabel label) {
      while (succNodes.hasNext()) {
        handleCopy(curPk, (PointerKey) succNodes.next(), label);
      }
    }

    /**
     * 
     * @param curPkAndState
     * @param predPk
     * @param label
     *            the label of the edge from curPk to predPk (must be barred)
     * @return those {@link PointerKeyAndState}s whose points-to sets have been
     *         queried, such that the {@link PointerKey} is predPk, and
     *         transitioning from its state on <code>label.bar()</code> yields
     *         the state of <code>curPkAndState</code>
     */
    Collection<PointerKeyAndState> matchingPToQueried(PointerKeyAndState curPkAndState, PointerKey predPk, IFlowLabel label) {
      Collection<PointerKeyAndState> ret = ArraySet.make();
      if (Assertions.verifyAssertions) {
        Assertions._assert(label.isBarred());
      }
      IFlowLabel unbarredLabel = label.bar();
      final State curState = curPkAndState.getState();
      Set<State> predPkStates = pointsToQueried.get(predPk);
      for (State predState : predPkStates) {
        State transState = stateMachine.transition(predState, unbarredLabel);
        if (transState.equals(curState)) {
          // we have a winner!
          ret.add(new PointerKeyAndState(predPk, predState));
        }
      }
      return ret;
    }

    void handleBackCopy(PointerKeyAndState curPkAndState, PointerKey predPk, IFlowLabel label) {
      for (PointerKeyAndState predPkAndState : matchingPToQueried(curPkAndState, predPk, label)) {
        if (addAllToP2Set(pkToP2Set, predPkAndState, find(pkToP2Set, curPkAndState))) {
          addToPToWorklist(predPkAndState);
        }
      }
    }

    void handleAllBackCopies(PointerKeyAndState curPkAndState, Iterator<? extends Object> predNodes, IFlowLabel label) {
      while (predNodes.hasNext()) {
        handleBackCopy(curPkAndState, (PointerKey) predNodes.next(), label);
      }
    }

    /**
     * should only be called when pk's points-to set has just been updated. add
     * pk to the points-to worklist, and re-propagate and calls that had pk as
     * the receiver.
     * 
     * @param pkAndState
     */
    void addToPToWorklist(PointerKeyAndState pkAndState) {
      pointsToWorklist.add(pkAndState);
      Set<CallSiteAndCGNode> otfCalls = pkToOTFCalls.get(pkAndState);
      for (CallSiteAndCGNode callSiteAndCGNode : otfCalls) {
        propTargets(pkAndState, callSiteAndCGNode);
      }
    }

    boolean addToInitWorklist(PointerKeyAndState pkAndState) {
      if (pointsToQueried.put(pkAndState.getPointerKey(), pkAndState.getState())) {
        if (Assertions.verifyAssertions && pkAndState.getPointerKey() instanceof LocalPointerKey) {
          CGNode node = ((LocalPointerKey) pkAndState.getPointerKey()).getNode();
          Assertions._assert(g.hasSubgraphForNode(node), "missing constraints for node of var " + pkAndState);
        }
        if (DEBUG) {
          // Trace.println("adding to init_ " + pkAndState);
        }
        initWorklist.add(pkAndState);
        // if (pkAndStates.getMappedIndex(pkAndState) == -1) {
        // pkAndStates.add(pkAndState);
        // }
        return true;
      }
      return false;
    }

    void addToTrackedPToWorklist(PointerKeyAndState pkAndState) {
      if (Assertions.verifyAssertions && pkAndState.getPointerKey() instanceof LocalPointerKey) {
        CGNode node = ((LocalPointerKey) pkAndState.getPointerKey()).getNode();
        Assertions._assert(g.hasSubgraphForNode(node), "missing constraints for " + node);
      }
      if (DEBUG) {
        // Trace.println("adding to tracked points-to_ " + pkAndState);
      }
      trackedPointsToWorklist.add(pkAndState);
    }

    /**
     * Adds new targets for a virtual call, based on the points-to set of the
     * receiver, and propagates values for the parameters / return value of the
     * new targets. NOTE: this method will <em>not</em> do any propagation for
     * virtual call targets that have already been discovered.
     * 
     * @param receiverAndState
     *            the receiver
     * @param callSiteAndCGNode
     *            the call
     */
    void propTargets(PointerKeyAndState receiverAndState, CallSiteAndCGNode callSiteAndCGNode) {
      final CGNode caller = callSiteAndCGNode.getCGNode();
      CallSiteReference call = callSiteAndCGNode.getCallSiteReference();
      final State receiverState = receiverAndState.getState();
      OrdinalSet<InstanceKeyAndState> p2set = makeOrdinalSet(find(pkToP2Set, receiverAndState));
      for (InstanceKeyAndState ikAndState : p2set) {
        InstanceKey ik = ikAndState.getInstanceKey();
        IMethod targetMethod = options.getMethodTargetSelector().getCalleeTarget(caller, call, ik.getConcreteType());
        if (targetMethod == null) {
          // NOTE: target method can be null because we don't
          // always have type filters
          continue;
        }
        // if we've already handled this target, we can stop
        if (callToOTFTargets.get(callSiteAndCGNode).contains(targetMethod)) {
          continue;
        }
        callToOTFTargets.put(callSiteAndCGNode, targetMethod);
        // TODO can we just pick one of these, rather than all of them?
        // TODO handle clone() properly
        Set<CGNode> targetCGNodes = cg.getNodes(targetMethod.getReference());
        for (final CGNode targetForCall : targetCGNodes) {
          if (DEBUG) {
            // Trace.println("adding target " + targetForCall + " for call " +
            // call);
          }
          if (targetForCall.getIR() == null) {
            continue;
          }
          g.addSubgraphForNode(targetForCall);
          // need to check flows through parameters and returns,
          // in direction of value flow and reverse
          SSAAbstractInvokeInstruction[] calls = getIR(caller).getCalls(call);
          for (final SSAAbstractInvokeInstruction invokeInstr : calls) {
            final ReturnLabel returnLabel = ReturnLabel.make(new CallSiteAndCGNode(call, caller));
            if (invokeInstr.hasDef()) {
              final PointerKeyAndState defAndState = new PointerKeyAndState(heapModel.getPointerKeyForLocal(caller, invokeInstr
                  .getDef()), receiverState);
              final PointerKey ret = heapModel.getPointerKeyForReturnValue(targetForCall);
              doTransition(receiverState, returnLabel, new Function<State, Object>() {

                public Object apply(State retState) {
                  repropCallArg(defAndState, new PointerKeyAndState(ret, retState), returnLabel.bar());
                  return null;
                }

              });
            }
            final PointerKeyAndState exc = new PointerKeyAndState(heapModel.getPointerKeyForLocal(caller, invokeInstr
                .getException()), receiverState);
            final PointerKey excRet = heapModel.getPointerKeyForExceptionalReturnValue(targetForCall);
            doTransition(receiverState, returnLabel, new Function<State, Object>() {

              public Object apply(State excRetState) {
                repropCallArg(exc, new PointerKeyAndState(excRet, excRetState), returnLabel.bar());
                return null;
              }

            });
            for (Iterator<Integer> iter = g.pointerParamValueNums(targetForCall); iter.hasNext();) {
              final int formalNum = iter.next();
              final int actualNum = formalNum - 1;
              final ParamBarLabel paramBarLabel = ParamBarLabel.make(new CallSiteAndCGNode(call, caller));
              doTransition(receiverState, paramBarLabel, new Function<State, Object>() {

                public Object apply(State formalState) {
                  repropCallArg(
                      new PointerKeyAndState(heapModel.getPointerKeyForLocal(targetForCall, formalNum), formalState),
                      new PointerKeyAndState(heapModel.getPointerKeyForLocal(caller, invokeInstr.getUse(actualNum)), receiverState),
                      paramBarLabel);
                  return null;
                }

              });
            }
          }
        }
      }
    }

    /**
     * handle possible updated flow in both directions for a call parameter
     * 
     * @param src
     * @param dst
     */
    private void repropCallArg(PointerKeyAndState src, PointerKeyAndState dst, IFlowLabel dstToSrcLabel) {
      for (PointerKeyAndState srcToHandle : matchingPToQueried(dst, src.getPointerKey(), dstToSrcLabel)) {
        handleCopy(srcToHandle, dst);
      }
      IntSet trackedSet = find(pkToTrackedSet, dst);
      if (!trackedSet.isEmpty()) {
        if (findOrCreate(pkToTrackedSet, src).addAll(trackedSet)) {
          addToTrackedPToWorklist(src);
        }
      }
    }

    void handleInitWorklist() {
      while (!initWorklist.isEmpty()) {
        incrementNumNodesTraversed();
        final PointerKeyAndState curPkAndState = initWorklist.iterator().next();
        initWorklist.remove(curPkAndState);
        final PointerKey curPk = curPkAndState.getPointerKey();
        final State curState = curPkAndState.getState();
        if (DEBUG)
          Trace.println("init " + curPkAndState);
        if (Assertions.verifyAssertions && curPk instanceof LocalPointerKey) {
          Assertions._assert(g.hasSubgraphForNode(((LocalPointerKey) curPk).getNode()));
        }
        // if (curPk instanceof LocalPointerKey) {
        // Collection<InstanceKey> constantVals =
        // getConstantVals((LocalPointerKey) curPk);
        // if (constantVals != null) {
        // for (InstanceKey ik : constantVals) {
        // pkToP2Set.put(curPk, ik);
        // addToPToWorklist(curPk);
        // }
        // }
        // }
        IFlowLabelVisitor v = new AbstractFlowLabelVisitor() {

          @Override
          public void visitNew(NewLabel label, Object dst) {
            final InstanceKey ik = (InstanceKey) dst;
            if (DEBUG) {
              Trace.println("alloc " + ik + " assigned to " + curPk);
            }
            doTransition(curState, label, new Function<State, Object>() {

              public Object apply(State newState) {
                InstanceKeyAndState ikAndState = new InstanceKeyAndState(ik, newState);
                int n = ikAndStates.add(ikAndState);
                findOrCreate(pkToP2Set, curPkAndState).add(n);
                addToPToWorklist(curPkAndState);
                return null;
              }

            });
          }

          @Override
          public void visitGetField(GetFieldLabel label, Object dst) {
            IField field = (label).getField();
            if (refineFieldAccesses(field)) {
              PointerKey loadBase = (PointerKey) dst;
              // if (Assertions.verifyAssertions) {
              // Assertions._assert(stateMachine.transition(curState, label) ==
              // curState);
              // }
              PointerKeyAndState loadBaseAndState = new PointerKeyAndState(loadBase, curState);
              addEncounteredLoad(new LoadEdge(loadBaseAndState, field, curPkAndState));
              if (!addToInitWorklist(loadBaseAndState)) {
                // handle like x = y.f, with Y updated
                for (InstanceKeyAndState ikAndState : makeOrdinalSet(find(pkToP2Set, loadBaseAndState))) {
                  trackInstanceField(ikAndState, field, forwInstKeyToFields);
                }
              }
            } else {
              handleAllCopies(curPkAndState, g.getWritesToInstanceField(field), MatchLabel.v());
            }
          }

          @Override
          public void visitAssignGlobal(AssignGlobalLabel label, Object dst) {
            handleAllCopies(curPkAndState, g.getWritesToStaticField((StaticFieldKey) dst), AssignGlobalLabel.v());
          }

          @Override
          public void visitAssign(AssignLabel label, Object dst) {
            handleCopy(curPkAndState, (PointerKey) dst, AssignLabel.v());
          }

        };
        g.visitSuccs(curPk, v);
        // interprocedural edges
        handleForwInterproc(curPkAndState, new CopyHandler() {

          @Override
          void handle(PointerKeyAndState src, PointerKey dst, IFlowLabel label) {
            handleCopy(src, dst, label);
          }

        });
      }

    }

    /**
     * handle flow from actuals to formals, and from returned values to
     * variables at the caller
     * 
     * @param curPk
     * @param handler
     */
    private void handleForwInterproc(final PointerKeyAndState curPkAndState, final CopyHandler handler) {
      PointerKey curPk = curPkAndState.getPointerKey();
      if (curPk instanceof LocalPointerKey) {
        final LocalPointerKey localPk = (LocalPointerKey) curPk;
        if (g.isParam(localPk)) {
          // System.err.println("at param");
          final CGNode callee = localPk.getNode();
          final int paramPos = localPk.getValueNumber() - 1;
          for (Iterator<? extends CGNode> iter = cg.getPredNodes(callee); iter.hasNext();) {
            final CGNode caller = iter.next();
            // if (DEBUG) {
            // Trace.println("handling caller " + caller);
            // }
            final IR ir = getIR(caller);
            for (Iterator<CallSiteReference> iterator = cg.getPossibleSites(caller, callee); iterator.hasNext();) {
              final CallSiteReference call = iterator.next();
              final CallSiteAndCGNode callSiteAndCGNode = new CallSiteAndCGNode(call, caller);
              final ParamLabel paramLabel = ParamLabel.make(callSiteAndCGNode);
              doTransition(curPkAndState.getState(), paramLabel, new Function<State, Object>() {

                private void propagateToCallee() {
                  if (caller.getIR() == null) {
                    return;
                  }
                  g.addSubgraphForNode(caller);
                  SSAAbstractInvokeInstruction[] callInstrs = ir.getCalls(call);
                  for (int i = 0; i < callInstrs.length; i++) {
                    SSAAbstractInvokeInstruction callInstr = callInstrs[i];
                    PointerKey actualPk = heapModel.getPointerKeyForLocal(caller, callInstr.getUse(paramPos));
                    if (Assertions.verifyAssertions) {
                      Assertions._assert(g.containsNode(actualPk));
                      Assertions._assert(g.containsNode(localPk));
                    }
                    handler.handle(curPkAndState, actualPk, paramLabel);
                  }
                }

                public Object apply(State callerState) {
                  Set<CGNode> possibleTargets = cg.getPossibleTargets(caller, call);
                  if (noOnTheFlyNeeded(callSiteAndCGNode, possibleTargets)) {
                    propagateToCallee();
                  } else {
                    if (callToOTFTargets.get(callSiteAndCGNode).contains(callee.getMethod())) {
                      // already found this target as valid, so do propagation
                      propagateToCallee();
                    } else {
                      // if necessary, start a query for the call site
                      queryCallTargets(callSiteAndCGNode, ir, callerState);
                    }
                  }
                  return null;
                }

              });
            }
          }
        }
        SSAInvokeInstruction callInstr = g.getInstrReturningTo(localPk);
        if (callInstr != null) {
          CGNode caller = localPk.getNode();
          boolean isExceptional = localPk.getValueNumber() == callInstr.getException();

          CallSiteReference callSiteRef = callInstr.getCallSite();
          CallSiteAndCGNode callSiteAndCGNode = new CallSiteAndCGNode(callSiteRef, caller);
          // get call targets
          Set<CGNode> possibleCallees = cg.getPossibleTargets(caller, callSiteRef);
          // if (DEBUG &&
          // callSiteRef.getDeclaredTarget().toString().indexOf("clone()") !=
          // -1) {
          // System.err.println(possibleCallees);
          // System.err.println(Iterator2Collection.toCollection(cg.getSuccNodes(caller)));
          // System.err.println(Iterator2Collection.toCollection(cg.getPredNodes(possibleCallees.iterator().next())));
          // }
          // construct graph for each target
          if (noOnTheFlyNeeded(callSiteAndCGNode, possibleCallees)) {
            for (CGNode callee : possibleCallees) {
              if (callee.getIR() == null) {
                continue;
              }
              g.addSubgraphForNode(callee);
              PointerKey retVal = isExceptional ? heapModel.getPointerKeyForExceptionalReturnValue(callee) : heapModel
                  .getPointerKeyForReturnValue(callee);
              if (Assertions.verifyAssertions) {
                Assertions._assert(g.containsNode(retVal));
              }
              handler.handle(curPkAndState, retVal, ReturnLabel.make(callSiteAndCGNode));
            }
          } else {
            if (callToOTFTargets.containsKey(callSiteAndCGNode)) {
              // already queried this call site
              // handle existing targets
              Set<IMethod> targetMethods = callToOTFTargets.get(callSiteAndCGNode);
              for (CGNode callee : possibleCallees) {
                if (targetMethods.contains(callee.getMethod())) {
                  if (callee.getIR() == null) {
                    continue;
                  }
                  g.addSubgraphForNode(callee);
                  PointerKey retVal = isExceptional ? heapModel.getPointerKeyForExceptionalReturnValue(callee) : heapModel
                      .getPointerKeyForReturnValue(callee);
                  if (Assertions.verifyAssertions) {
                    Assertions._assert(g.containsNode(retVal));
                  }
                  handler.handle(curPkAndState, retVal, ReturnLabel.make(callSiteAndCGNode));
                }
              }
            } else {
              // if necessary, raise a query for the call site
              queryCallTargets(callSiteAndCGNode, getIR(caller), curPkAndState.getState());
            }
          }
        }
      }
    }

    /**
     * track a field of some instance key, as we are interested in statements
     * that read or write to the field
     * 
     * @param ikAndState
     * @param field
     * @param ikToFields
     *            either {@link #forwInstKeyToFields} or
     *            {@link #backInstKeyToFields}
     */
    private void trackInstanceField(InstanceKeyAndState ikAndState, IField field, MultiMap<InstanceKeyAndState, IField> ikToFields) {
      State state = ikAndState.getState();
      if (Assertions.verifyAssertions) {
        Assertions._assert(refineFieldAccesses(field));
      }
      ikToFields.put(ikAndState, field);
      for (Iterator<? extends Object> iter = g.getPredNodes(ikAndState.getInstanceKey(), NewLabel.v()); iter.hasNext();) {
        PointerKey ikPred = (PointerKey) iter.next();
        PointerKeyAndState ikPredAndState = new PointerKeyAndState(ikPred, state);
        int mappedIndex = ikAndStates.getMappedIndex(ikAndState);
        if (Assertions.verifyAssertions) {
          Assertions._assert(mappedIndex != -1);
        }
        if (findOrCreate(pkToTrackedSet, ikPredAndState).add(mappedIndex)) {
          addToTrackedPToWorklist(ikPredAndState);
        }
      }
    }

    private boolean refineFieldAccesses(IField field) {
      return refinementPolicy.getFieldRefinePolicy().shouldRefine(field);
    }

    /**
     * Initiates a query for the targets of some virtual call, by asking for
     * points-to set of receiver. NOTE: if receiver has already been queried,
     * will not do any additional propagation for already-discovered virtual
     * call targets
     * 
     * @param caller
     * @param ir
     * @param call
     * @param callerState
     */
    private void queryCallTargets(CallSiteAndCGNode callSiteAndCGNode, IR ir, State callerState) {
      final CallSiteReference call = callSiteAndCGNode.getCallSiteReference();
      final CGNode caller = callSiteAndCGNode.getCGNode();
      for (SSAAbstractInvokeInstruction callInstr : ir.getCalls(call)) {
        PointerKey thisArg = heapModel.getPointerKeyForLocal(caller, callInstr.getUse(0));
        PointerKeyAndState thisArgAndState = new PointerKeyAndState(thisArg, callerState);
        if (pkToOTFCalls.put(thisArgAndState, callSiteAndCGNode)) {
          // added the call target
          final CGNode node = ((LocalPointerKey) thisArg).getNode();
          if (node.getIR() == null) {
            return;
          }
          g.addSubgraphForNode(node);
          if (!addToInitWorklist(thisArgAndState)) {
            // need to handle pk's current values for call
            propTargets(thisArgAndState, callSiteAndCGNode);
          } else {
            if (DEBUG) {
              Trace.println("querying for targets of call " + call + " in " + caller);
            }
          }
        } else {
          // TODO: I think we can remove this call
          propTargets(thisArgAndState, callSiteAndCGNode);
        }
      }
    }

    private boolean noOnTheFlyNeeded(CallSiteAndCGNode call, Set<CGNode> possibleTargets) {
      // NOTE: if we want to be more precise for queries in dead code,
      // we shouldn't rely on possibleTargets here (since there may be
      // zero targets)
      return !refinementPolicy.getCallGraphRefinePolicy().shouldRefine(call) || possibleTargets.size() <= 1;
    }

    void handlePointsToWorklist() {
      while (!pointsToWorklist.isEmpty()) {
        incrementNumNodesTraversed();
        final PointerKeyAndState curPkAndState = pointsToWorklist.iterator().next();
        pointsToWorklist.remove(curPkAndState);
        final PointerKey curPk = curPkAndState.getPointerKey();
        final State curState = curPkAndState.getState();
        if (DEBUG) {
          Trace.println("points-to " + curPkAndState);
          Trace.println("***pto-set " + find(pkToP2Set, curPkAndState) + "***");
        }
        IFlowLabelVisitor predVisitor = new AbstractFlowLabelVisitor() {

          @Override
          public void visitPutField(PutFieldLabel label, Object dst) {
            IField field = label.getField();
            if (refineFieldAccesses(field)) {
              // statements x.f = y, Y updated (X' not empty required)
              // update Z.f for all z in X'
              PointerKeyAndState storeBaseAndState = new PointerKeyAndState(((PointerKey) dst), curState);
              encounteredStores.add(new StoreEdge(storeBaseAndState, field, curPkAndState));
              for (InstanceKeyAndState ikAndState : makeOrdinalSet(find(pkToTrackedSet, storeBaseAndState))) {
                if (forwInstKeyToFields.get(ikAndState).contains(field)) {
                  InstanceFieldKeyAndState ifKeyAndState = getInstFieldKey(ikAndState, field);
                  findOrCreate(instFieldKeyToP2Set, ifKeyAndState).addAll(find(pkToP2Set, curPkAndState));

                }
              }
            } else {
              handleAllBackCopies(curPkAndState, g.getReadsOfInstanceField(field), MatchBarLabel.v());
            }
          }

          @Override
          public void visitGetField(GetFieldLabel label, Object dst) {
            IField field = (label).getField();
            if (refineFieldAccesses(field)) {
              // statements x = y.f, Y updated
              // if X queried, start tracking Y.f
              PointerKey dstPtrKey = (PointerKey) dst;
              PointerKeyAndState loadDefAndState = new PointerKeyAndState(dstPtrKey, curState);
              addEncounteredLoad(new LoadEdge(curPkAndState, field, loadDefAndState));
              if (pointsToQueried.get(dstPtrKey).contains(curState)) {
                for (InstanceKeyAndState ikAndState : makeOrdinalSet(find(pkToP2Set, curPkAndState))) {
                  trackInstanceField(ikAndState, field, forwInstKeyToFields);
                }
              }
            }
          }

          @Override
          public void visitAssignGlobal(AssignGlobalLabel label, Object dst) {
            handleAllBackCopies(curPkAndState, g.getReadsOfStaticField((StaticFieldKey) dst), label.bar());
          }

          @Override
          public void visitAssign(AssignLabel label, Object dst) {
            handleBackCopy(curPkAndState, (PointerKey) dst, label.bar());
          }

        };
        g.visitPreds(curPk, predVisitor);
        IFlowLabelVisitor succVisitor = new AbstractFlowLabelVisitor() {

          @Override
          public void visitPutField(PutFieldLabel label, Object dst) {
            IField field = (label).getField();
            if (refineFieldAccesses(field)) {
              // x.f = y, X updated
              // if Y' non-empty, then update
              // tracked set of X.f, to trace flow
              // to reads
              PointerKeyAndState storeDst = new PointerKeyAndState((PointerKey) dst, curState);
              encounteredStores.add(new StoreEdge(curPkAndState, field, storeDst));
              IntSet trackedSet = find(pkToTrackedSet, storeDst);
              if (!trackedSet.isEmpty()) {
                for (InstanceKeyAndState ikAndState : makeOrdinalSet(find(pkToP2Set, curPkAndState))) {
                  InstanceFieldKeyAndState ifk = getInstFieldKey(ikAndState, field);
                  findOrCreate(instFieldKeyToTrackedSet, ifk).addAll(trackedSet);
                  trackInstanceField(ikAndState, field, backInstKeyToFields);
                }
              }
            }
          }
        };
        g.visitSuccs(curPk, succVisitor);
        handleBackInterproc(curPkAndState, new CopyHandler() {

          @Override
          void handle(PointerKeyAndState src, PointerKey dst, IFlowLabel label) {
            handleBackCopy(src, dst, label);
          }

        }, false);
      }
    }

    /**
     * handle flow from return value to callers, or from actual to formals
     * 
     * @param curPkAndState
     * @param handler
     */
    private void handleBackInterproc(final PointerKeyAndState curPkAndState, final CopyHandler handler, final boolean addGraphs) {
      final PointerKey curPk = curPkAndState.getPointerKey();
      final State curState = curPkAndState.getState();
      // interprocedural edges
      if (curPk instanceof ReturnValueKey) {
        final ReturnValueKey returnKey = (ReturnValueKey) curPk;
        if (DEBUG) {
          Trace.println("return value");
        }
        final CGNode callee = returnKey.getNode();
        if (DEBUG) {
          Trace.println("returning from " + callee);
          // Trace.println("CALL GRAPH:\n" + cg);
          // Trace.println(new
          // Iterator2Collection(cg.getPredNodes(cgNode)));
        }
        final boolean isExceptional = returnKey instanceof ExceptionReturnValueKey;
        // iterate over callers
        for (Iterator<? extends CGNode> iter = cg.getPredNodes(callee); iter.hasNext();) {
          final CGNode caller = iter.next();
          if (!addGraphs) {
            // shouldn't need to add the graph, so check if it is present;
            // if not, terminate
            if (!g.hasSubgraphForNode(caller)) {
              continue;
            }
          }
          final IR ir = getIR(caller);
          for (Iterator<CallSiteReference> iterator = cg.getPossibleSites(caller, callee); iterator.hasNext();) {
            final CallSiteReference call = iterator.next();
            final CallSiteAndCGNode callSiteAndCGNode = new CallSiteAndCGNode(call, caller);
            final ReturnBarLabel returnBarLabel = ReturnBarLabel.make(callSiteAndCGNode);
            doTransition(curState, returnBarLabel, new Function<State, Object>() {

              private void propagateToCaller() {
                if (caller.getIR() == null) {
                  return;
                }
                g.addSubgraphForNode(caller);
                SSAAbstractInvokeInstruction[] callInstrs = ir.getCalls(call);
                for (int i = 0; i < callInstrs.length; i++) {
                  SSAAbstractInvokeInstruction callInstr = callInstrs[i];
                  PointerKey returnAtCallerKey = heapModel.getPointerKeyForLocal(caller, isExceptional ? callInstr.getException()
                      : callInstr.getDef());
                  if (Assertions.verifyAssertions) {
                    Assertions._assert(g.containsNode(returnAtCallerKey));
                    Assertions._assert(g.containsNode(returnKey));
                  }
                  handler.handle(curPkAndState, returnAtCallerKey, returnBarLabel);
                }
              }

              public Object apply(State callerState) {
                // if (DEBUG) {
                // Trace.println("caller " + caller);
                // }
                Set<CGNode> possibleTargets = cg.getPossibleTargets(caller, call);
                if (noOnTheFlyNeeded(callSiteAndCGNode, possibleTargets)) {
                  propagateToCaller();
                } else {
                  if (callToOTFTargets.get(callSiteAndCGNode).contains(callee.getMethod())) {
                    // already found this target as valid, so do propagation
                    propagateToCaller();
                  } else {
                    // if necessary, start a query for the call site
                    queryCallTargets(callSiteAndCGNode, ir, callerState);
                  }
                }
                return null;
              }

            });
          }
        }
      }
      if (curPk instanceof LocalPointerKey) {
        LocalPointerKey localPk = (LocalPointerKey) curPk;
        CGNode caller = localPk.getNode();
        // from parameter to callee
        for (Iterator<SSAInvokeInstruction> iter = g.getInstrsPassingParam(localPk); iter.hasNext();) {
          SSAInvokeInstruction callInstr = iter.next();
          for (int i = 0; i < callInstr.getNumberOfUses(); i++) {
            if (localPk.getValueNumber() != callInstr.getUse(i))
              continue;
            CallSiteReference callSiteRef = callInstr.getCallSite();
            CallSiteAndCGNode callSiteAndCGNode = new CallSiteAndCGNode(callSiteRef, caller);
            // get call targets
            Set<CGNode> possibleCallees = cg.getPossibleTargets(caller, callSiteRef);
            // construct graph for each target
            if (noOnTheFlyNeeded(callSiteAndCGNode, possibleCallees)) {
              for (CGNode callee : possibleCallees) {
                if (!addGraphs) {
                  // shouldn't need to add the graph, so check if it is present;
                  // if not, terminate
                  if (!g.hasSubgraphForNode(callee)) {
                    continue;
                  }
                }
                if (callee.getIR() == null) {
                  continue;
                }
                g.addSubgraphForNode(callee);
                PointerKey paramVal = heapModel.getPointerKeyForLocal(callee, i + 1);
                if (Assertions.verifyAssertions) {
                  Assertions._assert(g.containsNode(paramVal));
                }
                handler.handle(curPkAndState, paramVal, ParamBarLabel.make(callSiteAndCGNode));
              }
            } else {
              if (callToOTFTargets.containsKey(callSiteAndCGNode)) {
                // already queried this call site
                // handle existing targets
                Set<IMethod> targetMethods = callToOTFTargets.get(callSiteAndCGNode);
                for (CGNode callee : possibleCallees) {
                  if (targetMethods.contains(callee.getMethod())) {
                    if (caller.getIR() == null) {
                      continue;
                    }
                    g.addSubgraphForNode(callee);
                    PointerKey paramVal = heapModel.getPointerKeyForLocal(callee, i + 1);
                    if (Assertions.verifyAssertions) {
                      Assertions._assert(g.containsNode(paramVal));
                    }
                    handler.handle(curPkAndState, paramVal, ParamBarLabel.make(callSiteAndCGNode));
                  }
                }
              } else {
                // if necessary, raise a query for the call site
                queryCallTargets(callSiteAndCGNode, getIR(caller), curState);
              }
            }
          }
        }
      }
    }

    public void handleTrackedPointsToWorklist() {
      // if (Assertions.verifyAssertions) {
      // Assertions._assert(trackedPointsToWorklist.isEmpty() || refineFields);
      // }
      while (!trackedPointsToWorklist.isEmpty()) {
        incrementNumNodesTraversed();
        final PointerKeyAndState curPkAndState = trackedPointsToWorklist.iterator().next();
        trackedPointsToWorklist.remove(curPkAndState);
        final PointerKey curPk = curPkAndState.getPointerKey();
        final State curState = curPkAndState.getState();
        if (DEBUG)
          Trace.println("tracked points-to " + curPkAndState);
        final MutableIntSet trackedSet = find(pkToTrackedSet, curPkAndState);
        IFlowLabelVisitor succVisitor = new AbstractFlowLabelVisitor() {

          @Override
          public void visitPutField(PutFieldLabel label, Object dst) {
            // statements x.f = y, X' updated, f in map
            // query y; if already queried, add Y to Z.f for all
            // z in X'
            IField field = label.getField();
            if (refineFieldAccesses(field)) {
              for (InstanceKeyAndState ikAndState : makeOrdinalSet(trackedSet)) {
                boolean needField = forwInstKeyToFields.get(ikAndState).contains(field);
                PointerKeyAndState storeDst = new PointerKeyAndState((PointerKey) dst, curState);
                encounteredStores.add(new StoreEdge(curPkAndState, field, storeDst));
                if (needField) {
                  if (!addToInitWorklist(storeDst)) {
                    InstanceFieldKeyAndState ifk = getInstFieldKey(ikAndState, field);
                    findOrCreate(instFieldKeyToP2Set, ifk).addAll(find(pkToP2Set, storeDst));
                  }
                }
              }
            }
          }
        };
        g.visitSuccs(curPk, succVisitor);
        IFlowLabelVisitor predVisitor = new AbstractFlowLabelVisitor() {

          @Override
          public void visitAssignGlobal(AssignGlobalLabel label, Object dst) {
            for (Iterator<? extends Object> readIter = g.getReadsOfStaticField((StaticFieldKey) dst); readIter.hasNext();) {
              final PointerKey predPk = (PointerKey) readIter.next();
              doTransition(curState, AssignGlobalBarLabel.v(), new Function<State, Object>() {

                public Object apply(State predPkState) {
                  PointerKeyAndState predPkAndState = new PointerKeyAndState(predPk, predPkState);
                  if (findOrCreate(pkToTrackedSet, predPkAndState).addAll(trackedSet)) {
                    addToTrackedPToWorklist(predPkAndState);
                  }
                  return null;
                }

              });
            }
          }

          @Override
          public void visitPutField(PutFieldLabel label, Object dst) {
            IField field = label.getField();
            if (refineFieldAccesses(field)) {
              PointerKeyAndState storeBase = new PointerKeyAndState((PointerKey) dst, curState);
              encounteredStores.add(new StoreEdge(storeBase, field, curPkAndState));
              if (!addToInitWorklist(storeBase)) {
                for (InstanceKeyAndState ikAndState : makeOrdinalSet(find(pkToP2Set, storeBase))) {
                  InstanceFieldKeyAndState ifk = getInstFieldKey(ikAndState, field);
                  findOrCreate(instFieldKeyToTrackedSet, ifk).addAll(trackedSet);
                  trackInstanceField(ikAndState, field, backInstKeyToFields);
                }
              }
            } else {
              // send to all getfield sources
              for (Iterator<PointerKey> readIter = g.getReadsOfInstanceField(field); readIter.hasNext();) {
                final PointerKey predPk = readIter.next();
                doTransition(curState, MatchBarLabel.v(), new Function<State, Object>() {

                  public Object apply(State predPkState) {
                    PointerKeyAndState predPkAndState = new PointerKeyAndState(predPk, predPkState);
                    if (findOrCreate(pkToTrackedSet, predPkAndState).addAll(trackedSet)) {
                      addToTrackedPToWorklist(predPkAndState);
                    }
                    return null;
                  }

                });
              }

            }
          }

          @Override
          public void visitGetField(GetFieldLabel label, Object dst) {
            IField field = label.getField();
            // x = y.f, Y' updated
            if (refineFieldAccesses(field)) {
              for (InstanceKeyAndState ikAndState : makeOrdinalSet(trackedSet)) {
                // tracking value written into ik.field
                boolean needField = backInstKeyToFields.get(ikAndState).contains(field);
                PointerKeyAndState loadedVal = new PointerKeyAndState((PointerKey) dst, curState);
                addEncounteredLoad(new LoadEdge(curPkAndState, field, loadedVal));
                if (needField) {
                  InstanceFieldKeyAndState ifk = getInstFieldKey(ikAndState, field);
                  if (findOrCreate(pkToTrackedSet, loadedVal).addAll(find(instFieldKeyToTrackedSet, ifk))) {
                    addToTrackedPToWorklist(loadedVal);
                  }
                }
              }
            }
          }

          @Override
          public void visitAssign(AssignLabel label, Object dst) {
            final PointerKey predPk = (PointerKey) dst;
            doTransition(curState, AssignBarLabel.v(), new Function<State, Object>() {

              public Object apply(State predPkState) {
                PointerKeyAndState predPkAndState = new PointerKeyAndState(predPk, predPkState);
                if (findOrCreate(pkToTrackedSet, predPkAndState).addAll(trackedSet)) {
                  addToTrackedPToWorklist(predPkAndState);
                }
                return null;
              }

            });
          }

        };
        g.visitPreds(curPk, predVisitor);
        handleBackInterproc(curPkAndState, new CopyHandler() {

          @Override
          void handle(PointerKeyAndState src, final PointerKey dst, IFlowLabel label) {
            if (Assertions.verifyAssertions) {
              Assertions._assert(src == curPkAndState);
            }
            doTransition(curState, label, new Function<State, Object>() {

              public Object apply(State dstState) {
                PointerKeyAndState dstAndState = new PointerKeyAndState(dst, dstState);
                if (findOrCreate(pkToTrackedSet, dstAndState).addAll(trackedSet)) {
                  addToTrackedPToWorklist(dstAndState);
                }
                return null;
              }

            });
          }

        }, true);
      }
    }

    private void addEncounteredLoad(LoadEdge loadEdge) {
      if (encounteredLoads.add(loadEdge)) {
        // if (DEBUG) {
        // Trace.println("encountered load edge " + loadEdge);
        // }
      }
    }

    public void makePassOverFieldStmts() {
      for (StoreEdge storeEdge : encounteredStores) {
        PointerKeyAndState storedValAndState = storeEdge.val;
        IField field = storeEdge.field;
        PointerKeyAndState baseAndState = storeEdge.base;
        // x.f = y, X' updated
        // for each z in X' such that f in z's map,
        // add Y to Z.f
        IntSet trackedSet = find(pkToTrackedSet, baseAndState);
        for (InstanceKeyAndState ikAndState : makeOrdinalSet(trackedSet)) {
          if (forwInstKeyToFields.get(ikAndState).contains(field)) {
            if (!addToInitWorklist(storedValAndState)) {
              InstanceFieldKeyAndState ifk = getInstFieldKey(ikAndState, field);
              findOrCreate(instFieldKeyToP2Set, ifk).addAll(find(pkToP2Set, storedValAndState));
            }
          }
        }
      }
      for (LoadEdge loadEdge : encounteredLoads) {
        PointerKeyAndState loadedValAndState = loadEdge.val;
        IField field = loadEdge.field;
        PointerKey basePointerKey = loadEdge.base.getPointerKey();
        State loadDstState = loadedValAndState.getState();
        PointerKeyAndState baseAndStateToHandle = new PointerKeyAndState(basePointerKey, loadDstState);
        if (Assertions.verifyAssertions) {
          boolean basePointerOkay = pointsToQueried.get(basePointerKey).contains(loadDstState)
              || !pointsToQueried.get(loadedValAndState.getPointerKey()).contains(loadDstState)
              || initWorklist.contains(loadedValAndState);
          // if (!basePointerOkay) {
          // Trace.println("ASSERTION WILL FAIL");
          // Trace.println("QUERIED: " + queriedPkAndStates);
          // }
          Assertions._assert(basePointerOkay, "queried " + loadedValAndState + " but not " + baseAndStateToHandle);
        }
        final IntSet curP2Set = find(pkToP2Set, baseAndStateToHandle);
        // int startSize = curP2Set.size();
        // int curSize = -1;
        for (InstanceKeyAndState ikAndState : makeOrdinalSet(curP2Set)) {
          // curSize = curP2Set.size();
          // if (Assertions.verifyAssertions) {
          // Assertions._assert(startSize == curSize);
          // }
          InstanceFieldKeyAndState ifk = getInstFieldKey(ikAndState, field);
          if (addAllToP2Set(pkToP2Set, loadedValAndState, find(instFieldKeyToP2Set, ifk))) {
            if (DEBUG) {
              Trace.println("from load edge " + loadEdge);
            }
            addToPToWorklist(loadedValAndState);
          }
        }
        // }
        // x = y.f, Y' updated
        PointerKeyAndState baseAndState = loadEdge.base;
        for (InstanceKeyAndState ikAndState : makeOrdinalSet(find(pkToTrackedSet, baseAndState))) {
          if (backInstKeyToFields.get(ikAndState).contains(field)) {
            // tracking value written into ik.field
            InstanceFieldKeyAndState ifk = getInstFieldKey(ikAndState, field);
            if (findOrCreate(pkToTrackedSet, loadedValAndState).addAll(find(instFieldKeyToTrackedSet, ifk))) {
              if (DEBUG) {
                Trace.println("from load edge " + loadEdge);
              }
              addToTrackedPToWorklist(loadedValAndState);
            }
          }
        }
      }
    }

    private InstanceFieldKeyAndState getInstFieldKey(InstanceKeyAndState ikAndState, IField field) {
      return new InstanceFieldKeyAndState(new InstanceFieldKey(ikAndState.getInstanceKey(), field), ikAndState.getState());
    }

    private <K> MutableIntSet findOrCreate(Map<K, MutableIntSet> M, K key) {
      MutableIntSet result = M.get(key);
      if (result == null) {
        result = intSetFactory.make();
        M.put(key, result);
      }
      return result;
    }

    private final MutableIntSet emptySet = intSetFactory.make();

    private <K> MutableIntSet find(Map<K, MutableIntSet> M, K key) {
      MutableIntSet result = M.get(key);
      if (result == null) {
        result = emptySet;
      }
      return result;
    }
  }

  private IR getIR(CGNode node) {
    return node.getIR();
  }

  private Object doTransition(State curState, IFlowLabel label, Function<State, Object> func) {
    State nextState = stateMachine.transition(curState, label);
    Object ret = null;
    if (nextState != StateMachine.ERROR) {
      ret = func.apply(nextState);
    } else {
      // System.err.println("filtered at edge " + label);
    }
    return ret;
  }

  public StateMachineFactory<IFlowLabel> getStateMachineFactory() {
    return stateMachineFactory;
  }

  public void setStateMachineFactory(StateMachineFactory<IFlowLabel> stateMachineFactory) {
    this.stateMachineFactory = stateMachineFactory;
  }

  public RefinementPolicyFactory getRefinementPolicyFactory() {
    return refinementPolicyFactory;
  }

  public void setRefinementPolicyFactory(RefinementPolicyFactory refinementPolicyFactory) {
    this.refinementPolicyFactory = refinementPolicyFactory;
  }

}
