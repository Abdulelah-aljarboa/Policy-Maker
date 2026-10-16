# Fraud Policy Engine — Design & Developer Guide

## 1. What this system does

After the enrichment step completes for a case item (establishment, person, and
engagement data all loaded), the system evaluates a set of user-defined
**policies** against that case item. Policies are authored and reordered by
end users at runtime through an admin UI — no code deploy required to add,
change, or reorder a rule.

Each policy is an **ordered set of conditions** (rules) that, when fully
satisfied, produces an **outcome** — setting fields on the inspection request
(priority, visit type, risk) sent to Rased, or performing an action such as
assigning the case to a user.

Policies are evaluated **in order, first match wins**. The first policy whose
conditions are satisfied by the case item "fires," and its actions are
applied. No further policies are evaluated for that case item.

### Core requirements this design satisfies

- Users define rules comparing fields across three enriched entities
  (`establishment`, `person`, `engagement`) using operators appropriate to
  each field's type.
- Users can combine rules with **arbitrary AND/OR nesting** —
  `A AND B OR (C AND D)` is expressible, not just a flat list.
- Users can **reorder policies** and reorder/add/remove rules within a
  policy, at runtime, through a UI.
- Policies can target **different systems** (e.g. Fraud, Triage, or a new system) independently.
- Policies support a **draft → test → publish** lifecycle so a user can try a
  policy against sample data before it affects real case items.
- Actions are **not limited to "set a field"** — the system supports
  arbitrary action types (e.g. "assign to a user") via a pluggable handler
  registry, so adding a new kind of action later requires no changes to the
  evaluation engine.

---

## 2. Why this shape (and not an off-the-shelf rules engine)

We considered Drools, Easy Rules, GoRules/Zen Engine (JDM decision tables),
and SpEL-based approaches before settling on a custom domain model. The
deciding factors:

- **Arbitrary AND/OR nesting with a visual, non-technical UI** is awkward to
  express in decision-table engines (JDM, OpenL) without falling back to
  typed expression strings in table cells, which defeats the goal of a
  form-driven rule builder.
- **Full control over evaluation semantics** (first-match-wins across
  policies, short-circuiting AND/OR, field-to-field comparisons, custom
  action types) is simplest when we own the evaluator outright.
- **No external engine dependency** to operate, upgrade, or work around when
  a new requirement doesn't fit the engine's model (e.g. comparing two
  enrichment fields to each other, or calling a Java service mid-evaluation).

The tradeoff: we write and maintain more code than dropping in a library.
That cost is accepted deliberately — the system is simple enough (an
AND/OR expression tree over typed field comparisons) that hand-rolling it is
low-risk and gives us an evaluator with no ceiling on future requirements.

---

## 3. Domain model

### 3.1 Conceptual structure

```
Policy                              (ordered: evaluated first-match-wins, per system)
└─ RuleGroup (root)                 (AND / OR — recursive tree, arbitrary depth)
   ├─ RuleGroup (child)             (nested AND/OR)
   │  ├─ Rule                       (leaf: one field comparison)
   │  └─ Rule
   └─ Rule
Policy
└─ PolicyAction (1..N)              (outcome: SET_FIELD, ASSIGN_USER, ...)
```

A `RuleGroup` is a **self-referencing node** — it can contain child
`RuleGroup`s, leaf `Rule`s, or both, combined by its own single
`logicOperator` (AND or OR). This is what allows arbitrary nesting: the tree
has no fixed depth limit, and the same recursive evaluator handles every
level identically.

**Key invariant:** a group never mixes AND and OR *within itself* — if a
user needs to combine both at the same conceptual "level," they nest a new
sub-group. This is what makes authoring unambiguous: the tree structure *is*
the expression, so there's no operator-precedence parsing needed anywhere in
the system (unlike `X AND Y OR Z` typed as raw text, which is genuinely
ambiguous without precedence rules).

### 3.2 Database schema

