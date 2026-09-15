import { readFileSync, writeFileSync } from 'node:fs';

export function readEnv(path = '.env') {
  const text = readFileSync(path, 'utf8');
  const values = new Map();
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const separator = line.indexOf('=');
    if (separator < 1) continue;
    const key = line.slice(0, separator).trim();
    let value = line.slice(separator + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    values.set(key, value);
  }
  return { text, values };
}

export function writeEnvValue(path, key, value) {
  const { text } = readEnv(path);
  const expression = new RegExp(`^${key}=.*$`, 'm');
  const next = expression.test(text)
    ? text.replace(expression, `${key}=${value}`)
    : `${text.replace(/\s*$/, '')}\n${key}=${value}\n`;
  writeFileSync(path, next, 'utf8');
}
