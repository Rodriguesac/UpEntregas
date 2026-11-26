import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import vm from "node:vm";

const code=readFileSync(new URL("../web-gestor-independente/manual-address.js",import.meta.url),"utf8");
const api={};
vm.runInNewContext(code,{window:api,console});
assert.equal(typeof api.UPManualAddress?.open,"function");
assert.equal(typeof api.UPManualAddress?.cleanup,"function");
assert.match(code,/typeof gl\.supported==="function"/);
assert.doesNotMatch(code,/\bmaplibregl\.supported\s*\(/);
assert.match(code,/typeof gl\.Map!=="function"/);
console.log("PASS: maplibre v5 compatibility: optional supported() API");