```sql
CREATE TABLE policy (
    id            NUMBER PRIMARY KEY,
    name          VARCHAR2(200) NOT NULL,
    description   VARCHAR2(1000),
    system_code   VARCHAR2(50) NOT NULL,   -- which downstream system this policy applies to
    status        VARCHAR2(20) DEFAULT 'DRAFT' NOT NULL, -- DRAFT | PUBLISHED | ARCHIVED
    order_index   NUMBER NOT NULL,         -- evaluation order within (system_code, status=PUBLISHED)
    active        NUMBER(1) DEFAULT 1,
    root_group_id NUMBER NOT NULL          -- FK to rule_group, entry point of the tree
);

CREATE UNIQUE INDEX uq_policy_system_order ON policy (system_code, order_index);

CREATE TABLE rule_group (
    id              NUMBER PRIMARY KEY,
    policy_id       NUMBER,                -- set ONLY on the root group of a policy, else null
    parent_group_id NUMBER,                -- FK to rule_group.id, null for root
    logic_operator  VARCHAR2(3) NOT NULL,  -- 'AND' or 'OR'
    order_index     NUMBER NOT NULL,       -- child display/evaluation order within parent
    CONSTRAINT fk_rg_policy FOREIGN KEY (policy_id) REFERENCES policy(id),
    CONSTRAINT fk_rg_parent FOREIGN KEY (parent_group_id) REFERENCES rule_group(id)
);

CREATE TABLE param (
    id          NUMBER PRIMARY KEY,
    entity_name VARCHAR2(50) NOT NULL,     -- 'ESTABLISHMENT' | 'PERSON' | 'ENGAGEMENT'
    param_name  VARCHAR2(100) NOT NULL,    -- 'legalEntity', 'wage', 'engagementType'
    param_type  VARCHAR2(20) NOT NULL,     -- 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN'
    CONSTRAINT uq_param UNIQUE (entity_name, param_name)
);

CREATE TABLE param_operator (
    id       NUMBER PRIMARY KEY,
    param_id NUMBER NOT NULL,
    operator VARCHAR2(20) NOT NULL,        -- '=', '!=', '>', '<', 'IN', 'NOT_IN', 'BETWEEN', ...
    CONSTRAINT fk_po_param FOREIGN KEY (param_id) REFERENCES param(id),
    CONSTRAINT uq_param_op UNIQUE (param_id, operator)
);

CREATE TABLE rule (
    id           NUMBER PRIMARY KEY,
    group_id     NUMBER NOT NULL,          -- parent rule_group (every leaf lives inside a group)
    param_id     NUMBER NOT NULL,
    operator     VARCHAR2(20) NOT NULL,    -- must be allowed by param_operator for this param
    value_type   VARCHAR2(10) DEFAULT 'LITERAL', -- 'LITERAL' | 'PARAM_REF'
    ref_param_id NUMBER,                   -- set only if value_type = PARAM_REF (field-to-field compare)
    negate       NUMBER(1) DEFAULT 0,
    order_index  NUMBER NOT NULL,
    CONSTRAINT fk_rule_group FOREIGN KEY (group_id) REFERENCES rule_group(id),
    CONSTRAINT fk_rule_param FOREIGN KEY (param_id) REFERENCES param(id),
    CONSTRAINT fk_rule_ref_param FOREIGN KEY (ref_param_id) REFERENCES param(id)
);

CREATE TABLE rule_value (
    id      NUMBER PRIMARY KEY,
    rule_id NUMBER NOT NULL,
    value   VARCHAR2(500) NOT NULL,        -- raw string; coerced to the real type at tree-build time
    CONSTRAINT fk_rv_rule FOREIGN KEY (rule_id) REFERENCES rule(id)
);

CREATE TABLE policy_action (
    id          NUMBER PRIMARY KEY,
    policy_id   NUMBER NOT NULL,
    action_type VARCHAR2(50) NOT NULL,     -- 'SET_FIELD' | 'ASSIGN_USER' | ... (extensible)
    output_param_name VARCHAR2(100),       -- used by SET_FIELD
    output_value       VARCHAR2(500),      -- used by SET_FIELD
    CONSTRAINT fk_pa_policy FOREIGN KEY (policy_id) REFERENCES policy(id)
);

-- Handler-specific config that doesn't fit output_param_name/output_value —
-- e.g. ASSIGN_USER's "strategy" / "queueId". PolicyAction.getParam(key) reads
-- these; PolicyAction.addParam(key, value) writes them (see §5.2 entity).
CREATE TABLE policy_action_param (
    id          NUMBER PRIMARY KEY,
    action_id   NUMBER NOT NULL,
    param_key   VARCHAR2(100) NOT NULL,
    param_value VARCHAR2(500) NOT NULL,
    CONSTRAINT fk_pap_action FOREIGN KEY (action_id) REFERENCES policy_action(id)
);
```

### 3.3 Example: expressing `(X AND Y) OR (Z AND (W OR V))`

| id | policy_id | parent_group_id | logic_operator |
|----|-----------|------------------|----------------|
| 10 | 100       | null             | OR (root)      |
| 11 | null      | 10               | AND            |
| 12 | null      | 10               | AND            |
| 13 | null      | 12               | OR             |

Rules X, Y → `group_id = 11`. Rule Z → `group_id = 12`. Rules W, V →
`group_id = 13`.

A **pure-AND or pure-OR policy** (the common case) is just a single root
group with no child groups — tree depth 1. Nesting is only introduced the
moment a policy needs to mix AND and OR, since that's the only situation
with genuine ambiguity about grouping (see §6).

---

## 4. The evaluation engine

### 4.1 Why we don't evaluate against JPA entities directly

Loading `RuleGroup`/`Rule` JPA entities and walking lazy-loaded associations
once **per case item** would be wasteful — the rule structure for a given
policy doesn't change between case items. Instead:

1. Build an **immutable in-memory tree** (`RuleNode`) from the JPA entities
   **once**, whenever a policy is created, edited, published, or on startup.
2. **Cache** that tree.
3. Evaluate the cached tree against each `CaseItemContext`, as many times as
   needed, with no further DB traversal.

### 4.2 The tree types

```java
sealed interface RuleNode permits GroupNode, LeafNode {
    boolean evaluate(CaseItemContext ctx);
}

record GroupNode(LogicOperator operator, List<RuleNode> children) implements RuleNode {
    public boolean evaluate(CaseItemContext ctx) {
        return switch (operator) {
            case AND -> children.stream().allMatch(c -> c.evaluate(ctx)); // short-circuits; true on empty list
            case OR  -> children.stream().anyMatch(c -> c.evaluate(ctx)); // short-circuits; false on empty list
        };
    }
}

record LeafNode(
    String entityName, String paramName, ParamType paramType,
    String operator, ValueType valueType,
    List<Object> literalValues,          // already type-coerced at build time
    String refEntityName, String refParamName,  // set only if valueType = PARAM_REF
    boolean negate
) implements RuleNode {
    public boolean evaluate(CaseItemContext ctx) {
        Object actual = ctx.resolve(entityName, paramName);
        Object comparisonTarget = valueType == ValueType.PARAM_REF
            ? ctx.resolve(refEntityName, refParamName)
            : (literalValues.size() == 1 ? literalValues.get(0) : literalValues);
        boolean result = OperatorEvaluator.apply(operator, actual, comparisonTarget, paramType);
        return negate != result; // XOR-style negation
    }
}
```

