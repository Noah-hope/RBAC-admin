// Generated from docs/03-api/openapi.yaml; npm run generate:api
export type FieldError = {
  pointer: string;
  code: string;
  message: string;
  rowNumber?: number;
};
export type Problem = {
  type: string;
  title: string;
  status: number;
  code: string;
  detail: string;
  requestId: string;
  errors?: Array<FieldError>;
};
export type LoginInput = { loginName: string; password: string };
export type Session = {
  sessionId: string;
  accountId: string;
  employeeId: string | null;
  identity: "SUPER_ADMIN" | "PERMISSION_ADMIN";
  mode: "NORMAL" | "PASSWORD_CHANGE_ONLY";
  accessToken: string;
  tokenType: "Bearer";
  expiresAt: string;
};
export type SessionView = {
  sessionId: string;
  accountId: string;
  employeeId: string | null;
  identity: "SUPER_ADMIN" | "PERMISSION_ADMIN";
  mode: "NORMAL" | "PASSWORD_CHANGE_ONLY";
  expiresAt: string;
};
export type PasswordInput = {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
};
export type AccountCreate = {
  employeeId: string;
  loginName: string;
  temporaryPassword: string;
  confirmPassword: string;
  reason: string;
};
export type PasswordReset = {
  expectedVersion: number;
  temporaryPassword: string;
  confirmPassword: string;
  reason: string;
};
export type AccountStateInput = {
  expectedVersion: number;
  enabled: boolean;
  reason: string;
};
export type Account = {
  id: string;
  employeeId: string | null;
  loginName: string;
  enabled: boolean;
  mustChangePassword: boolean;
  version: number;
  createdAt: string;
};
export type Company = {
  id: string;
  code: string;
  name: string;
  version: number;
};
export type Department = {
  id: string;
  companyId: string;
  code: string;
  name: string;
  version: number;
};
export type Position = {
  id: string;
  departmentId: string;
  code: string;
  name: string;
  version: number;
};
export type Employee = {
  id: string;
  employeeKey: string;
  name: string;
  status: "ACTIVE" | "INACTIVE";
  version: number;
  createdAt: string;
};
export type Employment = {
  id: string;
  employeeId: string;
  companyId: string;
  departmentId: string;
  positionId: string;
  status: "ACTIVE" | "ENDED";
  version: number;
  startedAt: string | null;
  endedAt: string | null;
};
export type Application = {
  id: string;
  code: string;
  name: string;
  description: string;
  enabled: boolean;
  version: number;
  catalogVersion: number;
  systemAccessPermissionId: string;
  createdAt: string;
};
export type ApplicationInput = {
  code: string;
  name: string;
  description?: string;
  reason: string;
};
export type ApplicationEdit = {
  expectedVersion: number;
  name: string;
  description: string;
  enabled: boolean;
  reason: string;
};
export type ApplicationSave = { input: ApplicationEdit; previewId: string };
export type DirectoryNode =
  | {
      id: string;
      systemId: string;
      code: string;
      name: string;
      nodeType: "NAVIGATION";
      parentId: string | null;
      sortOrder: number;
      enabled: boolean;
      visibilityPermissionId: null;
      version: number;
    }
  | {
      id: string;
      systemId: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: string | null;
      sortOrder: number;
      enabled: false;
      visibilityPermissionId: string | null;
      version: number;
    }
  | {
      id: string;
      systemId: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: string | null;
      sortOrder: number;
      enabled: true;
      visibilityPermissionId: string;
      version: number;
    };
export type Permission =
  | {
      id: string;
      systemId: string;
      code: string;
      name: string;
      permissionType: "SYSTEM_ACCESS";
      functionId: null;
      menuEligible: false;
      enabled: boolean;
      version: number;
    }
  | {
      id: string;
      systemId: string;
      code: string;
      name: string;
      permissionType: "FUNCTION_OPERATION";
      functionId: string;
      menuEligible: boolean;
      enabled: boolean;
      version: number;
    };
