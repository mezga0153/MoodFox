#!/usr/bin/env node

import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { parseArgs } from 'util';
import OpenAI from 'openai';

const MOOD_EXPRESSIONS = {
  '-10': 'extreme sadness',
  '-9': 'very deep sadness',
  '-8': 'very strong sadness',
  '-7': 'strong sadness',
  '-6': 'clear sadness',
  '-5': 'moderate sadness',
  '-4': 'mild sadness',
  '-3': 'slight sadness',
  '-2': 'very slight sadness',
  '-1': 'trace sadness',
  '0': 'neutral',
  '1': 'trace warmth',
  '2': 'very slight happiness',
  '3': 'slight happiness',
  '4': 'mild happiness',
  '5': 'moderate happiness',
  '6': 'clear happiness',
  '7': 'strong happiness',
  '8': 'very strong happiness',
  '9': 'very intense happiness',
  '10': 'extreme joy',
};

const PNG_MAGIC = Buffer.from([0x89, 0x50, 0x4e, 0x47]);

const moodToLabel = (value) => {
  return value > 0 ? `+${value}` : `${value}`;
};

const moodToFilename = (name, value, candidateIndex = null) => {
  const suffix = value < 0 ? `neg${Math.abs(value)}` : `${value}`;
  const base = `${name.toLowerCase()}_mood_${suffix}`;
  return candidateIndex !== null ? `${base}_c${candidateIndex}.png` : `${base}.png`;
};

const assertPngFile = (filePath, label) => {
  if (!fs.existsSync(filePath)) {
    throw new Error(`${label} not found: ${filePath}`);
  }

  const buf = fs.readFileSync(filePath);

  if (buf.length < 100) {
    throw new Error(`${label} is too small (${buf.length} bytes): ${filePath}`);
  }

  if (!buf.subarray(0, 4).equals(PNG_MAGIC)) {
    throw new Error(`${label} is not a valid PNG: ${filePath}`);
  }

  return buf;
};

const loadPngAsFile = (filePath, label) => {
  const buf = assertPngFile(filePath, label);
  console.log(`       📎 ${label}: ${path.basename(filePath)} (${buf.length} bytes)`);
  return new File([buf], path.basename(filePath), { type: 'image/png' });
};

const buildPrompt = ({ fromMood, toMood, hasPrevRef, useMask }) => {
  const targetLabel = moodToLabel(toMood);
  const fromLabel = fromMood === null ? '0' : moodToLabel(fromMood);
  const expression = MOOD_EXPRESSIONS[String(toMood)];

  const lines = [
    'The first image is the master reference for this character.',
    'Preserve the first image character identity exactly: head shape, ears, eye style, muzzle, nose, colors, line style, markings, proportions, framing, and accessory details.',
  ];

  if (hasPrevRef) {
    lines.push('The second image shows the previous mood level. Use it only as a hint for the direction and intensity of the expression change.');
  }

  if (useMask) {
    lines.push('Edit only inside the masked region. Do not change anything outside the mask.');
  } else {
    lines.push('Edit only the facial expression. Do not redesign or restyle the character.');
  }

  lines.push(
    `Move the expression from mood ${fromLabel} to mood ${targetLabel}.`,
    'This must be exactly one mood step of change, not a large reinterpretation.',
    `Target emotion: ${expression}.`
  );

  if (toMood < 0) {
    lines.push('Express sadness only through mouth downturn, subtle inner eyebrow lift, slightly heavier eyelids, and reduced eye brightness.');
    if (Math.abs(toMood) < 8) {
      lines.push('Do not add tears.');
    } else {
      lines.push('At this intensity, small visible tears are allowed if needed.');
    }
  } else if (toMood > 0) {
    lines.push('Express happiness only through mouth uplift, cheek lift, and slightly brighter more open eyes.');
    lines.push('Do not add sparkles, stars, blush, particles, or decorative effects.');
  } else {
    lines.push('Use a neutral relaxed mouth, neutral brows, and neutral eyes.');
  }

  lines.push('Keep the background transparent.');

  return lines.join('\n');
};

const optimizeImage = (filePath) => {
  try {
    execSync(`sips -Z 512 "${filePath}" --out "${filePath}"`, { stdio: 'pipe' });
  } catch (e) {
    console.warn(`       ⚠ sips failed: ${e.message}`);
  }

  try {
    execSync(`pngquant --quality=65-80 --force --ext .png "${filePath}"`, { stdio: 'pipe' });
  } catch (e) {
    if (!String(e.message).includes('exit code 98')) {
      console.warn(`       ⚠ pngquant failed: ${e.message}`);
    }
  }
};

