--------------------------------------------------------------------------------
-- Fraud Policy Engine — Full Schema DDL (Oracle)
--------------------------------------------------------------------------------
-- Sequences are declared first since every table's id column is populated by
-- JPA via GenerationType.SEQUENCE, not IDENTITY (IDENTITY disables Hibernate's
-- JDBC insert batching). Each sequence's INCREMENT BY matches the
-- @SequenceGenerator(allocationSize = ...) on its entity — keep these in sync
-- if either side changes, or you lose the batching benefit without erroring.
--------------------------------------------------------------------------------

CREATE SEQUENCE policy_id_seq              START WITH 1 INCREMENT BY 50 NOCACHE;
CREATE SEQUENCE rule_group_id_seq          START WITH 1 INCREMENT BY 50 NOCACHE;
CREATE SEQUENCE rule_id_seq                START WITH 1 INCREMENT BY 50 NOCACHE;
CREATE SEQUENCE rule_value_id_seq          START WITH 1 INCREMENT BY 50 NOCACHE;
CREATE SEQUENCE param_id_seq               START WITH 1 INCREMENT BY 20 NOCACHE;
CREATE SEQUENCE param_operator_id_seq      START WITH 1 INCREMENT BY 20 NOCACHE;
CREATE SEQUENCE policy_action_id_seq       START WITH 1 INCREMENT BY 20 NOCACHE;
CREATE SEQUENCE policy_action_param_id_seq START WITH 1 INCREMENT BY 20 NOCACHE;

-- NOCACHE is fine for a single Oracle instance. On RAC with multiple
-- instances, switch to CACHE (e.g. CACHE 50) so instances aren't serializing
-- on the same sequence for every allocation.

--------------------------------------------------------------------------------
-- POLICY — a user-authored, ordered rule set with an outcome.
-- Evaluated first-match-wins, scoped to one system_code, in order_index order,
-- restricted to status = PUBLISHED (see PolicyTreeCache).
--------------------------------------------------------------------------------
CREATE TABLE policy (
                        id            NUMBER PRIMARY KEY,
                        name          VARCHAR2(200) NOT NULL,
                        description   VARCHAR2(1000),
                        system_code   VARCHAR2(50) NOT NULL,                  -- which downstream system this policy applies to (e.g. 'RASED')
                        status        VARCHAR2(20) DEFAULT 'DRAFT' NOT NULL,  -- DRAFT | PUBLISHED | ARCHIVED — only PUBLISHED is loaded for evaluation
                        order_index   NUMBER NOT NULL,                         -- evaluation order within (system_code, status = PUBLISHED); lower = evaluated first
                        active        NUMBER(1) DEFAULT 1,                     -- soft-disable flag, independent of status
                        root_group_id NUMBER NOT NULL                          -- FK to rule_group: entry point of this policy's AND/OR expression tree
);

COMMENT ON TABLE policy IS
    'A user-authored policy: an ordered, nestable set of AND/OR rules that, when fully satisfied by a case item, triggers one or more PolicyAction outcomes. Evaluated first-match-wins, per system_code, in order_index order, restricted to PUBLISHED policies. uq_policy_system_order (declared further below, after this table) enforces unique ordering per system and is deferred to COMMIT so multi-row reorders can pass through intermediate colliding states.';
COMMENT ON COLUMN policy.id IS 'Surrogate key, populated from policy_id_seq.';
COMMENT ON COLUMN policy.name IS 'Display name shown in the admin UI.';
COMMENT ON COLUMN policy.description IS 'Free-text description of what this policy is for.';
COMMENT ON COLUMN policy.system_code IS 'Downstream system this policy evaluates for (e.g. RASED vs a second system). Policies for different systems never compete on order_index.';
COMMENT ON COLUMN policy.status IS 'Lifecycle state: DRAFT (editable, invisible to evaluation), PUBLISHED (live, cached, affects real case items), ARCHIVED (retired, invisible to evaluation).';
COMMENT ON COLUMN policy.order_index IS 'Evaluation priority within (system_code, status = PUBLISHED); the first policy whose rule tree matches wins. Unique per system_code — see uq_policy_system_order below.';
COMMENT ON COLUMN policy.active IS 'Soft-disable switch. 1 = eligible for evaluation (subject to status also being PUBLISHED), 0 = excluded without deleting the row.';
COMMENT ON COLUMN policy.root_group_id IS 'FK to rule_group.id — the root node of this policy''s AND/OR expression tree. Never null; every policy has at least one rule (see PolicyService.validate).';

