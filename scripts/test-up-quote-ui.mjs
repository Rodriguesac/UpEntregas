import assert from "node:assert/strict";import fs from "node:fs";import vm from "node:vm";
const source=fs.readFileSync("web-gestor-independente/app.js","utf8");
const fn=source.slice(source.indexOf("function quoteNumber("),source.indexOf("async function tariffSettings("));
const ctx=vm.createContext({});vm.runInContext(fn,ctx);
assert.equal(ctx.quoteNumber("9,50"),9.5);assert.equal(ctx.quoteNumber(""),null);assert.throws(()=>ctx.quoteNumber("-1"));assert.throws(()=>ctx.quoteNumber("1.001"));
assert.ok(source.includes('quote_mode:mode'));assert.ok(source.includes('tariff_save'));console.log("Quote UI validation passed");