export type NodeCreate =
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "NAVIGATION";
      parentId: null;
      parentLocalKey: string;
      sortOrder: number;
      enabled: boolean;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: null;
      parentLocalKey: string;
      sortOrder: number;
      enabled: false;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: null;
      parentLocalKey: string;
      sortOrder: number;
      enabled: false;
      visibilityPermissionId: string;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: null;
      parentLocalKey: string;
      sortOrder: number;
      enabled: false;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: string;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: null;
      parentLocalKey: string;
      sortOrder: number;
      enabled: true;
      visibilityPermissionId: string;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: null;
      parentLocalKey: string;
      sortOrder: number;
      enabled: true;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: string;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "NAVIGATION";
      parentId: string;
      parentLocalKey: null;
      sortOrder: number;
      enabled: boolean;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: string;
      parentLocalKey: null;
      sortOrder: number;
      enabled: false;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: string;
      parentLocalKey: null;
      sortOrder: number;
      enabled: false;
      visibilityPermissionId: string;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: string;
      parentLocalKey: null;
      sortOrder: number;
      enabled: false;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: string;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: string;
      parentLocalKey: null;
      sortOrder: number;
      enabled: true;
      visibilityPermissionId: string;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: string;
      parentLocalKey: null;
      sortOrder: number;
      enabled: true;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: string;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "NAVIGATION";
      parentId: null;
      parentLocalKey: null;
      sortOrder: number;
      enabled: boolean;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: null;
      parentLocalKey: null;
      sortOrder: number;
      enabled: false;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: null;
      parentLocalKey: null;
      sortOrder: number;
      enabled: false;
      visibilityPermissionId: string;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: null;
      parentLocalKey: null;
      sortOrder: number;
      enabled: false;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: string;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: null;
      parentLocalKey: null;
      sortOrder: number;
      enabled: true;
      visibilityPermissionId: string;
      visibilityPermissionLocalKey: null;
    }
  | {
      kind: "NODE_CREATE";
      localKey: string;
      code: string;
      name: string;
      nodeType: "FUNCTION";
      parentId: null;
      parentLocalKey: null;
      sortOrder: number;
      enabled: true;
      visibilityPermissionId: null;
      visibilityPermissionLocalKey: string;
    };
export type NodeUpdate = {
  kind: "NODE_UPDATE";
  id: string;
  expectedVersion: number;
  name: string;
  parentId: string | null;
  sortOrder: number;
  enabled: boolean;
  visibilityPermissionId: string | null;
};
export type PermissionCreate =
  | {
      kind: "PERMISSION_CREATE";
      localKey: string;
      functionId: string;
      functionLocalKey: null;
      code: string;
      name: string;
      menuEligible: boolean;
      enabled: boolean;
    }
  | {
      kind: "PERMISSION_CREATE";
      localKey: string;
      functionId: null;
      functionLocalKey: string;
      code: string;
      name: string;
      menuEligible: boolean;
      enabled: boolean;
    };
export type PermissionUpdate = {
  kind: "PERMISSION_UPDATE";
  id: string;
  expectedVersion: number;
  name: string;
  menuEligible: boolean;
  enabled: boolean;
};
export type CatalogCommand =
  NodeCreate | NodeUpdate | PermissionCreate | PermissionUpdate;
export type CatalogInput = {
  expectedCatalogVersion: number;
  commands: Array<CatalogCommand>;
  reason: string;
};
export type CatalogSave = {
  expectedCatalogVersion: number;
  commands: Array<CatalogCommand>;
  reason: string;
  previewId: string;
};
export type LocalId = { localKey: string; id: string };
export type CatalogResult = {
  systemId: string;
  catalogVersion: number;
  outcome: "APPLIED" | "NO_CHANGE";
  createdIds: Array<LocalId>;
  auditEventIds: Array<string>;
};
export type ImpactSummary = {
  visibleEmployeeCount: number;
  visibleLosingAccessCount: number;
  visibleRetainingAccessCount: number;
  visibleGainingAccessCount: number;
  visibility: "COMPLETE" | "SCOPED";
  hasUndisclosedImpact: boolean;
  warning: string;
  visibleRoleCount: number;
  visibleGrantCount: number;
  visibleSourceCount: number;
  visibleAppointmentCount: number;
  visiblePendingRequestCount: number;
};
export type ImpactEmployee = {
  employeeId: string;
  companyId: string;
  systemId: string;
  canAccessBefore: boolean;
  canAccessAfter: boolean;
  resultCompleteness: "COMPLETE" | "REDACTED";
  visibleGrantIds: Array<string>;
};
export type Preview = {
  id: string;
  contentDigest: string;
  expiresAt: string;
  impact: ImpactSummary;
  warnings: Array<string>;
};
export type Role =
  | {
      id: string;
      code: string;
      name: string;
      description: string;
      companyId: string;
      departmentId: string | null;
      systemId: string;
      state: "UNCONFIGURED";
      metadataVersion: number;
      configurationVersion: number;
      permissionIds: Array<string>;
      activeChangeRequestId: string | null;
      createdAt: string;
    }
  | {
      id: string;
      code: string;
      name: string;
      description: string;
      companyId: string;
      departmentId: string | null;
      systemId: string;
      state: "EFFECTIVE";
      metadataVersion: number;
      configurationVersion: number;
      permissionIds: Array<string>;
      activeChangeRequestId: string | null;
      createdAt: string;
    };
