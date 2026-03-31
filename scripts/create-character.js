#!/usr/bin/env node
/**
 * create-character.js
 *
 * Creates a new MoodFox character's neutral (mood 0) image using an existing
 * character as a style reference. The user provides a name and description
 * for the new character, and the script generates it in the same art style.
 *
 * Usage:
 *   node scripts/create-character.js \
 *     --name panda \
 *     --description "a cute round panda with big dark eye patches" \
 *     --style-ref path/to/fox_mood_0.png \
 *     --api-key sk-...                         # or set OPENAI_API_KEY env var
 *     [--output app/mobile/src/main/res/drawable-nodpi]
 *     [--attempts 3]                           # number of variants to generate
 */

import fs             from 'fs';
import path           from 'path';
import { execSync }   from 'child_process';
import { parseArgs }  from 'util';
import OpenAI         from 'openai';

// ── PNG validation ───────────────────────────────────────────────────────────

const PNG_MAGIC = Buffer.from([0x89, 0x50, 0x4E, 0x47]);

function validatePng(filePath) {
  const buf = fs.readFileSync(filePath);
  if (buf.length < 100) {
    throw new Error(`Image too small (${buf.length} bytes): ${filePath}`);
  }
  if (!buf.subarray(0, 4).equals(PNG_MAGIC)) {
    throw new Error(`Not a valid PNG (magic: ${buf.subarray(0, 4).toString('hex')}): ${filePath}`);
  }
  return buf;
}

// ── PNG optimisation ─────────────────────────────────────────────────────────

function optimizeImage(filePath) {
  try {
    execSync(`sips -Z 512 "${filePath}" --out "${filePath}"`, { stdio: 'pipe' });
  } catch (e) {
    console.warn(`  ⚠ sips failed: ${e.message}`);
  }
  try {
    execSync(`pngquant --quality=65-80 --force --ext .png "${filePath}"`, { stdio: 'pipe' });
  } catch (e) {
    if (!e.message.includes('exit code 98')) {
      console.warn(`  ⚠ pngquant failed: ${e.message}`);
    }
  }
}

// ── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  const { values } = parseArgs({
    options: {
      name:        { type: 'string' },
      description: { type: 'string' },
      'style-ref': { type: 'string' },
      'api-key':   { type: 'string' },
      attempts:    { type: 'string', default: '3' },
    },
  });

  const name        = values.name;
  const description = values.description;
  const styleRef    = values['style-ref'];
  const apiKey      = values['api-key'] || process.env.OPENAI_API_KEY;
  const attempts    = parseInt(values.attempts, 10);

  if (!name || !description || !styleRef || !apiKey) {
    console.error([
      '',
      'Usage:',
      '  node scripts/create-character.js \\',
      '    --name <character>              character name (used for filenames)',
      '    --description "<description>"   what the character looks like',
      '    --style-ref <image.png>         existing character PNG as style reference',
      '    --api-key <sk-...>              OpenAI API key (or set OPENAI_API_KEY)',
      '    [--attempts 3]                  number of variants to generate (pick best)',
      '',
    ].join('\n'));
    process.exit(1);
  }

  if (!fs.existsSync(styleRef)) {
    console.error(`Error: style reference image not found: ${styleRef}`);
    process.exit(1);
  }

  const styleBuffer = validatePng(styleRef);

  const client = new OpenAI({ apiKey });

  const prompt =
    `Create a new character illustration of a ${name}: ${description}.\n\n` +
    `STYLE: Match the EXACT art style of the reference image — same line thickness, ` +
    `shading technique, colour saturation, level of detail, and overall aesthetic. ` +
    `The new character should look like it belongs in the same app/game as the reference.\n\n` +
    `EXPRESSION: Perfectly neutral, calm and composed, neither happy nor sad. ` +
    `This is the mood-0 baseline for the character.\n\n` +
    `COMPOSITION: Head only, centered on canvas, facing forward. ` +
    `Similar framing and size as the reference character.\n\n` +
    `TRANSPARENCY: The background must be fully transparent (alpha = 0). ` +
    `The character itself must be fully opaque. No scenery, no ground, no effects.`;

  console.log(`\nCharacter   : ${name}`);
  console.log(`Description : ${description}`);
  console.log(`Style ref   : ${styleRef}`);
  console.log(`Attempts    : ${attempts}`);
  console.log(`\n── Prompt ──────────────────────────────────────`);
  console.log(prompt);
  console.log(`────────────────────────────────────────────────\n`);

  const styleFile = new File([styleBuffer], path.basename(styleRef), { type: 'image/png' });

  for (let i = 1; i <= attempts; i++) {
    const suffix   = attempts > 1 ? `_v${i}` : '';
    const filename = `${name.toLowerCase()}${suffix}.png`;
    const outPath  = filename;

    if (fs.existsSync(outPath)) {
      console.log(`  ⏭  ${filename} (already exists, skipping)`);
      continue;
    }

    console.log(`  →  Generating ${filename} (attempt ${i}/${attempts})...`);
    console.log(`     📎 Style reference: ${path.basename(styleRef)} (${styleBuffer.length} bytes)`);

    const response = await client.images.edit({
      model: 'chatgpt-image-latest',
      image: [styleFile],
      prompt,
      quality: 'high',
      output_format: 'png',
      background: 'transparent',
      n: 1,
      size: '1024x1024',
    });

    const item = response.data?.[0];
    let imageData;
    if (item?.b64_json) {
      imageData = Buffer.from(item.b64_json, 'base64');
    } else if (item?.url) {
      const imgRes = await fetch(item.url);
      imageData = Buffer.from(await imgRes.arrayBuffer());
    } else {
      throw new Error('No image data in response');
    }

    fs.writeFileSync(outPath, imageData);
    console.log(`     ✓ saved → ${outPath}`);

    if (i < attempts) {
      await new Promise(r => setTimeout(r, 2000));
    }
  }

  console.log(`\nDone.`);
  if (attempts > 1) {
    console.log(`\nGenerated ${attempts} variants. Review them and rename your favourite to:`);
    console.log(`  ${name.toLowerCase()}.png`);
    console.log(`\nThen run generate-character.js to create all mood variants:`);
    console.log(`  node scripts/generate-character.js --name ${name} --neutral ${name.toLowerCase()}.png`);
  } else {
    console.log(`\nNow generate all mood variants:`);
    console.log(`  node scripts/generate-character.js --name ${name} --neutral ${name.toLowerCase()}.png`);
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