const getExpectedPrimaryPath = (outputDir, characterName, moodValue, candidates) => {
  const filename = candidates > 1
    ? moodToFilename(characterName, moodValue, 1)
    : moodToFilename(characterName, moodValue);

  return path.join(outputDir, filename);
};

const getExistingCandidatePaths = (outputDir, characterName, moodValue, candidates) => {
  const paths = [];

  for (let i = 1; i <= candidates; i++) {
    const filename = candidates > 1
      ? moodToFilename(characterName, moodValue, i)
      : moodToFilename(characterName, moodValue);

    const fullPath = path.join(outputDir, filename);
    if (fs.existsSync(fullPath)) {
      paths.push(fullPath);
    }
  }

  return paths;
};

const callImageEdit = async ({ client, neutralPath, prevRefPath, maskPath, prompt, candidates }) => {
  const images = [loadPngAsFile(neutralPath, 'Image 1 (master)')];

  if (prevRefPath) {
    images.push(loadPngAsFile(prevRefPath, 'Image 2 (previous mood hint)'));
  }

  const request = {
    model: 'chatgpt-image-latest',
    image: images,
    prompt,
    quality: 'high',
    output_format: 'png',
    background: 'transparent',
    input_fidelity: 'high',
    n: candidates,
    size: '1024x1024',
  };

  if (maskPath) {
    request.mask = loadPngAsFile(maskPath, 'Mask');
  }

  const response = await client.images.edit(request);
  const results = [];

  for (const item of response.data ?? []) {
    if (item?.b64_json) {
      results.push(Buffer.from(item.b64_json, 'base64'));
    }
  }

  if (results.length === 0) {
    throw new Error('No image data returned from API');
  }

  return results;
};

const generateStep = async ({
  client,
  characterName,
  neutralPath,
  prevRefPath,
  maskPath,
  fromMood,
  moodValue,
  outputDir,
  candidates,
}) => {
  const expectedPrimaryPath = getExpectedPrimaryPath(outputDir, characterName, moodValue, candidates);
  const existingPaths = getExistingCandidatePaths(outputDir, characterName, moodValue, candidates);

  if (existingPaths.length > 0) {
    console.log(`  ⏭  ${moodToLabel(moodValue).padStart(3)}  ${path.basename(expectedPrimaryPath)} (exists, skipping)`);
    return {
      primaryPath: existingPaths[0] ?? expectedPrimaryPath,
      savedPaths: existingPaths,
      skipped: true,
      failed: false,
    };
  }

  const prompt = buildPrompt({
    fromMood,
    toMood: moodValue,
    hasPrevRef: !!prevRefPath,
    useMask: !!maskPath,
  });

  console.log(`  →  ${moodToLabel(moodValue).padStart(3)}  ${path.basename(expectedPrimaryPath)}`);
  console.log(`\n── Prompt ──────────────────────────────────────\n${prompt}\n────────────────────────────────────────────────\n`);

  try {
    const buffers = await callImageEdit({
      client,
      neutralPath,
      prevRefPath,
      maskPath,
      prompt,
      candidates,
    });

    const savedPaths = [];
    let primaryPath = null;

    buffers.forEach((buf, index) => {
      const filename = candidates > 1
        ? moodToFilename(characterName, moodValue, index + 1)
        : moodToFilename(characterName, moodValue);

      const outPath = path.join(outputDir, filename);
      fs.writeFileSync(outPath, buf);
      savedPaths.push(outPath);

      if (index === 0) {
        primaryPath = outPath;
      }

      console.log(`       ✓ saved (raw): ${filename}`);
    });

    return {
      primaryPath,
      savedPaths,
      skipped: false,
      failed: false,
    };
  } catch (err) {
    console.error(`       ✗ failed: ${err.message}`);
    return {
      primaryPath: null,
      savedPaths: [],
      skipped: false,
      failed: true,
    };
  }
};

const parseInteger = (value, label) => {
  const parsed = Number.parseInt(value, 10);

  if (Number.isNaN(parsed)) {
    throw new Error(`Invalid ${label}: ${value}`);
  }

  return parsed;
};