Note the **vacuous-truth property** of `GroupNode`: an `AND` group with zero
children evaluates to `true` (there's nothing to violate); an `OR` group
with zero children evaluates to `false`. This matters for how a "default /
catch-all" policy is conventionally authored — see §8.

### 4.3 `CaseItemContext` — resolving field paths

```java
public class CaseItemContext {
    private final CaseItemEntity caseItem;
    private final EstablishmentEnrichment establishment;
    private final PersonEnrichment person;
    private final EngagementEnrichment engagement;

    public Object resolve(String entityName, String paramName) {
        Object target = switch (entityName) {
            case "ESTABLISHMENT" -> establishment;
            case "PERSON" -> person;
            case "ENGAGEMENT" -> engagement;
            default -> throw new RuleEvaluationException("Unknown entity: " + entityName);
        };
        try {
            return PropertyAccessorFactory.forBeanPropertyAccess(target)
                .getPropertyValue(paramName);
        } catch (InvalidPropertyException e) {
            throw new RuleEvaluationException(
                "Unknown param: " + entityName + "." + paramName
                    + " — check the Param table matches the entity's getters", e);
        }
    }
}
```

We use Spring's `PropertyAccessorFactory.forBeanPropertyAccess(Object)` (the
supported factory entry point — **not** `new BeanWrapperImpl(...)`, which
Spring's own javadoc marks as primarily for internal framework use) to read
a named property off whichever enrichment bean the rule references. This
avoids hand-written reflection and a parallel "field accessor registry" that
would need manual upkeep every time a `Param` row is added.

A second constructor path, `CaseItemContext.fromRawMaps(...)`, supports the
**policy test flow** (§9) by wrapping plain `Map<String,Object>` test input
instead of real JPA entities — `resolve()` branches on whether the target is
a `Map` or a bean.

### 4.4 `OperatorEvaluator` and `ValueCoercer`

- **`ValueCoercer.coerce(rawValue, paramType)`** converts the raw string
  stored in `rule_value.value` into the real typed object (`BigDecimal`,
  `LocalDate`, `Boolean`, or `String`) based on `Param.paramType`. This runs
  **once per value, at tree-build time** — not on every evaluation — so a
  malformed value (e.g. `"abc"` in a NUMBER field) fails loudly when the
  tree is built (policy save, or cache reload), not buried in a hot
  per-case-item loop.
- **`OperatorEvaluator.apply(operator, actual, target, paramType)`**
  centralizes every comparison (`=`, `!=`, `>`, `<`, `>=`, `<=`, `IN`,
  `NOT_IN`, `BETWEEN`). List-based operators (`IN`, `NOT_IN`, `BETWEEN`) all
  funnel through a shared `asList(operator, target)` helper that wraps the
  `(List<?>) target` cast in a try/catch, converting any `ClassCastException`
  (wrong arity collapsed to a single value upstream) into a
  `RuleEvaluationException` with a message naming the operator and the bad
  value. `BETWEEN` additionally asserts exactly 2 elements after the cast
  succeeds (guards the 0-element case; the 1-element case is caught by the
  cast wrapper, since a single value never reaches here as a `List` at all).

### 4.5 `PolicyTreeBuilder` — JPA entities → `RuleNode` tree

```java
@Component
public class PolicyTreeBuilder {
    public RuleNode buildTree(RuleGroup dbGroup) {
        List<RuleNode> children = new ArrayList<>();
        for (RuleGroup child : dbGroup.getChildGroups()) {
            children.add(buildTree(child)); // recurse into sub-groups
        }
        for (Rule rule : dbGroup.getRules()) {
            children.add(toLeafNode(rule));  // convert leaf
        }
        return new GroupNode(dbGroup.getLogicOperator(), children);
    }

    private LeafNode toLeafNode(Rule rule) {
        List<Object> coercedValues = rule.getValues().stream()
            .map(rv -> ValueCoercer.coerce(rv.getValue(), rule.getParam().getParamType()))
            .toList();
        return new LeafNode(
            rule.getParam().getEntityName(), rule.getParam().getParamName(),
            rule.getParam().getParamType(), rule.getOperator(), rule.getValueType(),
            coercedValues,
            rule.getValueType() == ValueType.PARAM_REF ? rule.getRefParam().getEntityName() : null,
            rule.getValueType() == ValueType.PARAM_REF ? rule.getRefParam().getParamName() : null,
            Boolean.TRUE.equals(rule.getNegate())
        );
    }
}
```

Stateless; safe to call from anywhere. In production it's only ever called
from `PolicyTreeCache` (§4.6) and `PolicyTestService` (§9) — never directly
in the per-case-item hot path.

### 4.6 `PolicyTreeCache` — built once, keyed by system

```java
@Component
public class PolicyTreeCache {
    private volatile Map<String, List<PolicyTree>> cachedTreesBySystem = Map.of();

    @PostConstruct
    public void init() { reload(); }

    public synchronized void reload() {
        List<Policy> policies = policyRepository
            .findByStatusAndActiveTrueOrderBySystemCodeAscOrderIndexAsc(PolicyStatus.PUBLISHED);
        this.cachedTreesBySystem = policies.stream()
            .map(p -> new PolicyTree(p, treeBuilder.buildTree(p.getRootGroup())))
            .collect(Collectors.groupingBy(pt -> pt.policy().getSystemCode()));
    }

    public List<PolicyTree> getPoliciesInOrder(String systemCode) {
        return cachedTreesBySystem.getOrDefault(systemCode, List.of());
    }
}

record PolicyTree(Policy policy, RuleNode rootNode) {}
```

**Critical invariant:** the cache query filters on `status = PUBLISHED`. A
`DRAFT` policy is *never* loaded into this cache and therefore can never
affect a real case item, regardless of what order_index it has. `reload()`
must be called by `PolicyService` after every create, edit, publish, delete,
or reorder operation so the cache never goes stale.

Grouping by `systemCode` means system A's policies and system B's policies
are completely independent lists — their `order_index` values don't compete
with each other, and evaluating for system A never even looks at system B's
policies.

### 4.7 `PolicyEvaluationService` — the per-case-item entry point

```java
@Component
public class PolicyEvaluationService {
    public Optional<Policy> evaluateFirstMatch(String systemCode, CaseItemContext ctx) {
        for (PolicyTree pt : treeCache.getPoliciesInOrder(systemCode)) {
            try {
                if (pt.rootNode().evaluate(ctx)) {
                    return Optional.of(pt.policy());
                }
            } catch (RuleEvaluationException e) {
                log.error("Policy '{}' (id={}) misconfigured, skipping: {}",
                    pt.policy().getName(), pt.policy().getId(), e.getMessage());
                // falls through to the loop's next iteration — no explicit `continue` needed
            }
        }
        return Optional.empty();
    }
}
```

**Error containment:** because `evaluate()` is recursive, an exception
thrown deep inside a nested leaf propagates up through every parent
`GroupNode.evaluate()` call automatically (normal Java exception unwinding)
and surfaces at this one `try/catch`. This means a single misconfigured rule
anywhere in a policy's tree only ever skips *that policy* — logged loudly —
never the whole batch and never the other policies in the list. No
try/catch is needed inside `GroupNode` or `LeafNode` themselves.


```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void processChunk(List<CaseItemEntity> caseItems) {
    for (CaseItemEntity caseItem : caseItems) {
        CaseItemContext ctx = buildContext(caseItem);
        Optional<Policy> matched = policyEvaluationService.evaluateFirstMatch("RASED", ctx);

        InspectionRequest request = buildBaseInspectionRequest(caseItem);
        matched.ifPresent(policy ->
            policyActionApplier.applyActions(request, caseItem, policy.getActions()));

        // existing groupId-commit + JMS send logic follows
    }
}
```

---

## 5. Actions — pluggable handler registry

An action is **not limited to "set a field."** `PolicyAction.actionType`
determines which handler executes it; new action types require **zero
changes** to the evaluation engine — just a new `@Component`.

### 5.1 The interface

```java
public interface PolicyActionHandler {
    String getActionType(); // e.g. "SET_FIELD", "ASSIGN_USER"
    void apply(InspectionRequest request, CaseItemEntity caseItem, PolicyAction action);
}
```

### 5.2 Built-in handlers

**`SET_FIELD`** — writes `action.outputValue` onto `request.outputParamName`
using `PropertyAccessorFactory.forBeanPropertyAccess(request)` (symmetric
with how `CaseItemContext` *reads* fields — one mechanism for both
directions, with built-in type conversion for free, e.g. `"4"` → `Integer`).

**`ASSIGN_USER`** — reads handler-specific config out of
`policy_action_param` (e.g. `strategy`, `queueId`) via
`PolicyAction.getParam(key)`, delegates to a `UserAssignmentService` to pick
an assignee, and updates the case item accordingly.

```java
@Component
public class PolicyActionApplier {
    private final Map<String, PolicyActionHandler> handlersByType;

    public PolicyActionApplier(List<PolicyActionHandler> handlers) {
        // Spring autowires every PolicyActionHandler bean automatically
        this.handlersByType = handlers.stream()
            .collect(Collectors.toMap(PolicyActionHandler::getActionType, h -> h));
    }

    public void applyActions(InspectionRequest request, CaseItemEntity caseItem, List<PolicyAction> actions) {
        for (PolicyAction action : actions) {
            PolicyActionHandler handler = handlersByType.get(action.getActionType());
            if (handler == null) {
                throw new RuleEvaluationException("Unknown action type: " + action.getActionType());
            }
            handler.apply(request, caseItem, action);
        }
    }
}
```

### 5.3 How to add a new action type

1. Decide what config the action needs. If it's just a field/value pair,
   you can reuse `output_param_name`/`output_value`. If it needs arbitrary
   config (like `ASSIGN_USER`'s `strategy`/`queueId`), store it as rows in
   `policy_action_param` under that action.
2. Write a new class implementing `PolicyActionHandler`, annotated
   `@Component`, with a unique `getActionType()` string (e.g.
   `"SEND_NOTIFICATION"`).
3. Implement `apply(...)` with whatever logic the action needs — it can
   call any other Spring bean, service, or external integration.
4. That's it. `PolicyActionApplier` discovers it automatically through
   Spring's `List<PolicyActionHandler>` constructor injection — no registry
   file to edit, no switch statement to extend.
5. Surface the new action type as an option in the admin UI's action
   section so users can select it when building a policy.

### 5.4 The "default / catch-all" policy is not a special engine concept

There is **no `is_default` flag and no separate fallback code path.** A
"when nothing else matches, assign to a user" behavior is authored as an
**ordinary policy**:

- A real (non-empty) rule or set of rules broad enough to catch whatever
  should fall through (e.g. a condition that's true for virtually every
  case item).
- Placed at the **highest `order_index`** in its system, so it's only
  reached once every preceding policy has failed to match.
- With an `ASSIGN_USER` action (or whatever the desired fallback behavior
  is), applied through the exact same `PolicyActionApplier` as any other
  policy's actions.

This keeps the engine simple: `PolicyService.validate()` always requires at
least one real rule and at least one action, with no special-casing for
"default" policies, and `evaluateFirstMatch`/`PolicyTreeCache` need no
fallback branch — the catch-all is just the last item the loop naturally
reaches.

---

## 6. Why nesting is unavoidable the moment AND and OR mix

A flat sequence like `X AND Y OR Z` has no single correct reading — it could
mean `(X AND Y) OR Z` or `X AND (Y OR Z)`, and these produce different
results on the same data. Rather than parse operator precedence from a
typed expression (which users would have to learn and could get wrong), the
UI makes grouping **explicit and visual**: nesting is created by the user
placing conditions inside boxes, not inferred from text.

- **All-AND or all-OR policy:** a single flat `RuleGroup` (depth 1), no
  nesting needed.
- **The moment one OR needs to apply to only a subset of otherwise-AND
  conditions** (or vice versa): that subset must be pulled into its own
  nested `RuleGroup`. There is no way to express "mostly AND, with one
  exception" without a second tree level — this is a structural property of
  boolean logic, not a limitation of this particular implementation.

---

## 7. UI — rule builder

### 7.1 Visual model

Each `RuleGroup` renders as a **bordered box** with an AND/OR toggle at its
top. A box's children render as a list where **rules and nested group-boxes
can be mixed** at the same level, separated by a connector label that always
repeats that box's own operator (a box never shows both "AND" and "OR"
connectors internally — that's the invariant that keeps the tree
unambiguous). Indentation = tree depth.

```
┌─ Match if:  [ OR ▾ ]────────────────────────────┐
│  ┌─ [ AND ▾ ]──────────────────────────────┐    │
│  │  Establishment │ legalEntity │ = │ "LLC" │    │
│  │  ── AND ──                               │    │
│  │  Engagement    │ wage        │ > │ 10000 │    │
│  └───────────────────────────────────────────┘    │
│  ── OR ──                                          │
│  ┌─ [ AND ▾ ]──────────────────────────────┐    │
│  │  Person │ engagementType │ = │ "CONTRACTOR" │  │
│  │  ── AND ──                               │    │
│  │  ┌─ [ OR ▾ ]────────────────────────┐    │    │
│  │  │ Engagement │ wage │ > │ 15000     │    │    │
│  │  │ ── OR ──                          │    │    │
│  │  │ Engagement │ hasMultipleEmployers │=│true│   │    │
│  │  └───────────────────────────────────┘    │    │
│  └───────────────────────────────────────────┘    │
│  [+ Add Rule]   [+ Add Group]                      │
└──────────────────────────────────────────────────┘
```

### 7.2 Each rule row

```
[Entity ▾]  [Param ▾]  [Operator ▾]  [Value]   [☐ Compare to field]   [☐ NOT]
```

- **Entity** filters which params are selectable.
- **Param** populates from the `param` table; selecting one fetches its
  `param_operator` list.
- **Operator** is restricted to what's valid for the selected param's type
  (a STRING param never offers `>`; a NUMBER param never offers string-only
  operators).
- **Value** renders based on `param_type` (text / number / date-picker /
  boolean toggle); switches to a second Entity/Param pair when "Compare to
  field" is checked (→ `value_type = PARAM_REF`).

### 7.3 Building nested groups — the actual interaction

**There is no "select existing rows and wrap them" step.** The user clicks
**"+ Add Group"** inside whichever box they want the nesting inside; this
inserts a new, empty `RuleGroup` with its own AND/OR toggle. The user then
clicks **"+ Add Rule"** / **"+ Add Group"** *inside that new box* — every
box's buttons are scoped to that box's own node, so populating a nested
group uses exactly the same two actions as populating the root.

### 7.4 UI-side tree model (TypeScript)

```typescript
type UiNode = UiGroupNode | UiRuleNode;

interface UiGroupNode {
  type: 'GROUP';
  id: string;
  logicOperator: 'AND' | 'OR';
  children: UiNode[]; // mix of UiGroupNode and UiRuleNode
}

interface UiRuleNode {
  type: 'RULE';
  id: string;
  entityName: string | null;
  paramName: string | null;
  operator: string | null;
  valueType: 'LITERAL' | 'PARAM_REF';
  values: string[];
  refEntityName: string | null;
  refParamName: string | null;
  negate: boolean;
}
```

`addRule(parent)` and `addGroup(parent)` push into `parent.children` — both
take whichever `UiGroupNode` the acting button belongs to, so the recursive
Angular component (`RuleGroupComponent`, which renders itself for nested
`GROUP` children) needs no special-casing between "root" and "nested."

### 7.5 Serialization — UI tree → backend DTO

```typescript
function buildRuleGroupDto(node: UiGroupNode): RuleGroupDto {
  const rules: RuleDto[] = [];
  const childGroups: RuleGroupDto[] = [];
  for (const child of node.children) {
    if (child.type === 'GROUP') childGroups.push(buildRuleGroupDto(child)); // recurse
    else rules.push(buildRuleDto(child));
  }
  return { logicOperator: node.logicOperator, rules, childGroups };
}
```

This recursion mirrors `PolicyTreeBuilder.buildTree` on the backend almost
line for line — same base case (leaf = rule), same recursive case (group
with children). Loading an existing policy for editing uses the mirror-image
function, `dtoToUiNode`, so create and edit share one component.

Validation (`validateTree`) runs client-side before save: an empty group, a
group with only one child (redundant nesting), or a rule missing its
entity/param/operator/value all block submission with an inline message.

---

## 8. Multiple downstream systems

`Policy.systemCode` scopes a policy to exactly one system (e.g. `RASED`,
or a second system's code). `order_index` is unique **per system**
(`UNIQUE (system_code, order_index)`), so each system has its own
independent priority ordering — they never compete.

`PolicyTreeCache` groups cached trees by `systemCode`
(`getPoliciesInOrder(systemCode)`), and `PolicyEvaluationService
.evaluateFirstMatch(systemCode, ctx)` only ever walks that one system's
list. The scheduled job (or whichever caller triggers evaluation) passes the
system it's currently processing for.

---

## 9. Draft → Test → Publish lifecycle

### 9.1 Status field

`Policy.status` is `DRAFT`, `PUBLISHED`, or `ARCHIVED`. **Only `PUBLISHED`
policies are loaded into `PolicyTreeCache`** — a `DRAFT` policy can never
affect a real case item, no matter its `order_index` or content. This is
enforced at the single query `PolicyTreeCache.reload()` uses
(`findByStatusAndActiveTrueOrderBySystemCodeAscOrderIndexAsc(PUBLISHED)`),
so there's exactly one place that invariant could be broken, making it easy
to audit.

### 9.2 Testing a draft before publishing

`POST /api/policies/{id}/test` (handled by `PolicyTestService`, separate from
`PolicyService`) accepts sample field values (not a real `CaseItemEntity`)
and evaluates the **exact same tree-building and evaluation code** the
production path uses — just invoked fresh, bypassing the cache entirely,
since drafts never live there:

```java
public TestPolicyResponse testPolicy(Long policyId, TestPolicyRequest request) {
    Policy policy = policyRepository.findById(policyId).orElseThrow(...);
    RuleNode tree = treeBuilder.buildTree(policy.getRootGroup()); // built fresh, not cached
    CaseItemContext ctx = CaseItemContext.fromRawMaps(
        request.getEstablishment(), request.getPerson(), request.getEngagement());
    try {
        boolean matched = tree.evaluate(ctx);
        return new TestPolicyResponse(matched, matched ? toActionDtos(policy.getActions()) : List.of(), null);
    } catch (RuleEvaluationException e) {
        return new TestPolicyResponse(false, List.of(), e.getMessage());
    }
}
```

Because this reuses `PolicyTreeBuilder` and `RuleNode.evaluate()` verbatim,
there is no risk of the test path drifting out of sync with what actually
runs once published.

### 9.3 Publishing

```java
@Transactional
public void publish(Long policyId) {
    Policy policy = policyRepository.findById(policyId).orElseThrow(...);
    policy.setStatus(PolicyStatus.PUBLISHED);
    policyRepository.save(policy);
    policyTreeCache.reload(); // the new/changed policy takes effect immediately
}
```

**Open design question (not yet decided):** should editing an already-
`PUBLISHED` policy take effect immediately, or should any edit to a
published policy revert its status to `DRAFT` until explicitly republished?
The latter is safer — it forces a re-test after every change — and is
recommended given the whole point of this lifecycle is a safety net before
changes go live.

---

## 10. API layer

### 10.1 Endpoints (`PolicyController`, base path `/api/policies`)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/` | Create a policy (always starts as `DRAFT`) |
| `GET` | `/{id}` | Fetch one policy, full rule tree included (`PolicyResponse`) |
| `GET` | `/?systemCode=...` | List policies (lightweight `PolicySummaryResponse`, no tree); `systemCode` optional |
| `PUT` | `/{id}` | Full update — replaces the rule tree and actions (see §10.3) |
| `DELETE` | `/{id}` | Delete a policy and its whole tree |
| `POST` | `/{id}/publish` | `DRAFT`/`ARCHIVED` → `PUBLISHED`, reloads the cache |
| `POST` | `/{id}/archive` | → `ARCHIVED`, reloads the cache |
| `PATCH` | `/reorder` | Bulk `order_index` update across policies, reloads the cache |
| `POST` | `/{id}/test` | Evaluate (any status) against sample field values — see §9.2 |

`ParamController` (`GET /api/params`) returns every `Param` with its allowed
operators embedded (`ParamMetaDto`), driving the UI's Entity/Param/Operator
dropdowns in one call.

### 10.2 DTOs

- **`RuleDto`** — one leaf comparison: `entityName`, `paramName`, `operator`,
  `valueType` (`LITERAL`/`PARAM_REF`), `values` (list, for `IN`/`BETWEEN`
  support), `refEntityName`/`refParamName` (for field-to-field comparisons),
  `negate`.
- **`RuleGroupDto`** — recursive: `logicOperator`, `rules: List<RuleDto>`,
  `childGroups: List<RuleGroupDto>`. Bean-validated with `@Valid` so nested
  violations surface correctly.
- **`PolicyActionDto`** — `actionType`, `outputParamName`/`outputValue` (for
  `SET_FIELD`), plus a `params: Map<String,String>` for handler-specific
  config (e.g. `ASSIGN_USER`'s `strategy`/`queueId`).
- **`CreatePolicyRequest`** / **`UpdatePolicyRequest`** — `name`,
  `description`, `systemCode`, `orderIndex`, `rootGroup: RuleGroupDto`,
  `actions: List<PolicyActionDto>`. `status` is deliberately **not** a field
  on either — status transitions only happen through `/publish` and
  `/archive`, so they stay auditable and can't be smuggled in via an
  unrelated edit.
- **`PolicyResponse`** — full representation including the serialized
  `rootGroup` tree, for the single-policy GET and after create/update.
- **`PolicySummaryResponse`** — `id`, `name`, `systemCode`, `status`,
  `orderIndex`, `active` only — no tree — for the list view.
- **`ReorderPoliciesRequest`** — `order: List<{policyId, orderIndex}>` for
  the bulk reorder PATCH.
- **`TestPolicyRequest`** / **`TestPolicyResponse`** — see §9.2.
- **`ParamMetaDto`** — `id`, `entityName`, `paramName`, `paramType`,
  `allowedOperators: List<String>`.

### 10.3 `PolicyMapper` — single place for DTO ↔ entity conversion

All recursive tree conversion (both directions) lives in one `@Component`,
`PolicyMapper`, so `PolicyService`/`PolicyController` never walk the tree
themselves:

```java
public RuleGroup toEntity(RuleGroupDto dto, RuleGroup parent, int orderIndex) {
    RuleGroup group = new RuleGroup();
    group.setLogicOperator(LogicOperator.valueOf(dto.getLogicOperator()));
    group.setParent(parent);
    group.setOrderIndex(orderIndex);

    List<RuleGroup> childGroups = new ArrayList<>();
    int i = 1;
    for (RuleGroupDto childDto : dto.getChildGroups()) {
        childGroups.add(toEntity(childDto, group, i++)); // recurse
    }
    group.setChildGroups(childGroups);
    // ...rules built the same way via toEntity(RuleDto, group, orderIndex), which
    // resolves Param by (entityName, paramName) and validates the operator is
    // allowed for it via ParamOperatorRepository before building the Rule...
    return group;
}
```

The mirror-image `toDto(RuleGroup)` / `toDto(Rule)` / `toDto(PolicyAction)` /
`toResponse(Policy)` methods build the response shapes the same way, and
`toSummary(Policy)` builds the lightweight list-view shape.

### 10.4 `PolicyService` — orchestration

```java
@Transactional
public PolicyResponse createPolicy(CreatePolicyRequest request) {
    validate(request.getRootGroup(), request.getActions());
    Policy policy = Policy.builder()
        .name(request.getName()).description(request.getDescription())
        .systemCode(request.getSystemCode()).orderIndex(request.getOrderIndex())
        .status(PolicyStatus.DRAFT).active(true).build();
    policy.setRootGroup(policyMapper.toEntity(request.getRootGroup(), null, 1));
    request.getActions().forEach(a -> policy.addAction(policyMapper.toEntity(a, policy)));
    return policyMapper.toResponse(policyRepository.save(policy));
    // no cache reload: a new policy is DRAFT and invisible to evaluation until published
}
```

**Update semantics — full tree replacement, not diffing:**

```java
@Transactional
public PolicyResponse updatePolicy(Long id, UpdatePolicyRequest request) {
    validate(request.getRootGroup(), request.getActions());
    Policy policy = findOrThrow(id);
    // ...set name/description/systemCode/orderIndex...
    policy.setRootGroup(policyMapper.toEntity(request.getRootGroup(), null, 1));
    // orphanRemoval=true on Policy.rootGroup (OneToOne, CascadeType.ALL) deletes
    // the OLD RuleGroup subtree automatically when replaced with a new one

    policy.getActions().clear(); // orphanRemoval deletes the old PolicyAction rows
    request.getActions().forEach(a -> policy.addAction(policyMapper.toEntity(a, policy)));

    if (policy.getStatus() == PolicyStatus.PUBLISHED) {
        policy.setStatus(PolicyStatus.DRAFT); // editing a live policy pulls it back to DRAFT —
    }                                         // changes never go live without an explicit re-publish
    Policy saved = policyRepository.save(policy);
    policyTreeCache.reload(); // in case it was PUBLISHED before, it must drop out of the cache now
    return policyMapper.toResponse(saved);
}
```

Diffing an arbitrary-depth recursive tree for a partial update was considered
and rejected — disproportionately complex code for an operation ("edit an
existing policy's rules") that doesn't need to be cheap, since it's an
occasional admin action, not a hot path.

**Reorder** is a lightweight bulk update, not a tree resend:

```java
@Transactional
public void reorder(ReorderPoliciesRequest request) {
    request.getOrder().forEach(entry -> {
        Policy policy = findOrThrow(entry.getPolicyId());
        policy.setOrderIndex(entry.getOrderIndex());
        policyRepository.save(policy);
    });
    policyTreeCache.reload();
}
```

**Validation remains unconditional** — including for a policy intended as
the system's catch-all/default (§5.4), which is authored as an ordinary
policy with real rules, never an empty-group special case:

```java
private void validate(RuleGroupDto rootGroup, List<PolicyActionDto> actions) {
    if (rootGroup == null || isEmptyGroup(rootGroup)) {
        throw new IllegalArgumentException("Policy has no rules defined");
    }
    if (actions == null || actions.isEmpty()) {
        throw new IllegalArgumentException("Policy must define at least one output action");
    }
}
```

### 10.5 Error handling — `PolicyApiExceptionHandler`

A `@RestControllerAdvice` scoped to the `policy.api` package converts
`IllegalArgumentException` → `400`, `RuleEvaluationException` → `422`, and
Bean Validation failures (`MethodArgumentNotValidException`) → `400` with
the first failing field named in the message — so malformed requests return
a clean JSON error body instead of a raw stack trace.

### 10.6 Open item

`PolicyController.update`/`delete` do not currently check `status` before
acting — e.g. hard-deleting a `PUBLISHED` policy is allowed. Depending on
audit requirements, consider requiring a policy to be `ARCHIVED` before it
can be deleted, rather than allowing a direct delete of a live policy.

---

## 11. Exception handling

`RuleEvaluationException` (unchecked, extends `RuntimeException`) is the
single exception type used throughout evaluation for "this rule/policy
configuration doesn't match reality" — as opposed to a genuine bug (NPE,
`IllegalStateException`) which should propagate and fail loudly, not be
silently swallowed.

Thrown from:
- `CaseItemContext.resolve()` — unknown entity, or `InvalidPropertyException`
  from `PropertyAccessorFactory` (param name doesn't match a real getter).
- `ValueCoercer.coerce()` — a stored value doesn't parse as its declared
  `ParamType` (e.g. non-numeric string for a NUMBER param).
- `OperatorEvaluator.apply()` — wrong arity for `BETWEEN`, or a value that
  isn't a list for `IN`/`NOT_IN`/`BETWEEN` (wrapped `ClassCastException`).
- `PolicyActionApplier` — unknown `actionType`, or a `SET_FIELD` action
  pointing at a non-existent `InspectionRequest` property.

Caught exactly once, at the per-policy loop in
`PolicyEvaluationService.evaluateFirstMatch` — this is the single point
that contains the blast radius of any misconfiguration to "skip this one
policy, log clearly, keep going," regardless of how deep in a nested tree
the problem originated.

**Defense in depth:** the same classes of error (bad arity, bad param name,
bad operator) are also rejected at **policy save time** in `PolicyService`,
so the common path (an admin using the UI) never produces a malformed rule
in the first place. The runtime guards exist for anything that could still
reach the DB outside that path — manual SQL, a migration, a future bulk
import.

---

## 12. Glossary / where to find things

| Concept | Class / Table | Package |
|---|---|---|
| A user-authored rule set with an outcome | `Policy` | `domain` |
| AND/OR node in the expression tree (DB) | `RuleGroup` | `domain` |
| A single field comparison (DB) | `Rule`, `RuleValue` | `domain` |
| Field metadata + allowed operators | `Param`, `ParamOperator` | `domain` |
| An outcome of a matched policy | `PolicyAction`, `PolicyActionParam` | `domain` |
| In-memory AND/OR node (runtime) | `GroupNode` | `evaluation.tree` |
| In-memory leaf comparison (runtime) | `LeafNode` | `evaluation.tree` |
| DB tree → runtime tree converter | `PolicyTreeBuilder` | `evaluation` |
| Cached, system-scoped, published-only trees | `PolicyTreeCache` | `evaluation` |
| Per-case-item evaluation entry point | `PolicyEvaluationService` | `evaluation` |
| Resolves `entity.field` against enrichment data | `CaseItemContext` | `evaluation` |
| Comparison logic per operator | `OperatorEvaluator` | `evaluation` |
| Raw string → typed value | `ValueCoercer` | `evaluation` |
| Dispatches a matched policy's actions | `PolicyActionApplier` | `evaluation` |
| Pluggable action behavior | `PolicyActionHandler` (+ impls) | `evaluation` |
| Config/evaluation error, caught per-policy | `RuleEvaluationException` | `exception` |
| Create/validate/persist a policy | `PolicyService` | `api` |
| Draft test-without-publishing flow | `PolicyTestService` | `api` |
| DTO ↔ entity conversion (recursive, both directions) | `PolicyMapper` | `api` |
| REST endpoints for policies | `PolicyController` | `api` |
| REST endpoint for field/operator metadata | `ParamController` | `api` |
| Policy persistence queries | `PolicyRepository` | `api` |
| Param/operator lookups | `ParamRepository`, `ParamOperatorRepository` | `api` |
| Clean JSON error bodies from thrown exceptions | `PolicyApiExceptionHandler` | `api` |
| Bulk `order_index` update DTO | `ReorderPoliciesRequest` | `dto` |
| Lightweight list-view shape (no tree) | `PolicySummaryResponse` | `dto` |
| Handler-specific action config (e.g. ASSIGN_USER's strategy) | `PolicyActionParam` | `domain` |
| UI tree model + mutation/serialization helpers | `rule-node.model.ts`, `rule-tree.util.ts` | Angular |
| Recursive group box component | `RuleGroupComponent` | Angular |
| Single rule row component | `RuleRowComponent` | Angular |
| Top-level policy form | `PolicyBuilderComponent` | Angular |

---

## 13. How to extend this system

**Add a new comparable field:** insert a `Param` row (entity, name, type)
and its allowed `ParamOperator` rows. No code change needed — the UI
dropdowns and the evaluator both read field/operator metadata from these
tables.

**Add a new operator:** add a case to `OperatorEvaluator.apply()` (and to
`compare()` if it's an ordering comparison), then add corresponding
`ParamOperator` rows for whichever params should offer it.

**Add a new action type:** implement `PolicyActionHandler`, annotate
`@Component`. See §5.3 for the full checklist. No changes to
`PolicyEvaluationService`, `PolicyTreeBuilder`, or `PolicyTreeCache`.

**Add a new downstream system:** no schema change — just start creating
policies with a new `systemCode` value and call `evaluateFirstMatch` with
that code from the relevant job/caller.

**Change what "match" means for a field type:** `OperatorEvaluator.compare()`
is the single place ordering comparisons are implemented per `ParamType` —
changes there apply uniformly across every rule using that type.