export type RoleCreate = {
  name: string;
  description?: string;
  companyId: string;
  departmentId: string | null;
  systemId: string;
  reason: string;
};
export type RoleMetadata = {
  expectedVersion: number;
  name: string;
  description: string;
  reason: string;
};
export type RoleCreated = { role: Role; draftId: string; draftVersion: number };
export type SourceKey = {
  employeeId: string;
  companyId: string;
  departmentId: string;
  systemId: string;
};
export type SourceBaseline = {
  source: SourceKey;
  sourceEpoch: number;
  version: number;
};
export type Grant =
  | {
      id: string;
      employeeId: string;
      roleId: string;
      companyId: string;
      sourceDepartmentId: string;
      systemId: string;
      employmentIds: Array<string>;
      sourceEpoch: number;
      relationVersion: number;
      status: "ACTIVE";
      endedReason: string | null;
      createdAt: string;
    }
  | {
      id: string;
      employeeId: string;
      roleId: string;
      companyId: string;
      sourceDepartmentId: string;
      systemId: string;
      employmentIds: Array<string>;
      sourceEpoch: number;
      relationVersion: number;
      status: "REVOKED" | "INVALIDATED";
      endedReason: string | null;
      createdAt: string;
    };
export type GrantKey = {
  employeeId: string;
  roleId: string;
  sourceCompanyId: string;
  sourceDepartmentId: string;
};
export type GrantBaseline = {
  key: GrantKey;
  systemId: string;
  roleConfigurationVersion: number;
  sourceEpoch: number;
  relationVersion: number;
  employmentIds: Array<string>;
  activeGrantId: string | null;
};
export type BaselineQuery = { items: Array<GrantKey> };
export type BaselineResult = { items: Array<GrantBaseline> };
export type GrantLine = {
  employeeId: string;
  roleId: string;
  sourceCompanyId: string;
  sourceDepartmentId: string;
  expectedRoleConfigurationVersion: number;
  expectedSourceEpoch: number;
  expectedRelationVersion: number;
  employmentIds: Array<string>;
};
export type RevokeLine = { grantId: string; expectedRelationVersion: number };
export type RoleConfiguration = {
  kind: "ROLE_CONFIGURATION";
  companyId: string;
  roleId: string;
  expectedConfigurationVersion: number;
  permissionIds: Array<string>;
};
export type RoleGrant = {
  kind: "ROLE_GRANT";
  companyId: string;
  items: Array<GrantLine>;
};
export type RoleRevocation = {
  kind: "ROLE_REVOCATION";
  companyId: string;
  items: Array<RevokeLine>;
};
export type ChangeContent = RoleConfiguration | RoleGrant | RoleRevocation;
export type DraftInput = {
  content: ChangeContent;
  reason: string;
  copiedFromRequestId?: string;
};
export type DraftEdit = {
  expectedDraftVersion: number;
  content: ChangeContent;
  reason: string;
};
export type Authority =
  | {
      kind: "GLOBAL";
      scopeId: null;
      scopeVersion: null;
      appointmentId: null;
      appointmentVersion: null;
      companyId: string;
      departmentId: null;
      systemIds: Array<string>;
    }
  | {
      kind: "SCOPE";
      scopeId: string;
      scopeVersion: number;
      appointmentId: string;
      appointmentVersion: number;
      companyId: string;
      departmentId: string;
      systemIds: Array<string>;
    };
export type NoChangeLine = {
  index: number;
  reason: "ALREADY_GRANTED" | "ALREADY_ENDED" | "SAME_CONFIGURATION";
  grantId: string | null;
};
export type ChangeRequest =
  | DraftChangeRequest
  | PendingChangeRequest
  | RejectedChangeRequest
  | ApprovedChangeRequest
  | ExecutedChangeRequest
  | ExecutionFailedChangeRequest
  | InvalidatedChangeRequest;
export type ChangePreviewInput = { expectedDraftVersion: number };
export type ChangePreview = {
  preview: Preview;
  draftId: string;
  draftVersion: number;
  addedPermissionIds: Array<string>;
  removedPermissionIds: Array<string>;
  effectiveContent: ChangeContent | null;
  noChangeItems: Array<NoChangeLine>;
};
export type SubmitInput = { expectedDraftVersion: number; previewId: string };
export type SubmissionResult =
  | {
      outcome: "SUBMITTED";
      request: PendingChangeRequest;
      noChangeItems: Array<NoChangeLine>;
    }
  | {
      outcome: "NO_CHANGE";
      request: DraftChangeRequest;
      noChangeItems: Array<NoChangeLine>;
    };