CREATE UNIQUE INDEX uq_policy_system_order ON policy (system_code, order_index);

--------------------------------------------------------------------------------
-- RULE_GROUP — a node in the AND/OR expression tree. Self-referencing, so the
-- tree can nest to arbitrary depth. A group's children (other rule_group rows
-- via parent_group_id, or rule rows via rule.group_id) are combined using this
-- group's own logic_operator. A group never mixes AND and OR among its own
-- direct children — mixing requires nesting a new child group instead.
--------------------------------------------------------------------------------
CREATE TABLE rule_group (
                            id              NUMBER PRIMARY KEY,
                            policy_id       NUMBER,                 -- set ONLY on the root group of a policy; null for every non-root group
                            parent_group_id NUMBER,                 -- FK to rule_group.id; null only for the root group
                            logic_operator  VARCHAR2(3) NOT NULL,   -- 'AND' or 'OR' — combines this group's own direct children
                            order_index     NUMBER NOT NULL,        -- display/evaluation order among sibling children of the same parent
                            CONSTRAINT fk_rg_policy FOREIGN KEY (policy_id) REFERENCES policy(id),
                            CONSTRAINT fk_rg_parent FOREIGN KEY (parent_group_id) REFERENCES rule_group(id)
);

COMMENT ON TABLE rule_group IS
    'A node in a policy''s AND/OR expression tree. Self-referencing via parent_group_id, so nesting depth is unbounded. An AND group with zero children evaluates to true (vacuous truth); an OR group with zero children evaluates to false.';
COMMENT ON COLUMN rule_group.id IS 'Surrogate key, populated from rule_group_id_seq.';
COMMENT ON COLUMN rule_group.policy_id IS 'Set only on the single root group of a policy (one-to-one with policy.root_group_id); every other group in the tree has this null and is reached only via parent_group_id.';
COMMENT ON COLUMN rule_group.parent_group_id IS 'FK to the enclosing rule_group. Null only for a policy''s root group. This self-reference is what allows arbitrary AND/OR nesting.';
COMMENT ON COLUMN rule_group.logic_operator IS 'AND or OR — how this group combines ITS OWN direct children (both child rule_group rows and child rule rows). A group never mixes both; mixing AND/OR requires introducing a nested child group.';
COMMENT ON COLUMN rule_group.order_index IS 'Order among sibling children under the same parent_group_id (or among a policy''s root-level children). Purely for stable display/short-circuit ordering — AND/OR are commutative, so this never changes the evaluation result.';

--------------------------------------------------------------------------------
-- PARAM — metadata describing one comparable field on one enriched entity.
-- Drives the UI's Entity/Field dropdowns and the evaluator's type coercion.
--------------------------------------------------------------------------------
CREATE TABLE param (
                       id          NUMBER PRIMARY KEY,
                       entity_name VARCHAR2(50) NOT NULL,      -- 'ESTABLISHMENT' | 'PERSON' | 'ENGAGEMENT'
                       param_name  VARCHAR2(100) NOT NULL,     -- the field's name, matching a getter on the corresponding enrichment bean (e.g. 'wage', 'legalEntity')
                       param_type  VARCHAR2(20) NOT NULL,      -- 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' — governs value coercion and which operators are sensible
                       CONSTRAINT uq_param UNIQUE (entity_name, param_name)
);

COMMENT ON TABLE param IS
    'Metadata for one comparable field on one enrichment entity (ESTABLISHMENT, PERSON, or ENGAGEMENT). Adding a field to the rule builder is a data change here, not a code change.';
COMMENT ON COLUMN param.id IS 'Surrogate key, populated from param_id_seq.';
COMMENT ON COLUMN param.entity_name IS 'Which enrichment entity this field belongs to: ESTABLISHMENT, PERSON, or ENGAGEMENT. Resolved against the matching bean via PropertyAccessorFactory at evaluation time.';
COMMENT ON COLUMN param.param_name IS 'The field name as it appears in the UI and is stored on a Rule row; must match a real getter on the corresponding enrichment bean (e.g. getWage() for "wage").';
COMMENT ON COLUMN param.param_type IS 'STRING, NUMBER, DATE, or BOOLEAN. Used by ValueCoercer to parse stored rule_value rows into the real typed value, and restricts which operators are offered for this field in the UI.';

