import fs from "node:fs";
import { parse } from "yaml";
import { format } from "prettier";
// Types/routes only. No example values or credentials are copied into the application.
const spec = parse(
  fs.readFileSync(
    new URL("../../docs/03-api/openapi.yaml", import.meta.url),
    "utf8",
  ),
);
const schemas = spec.components.schemas;
const quote = JSON.stringify;
function type(s) {
  if (!s) return "unknown";
  if (s.$ref) {
    const path = s.$ref.split("/").slice(1);
    if (path.length === 3 && path[0] === "components" && path[1] === "schemas")
      return path[2];
    const value = path.reduce(
      (v, k) => v?.[k.replace(/~1/g, "/").replace(/~0/g, "~")],
      spec,
    );
    if (!value) throw new Error("Unresolved reference: " + s.$ref);
    return type(value);
  }
  if (s.enum) return s.enum.map(quote).join(" | ");
  if (s.const !== undefined) return quote(s.const);
  const parts = [];
  if (s.oneOf || s.anyOf)
    parts.push("(" + (s.oneOf || s.anyOf).map(type).join(" | ") + ")");
  if (s.allOf) parts.push(...s.allOf.map(type));
  if (s.properties)
    parts.push(
      "{ " +
        Object.entries(s.properties)
          .map(
            ([k, v]) =>
              `${quote(k)}${s.required?.includes(k) ? "" : "?"}: ${type(v)}`,
          )
          .join("; ") +
        " }",
    );
  if (parts.length) return parts.join(" & ");
  if (Array.isArray(s.type))
    return s.type.map((t) => type({ ...s, type: t })).join(" | ");
  if (s.type === "array") return `Array<${type(s.items)}>`;
  if (s.type === "integer" || s.type === "number") return "number";
  if (s.type === "boolean") return "boolean";
  if (s.type === "null") return "null";
  if (s.type === "object") return "Record<string, unknown>";
  return "string";
}
const routes = {},
  inputs = [],
  outputs = [];
for (const [path, methods] of Object.entries(spec.paths))
  for (const [method, op] of Object.entries(methods)) {
    if (
      !["get", "post", "put", "delete", "patch"].includes(method) ||
      !path.startsWith("/api/v1/admin/")
    )
      continue;
    const params = (op.parameters || []).map((p) =>
      p.$ref ? spec.components.parameters[p.$ref.split("/").at(-1)] : p,
    );
    const body = Object.values(op.requestBody?.content || {})[0]?.schema;
    routes[op.operationId] = {
      path,
      method: method.toUpperCase(),
      scoped: params.some((p) => p.name === "X-Admin-Appointment-Id"),
      idempotent: params.some((p) => p.name === "Idempotency-Key"),
      query: params.filter((p) => p.in === "query").map((p) => p.name),
    };
    inputs.push(`${op.operationId}: ${body ? type(body) : "undefined"}`);
    const responses = Object.entries(op.responses)
      .filter(([s]) => s.startsWith("2"))
      .map(([, r]) => Object.values(r.content || {})[0]?.schema)
      .map((s) => (s ? type(s) : "void"));
    outputs.push(`${op.operationId}: ${[...new Set(responses)].join(" | ")}`);
  }
async function writeTypes(url, source) {
  fs.writeFileSync(url, await format(source, { parser: "typescript" }));
}
await writeTypes(
  new URL("../src/api/schema.ts", import.meta.url),
  "// Generated from docs/03-api/openapi.yaml; npm run generate:api\n" +
    Object.entries(schemas)
      .map(([k, s]) => `export type ${k} = ${type(s)};`)
      .join("\n") +
    `\nexport type Inputs = {${inputs.join(";\n")}};\nexport type Outputs = {${outputs.join(";\n")}};\n`,
);
await writeTypes(
  new URL("../src/api/routes.ts", import.meta.url),
  "// Generated; admin operations only.\nexport const routes = " +
    JSON.stringify(routes, null, 2) +
    " as const;\nexport type Operation = keyof typeof routes;\n",
);
console.log(
  `Generated ${Object.keys(routes).length} administrator operations.`,
);