export type RetryInput = { expectedStatusVersion: number; reason: string };
export type SourceQuery = {
  employeeId: string;
  companyId: string;
  departmentId: string;
  systemIds: Array<string>;
};
export type SourceQueryResult = { items: Array<SourceBaseline> };
export type SourceSystem = {
  systemId: string;
  expectedSourceEpoch: number;
  expectedVersion: number;
};
export type SourceCloseInput = {
  employeeId: string;
  companyId: string;
  departmentId: string;
  systems: Array<SourceSystem>;
  reason: string;
};
export type SourceCloseSave = { input: SourceCloseInput; previewId: string };
export type SourceCloseResult = (
  | { visibility: "COMPLETE"; hasUndisclosedImpact: false }
  | { visibility: "SCOPED"; hasUndisclosedImpact: true }
) & {
  outcome: "APPLIED";
  updatedSources: Array<SourceBaseline>;
  auditEventIds: Array<string>;
  invalidatedGrantCount: number;
  invalidatedRequestCount: number;
  visibility: "COMPLETE" | "SCOPED";
  hasUndisclosedImpact: boolean;
};
export type EmployeeStateInput = {
  expectedVersion: number;
  status: "ACTIVE" | "INACTIVE";
  reason: string;
};
export type EmployeeStateSave = {
  input: EmployeeStateInput;
  previewId: string;
};
export type EmployeeStateResult = {
  employee: Employee;
  outcome: "APPLIED" | "NO_CHANGE";
  invalidatedGrantCount: number;
  invalidatedAppointmentCount: number;
  auditEventIds: Array<string>;
};
export type Scope = {
  id: string;
  companyId: string;
  departmentId: string;
  systemIds: Array<string>;
  version: number;
  createdAt: string;
};
export type Appointment = {
  id: string;
  scopeId: string;
  accountId: string;
  employeeId: string;
  status: "ACTIVE" | "REVOKED" | "INVALIDATED";
  version: number;
  scopeVersion: number;
  companyId: string;
  departmentId: string;
  systemIds: Array<string>;
};
export type ScopeInput = {
  companyId: string;
  departmentId: string;
  systemIds: Array<string>;
  accountIds: Array<string>;
  reason: string;
};
export type ScopeEdit = {
  expectedVersion: number;
  companyId: string;
  departmentId: string;
  systemIds: Array<string>;
  accountIds: Array<string>;
  reason: string;
};
export type ScopeSave = { input: ScopeEdit; previewId: string };
export type ScopeCreateSave = { input: ScopeInput; previewId: string };
export type ScopeResult = {
  scope: Scope;
  auditEventIds: Array<string>;
  outcome: "APPLIED" | "NO_CHANGE";
  appointmentsChangedCount: number;
};
export type ChangeEvent = {
  id: string;
  requestId: string;
  occurredAt: string;
  eventType:
    | "DRAFT_SAVED"
    | "SUBMITTED"
    | "DELIVERY_FAILED"
    | "DELIVERY_LINKED"
    | "OA_APPROVED"
    | "OA_REJECTED"
    | "EXECUTION_FAILED"
    | "EXECUTED"
    | "INVALIDATED";
  actorId: string;
  message: string;
};
export type AuditEvent = {
  id: string;
  occurredAt: string;
  eventType: string;
  companyId: string | null;
  sourceDepartmentId: string | null;
  systemId: string | null;
  actorId: string;
  targetId: string;
  result: string;
  changeRequestId: string | null;
  requestId: string;
  reason: string;
};
export type AuditDifference = {
  field: string;
  before: string | null;
  after: string | null;
};
export type AuditDetail = {
  event: AuditEvent;
  differences: Array<AuditDifference>;
  originalSubmitterId: string | null;
  oaApprovalId: string | null;
  submittedVersion: number | null;
  failureCode: string | null;
};
export type AuthorizationInput = { employeeKey: string; companyId: string };
export type Menu = {
  id: string;
  code: string;
  parentId: string | null;
  name: string;
  containerOnly: boolean;
  sortOrder: number;
};
export type EffectiveOperation = {
  permissionId: string;
  permissionCode: string;
  functionId: string;
  functionCode: string;
};
export type AuthorizationResult =
  | {
      employeeKey: string;
      companyId: string;
      systemId: string;
      systemCode: string;
      canAccessSystem: false;
      menus: Array<Menu>;
      operations: Array<EffectiveOperation>;
      evaluatedAt: string;
      requestId: string;
    }
  | {
      employeeKey: string;
      companyId: string;
      systemId: string;
      systemCode: string;
      canAccessSystem: true;
      menus: Array<Menu>;
      operations: Array<EffectiveOperation>;
      evaluatedAt: string;
      requestId: string;
    };
