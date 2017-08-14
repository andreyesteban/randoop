package randoop.condition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The collection of all pre-, return-, and throws-conditions defined on this operation and
 * inherited from supertypes.
 */
public class OperationConditions {

  /** The pre-conditions for the operation */
  private final List<BooleanExpression> guardExpressions;

  /** The return-conditions. */
  private final List<GuardPropertyPair> prePostConditionPairs;

  /** The throws-conditions. */
  private final List<GuardThrowsPair> preThrowsConditionPairs;

  /**
   * Mirrors the overrides/implements relation among methods. If this OperationConditions is the
   * local specification for method m, the {@code parentList} contains one element for each method
   * that m overrides or implements.
   *
   * <p>For an operation that is a method, the {@link OperationConditions} form an arbitrary
   * directed acyclic graph consisting of conditions for methods of supertypes, each of which has
   * attached specifications.
   */
  private List<OperationConditions> parentList = new ArrayList<>();

  /** Creates an empty {@link OperationConditions} object. */
  OperationConditions() {
    this(
        new ArrayList<BooleanExpression>(),
        new ArrayList<GuardPropertyPair>(),
        new ArrayList<GuardThrowsPair>());
  }

  /**
   * Creates an {@link OperationConditions} object for the given pre-conditions, pre-post-condition
   * pairs, and pre-throws condition pairs.
   *
   * @param guardExpressions the pre-conditions
   * @param prePostConditionPairs the pre-post-condition pairs
   * @param preThrowsConditionPairs the pre-throws condition pairs
   */
  OperationConditions(
      List<BooleanExpression> guardExpressions,
      List<GuardPropertyPair> prePostConditionPairs,
      List<GuardThrowsPair> preThrowsConditionPairs) {
    this.guardExpressions = guardExpressions;
    this.prePostConditionPairs = prePostConditionPairs;
    this.preThrowsConditionPairs = preThrowsConditionPairs;
  }

  /**
   * Check the pre-conditions for this operation against the arguments. Constructs an {@link
   * ExpectedOutcomeTable} for this operation, and for this operation in all supertypes.
   *
   * @param args the argument values to test the guardExpressions
   * @return the table with entries for this operation
   * @see #check(Object[], ExpectedOutcomeTable)
   */
  public ExpectedOutcomeTable check(Object[] args) {
    ExpectedOutcomeTable table = new ExpectedOutcomeTable();
    this.check(args, table);
    for (OperationConditions conditions : parentList) {
      conditions.check(args, table);
    }
    return table;
  }

  /**
   * Modifies the given table, adding an {@link ExpectedOutcomeTable} entry for the guard
   * expressions of this method.
   *
   * <ol>
   *   <li>Whether the preconditions of the specification fail or are satisfied. The preconditions
   *       fail if the Boolean expression of any precondition in the specification is false.
   *       Otherwise, the preconditions are satisfied. See {@link
   *       randoop.condition.OperationConditions#checkPreconditions(java.lang.Object[])}.
   *   <li>A set of expected exceptions. Evaluate the guard of each throws-condition, and for each
   *       one satisfied, add the exception to the set of expected exceptions. (There will be one
   *       set per specification.) See {@link
   *       randoop.condition.OperationConditions#checkThrowsPreconditions(java.lang.Object[])}.
   *   <li>The expected postcondition, if any. If the preconditions are satisfied, test the guards
   *       of the normal postconditions of the specification in order, and save the property for the
   *       first guard satisfied, if there is one. See {@link
   *       randoop.condition.OperationConditions#checkPostconditionGuards(java.lang.Object[])}.
   * </ol>
   *
   * <p>(See the evaluation algorithm in {@link randoop.condition}.)
   *
   * @param args the argument values
   * @param table the table to which the created entry is to be added
   */
  private void check(Object[] args, ExpectedOutcomeTable table) {
    boolean preconditionCheck = checkPreconditions(args);
    Set<ThrowsClause> throwsClauses = checkThrowsPreconditions(args);
    PropertyExpression postCondition = checkPostconditionGuards(args);
    table.add(preconditionCheck, throwsClauses, postCondition);
  }

  /**
   * Tests the given argument values against the guardExpressions in this {@link
   * OperationConditions}. The guardExpressions fail if any precondition evaluates to false on the
   * arguments.
   *
   * @param args the argument values
   * @return false if any precondition fails on the argument values, true if all guardExpressions
   *     succeed
   */
  private boolean checkPreconditions(Object[] args) {
    for (BooleanExpression preCondition : guardExpressions) {
      if (!preCondition.check(args)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tests the given argument values against the guards of the pre-throws-condition pairs in this
   * {@link OperationConditions} and returns the set of throws-conditions whose precondition
   * evaluated to true.
   *
   * @param args the argument values
   * @return the set of exceptions for which the precondition evaluated to true
   */
  private Set<ThrowsClause> checkThrowsPreconditions(Object[] args) {
    Set<ThrowsClause> throwsClauses = new LinkedHashSet<>();
    for (GuardThrowsPair pair : preThrowsConditionPairs) {
      BooleanExpression guardExpression = pair.guardExpression;
      if (guardExpression.check(args)) {
        throwsClauses.add(pair.throwsClause);
      }
    }
    return throwsClauses;
  }

  /**
   * Tests the given argument values against the guards of the post-conditions in this {@link
   * OperationConditions} and returns the first post-condition whose precondition evaluated to true.
   *
   * @param args the argument values
   * @return the first post-condition for which the precodition evaluates to true; null if there is
   *     none
   */
  private PropertyExpression checkPostconditionGuards(Object[] args) {
    for (GuardPropertyPair pair : prePostConditionPairs) {
      BooleanExpression precondition = pair.guardExpression;
      if (precondition.check(args)) {
        return pair.propertyExpression.addPrestate(args);
      }
    }
    return null;
  }

  /**
   * Add the parent {@link OperationConditions} for this collection of conditions.
   *
   * @param parentConditions the {@link OperationConditions} to which to link
   */
  void addParent(OperationConditions parentConditions) {
    parentList.add(parentConditions);
  }

  /**
   * Indicates whether this {@link OperationConditions}, and the parent, has no conditions.
   *
   * @return true if there are no pre-, post-, or throws-conditions in this or the parent, false
   *     otherwise
   */
  public boolean isEmpty() {
    if (!(guardExpressions.isEmpty()
        && prePostConditionPairs.isEmpty()
        && preThrowsConditionPairs.isEmpty())) {
      return false;
    }
    for (OperationConditions conditions : parentList) {
      if (!conditions.isEmpty()) {
        return false;
      }
    }
    return true;
  }
}