--------------------------------------------------------------------------------
-- PARAM_OPERATOR — which comparison operators are valid for a given param.
-- Drives the UI's Operator dropdown and backend validation on save.
--------------------------------------------------------------------------------
CREATE TABLE param_operator (
                                id       NUMBER PRIMARY KEY,
                                param_id NUMBER NOT NULL,
                                operator VARCHAR2(20) NOT NULL,          -- '=', '!=', '>', '<', '>=', '<=', 'IN', 'NOT_IN', 'BETWEEN', ...
                                CONSTRAINT fk_po_param FOREIGN KEY (param_id) REFERENCES param(id),
                                CONSTRAINT uq_param_op UNIQUE (param_id, operator)
);

COMMENT ON TABLE param_operator IS
    'The set of operators allowed for a given param (e.g. a STRING param offers =/!=/IN but not >/<). PolicyMapper validates every incoming Rule''s operator against this table before persisting.';
COMMENT ON COLUMN param_operator.id IS 'Surrogate key, populated from param_operator_id_seq.';
COMMENT ON COLUMN param_operator.param_id IS 'FK to param.id this operator is valid for.';
COMMENT ON COLUMN param_operator.operator IS 'The operator token, matched verbatim against Rule.operator and interpreted by OperatorEvaluator at evaluation time.';

--------------------------------------------------------------------------------
-- RULE — a single leaf field comparison; the leaf nodes of a policy's
-- expression tree. Always lives inside exactly one rule_group.
--------------------------------------------------------------------------------
CREATE TABLE rule (
                      id           NUMBER PRIMARY KEY,
                      group_id     NUMBER NOT NULL,                    -- the enclosing rule_group (every leaf lives inside a group)
                      param_id     NUMBER NOT NULL,                     -- which field this rule compares
                      operator     VARCHAR2(20) NOT NULL,               -- must be present in param_operator for param_id
                      value_type   VARCHAR2(10) DEFAULT 'LITERAL',      -- 'LITERAL' (compare to a stored value) | 'PARAM_REF' (compare to another field)
                      ref_param_id NUMBER,                               -- set only when value_type = PARAM_REF (field-to-field comparison target)
                      negate       NUMBER(1) DEFAULT 0,                  -- 1 = invert the comparison result (NOT)
                      order_index  NUMBER NOT NULL,                      -- display order among sibling rules within the same group
                      CONSTRAINT fk_rule_group FOREIGN KEY (group_id) REFERENCES rule_group(id),
                      CONSTRAINT fk_rule_param FOREIGN KEY (param_id) REFERENCES param(id),
                      CONSTRAINT fk_rule_ref_param FOREIGN KEY (ref_param_id) REFERENCES param(id)
);

COMMENT ON TABLE rule IS
    'A single leaf comparison in a policy''s expression tree: "<param> <operator> <value or ref_param>". Always belongs to exactly one rule_group; siblings in the same group are combined via that group''s logic_operator.';
COMMENT ON COLUMN rule.id IS 'Surrogate key, populated from rule_id_seq.';
COMMENT ON COLUMN rule.group_id IS 'FK to the enclosing rule_group. Determines which AND/OR this rule participates in.';
COMMENT ON COLUMN rule.param_id IS 'FK to param — the field being compared (e.g. engagement.wage).';
COMMENT ON COLUMN rule.operator IS 'Comparison operator, validated against param_operator for this param_id at save time; interpreted by OperatorEvaluator at evaluation time.';
COMMENT ON COLUMN rule.value_type IS 'LITERAL: compare against the value(s) in rule_value. PARAM_REF: compare against another field''s live value (ref_param_id) instead of a stored constant.';
COMMENT ON COLUMN rule.ref_param_id IS 'FK to param, set only when value_type = PARAM_REF — the field this rule compares against (e.g. engagement.wage > person.declaredIncome).';
COMMENT ON COLUMN rule.negate IS '1 inverts the comparison result (NOT <comparison>), avoiding the need for separate NOT_* operator variants.';
COMMENT ON COLUMN rule.order_index IS 'Display order among sibling rules in the same group. Does not affect evaluation result (AND/OR are commutative); may affect short-circuit performance.';

--------------------------------------------------------------------------------
-- RULE_VALUE — the literal value(s) a LITERAL-type rule compares against.
-- A separate child table (rather than a single column) so operators needing
-- more than one value (IN, NOT_IN, BETWEEN) are supported without delimiter
-- hacks in a single varchar.
--------------------------------------------------------------------------------
CREATE TABLE rule_value (
                            id      NUMBER PRIMARY KEY,
                            rule_id NUMBER NOT NULL,
                            value   VARCHAR2(500) NOT NULL,     -- raw string as entered in the UI; coerced to the real typed value (via ValueCoercer) at tree-build time, not stored pre-coerced
                            CONSTRAINT fk_rv_rule FOREIGN KEY (rule_id) REFERENCES rule(id)
);