export type CheckInput = {
  employeeKey: string;
  companyId: string;
  permissionCode: string;
};
export type CheckResult =
  | {
      employeeKey: string;
      companyId: string;
      systemId: string;
      permissionCode: string;
      permissionType: null;
      hasPermission: false;
      evaluatedAt: string;
      requestId: string;
    }
  | {
      employeeKey: string;
      companyId: string;
      systemId: string;
      permissionCode: string;
      permissionType: "SYSTEM_ACCESS" | "FUNCTION_OPERATION";
      hasPermission: boolean;
      evaluatedAt: string;
      requestId: string;
    };
export type OaFinalResult = {
  eventId: string;
  requestId: string;
  oaApprovalId: string;
  submittedVersion: number;
  contentDigest: string;
  decision: "APPROVED" | "REJECTED";
  isFinal: true;
  decidedAt: string;
  comment: string;
};
export type OaReceipt =
  | {
      receiptId: string;
      requestId: string;
      accepted: true;
      duplicate: boolean;
      approvalResult: "REJECTED";
      executionStatus: "NOT_STARTED" | "INVALIDATED";
      retryable: false;
    }
  | {
      receiptId: string;
      requestId: string;
      accepted: true;
      duplicate: boolean;
      approvalResult: "APPROVED";
      executionStatus: "PENDING" | "FAILED";
      retryable: true;
    }
  | {
      receiptId: string;
      requestId: string;
      accepted: true;
      duplicate: boolean;
      approvalResult: "APPROVED";
      executionStatus: "SUCCEEDED" | "INVALIDATED";
      retryable: false;
    };
