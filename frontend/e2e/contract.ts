import { readFileSync } from "node:fs";
import { parse } from "yaml";
import Ajv from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import type { Page, Request } from "@playwright/test";
const spec = parse(
  readFileSync(
    new URL("../../docs/03-api/openapi.yaml", import.meta.url),
    "utf8",
  ),
);
const ajv = new Ajv({ strict: false, allErrors: true });
addFormats(ajv);
ajv.addSchema({ $id: "rbac-contract", components: spec.components });
const validators = new Map<string, ReturnType<typeof ajv.compile>>();
function validate(request: Request) {
  const url = new URL(request.url());
  if (!url.pathname.startsWith("/api/v1/")) return;
  const path = Object.keys(spec.paths).find((p) =>
    new RegExp("^" + p.replace(/\{[^}]+\}/g, "[^/]+") + "$").test(url.pathname),
  );
  const op = path && spec.paths[path][request.method().toLowerCase()];
  if (!op)
    throw new Error(
      "Undeclared API operation: " + request.method() + " " + url.pathname,
    );
  if (!path.startsWith("/api/v1/admin/"))
    throw new Error("Browser must not call a service-credential endpoint");
  const parameters = (op.parameters || []).map((p: any) =>
    p.$ref ? spec.components.parameters[p.$ref.split("/").at(-1)] : p,
  );
  for (const key of url.searchParams.keys())
    if (!parameters.some((p: any) => p.in === "query" && p.name === key))
      throw new Error("Unknown query parameter " + key + " on " + path);
  const schema = op.requestBody?.content?.["application/json"]?.schema;
  if (schema) {
    let v = validators.get(op.operationId);
    if (!v) {
      const copy = JSON.parse(
        JSON.stringify(schema).replaceAll(
          '"#/components/',
          '"rbac-contract#/components/',
        ),
      );
      v = ajv.compile(copy);
      validators.set(op.operationId, v);
    }
    if (!v(request.postDataJSON()))
      throw new Error(
        op.operationId +
          " request violates contract: " +
          ajv.errorsText(v.errors),
      );
  }
  const headers = request.headers();
  for (const p of parameters.filter(
    (p: any) => p.in === "header" && p.required,
  ))
    if (!headers[p.name.toLowerCase()])
      throw new Error("Missing " + p.name + " on " + path);
}
export function monitor(page: Page) {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("request", (r) => {
    try {
      validate(r);
    } catch (e) {
      errors.push(String(e));
    }
  });
  return errors;
}