COMMENT ON TABLE rule_value IS
    'One literal value belonging to a LITERAL-type rule. A rule has one row for =/>/< etc., exactly two rows for BETWEEN, and one-or-more rows for IN/NOT_IN.';
COMMENT ON COLUMN rule_value.id IS 'Surrogate key, populated from rule_value_id_seq.';
COMMENT ON COLUMN rule_value.rule_id IS 'FK to the owning rule.';
COMMENT ON COLUMN rule_value.value IS 'Raw string as entered in the UI (e.g. "10000", "LLC", "true"). Coerced to the param''s real type (BigDecimal/LocalDate/Boolean/String) by ValueCoercer when the evaluation tree is built, not stored pre-coerced.';

--------------------------------------------------------------------------------
-- POLICY_ACTION — one outcome applied when a policy's root group matches.
-- action_type selects the handler (PolicyActionHandler) that executes it,
-- so new action kinds are added without changing the evaluation engine.
--------------------------------------------------------------------------------
CREATE TABLE policy_action (
                               id                 NUMBER PRIMARY KEY,
                               policy_id          NUMBER NOT NULL,
                               action_type        VARCHAR2(50) NOT NULL,    -- 'SET_FIELD' | 'ASSIGN_USER' | ... (extensible — see PolicyActionHandler)
                               output_param_name  VARCHAR2(100),            -- used by SET_FIELD: the InspectionRequest field to set
                               output_value       VARCHAR2(500),            -- used by SET_FIELD: the value to set it to
                               CONSTRAINT fk_pa_policy FOREIGN KEY (policy_id) REFERENCES policy(id)
);

COMMENT ON TABLE policy_action IS
    'One outcome of a matched policy. action_type picks which PolicyActionHandler bean executes it (e.g. SET_FIELD writes a field on the InspectionRequest; ASSIGN_USER routes the case item to a person). A policy may have multiple actions.';
COMMENT ON COLUMN policy_action.id IS 'Surrogate key, populated from policy_action_id_seq.';
COMMENT ON COLUMN policy_action.policy_id IS 'FK to the owning policy.';
COMMENT ON COLUMN policy_action.action_type IS 'Dispatch key looked up in PolicyActionApplier''s handler registry. Adding a new value here requires a corresponding new PolicyActionHandler bean, not a change to the evaluator.';
COMMENT ON COLUMN policy_action.output_param_name IS 'Used only by SET_FIELD actions: the name of the field to set on the outgoing InspectionRequest (e.g. "priority").';
COMMENT ON COLUMN policy_action.output_value IS 'Used only by SET_FIELD actions: the value to assign to output_param_name (e.g. "HIGH").';

--------------------------------------------------------------------------------
-- POLICY_ACTION_PARAM — arbitrary key/value config for action types that need
-- more than a single field/value pair (e.g. ASSIGN_USER's strategy/queueId).
--------------------------------------------------------------------------------
CREATE TABLE policy_action_param (
                                     id          NUMBER PRIMARY KEY,
                                     action_id   NUMBER NOT NULL,
                                     param_key   VARCHAR2(100) NOT NULL,     -- handler-specific config key, e.g. 'strategy', 'queueId'
                                     param_value VARCHAR2(500) NOT NULL,
                                     CONSTRAINT fk_pap_action FOREIGN KEY (action_id) REFERENCES policy_action(id)
);

COMMENT ON TABLE policy_action_param IS
    'Handler-specific key/value configuration for a PolicyAction, read via PolicyAction.getParam(key) and written via PolicyAction.addParam(key, value). Exists because action types other than SET_FIELD typically need more config than output_param_name/output_value can express.';
COMMENT ON COLUMN policy_action_param.id IS 'Surrogate key, populated from policy_action_param_id_seq.';
COMMENT ON COLUMN policy_action_param.action_id IS 'FK to the owning policy_action.';
COMMENT ON COLUMN policy_action_param.param_key IS 'Config key, defined by whichever PolicyActionHandler handles this action''s action_type (e.g. ASSIGN_USER''s handler reads "strategy" and "queueId").';
COMMENT ON COLUMN policy_action_param.param_value IS 'Config value for param_key, as a raw string; parsed/interpreted by the owning handler.';