const main = async () => {
  const { values } = parseArgs({
    options: {
      name: { type: 'string' },
      neutral: { type: 'string' },
      mask: { type: 'string' },
      'api-key': { type: 'string' },
      output: { type: 'string', default: 'app/mobile/src/main/res/drawable-nodpi' },
      start: { type: 'string', default: '-10' },
      end: { type: 'string', default: '10' },
      chain: { type: 'boolean', default: false },
      candidates: { type: 'string', default: '1' },
      'post-optimize': { type: 'boolean', default: false },
    },
    allowPositionals: false,
  });

  const characterName = values.name;
  const neutralPath = values.neutral;
  const maskPath = values.mask || null;
  const apiKey = values['api-key'] || process.env.OPENAI_API_KEY;
  const outputDir = values.output;
  const startVal = parseInteger(values.start, 'start');
  const endVal = parseInteger(values.end, 'end');
  const useChain = values.chain;
  const candidates = Math.min(10, Math.max(1, parseInteger(values.candidates, 'candidates')));
  const postOptimize = values['post-optimize'];

  if (!characterName || !neutralPath || !apiKey) {
    console.error([
      '',
      'Usage:',
      '  node scripts/generate-character.js \\',
      '    --name <character>',
      '    --neutral <neutral.png>',
      '    [--mask <mask.png>]',
      '    [--api-key <sk-...>] or set OPENAI_API_KEY',
      '    [--output <dir>]',
      '    [--start -10]',
      '    [--end 10]',
      '    [--chain]',
      '    [--candidates 1]',
      '    [--post-optimize]',
      '',
    ].join('\n'));
    process.exit(1);
  }

  if (startVal < -10 || startVal > 10 || endVal < -10 || endVal > 10) {
    throw new Error('start and end must be between -10 and 10');
  }

  assertPngFile(neutralPath, 'Neutral master');

  if (maskPath) {
    assertPngFile(maskPath, 'Mask');
  }

  fs.mkdirSync(outputDir, { recursive: true });

  const neutralOutputPath = path.join(outputDir, moodToFilename(characterName, 0));
  if (!fs.existsSync(neutralOutputPath)) {
    fs.copyFileSync(neutralPath, neutralOutputPath);
    console.log(`\nCopied neutral → ${path.basename(neutralOutputPath)} (unoptimized)`);
  }

  console.log(`\nCharacter  : ${characterName}`);
  console.log(`Master ref : ${neutralPath}`);
  console.log(`Mask       : ${maskPath ?? '(none)'}`);
  console.log(`Output     : ${outputDir}`);
  console.log(`Model      : chatgpt-image-latest`);
  console.log(`Chain mode : ${useChain ? 'on' : 'off'}`);
  console.log(`Candidates : ${candidates}\n`);

  const client = new OpenAI({ apiKey });

  let generatedCount = 0;
  let skippedCount = 0;
  let failedCount = 0;
  const generatedPaths = [];

  const delay = () => new Promise((resolve) => setTimeout(resolve, 2000));

  if (startVal < 0) {
    console.log('── Negative moods ──────────────────────────────');
    let prevRef = null;
    let prevMood = 0;

    for (let v = -1; v >= startVal; v--) {
      const result = await generateStep({
        client,
        characterName,
        neutralPath,
        prevRefPath: useChain ? prevRef : null,
        maskPath,
        fromMood: useChain ? prevMood : 0,
        moodValue: v,
        outputDir,
        candidates,
      });

      if (result.failed) {
        failedCount++;
      } else if (result.skipped) {
        skippedCount++;
        if (useChain && result.primaryPath) {
          prevRef = result.primaryPath;
          prevMood = v;
        }
      } else {
        generatedCount++;
        generatedPaths.push(...result.savedPaths);
        if (useChain && result.primaryPath) {
          prevRef = result.primaryPath;
          prevMood = v;
        }
      }

      if (v > startVal) {
        await delay();
      }
    }
  }

  if (endVal > 0) {
    console.log('\n── Positive moods ──────────────────────────────');
    let prevRef = null;
    let prevMood = 0;

    for (let v = 1; v <= endVal; v++) {
      const result = await generateStep({
        client,
        characterName,
        neutralPath,
        prevRefPath: useChain ? prevRef : null,
        maskPath,
        fromMood: useChain ? prevMood : 0,
        moodValue: v,
        outputDir,
        candidates,
      });

      if (result.failed) {
        failedCount++;
      } else if (result.skipped) {
        skippedCount++;
        if (useChain && result.primaryPath) {
          prevRef = result.primaryPath;
          prevMood = v;
        }
      } else {
        generatedCount++;
        generatedPaths.push(...result.savedPaths);
        if (useChain && result.primaryPath) {
          prevRef = result.primaryPath;
          prevMood = v;
        }
      }

      if (v < endVal) {
        await delay();
      }
    }
  }

  console.log(`\nDone. ${generatedCount} generated, ${skippedCount} skipped, ${failedCount} failed.`);

  if (postOptimize && generatedPaths.length > 0) {
    console.log(`\n── Post-optimizing ${generatedPaths.length} file(s) ──`);
    for (const filePath of generatedPaths) {
      console.log(`  ${path.basename(filePath)}`);
      optimizeImage(filePath);
    }
    console.log('── Done ──');
  } else if (!postOptimize && generatedPaths.length > 0) {
    console.log('\nRaw outputs saved. Use --post-optimize only after you are done generating.');
  }
};

main().catch((err) => {
  console.error(err);
  process.exit(1);
});