export type ImportFile = {
  id: string;
  fileName: string;
  sha256: string;
  sizeBytes: number;
  sheetNames: Array<string>;
  createdAt: string;
};
export type FieldMapping = {
  sourceColumn: string;
  targetField:
    | "employeeId"
    | "employeeKey"
    | "previousEmployeeKey"
    | "name"
    | "action"
    | "companyCode"
    | "departmentCode"
    | "positionCode"
    | "employmentId"
    | "expectedVersion"
    | "companyName"
    | "departmentName"
    | "positionName"
    | "targetCompanyCode"
    | "employmentState"
    | "startedAt"
    | "endedAt";
};
export type OrganizationMapping = {
  sourcePath: string;
  companyId: string | null;
  departmentId: string | null;
  companyCode: string | null;
  departmentCode: string | null;
};
export type ImportInput = {
  fileId: string;
  sourceSystem: string;
  sourceBatchId: string;
  importType: "ORGANIZATION" | "EMPLOYEE_INITIALIZATION" | "PERSONNEL_CHANGES";
  worksheet: string;
  fieldMappings: Array<FieldMapping>;
  organizationMappings: Array<OrganizationMapping>;
  initialEmploymentState: "EXPLICIT_ACTIVE" | "FROM_SOURCE";
  reason: string;
};
export type ImportIssue = {
  rowNumber: number;
  field: string;
  severity: "WARNING" | "BLOCKING";
  code: string;
  message: string;
};
export type ImportCounts = {
  created: number;
  updated: number;
  unchanged: number;
  endedEmployments: number;
  disabledEmployees: number;
  restoredEmployees: number;
  invalidatedGrants: number;
  invalidatedAppointments: number;
};
export type ImportBatch = {
  id: string;
  input: ImportInput;
  version: number;
  contentDigest: string;
  state: "BLOCKED" | "READY" | "SUCCEEDED" | "FAILED" | "STALE";
  plannedCounts: ImportCounts;
  actualCounts: ImportCounts | null;
  blockingIssueCount: number;
  warningCount: number;
  failureCode: string | null;
  createdAt: string;
};
export type ImportCommit = { expectedVersion: number; contentDigest: string };
export type CompanyPage = {
  items: Array<Company>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type DepartmentPage = {
  items: Array<Department>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type PositionPage = {
  items: Array<Position>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type EmployeePage = {
  items: Array<Employee>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type EmploymentPage = {
  items: Array<Employment>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type ApplicationPage = {
  items: Array<Application>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type DirectoryNodePage = {
  items: Array<DirectoryNode>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type PermissionPage = {
  items: Array<Permission>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type RolePage = {
  items: Array<Role>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type GrantPage = {
  items: Array<Grant>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type AccountPage = {
  items: Array<Account>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type ScopePage = {
  items: Array<Scope>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type AppointmentPage = {
  items: Array<Appointment>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type ChangeRequestPage = {
  items: Array<ChangeRequest>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type ChangeEventPage = {
  items: Array<ChangeEvent>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type AuditEventPage = {
  items: Array<AuditEvent>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type ImpactEmployeePage = {
  items: Array<ImpactEmployee>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type ImportBatchPage = {
  items: Array<ImportBatch>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type ImportIssuePage = {
  items: Array<ImportIssue>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type OaSubmission = {
  requestId: string;
  submittedVersion: number;
  contentDigest: string;
  content:
    | {
        kind: "ROLE_CONFIGURATION";
        companyId: string;
        roleId: string;
        expectedConfigurationVersion: number;
        permissionIds: Array<string>;
      }
    | RoleGrant
    | RoleRevocation;
  reason: string;
  originalAuthority: Authority;
  submittedByAccountId: string;
  submittedAt: string;
};
export type OaLink = {
  requestId: string;
  submittedVersion: number;
  contentDigest: string;
  oaApprovalId: string;
  acceptedAt: string;
};
export type ImportPlanChange = {
  rowNumber: number;
  action:
    | "CREATE_COMPANY"
    | "RENAME_COMPANY"
    | "CREATE_DEPARTMENT"
    | "RENAME_DEPARTMENT"
    | "MOVE_DEPARTMENT"
    | "CREATE_POSITION"
    | "RENAME_POSITION"
    | "CREATE_EMPLOYEE"
    | "RENAME_EMPLOYEE"
    | "REMAP_EMPLOYEE_KEY"
    | "ADD_EMPLOYMENT"
    | "END_EMPLOYMENT"
    | "DISABLE_EMPLOYEE"
    | "RESTORE_EMPLOYEE"
    | "UNCHANGED";
  targetId: string | null;
  stableCode: string | null;
  expectedVersion: number | null;
  summary: string;
  companyCode: string | null;
  departmentCode: string | null;
  positionCode: string | null;
  fieldChanges: Array<ImportValueChange>;
};
export type ImportPlanChangePage = {
  items: Array<ImportPlanChange>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type ImpactTarget =
  | {
      targetType: "SOURCE";
      targetId: string | null;
      source: SourceKey;
      companyId: string | null;
      departmentId: string | null;
      systemId: string | null;
      targetVersion: number | null;
      effect:
        | "CONFIGURATION_CHANGE"
        | "ENABLE"
        | "DISABLE"
        | "INVALIDATE"
        | "REVOKE_APPOINTMENT"
        | "KEEP_VALID"
        | "CLOSE_SOURCE";
      summary: string;
    }
  | {
      targetType:
        | "ROLE"
        | "GRANT"
        | "APPOINTMENT"
        | "CHANGE_REQUEST"
        | "DIRECTORY_NODE"
        | "PERMISSION";
      targetId: string;
      source: SourceKey | null;
      companyId: string | null;
      departmentId: string | null;
      systemId: string | null;
      targetVersion: number | null;
      effect:
        | "CONFIGURATION_CHANGE"
        | "ENABLE"
        | "DISABLE"
        | "INVALIDATE"
        | "REVOKE_APPOINTMENT"
        | "KEEP_VALID"
        | "CLOSE_SOURCE";
      summary: string;
    };
export type ImpactTargetPage = {
  items: Array<ImpactTarget>;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
  total: number;
};
export type ImportValueChange = {
  field:
    | "employeeKey"
    | "name"
    | "companyCode"
    | "departmentCode"
    | "positionCode"
    | "employmentId"
    | "employmentState"
    | "startedAt"
    | "endedAt"
    | "status";
  before: string | number | boolean | null;
  after: string | number | boolean | null;
};
export type DraftChangeRequest = {
  id: string;
  content: ChangeContent;
  reason: string;
  draftVersion: number;
  submittedVersion: null;
  contentDigest: null;
  statusVersion: number;
  state: "DRAFT";
  deliveryStatus: "NOT_SUBMITTED";
  approvalResult: "NONE";
  executionStatus: "NOT_STARTED";
  originalAuthority: null;
  submittedByAccountId: null;
  oaApprovalId: null;
  retryable: false;
  failureCode: null;
  nextRetryAt: null;
  createdAt: string;
  submittedAt: null;
};
export type PendingChangeRequest =
  | {
      id: string;
      content: ChangeContent;
      reason: string;
      draftVersion: number;
      submittedVersion: number;
      contentDigest: string;
      statusVersion: number;
      state: "PENDING";
      deliveryStatus: "PENDING_SEND";
      approvalResult: "NONE";
      executionStatus: "NOT_STARTED";
      originalAuthority: Authority;
      submittedByAccountId: string;
      oaApprovalId: null;
      retryable: false;
      failureCode: null;
      nextRetryAt: null;
      createdAt: string;
      submittedAt: string;
    }
  | {
      id: string;
      content: ChangeContent;
      reason: string;
      draftVersion: number;
      submittedVersion: number;
      contentDigest: string;
      statusVersion: number;
      state: "PENDING";
      deliveryStatus: "SEND_FAILED";
      approvalResult: "NONE";
      executionStatus: "NOT_STARTED";
      originalAuthority: Authority;
      submittedByAccountId: string;
      oaApprovalId: null;
      retryable: false;
      failureCode: null;
      nextRetryAt: null;
      createdAt: string;
      submittedAt: string;
    }
  | {
      id: string;
      content: ChangeContent;
      reason: string;
      draftVersion: number;
      submittedVersion: number;
      contentDigest: string;
      statusVersion: number;
      state: "PENDING";
      deliveryStatus: "LINKED";
      approvalResult: "NONE";
      executionStatus: "NOT_STARTED";
      originalAuthority: Authority;
      submittedByAccountId: string;
      oaApprovalId: string;
      retryable: false;
      failureCode: null;
      nextRetryAt: null;
      createdAt: string;
      submittedAt: string;
    };
export type RejectedChangeRequest = {
  id: string;
  content: ChangeContent;
  reason: string;
  draftVersion: number;
  submittedVersion: number;
  contentDigest: string;
  statusVersion: number;
  state: "REJECTED";
  deliveryStatus: "LINKED";
  approvalResult: "REJECTED";
  executionStatus: "NOT_STARTED";
  originalAuthority: Authority;
  submittedByAccountId: string;
  oaApprovalId: string;
  retryable: false;
  failureCode: null;
  nextRetryAt: null;
  createdAt: string;
  submittedAt: string;
};
export type ApprovedChangeRequest = {
  id: string;
  content: ChangeContent;
  reason: string;
  draftVersion: number;
  submittedVersion: number;
  contentDigest: string;
  statusVersion: number;
  state: "APPROVED";
  deliveryStatus: "LINKED";
  approvalResult: "APPROVED";
  executionStatus: "PENDING";
  originalAuthority: Authority;
  submittedByAccountId: string;
  oaApprovalId: string;
  retryable: true;
  failureCode: null;
  nextRetryAt: string | null;
  createdAt: string;
  submittedAt: string;
};
export type ExecutedChangeRequest = {
  id: string;
  content: ChangeContent;
  reason: string;
  draftVersion: number;
  submittedVersion: number;
  contentDigest: string;
  statusVersion: number;
  state: "EXECUTED";
  deliveryStatus: "LINKED";
  approvalResult: "APPROVED";
  executionStatus: "SUCCEEDED";
  originalAuthority: Authority;
  submittedByAccountId: string;
  oaApprovalId: string;
  retryable: false;
  failureCode: null;
  nextRetryAt: null;
  createdAt: string;
  submittedAt: string;
};
export type ExecutionFailedChangeRequest = {
  id: string;
  content: ChangeContent;
  reason: string;
  draftVersion: number;
  submittedVersion: number;
  contentDigest: string;
  statusVersion: number;
  state: "EXECUTION_FAILED";
  deliveryStatus: "LINKED";
  approvalResult: "APPROVED";
  executionStatus: "FAILED";
  originalAuthority: Authority;
  submittedByAccountId: string;
  oaApprovalId: string;
  retryable: true;
  failureCode: string;
  nextRetryAt: string;
  createdAt: string;
  submittedAt: string;
};
export type InvalidatedChangeRequest =
  | {
      id: string;
      content: ChangeContent;
      reason: string;
      draftVersion: number;
      submittedVersion: number;
      contentDigest: string;
      statusVersion: number;
      state: "INVALIDATED";
      deliveryStatus: "PENDING_SEND";
      approvalResult: "NONE";
      executionStatus: "INVALIDATED";
      originalAuthority: Authority;
      submittedByAccountId: string;
      oaApprovalId: null;
      retryable: false;
      failureCode: string;
      nextRetryAt: null;
      createdAt: string;
      submittedAt: string;
    }
  | {
      id: string;
      content: ChangeContent;
      reason: string;
      draftVersion: number;
      submittedVersion: number;
      contentDigest: string;
      statusVersion: number;
      state: "INVALIDATED";
      deliveryStatus: "SEND_FAILED";
      approvalResult: "NONE";
      executionStatus: "INVALIDATED";
      originalAuthority: Authority;
      submittedByAccountId: string;
      oaApprovalId: null;
      retryable: false;
      failureCode: string;
      nextRetryAt: null;
      createdAt: string;
      submittedAt: string;
    }
  | {
      id: string;
      content: ChangeContent;
      reason: string;
      draftVersion: number;
      submittedVersion: number;
      contentDigest: string;
      statusVersion: number;
      state: "INVALIDATED";
      deliveryStatus: "LINKED";
      approvalResult: "NONE" | "APPROVED" | "REJECTED";
      executionStatus: "INVALIDATED";
      originalAuthority: Authority;
      submittedByAccountId: string;
      oaApprovalId: string;
      retryable: false;
      failureCode: string;
      nextRetryAt: null;
      createdAt: string;
      submittedAt: string;
    };
export type Inputs = {
  login: LoginInput;
  currentSession: undefined;
  logout: undefined;
  changeOwnPassword: PasswordInput;
  myAppointments: undefined;
  listAccounts: undefined;
  createAccount: AccountCreate;
  getAccount: undefined;
  setAccountState: AccountStateInput;
  resetAccountPassword: PasswordReset;
  listSystems: undefined;
  createSystem: ApplicationInput;
  getSystem: undefined;
  updateSystem: ApplicationSave;
  systemStatePreview: ApplicationEdit;
  listDirectoryNodes: undefined;
  listPermissions: undefined;
  catalogPreview: CatalogInput;
  saveCatalog: CatalogSave;
  listRoles: undefined;
  createRole: RoleCreate;
  getRole: undefined;
  updateRoleMetadata: RoleMetadata;
  listEmployees: undefined;
  getEmployee: undefined;
  listEmployments: undefined;
  listGrants: undefined;
  grantBaselines: BaselineQuery;
  listChangeRequests: undefined;
  createChangeDraft: DraftInput;
  getChangeRequest: undefined;
  updateChangeDraft: DraftEdit;
  deleteChangeDraft: undefined;
  changePreview: ChangePreviewInput;
  submitChange: SubmitInput;
  retryOaDelivery: RetryInput;
  listChangeEvents: undefined;
  listPreviewEmployees: undefined;
  sourceBaselines: SourceQuery;
  sourceClosePreview: SourceCloseInput;
  closeSource: SourceCloseSave;
  listCompanies: undefined;
  listDepartments: undefined;
  listPositions: undefined;
  employeeStatePreview: EmployeeStateInput;
  setEmployeeState: EmployeeStateSave;
  listScopes: undefined;
  createScope: ScopeCreateSave;
  scopeCreatePreview: ScopeInput;
  getScope: undefined;
  replaceScope: ScopeSave;
  listScopeAppointments: undefined;
  scopeEditPreview: ScopeEdit;
  listAudit: undefined;
  getAudit: undefined;
  uploadImportFile: { file: string };
  getImportFile: undefined;
  precheckImport: ImportInput;
  listImportBatches: undefined;
  getImportBatch: undefined;
  listImportIssues: undefined;
  commitImport: ImportCommit;
  listImportPlanChanges: undefined;
  listPreviewTargets: undefined;
};
export type Outputs = {
  login: Session;
  currentSession: SessionView;
  logout: void;
  changeOwnPassword: void;
  myAppointments: AppointmentPage;
  listAccounts: AccountPage;
  createAccount: Account;
  getAccount: Account;
  setAccountState: Account;
  resetAccountPassword: Account;
  listSystems: ApplicationPage;
  createSystem: Application;
  getSystem: Application;
  updateSystem: Application;
  systemStatePreview: Preview;
  listDirectoryNodes: DirectoryNodePage;
  listPermissions: PermissionPage;
  catalogPreview: Preview;
  saveCatalog: CatalogResult;
  listRoles: RolePage;
  createRole: RoleCreated;
  getRole: Role;
  updateRoleMetadata: Role;
  listEmployees: EmployeePage;
  getEmployee: Employee;
  listEmployments: EmploymentPage;
  listGrants: GrantPage;
  grantBaselines: BaselineResult;
  listChangeRequests: ChangeRequestPage;
  createChangeDraft: ChangeRequest;
  getChangeRequest: ChangeRequest;
  updateChangeDraft: ChangeRequest;
  deleteChangeDraft: void;
  changePreview: ChangePreview;
  submitChange: SubmissionResult;
  retryOaDelivery: ChangeRequest;
  listChangeEvents: ChangeEventPage;
  listPreviewEmployees: ImpactEmployeePage;
  sourceBaselines: SourceQueryResult;
  sourceClosePreview: Preview;
  closeSource: SourceCloseResult;
  listCompanies: CompanyPage;
  listDepartments: DepartmentPage;
  listPositions: PositionPage;
  employeeStatePreview: Preview;
  setEmployeeState: EmployeeStateResult;
  listScopes: ScopePage;
  createScope: ScopeResult;
  scopeCreatePreview: Preview;
  getScope: Scope;
  replaceScope: ScopeResult;
  listScopeAppointments: AppointmentPage;
  scopeEditPreview: Preview;
  listAudit: AuditEventPage;
  getAudit: AuditDetail;
  uploadImportFile: ImportFile;
  getImportFile: ImportFile;
  precheckImport: ImportBatch;
  listImportBatches: ImportBatchPage;
  getImportBatch: ImportBatch;
  listImportIssues: ImportIssuePage;
  commitImport: ImportBatch;
  listImportPlanChanges: ImportPlanChangePage;
  listPreviewTargets: ImpactTargetPage;
};
