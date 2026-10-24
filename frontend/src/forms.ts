import type { Operation } from "./api/routes";
import type { Preview } from "./api/schema";
export type Values = Record<string, any>;
export type Field = {
  key: string;
  label: string;
  type?:
    | "text"
    | "password"
    | "textarea"
    | "boolean"
    | "select"
    | "entity"
    | "number";
  required?: boolean;
  minLength?: number;
  maxLength?: number;
  options?: { value: string; label: string }[];
  operation?: Operation;
  params?: Record<string, string>;
  query?: Record<string, unknown> | ((v: Values) => Record<string, unknown>);
  multiple?: boolean;
  hint?: string;
  dependsOn?: string[];
};
export type FormSpec = {
  title: string;
  description?: string;
  fields: Field[];
  initial?: Values;
  preview?: (v: Values) => Promise<Preview>;
  save: (v: Values, key: string, preview?: Preview) => Promise<unknown>;
  done?: () => void;
  submitLabel?: string;
};
export const reason: Field = {
  key: "reason",
  label: "办理原因",
  type: "textarea",
  required: true,
  maxLength: 500,
};
export const entity = (
  key: string,
  label: string,
  operation: Operation,
  extra: Partial<Field> = {},
): Field => ({
  key,
  label,
  operation,
  type: "entity",
  required: true,
  ...extra,
});
